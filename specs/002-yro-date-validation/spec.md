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

### Edge Cases

- What happens when multiple defendants each have a YRO, and only some have the end-date breach? Only the affected defendants should appear in the error; others should not be blocked.
- What happens when YRC1, YRC2, and YRC3 are all present and all breach the YRO end date? All three requirement names must appear in the AC2 error message.
- What happens when the YRO has no curfew child results at all? AC2 does not apply; no error is raised for AC2.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST detect when any YRO result (YROEW, YRONI, YROFEW, YROISS, YROINI) has an end date earlier than the end date of any linked YRC1, YRC2, or YRC3 child requirement.
- **FR-002**: When FR-001 is violated, the system MUST prevent "Save and Continue" navigation to Manage Hearings and display an error banner identifying each breaching requirement by its full display name.
- **FR-003**: For AC2 violations, the error banner MUST list all affected defendants by name in a "This affects: [name(s)]" section.
- **FR-004**: For AC2 violations, an inline error message MUST appear above the offence(s) affected, matching the summary error text.
- **FR-005**: When a sharing-blocking error exists, the Share button MUST NOT be visible on the Manage Hearings screen regardless of how the caseworker navigates there.
- **FR-006**: No error messages related to these rules MUST be displayed on the Manage Hearings screen itself — errors are surfaced only on the Enter Results screen.
- **FR-007**: Once a caseworker corrects all violations and saves successfully, the errors MUST clear and sharing MUST become available.
- **FR-008**: Validations MUST apply independently — a YRO can trigger multiple AC2 violations simultaneously, and all errors must be surfaced together.

### Requirement ↔ rule-condition mapping

| Functional requirement | Jira AC | DR-YRO-001 condition | CEL expression |
|---|---|---|---|
| FR-001 / FR-002 | AC2 | AC2a (YRC2) | `curViolationCount > 0` |
| FR-001 / FR-002 | AC2 | AC2b (YRC1) | `cureViolationCount > 0` |
| FR-001 / FR-002 | AC2 | AC2c (YRC3) | `curaViolationCount > 0` |

### Service scope vs. UI scope

This service is a stateless validation API: it emits ERROR/WARNING `ValidationIssue`s with affected
offences and defendants. The following requirements describe **frontend behaviour** and are owned by the
consuming UI, not this service — they are not implemented or tested here:

- **FR-002 / FR-004 / FR-012** (the "prevent Save and Continue / navigation" aspect), **FR-007** (hide
  the Share button), **FR-008** (no errors on Manage Hearings), **FR-009** (errors clear on re-save),
  and success criteria **SC-004 / SC-005**.

This service satisfies the *data contract* underpinning them: it returns the blocking errors (with
messages, affected offences, and affected defendants) that the UI consumes to drive that behaviour.

### Key Entities

- **YRO (Youth Rehabilitation Order)**: The parent result (shortCodes: YROEW, YRONI, YROFEW, YROISS, YROINI). Has a mandatory end date.
- **YRO Requirement**: A child result attached to the YRO. Relevant types for these rules:
  - YRC2 – Youth Rehabilitation Requirement: Curfew (has "End date")
  - YRC1 – Youth Rehabilitation Requirement: Curfew with electronic monitoring (has "End date of tag")
  - YRC3 – Youth Rehabilitation Requirement: Further curfew requirement made (has "End date")
- **Defendant**: A person charged. The error must identify which defendants are affected.
- **Validation issue**: A blocking ERROR that prevents sharing when triggered.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of YRO results where the order end date precedes any linked curfew requirement end date are flagged as errors before the result can be shared.
- **SC-002**: Caseworkers see the specific requirement name(s) causing the AC2 violation in the error message, enabling them to identify and fix the issue without additional guidance.
- **SC-003**: The Share button is suppressed on Manage Hearings whenever an unresolved AC2 error exists, with zero false negatives.
- **SC-004**: After correcting the violation and saving, caseworkers can proceed to share without needing to reload or re-navigate, with no residual error state.

## Assumptions

- AC2 applies only to YRC1, YRC2, and YRC3 child requirements. Other requirement types with date fields are out of scope for this rule.
- Both rules apply at the defendant level — a defendant is only flagged if their own YRO/requirement data violates the rule.
- The "End date of tag" field on YRC1 is treated identically to "End date" for the purposes of the AC2 comparison.
- The inline error message above the offence and the banner error message use the same text (as indicated in the acceptance criteria).
- Multiple defendants can be affected simultaneously; all affected defendant names appear in the "This affects:" list.
- These are ERROR-severity issues (blocking share), not warnings.
- The service validates on every "Save and Continue" attempt; there is no deferred or async validation.
