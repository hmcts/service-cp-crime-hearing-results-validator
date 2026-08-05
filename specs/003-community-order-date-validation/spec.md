# Feature Specification: Community Order End Date Validation

**Feature Branch**: `dev/DD-42678-co-end-date-rule`
**Created**: 2026-05-20
**Updated**: 2026-08-05 — ported requirement duration end date validation (DD-41655, User Stories 4–7) from PR #116 onto `DR-COEW-005` (see [Clarifications](#clarifications))
**Status**: Implemented
**Input**: User description: "Community order end date validation — AC2 (requirement end dates), AC4/AC5 (error display patterns); DD-41655 (requirement duration end date validation)"
**Scope**: AC2, AC4, AC5, and DD-41655 (CUR/CURE/AAR duration-mismatch checks) only. AC1 ("end date must be in the future") is out of scope — handled by a separate ticket.

---

## User Scenarios & Testing *(mandatory)*

### ~~User Story 1 — AC1: Community Order End Date Must Be In The Future~~ *(Out of scope — separate ticket)*

> AC1 (Scenarios 1–5, "The end date must be in the future") is explicitly excluded from this ticket. It will be implemented and tested under a separate Jira ticket. This ticket covers AC2, AC4, and AC5 only.

---

### User Story 1 — Requirement End Date Cannot Exceed the Community Order End Date (Priority: P1)

As a court clerk, when I try to navigate to Manage Hearings after entering results, the system must detect if any community order's requirements (CUR, CURE, CURA, AAR) have an end date later than the parent order end date — and block navigation until I correct the inconsistency.

**Why this priority**: A requirement that outlasts its parent order is a legal inconsistency. Allowing it to be shared would produce invalid records in downstream systems.

**Independent Test**: Add a community order result with a child requirement (CUR, CURE, CURA, or AAR) whose end date exceeds the parent order end date, then click "Save and continue". Navigation to Manage Hearings must be blocked and the error must name the specific requirement.

**Acceptance Scenarios**:

1. **Given** a COEW order ending 30/10/2026 with a CUR child result ending 30/11/2026, **When** the user selects "Save and continue", **Then** navigation is blocked, the error "The end date of the order must match or be longer than the end date of Curfew (community requirement)" appears in the top error summary and inline above the affected offence result, and "This affects: John Smith" is shown.
2. **Given** a COS order with a CURE child result whose "End date of tag" is 15/12/2026 and the order ends 01/12/2026, **When** the user selects "Save and continue", **Then** the error references "Curfew with electronic monitoring" at the top of the screen and against each affected offence.
3. **Given** a CONI order ending 01/01/2027 with a CURA child result ending 15/01/2027, **When** the user selects "Save and continue", **Then** the error references "Further curfew requirement made" at the top of the screen and above the offence.
4. **Given** a COEW order ending 01/06/2027 with an AAR child result with "Until" date of 15/06/2027, **When** the user selects "Save and continue", **Then** the error references "Alcohol abstinence and monitoring" at the top of the screen and above the offence.
5. **Given** a COEW order ending 30/12/2026 with a CUR child result ending 30/11/2026 (order end date is later than requirement end date), **When** the user selects "Save and continue", **Then** no validation error is shown and the user navigates to Manage Hearings with the Share button visible.
6. **Given** a COEW order ending 01/06/2027 with both a CUR ending 15/06/2027 and an AAR Until 25/06/2027, **When** the user selects "Save and continue", **Then** two separate error messages are shown — one referencing "Curfew (community requirement)" and one referencing "Alcohol abstinence and monitoring" — each with its own "This affects:" line.
7. **Given** three defendants where Defendant 1 is valid, Defendant 2 has a CUR ending after the order, and Defendant 3 has an AAR Until exceeding the order, **When** the user selects "Save and continue", **Then** the error summary reads "This affects: Defendant 2, Defendant 3" and Defendant 1 is not listed.
8. **Given** a community order validation failure under AC2, **When** the user manually navigates to Manage Hearings, **Then** the Share button is not displayed and no validation error messages are shown on the Manage Hearings screen.
9. **Given** two `defendantId` entries in the request share the same `masterDefendantId` (a defendant linked across cases), **When** a community order under one `defendantId` has a requirement violation recorded under the other `defendantId`, **Then** the two `defendantId`s are treated as a single defendant for AC2 evaluation and the error's affected-defendant/offence data is merged into one context — mirroring the master-defendant grouping already used by `CustodialPreprocessor` and `YouthRehabilitationPreprocessor`.

