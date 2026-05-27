# Feature Specification: Curfew and AAR Requirement End-Date Period Validation

**Feature Branch**: `DD-41655-curfew-aar-end-date-validation`
**Created**: 2026-05-26
**Status**: Draft
**Input**: User description: "Validate that the end date recorded for Curfew (CUR, CURE, YRC1, YRC2) and Alcohol Abstinence and Monitoring (AAR) requirements matches the date calculated from the entered period, to prevent common data-entry errors before results are shared."

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Curfew Requirement End Date Does Not Match Calculated Duration (Priority: P1)

As a court clerk entering results, when I have added a Community Order (COEW, COS, CONI) or Youth Rehabilitation Order (YROEW, YRONI, YROFEW, YROISS, YROINI) and one of its requirements is a Curfew requirement (CUR, CURE, YRC1, or YRC2), the system must detect when the recorded end date does not equal the start date plus the entered curfew period minus one day — and block saving until the inconsistency is corrected.

**Why this priority**: The curfew end date is a legal obligation derived from the period entered. A mismatch means either the period or the date is wrong; allowing it through would result in an invalid record being shared downstream.

**Independent Test**: Submit a draft hearing result containing a COEW or YROEW parent order with a CUR, CURE, YRC2, or YRC1 child requirement where the recorded end date does not equal `startDate + curfewPeriod - 1 day`. The validation service must return an ERROR issue carrying the rule ID, the error message, the calculated correct end date, and the affected offence.

**Acceptance Scenarios**:

1. **Given** a COEW parent result with a CUR child result where start date is 01/06/2026, curfew period is 60 days, and the recorded end date is 31/07/2026 (which is 60 days from start, not start + 60 - 1 = 30/07/2026), **When** the draft is validated, **Then** an ERROR is returned with message "The end date does not match the period entered. End date based on entered period: 30/07/2026".

2. **Given** a COEW parent result with a CURE child result where start date of tagging is 01/06/2026, curfew and electronic monitoring period is 90 days, and the recorded end date of tagging is 29/08/2026 (which equals start + 90 - 1 = 29/08/2026), **When** the draft is validated, **Then** no error is raised for this requirement.

3. **Given** a YROEW parent result with a YRC2 child result where start date is 01/06/2026, curfew period is 30 days, and the recorded end date is 01/07/2026 (which should be 30/06/2026), **When** the draft is validated, **Then** an ERROR is returned with the calculated correct end date 30/06/2026 in the error message.

4. **Given** a YROEW parent result with a YRC1 child result where start date of tagging is 10/07/2026, curfew and electronic monitoring period is 14 days, and the recorded end date of tagging is 23/07/2026 (which equals start + 14 - 1 = 23/07/2026), **When** the draft is validated, **Then** no error is raised.

5. **Given** two offences each with their own CUR child result — offence A has a correct end date and offence B has an incorrect end date — **When** the draft is validated, **Then** exactly one ERROR is raised, scoped to offence B only.

6. **Given** a YROEW order with both a YRC1 (end date correct) and a YRC2 (end date incorrect) child result on the same offence, **When** the draft is validated, **Then** one ERROR is raised for the YRC2 violation only.

7. **Given** two defendants where Defendant 1 has a CUR with a correct end date and Defendant 2 has a CUR with an incorrect end date, **When** the draft is validated, **Then** the error issue carries `affectedDefendants: [{ defendantId: "<Defendant 2 id>" }]` and Defendant 1 is not listed.

8. **Given** a result line with a missing or unparseable start date or period value, **When** the draft is validated, **Then** no error is raised for that requirement (the check is skipped gracefully) and a warning is logged.

---

### User Story 2 — Alcohol Abstinence and Monitoring Requirement End Date Does Not Match Duration (Priority: P1)

As a court clerk entering results, when I have added a Community Order (COEW, COS, CONI) and one of its requirements is an AAR (Alcohol Abstinence and Monitoring), the system must detect when the recorded "Until" date does not equal the hearing date plus the entered number of days to abstain minus one day — and block saving until the inconsistency is corrected.

**Why this priority**: The abstinence end date is derived from the number of days ordered and the hearing date. A mismatch indicates a data-entry error that would create an invalid legal record.

**Independent Test**: Submit a draft hearing result containing a COEW parent order with an AAR child requirement where the `until` date does not equal `hearingDate + numberOfDaysToAbstain - 1 day`. The validation service must return an ERROR issue carrying the rule ID, the error message, the calculated correct until date, and the affected offence.

