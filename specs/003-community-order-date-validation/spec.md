# Feature Specification: Community Order End Date Validation

**Feature Branch**: `DD-41653-community-order-date-validation`  
**Created**: 2026-05-20  
**Status**: Draft  
**Input**: User description: "Community order end date validation — AC2 (requirement end dates), AC4/AC5 (error display patterns)"  
**Scope**: AC2, AC4, AC5 only. AC1 ("end date must be in the future") is out of scope — handled by a separate ticket.

**Extended**: 2026-07-06 (Jira `DD-41655`, branch `DD-41655-requirement-duration-validation`, based on `team/DD-41653`) — adds **Requirement Duration End Date Validation** (User Stories 4–7 below): a requirement's own recorded end date must match its calculated duration (Start date + period − 1 day, or hearing date + days − 1 day for AAR). This is independent of and additive to the order-end-date-vs-requirement-end-date check in User Story 1 — a requirement may fail either, both, or neither check.

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

### User Story 4 — Curfew (Non-Electronic Monitoring) Requirement End Date Must Match Calculated Duration (Priority: P1)

As a court clerk, when I enter a Curfew (Non-electronic monitoring) requirement (CUR) on a Community Order (COEW, COS, CONI) and select "Save and continue", the system must detect if the requirement's "End date" does not equal "Start date" + "Curfew period" − 1 day, and block navigation to Manage Hearings until I correct it.

**Why this priority**: An end date that doesn't match the recorded curfew period is a data-entry error that produces an inconsistent requirement duration in downstream systems — the same class of risk as User Story 1, but caught earlier, from the requirement's own fields rather than by comparison to the parent order.

**Independent Test**: Add a CUR child result with a Start date, a Curfew period, and an End date that does not equal Start date + period − 1 day, then select "Save and continue". Navigation must be blocked and the error must name the Curfew Requirement.

**Acceptance Scenarios**:

1. **Given** a CUR child result with Start date 01/09/2026 and Curfew period 30 days, **When** the End date entered is 01/10/2026 (the correct value is 30/09/2026) and the user selects "Save and continue", **Then** navigation is blocked, the top error summary shows "The end date for the Curfew Requirement does not match the period of the requirement." with "This affects: <<defendant name>>", and an inline error below the offence reads "The end date for the Curfew Requirement does not match the period of the requirement. The current recorded period would mean the end date should be 30/09/2026."
2. **Given** a CUR child result where the End date exactly equals Start date + Curfew period − 1 day, **When** the user selects "Save and continue", **Then** no error is raised for this requirement.
3. **Given** a CUR duration-mismatch error exists, **When** the user manually navigates to Manage Hearings via the tab, **Then** the Share button is not visible and no validation error is shown on Manage Hearings.

---

### User Story 5 — Curfew with Electronic Monitoring Requirement "End Date of Tagging" Must Match Calculated Duration (Priority: P1)

As a court clerk, when I enter a Curfew with electronic monitoring requirement (CURE) on a Community Order and select "Save and continue", the system must detect if "End date of tagging" does not equal "Start date of tagging" + "Curfew and electronic monitoring period" − 1 day, and block navigation until corrected.

**Why this priority**: Same class of data-entry inconsistency as User Story 4, applied to the electronically-monitored curfew variant, which uses its own distinct field names.

**Independent Test**: Add a CURE child result with a Start date of tagging, a Curfew and electronic monitoring period, and an End date of tagging that does not match, then select "Save and continue". Navigation must be blocked.

**Acceptance Scenarios**:

1. **Given** a CURE child result with Start date of tagging 01/09/2026 and Curfew and electronic monitoring period 60 days, **When** End date of tagging entered is 01/11/2026 (the correct value is 30/10/2026) and the user selects "Save and continue", **Then** navigation is blocked, the top error summary shows "The end date for the Curfew Requirement does not match the period of the requirement." with "This affects: <<defendant name>>", and the inline error states the correct end date should be 30/10/2026.
2. **Given** a CURE child result where End date of tagging exactly equals Start date of tagging + period − 1 day, **When** the user selects "Save and continue", **Then** no error is raised for this requirement.
3. **Given** a CURE duration-mismatch error exists, **When** the user manually navigates to Manage Hearings, **Then** the Share button is not visible.

---

### User Story 6 — Alcohol Abstinence Monitoring Requirement "Until" Date Must Match Calculated Duration (Priority: P1)

As a court clerk, when I enter an Alcohol Abstinence Monitoring requirement (AAR) on a Community Order and select "Save and continue", the system must detect if the "Until" date does not equal the hearing date + "Number of days to abstain from consuming any alcohol" − 1 day, and block navigation until corrected.

