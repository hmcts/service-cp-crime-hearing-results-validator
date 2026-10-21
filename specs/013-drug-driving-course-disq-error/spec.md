# Feature Specification: Block Disqualification with Course Reduction on Drug Driving Offences

**Feature Branch**: `CRA-345-drug-driving-disq-block`
**Jira**: CRA-345
**Created**: 2026-10-16
**Status**: Implemented
**Input**: CRA-345 — Block disqualification with course reduction (DDRCO / DDRCOT) on drug driving offences. Common Platform currently allows adding DDRCO or DDRCOT to a drug driving offence, producing an unlawful result. This adds an ERROR-level validation check that blocks sharing until the user replaces the result with a lawful disqualification.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Drug driving offence with course-reduction disqualification triggers error (Priority: P1)

A court user is resulting a drug driving offence (one of the five triggering offences: RT88584, RT88586, RT88584B, RT88340, RA88045) and has added a course-reduction disqualification result — either DDRCO (Driving Disqualification obligatory – Reduction for course) or DDRCOT (Obligatory disqualification until extended test passed – reduction for course) — to that offence. When the user selects Save and continue, the system raises an ERROR against that offence, preventing the unlawful result from being shared with the DVLA and the defendant.

**Why this priority**: This is the core business rule. Without it, unlawful sentencing results reach the DVLA and the defendant and require manual amendment and resharing once discovered. Every other user story either describes how the error is presented (P2) or clarifies boundary conditions (P2).

**Independent Test**: Can be fully tested by constructing a hearing with a single triggering drug driving offence (e.g. RT88584), adding a DDRCO result to it, calling the validation endpoint, and asserting that exactly one ERROR issue is returned for that offence with the prescribed message.

**Acceptance Scenarios**:

1. **Given** a hearing contains one of the five triggering drug driving offences (RT88584, RT88586, RT88584B, RT88340, RA88045), **And** the user has added result DDRCO to that offence, **When** the user selects Save and continue, **Then** an ERROR is raised against that offence with the message "You cannot add a disqualification with a reduction for attending a course to a drug driving offence. Remove this result and add the correct disqualification."
2. **Given** a hearing contains one of the five triggering offences, **And** the user has added result DDRCOT to that offence, **When** the user selects Save and continue, **Then** an ERROR is raised against that offence with the same prescribed message.
3. **Given** a hearing contains more than one triggering offence and each has DDRCO or DDRCOT added, **When** the user selects Save and continue, **Then** a separate ERROR is raised for each affected offence individually (one error per offence).

---

### User Story 2 - Error blocks sharing and clears when the unlawful result is removed (Priority: P1)

While an unresolved DDRCO or DDRCOT error exists on any offence in the hearing, the share action is unavailable. Once the user removes the offending result or replaces it with a lawful disqualification (such as DDO), saves again, and no other errors remain, sharing becomes available.

**Why this priority**: Blocking sharing is the whole point of this validation. Without it, the error is advisory only and the unlawful result can still reach the DVLA, defeating the purpose of the story. Resolving and unblocking is an equally critical path — users need a clear route out of the error state.

**Independent Test**: Can be tested in two sequential calls: first validate with DDRCO present and assert sharing is blocked (ERROR returned); then validate again with DDRCO replaced by DDO on the same offence and assert no errors are returned and sharing is no longer blocked.

**Acceptance Scenarios**:

1. **Given** an unresolved DDRCO or DDRCOT error exists on any offence in the hearing, **When** the user attempts to share results, **Then** the share button is unavailable and results cannot be shared until every instance of the error is resolved.
2. **Given** an error from AC1 exists on an offence, **When** the user removes DDRCO or DDRCOT from that offence and selects Save and continue, **Then** the error for that offence is cleared and, provided no other errors exist in the hearing, sharing becomes available.
3. **Given** an error from AC1 exists on an offence, **When** the user replaces DDRCO or DDRCOT with a different disqualification result (e.g. DDO) and selects Save and continue, **Then** the error for that offence is cleared and sharing becomes available (assuming no other errors).

---

### User Story 3 - No false positives: rule does not fire when conditions are not met (Priority: P2)

The rule must not raise errors for legitimate results. Adding DDRCO or DDRCOT to an offence that is not in the triggering list must not produce an error. Adding any disqualification other than DDRCO or DDRCOT (e.g. DDO, DDOTE, DDRCD) to a triggering offence must also not produce an error from this rule.

**Why this priority**: False positives block legitimate sharing and erode user trust in the validation system. Boundary accuracy is critical but logically downstream of the happy-path error trigger (User Stories 1 and 2).

