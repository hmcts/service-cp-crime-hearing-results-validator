# Feature Specification: YRO Curfew Requirement Duration Validation

**Feature Branch**: `dev/DD-42850-YRO-Duration`
**Created**: 2026-07-20
**Status**: Draft
**Jira**: DD-42850
**Input**: User description: "As a user with access to enter results, I want the system to check the end date of a requirement so that common errors are avoided and the need to make amendments and reshare are reduced. AC1 — YRO Curfew Requirement (YRC2) end date must equal Start date + Curfew period − 1 day. AC1A — YRO Curfew with electronic monitoring Requirement (YRC1) End date of tagging must equal Start date of tagging + Curfew and electronic monitoring period − 1 day."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Curfew Requirement End Date Must Match Calculated Duration (Priority: P1)

As a caseworker entering results, when I add a Youth Rehabilitation Order (YROEW, YRONI, YROFEW, YROISS, or YROINI) result with a Curfew requirement (YRC2) and select "Save and continue", the system must detect if the requirement's "End date" does not equal "Start date" + "Curfew period" − 1 day, and block navigation to Manage Hearings until I correct it.

**Why this priority**: An end date that doesn't match the recorded curfew period is a data-entry error that produces an inconsistent requirement duration in downstream systems. Catching it at the point of entry avoids the need for amendments and reshares.

**Independent Test**: Add a YRC2 child result with a Start date, a Curfew period, and an End date that does not equal Start date + period − 1 day, then select "Save and continue". Navigation must be blocked and the error must name the Curfew Requirement.

**Acceptance Scenarios**:

1. **Given** a YRC2 child result with Start date 01/09/2026 and Curfew period 21 days, **When** the End date entered does not equal 21/09/2026 (Start date + period − 1 day) and the caseworker selects "Save and continue", **Then** the caseworker remains on the Enter Results screen, is scrolled to the top, and sees an error summary headed "There is a Problem" reading "The end date for the Curfew Requirement does not match the period of the requirement." with "This affects: <<defendant name(s)>>", and an inline error below the affected offence reads "The end date for the Curfew Requirement does not match the period of the requirement. The current recorded period would mean the end date should be 21/09/2026."
2. **Given** a YRC2 child result where the End date exactly equals Start date + Curfew period − 1 day, **When** the caseworker selects "Save and continue", **Then** no error is raised for this requirement and navigation proceeds normally.
3. **Given** a YRC2 duration-mismatch error exists, **When** the caseworker navigates to the Manage Hearings tab directly, **Then** the Share button is not visible and no validation error is shown on the Manage Hearings screen.
4. **Given** the error is displayed, **When** the caseworker corrects the End date to match the calculated value and saves again, **Then** the error clears and sharing becomes available.

---

### User Story 2 — Curfew with Electronic Monitoring Requirement "End Date of Tagging" Must Match Calculated Duration (Priority: P1)

As a caseworker entering results, when I add a Youth Rehabilitation Order result with a Curfew with electronic monitoring requirement (YRC1) and select "Save and continue", the system must detect if "End date of tagging" does not equal "Start date of tagging" + "Curfew and electronic monitoring period" − 1 day, and block navigation until corrected.

**Why this priority**: Same class of data-entry inconsistency as User Story 1, applied to the electronically-monitored curfew variant, which uses its own distinct field names.

**Independent Test**: Add a YRC1 child result with a Start date of tagging, a Curfew and electronic monitoring period, and an End date of tagging that does not match, then select "Save and continue". Navigation must be blocked.

**Acceptance Scenarios**:

1. **Given** a YRC1 child result with Start date of tagging 01/09/2026 and Curfew and electronic monitoring period 60 days, **When** the End date of tagging entered does not equal 30/10/2026 (Start date of tagging + period − 1 day) and the caseworker selects "Save and continue", **Then** the caseworker remains on the Enter Results screen, sees the error summary "There is a Problem" reading "The end date for the Curfew Requirement does not match the period of the requirement." with "This affects: <<defendant name(s)>>", and the inline error states the correct end date should be 30/10/2026.
2. **Given** a YRC1 child result where End date of tagging exactly equals Start date of tagging + period − 1 day, **When** the caseworker selects "Save and continue", **Then** no error is raised for this requirement.
3. **Given** a YRC1 duration-mismatch error exists, **When** the caseworker navigates to Manage Hearings directly, **Then** the Share button is not visible and no validation error is shown on that screen.
4. **Given** the error is displayed, **When** the caseworker corrects the End date of tagging to match the calculated value and saves again, **Then** the error clears and sharing becomes available.