**Why this priority**: Same class of data-entry inconsistency as User Stories 4 and 5, calculated from the hearing date rather than a requirement-specific start date.

**Independent Test**: Add an AAR child result with a "Number of days to abstain" value and an "Until" date that does not match hearing date + days − 1, then select "Save and continue". Navigation must be blocked.

**Acceptance Scenarios**:

1. **Given** a hearing date of 01/09/2026 and an AAR child result with "Number of days to abstain" = 90 and "Until" = 01/12/2026 (the correct value is 29/11/2026), **When** the user selects "Save and continue", **Then** navigation is blocked, the top error summary shows "The end date for the Alcohol Abstinence Monitoring Requirement does not match the period of the requirement." with "This affects: <<defendant name>>", and the inline error states the correct "Until" date should be 29/11/2026.
2. **Given** an AAR child result where "Until" exactly equals hearing date + days to abstain − 1 day, **When** the user selects "Save and continue", **Then** no error is raised for this requirement.
3. **Given** an AAR duration-mismatch error exists, **When** the user manually navigates to Manage Hearings, **Then** the Share button is not visible.

---

### User Story 7 — Duration-Mismatch Errors Combine With Other Validation Errors in a Single Summary (Priority: P2)

As a court clerk, when my entered results trigger duration-mismatch errors (User Stories 4–6) together with each other, with the order-end-date check (User Story 1), or with validation rules from other features, I need to see every triggered error together in one place so I can resolve them all before sharing.

**Why this priority**: Reuses the error-summary and inline-error display mechanism already proven for this feature (User Stories 2 and 3); this story only verifies the new duration-mismatch conditions integrate correctly with it — it does not introduce new UI.

**Independent Test**: Trigger a CUR duration mismatch and an AAR duration mismatch simultaneously (or a duration mismatch together with an order-end-date violation from User Story 1), select "Save and continue", and verify every error appears in the top summary and inline, each with its own correct "This affects:" list.

**Acceptance Scenarios**:

1. **Given** a hearing where one defendant has a CUR duration mismatch and another has an AAR duration mismatch, **When** the user selects "Save and continue", **Then** both errors appear in the top summary, each with its own "This affects:" line naming only the relevant defendant, and navigation is blocked.
2. **Given** a hearing where a single defendant's community order simultaneously fails the order-end-date check (User Story 1) and a CUR duration-mismatch check, **When** the user selects "Save and continue", **Then** both errors are shown together in the summary and inline against the same offence, and the user cannot share until both are resolved.
3. **Given** no duration-mismatch or other validation errors are triggered, **When** the user selects "Save and continue", **Then** the user navigates to Manage Hearings and the Share button is visible.

---

### Edge Cases

- What happens when multiple defendants in a hearing have AC2 failures alongside valid defendants? Only the defendants with each specific error type appear in that error's "This affects:" list; valid defendants are never listed.
- What happens when a community order has multiple requirements simultaneously violating AC2 (e.g. both CUR and AAR exceed the order)? One separate error message is produced per violated requirement type — e.g. one for CUR and one for AAR — each naming the specific requirement. The YAML+CEL engine evaluates each requirement condition independently, so each fires its own `ValidationIssue` (AC2a=CUR, AC2b=CURE, AC2c=CURA, AC2d=AAR). This gives per-condition runtime configurability.
- What happens when the requirement end date is exactly equal to the community order end date? This is valid — AC2 only triggers when the requirement end date is strictly later than the order end date.
- What happens when the user navigates directly to Manage Hearings (bypassing "Save and continue") while unresolved validation errors exist? The Share button must not be visible; the Manage Hearings screen must not show any validation error messages.
- What happens when the community order end date is null/missing? This should be caught by mandatory field validation before AC2 checks run.

**Duration validation (User Stories 4–7):**

