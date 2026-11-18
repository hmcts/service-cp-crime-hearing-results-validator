# Validation API Contract Delta: CRA-38

No new endpoints. The existing `POST /validate` response is unchanged in structure.

## Example: DR-EMON-017 fires (AC2 scenario)

### Request (abbreviated)

```json
{
  "hearingId": "h-abc123",
  "offences": [
    {"offenceId": "off-1", "offenceCode": "CD98001", "hasActiveElectronicMonitoring": true, "defendantId": "d-1"},
    {"offenceId": "off-2", "offenceCode": "TH68001", "hasActiveElectronicMonitoring": false, "defendantId": "d-1"}
  ],
  "resultLines": [
    {"resultLineId": "rl-1", "shortCode": "IMP", "category": "F", "offenceId": "off-1", "defendantId": "d-1", "prompts": []},
    {"resultLineId": "rl-2", "shortCode": "FINE", "category": "F", "offenceId": "off-2", "defendantId": "d-1", "prompts": []}
  ],
  "defendants": [
    {"defendantId": "d-1", "firstName": "John", "lastName": "Smith"}
  ]
}
```

**Why it fires**: off-1 has `hasActiveElectronicMonitoring=true`, has a final result (IMP), no EMONE, and no prompt with promptRef in {ELMONDET, EMREQ, EMREQV, EMSO} with value "Yes".

### Response (abbreviated)

```json
{
  "hearingId": "h-abc123",
  "mode": "enabled",
  "errors": [
    {
      "ruleId": "DR-EMON-017",
      "conditionId": "AC2",
      "severity": "ERROR",
      "message": "Electronic monitoring is in place on offences. Add a result that continues electronic monitoring, or end it with 'Electronic Monitoring End - notify contractor (EMONE)'.",
      "offenceIds": ["off-1"]
    }
  ],
  "warnings": []
}
```

## Example: AC1 — all resolved (no issues)

### Request: EM-flagged offence with EMONE

```json
{
  "offences": [{"offenceId": "off-1", "hasActiveElectronicMonitoring": true, "defendantId": "d-1"}],
  "resultLines": [
    {"resultLineId": "rl-1", "shortCode": "IMP", "category": "F", "offenceId": "off-1"},
    {"resultLineId": "rl-2", "shortCode": "EMONE", "category": "I", "offenceId": "off-1"}
  ]
}
```

### Response: clean

```json
{"hearingId": "...", "mode": "enabled", "errors": [], "warnings": []}
```

## Example: AC9 — no EM-flagged offences (no issues)

```json
{
  "offences": [{"offenceId": "off-1", "hasActiveElectronicMonitoring": false, "defendantId": "d-1"}],
  "resultLines": [{"shortCode": "IMP", "category": "F", "offenceId": "off-1"}]
}
```

### Response: clean (no EM validation performed)

```json
{"hearingId": "...", "mode": "enabled", "errors": [], "warnings": []}
```