---

### User Story 3 — Duration-Mismatch Errors Combine With Other Validation Errors in a Single Summary (Priority: P2)

As a caseworker, when my entered results trigger duration-mismatch errors (User Stories 1–2) together with each other, with the existing YRO order-end-date check, or with validation rules from other features, I need to see every triggered error together in one place so I can resolve them all before sharing.

**Why this priority**: Confirms the new duration-mismatch conditions integrate correctly with the existing error-summary and inline-error display mechanism; it does not introduce new UI.

**Independent Test**: Trigger a YRC2 duration mismatch and a YRC1 duration mismatch simultaneously (or a duration mismatch together with the existing YRO order-end-date violation), select "Save and continue", and verify every error appears in the top summary and inline, each with its own correct "This affects:" list.

**Acceptance Scenarios**:

1. **Given** a hearing where one defendant has a YRC2 duration mismatch and another has a YRC1 duration mismatch, **When** the caseworker selects "Save and continue", **Then** both errors appear in the top summary, each with its own "This affects:" line naming only the relevant defendant, and navigation is blocked.
2. **Given** a hearing where a single defendant's YRO simultaneously fails the existing order-end-date check and a YRC2 duration-mismatch check, **When** the caseworker selects "Save and continue", **Then** both errors are shown together in the summary and inline against the same offence, and the caseworker cannot share until both are resolved.
3. **Given** no duration-mismatch or other validation errors are triggered, **When** the caseworker selects "Save and continue", **Then** the caseworker navigates to Manage Hearings and the Share button is visible.

---

### Edge Cases