- What happens when the Curfew period, the Curfew and electronic monitoring period, or the Number of days to abstain is 0 or missing? Mandatory field validation is expected to catch missing values before "Save and continue"; the duration-mismatch check does not fire without a period value.
- What happens when the Start date (or Start date of tagging) is missing? Caught by mandatory field validation before the duration-mismatch check runs.
- What happens when the recorded end date equals Start date (or hearing date) + period, without subtracting 1 day? This is flagged as an error — the "− 1 day" convention is part of the expected calculation, and this off-by-one is a common clerical mistake the rule is designed to catch.
- What happens when a single community order has more than one CUR, CURE, or AAR requirement attached? Each requirement instance is validated independently; each mismatch produces its own error scoped to that specific requirement and offence.
- What happens when a requirement fails both the order-end-date check (User Story 1) and its own duration-mismatch check (User Stories 4–6) at the same time? Both errors are shown — one per violated rule — against the same offence, per User Story 7.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** *(Out of scope — AC1 is handled by a separate ticket)*: ~~System MUST reject saving a community order result (COEW, COS, CONI) when the end date entered is on or before the hearing date, displaying the error "The end date must be in the future" at the field level and in the page-level error summary.~~ This ticket assumes AC1 is either already implemented or will be delivered separately; AC2 checks at "Save and continue" assume the order end date is already a valid future date when they run.
- **FR-002**: System MUST detect, at "Save and continue", when a community order's end date is strictly earlier than the end date, "End date of tag", or "Until" date of any of its child requirement results of type CUR, CURE, CURA, or AAR. These dates are supplied by the UI as `Prompt` entries in `ResultLineDto.prompts`, matched by `promptRef` (e.g. `"endDate"`, `"endDateOfTag"`, `"until"`) and read from `promptValue`.
- **FR-003**: When FR-002 triggers, the validation service MUST emit one separate `ValidationIssue` per violated requirement type using these exact display names: "Curfew (community requirement)" (CUR), "Curfew with electronic monitoring" (CURE), "Further curfew requirement made" (CURA), "Alcohol abstinence and monitoring" (AAR). Each violation type maps to an independent YAML condition (AC2a=CUR, AC2b=CURE, AC2c=CURA, AC2d=AAR), enabling per-condition runtime enable/disable via the `validation_rule` table. If both CUR and AAR violate on the same order, two distinct `ValidationIssue` entries appear in the response — one per requirement type.
- **FR-004**: Each AC2 `ValidationIssue` message MUST read: "The end date of the order must match or be longer than the end date of <<requirement display name>>" where `<<requirement display name>>` is the display name for that specific requirement type only (one requirement per message).
- **FR-007**: When one or more AC2 validation errors block navigation to Manage Hearings, the system MUST keep the user on the Enter Results screen and scroll them to the top, displaying a red error summary box with the bold heading "There is a Problem".
- **FR-008**: Each distinct validation error in the summary MUST be shown in bold red text, followed by "This affects: <<defendant name(s)>>" in red, listing all defendants whose results triggered that specific error. The API delivers this via `affectedDefendants: [{ defendantId }]` on each `ValidationIssue`; the UI looks up the defendant display name from `defendantId`.
- **FR-009**: When multiple distinct validation errors exist, ALL must appear in the summary.
- **FR-010**: For every offence/result combination that triggered a validation error, an inline red error message with a vertical left border MUST appear below the offence label and above the result on the Enter Results screen.
- **FR-011**: When multiple validation errors apply to a single offence/result, ALL errors for that result MUST be shown inline.
- **FR-012**: When any AC2 validation errors exist for any defendant in the hearing, the Share button MUST NOT be visible on the Manage Hearings screen — the Share button is a single hearing-level control, hidden for the whole hearing regardless of which defendant(s) have errors, and regardless of whether the user reached Manage Hearings via "Save and continue" or by navigating directly.
- **FR-013**: The Manage Hearings screen MUST NOT display validation error messages; errors are surfaced exclusively on the Enter Results screen.
- **FR-014**: Per-defendant isolation applies to error display only — inline errors and "This affects:" lists on the Enter Results screen MUST reference only the defendants whose results triggered each specific error. Valid defendants must never appear in an error's "This affects:" list. This does not imply independent per-defendant sharing; the Share button remains a single hearing-level control (see FR-012).

**Requirement duration validation (User Stories 4–7):**

- **FR-015**: System MUST detect, at "Save and continue", when a Curfew (Non-electronic monitoring) requirement (CUR) child result's "End date" does not equal "Start date" + "Curfew period" (in days) − 1 day.
- **FR-016**: System MUST detect, at "Save and continue", when a Curfew with electronic monitoring requirement (CURE) child result's "End date of tagging" does not equal "Start date of tagging" + "Curfew and electronic monitoring period" (in days) − 1 day.
- **FR-017**: System MUST detect, at "Save and continue", when an Alcohol Abstinence Monitoring requirement (AAR) child result's "Until" date does not equal the hearing date + "Number of days to abstain from consuming any alcohol" (in days) − 1 day.
- **FR-018**: For FR-015 and FR-016 violations, the validation service MUST emit a `ValidationIssue` (severity ERROR) with message "The end date for the Curfew Requirement does not match the period of the requirement." For FR-017 violations, the message MUST read "The end date for the Alcohol Abstinence Monitoring Requirement does not match the period of the requirement."
- **FR-019**: The inline (per-offence) rendering of each FR-018 message MUST additionally state the correctly calculated date, e.g. "The current recorded period would mean the end date should be <<calculated date>>", substituting the actual Start date (or Start date of tagging, or hearing date) + period − 1 day.
- **FR-020**: FR-015/FR-016/FR-017 violations MUST reuse the existing error summary (FR-007/FR-008/FR-009), inline error (FR-010/FR-011), and Share-button suppression (FR-012/FR-013) behaviour already defined for this feature — no new display mechanism is introduced.
- **FR-021**: When a duration-mismatch violation (FR-015/FR-016/FR-017) and an order-end-date violation (FR-002/FR-003) both apply to the same requirement/offence, both MUST appear as separate entries in the error summary and inline, per FR-009/FR-011.

