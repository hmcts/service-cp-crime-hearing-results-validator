# Quickstart: Testing DR-CONV-006 (No Conviction Warning)

**Branch**: `DD-43039-no-conviction-warning` | **Date**: 2026-07-31

> No upstream prerequisite — both `ResultLineDto.category` and `OffenceDto.isConvicted` already exist
> in the currently-consumed `api-cp-crime-hearing-results-validator` version.

---

## Run the tests

```bash
# Unit tests for the new preprocessor and context record
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.NoConvictionPreprocessorTest"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.NoConvictionContextTest"

# Integration test for the full rule end-to-end
gradle test --tests "uk.gov.hmcts.cp.integration.NoConvictionWarningIntegrationTest"

# Full build (Checkstyle + PMD + all tests)
gradle build
```

---

## Trigger the warning (minimal request)

POST to `http://localhost:4550/validate` with:

```json
{
  "hearingId": "hearing-1",
  "hearingDay": "2026-07-31",
  "courtType": "MAGISTRATES",
  "offences": [
    {
      "offenceId": "offence-1",
      "offenceCode": "TH68001",
      "offenceTitle": "Theft",
      "isConvicted": false
    }
  ],
  "resultLines": [
    {
      "resultLineId": "rl-1",
      "shortCode": "COEW",
      "label": "Committed to Crown Court for sentence",
      "defendantId": "defendant-1",
      "offenceId": "offence-1",
      "category": "F"
    }
  ],
  "defendants": [
    { "defendantId": "defendant-1", "firstName": "Jane", "lastName": "Doe" }
  ]
}
```

Expected response includes a warning for `offence-1`:

```json
{
  "warnings": [
    {
      "ruleId": "DR-CONV-006",
      "conditionId": "AC1",
      "message": "No conviction has been added against the offence. Check whether you need to add a guilty plea or verdict",
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
| Offence convicted (AC1A / AC1B) | Set `"isConvicted": true` on the offence |
| Excluded final disposal | Change `"shortCode": "COEW"` to one of `wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`, `err`, `errf`, `dead` |
| No final result yet | Change `"category": "F"` to `"I"` or `"A"`, or remove the result line |

In all three cases `warnings` should be empty for `offence-1`.

---

## Rule override (runtime ceiling)

Already proven once against `DR-SENT-002` in `ValidationRuleOverrideIntegrationTest.java` — no per-rule override test is needed for `DR-CONV-006` (see `.claude/rules/design_rules.md`, "Test the framework once, not the rule again"). To exercise it manually:

```sql
INSERT INTO validation_rule (rule_id, enabled, severity) VALUES ('DR-CONV-006', true, 'WARNING');
```

The WARNING severity cannot be promoted — ceiling has no effect here. To disable the rule entirely:

```sql
UPDATE validation_rule SET enabled = false WHERE rule_id = 'DR-CONV-006';
```