---

### User Story 2 — Error Summary Displayed at the Top of the Enter Results Screen (Priority: P2)

As a court clerk, when validation errors prevent me from navigating to Manage Hearings, I need a clear error summary box at the top of the Enter Results screen listing every error and which defendants it affects — so I know exactly what to correct before I can share results.

**Why this priority**: Without a prominent, structured summary, court clerks may miss which defendants and offences require correction and may be unable to efficiently resolve all issues.

**Independent Test**: Trigger any AC2 validation error and click "Save and continue". Verify the top-of-page red error summary box appears with the correct heading, error text, and defendant list structure.

**Acceptance Scenarios**:

1. **Given** one or more validation errors are triggered, **When** the user selects "Save and continue", **Then** the user remains on Enter Results, is navigated to the top of the screen, and sees a red box with the bold black heading "There is a Problem".
2. **Given** the error summary is shown, **When** displaying each error, **Then** each error message is shown in bold red text and is followed on the next line by "This affects: <<defendant name>>, <<defendant name>>" in red text listing all defendants who triggered that specific error.
3. **Given** multiple distinct validation errors are present (e.g. AC2 errors for different requirement types), **When** the summary is shown, **Then** each error is shown separately with its own "This affects:" line.
4. **Given** validation errors exist, **When** the user manually navigates to Manage Hearings via the tab, **Then** the Share button is not visible on the Manage Hearings screen.

---

### User Story 3 — Inline Error Shown Above Each Result That Triggered a Validation Error (Priority: P2)

As a court clerk, when I scroll through the Enter Results screen after a validation failure, I need to see the specific error message shown inline — above the offending result — for every offence that triggered a validation error, so I can locate and fix each issue without only relying on the top-level summary.

**Why this priority**: Inline errors guide the clerk directly to each record requiring correction; without them, clerks would need to manually match summary errors to individual offences.

**Independent Test**: Trigger any AC2 validation error and scroll through the Enter Results screen. A red inline error with a vertical left border must appear above each offending result.

**Acceptance Scenarios**:

1. **Given** a validation error is triggered by a result on a specific offence, **When** the user scrolls the Enter Results screen, **Then** a red error message with a vertical left border is displayed below the offence label and above the result for each affected offence.
2. **Given** a result on a single offence triggers multiple validation errors (e.g. both AC2a and AC2d), **When** the inline errors are shown, **Then** all errors for that offence are listed inline.

---

### User Story 4 — Requirement's Own End Date Must Match Its Calculated Duration (Priority: P1)

As a court clerk, when I record a CUR, CURE, or AAR requirement, the system must detect if the requirement's own recorded end date does not match its calculated duration — start date plus period minus one day (or hearing date plus days minus one day for AAR) — and block navigation until I correct it, independent of any AC2 order-end-date check.

**Why this priority**: A requirement whose recorded end date doesn't match its own stated period is internally inconsistent and would produce an invalid record even when it happens not to exceed the parent order's end date — a case AC2 alone cannot catch.

**Independent Test**: Add a CUR requirement with a start date and period whose calculated end date (start date + period − 1 day) does not equal the recorded end date, then click "Save and continue". Navigation must be blocked and the error must state the correctly calculated end date.

**Acceptance Scenarios**:

1. **Given** a CUR requirement with start date 01/09/2026 and curfew period 30, **When** the recorded end date is 29/09/2026 (one day early), **Then** navigation is blocked and the error states the end date should be 30/09/2026.
2. **Given** a CUR requirement with start date 01/09/2026 and curfew period 30, **When** the recorded end date is exactly 30/09/2026, **Then** no DUR-CUR error is shown.
3. **Given** a CURE requirement with start date of tagging 01/09/2026 and curfew-and-electronic-monitoring period 60, **When** the recorded end date of tagging does not equal 30/10/2026, **Then** navigation is blocked and the error states the end date should be 30/10/2026.
4. **Given** an AAR requirement on a hearing dated 01/01/2026 with 90 days to abstain, **When** the recorded "Until" date does not equal 31/03/2026 (hearing date + 90 − 1 day), **Then** navigation is blocked and the error states the end date should be 31/03/2026.
5. **Given** a CUR requirement whose end date is both after the parent order's end date (AC2a) and does not match its own calculated duration (DUR-CUR), **When** the user selects "Save and continue", **Then** two independent errors are shown for the same offence — one for each condition.
6. **Given** a CURA requirement carrying the same-shaped prompts (start date, period, end date) as CUR, **When** the requirement is evaluated, **Then** no duration-mismatch check runs against it — DUR-CUR/CURE/AAR apply only to CUR, CURE, and AAR; CURA is covered by AC2c only.
7. **Given** a CUR requirement missing its start date, period, or end date prompt, **When** the requirement is evaluated, **Then** the duration-mismatch check is skipped for that requirement (no error, no exception) rather than failing closed.