### Key Entities

- **Community Order**: A sentencing result of type COEW, COS, or CONI. Represented as a `ResultLineDto` in `DraftValidationRequest.resultLines`. Its mandatory end date is carried as a named value in `ResultLineDto.prompts`.
- **Requirement (child result)**: A sub-result attached to a community order, also represented as a `ResultLineDto`. The date field for each requirement type is carried as a named prompt value in `ResultLineDto.prompts`: CUR → `endDate`, CURE → `endDateOfTag`, CURA → `endDate`, AAR → `until`.
- **`ResultLineDto.prompts`**: `List<Prompt>` — available from API library version `0.1.6` (now in use). Carries per-result named values entered by the clerk. Each `Prompt` has two `String` fields: `promptRef` (the prompt key, e.g. `"endDate"`, `"endDateOfTag"`, `"until"`) and `promptValue` (the entered value as a string, e.g. `"2026-11-30"`). Accessed via `getPromptRef()` and `getPromptValue()`.
- **Validation error**: A blocking issue produced by the validation service that prevents result sharing. Carries a message text, the set of affected defendants, and linkage to the specific offences/results that triggered it.
- **Defendant**: An individual in the hearing. A hearing may include multiple defendants; each defendant's results are validated independently.
- **Curfew period** / **Curfew and electronic monitoring period**: A whole number of days recorded against a CUR/CURE requirement, used together with that requirement's own Start date (or Start date of tagging) to calculate its expected end date.
- **Number of days to abstain from consuming any alcohol**: A whole number of days recorded against an AAR requirement, used together with the hearing date to calculate the expected "Until" date.
- **Hearing date**: The date of the hearing, carried on `DraftValidationRequest` as `hearingDay`; used as the reference start point for AAR's duration calculation.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** *(AC1 — out of scope)*: ~~End date in-the-past check~~ — covered by a separate ticket (Scenarios 1–5 excluded from this ticket's acceptance test suite.
- **SC-002**: 100% of community orders where any child requirement's date exceeds the order end date (AC2) are blocked at "Save and continue" with the requirement display name in the error message — zero missed violations.
- **SC-004**: The Share button is hidden on the Manage Hearings screen in 100% of cases where unresolved validation errors exist — regardless of the navigation path taken.
- **SC-005**: Error messages precisely identify only the defendants affected by each specific error — no defendant is incorrectly included or omitted from any "This affects:" list.
- **SC-006**: All 8 in-scope acceptance scenarios (Scenarios 6–13 from the original feature description) pass automated regression tests. Scenarios 1–5 (AC1) are excluded from this ticket's test suite.
- **SC-007**: Valid defendants are never listed in any error's "This affects:" line when other defendants in the same hearing have errors; however, the Share button is hidden for the whole hearing until all errors are resolved.
- **SC-008**: 100% of CUR, CURE, and AAR requirements whose recorded end date does not equal Start date (or hearing date) + period − 1 day are blocked at "Save and continue" with the correct requirement-specific error message and calculated correct date — zero missed violations, zero false positives when the dates match exactly.
- **SC-009**: All acceptance scenarios for User Stories 4–7 pass automated regression tests without changing the pass/fail outcome of any existing User Story 1–3 scenario (no regression on the already-shipped order-end-date check).

---

## Assumptions

