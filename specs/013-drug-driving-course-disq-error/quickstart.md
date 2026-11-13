# Quickstart: Testing DR-DISQ-013 Locally

## 1. Start the stack

```bash
gradle bootJar && docker compose up --build --force-recreate -d
```

## 2. Happy-path error (RT88584 + DDRCO)

```http
POST http://localhost:4550/api/validation/validate
Content-Type: application/json
CJSCPPUID: test-user
CPP-ACTION: validation-service.validate

{
  "hearingId": "h1",
  "hearingDay": "2026-10-16",
  "courtType": "MAGISTRATES",
  "resultLines": [
    {
      "resultLineId": "rl1",
      "shortCode": "DDRCO",
      "category": "F",
      "label": "Driving Disqualification – Reduction for Course",
      "defendantId": "d1",
      "offenceId": "off1"
    }
  ],
  "defendants": [
    {"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}
  ],
  "offences": [
    {
      "offenceId": "off1",
      "defendantId": "d1",
      "offenceCode": "RT88584",
      "offenceTitle": "Drive with proportion of specified controlled drug above limit",
      "orderIndex": 1
    }
  ]
}
```

**Expected response** — `$.errors.validationIssues` contains one issue:

```json
{
  "ruleId": "DR-DISQ-013",
  "severity": "ERROR",
  "message": "You cannot add a disqualification with a reduction for attending a course to a drug driving offence. Remove this result and add the correct disqualification.",
  "offenceIds": ["off1"]
}
```

## 3. No false positive (DDO on triggering offence)

Replace `"shortCode": "DDRCO"` with `"shortCode": "DDO"` → `$.errors.validationIssues` must be empty.

## 4. No false positive (DDRCO on non-triggering offence)

Replace `"offenceCode": "RT88584"` with `"offenceCode": "RT88026"` → `$.errors.validationIssues` must be empty.

## 5. Run the automated test suite

```bash
# Unit + integration tests only
gradle test

# Single test class
gradle test --tests "uk.gov.hmcts.cp.integration.DrugDrivingCourseReductionRuleIT"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.DrugDrivingCourseReductionPreprocessorTest"
```