---

### Edge Cases

- What happens when multiple defendants in a hearing have AC2 failures alongside valid defendants? Only the defendants with each specific error type appear in that error's "This affects:" list; valid defendants are never listed.
- What happens when a community order has multiple requirements simultaneously violating AC2 (e.g. both CUR and AAR exceed the order)? One separate error message is produced per violated requirement type — e.g. one for CUR and one for AAR — each naming the specific requirement. The YAML+CEL engine evaluates each requirement condition independently, so each fires its own `ValidationIssue` (AC2a=CUR, AC2b=CURE, AC2c=CURA, AC2d=AAR). This gives per-condition runtime configurability.
- What happens when the requirement end date is exactly equal to the community order end date? This is valid — AC2 only triggers when the requirement end date is strictly later than the order end date.
- What happens when the user navigates directly to Manage Hearings (bypassing "Save and continue") while unresolved validation errors exist? The Share button must not be visible; the Manage Hearings screen must not show any validation error messages.
- What happens when the community order end date is null/missing? This should be caught by mandatory field validation before AC2 checks run.
- What happens when a defendant's community order and one of their requirement lines are recorded under two different `defendantId` values that share the same `masterDefendantId`? Both `defendantId`s are folded into a single group (keyed by `masterDefendantId`) before AC2 checks run, so the violation is still detected and reported once, against the shared identity. See Scenario 9.
- What happens when a CUR/CURE/AAR requirement fails both AC2 (exceeds the order end date) and its own duration-mismatch check? Both fire independently as two separate `ValidationIssue` entries against the same offence — DUR-CUR/CURE/AAR does not depend on a parseable order end date and evaluates regardless of the AC2 outcome. See User Story 4, Scenario 5.
- What happens when a community order has no parseable end date at all? AC2 checks are skipped for that offence (no order end date to compare against), but DUR-CUR/CURE/AAR duration-mismatch checks still run — they compare the requirement's own start date/period/end date and never reference the order.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** *(Out of scope — AC1 is handled by a separate ticket)*: ~~System MUST reject saving a community order result (COEW, COS, CONI) when the end date entered is on or before the hearing date, displaying the error "The end date must be in the future" at the field level and in the page-level error summary.~~ This ticket assumes AC1 is either already implemented or will be delivered separately; AC2 checks at "Save and continue" assume the order end date is already a valid future date when they run.
- **FR-002**: System MUST detect, at "Save and continue", when a community order's end date is strictly earlier than the end date, "End date of tag", or "Until" date of any of its child requirement results of type CUR, CURE, CURA, or AAR. These dates are supplied by the UI as `Prompt` entries in `ResultLineDto.prompts`, matched by `promptRef` (e.g. `"endDate"`, `"endDateOfTag"`, `"until"`) and read from `promptValue`.
- **FR-003**: When FR-002 triggers, the validation service MUST emit one separate `ValidationIssue` per violated requirement type using these exact display names: "Curfew (community requirement)" (CUR), "Curfew with electronic monitoring" (CURE), "Further curfew requirement made" (CURA), "Alcohol abstinence and monitoring" (AAR). Each violation type maps to an independent YAML condition (AC2a=CUR, AC2b=CURE, AC2c=CURA, AC2d=AAR), enabling per-condition runtime enable/disable via the `validation_rule` table. If both CUR and AAR violate on the same order, two distinct `ValidationIssue` entries appear in the response — one per requirement type.
- **FR-004**: Each AC2 `ValidationIssue` message MUST read: "The end date of the order must match or be longer than the end date of <<requirement display name>>" where `<<requirement display name>>` is the display name for that specific requirement type only (one requirement per message).
- **FR-005**: When two `defendantId` values in the request share the same non-blank `masterDefendantId`, the preprocessor MUST treat them as a single defendant for AC2 purposes — merging their result lines before grouping by offence, and reporting violations against the shared `masterDefendantId` group. A blank or missing `masterDefendantId` falls back to the defendant's own `defendantId` as the group key.
- **FR-007**: When one or more AC2 validation errors block navigation to Manage Hearings, the system MUST keep the user on the Enter Results screen and scroll them to the top, displaying a red error summary box with the bold heading "There is a Problem".
- **FR-008**: Each distinct validation error in the summary MUST be shown in bold red text, followed by "This affects: <<defendant name(s)>>" in red, listing all defendants whose results triggered that specific error. The API delivers this via `affectedDefendants: [{ defendantId }]` on each `ValidationIssue`; the UI looks up the defendant display name from `defendantId`.
- **FR-009**: When multiple distinct validation errors exist, ALL must appear in the summary.
- **FR-010**: For every offence/result combination that triggered a validation error, an inline red error message with a vertical left border MUST appear below the offence label and above the result on the Enter Results screen.
- **FR-011**: When multiple validation errors apply to a single offence/result, ALL errors for that result MUST be shown inline.
- **FR-012**: When any AC2 validation errors exist for any defendant in the hearing, the Share button MUST NOT be visible on the Manage Hearings screen — the Share button is a single hearing-level control, hidden for the whole hearing regardless of which defendant(s) have errors, and regardless of whether the user reached Manage Hearings via "Save and continue" or by navigating directly.
- **FR-013**: The Manage Hearings screen MUST NOT display validation error messages; errors are surfaced exclusively on the Enter Results screen.
- **FR-014**: Per-defendant isolation applies to error display only — inline errors and "This affects:" lists on the Enter Results screen MUST reference only the defendants whose results triggered each specific error. Valid defendants must never appear in an error's "This affects:" list. This does not imply independent per-defendant sharing; the Share button remains a single hearing-level control (see FR-012).
- **FR-015**: System MUST detect, at "Save and continue", when a CUR, CURE, or AAR requirement's own recorded end date does not equal its calculated duration: start date + period − 1 day (CUR: `startDate`/`curfewPeriod`/`endDate`; CURE: `startDateOfTagging`/`curfewAndElectronicMonitoringPeriod`/`endDateOfTagging`), or hearing date + days − 1 day for AAR (`numberOfDaysToAbstain`/`until`, using `DraftValidationRequest.hearingDay`). This check is independent of and additive to the FR-002 order-end-date check and does not require a parseable order end date to run. CURA is out of scope for this check.
- **FR-016**: When FR-015 triggers, the validation service MUST emit a `ValidationIssue` whose inline message states the correctly calculated end date (formatted DD/MM/YYYY) via a `${calculatedEndDate}` placeholder, and whose error-summary message follows the same "This affects: <<defendant name(s)>>" pattern as FR-004/FR-008. Each requirement type (CUR, CURE, AAR) maps to an independent YAML condition (DUR-CUR, DUR-CURE, DUR-AAR).
- **FR-017**: When the start date, period, or end date prompt required by FR-015 is missing or unparseable, the duration-mismatch check MUST be skipped for that requirement (no error) rather than failing the request.