**Independent Test**: Can be tested with two independent requests: (a) a triggering offence with DDO (no DDRCO/DDRCOT) — assert no error from this rule; (b) a non-triggering offence with DDRCO — assert no error from this rule.

**Acceptance Scenarios**:

1. **Given** DDRCO or DDRCOT is added to an offence whose CJS code is NOT one of the five triggering codes, **When** the user selects Save and continue, **Then** no error is raised by this rule for that offence.
2. **Given** a triggering offence has a disqualification result other than DDRCO or DDRCOT recorded against it (e.g. DDO, DDOTE, DDRCD), **When** the user selects Save and continue, **Then** no error is raised by this rule for that offence.
3. **Given** a hearing has no triggering offences at all, **When** the user selects Save and continue, **Then** this rule produces no output.

---

### User Story 4 - Amend and reshare: error reappears when an unlawful result is added post-sharing (Priority: P2)

Results have already been shared for a triggering drug driving offence. A user subsequently amends the results and adds DDRCO or DDRCOT to that offence. When the amendment is saved, the same ERROR is raised in Enter Results. The amend-and-reshare flow is consistent with how existing errors behave on amendment: Request approval and Validate amendments are hidden, leaving only Cancel or Reject (contingent on CRA-282 being delivered).

**Why this priority**: Prevents the unlawful result from re-entering the system via the amendment pathway. Logically depends on User Story 1 (same error, same message), and its full UI behaviour is contingent on the separate CRA-282 story being delivered.

**Independent Test**: Can be fully tested at the validation API level by submitting a request for a previously-shared hearing with DDRCO added to a triggering offence and asserting the same ERROR is returned — the amendment-flow UI behaviour (hiding Request approval / Validate amendments) is outside the scope of this service's validation endpoint.

**Acceptance Scenarios**:

1. **Given** results have already been shared for a triggering offence, **And** a user amends the results to add DDRCO or DDRCOT to that offence, **When** the amendment is saved, **Then** the same ERROR is raised in Enter Results with the same prescribed message.
2. **Given** the ERROR from Scenario 1 is present during amendment, **When** CRA-282 has been delivered, **Then** Request approval and Validate amendments are hidden, leaving only Cancel or Reject, consistent with existing amend-and-reshare error behaviour.

---

### Edge Cases

- What happens when a hearing has multiple defendants, each with their own triggering offence carrying DDRCO or DDRCOT? → A separate ERROR is raised for each affected offence; the error summary at the top of the page includes all defendants' names.
- What happens when the same offence in a hearing has both DDRCO and another result recorded? → The rule evaluates whether DDRCO (or DDRCOT) is present on a triggering offence — it fires regardless of other results on the same offence.
- What happens when DDRCO result code casing differs (e.g. `ddrco` vs `DDRCO`)? → Matching is case-insensitive, consistent with existing short-code checks in this service.
- What happens when a triggering offence has no results recorded at all? → No error is raised by this rule; a result of DDRCO or DDRCOT must be present for the condition to trigger.
- What happens when a hearing contains both triggering and non-triggering offences, and only the non-triggering one has DDRCO? → No error is raised by this rule; only the combination of a triggering offence code AND a DDRCO/DDRCOT result triggers the error.
- What happens when a triggering offence already had DDRCO shared and then results are amended to remove it? → No error is raised (User Story 2, resolving path); sharing is unblocked once the offending result is gone.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST identify a "drug driving offence" as any offence whose CJS offence code is one of: `RT88584`, `RT88586`, `RT88584B`, `RT88340`, `RA88045`. Only offences matching one of these codes are evaluated by this rule.
- **FR-002**: For each drug driving offence in the hearing, the system MUST check whether any result line recorded against that offence carries the short code `DDRCO` or `DDRCOT`. If at least one such result is present, the system MUST produce an ERROR for that offence.
- **FR-003**: The error message text MUST be exactly: "You cannot add a disqualification with a reduction for attending a course to a drug driving offence. Remove this result and add the correct disqualification."
- **FR-004**: The error MUST be an offence-level issue at ERROR severity, scoped to the individual offence(s) that breach the rule; other offences in the same hearing MUST NOT be affected by this rule.
- **FR-005**: Short-code matching for `DDRCO` and `DDRCOT` MUST be case-insensitive, consistent with existing short-code checks in this service.
- **FR-006**: When multiple triggering offences in the same hearing each have DDRCO or DDRCOT recorded, the system MUST raise a separate ERROR for each affected offence.
- **FR-007**: The ERROR from this rule MUST block sharing. The share action MUST be unavailable while any unresolved instance of this error exists in the hearing.
- **FR-008**: Removing DDRCO or DDRCOT from an affected offence, or replacing it with a non-triggering disqualification result, MUST clear the error for that offence on the next validation run.
- **FR-009**: The rule MUST NOT raise an error when DDRCO or DDRCOT is recorded against an offence whose CJS code is not in the triggering list (FR-001).
- **FR-010**: The rule MUST NOT raise an error when a disqualification result other than DDRCO or DDRCOT (e.g. DDO, DDOTE, DDRCD) is recorded against a triggering offence.
- **FR-011**: The same ERROR and message MUST be produced when results have already been shared and are subsequently amended to include DDRCO or DDRCOT on a triggering offence (amend-and-reshare path, contingent on CRA-282 for the UI suppression behaviour).

