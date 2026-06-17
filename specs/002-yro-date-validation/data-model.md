# Data Model: YRO Date Validation (DR-YRO-001)

## Entities in scope

### YRO result line (parent order)

| Attribute | Source | Notes |
|---|---|---|
| `shortCode` | `ResultLineDto.getShortCode()` | One of: YROEW, YRONI, YROFEW, YROISS, YROINI |
| `defendantId` | `ResultLineDto.getDefendantId()` | Groups lines by defendant |
| `offenceId` | `ResultLineDto.getOffenceId()` | Groups lines by offence within defendant |
| `endDate` | `ResultLineDto.getPrompts()` where `promptRef = "endDate"` | ISO-8601 date string; the YRO order end date |

### YRO requirement result lines (child requirements)

| Requirement display name | Short code | Prompt ref | Field label | AC |
|---|---|---|---|---|
| Youth Rehabilitation Requirement: Curfew | YRC2 | `endDate` | End date | AC2a |
| Youth Rehabilitation Requirement: Curfew with electronic monitoring | YRC1 | `endDateOfTagging` | End date of tag | AC2b |
| Youth Rehabilitation Requirement: Further curfew requirement made | YRC3 | `endDate` | End date | AC2c |
| Youth Rehabilitation Requirement: Unpaid work | YRUP1 | *(no date prompt)* | *(presence-only)* | AC3 |

### Defendant

| Attribute | Source |
|---|---|
| `id` | `DefendantDto.getId()` |
| `firstName` | `DefendantDto.getFirstName()` |
| `lastName` | `DefendantDto.getLastName()` |

Defendant names are resolved via `firstName + " " + lastName` for use in `${defendantNames}` template expansion.

### Hearing day

`DraftValidationRequest.getHearingDay()` (`LocalDate`) — used in AC3 to compute the minimum valid order end date.

---

## Violation detection logic

### AC1 — YRO end date not in the future

Per defendant, per offence:

1. Locate the YRO parent result line and parse its `endDate` prompt → `orderEndDate` (`LocalDate`).
2. If `orderEndDate` is null (missing or unparseable) → skip this offence.
3. If `hearingDay` is non-null and `!orderEndDate.isAfter(hearingDay)` (i.e. end date is on or before
   the hearing date) → add offenceId to `pastEndDateOffenceIds`. The end date must be **strictly after**
   the hearing date; equal to the hearing date is an error.

### AC2 — Curfew requirement end date exceeds order end date

Per defendant, per offence:

1. Locate the YRO parent result line (short code in YROEW/YRONI/YROFEW/YROISS/YROINI).
2. Parse `endDate` prompt → `orderEndDate` (`LocalDate`).
3. If `orderEndDate` is null (missing or unparseable) → skip AC2 for this offence.
4. For each curfew-type requirement on the same offence:
   - **YRC2**: parse `endDate` prompt → `reqDate`; if `reqDate.isAfter(orderEndDate)` → add offenceId to `curViolationOffenceIds`
   - **YRC1**: parse `endDateOfTagging` prompt → `reqDate`; if `reqDate.isAfter(orderEndDate)` → add offenceId to `cureViolationOffenceIds`
   - **YRC3**: parse `endDate` prompt → `reqDate`; if `reqDate.isAfter(orderEndDate)` → add offenceId to `curaViolationOffenceIds`

### AC3 — Unpaid work order shorter than 12 months

Per defendant, per offence (evaluated after AC2 date check):

1. Reuse `orderEndDate` from step 2 above.
2. Check presence of YRUP1 on the same offence.
3. If YRUP1 present and `hearingDay` non-null:
   - `minEndDate = hearingDay.plusMonths(12).minusDays(1)`
   - If `orderEndDate.isBefore(minEndDate)` → add offenceId to `upwrViolationOffenceIds`

**Boundary example**: hearing date 20/05/2026 → `minEndDate` = 19/05/2027. End date 18/05/2027 → error; 19/05/2027 → valid.

---

## CEL context variables (from `YouthRehabilitationContext`)

These are the variables available in CEL condition expressions for DR-YRO-001:

| CEL variable | Meaning for DR-YRO-001 | Used by condition |
|---|---|---|
| `pastEndDateCount` | Number of offences whose YRO end date is on or before the hearing date | AC1 |
| `curViolationCount` | Number of offences where YRC2 end date exceeds YRO end date | AC2a |
| `cureViolationCount` | Number of offences where YRC1 end-of-tag exceeds YRO end date | AC2b |
| `curaViolationCount` | Number of offences where YRC3 end date exceeds YRO end date | AC2c |
| `upwrViolationCount` | Number of offences with YRUP1 and YRO end date < 12 months | AC3 |

---

## Offence-id sets (from `YouthRehabilitationContext`)

Resolved by `CelValidationRule` when building `ValidationIssue.affectedOffences`:

| Set name | Contents |
|---|---|
| `pastEndDateOffenceIds` | Offence IDs violating AC1 |
| `curViolationOffenceIds` | Offence IDs violating AC2a |
| `cureViolationOffenceIds` | Offence IDs violating AC2b |
| `curaViolationOffenceIds` | Offence IDs violating AC2c |
| `upwrViolationOffenceIds` | Offence IDs violating AC3 |
| `allOffenceIds` | All offence IDs for the defendant |

---

## No schema migrations

This rule uses only existing runtime override infrastructure (`validation_rule` table). No Liquibase migration is required.

## No new API contracts

Request/response DTOs are owned by `api-cp-crime-hearing-results-validator`. No upstream changes needed — `ResultLineDto` already carries `shortCode` and `prompts`; `DraftValidationRequest` already carries `hearingDay`.
