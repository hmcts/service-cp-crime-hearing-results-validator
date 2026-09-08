# Quickstart: Verifying YRO Curfew Duration Validation Locally

## 1. Enable the rule

`DR-YRO-001` ships with `enabled: false` in both the YAML default and the
`validation_rule` DB row (`V1.003__insert_dr_yro_001.sql`). To exercise it locally:

```sql
UPDATE validation_rule SET enabled = true WHERE id = 'DR-YRO-001';
```

Wait ~2s for the Caffeine cache (`RuleOverrideService`) TTL to expire, or restart the service.

## 2. Start the service

```bash
gradle bootRun
```

Default port `4550` (override via `SERVER_PORT`).

## 3. Send a request that triggers `DUR-YRC2`

```bash
curl -s -X POST http://localhost:4550/api/validation/validate \
  -H "Content-Type: application/json" \
  -d '{
    "hearingId": "hearing-1",
    "hearingDay": "2026-09-01",
    "courtType": "MAGISTRATES",
    "defendants": [{"defendantId": "def-1", "firstName": "Jordan", "lastName": "Smith"}],
    "offences": [{"offenceId": "offence-1", "offenceCode": "RT88026", "displayOrder": 1}],
    "resultLines": [
      {
        "resultLineId": "rl-1", "shortCode": "YROEW", "label": "Youth Rehabilitation Order",
        "defendantId": "def-1", "offenceId": "offence-1",
        "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]
      },
      {
        "resultLineId": "rl-2", "shortCode": "YRC2", "label": "Curfew",
        "defendantId": "def-1", "offenceId": "offence-1",
        "prompts": [
          {"promptRef": "startDate", "promptValue": "2026-09-01"},
          {"promptRef": "curfewPeriod", "promptValue": "21 Days"},
          {"promptRef": "endDate", "promptValue": "2026-10-01"}
        ]
      }
    ]
  }'
```

Expected: `isValid: false`, an `errors.validationIssues[]` entry with `ruleId: "DR-YRO-001"` whose
offence message reads "...the end date should be 21/09/2026" (Start date 01/09/2026 + 21 days − 1).

## 4. Confirm the negative case

Change `endDate` on `rl-2` to `"2026-09-21"` (the correct value) and resend — expect `isValid: true`
and no `DUR-YRC2` entry.

## 5. Automated verification

```bash
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.YouthRehabilitationPreprocessorTest"
gradle test --tests "uk.gov.hmcts.cp.integration.YroEndDateValidationIntegrationTest"
gradle api   # live HTTP test against docker-compose, includes YroCurfewDurationApiHttpLiveTest
```
