# Feature Specification: YRO Date Validation

**Feature Branch**: `DD-41654-yro-date-validation`
**Created**: 2026-05-27
**Status**: Draft
**Jira**: DD-41654

## User Scenarios & Testing *(mandatory)*

### User Story 1 – YRO end date must not be earlier than any curfew requirement end date (Priority: P1)

A caseworker entering results has added a Youth Rehabilitation Order (YRO) to one or more offences and has also recorded a curfew-type requirement with its own end date. If the YRO's own end date falls before the requirement's end date, the system must prevent sharing and surface a clear error.

**Why this priority**: Prevents legally invalid sentencing data from being shared. A YRO cannot expire before one of its active requirements.

**Independent Test**: Can be fully tested by submitting a draft hearing result where a YRO (e.g. YROEW) has an end date of 30/10/2026 and a linked Curfew requirement (YRC2) has an end date of 30/11/2026, then attempting Save and Continue. The system should block navigation and display the error.

**Acceptance Scenarios**:

1. **Given** a YRO result (YROEW, YRONI, YROFEW, YROISS, or YROINI) is linked to one or more offences with all mandatory fields completed, **and** one or more of its child requirements is YRC1, YRC2, or YRC3 with an "End date" or "End date of tag" recorded, **and** the YRO end date is earlier than the end date of at least one such requirement, **When** the caseworker selects "Save and Continue" to navigate to "Manage Hearings", **Then** the caseworker remains on the Enter Results screen, an error banner reads "There is a Problem — The end date of the order must match or be longer than the end date of [requirement name(s)]", the specific breaching requirement(s) are listed, and the affected defendants are listed beneath ("This affects: [defendant name(s)]").

2. **Given** the same setup as above, **When** the caseworker navigates directly to the "Manage Hearings" tab, **Then** the Share button is not visible and no error is shown on that screen.

3. **Given** the YRO end date equals the end date of the requirement, **When** the caseworker selects "Save and Continue", **Then** no error is raised and navigation proceeds normally.

4. **Given** multiple requirements all breach the rule simultaneously, **When** the caseworker selects "Save and Continue", **Then** all breaching requirement names appear in the error message.

5. **Given** the error is displayed, **When** the caseworker corrects the YRO end date so it is on or after all requirement end dates and saves again, **Then** the error clears and sharing becomes available.

---

### User Story 2 – YRO end date must be at least 12 months when an unpaid work requirement is included (Priority: P2)

A caseworker has added a YRO with an "Unpaid Work" requirement (YRUP1). The YRO end date must be at least 12 months from the hearing date. If it falls short by even one day, the system must block sharing and surface a clear error.

**Why this priority**: Mandatory sentencing law requires a minimum 12-month order duration when unpaid work is imposed. Blocking sharing enforces statutory compliance before a result is communicated.

**Independent Test**: Can be fully tested by submitting a draft where the hearing date is 20/05/2026, the YRO includes YRUP1, and the YRO end date is 18/05/2027 (less than 12 months). The system should block navigation and display the error. Setting the end date to 20/05/2027 (exactly 12 months) should pass.

**Acceptance Scenarios**:

1. **Given** a YRO result (YROEW, YRONI, YROFEW, YROISS, or YROINI) includes the YRUP1 (Youth rehabilitation requirement: Unpaid work) child result, **and** the YRO end date is less than 12 months from the hearing date, **When** the caseworker selects "Save and Continue", **Then** the caseworker remains on the Enter Results screen, an error banner reads "There is a Problem — The end date of the order must be at least 12 months as it includes an unpaid work requirement", the affected defendants are listed beneath ("This affects: [defendant name(s)]"), and the inline message above the offence reads "The end date of the order must be at least 12 months".

2. **Given** the same setup, **When** the caseworker navigates to the "Manage Hearings" tab directly, **Then** the Share button is not visible and no error is shown on that screen.

3. **Given** the YRO end date is exactly 12 months from the hearing date, **When** the caseworker selects "Save and Continue", **Then** no error is raised and navigation proceeds normally.

4. **Given** the error is displayed, **When** the caseworker updates the end date to be at least 12 months from the hearing date and saves again, **Then** the error clears and sharing becomes available.

---

### Edge Cases