### Key Entities

- **Community Order**: A sentencing result of type COEW, COS, or CONI. Represented as a `ResultLineDto` in `DraftValidationRequest.resultLines`. Its mandatory end date is carried as a named value in `ResultLineDto.prompts`.
- **Requirement (child result)**: A sub-result attached to a community order, also represented as a `ResultLineDto`. The date field for each requirement type is carried as a named prompt value in `ResultLineDto.prompts`: CUR → `endDate`, CURE → `endDateOfTag`, CURA → `endDate`, AAR → `until`.
- **`ResultLineDto.prompts`**: `List<Prompt>` — available from API library version `0.1.6` (now in use). Carries per-result named values entered by the clerk. Each `Prompt` has two `String` fields: `promptRef` (the prompt key, e.g. `"endDate"`, `"endDateOfTag"`, `"until"`) and `promptValue` (the entered value as a string, e.g. `"2026-11-30"`). Accessed via `getPromptRef()` and `getPromptValue()`.
- **Validation error**: A blocking issue produced by the validation service that prevents result sharing. Carries a message text, the set of affected defendants, and linkage to the specific offences/results that triggered it.
- **Defendant**: An individual in the hearing. A hearing may include multiple defendants; each defendant's results are validated independently, except where two `defendantId`s share a `masterDefendantId` (see FR-005), in which case they are validated as one.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** *(AC1 — out of scope)*: ~~End date in-the-past check~~ — covered by a separate ticket (Scenarios 1–5 excluded from this ticket's acceptance test suite.
- **SC-002**: 100% of community orders where any child requirement's date exceeds the order end date (AC2) are blocked at "Save and continue" with the requirement display name in the error message — zero missed violations.
- **SC-004**: The Share button is hidden on the Manage Hearings screen in 100% of cases where unresolved validation errors exist — regardless of the navigation path taken.
- **SC-005**: Error messages precisely identify only the defendants affected by each specific error — no defendant is incorrectly included or omitted from any "This affects:" list, including when `masterDefendantId` grouping folds two `defendantId`s together.
- **SC-006**: All 9 in-scope acceptance scenarios pass automated regression tests. Scenarios 1–5 (AC1) are excluded from this ticket's test suite.
- **SC-007**: Valid defendants are never listed in any error's "This affects:" line when other defendants in the same hearing have errors; however, the Share button is hidden for the whole hearing until all errors are resolved.
- **SC-008**: 100% of CUR/CURE/AAR requirements whose recorded end date does not match their calculated duration (FR-015) are blocked at "Save and continue" with the correctly calculated end date shown inline — zero missed violations, independent of whether the AC2 order-end-date check also fires on the same offence.

