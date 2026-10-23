# Quickstart: Non-Endorsable Offence Warning (CRA-337)

## Prerequisites

- Java 25, Gradle wrapper available
- Docker Desktop running (for integration tests / API tests)
- WireMock stub `wiremock/mappings/referencedataoffences-non-endorsable-stub.json` present (committed)

## Build

```bash
gradle build
```

## Run unit + integration tests only

```bash
gradle test
```

## Run a single test class

```bash
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.NonEndorsableOffencePreprocessorTest"
gradle test --tests "uk.gov.hmcts.cp.integration.NonEndorsableOffenceRuleIT"
```

## Run API tests (docker-compose managed)

```bash
gradle api
```

## Manual HTTP smoke test (once service is running locally on port 4550)

See `specs/015-non-endorsable-offence-warning/CRA-337.http` for ready-made HTTP requests.

### AC1a — endorsement on non-endorsable (expect WARNING DR-END-014 condition AC1)

```http
POST http://localhost:4550/api/validation/validate
Content-Type: application/json
CJSCPPUID: test-user

{
  "hearingId": "test-hearing-001",
  "hearingDay": "2026-10-20",
  "courtType": "MAGISTRATES",
  "resultLines": [
    { "resultLineId": "rl-001", "shortCode": "LEP", "label": "LEP label",
      "defendantId": "d1", "offenceId": "off-001" }
  ],
  "defendants": [
    { "defendantId": "d1", "firstName": "Test", "lastName": "User" }
  ],
  "offences": [
    { "offenceId": "off-001", "offenceCode": "NON-ENDORSABLE-001",
      "offenceTitle": "Test offence", "orderIndex": 1, "defendantId": "d1" }
  ]
}
```

Expected: `200 OK`, `warnings[0].ruleId = "DR-END-014"`, `warnings[0].affectedOffences[0].message = "This offence is not endorsable. Remove the endorsement result."`

### AC1b — disqualification on non-endorsable (expect WARNING DR-END-014 condition AC2)

Same request shape with `"shortCode": "DDO"`.

Expected: `200 OK`, `warnings[0].ruleId = "DR-END-014"`, `warnings[0].affectedOffences[0].message = "This offence is not endorsable. Remove the disqualification result, or record a disqualification for non-endorsable offence result instead."`

### AC1c — both families on same offence (expect two WARNINGs, both DR-END-014)

Add both `LEP` and `DDO` result lines against the same non-endorsable offence.

Expected: `200 OK`, two warnings each with `ruleId = "DR-END-014"` — one with endorsement message, one with disqualification message.

### AC2 — trigger on endorsable offence (expect no DR-END-014 warning)

Use an offence code that returns `endorsableFlag: true` from the WireMock stub.

Expected: `200 OK` with no DR-END-014 warnings.

### AC3 — DDRN on non-endorsable offence (expect no warning)

Use `"shortCode": "DDRN"` against a non-endorsable offence.

Expected: `200 OK` with no DR-END-014 warnings.

## Key WireMock stub patterns (for integration tests)

The committed stub at `wiremock/mappings/referencedataoffences-non-endorsable-stub.json` covers:
- Offence codes matching `"ne-*"` prefixes → `endorsableFlag: false` (non-endorsable)
- Offence codes matching `"live-ne-*"` → `endorsableFlag: false` (non-endorsable, for API live tests)
- Offence codes matching `"live-ne-endorsable"` → `endorsableFlag: true`
- Integration tests use per-test unique codes (e.g. `"ne-end-lep"`) and register WireMock stubs programmatically via `stubEndorsableFlagResponse()`

## Checkstyle & PMD

```bash
gradle checkstyleMain
gradle pmdMain
```

Both must report zero violations before raising a PR.