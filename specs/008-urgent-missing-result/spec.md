# Feature Specification: Urgent Missing Result Warning (Crown Court)

**Feature Branch**: `CRA-22-urgent-missing-result`
**Created**: 2026-08-19
**Status**: Draft
**Jira**: CRA-22
**Input**: PDF — "Functional Delivery - 8. Results validation - Inform the user if URGENT result missing - Crown Court"

## User Scenarios & Testing *(mandatory)*

### User Story 1 – Warning when conditional bail ends and URGENT is absent (Priority: P1)

A caseworker is entering results for a Crown Court hearing. One or more offences for a defendant previously had conditional bail. All of those conditional-bail offences have now been resulted with bail-ending short codes (any combination of: final/inactive result, sentence deferred, remand-in-custody variants, or warrant without bail). None of those offences include the `URGENT` result. When the caseworker selects "Save and continue" to navigate to Manage Hearings, they see a defendant-level warning advising them that the defendant's conditional bail has ended and that they may need to add the `URGENT` result and select "Bail conditions cancelled".

**Why this priority**: This is the core rule — AC1 through AC5 in CRA-22. Without it the feature does not exist.

**Independent Test**: Construct a Crown Court hearing where a defendant has one or more conditional-bail offences all resulted with bail-ending codes and none carrying `URGENT`. Call the validation endpoint and assert the defendant-level warning is returned with the exact prescribed message.

**Acceptance Scenarios**:

1. **Given** a Crown Court hearing where a defendant has one or more offences that previously had conditional bail, **And** all those offences are resulted with a final/inactive result (offence becomes inactive due to sentence, e.g. `COEW`, `IMP`, or end-offence outcomes such as discharged, dismissed, defendant deceased), **And** none of those offences include `URGENT`, **When** validation is triggered, **Then** a WARNING is returned at defendant level with the prescribed message.
2. **Given** all conditional-bail offences for a defendant are resulted with sentence deferred (`DS`) and none include `URGENT`, **When** validation is triggered, **Then** a defendant-level WARNING is returned.
3. **Given** all conditional-bail offences for a defendant are resulted with one or more remand-in-custody short codes (`RI`, `RIYDA`, `RIH`, `RIB`, `RILA`, `RILAB`, `REMYD`) and none include `URGENT`, **When** validation is triggered, **Then** a defendant-level WARNING is returned.
4. **Given** all conditional-bail offences for a defendant are resulted with warrant without bail (`WOFN`) and none include `URGENT`, **When** validation is triggered, **Then** a defendant-level WARNING is returned.
5. **Given** a defendant has four conditional-bail offences resulted with a mix (one final result, one `DS`, two `RI`) and none include `URGENT`, **When** validation is triggered, **Then** a single defendant-level WARNING is returned.
6. **Given** multiple defendants in the hearing each independently meeting the warning condition, **When** validation is triggered, **Then** a separate defendant-level warning is produced for each qualifying defendant.

---

### User Story 2 – Warning suppressed when URGENT is present on any conditional-bail offence (Priority: P2)

A caseworker has entered a bail-ending result but has also included the `URGENT` result on at least one of the conditional-bail offences. No warning should appear for that defendant.

**Why this priority**: False positives erode trust. If the caseworker has already recorded `URGENT`, the warning must not fire.

**Independent Test**: Construct a hearing where at least one conditional-bail offence carries `URGENT` alongside a bail-ending result. Assert no warning is returned for that defendant.

**Acceptance Scenarios**:

1. **Given** all conditional-bail offences have bail-ending results, **And** at least one of those offences also has the `URGENT` result, **When** validation is triggered, **Then** no warning is produced for that defendant.
2. **Given** a defendant has two conditional-bail offences — one with `RI` + `URGENT`, one with `DS` — **When** validation is triggered, **Then** no warning is produced (`URGENT` on any qualifying offence suppresses the warning).

---