- What happens when multiple defendants each have a YRO, and only some have the end-date breach? Only the affected defendants should appear in the error; others should not be blocked.
- What happens when a YRO has both a curfew requirement breach (AC2) and an unpaid work duration breach (AC3) simultaneously? Both errors should be surfaced independently.
- What happens when YRC1, YRC2, and YRC3 are all present and all breach the YRO end date? All three requirement names must appear in the AC2 error message.
- What constitutes "12 months"? The boundary condition is hearing date + 365 days (or + 366 in a leap year). "Less than 12 months - 1 day" means strictly before the anniversary date.
- What happens when the YRO has no curfew child results at all? AC2 does not apply; no error is raised for AC2.
- What happens when the YRO has no YRUP1 child result? AC3 does not apply; no error is raised for AC3.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST detect when any YRO result (YROEW, YRONI, YROFEW, YROISS, YROINI) has an end date earlier than the end date of any linked YRC1, YRC2, or YRC3 child requirement.
- **FR-002**: When FR-001 is violated, the system MUST prevent "Save and Continue" navigation to Manage Hearings and display an error banner identifying each breaching requirement by its full display name.
- **FR-003**: The system MUST detect when any YRO result includes a YRUP1 child requirement and the YRO end date is strictly less than 12 months from the hearing date.
- **FR-004**: When FR-003 is violated, the system MUST prevent "Save and Continue" navigation and display an error banner stating the minimum 12-month requirement.
- **FR-005**: For both AC2 and AC3 violations, the error banner MUST list all affected defendants by name in a "This affects: [name(s)]" section.
- **FR-006**: For both AC2 and AC3 violations, an inline error message MUST appear above the offence(s) affected, matching the summary error text.
- **FR-007**: When a sharing-blocking error exists, the Share button MUST NOT be visible on the Manage Hearings screen regardless of how the caseworker navigates there.
- **FR-008**: No error messages related to these rules MUST be displayed on the Manage Hearings screen itself — errors are surfaced only on the Enter Results screen.
- **FR-009**: Once a caseworker corrects all violations and saves successfully, the errors MUST clear and sharing MUST become available.
- **FR-010**: Validations MUST apply independently — a YRO can trigger both AC2 and AC3 simultaneously, and both errors must be surfaced together.

### Key Entities

- **YRO (Youth Rehabilitation Order)**: The parent result (shortCodes: YROEW, YRONI, YROFEW, YROISS, YROINI). Has a mandatory end date.
- **YRO Requirement**: A child result attached to the YRO. Relevant types for these rules:
  - YRC2 – Youth Rehabilitation Requirement: Curfew (has "End date")
  - YRC1 – Youth Rehabilitation Requirement: Curfew with electronic monitoring (has "End date of tag")
  - YRC3 – Youth Rehabilitation Requirement: Further curfew requirement made (has "End date")
  - YRUP1 – Youth Rehabilitation Requirement: Unpaid work
- **Hearing date**: The date of the hearing session from which the 12-month minimum is calculated for AC3.
- **Defendant**: A person charged. The error must identify which defendants are affected.
- **Validation issue**: A blocking ERROR that prevents sharing when triggered.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of YRO results where the order end date precedes any linked curfew requirement end date are flagged as errors before the result can be shared.
- **SC-002**: 100% of YRO results containing YRUP1 where the order end date is less than 12 months from the hearing date are flagged as errors before the result can be shared.
- **SC-003**: Caseworkers see the specific requirement name(s) causing the AC2 violation in the error message, enabling them to identify and fix the issue without additional guidance.
- **SC-004**: The Share button is suppressed on Manage Hearings whenever an unresolved AC2 or AC3 error exists, with zero false negatives.
- **SC-005**: After correcting the violation and saving, caseworkers can proceed to share without needing to reload or re-navigate, with no residual error state.

## Assumptions

- The hearing date used for the AC3 12-month calculation is the date of the hearing session as stored in the draft validation request, not the date the result was entered.
- "12 months" is calculated as a calendar date anniversary (e.g. 20/05/2026 → 20/05/2027). A YRO end date of exactly 20/05/2027 passes; 19/05/2027 fails.
- AC2 applies only to YRC1, YRC2, and YRC3 child requirements. Other requirement types with date fields are out of scope for this rule.
- AC3 applies only when YRUP1 is present as a child result; the check is not triggered by any other requirement type.
- Both rules apply at the defendant level — a defendant is only flagged if their own YRO/requirement data violates the rule.
- The "End date of tag" field on YRC1 is treated identically to "End date" for the purposes of the AC2 comparison.
- The inline error message above the offence and the banner error message use the same text (as indicated in the acceptance criteria).
- Multiple defendants can be affected simultaneously; all affected defendant names appear in the "This affects:" list.
- These are ERROR-severity issues (blocking share), not warnings.
- The service validates on every "Save and Continue" attempt; there is no deferred or async validation.
