# Feature Specification: Application Results Recorded Against Offences – ERROR Validation

**Feature Branch**: `010-application-result-offence-error`
**Created**: 2026-09-16
**Status**: Draft
**Input**: User description: "Application results recorded against offences – ERROR validation – ACs. The listed results are application results and may only be recorded against an application. Recording any of them against an offence triggers an ERROR that prevents sharing until resolved. AC1 — a single application result against an offence raises a page-level error and an inline error, and hides the Share button; AC2 — multiple application results against offences produce a page-level error naming every breaching result and an inline error per breaching result."

## User Scenarios & Testing *(mandatory)*

### User Story 1 – Single application result recorded against an offence raises an ERROR (Priority: P1)

A Legal Adviser or Court Clerk entering results for a defendant records a result that is only valid against an application — for example `LAWD` (Legal Aid Withdrawn) — against an offence rather than against an application on the hearing. When they select "Save and continue", they remain on the Enter Results screen. A page-level error at the top of the page names the breaching result and tells them to remove it from the offence and add an application to the hearing. An inline error is shown below the affected offence and above the breaching result. The results cannot be shared until the error is resolved.

**Why this priority**: This is the core acceptance criterion — without it the rule does not exist. An application result attached to an offence corrupts the Court record: the offence carries a disposal it can never lawfully have, and the application that should have carried it is missing from the hearing entirely.

**Independent Test**: Can be fully tested by constructing a hearing with a single defendant and a single offence carrying one application-only result code, calling validation, and asserting an ERROR is returned naming that result's label and identifying the affected offence.

**Acceptance Scenarios**:

1. **Given** a user is entering results for a defendant in a hearing, **And** one of the application-only results (`ARBSFG`, `ARBSPG`, `ARBSR`, `VT`, `G`, `LAREP`, `LAREPCC`, `ORDC`, `LATG`, `LATR`, `LAWD`, `RFSD`, `SMAR`) is recorded against an offence, **When** the user selects "Save and continue" and validation is triggered, **Then** an ERROR is returned and the user remains on the Enter Results screen. *(AC1)*
2. **Given** the error from scenario 1 is raised, **When** the page-level error is displayed at the top of the Enter Results screen, **Then** it reads "\<Application result label\> is an application result. It cannot be added to an offence. Remove it from the offence and add an application to the hearing".
3. **Given** the error from scenario 1 is raised, **And** the hearing has more than one defendant, **When** the page-level error is displayed, **Then** it is followed by "This affects: \<defendant name\>" naming the defendant whose offence carries the breaching result.
4. **Given** the error from scenario 1 is raised, **And** the hearing has exactly one defendant, **When** the page-level error is displayed, **Then** no "This affects:" line is shown.
5. **Given** the error from scenario 1 is raised, **When** the Enter Results screen is rendered, **Then** an inline error reading "Remove \<Application result label\> from this offence. It is an application result, so it can only be added to an application." is displayed below the affected offence and above the breaching result.
6. **Given** the error from scenario 1 is raised, **When** the user navigates to the Manage Hearings tab, **Then** the Share button is not visible **And** no errors are displayed on the Manage Hearings screen.
7. **Given** the error from scenario 1 is raised, **When** the user removes the application result from the offence and re-triggers validation, **Then** no error is returned for that offence and sharing is no longer blocked by this rule.

---

### User Story 2 – Multiple application results against offences: every breach is reported (Priority: P1)

A user records application-only results against more than one offence — for the same defendant, across several defendants, or more than one application-only result against a single offence. When they select "Save and continue", ONE consolidated page-level error identifies every breaching result across the hearing (comma-separated), the "This affects:" line lists every affected defendant (comma-separated where there is more than one), and each affected offence shows its own single consolidated inline error naming all of its breaching results. Every named result must be removed before sharing is possible; while any remain, the relevant error(s) continue to display, naming only the results that remain.

**Why this priority**: A rule that reports only the first breach forces the user through repeated save-and-fix cycles and risks results being shared with breaches still present after a partial fix. Complete reporting in a single pass is part of the same acceptance criteria set as User Story 1.

**Independent Test**: Can be fully tested by constructing a hearing with several offences (across at least two defendants) carrying multiple application-only result codes, calling validation, and asserting one error per breaching result with the complete affected-defendant list.

**Acceptance Scenarios**:

1. **Given** two or more application-only results are recorded against the SAME offence, **When** the user selects "Save and continue", **Then** ONE error is shown for that offence — not a separate error per result — **And** the error names ALL of the breaching application results, comma-separated, within the single message, **And** the inline error below the offence is likewise a single message naming all the breaching results, **And** resolving requires ALL of the named results to be removed from the offence — removing only some leaves the error displayed, naming the results that remain. *(AC2A)*
2. **Given** application results are recorded against MULTIPLE offences — for one defendant or across several — **When** the user selects "Save and continue", **Then** ONE page-level error is shown at the top of the page — not a separate error per offence or per result — **And** the single error names ALL of the offending application results across all offences, comma-separated, using the same presentation as AC2A. *(AC2B)*
3. **Given** the error from scenario 2 (AC2B) is raised, **And** the hearing has more than one defendant, **When** the page-level error is displayed, **Then** "This affects:" lists every affected defendant.
4. **Given** the error from scenario 2 (AC2B) is raised, **When** the Enter Results screen is rendered, **Then** each affected offence displays its own inline error below the offence, naming the breaching result(s) on that offence, consolidated per offence per AC2A.
5. **Given** several breaches exist and the user resolves some but not all of the named results, **When** validation is re-triggered, **Then** the relevant error(s) continue to display, naming only the results that remain, **And** sharing remains blocked.
6. **Given** several breaches exist, **When** the user resolves all of them and validation is re-triggered, **Then** no errors are returned by this rule **And** sharing is no longer blocked by it.

---

### User Story 3 – Amendment of an already-shared hearing (Priority: P2)

A user amending results on a hearing that has already been shared records (or leaves in place) an application-only result against an offence. The same ERROR is raised, and the user cannot request validation and reshare until it is resolved.

**Why this priority**: The amendment path is a second route to the same corrupted record. It reuses the rule from User Story 1 unchanged, so it carries less implementation risk, but it must be covered before the feature is complete.

**Independent Test**: Can be tested by triggering validation for an amendment of a previously shared hearing carrying an application-only result on an offence and asserting the same ERROR is returned and reshare is blocked.

**Acceptance Scenarios**:

1. **Given** a hearing whose results have already been shared, **And** an amendment records an application-only result against an offence, **When** validation is triggered, **Then** the same ERROR is returned with the same page-level and inline messages.
2. **Given** the error is raised on an amendment, **When** the user attempts to request validation and reshare, **Then** the request is blocked until the error is resolved.

---

### Edge Cases

- What happens when an application-only result is correctly recorded against an application rather than an offence? → No error. The rule only evaluates results linked to an offence.
- What happens when more than one application-only result is recorded against the SAME offence? → One consolidated inline error for that offence naming all of its breaching results, comma-separated (AC2A); the page-level error's single comma-separated list includes each of them.
- What happens when the same application-only result code appears on two different offences for the same defendant? → Each offence shows its own inline error naming that offence's breaching result(s); the single page-level error names the result once (not duplicated), and the defendant is named once in the "This affects:" list.
- What happens when the same application-only result code breaches on offences belonging to two different defendants? → Each offence's inline error names its own breaching result(s); the single page-level error names each distinct breaching result once, and both defendants are named in the "This affects:" list, comma-separated.
- What happens when a hearing has exactly one defendant? → The page-level error is shown without the "This affects:" line.
- What happens when a defendant has no name recorded? → The defendant is identified by the name held on the hearing record; where no name is available the entry is omitted from the "This affects:" list rather than rendering an empty name.
- What happens when an offence carries both an application-only result and other, valid results? → Only the application-only results raise errors; the valid results are unaffected.
- What happens when a hearing contains no application-only results against any offence? → No error from this rule; other validation rules are unaffected.
- What happens when this rule is disabled, or its severity is capped, via runtime configuration? → The rule's issues are suppressed or reduced accordingly; the severity is never raised above ERROR.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST treat the following result codes as application-only: `ARBSFG`, `ARBSPG`, `ARBSR`, `VT`, `G`, `LAREP`, `LAREPCC`, `ORDC`, `LATG`, `LATR`, `LAWD`, `RFSD`, `SMAR`.
- **FR-002**: System MUST raise a validation issue at severity ERROR for every occurrence of an application-only result recorded against an offence.
- **FR-003**: System MUST NOT raise an issue when an application-only result is recorded against an application rather than an offence.
- **FR-004**: System MUST produce exactly ONE page-level error message for this rule per validation pass, of the form "\<Application result label(s)\> is an application result. It cannot be added to an offence. Remove it from the offence and add an application to the hearing", where \<Application result label(s)\> is a single, comma-separated, de-duplicated list of the human-readable labels (not the codes) of every breaching result across the whole hearing — never a separate page-level error per offence or per result. *(AC2A, AC2B)*
- **FR-005**: System MUST append "This affects: \<defendant name(s)\>" to the page-level error when, and only when, the hearing contains more than one defendant. Multiple defendants MUST be listed comma-separated, de-duplicated, in hearing display order.
- **FR-006**: System MUST produce exactly ONE inline error message per affected offence, of the form "Remove \<Application result label(s)\> from this offence. It is an application result, so it can only be added to an application.", where \<Application result label(s)\> is a comma-separated, de-duplicated list of every breaching result recorded against that offence — never a separate inline error per result on the same offence. *(AC2A)*
- **FR-007**: System MUST identify every breaching result across the whole hearing in a single validation pass — it MUST NOT stop at the first breach, whether the breaches are on one offence, several offences for one defendant, or offences across several defendants — and MUST consolidate them into the single page-level error (FR-004) and one inline error per offence (FR-006), never a separate error per result.
- **FR-008**: System MUST report the affected offence and defendant for each issue so the consuming screen can position the inline error below the correct offence.
- **FR-009**: System MUST block sharing while any issue from this rule is outstanding, and MUST block the validate-and-reshare request on an amendment on the same terms.
- **FR-010**: System MUST return no issues from this rule once every breaching result has been removed from its offence.
- **FR-011**: System MUST honour runtime rule configuration: the rule can be disabled, and its severity capped downward, without a code change; severity MUST never be promoted above the ERROR defined here.

