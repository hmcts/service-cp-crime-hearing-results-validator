# Quickstart — DD-41656 Extended Test Disqualification Warning

A short, copy-pasteable guide for running the new rule end-to-end and verifying it fires (and suppresses) correctly. Assumes a working local environment: Java 25, Gradle, Docker for `gradle api`, and direnv-loaded `.env` if you use one.

> **Revised 2026-04-28** — payloads now include `category` on each result line (`A`/`I`/`F`). Adjournment scenario added below as smoke test 3.5. The rule's `category = 'F'` gate means callers that haven't yet been upgraded to populate `category` will see the rule silently skip relevant offences — see the FR-015 fail-safe note in the troubleshooting table.

---

## 1. Build and run unit + integration tests

```bash
# From the repo root
gradle test

# Just the new preprocessor tests
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.DisqualificationExtendedTestPreprocessorTest"

# Just the new rule's integration test
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.integration.DisqualificationExtendedTestRuleIT"

# Everything (compile + Checkstyle Google + PMD + tests)
gradle build
```

Constitution gates: `gradle build` must pass with `maxWarnings = 0`. Run after every code change before committing (per project `feedback_run_checkstyle.md`).

---

## 2. Run the service locally

```bash
gradle bootRun                    # default port 4550
SERVER_PORT=8080 gradle bootRun   # override port
```

The new rule is auto-discovered at startup by `ValidationRuleAutoConfiguration` from `src/main/resources/rules/DR-DISQ-001.yaml`. Look for this log line on startup:

```
INFO  ...config.ValidationRuleAutoConfiguration : Auto-discovered 2 validation rule(s) from classpath:rules/
```

If you see `1` instead of `2`, the YAML did not load — check the file is named `DR-DISQ-001.yaml` (the loader filters on the `DR-` prefix) and that YAML parsing did not throw on startup.

---

## 3. Smoke-test the rule with curl

### Triggers the warning

```bash
curl -sX POST http://localhost:4550/api/validation/validate \
  -H 'Content-Type: application/json' \
  -H 'CPP-ACTION: hearing-results.validate' \
  -d '{
    "hearingId": "h-1",
    "hearingDay": "2026-04-25",
    "courtType": "MAGISTRATES",
    "defendants": [
      {"id": "d-1", "firstName": "Alex", "lastName": "Driver"}
    ],
    "offences": [
      {"id": "o-1", "offenceCode": "RT88026", "offenceTitle": "Dangerous driving"}
    ],
    "resultLines": [
      {"id": "r-1", "shortCode": "COEW", "category": "F", "label": "Convicted...", "defendantId": "d-1", "offenceId": "o-1"}
    ]
  }'
```

Expected: response contains exactly one `ValidationIssue` with `ruleId: "DR-DISQ-001"`, `severity: "WARNING"`, the exact AC1A message, and `affectedOffences: [{ "offenceId": "o-1", ... }]`.

### Suppressed by an excluded final result (on the F line)

Replace `resultLines[0].shortCode` with `"wdrn"` (keep `category: "F"`). Expected: zero `DR-DISQ-001` issues. (You may still see issues from other rules.) Note: under the new gate, the excluded short code must be on the **F-category** line — an excluded short code on an `A`/`I` line no longer suppresses.

### Suppressed by DDOTE present

Add a second result line (the DDOTE line is typically `category: "I"` — disqualifications are intermediary, not final-status-determining):

```json
{"id": "r-2", "shortCode": "DDOTE", "category": "I", "label": "Disqual extended test", "defendantId": "d-1", "offenceId": "o-1"}
```

