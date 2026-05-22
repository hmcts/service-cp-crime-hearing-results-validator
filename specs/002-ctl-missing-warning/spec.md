# Feature Specification: CTL Missing Warning

**Feature Branch**: `DD-41663-ctl-missing-warning`
**Created**: 2026-05-06
**Status**: Draft
**Input**: User description: "AC1 – No CTL result when relevant result added, offence not convicted and no CTL recorded against the offence"

## User Scenarios & Testing *(mandatory)*

### User Story 1 – Warning displayed for offence missing CTL (Priority: P1)

A caseworker has entered a remand-type result against an offence. The offence has no existing CTL record from a previous hearing, no CTL result in the current hearing, and the offence is not convicted. When the caseworker navigates to Manage Hearings (via "Save and continue" or the tab), they see a per-offence warning advising them that a CTL is absent and that it is their responsibility to confirm whether one is needed.

**Why this priority**: This is the core AC — without it the rule does not exist. The warning protects against overlooked CTL obligations when a defendant is remanded.

**Independent Test**: Can be fully tested by constructing a hearing with a single offence carrying a remand-type result and none of the bypass conditions, then calling the validation endpoint and asserting the warning is returned.

**Acceptance Scenarios**:

1. **Given** an offence has result short code `RI` (or any of `RIYDA`, `RIH`, `RIB`, `RILA`, `RILAB`, `REMYD`) in the current hearing, **And** the offence has no existing CTL record from a previous hearing, **And** the offence has no CTL result in the current hearing, **And** the offence is not convicted, **When** validation is triggered, **Then** a WARNING is returned for that offence with the message "This offence does not have a CTL. If the trial has started a CTL is not needed. It is your responsibility to check and confirm."
2. **Given** a hearing with multiple offences where only one meets all warning conditions, **When** validation is triggered, **Then** the warning appears only on the breaching offence and not on the compliant offences.

---

### User Story 2 – Warning suppressed when any bypass condition is satisfied (Priority: P2)

A caseworker enters a remand-type result but one of the four bypass conditions is true. In each case no warning should appear for that offence.

**Why this priority**: Avoiding false positives is critical — unnecessary warnings erode trust and cause unnecessary rework.

**Independent Test**: Can be fully tested by running four separate validation requests, each satisfying exactly one bypass condition, and asserting no warning is returned in any case.

**Acceptance Scenarios**:

1. **Given** an offence has a remand-type result **And** an existing CTL record is already recorded against the offence from a previous hearing, **When** validation is triggered, **Then** no warning is produced for that offence.
2. **Given** an offence has a remand-type result **And** a CTL result is recorded against that offence in the current hearing, **When** validation is triggered, **Then** no warning is produced for that offence.
3. **Given** an offence has a remand-type result **And** the offence is convicted (guilty plea, finding of guilt, or date of conviction recorded), **When** validation is triggered, **Then** no warning is produced for that offence.
4. **Given** an offence has only non-remand results (none of `RI`, `RIYDA`, `RIH`, `RIB`, `RILA`, `RILAB`, `REMYD`), **When** validation is triggered, **Then** no warning is produced for that offence.

---

### User Story 3 – Warning is advisory only; sharing is not blocked (Priority: P3)

When a warning is raised the caseworker can still share results without making any changes. The warning is informational, not a blocking error.

**Why this priority**: The business explicitly requires that the warning does not block sharing — it is the caseworker's responsibility to decide whether a CTL is needed.

**Independent Test**: Can be tested by asserting the validation response carries the issue at severity WARNING (not ERROR) and that no blocking outcome is set.

**Acceptance Scenarios**:

1. **Given** the warning condition is met, **When** validation runs, **Then** the issue is returned at severity WARNING, not ERROR.
2. **Given** the warning is present, **When** the caseworker chooses to share without remediation, **Then** the share action is not blocked by this validation rule.

---

### Edge Cases