**Acceptance Scenarios**:

1. **Given** a COEW parent result with an AAR child result where hearing date is 26/05/2026, number of days to abstain is 120, and the recorded "Until" date is 23/09/2026 (which equals hearing date + 120 - 1 = 23/09/2026), **When** the draft is validated, **Then** no error is raised for this requirement.

2. **Given** a COEW parent result with an AAR child result where hearing date is 26/05/2026, number of days to abstain is 120, and the recorded "Until" date is 24/09/2026 (one day more than the correct 23/09/2026), **When** the draft is validated, **Then** an ERROR is returned with message "The end date does not match the period entered. End date based on entered period: 23/09/2026".

3. **Given** a COS parent result with an AAR child result whose "Until" date is incorrect, **When** the draft is validated, **Then** an ERROR is returned scoped to the offence of the AAR result.

4. **Given** a YROEW parent result with an AAR child result whose "Until" date is incorrect, **When** the draft is validated, **Then** no error is raised — AAR validation applies to Community Orders only (COEW, COS, CONI), not to YROs.

5. **Given** an AAR result line with a missing or unparseable `numberOfDaysToAbstain` prompt, **When** the draft is validated, **Then** no error is raised for that requirement (the check is skipped gracefully) and a warning is logged.

6. **Given** a COEW order with an AAR requirement on two offences — one with a correct "Until" date and one with an incorrect "Until" date — **When** the draft is validated, **Then** one ERROR is raised scoped to the offence with the incorrect date only.

---

### User Story 3 — Error Response Format for Period-Mismatch Violations (Priority: P2)

As a front-end or consuming service developer, when the validation service detects a period-mismatch error (AC1 or AC2), I need the error response to carry a machine-readable message that includes the calculated correct end date — so the UI can display it inline below the "End date" / "End date of tagging" / "Until" label and also in the top-of-page error summary with a "There is a Problem" heading.

**Why this priority**: Without the calculated expected date in the error message, the UI cannot guide the clerk to the correct value. This story ensures the service produces the information needed for the inline error display pattern (AC3 in the user story).

**Independent Test**: Trigger any period-mismatch violation and inspect the `errors[].message` field in the response. It must contain the text "The end date does not match the period entered. End date based on entered period: " followed by the correct date in `DD/MM/YYYY` format.

**Acceptance Scenarios**:

1. **Given** a CUR violation where the calculated correct end date is 30/07/2026, **When** the validation service returns the error, **Then** `errors[].message` is "The end date does not match the period entered. End date based on entered period: 30/07/2026".

2. **Given** an AAR violation where the calculated correct end date is 23/09/2026, **When** the validation service returns the error, **Then** `errors[].message` is "The end date does not match the period entered. End date based on entered period: 23/09/2026".

3. **Given** any period-mismatch violation, **When** the validation service returns the error, **Then** `errors[].affectedOffences` contains the offence ID of the requirement that violated the check, and `errors[].affectedDefendants` contains the defendant ID of the defendant who has the violation.

4. **Given** any period-mismatch violation, **When** the validation service returns the error, **Then** `errors[].severity` is `ERROR` (blocking).

---

### Edge Cases