### Key Entities

- **Application-only result**: A recorded outcome whose result code appears in the FR-001 list. Valid against an application on a hearing; never valid against an offence. Carries both a code and a human-readable label; the label is what appears in user-facing messages.
- **Offence**: A charge in the hearing against which results are recorded. Identified so that inline errors can be positioned against the correct offence.
- **Defendant**: A person charged in the hearing. Named in the "This affects:" list when the hearing has more than one defendant.
- **Application**: A request made within a hearing (e.g. legal aid transfer, vacate, special measures) which is the only valid target for the results in FR-001. Not itself validated by this rule.
- **Validation issue**: An ERROR raised by this rule, carrying the message text, the affected offence, and the affected defendant.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of hearings in which an application-only result is recorded against an offence are blocked from sharing until the breach is removed.
- **SC-002**: 100% of breaching results in a hearing are reported in a single "Save and continue" pass — a user never needs more than one validation cycle to discover the complete set of breaches.
- **SC-003**: Zero errors are raised for hearings where the same result codes are correctly recorded against applications, measured across a representative sample of legitimate hearings.
- **SC-004**: Users can identify which defendant and which offence each error relates to without leaving the Enter Results screen, in 100% of error cases.
- **SC-005**: The count of shared hearings containing an application result attached to an offence falls to zero after release.
- **SC-006**: Adding or removing a result code from the application-only list requires a configuration change only, with no change to application code.

## Assumptions

- The thirteen result codes listed in FR-001 are the complete and authoritative scope for this release; no further codes are in scope.
- The rule fires on the result code alone — no additional context (offence type, defendant age, hearing type, court) qualifies or exempts a breach.
- Result labels used in the error messages are the labels supplied with the result data; the validation output names the label, and the consuming screen renders it as-is.
- "More than one defendant" is assessed against the number of defendants in the hearing, not the number of defendants with breaches — a hearing with three defendants where only one breaches still shows the "This affects:" line naming that one defendant.
- One page-level error entry is produced per distinct breaching result label; repeated occurrences of the same label are reflected by separate inline errors rather than repeated page-level entries.
- Validation is triggered on "Save and continue" from Enter Results and on request for an amendment; this feature does not introduce a new trigger point.
- Presentation concerns — remaining on the Enter Results screen, hiding the Share button on Manage Hearings, suppressing errors on the Manage Hearings screen, and the positioning of the inline error — are the consuming user interface's responsibility, following the established pattern for existing ERROR-severity rules. This feature supplies the issues, their severity, their message text, and their offence/defendant linkage.
- The rule evaluates draft results at validation time. Hearings already shared before release are not retrospectively re-validated; the amendment path (User Story 3) is the route by which an existing breach surfaces.
- The runtime enable/disable and severity-ceiling mechanism already proven for existing rules applies unchanged; this feature does not need its own override behaviour.