- What happens when an offence has both a trigger result (e.g. `RI`) and a CTL result in the same hearing? → No warning (CTL result bypass applies).
- What happens when every offence in the hearing meets the warning condition? → A warning is produced for each offence independently.
- What happens when the same offence has multiple result lines, some triggering and some not? → The trigger check is satisfied if any result line carries a trigger short code; bypass checks operate at the offence level.
- What happens when the offence has no results at all? → No trigger result is present; no warning is produced.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST recognise the following result short codes as remand-type trigger results: `RI`, `RIYDA`, `RIH`, `RIB`, `RILA`, `RILAB`, `REMYD`.
- **FR-002**: For each offence, the system MUST check whether at least one result line carries a trigger short code (FR-001). If none, no further evaluation is performed for that offence.
- **FR-003**: For each offence that has a trigger result, the system MUST check whether an existing CTL record is already associated with that offence from a previous hearing. If one exists, no warning is produced for that offence.
- **FR-004**: For each offence that has a trigger result and no existing CTL record, the system MUST check whether the current hearing includes a CTL result (short code `CTL`) against that offence. If one exists, no warning is produced for that offence.
- **FR-005**: For each offence that has a trigger result, no existing CTL record, and no CTL result in the current hearing, the system MUST check whether the offence is convicted (guilty plea, finding of guilt, or date of conviction recorded). If it is convicted, no warning is produced for that offence.
- **FR-006**: When all four conditions in FR-002 through FR-005 indicate the warning state, the system MUST produce a WARNING issue for that offence.
- **FR-007**: The warning message text MUST be exactly: "This offence does not have a CTL. If the trial has started a CTL is not needed. It is your responsibility to check and confirm."
- **FR-008**: The warning MUST be scoped to the individual offence that breaches the rule; other offences in the same hearing MUST NOT be affected.
- **FR-009**: The warning issue MUST carry severity WARNING, not ERROR, so that it does not block result sharing.

### Key Entities

- **Offence**: A charge in the hearing. Has an identifier, a list of result lines for the current hearing, a conviction status (convicted / not convicted), and an indicator of whether an existing CTL record is associated from a previous hearing.
- **Result line**: A recorded outcome on an offence. Carries a short code (e.g. `RI`, `CTL`) that identifies the type of result.
- **Existing CTL record**: A custody time limit record associated with the offence from a prior hearing. Distinct from a CTL result entered in the current hearing.
- **Conviction status**: Whether the offence has a guilty plea, a finding of guilt, or a recorded date of conviction.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every offence satisfying all four warning conditions receives the exact prescribed warning message — zero omissions across all test scenarios.
- **SC-002**: No false positives: offences where any single bypass condition is satisfied produce zero warnings — validated across at least four distinct bypass scenarios.
- **SC-003**: The warning is returned at WARNING severity in 100% of triggered cases, never at ERROR severity.
- **SC-004**: Validation results for a multi-offence hearing correctly isolate the warning to breaching offences only, with no spillover to non-breaching offences.
- **SC-005**: Result sharing is not blocked when the only issues present are warnings from this rule.

## Assumptions

- **Upstream API change required (blocker)**: The `existingCtlRecord` indicator (whether a CTL was recorded at a prior hearing) and/or the `convicted` / conviction-status field are not currently present on the offence object in `DraftValidationRequest`. These fields must be added to the upstream API contract (`api-cp-crime-hearing-results-validator`) and published before this validation rule can be implemented. The rule is blocked on that upstream work.
- The validation service evaluates this rule independently for each offence in the hearing; there are no cross-offence dependencies.
- The warning is produced at the point of validation (when the user navigates to Manage Hearings), not at the point of entering results.
- The rule applies regardless of offence type or court type — no filtering by offence code is required for this rule.
- Multiple defendants sharing the same offence are not a concern for this rule — the rule operates at the offence level, not per-defendant.
