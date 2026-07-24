# Feature Specification: Imprisonment Result Age Restriction (DD-42950)

**Feature Branch**: `DD-42950-imprisonment-age-restriction`
**Created**: 2026-07-20
**Status**: Draft
**Input**: User description: "DD-42950 — Imprisonment result entered against a defendant under 21 years of age must block sharing with an error; defendants aged 21 or over must not be affected."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Imprisonment result recorded for a defendant aged 21 or over (Priority: P1)

A user with access to enter results is recording results for a defendant on a case. They enter an imprisonment-type result (Imprisonment `IMP`, Extended Sentence for Certain Violent or Sexual Offences `EXTIVS`, or Special Custodial Sentence `SPECC`) against one or more of that defendant's offences. The defendant is 21 years of age or over on the hearing date being resulted. When the user selects "Save and continue" to navigate to "Manage hearings", no error is shown and the user proceeds normally.

**Why this priority**: This is the baseline "no false positive" path. The rule must never obstruct a legitimate imprisonment result for an eligible defendant — if this fails, every adult defendant receiving a custodial sentence is blocked, making the feature unusable.

**Independent Test**: Submit a hearing with one defendant aged 21 or over on the hearing date, with an `IMP` (or `EXTIVS`/`SPECC`) result recorded against one of their offences. The validation response must contain no error from this rule, and the user is able to navigate to "Manage hearings".

**Acceptance Scenarios**:

1. **Given** a defendant who is 21 years of age or over on the hearing date, **When** an `IMP`, `EXTIVS`, or `SPECC` result is recorded against one of their offences and the user selects "Save and continue", **Then** the user navigates to "Manage hearings" and no error is shown.

---

### User Story 2 - Imprisonment result recorded for a defendant under 21 is blocked with an error (Priority: P1)

A user with access to enter results is recording results for a defendant on a case. They enter an imprisonment-type result (`IMP`, `EXTIVS`, or `SPECC`) against one or more of that defendant's offences, but the defendant is under 21 years of age on the hearing date being resulted. When the user tries to navigate to "Manage hearings" via "Save and continue", they remain on the enter-results screen and see a blocking error. The error states "There is a problem" and "The defendant is under 21 years of age and cannot receive a sentence of imprisonment", followed by which defendant(s) are affected when the hearing has more than one defendant. The user cannot share the result while this error is present.

**Why this priority**: This is the core legal safeguard the feature exists to deliver — preventing an unlawful imprisonment sentence on a defendant under 21 from ever being shared. Without this, the feature has no effect.

**Independent Test**: Submit a hearing with one defendant under 21 years of age on the hearing date, with an `IMP` (or `EXTIVS`/`SPECC`) result recorded against one of their offences. The validation response must contain a blocking error with the exact message text specified, and the result cannot be shared.

**Acceptance Scenarios**:

1. **Given** a defendant who is under 21 years of age on the hearing date, **When** an `IMP`, `EXTIVS`, or `SPECC` result is recorded against one of their offences and the user selects "Save and continue", **Then** the user remains on the enter-results screen and a blocking error is shown.
2. **Given** the error is shown, **When** the user reads it, **Then** it reads exactly: "There is a problem" followed by "The defendant is under 21 years of age and cannot receive a sentence of imprisonment".
3. **Given** the hearing contains more than one defendant, **When** the error is shown, **Then** it is followed by additional text identifying which defendant(s) are affected, in the form "This affects: <defendant name>, <defendant name>".
4. **Given** the hearing contains exactly one defendant, **When** the error is shown, **Then** no "This affects" list is required, since the affected defendant is unambiguous.
5. **Given** the error is present, **When** the user attempts to share the result without changing anything, **Then** sharing remains blocked.

---

### User Story 3 - Correcting the defendant's date of birth clears the error (Priority: P2)

Having seen the error from User Story 2, the user navigates to edit the case and amends the affected defendant's date of birth so that the defendant is 21 years of age or over on the hearing date. They return to "Enter results" and select "Save and continue" again. The error no longer appears, and the user can check and share the results.

**Why this priority**: This proves the rule re-evaluates on corrected data rather than "sticking" once raised, and gives the user a real path to resolution (the alternative being to change the recorded result itself, which is implicit in FR-006). It depends on User Story 2 existing first, so it is lower priority, but it is required before the feature is considered complete and safe to ship.

**Independent Test**: Reproduce the User Story 2 scenario, then resubmit the same hearing with only the defendant's date of birth changed such that they are now 21 or over on the hearing date. The validation response must no longer contain the error.

**Acceptance Scenarios**:

1. **Given** the error from User Story 2 has been raised for a defendant, **When** that defendant's date of birth is amended so they are 21 years of age or over on the hearing date, and the user selects "Save and continue" again, **Then** no error is shown and the user can check and share the results.

---

### Edge Cases