### User Story 3 – Warning not triggered when not all conditional-bail offences are bail-ended (Priority: P3)

If one or more conditional-bail offences for a defendant have not yet received a bail-ending result in this hearing, the warning condition is not met.

**Why this priority**: Prevents premature warnings during partial result entry.

**Independent Test**: Construct a hearing where one conditional-bail offence is resulted with `RI` and another conditional-bail offence carries only a non-bail-ending result. Assert no warning.

**Acceptance Scenarios**:

1. **Given** a defendant has two conditional-bail offences, one resulted with `RI` and one with a non-bail-ending result, **When** validation is triggered, **Then** no warning is produced for that defendant.

---

### User Story 4 – Warning is advisory only; sharing is not blocked (Priority: P4)

The warning is informational. The caseworker can still share results without adding `URGENT` or making any other change.

**Why this priority**: The business explicitly requires a WARNING (not ERROR) — confirmed in CRA-22 comments correcting the earlier "unable" typo to "able".

**Independent Test**: Assert the validation response carries severity WARNING (not ERROR) and that sharing is not blocked.

**Acceptance Scenarios**:

1. **Given** the warning condition is met, **When** validation runs, **Then** the issue is returned at severity WARNING, not ERROR.
2. **Given** the warning is present, **When** the caseworker chooses to share without remediation, **Then** sharing is not blocked by this rule.

---

### User Story 5 – Warnings from this rule coexist with warnings and errors from other rules (Priority: P5)

When the entered results trigger warnings or errors from multiple validation rules simultaneously, all are displayed together in Manage Hearings. Defendant-level and offence-level warnings are each shown per their respective designs.

**Why this priority**: The warning aggregation behaviour (AC6, AC7) is a cross-rule concern required for a coherent user experience.

**Independent Test**: Construct a hearing that triggers both this rule and another active validation rule; assert both issues appear in the response without either suppressing the other.

**Acceptance Scenarios**:

1. **Given** a hearing that triggers a defendant-level warning from this rule and an offence-level warning from another rule, **When** validation runs, **Then** both warnings appear in the response without either suppressing the other.
2. **Given** multiple defendants each triggering this rule, **When** validation runs, **Then** a separate defendant-level warning appears for each qualifying defendant.

---

### Edge Cases

- What if a defendant has no offences previously on conditional bail? → No warning — the condition requires at least one conditional-bail offence.
- What if a conditional-bail offence has no result entered in this hearing? → That offence is not bail-ended; the condition is not met for that defendant.
- What if a defendant has a mix of conditional-bail and non-conditional-bail offences? → Only the conditional-bail offences are evaluated; non-conditional-bail offences are irrelevant to this rule.
- What if the same offence carries both a bail-ending result and `URGENT`? → The presence of `URGENT` suppresses the warning; the caseworker has already acted.
- What if the same offence carries multiple bail-ending results of different categories? → As long as `URGENT` is absent, the bail-ending condition is satisfied for that offence.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The rule MUST apply only to Crown Court hearings. Magistrates Court is covered by a separate rule (CRA-28).
- **FR-002**: For each defendant in the hearing, the system MUST identify all offences that previously had conditional bail status ("conditional-bail offences"). If a defendant has no conditional-bail offences, no further evaluation is performed for that defendant.
- **FR-003**: The system MUST classify the following result short codes as bail-ending results:
  - **Final/inactive results**: any result that causes the offence to become inactive (sentenced — e.g. `COEW`, `IMP`; or end-offence outcomes — discharged, dismissed, defendant deceased). This is determined by an `isFinalResult` flag on the result line rather than by enumerating every short code.
  - **Sentence deferred**: `DS`
  - **Remand in custody variants**: `RI`, `RIYDA`, `RIH`, `RIB`, `RILA`, `RILAB`, `REMYD`
  - **Warrant without bail**: `WOFN`