- What happens when the Curfew period, or the Curfew and electronic monitoring period, is 0 or missing? Mandatory field validation is expected to catch missing values before "Save and continue"; the duration-mismatch check does not fire without a period value.
- What happens when the Start date (or Start date of tagging) is missing? Caught by mandatory field validation before the duration-mismatch check runs.
- What happens when the recorded end date equals Start date (or Start date of tagging) + period, without subtracting 1 day? This is flagged as an error — the "− 1 day" convention is part of the expected calculation, and this off-by-one is a common clerical mistake the rule is designed to catch.
- What happens when a single YRO has more than one YRC1 or YRC2 requirement attached? Each requirement instance is validated independently; each mismatch produces its own error scoped to that specific requirement and offence.
- What happens when a requirement fails both the existing order-end-date check (YRO end date vs. requirement end date, DR-YRO-001 AC2) and its own duration-mismatch check (User Stories 1–2) at the same time? Both errors are shown — one per violated rule — against the same offence, per User Story 3.
- What happens when multiple defendants have duration-mismatch errors alongside valid defendants? Only the defendants with each specific error type appear in that error's "This affects:" list; valid defendants are never listed.
- What happens when the YRC3 (Further curfew requirement made) requirement is present? Out of scope for this ticket — only YRC1 and YRC2 duration is validated (see Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST detect, at "Save and continue", when a Curfew requirement (YRC2) child result's "End date" does not equal "Start date" + "Curfew period" (in days) − 1 day.
- **FR-002**: System MUST detect, at "Save and continue", when a Curfew with electronic monitoring requirement (YRC1) child result's "End date of tagging" does not equal "Start date of tagging" + "Curfew and electronic monitoring period" (in days) − 1 day.
- **FR-003**: For FR-001 and FR-002 violations, the validation service MUST emit a `ValidationIssue` (severity ERROR) with message "The end date for the Curfew Requirement does not match the period of the requirement."
- **FR-004**: The inline (per-offence) rendering of each FR-003 message MUST additionally state the correctly calculated date, e.g. "The current recorded period would mean the end date should be <<calculated date>>", substituting the actual Start date (or Start date of tagging) + period − 1 day.
- **FR-005**: Each duration-mismatch `ValidationIssue` MUST include the affected defendants so the UI can render "This affects: <<defendant name(s)>>" beneath the error summary entry.
- **FR-006**: FR-001/FR-002 violations MUST reuse the existing error summary, inline error, and Share-button suppression behaviour already defined for YRO result validation (DR-YRO-001) — no new display mechanism is introduced.
- **FR-007**: When a duration-mismatch violation (FR-001/FR-002) and the existing order-end-date violation both apply to the same requirement/offence, both MUST appear as separate entries in the error summary and inline.
- **FR-008**: Validations MUST apply independently per requirement instance — a YRO with multiple YRC1/YRC2 requirements can trigger multiple duration-mismatch violations simultaneously, and all errors must be surfaced together.
- **FR-009**: Once a caseworker corrects all violations and saves successfully, the errors MUST clear and sharing MUST become available.

### Requirement ↔ rule-condition mapping

| Functional requirement | Jira AC | Condition | Trigger |
|---|---|---|---|
| FR-001 / FR-003 / FR-004 | AC1 | Curfew (YRC2) duration mismatch | End date ≠ Start date + Curfew period − 1 day |
| FR-002 / FR-003 / FR-004 | AC1A | Curfew with electronic monitoring (YRC1) duration mismatch | End date of tagging ≠ Start date of tagging + Curfew and electronic monitoring period − 1 day |

### Service scope vs. UI scope

This service is a stateless validation API: it emits ERROR `ValidationIssue`s with affected
offences and defendants. The following requirements describe **frontend behaviour** and are owned by the
consuming UI, not this service — they are not implemented or tested here:

- The "remain on Enter Results / scroll to top" navigation behaviour, hiding the Share button on
  Manage Hearings, and clearing the error display after a successful re-save.

This service satisfies the *data contract* underpinning them: it returns the blocking errors (with
messages, affected offences, calculated end dates, and affected defendants) that the UI consumes to
drive that behaviour.

### Key Entities

- **YRO (Youth Rehabilitation Order)**: The parent result (shortCodes: YROEW, YRONI, YROFEW, YROISS, YROINI).
- **Curfew Requirement (YRC2)**: A child result attached to the YRO. Has a "Start date", a "Curfew period" (whole number of days), and an "End date" that must equal Start date + period − 1 day.
- **Curfew with Electronic Monitoring Requirement (YRC1)**: A child result attached to the YRO. Has a "Start date of tagging", a "Curfew and electronic monitoring period" (whole number of days), and an "End date of tagging" that must equal Start date of tagging + period − 1 day.
- **Defendant**: A person charged. The error must identify which defendants are affected.
- **Validation issue**: A blocking ERROR that prevents sharing when triggered, carrying the message text, the calculated correct end date, the affected offences, and the affected defendants.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of YRC2 and YRC1 requirements whose recorded end date does not equal Start date (or Start date of tagging) + period − 1 day are blocked at "Save and continue" with the correct requirement-specific error message and calculated correct date — zero missed violations, zero false positives when the dates match exactly.
- **SC-002**: Caseworkers see the correctly calculated end date in the error message, enabling them to fix the issue without additional guidance.
- **SC-003**: The Share button is suppressed on Manage Hearings whenever an unresolved duration-mismatch error exists, with zero false negatives.
- **SC-004**: After correcting the violation and saving, caseworkers can proceed to share without needing to reload or re-navigate, with no residual error state.
- **SC-005**: All acceptance scenarios in this spec pass automated regression tests without changing the pass/fail outcome of any existing YRO validation scenario (no regression on the already-shipped order-end-date check, DR-YRO-001).

## Assumptions

- The "− 1 day" convention is deliberate: recorded end date = start date + period, minus 1 day. An off-by-one that omits the subtraction is a data-entry error the rule is designed to catch.
- "Curfew period" and "Curfew and electronic monitoring period" are recorded as whole numbers of days (not weeks/months).
- Duration-mismatch dates are compared as calendar dates with no time-of-day component.
- This ticket covers YRC2 and YRC1 duration validation only. YRC3 (Further curfew requirement made) is out of scope — it is not mentioned in the Jira acceptance criteria for this ticket, unlike the existing order-end-date check (DR-YRO-001) which does cover it.
- The duration-mismatch check (this ticket) is independent of and additive to the existing YRO order-end-date-vs-requirement-end-date check (DR-YRO-001, AC2a/AC2b). A single requirement may trigger both, one, or neither, depending on its data.
- Multiple requirement violations across a hearing each produce a separate `ValidationIssue` — one per violated requirement instance — consistent with the existing per-condition YAML+CEL evaluation model.
- The validation service operates on draft hearing result data and does not modify or persist the hearing record.
- Short codes (YROEW, YRONI, YROFEW, YROISS, YROINI, YRC1, YRC2) are already defined in the result code dictionary and are not modified by this feature.
- UI error display patterns (red box, bold heading, vertical border for inline errors) align with the existing GOV.UK Design System error summary and error message components already in use in the Enter Results screen.
- This feature follows the same implementation pattern as the equivalent Community Order requirement-duration validation (Curfew/CUR and Curfew with electronic monitoring/CURE duration checks) delivered in the sibling `service-cp-crime-hearing-results-validator` (Community Order) codebase.
