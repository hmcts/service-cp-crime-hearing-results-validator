# Quickstart: Community Order End Date Validation

**Branch**: `DD-41653-co-end-date-validation`

## Running Tests

```bash
# All unit and integration tests
gradle test

# Preprocessor unit tests only
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CommunityOrderEndDatePreprocessorTest"

# Rule integration test
gradle test --tests "uk.gov.hmcts.cp.integration.CommunityOrderEndDateValidationIT"

# Static analysis
gradle checkstyleMain pmdMain
```

## Manually Testing the Rule

The service starts on port 4550. Use the existing docker-compose stack:

```bash
gradle api
```

Or start locally:

```bash
gradle bootRun
```

> **Note**: Date fields are sent as prompt entries (`promptRef` + `promptValue`), not as direct
> fields on the result line. This requires `api-cp-crime-hearing-results-validator` 0.1.6+.

### AC2 — CUR end date after order end date

```http
POST http://localhost:4550/api/validation/validate
Content-Type: application/json
CJSCPPUID: test-user

{
  "hearingId": "abc123",
  "hearingDay": "2026-05-12",
  "courtType": "MAGISTRATES",
  "defendants": [{ "id": "def1", "firstName": "John", "lastName": "Smith" }],
  "offences": [{ "id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false }],
  "resultLines": [
    {
      "id": "rl1", "shortCode": "COEW", "label": "Community Order",
      "defendantId": "def1", "offenceId": "off1",
      "prompts": [{ "promptRef": "endDate", "promptValue": "2026-10-30" }]
    },
    {
      "id": "rl2", "shortCode": "CUR", "label": "Curfew",
      "defendantId": "def1", "offenceId": "off1",
      "prompts": [{ "promptRef": "endDate", "promptValue": "2026-11-30" }]
    }
  ]
}
```

**Expected**: error `"John Smith — The end date of the order must match or be longer than the end date of Curfew (community requirement) - CUR"`.

### AC3 — UPWR with order end date under 12 months

```http
POST http://localhost:4550/api/validation/validate
Content-Type: application/json
CJSCPPUID: test-user

{
  "hearingId": "abc123",
  "hearingDay": "2026-05-12",
  "courtType": "MAGISTRATES",
  "defendants": [{ "id": "def1", "firstName": "John", "lastName": "Smith" }],
  "offences": [{ "id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false }],
  "resultLines": [
    {
      "id": "rl1", "shortCode": "COEW", "label": "Community Order",
      "defendantId": "def1", "offenceId": "off1",
      "prompts": [{ "promptRef": "endDate", "promptValue": "2027-05-11" }]
    },
    {
      "id": "rl2", "shortCode": "UPWR",
      "label": "Unpaid work. Requirement to be completed within 12 months",
      "defendantId": "def1", "offenceId": "off1"
    }
  ]
}
```

**Expected**: error `"John Smith — The end date of the order must be at least 12 months as it includes an unpaid work requirement"`.

### Happy path — no errors

```http
POST http://localhost:4550/api/validation/validate
Content-Type: application/json
CJSCPPUID: test-user

{
  "hearingId": "abc123",
  "hearingDay": "2026-05-12",
  "courtType": "MAGISTRATES",
  "defendants": [{ "id": "def1", "firstName": "John", "lastName": "Smith" }],
  "offences": [{ "id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false }],
  "resultLines": [
    {
      "id": "rl1", "shortCode": "COEW", "label": "Community Order",
      "defendantId": "def1", "offenceId": "off1",
      "prompts": [{ "promptRef": "endDate", "promptValue": "2027-05-12" }]
    },
    {
      "id": "rl2", "shortCode": "CUR", "label": "Curfew",
      "defendantId": "def1", "offenceId": "off1",
      "prompts": [{ "promptRef": "endDate", "promptValue": "2026-10-30" }]
    },
    {
      "id": "rl3", "shortCode": "UPWR",
      "label": "Unpaid work. Requirement to be completed within 12 months",
      "defendantId": "def1", "offenceId": "off1"
    }
  ]
}
```

**Expected**: 200 with no errors from DR-COEW-001.

## Key Files After Implementation

```
src/main/resources/rules/DR-COEW-001.yaml
src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessor.java
src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContext.java
src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java   (modified)
src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessorTest.java
src/test/java/uk/gov/hmcts/cp/integration/CommunityOrderEndDateValidationIT.java
```