- What happens when the period unit is absent or unrecognised (not months, weeks, or days)? The check must be skipped gracefully with a warning log — no error raised, no exception.
- What happens when both start date and period are present but the period value is zero or negative? The check should be skipped (graceful skip with warning log) — zero or negative periods are an upstream data-entry issue outside this rule's scope.
- What happens when `hearingDay` is null on the request for an AAR rule evaluation? The check must be skipped gracefully for that requirement with a warning log — no NPE.
- What happens when a curfew requirement (CUR) appears without a parent community-order or YRO result line on the same offence? The rule must only fire when the requirement belongs to an appropriate parent order; if no qualifying parent is found, the check is skipped.
- What happens when multiple violations of the same type (e.g., two CUR requirements on two offences) exist for one defendant? Each violated offence must be reported in `affectedOffences`; one issue per violation offence must be emitted (not one consolidated issue per defendant).
- What happens when the period causes an overflow (e.g., extremely large number of days)? The check should be skipped gracefully with a warning log.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: For every CUR result line that is a child requirement of a CO or YRO parent on the same offence, the system MUST verify that `endDate` prompt value equals `startDate + period (applied in its declared unit of months, weeks, or days) - 1 day`; if not, an ERROR issue MUST be emitted.
- **FR-002**: For every CURE result line that is a child requirement of a CO or YRO parent on the same offence, the system MUST verify that `endDateOfTagging` equals `startDateOfTagging + period (applied in its declared unit) - 1 day`; if not, an ERROR issue MUST be emitted.
- **FR-003**: For every YRC2 result line (YRO-specific curfew) that is a child of a YRO parent, the system MUST apply the same unit-aware period check as FR-001 using the same prompt ref keys as CUR.
- **FR-004**: For every YRC1 result line (YRO-specific curfew with tag) that is a child of a YRO parent, the system MUST apply the same unit-aware period check as FR-002 using the same prompt ref keys as CURE.
- **FR-005**: For every AAR result line that is a child requirement of a CO parent only (COEW, COS, CONI — not YRO), the system MUST verify that the `until` prompt value equals `hearingDate + numberOfDaysToAbstain - 1 day`; if not, an ERROR issue MUST be emitted.
- **FR-006**: Each ERROR issue MUST carry a `message` of the form "The end date does not match the period entered. End date based on entered period: DD/MM/YYYY" where the date is the system-calculated correct end date.
- **FR-007**: Each ERROR issue MUST carry `affectedOffences` scoped to the specific offence whose requirement violated the check (one issue per offence per requirement type per defendant).
- **FR-008**: Each ERROR issue MUST carry `affectedDefendants: [{ defendantId }]` identifying the defendant whose result triggered the violation.
- **FR-009**: When a start date, period value, or hearing date is missing, blank, or unparseable, the system MUST skip the check for that requirement gracefully (no error raised, no uncaught exception) and log a warning via SLF4J.
- **FR-010**: The rule MUST be implemented as a new YAML rule file (`DR-COEW-002.yaml` or similar) and a new preprocessor — it MUST NOT extend or modify `DR-COEW-001` or `CommunityOrderEndDatePreprocessor`.
- **FR-011**: All severity overrides follow the existing ceiling model — the rule may be disabled or capped at WARNING at runtime via the `validation_rule` database table without code changes.
- **FR-012**: The upstream API library MUST be updated (or a new version consumed) to expose the nested `DURATION` prompt structure before this rule can be implemented. The preprocessor reads periods using two distinct patterns:
  - **DURATION** (CUR/YRC2 `curfewPeriod`, CURE/YRC1 `curfewAndElectronicMonitoringPeriod`): find the parent prompt by `promptRef`, then inspect the first child in its `value` array — child `promptRef` gives the unit (`"Days"` → `plusDays`, `"Weeks"` → `plusWeeks`, `"Months"` → `plusMonths`), child `value` gives the integer quantity.
  - **INT** (AAR `numberOfDaysToAbstainFromConsumingAnyAlcohol`): find the prompt by `promptRef`, read its `value` directly as an integer number of days; apply `plusDays`.

### Key Entities