- **FR-004**: For each defendant, the system MUST determine whether ALL of that defendant's conditional-bail offences have received at least one bail-ending result (FR-003). If any conditional-bail offence has no bail-ending result in the current hearing, the warning condition is not met.
- **FR-005**: For each defendant where FR-004 is satisfied, the system MUST check whether ANY of the conditional-bail offences has the `URGENT` result short code. If at least one conditional-bail offence has `URGENT`, no warning is produced for that defendant.
- **FR-006**: When FR-004 is satisfied and FR-005 finds no `URGENT` result, the system MUST produce a WARNING at defendant level for the affected defendant.
- **FR-007**: The warning message text MUST be exactly: `"The defendant's conditional bail has ended. You may need to add the URGENT result and select "Bail conditions cancelled" on one of the offences before sharing."`
- **FR-008**: The warning MUST be scoped to the defendant whose conditional-bail offences triggered the rule. Other defendants in the hearing are evaluated independently.
- **FR-009**: The warning issue MUST carry severity WARNING, not ERROR, so that result sharing is not blocked.
- **FR-010**: Warnings from this rule MUST be returned alongside issues from all other active validation rules in the same validation response.

### Key Entities

- **Defendant**: A person charged in the hearing. Has an identifier and one or more offences.
- **Offence**: A charge against a defendant. Carries a `previousConditionalBail` flag indicating whether it was on conditional bail at a prior hearing, plus a list of result lines entered in the current hearing.
- **Result line**: An outcome recorded against an offence. Carries a short code (e.g. `RI`, `DS`, `WOFN`, `URGENT`) and an `isFinalResult` boolean indicating whether the offence becomes inactive.
- **Conditional-bail offence**: An offence where `previousConditionalBail` is `true`.
- **Bail-ending result**: A result line whose short code is in the enumerated set (FR-003) or whose `isFinalResult` flag is `true`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All five bail-ending trigger categories (final result, DS, remand variants, WOFN, mixed) each produce the defendant-level warning in 100% of test scenarios when `URGENT` is absent.
- **SC-002**: No false positives: whenever `URGENT` is present on any conditional-bail offence for a defendant, zero warnings are produced for that defendant.
- **SC-003**: The warning is returned at WARNING severity in 100% of triggered cases — never at ERROR severity.
- **SC-004**: Multi-defendant hearings correctly isolate the warning to each qualifying defendant with no cross-defendant spillover.
- **SC-005**: Result sharing is not blocked when the only issues present are warnings from this rule.
- **SC-006**: Warnings from this rule appear alongside warnings and errors from other concurrently triggered rules in every combined-warning scenario.

## Assumptions

- **Upstream API dependency (CHD-2485 — blocker)**: Delivery of CHD-2485 is required to provide offence-level bail status, including the `previousConditionalBail` field. Until this upstream change is available, the rule cannot be implemented.
- **`previousConditionalBail` field**: A boolean on each offence record indicating whether it had conditional bail at a prior hearing. The exact field name will be confirmed when CHD-2485 is delivered.
- **`isFinalResult` flag**: Result lines carry (or will carry) an `isFinalResult` boolean identifying outcomes that make an offence inactive. This avoids enumerating every inactive-offence short code and keeps the rule future-proof.
- **Crown Court scoping**: The hearing payload contains a court type indicator that can be used to limit this rule to Crown Court hearings only.
- **Defendant-level warning**: One warning per qualifying defendant referencing all conditional-bail offences collectively — not a separate warning per offence. This matches the AC7 design.
- **Warning is non-blocking by design**: The product decision (confirmed in CRA-22 comments) is that this is a WARNING, not an ERROR. The earlier "unable" wording in the ACs was a copy/paste typo confirmed corrected to "able".
- **Rule scope**: Crown Court only. Magistrates Court behaviour is specified separately under CRA-28 (currently blocked).
- **`URGENT` short code**: The exact short code is the string `URGENT` (all caps), as stated in the acceptance criteria.
