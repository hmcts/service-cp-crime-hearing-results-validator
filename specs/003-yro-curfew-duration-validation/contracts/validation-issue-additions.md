# Contract: New `ValidationIssue` entries for `DR-YRO-001`

**No new API surface.** The existing `POST /api/validation/validate` endpoint, its request schema
(`DraftValidationRequest`), and its response schema (`DraftValidationResponse`) are unchanged. This
feature is additive: two new possible `ValidationIssue` entries can appear in `errors.validationIssues`
under the already-existing `ruleId: "DR-YRO-001"`.

## New condition ids

| Condition id | Trigger | `affectedOffenceSet` |
|---|---|---|
| `DUR-YRC2` | A Curfew requirement (YRC2)'s "End date" ≠ "Start date" + "Curfew period" − 1 day | `curDurationMismatchOffenceIds` |
| `DUR-YRC1` | A Curfew with electronic monitoring requirement (YRC1)'s "End date of tagging" ≠ "Start date of tagging" + "Curfew and electronic monitoring period" − 1 day | `cureDurationMismatchOffenceIds` |

## Example response fragment (illustrative — field names match the existing `ValidationIssue` schema)

```json
{
  "errors": {
    "validationIssues": [
      {
        "ruleId": "DR-YRO-001",
        "severity": "ERROR",
        "validationLevel": "OFFENCE",
        "affectedOffences": [
          {
            "offenceId": "offence-1",
            "message": "The end date for the Curfew Requirement does not match the period of the requirement. The current recorded period would mean the end date should be 21/09/2026."
          }
        ]
      }
    ],
    "errorMessages": [
      "The end date for the Curfew Requirement does not match the period of the requirement. This affects Jordan Smith."
    ]
  },
  "isValid": false
}
```

## Consumer contract

- A `DUR-YRC2`/`DUR-YRC1` entry may appear **alongside** an existing `AC2a`/`AC2b` entry for the same
  offence (a requirement can fail both the order-end-date check and its own duration check
  simultaneously) — the UI must render both, not just one.
- `${calculatedEndDate}` is always expanded to a concrete `dd/MM/yyyy` value before the response is
  returned — callers never see the literal token.
- Severity is `ERROR` by default; the `validation_rule` DB ceiling for `DR-YRO-001` (rule-level, not
  condition-level) can lower it to `WARNING` at runtime but never promote it — same as every other
  condition under this rule id.
