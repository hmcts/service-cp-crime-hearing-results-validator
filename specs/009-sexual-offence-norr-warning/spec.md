# Feature Specification: Sexual Offence Notification Requirement Warning

**Feature Branch**: `009-sexual-offence-norr-warning`
**Created**: 2026-08-25
**Status**: Draft
**Input**: User description: "AC1 — convicted relevant sexual offence (mis_code = SEX) with no NORRR notification-requirement result recorded, Adult defendant (18+) — offence-level WARNING; AC1A — same but Youth defendant (under 18), cleared by either NORRR or NORPGP — offence-level WARNING with youth-specific wording; AC2 — offence-level and defendant-level warnings from this and other validation rules are all shown together in Manage Hearings, sharing is never blocked by warnings."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Convicted sexual offence missing notification requirement result - Adult (Priority: P1)

A Legal Adviser or Court Clerk enters a result against a relevant sexual offence (an offence classified with mis_code "SEX" in offence reference data) charged against a defendant who is 18 or older at the date of hearing. The offence is convicted (guilty plea or guilty verdict), but no result recording the sexual offences notification requirement (short code `NORRR`) has been entered for that offence. When the user saves and continues to Manage Hearings, they see an offence-level warning telling them the notification requirement result is missing, so they can check whether it is needed before sharing.

**Why this priority**: This is the core adult-facing AC — without it, convicted sexual offences can be shared with no reminder to consider the statutory notification requirement, which is a safeguarding-significant gap.

**Independent Test**: Can be fully tested by constructing a hearing with a single relevant sexual offence, a convicted result, a defendant aged 18+ at the hearing date, and no `NORRR` result line, then calling the validation endpoint and asserting the warning is returned.

**Acceptance Scenarios**:

1. **Given** the defendant is 18 years of age or older at the date of hearing, **And** the offence is a relevant sexual offence (mis_code "SEX"), **And** the offence is convicted (e.g. guilty plea, "Found guilty" verdict), **And** the offence does not have a `NORRR` result recorded, **When** the user selects "Save and continue" and is navigated to Manage Hearings, **Then** an offence-level WARNING is generated for that offence reading "This offence does not have a sexual offences notification requirement (NORRR). Check if this is required before sharing", shown above the offence and aligned with the entered result, and the share button remains available.
2. **Given** the warning from Scenario 1 is displayed, **When** the user chooses not to make any changes, **Then** they can still share the result.
3. **Given** a hearing has multiple relevant sexual offences and only some are missing `NORRR`, **When** validation runs, **Then** the warning is shown above each breaching offence individually and not on offences that already have a `NORRR` result.

---

### User Story 2 - Convicted sexual offence missing notification requirement result - Youth (Priority: P1)

The same scenario as User Story 1, but the defendant is under 18 at the date of hearing. Because a youth notification requirement can be recorded against the defendant alone (`NORRR`) or against the parent and defendant (`NORPGP`), the offence is only missing its notification requirement if neither code has been recorded, and the warning wording reflects both options.

**Why this priority**: Youth cases require a different set of qualifying result codes and different wording; treating them the same as adult cases would either miss valid youth notification results or use adult-only wording, which is why this is tracked as an equally critical, independently testable path.

**Independent Test**: Can be fully tested by constructing a hearing with a relevant, convicted sexual offence, a defendant under 18 at the hearing date, and neither a `NORRR` nor a `NORPGP` result line, then asserting the youth-specific warning is returned; and separately asserting no warning is returned when either code is present.

**Acceptance Scenarios**:

1. **Given** the defendant is under 18 years of age at the date of hearing, **And** the offence is a relevant sexual offence (mis_code "SEX"), **And** the offence is convicted, **And** the offence has neither a `NORRR` nor a `NORPGP` result recorded, **When** the user selects "Save and continue" and is navigated to Manage Hearings, **Then** an offence-level WARNING is generated for that offence reading "This offence does not have a sexual offences notification requirement (NORRR - defendant or NORPGP - parent and defendant). Check if this is required before sharing", shown above the offence and aligned with the entered result, and the share button remains available.
2. **Given** the same offence instead has a `NORRR` result, a `NORPGP` result, or both, **When** validation runs, **Then** no warning is generated for that offence.
3. **Given** the warning from Scenario 1 is displayed, **When** the user chooses not to make any changes, **Then** they can still share the result.

---

### User Story 3 - Warnings shown together, sharing never blocked (Priority: P2)

