# Quickstart: CRA-362 – Pre-Release Bail Condition Warning (DR-BAIL-016)

**Branch**: `dev/CRA-362-pre-release-bail-condition-warning`

## Minimal trigger example

```json
POST /api/validation/validate

{
  "hearingId": "h-bail016-quickstart",
  "hearingDay": "2026-11-09",
  "courtType": "MAGISTRATES",
  "resultLines": [
    {
      "resultLineId": "rl-main",
      "shortCode": "RC",
      "label": "Remanded on conditional bail",
      "defendantId": "d1",
      "offenceId": "off-1"
    },
    {
      "resultLineId": "rl-bail",
      "shortCode": "PASS5",
      "label": "Passport - give to the court any passport held",
      "defendantId": "d1",
      "offenceId": "off-1"
    }
  ],
  "defendants": [
    {
      "defendantId": "d1",
      "firstName": "Jane",
      "lastName": "Doe",
      "dateOfBirth": "1990-01-01"
    }
  ],
  "offences": [
    {
      "offenceId": "off-1",
      "defendantId": "d1",
      "offenceCode": "TH001",
      "offenceTitle": "Theft",
      "orderIndex": 1,
      "bailStatus": "C"
    }
  ]
}
```

**Expected response**: `isValid=true`, 1 WARNING, `ruleId=DR-BAIL-016`, condition `AC1` (adult), Warning A message.

## Running locally

```bash
# Start the full stack
gradle bootJar && docker compose up --build --force-recreate -d

# Run unit + integration tests only
gradle test --tests "*PreReleaseBail*"

# Run the full build (Checkstyle + PMD + all tests)
gradle build
```

## Test quickly with the HTTP scratch file

Open `specs/017-pre-release-bail-condition-warning/CRA-362.http` in IntelliJ or VS Code with the
REST Client extension. Run each `###` block to exercise AC1–AC7 scenarios against a running local
instance (`http://localhost:4550`).

## Key short codes

| Group | Short codes |
|-------|-------------|
| Custodial remand statuses (OffenceDto.bailStatus) | C, L, S |
| Conditional bail result codes | RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB |
| Crown Court bail codes | CCIC, CCSC |
| Trigger bail conditions (named) | PASS1–PASS8, PORSC, PORS |
| Trigger bail condition (free-text) | PORO1 (with keyword in prompt) |
| Trigger keyword stems | passport, suret, securit, asbort, echniw, sicrwydd |
