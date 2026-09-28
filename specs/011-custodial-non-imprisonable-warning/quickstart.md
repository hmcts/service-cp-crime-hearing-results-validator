# Quickstart: Custodial Sentence Against Non-Imprisonable Offence – Warning (CRA-256)

*v3 updated 2026-10-06 — no upstream DTO prerequisite; custodial indicator is resolved via the reference-data API.*

## Try it locally

> **HTTP scratch file**: `specs/011-custodial-non-imprisonable-warning/CRA-256.http` contains
> ready-to-run requests for all acceptance-criteria scenarios (US1–US5 + edge cases). Open it in
> IntelliJ or VS Code (REST Client) and run against a live stack — see the prerequisite comment at
> the top of the file.

1. Start the service and the WireMock reference-data stub:
   `gradle bootRun` (or `docker compose up` for the full stack including WireMock).
   WireMock stubs for all `CRA-256.http` offence codes live in
   `wiremock/mappings/referencedataoffences-nonimprisonable-stub.json`.
2. Ensure `wiremock/mappings/referencedataoffences-stub.json` includes a stub for your test
   `offenceCode` returning the desired `custodialIndicator` value (see below).
3. `POST /api/validation/validate` with a `DraftValidationRequest` body.

### Scenario A — warning fires (reference data returns custodialIndicator = N)

**WireMock stub** (`wiremock/mappings/referencedataoffences-stub.json`):
```json
{
  "request": {
    "method": "GET",
    "urlPathPattern": ".*/referencedataoffences/offences",
    "queryParameters": { "cjsoffencecode": { "equalTo": "RT88026" } }
  },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/vnd.referencedataoffences.offences-list+json" },
    "jsonBody": {
      "offences": [{ "offenceId": "abc-123", "custodialIndicator": "N" }]
    }
  }
}
```

**Request body**:
```json
{
  "hearingId": "hearing-001",
  "offences": [
    { "offenceId": "off-001", "offenceCode": "RT88026", "offenceTitle": "Speeding" }
  ],
  "defendants": [
    { "defendantId": "def-001", "firstName": "John", "lastName": "Doe" }
  ],
  "resultLines": [
    { "resultLineId": "rl-001", "shortCode": "IMP", "offenceId": "off-001", "defendantId": "def-001" }
  ]
}
```

Expected response: `isValid: true`, `warnings` contains one entry with:
- `ruleId: "DR-SENT-011"`
- `severity: "WARNING"`
- `validationLevel: "OFFENCE"`
- `message: "A custodial sentence may not be available for this offence. Check the sentence is correct before continuing."`
- `affectedOffences` containing `off-001`

### Scenario B — no warning (column = Y)

Same stub but `"custodialIndicator": "Y"`. Expected: no `DR-SENT-011` in `warnings`.

### Scenario C — warning via details_json fallback (column absent, JSON code = N)

Stub response:
```json
{
  "offences": [{
    "offenceId": "abc-123",
    "custodialIndicator": null,
    "details": {
      "document": {
        "libra": {
          "custodialindicator": { "code": "N", "description": "NO" }
        }
      }
    }
  }]
}
```
Expected: same warning as Scenario A.

### Scenario D — no warning (non-custodial result)

Same request as Scenario A but `"shortCode": "COEW"`.
No WireMock call is made (short-code filter exits first). Expected: no `DR-SENT-011` warning.

### Scenario E — no warning (reference data returns no indicator)

Stub response: `"custodialIndicator": null`, no `details` or `custodialindicator` node.
Expected: no `DR-SENT-011` (fail-open, treated as imprisonable).

---

## Verifying the runtime override is NOT tested per-rule

Per `.claude/rules/design_rules.md`, runtime-override / severity-ceiling behaviour is proven
once against `DR-SENT-001` in `ValidationRuleOverrideIntegrationTest`. `DR-SENT-011` inherits
that coverage — do not add a per-rule override IT. If a gap exists, extend that shared test.

---

## Running the tests

```bash
# Client unit tests
gradle test --tests "uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClientTest"

# Preprocessor unit tests
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.NonImprisonableOffencePreprocessorTest"

# Integration tests
gradle test --tests "uk.gov.hmcts.cp.integration.NonImprisonableOffenceRuleIT"

# Full build loop (Checkstyle + PMD + all tests)
gradle build
```