Expected: zero `DR-DISQ-001` issues against offence `o-1`. DDOTE/DDOTEL on any category suppresses — by design (the rule's purpose is satisfied wherever the disqualification line sits).

### Suppressed when only an adjournment is recorded (BA scenario 5)

Replace the original triggering payload's `resultLines` with a single adjournment line on the relevant offence:

```json
"resultLines": [
  {"id": "r-1", "shortCode": "ADJN", "category": "A", "label": "Adjourned", "defendantId": "d-1", "offenceId": "o-1"}
]
```

Expected: zero `DR-DISQ-001` issues. The relevant offence has no F-category line, so the rule does not fire — even though `ADJN` is not in the excluded-final list. This is the canonical regression test for the 2026-04-28 revision (see SC-002 in spec.md).

### Two qualifying offences → two warnings

Add a second offence and a second F-category result line:

```json
"offences": [
  {"id": "o-1", "offenceCode": "RT88026", "offenceTitle": "Dangerous driving"},
  {"id": "o-2", "offenceCode": "RT88046", "offenceTitle": "Causing death by dangerous driving"}
],
"resultLines": [
  {"id": "r-1", "shortCode": "COEW", "category": "F", "label": "...", "defendantId": "d-1", "offenceId": "o-1"},
  {"id": "r-2", "shortCode": "FO",   "category": "F", "label": "...", "defendantId": "d-1", "offenceId": "o-2"}
]
```

Expected: two `DR-DISQ-001` issues, one per offence id.

### Disable via DB override

```sql
INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by)
VALUES ('DR-DISQ-001', false, NULL, now(), 'manual-test');
```

After ≤30 s (Caffeine TTL on `RuleOverrideService`), the smoke test from step 3.1 returns zero `DR-DISQ-001` issues without service restart.

---

## 4. Inspect the rule via the rules API

```bash
# All rules
curl -s http://localhost:4550/api/validation/rules | jq .

# This rule
curl -s http://localhost:4550/api/validation/rules/DR-DISQ-001 | jq .
```

Expected: `enabled: true`, `severity: "WARNING"`, `priority: 2000`.

---

## 5. Run the API tests against docker-compose

```bash
gradle api
```

This boots the full docker-compose stack (PostgreSQL + the service) and runs the live HTTP tests in `src/apiTest`. The new rule should be exercised by the integration test added by this change.

---

## 6. Run Gatling against the local service (optional)

```bash
gradle gatlingRun-uk.gov.hmcts.cp.simulation.CapacitySimulation \
  -Dgatling.baseUrl=http://localhost:4550
```

Compare the latency report to a recent baseline run on the same hardware. Per success criterion **SC-005**, the new rule must add no perceptible latency. The CEL expression is single and trivial; the preprocessor is O(offences + result lines).

---

## 7. Where to look when something breaks

| Symptom | Likely cause |
|---------|--------------|
| Service start fails with `IllegalStateException: No preprocessor registered for type: disqualification-extended-test` | The new `@Component` was not picked up — check the package, qualifier in `type()`, and that you ran a clean Gradle build. |
| Service starts but the rule is missing from `/api/validation/rules` | YAML file not on the classpath — check filename (`DR-DISQ-001.yaml`, exact case), or YAML parse error in startup logs. |
| Warning fires when it shouldn't | Re-check the gates in `DisqualificationExtendedTestPreprocessor` and the YAML's `excludedFinalShortCodes` / `extendedTestShortCodes` lists. Compare against [research.md R3-revised](./research.md#r3-revised-refinement-gate-on-categoryf-from-the-result-line-2026-04-28). Common cause: a non-F-category line is being treated as final due to a missed `category` check. |
| Warning doesn't fire when it should (relevant offence with apparent final result) | Most likely the result line is missing `category` or has it set to something other than `'F'`. Check the request payload directly. The FR-015 fail-safe deliberately silences warnings rather than risk false positives on legacy/malformed data — caller-side fix is to populate `category` correctly. INFO logs report unrecognised category values seen during preprocessing. |
| Warning doesn't fire when it should (correct `category = 'F'`) | Check `OffenceDto.offenceCode` casing — comparison is case-insensitive but a typo in the YAML's `relevantOffenceCodes` will silently drop matches. |
| `gradle build` fails on Checkstyle / PMD | Run `gradle checkstyleMain pmdMain` for full output; the project enforces `maxWarnings = 0` and `ignoreFailures = false`. |
| Cross-rule integration test fails after the registry refactor | Likely a regression in `CelValidationRule.evaluate` — check that `MessageTemplateResolver.resolve` is still receiving the right `defendantName` (null is OK for `DisqualificationContext`). |