- **Curfew Requirement**: A child result line with short code CUR, CURE, YRC2, or YRC1. Carries a start date prompt, a `DURATION`-typed period prompt, and an end date prompt. The period check compares the recorded end date against `startDate + period - 1 day`, where the unit and quantity are read from the `DURATION` prompt structure described below.
- **DURATION Prompt** (confirmed for CUR/YRC2 and CURE/YRC1): A nested prompt structure where the parent has `type: "DURATION"` and its `value` is an array of one child prompt. The child's `promptRef` identifies the unit (`"Days"`, `"Weeks"`, or `"Months"`) and the child's `value` is the integer quantity as a string. The date arithmetic applies `plusDays`, `plusWeeks`, or `plusMonths` according to the child `promptRef`, then subtracts one day. Confirmed promptRef keys: `"curfewPeriod"` (CUR, YRC2) and `"curfewAndElectronicMonitoringPeriod"` (CURE, YRC1). Example: `{ "promptRef": "curfewPeriod", "type": "DURATION", "value": [{ "promptRef": "Days", "type": "INT", "value": "21" }] }` → start date + 21 days − 1 day.
- **AAR Requirement**: A child result line with short code AAR under a CO parent (COEW, COS, CONI). Carries an `INT`-typed period prompt and an `until` date prompt. The period check compares the recorded `until` date against `hearingDate + numberOfDaysToAbstainFromConsumingAnyAlcohol - 1 day`. The period prompt is a flat structure (not DURATION): `{ "promptRef": "numberOfDaysToAbstainFromConsumingAnyAlcohol", "type": "INT", "value": "21" }` — the `value` is the integer number of days as a string, read directly (no child array to traverse).
- **Parent Order**: The top-level result line for the CO (COEW, COS, CONI) or YRO (YROEW, YRONI, YROFEW, YROISS, YROINI) on the same offence that the requirement belongs to. Used to determine which requirement types to validate.
- **Period-Mismatch Context**: The per-(defendant, offence) context record produced by the preprocessor, containing: the calculated expected end date (as a formatted string for the message template), the recorded actual end date, and the offence ID.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of submitted curfew requirement results (CUR, CURE, YRC2, YRC1) where the recorded end date does not match `startDate + period (in its declared unit of months, weeks, or days) - 1 day` are detected and returned as ERROR issues.
- **SC-002**: 100% of submitted AAR requirement results where the recorded "Until" date does not match `hearingDate + numberOfDaysToAbstain - 1 day` (under a CO parent) are detected and returned as ERROR issues.
- **SC-003**: Zero false positives — when the recorded end date exactly equals the calculated value, no error is raised.
- **SC-004**: Each ERROR issue message contains the correct calculated end date in `DD/MM/YYYY` format, enabling the UI to display it inline without additional computation.
- **SC-005**: Missing or unparseable prompt values produce zero unhandled exceptions and zero false-positive errors — the check is silently skipped per requirement.
- **SC-006**: The new validation rule is deployed without modifying any existing rule file, preprocessor, or Java class other than those introduced by this feature.

---

## Assumptions

- **Confirmed prompt structure for CUR / YRC2 period**: `promptRef: "curfewPeriod"`, `type: "DURATION"`, with a `value` array of one child prompt whose `promptRef` is the unit (`"Days"`, `"Weeks"`, or `"Months"`) and whose `value` is the integer quantity as a string. The unit for a given entry is always exactly one of these three — never mixed.
- **Confirmed prompt structure for CURE / YRC1 period**: `promptRef: "curfewAndElectronicMonitoringPeriod"`, `type: "DURATION"` — same nested child structure as `curfewPeriod` above. The date arithmetic is identical: `startDateOfTagging + period (in declared unit) - 1 day`. The prompt structure for AAR must be confirmed against the API contract during planning.
- **Data model dependency**: The current `Prompt` model in the upstream API library (`0.1.7`) exposes only `promptRef` and `promptValue` as simple strings. The nested `DURATION` prompt structure (parent with a `value` array of child prompts, each having `type`, `promptRef`, and `value`) is **not representable** in the current model. An upstream API library version bump will be required to expose this nested structure before the preprocessor can be implemented. This is a **blocking dependency** for implementation and must be resolved first in planning.
- **Confirmed start date promptRef keys**: `"startDate"` (CUR, YRC2) and `"startDateOfTagging"` (CURE, YRC1). Both carry an ISO-8601 date string as their `value` (same pattern as existing date prompts in the `0.1.6` model).
- **Confirmed prompt structure for AAR period**: `promptRef: "numberOfDaysToAbstainFromConsumingAnyAlcohol"`, `type: "INT"`, with `value` as a plain integer string (e.g. `"21"`). This is a flat prompt — no nested child array. The number represents calendar days, read directly and used in the calculation `hearingDate + value (days) - 1 day`.
- The hearing date (`request.getHearingDay()`) is already available in the `DraftValidationRequest` model (confirmed in spec 003 data model) and is used as the base date for the AAR calculation.
- This rule is a sibling to `DR-COEW-001` and must have a distinct rule ID. The ID `DR-COEW-002` is assumed; the exact ID is chosen during planning.
- The error message date format in the service response is `DD/MM/YYYY` (matching the existing UI date display convention). If the API contract specifies ISO-8601 (`YYYY-MM-DD`), the format is adjusted accordingly.
- Out of scope: AC3 from the user story (general inline error display pattern) is a UI rendering concern — the service already delivers error messages and affected-offence/defendant fields; the UI uses these to render inline errors and the top-of-page summary. No server-side change is needed for AC3.
- Out of scope: The user story's reference to "AC3 — The end date must be in the future" is a distinct rule not covered by this ticket (it is explicitly out of scope per the spec 003 scope note).