- **A-002**: For AC2, an order end date equal to the requirement end date is valid (the error triggers only when the order end date is strictly before the requirement end date). Scenario 10 confirms this boundary.
- **A-003**: AC1 validation ("end date must be in the future") fires on "Save result details" and prevents saving the draft; AC2 validations fire on "Save and continue" after all individual results are saved. Both surface errors in the page-level "There is a problem" summary.
- **A-004**: AC1 (Scenarios 1–5, "The end date must be in the future") is **explicitly out of scope** for this ticket and will be delivered under a separate Jira ticket. AC2 validations run at "Save and continue" and assume the order end date has already passed any prior field-level checks.
- **A-005**: Multiple requirement violations on a single order (e.g. both CUR and AAR exceeding the order) each produce a separate error message — one per violated requirement type. Scenario 11 produces two `ValidationIssue` entries: one for CUR and one for AAR. The YAML+CEL engine evaluates each requirement condition independently; the UI renders them adjacently in the "There is a problem" summary.
- **A-006**: The validation service operates on draft hearing result data and does not modify or persist the hearing record.
- **A-007**: `ResultLineDto.prompts` (`List<Prompt>`, each with `promptRef: String` and `promptValue: String`) is available from upstream library version `0.1.6`, which is now the version in use. No upstream API change is required. No new result codes or DB migrations are needed beyond the YAML rules and new preprocessor.
- **A-008**: Short codes (COEW, COS, CONI, CUR, CURE, CURA, AAR) are already defined in the result code dictionary and are not modified by this feature.
- **A-009**: UI error display patterns (red box, bold heading, vertical border for inline errors) align with the existing GOV.UK Design System error summary and error message components already in use in the Enter Results screen.
- **A-010**: "Curfew period", "Curfew and electronic monitoring period", and "Number of days to abstain from consuming any alcohol" are recorded as whole numbers of days (not weeks/months). The calculated end date is Start date (or Start date of tagging, or hearing date) + period, minus 1 day — matching the acceptance criteria's explicit "− 1 day" convention.
- **A-011**: The requirement duration-mismatch check (User Stories 4–7, Jira `DD-41655`) is independent of and additive to the order-end-date-vs-requirement-end-date check already delivered in User Story 1 (`DR-COEW-001`, AC2a–AC2d). A single requirement may trigger both, one, or neither, depending on its data.
- **A-012**: Duration-mismatch dates are compared as calendar dates with no time-of-day component, consistent with A-002 for the order-end-date check.

---

## Clarifications

### Session 2026-05-20

- Q: Does `DraftValidationRequest` currently carry the hearing date, or would it need to be added to the upstream API DTO? → A: Hearing date is already present as the `hearingDay` field on `DraftValidationRequest` from the upstream API — no upstream DTO change is needed.
- Q: When multiple requirements on one order violate AC2 simultaneously, how should the errors be emitted? → A: ~~One combined error message naming all violating requirements together (not separate messages per requirement).~~ **Revised (2026-05-20 post-analyze)**: Separate — one `ValidationIssue` per violated requirement type. The YAML+CEL engine evaluates each condition (AC2a=CUR, AC2b=CURE, AC2c=CURA, AC2d=AAR) independently; combined-message semantics require Java post-processing which violates Constitution Principle I. FR-003, FR-004, A-005, and Edge Cases updated to reflect this.
- Q: Is the Share button a single hearing-level control or per-defendant? → A: Single Share button for the whole hearing — hidden if any defendant has unresolved errors. Per-defendant isolation applies to error display ("This affects:" lists) only, not to sharing.
- Q: Is AC1 ("end date must be in the future") already implemented or new work for this ticket? → A: Out of scope — AC1 will be handled by a separate ticket. This ticket covers AC2, AC4, and AC5 only (Scenarios 6–13).
- Additional context: Child result end dates (for CUR, CURE, CURA, AAR) and the community order end date (for COEW/COS/CONI) are received as `promptRef`/`promptValue` pairs in `ResultLineDto.prompts` (`List<Prompt>`). Confirmed present in API library `0.1.6` (now in use). `Prompt` has `getPromptRef(): String` and `getPromptValue(): String`.

### Session 2026-07-06 (Jira DD-41655 — requirement duration end date validation)

- Q: Is this duration-mismatch check (requirement's own end date vs. Start date + period − 1 day) the same rule as the existing order-end-date-vs-requirement-end-date check (User Story 1)? → A: No — they are independent checks on different date comparisons, both scoped to CUR/CURE/AAR requirements on a Community Order. Folded into this spec (rather than a separate spec file) because they share the same entities, actors, and already-built error-display mechanism (User Stories 2/3).
- Q: Does the new duration-mismatch check need its own error-summary/inline-error UI? → A: No — it reuses the existing mechanism verbatim (FR-020); User Story 7 exists only to confirm the new conditions integrate with it, not to build new UI.
- Q: What Jira ticket and branch does this work track under? → A: Jira `DD-41655`; branch `DD-41655-requirement-duration-validation`, based on `team/DD-41653` (the branch carrying the already-shipped User Story 1–3 work) rather than `main`.