- **Defendant turns 21 on the hearing date itself** — treated as 21 years of age or over (no error). Age is evaluated as of, and inclusive of, the hearing date.
- **Multiple offences with imprisonment results for the same under-21 defendant** — the defendant is reported once in the "affects" list, not once per offence.
- **Multiple under-21 defendants each with an imprisonment result in the same hearing** — all affected defendants are named in the single error's "This affects" list.
- **An under-21 defendant has an imprisonment result, and a separate 21-or-over defendant in the same hearing also has an imprisonment result** — only the under-21 defendant appears in the "affects" list; the 21-or-over defendant does not block sharing.
- **Defendant's date of birth is missing, null, or not yet recorded** — the rule cannot determine age and must not raise this error for that defendant (fail-safe: no false-positive block on incomplete data). This MUST NOT cause the validation request to fail or error out, and MUST NOT prevent any other rule — or any other defendant's evaluation under this same rule — from being evaluated and reported normally. A missing date of birth degrades gracefully to "no output from this rule for this defendant," nothing more.
- **Result short code case variation (e.g. `imp`, `Imp`, `extivs`)** — matching against `IMP`, `EXTIVS`, `SPECC` is case-insensitive, consistent with other rules in this service.
- **Result is entered and then removed/changed to a non-imprisonment result before "Save and continue"** — the error does not fire, since no qualifying result remains against the offence.
- **Hearing already has an unrelated error or warning from another rule** — this rule's error is independent and additive; both are shown, and both must be resolved before sharing.
- **Operator has disabled this rule at runtime, or capped its severity below ERROR** — the rule produces no blocking output while so configured (existing severity-ceiling / rule-override mechanism; ceiling never promotes).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST recognise the following result short codes as imprisonment-type results: `IMP`, `EXTIVS`, `SPECC`. Matching MUST be case-insensitive.
- **FR-002**: The system MUST determine a defendant's age as of the hearing date being resulted, using the defendant's date of birth.
- **FR-003**: The system MUST treat a defendant as 21 years of age or over when their 21st birthday falls on or before the hearing date, and as under 21 otherwise.
- **FR-004**: The system MUST raise a blocking error when a defendant who is under 21 years of age on the hearing date has an imprisonment-type result (FR-001) recorded against one or more of their offences.
- **FR-005**: The system MUST NOT raise this error for a defendant who is 21 years of age or over on the hearing date, regardless of which results are recorded against their offences.
- **FR-006**: The error MUST remain in place, blocking the result from being shared, until either the imprisonment-type result is removed or replaced with a non-imprisonment result, or the defendant's recorded date of birth is corrected such that they are 21 years of age or over on the hearing date.
- **FR-007**: The error message MUST read exactly: "There is a problem" and "The defendant is under 21 years of age and cannot receive a sentence of imprisonment".
- **FR-008**: When more than one defendant on the hearing is affected, the error MUST be followed by additional text in the form "This affects: <defendant name>, <defendant name>" naming every affected defendant.
- **FR-009**: The error's severity MUST be of a blocking kind (equivalent to `ERROR`) — the user MUST NOT be able to proceed to share the result while the error is present.
- **FR-010**: The rule MUST be evaluated per defendant: a defendant is affected if at least one of their offences carries a qualifying imprisonment-type result; a defendant with none of their offences so affected MUST NOT appear in the affected list.
- **FR-011**: When a defendant's date of birth is missing, null, or cannot be determined, the system MUST NOT raise this error for that defendant (fail safe rather than false-positive block). This condition MUST be handled without throwing an exception or otherwise interrupting request processing: the overall validation request MUST still complete successfully, all other applicable rules MUST still be evaluated and their results MUST still be included in the response, and this rule MUST still be evaluated normally for every other defendant on the same hearing whose date of birth is present.
- **FR-012**: The rule MUST be subject to the existing runtime severity-ceiling model: an operator MUST be able to disable the rule entirely or cap its severity downward via the existing rule-configuration mechanism, without redeployment. Severity MUST NOT be promoted upward by the ceiling.
- **FR-013**: The rule MUST NOT mutate the request being validated, and MUST NOT depend on the outcome of any other validation rule.
- **FR-014**: The defendant's date of birth MUST be available to the validation process as part of the hearing data submitted for validation. *(Currently, the defendant record submitted for validation does not carry a date of birth — see Assumptions.)*

### Key Entities

- **Hearing (validation request)** — the unit of work submitted for validation. Contains a hearing date, one or more offences, one or more defendants, and the result lines recorded so far.
- **Defendant** — a person charged in the hearing. Identified by an id and a name. For this rule, also requires a date of birth so that age on the hearing date can be determined.
- **Offence** — a charge linked to a defendant.
- **Result line** — a recorded outcome linked to a specific offence. Carries a short code (e.g. `IMP`, `EXTIVS`, `SPECC`) identifying the type of result.
- **Imprisonment-type result** — a result line whose short code is one of `IMP`, `EXTIVS`, `SPECC` — result types that impose a custodial sentence and are therefore subject to the under-21 restriction.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of imprisonment-type results recorded against a defendant under 21 years of age are blocked from being shared until corrected.
- **SC-002**: 0% of imprisonment-type results recorded against a defendant aged 21 or over are blocked by this rule (no false positives).
- **SC-003**: Users can identify every affected defendant directly from the error message without needing to search the hearing, in hearings with any number of defendants.
- **SC-004**: A user who corrects the underlying issue (result or date of birth) can confirm the error has cleared and complete sharing within the same "Save and continue" attempt, with no additional navigation required beyond editing the case and returning to enter results.

## Assumptions

- The defendant's date of birth is not currently part of the data submitted to the validation process; this feature assumes that data will be made available (an upstream contract addition) before this rule can be evaluated. Until then, per FR-011's fail-safe behaviour, the rule cannot fire.
- "Hearing date" refers to the date the hearing is being resulted against, which already exists on the validation request.
- Age is calculated using calendar-date comparison (date of birth vs. hearing date), not time-of-day precision.
- The three short codes `IMP`, `EXTIVS`, and `SPECC` are the complete and only set of "imprisonment-type" results in scope for this rule; other custodial-adjacent result types (e.g. suspended sentences) are out of scope unless explicitly added later.
- The error is raised once per hearing (not once per offence) and lists all affected defendants together, consistent with how the "This affects" text is described in the acceptance criteria.
- Editing a case's defendant date of birth and re-running validation is an existing capability outside this rule's scope; this feature only concerns the validation outcome, not the case-editing UI itself.