A user's entered results trigger a mix of offence-level and defendant-level warnings — some from the notification-requirement rule in User Stories 1 and 2, and some from other, unrelated validation rules. When the user saves and continues to Manage Hearings, every triggered warning is shown, none are dropped or hidden by the presence of others, and the user can still choose to share without making any changes.

**Why this priority**: Confirms that adding this new warning does not suppress, replace, or interfere with warnings raised by other rules, and that the combined presence of multiple warnings still leaves sharing under the user's control. This depends on User Stories 1 and 2 existing, so it is sequenced after them.

**Independent Test**: Can be tested by constructing a hearing where this rule's condition and at least one other rule's condition are both met (producing at least one offence-level and one defendant-level warning), then asserting the validation response contains every triggered warning and that sharing is not blocked.

**Acceptance Scenarios**:

1. **Given** entered results trigger both offence-level and defendant-level warnings from a combination of this rule and other validation rules, **When** the user selects "Save and continue" and is navigated to Manage Hearings, **Then** all triggered warnings are shown together, with all defendant-level warnings visible and all offence-level warnings visible.
2. **Given** more than one defendant-level warning is triggered, **When** validation runs, **Then** every one of them is shown, not just the first.
3. **Given** more than one offence-level warning is triggered, **When** validation runs, **Then** every one of them is shown, not just the first.
4. **Given** any combination of warnings is shown, **When** the user chooses not to make any changes, **Then** they can still share the results.

---

### Edge Cases

- What happens when the offence is a relevant sexual offence but is not convicted? → No warning; conviction status is a precondition.
- What happens when the offence is convicted but is not a relevant sexual offence (mis_code is not "SEX")? → No warning; the rule only evaluates relevant sexual offences.
- What happens when the defendant is exactly 18 years old on the date of hearing? → Treated as Adult (the `NORRR`-only requirement applies).
- What happens when the defendant's date of birth is not available? → The defendant is treated as Adult (fail-safe default), so the `NORRR`-only requirement applies rather than the check being skipped.
- What happens when an offence has both a `NORRR` and a non-qualifying result recorded? → No warning; the presence of a qualifying code is sufficient regardless of other results on the offence.
- What happens with result short-code casing (e.g. `norrr` vs `NORRR`)? → Matching is case-insensitive, consistent with existing short-code checks in this service.
- What happens when a hearing has multiple defendants of different ages, each with their own relevant sexual offence? → Each offence is evaluated against its own defendant's age at the date of hearing, independently.
- What happens when every relevant sexual offence in the hearing is missing its notification requirement result? → A warning is produced for each one independently.
- What happens when no other rule triggers alongside this one? → Only this rule's warning(s) are shown; User Story 3's combination scenario is additive, not a precondition for User Stories 1 and 2.
- What happens when a relevant sexual offence is charged jointly against more than one defendant (or, before any result line is recorded, against none identifiably)? → No warning is produced for that offence. This is a deliberate, narrower fail-safe than the missing-date-of-birth case (edge case above): a missing date of birth still has exactly one known defendant to default to Adult for, but a joint offence has no single defendant whose age the system can attribute the check to without guessing — and guessing wrong would misclassify Adult/Youth and could show the wrong wording, which is a worse outcome than not showing the warning. Flagged here explicitly (rather than left as a silent gap) since it means this warning does not currently cover joint sexual offences at all; revisit if joint sexual offence charging turns out to be a real scenario this rule needs to cover.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST identify an offence as a "relevant sexual offence" by querying offence reference data for that offence's classification and checking whether its `misCode` value is "SEX".
- **FR-002**: For each relevant sexual offence, the system MUST check whether the offence is convicted (guilty plea, finding of guilt, or a recorded date of conviction). If not convicted, no further evaluation is performed for that offence.
- **FR-003**: For each convicted relevant sexual offence, the system MUST determine whether the charged defendant is Adult (18 years of age or older at the date of hearing) or Youth (under 18 at the date of hearing).
- **FR-004**: For a convicted relevant sexual offence charged against an Adult defendant, the system MUST check whether at least one result line recorded against that offence has the short code `NORRR`. If none is present, the system MUST produce a WARNING for that offence.
- **FR-005**: For a convicted relevant sexual offence charged against a Youth defendant, the system MUST check whether at least one result line recorded against that offence has the short code `NORRR` or `NORPGP`. If neither is present, the system MUST produce a WARNING for that offence.
- **FR-006**: The Adult warning message text MUST be exactly: "This offence does not have a sexual offences notification requirement (NORRR). Check if this is required before sharing".
- **FR-007**: The Youth warning message text MUST be exactly: "This offence does not have a sexual offences notification requirement (NORRR - defendant or NORPGP - parent and defendant). Check if this is required before sharing".
- **FR-008**: Both warnings MUST be offence-level issues at WARNING severity, scoped to the individual offence(s) that breach the rule; other offences in the same hearing MUST NOT be affected.
- **FR-009**: Short-code matching for `NORRR` and `NORPGP` MUST be case-insensitive, consistent with existing short-code checks in this service.
- **FR-010**: When a defendant's date of birth is unavailable, the system MUST default to Adult classification (FR-004) rather than skipping the check for that offence.
- **FR-011**: This warning MUST NOT block sharing; the share action MUST remain available whenever the only issues present are warnings.
- **FR-012**: When a hearing's entered results trigger warnings from this rule together with warnings from other validation rules (offence-level or defendant-level), the system MUST include every triggered warning in the validation result — none MUST be dropped or hidden because of the presence of others.