### Key Entities

- **Drug driving offence**: An offence whose CJS offence code is one of `RT88584`, `RT88586`, `RT88584B`, `RT88340`, or `RA88045`. These are the five DVLA-notifiable drug driving offence codes in scope for this rule.
- **Course-reduction disqualification result**: A result line carrying short code `DDRCO` (Driving Disqualification obligatory – Reduction for course) or `DDRCOT` (Obligatory disqualification until extended test passed – reduction for course). Both are unlawful when applied to a drug driving offence.
- **Validation error**: An offence-level ERROR issue produced by this rule. Errors block the share action until resolved; they are distinct from warnings, which are advisory only.
- **Triggering combination**: The pair (drug driving offence code ∈ {RT88584, RT88586, RT88584B, RT88340, RA88045}) × (result short code ∈ {DDRCO, DDRCOT}). Any occurrence of this pair in the hearing produces one ERROR per offence instance.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every drug driving offence carrying DDRCO or DDRCOT produces exactly one ERROR with the prescribed message — zero omissions and zero duplicates per offence, across all test scenarios.
- **SC-002**: No false positives: DDRCO/DDRCOT on non-triggering offences and non-DDRCO/DDRCOT disqualifications on triggering offences each produce zero errors from this rule, validated across at least four distinct bypass scenarios (one per AC6 variant).
- **SC-003**: The service returns at least one issue with `severity=ERROR` from this rule in 100% of cases where a triggering combination (drug driving offence code × DDRCO/DDRCOT result) is present, which prevents sharing. Frontend availability of the share action is a UI-layer concern out of scope for this service.
- **SC-004**: Removing or replacing the offending result clears the error in 100% of cases on the subsequent validation call, restoring sharing availability when no other errors remain.
- **SC-005**: In a multi-offence hearing where two or more triggering offences each have DDRCO or DDRCOT, errors are isolated per offence with no spillover — one ERROR per affected offence, none on unaffected offences.
- **SC-006**: The error is returned at ERROR severity in 100% of triggered cases, never at WARNING severity, and always blocks sharing.
- **SC-007**: The amend-and-reshare path produces the same ERROR as the initial-entry path — zero regression between first-entry and amendment scenarios.

## Assumptions

- The five triggering offence codes (`RT88584`, `RT88586`, `RT88584B`, `RT88340`, `RA88045`) are treated as a fixed, exhaustive list for this story. No dynamic lookup against offence reference data is required — unlike the sexual-offence notification requirement rule (CRA-XXX/009), the drug driving offence set is small, stable, and fully enumerated in the Jira ticket. The codes are embedded directly in the YAML rule configuration.
- The rule requires a new Java preprocessor type (`DrugDrivingCourseReductionPreprocessor`, qualifier `drug-driving-course-reduction`). Although the CEL condition shape matches DR-DISQ-002 (`qualifyingCount > 0`), DR-DISQ-013 is presence-based (fires when DDRCO/DDRCOT is present on any result line) whereas DR-DISQ-002 is absence-based (fires when DDOTE/DDOTEL is absent after a final non-excluded result exists). These logic differences require separate preprocessors to avoid coupling two unrelated rules.
- Short-code matching is case-insensitive, consistent with every other result-code check in this service.
- The amend-and-reshare UI behaviour (suppression of Request approval / Validate amendments) is gated on CRA-282 being delivered; this rule only needs to return the ERROR — it does not need to know whether the results were previously shared.
- The GDS error presentation requirements (AC2 — red border, "Error:" prefix, summary with role="alert", defendant name in multi-defendant hearings) are handled by the front-end using the ERROR issues returned by this service. This service is scoped to returning the correct error issue with the correct severity, message, and affected offence set; presentation is out of scope here.
- Rule ID: `DR-DISQ-013`. Rule priority: `13000`. SQL migration: `V1.014__insert_dr_disq_013.sql`.