---

## Assumptions

- **A-002**: For AC2, an order end date equal to the requirement end date is valid (the error triggers only when the order end date is strictly before the requirement end date). Scenario 5 confirms this boundary.
- **A-003**: AC1 validation ("end date must be in the future") fires on "Save result details" and prevents saving the draft; AC2 validations fire on "Save and continue" after all individual results are saved. Both surface errors in the page-level "There is a problem" summary.
- **A-004**: AC1 (Scenarios 1–5, "The end date must be in the future") is **explicitly out of scope** for this ticket and will be delivered under a separate Jira ticket. AC2 validations run at "Save and continue" and assume the order end date has already passed any prior field-level checks.
- **A-005**: Multiple requirement violations on a single order (e.g. both CUR and AAR exceeding the order) each produce a separate error message — one per violated requirement type. Scenario 6 produces two `ValidationIssue` entries: one for CUR and one for AAR. The YAML+CEL engine evaluates each requirement condition independently; the UI renders them adjacently in the "There is a problem" summary.
- **A-006**: The validation service operates on draft hearing result data and does not modify or persist the hearing record.
- **A-007**: `ResultLineDto.prompts` (`List<Prompt>`, each with `promptRef: String` and `promptValue: String`) is available from upstream library version `0.1.6`, which is now the version in use. No upstream API change is required. No new result codes or DB migrations are needed beyond the YAML rule and new preprocessor.
- **A-008**: Short codes (COEW, COS, CONI, CUR, CURE, CURA, AAR) are already defined in the result code dictionary and are not modified by this feature.
- **A-009**: UI error display patterns (red box, bold heading, vertical border for inline errors) align with the existing GOV.UK Design System error summary and error message components already in use in the Enter Results screen.
- **A-010**: The rule ships as `DR-COEW-005` (not `DR-COEW-001`) on `dev/DD-42678-co-end-date-rule` because migration slots `V1.001`–`V1.004` were already claimed by `DR-DISQ-001`, `DR-CTL-001`, and `DR-YRO-001` on this branch by the time this rule landed. The rule's behaviour, YAML conditions, and CEL variable names are otherwise unchanged from the original `DR-COEW-001` design.
- **A-011**: Unlike the original design (which shipped the DB override row `enabled: false`), this branch ships `DR-COEW-005` with the DB override row set to `enabled: true` — the rule is live by default rather than requiring a manual opt-in.
- **A-012**: The DUR-CUR/CURE prompt ref keys (`startDate`, `curfewPeriod`, `startDateOfTagging`, `curfewAndElectronicMonitoringPeriod`) remain unverified assumptions pending confirmation against the real upstream API contract, mirroring the caveat already recorded for the AC2 prompt ref keys. The DUR-AAR key was originally assumed as `numberOfDaysToAbstain` in PR #116 but has since been **confirmed** against a real payload as `numberOfDaysToAbstainFromConsumingAnyAlcohol` — this port carries the confirmed key directly. See research.md Decision 9.
- **A-013**: DUR-CUR/CURE/AAR reuse `DR-COEW-005`'s existing `${defendantNames}` error-summary mechanism and the OFFENCE-level inline message mechanism; no new rule file, DB migration, or preprocessor type is required — only new conditions on the existing `DR-COEW-005` YAML and new fields on `CommunityOrderContext`.

---

## Clarifications

### Session 2026-05-20

