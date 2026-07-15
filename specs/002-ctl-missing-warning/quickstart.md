# Quickstart: Testing DR-CTL-001 (CTL Missing Warning)

**Branch**: `DD-41663-ctl-missing-warning` | **Date**: 2026-05-06

> **Prerequisite**: The upstream `api-cp-crime-hearing-results-validator` JAR must be updated with
> `hasExistingCtlRecord` and `isConvicted` fields on `OffenceDto` before the Java implementation
> compiles. Once available, update `gradle.properties` to reference the new version.

---

## Run the tests

```bash
# Unit tests for the new preprocessor and context record
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CtlMissingPreprocessorTest"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CtlOffenceContextTest"

# Integration test for the full rule end-to-end
gradle test --tests "uk.gov.hmcts.cp.integration.CtlMissingRuleIT"

# Full build (Checkstyle + PMD + all tests)
gradle build
```

---

## Trigger the warning (minimal request)

POST to `http://localhost:4550/validate` with:

```json
{
  "offences": [
    {
      "id": "offence-1",
      "offenceCode": "TH68001",
      "hasExistingCtlRecord": false,
      "isConvicted": false
    }
  ],
  "resultLines": [
    {
      "id": "rl-1",
      "shortCode": "RI",
      "offenceId": "offence-1"
    }
  ],
  "defendants": []
}
```

Expected response includes a warning for `offence-1`:

```json
{
  "warnings": [
    {
      "ruleId": "DR-CTL-001",
      "conditionId": "AC1",
      "message": "This offence does not have a CTL. If the trial has started a CTL is not needed. It is your responsibility to check and confirm.",
      "affectedOffenceIds": ["offence-1"]
    }
  ],
  "errors": []
}
```

---

## Suppress the warning — bypass scenarios

| Bypass | Change to request |
|--------|------------------|
| Existing CTL record | Set `"hasExistingCtlRecord": true` on the offence |
| CTL result in current hearing | Add a result line `{"shortCode": "CTL", "offenceId": "offence-1"}` |
| Offence convicted | Set `"isConvicted": true` on the offence |
| No trigger result | Change `"RI"` to a non-remand short code (e.g. `"IMP"`) |

In all four cases `warnings` should be empty for `offence-1`.

---

## Rule override (runtime ceiling)

Insert a row in the `validation_rule` table to test the severity ceiling:

```sql
INSERT INTO validation_rule (rule_id, enabled, severity)
VALUES ('DR-CTL-001', true, 'WARNING');
```

The WARNING severity cannot be promoted — ceiling has no effect here. To disable the rule entirely:

```sql
UPDATE validation_rule SET enabled = false WHERE rule_id = 'DR-CTL-001';
```