### Key Entities

- **Relevant sexual offence**: An offence whose classification, retrieved from offence reference data, carries `misCode` "SEX" (e.g. `misDescription` "Sex offences"). Only offences meeting this classification are evaluated by this rule.
- **Offence reference data**: An external catalog of offence classification data (queried per offence), the source of the `misCode` value used to identify relevant sexual offences. Not currently integrated into this service.
- **Defendant age classification**: Adult (18 years of age or older at the date of hearing) or Youth (under 18 at the date of hearing), derived from the defendant's date of birth and the hearing date.
- **Notification requirement result**: A result line recorded against an offence carrying short code `NORRR` (defendant notification requirement) or, for Youth defendants only, `NORPGP` (parent and defendant notification requirement).
- **Validation warning**: An offence-level or defendant-level WARNING issue produced by this or other validation rules, advisory only and never blocking sharing.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every convicted relevant sexual offence charged against an Adult and missing a `NORRR` result receives the exact prescribed Adult warning message — zero omissions across all test scenarios.
- **SC-002**: Every convicted relevant sexual offence charged against a Youth and missing both `NORRR` and `NORPGP` results receives the exact prescribed Youth warning message — zero omissions across all test scenarios.
- **SC-003**: No false positives: offences that are not relevant sexual offences, not convicted, or that already carry a qualifying notification-requirement result produce zero warnings, validated across at least three distinct bypass scenarios.
- **SC-004**: The warning is returned at WARNING severity in 100% of triggered cases, never at ERROR severity, and never blocks sharing.
- **SC-005**: In a multi-offence, multi-defendant hearing, warnings are correctly isolated to breaching offences only, with no spillover to compliant offences or other defendants.
- **SC-006**: When this rule's warnings occur alongside warnings from other rules (offence-level and/or defendant-level, single or multiple), 100% of triggered warnings are present in the validation result and visible to the user in Manage Hearings.
- **SC-007**: Users can proceed to share results without making any changes whenever only warnings (no errors) are present, in 100% of cases.

## Assumptions

- **New external dependency, confirmed**: "relevant sexual offence" classification (mis_code = "SEX") is sourced by querying the offence reference-data service (`cpp-context-referencedata-offences`, exposed via `ReferencedataOffenceQueryApi.findOffence`), which returns a `misCode` field (e.g. `"SEX"`) alongside other offence classification data. This service is not currently integrated into this repository — no client, configuration, or dependency for it exists today — so delivering this feature requires adding that integration (following the existing `authz.http`-style external-service config pattern already used for the identity/permissions lookup), in addition to the new validation rule itself. This is a build dependency to flag during planning, not something this specification resolves.
- The Adult/Youth age boundary is evaluated as of the date of hearing (not the date of offence or date of conviction), consistent with the existing age-based rule already in this service that compares date of birth against hearing date.
- The visual presentation described in the originating acceptance criteria (bold black text, an exclamation mark in a black circle, alignment with the entered result, positioning above the triggering offence) is delivered by the existing warning-display design already used for other offence-level warnings in Manage Hearings; this feature is scoped to producing the correct warning message, severity, and level, not to introducing new presentation styling.
- The combined display of offence-level and defendant-level warnings from multiple rules (User Story 3) is expected to already be supported by the existing validation result structure, which returns all triggered issues rather than a single issue; this feature confirms the new rule's warnings coexist correctly within that structure rather than building new aggregation behaviour.
- `NORRR` and `NORPGP` are new result short codes not previously used elsewhere in this service; they are matched as exact short codes on result lines, case-insensitively, like existing short codes.
- A relevant sexual offence with multiple result lines is only considered missing its notification requirement if none of its result lines carry a qualifying code (`NORRR`, or for Youth, `NORRR`/`NORPGP`).