- Q: Does `DraftValidationRequest` currently carry the hearing date, or would it need to be added to the upstream API DTO? → A: Hearing date is already present as the `hearingDay` field on `DraftValidationRequest` from the upstream API — no upstream DTO change is needed.
- Q: When multiple requirements on one order violate AC2 simultaneously, how should the errors be emitted? → A: ~~One combined error message naming all violating requirements together (not separate messages per requirement).~~ **Revised (2026-05-20 post-analyze)**: Separate — one `ValidationIssue` per violated requirement type. The YAML+CEL engine evaluates each condition (AC2a=CUR, AC2b=CURE, AC2c=CURA, AC2d=AAR) independently; combined-message semantics require Java post-processing which violates Constitution Principle I. FR-003, FR-004, A-005, and Edge Cases updated to reflect this.
- Q: Is the Share button a single hearing-level control or per-defendant? → A: Single Share button for the whole hearing — hidden if any defendant has unresolved errors. Per-defendant isolation applies to error display ("This affects:" lists) only, not to sharing.
- Q: Is AC1 ("end date must be in the future") already implemented or new work for this ticket? → A: Out of scope — AC1 will be handled by a separate ticket. This ticket covers AC2, AC4, and AC5 only.
- Additional context: Child result end dates (for CUR, CURE, CURA, AAR) and the community order end date (for COEW/COS/CONI) are received as `promptRef`/`promptValue` pairs in `ResultLineDto.prompts` (`List<Prompt>`). Confirmed present in API library `0.1.6` (now in use). `Prompt` has `getPromptRef(): String` and `getPromptValue(): String`.

### Session 2026-08-05 — retrofit onto `dev/DD-42678-co-end-date-rule`

- Q: Why is the rule ID `DR-COEW-005` here instead of the `DR-COEW-001` used when this feature was first specified on `team/DD-41653`? → A: This branch had already claimed migration slots for `DR-DISQ-001`, `DR-CTL-001`, and `DR-YRO-001` by the time the community-order rule was ported across, so it was renumbered to `DR-COEW-005` and its DB seed row moved to `V1.005__insert_dr_coew_005.sql`. See A-010, FR-005, and research.md Decision 8.
- Q: Was any new behaviour added beyond the original AC2/AC4/AC5 scope during the port? → A: Yes — `masterDefendantId` grouping (FR-005, Scenario 9) was added so the preprocessor treats linked `defendantId`s as one defendant, matching the pattern already used by `CustodialPreprocessor` and `YouthRehabilitationPreprocessor`. The preprocessor's shared plumbing (short-code matching, grouping, date parsing) was also extracted into `PreprocessorHelper` for reuse across preprocessors — see research.md Decision 8 and plan.md.
- Q: Did the rule's default enabled/disabled state change? → A: Yes — it now ships with the DB override row `enabled: true` (live by default) rather than `false`. See A-011.

### Session 2026-08-05 — port requirement duration end date validation (DD-41655) from PR #116

- Q: PR #116 (`DD-41655-requirement-duration-validation`, merged upstream against an older `DR-COEW-001` lineage) was never merged into this branch's history — how is its functionality brought across? → A: Ported as new conditions (DUR-CUR, DUR-CURE, DUR-AAR) on the already-renumbered `DR-COEW-005` YAML, plus corresponding fields on `CommunityOrderContext`/`CommunityOrderEndDatePreprocessor`, rather than a literal git merge — the two branches' histories had diverged too far (rule renumbering, `PreprocessorRegistry` dispatch, `masterDefendantId` grouping) for a mechanical merge/cherry-pick to apply cleanly. See User Story 4, FR-015–FR-017, A-012, A-013.
- Q: Does the duration-mismatch check depend on the AC2 order-end-date check? → A: No — independent and additive. DUR-CUR/CURE/AAR compare a requirement's own start date/period/end date and run even when the order end date is missing or unparseable (Edge Cases).
- Q: Why does DUR-CUR/CURE/AAR exclude CURA even though CURA shares the same prompt shape as CUR? → A: The original DD-41655 design only covers CUR, CURE, and AAR; CURA remains covered by AC2c alone. See Scenario 6.
- Q: Does this port carry PR #116's original code exactly, or does it include later fixes? → A: Later fixes. PR #116's own branch had two further merged PRs (#127, #133) fixing real defects confirmed against live payloads — a period-value parsing bug (bare integers assumed; real payloads send `"90 Days"`-style values) and a wrong AAR prompt-ref key (`numberOfDaysToAbstain` vs. the real `numberOfDaysToAbstainFromConsumingAnyAlcohol`). Both would have made a duration-mismatch condition silently never fire. This port carries the corrected behaviour directly; see plan.md's Port Notes and research.md Decision 9.
