# Feature Specification: URGENT Result Missing Warning (Crown Court)

**Feature Branch**: `CRA-22-urgent-missing-result`
**Created**: 2026-09-02
**Status**: Draft
**Jira**: CRA-22
**Input**: User description: "CRA-22 Functional Delivery - 8. Results validation - Inform the user if URGENT result missing - Crown Court"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conditional Bail Ends with Bail-Ending Result, No URGENT (Priority: P1)

A Crown Court user finalises resulting for a defendant whose offences carried conditional bail. All of those offences are now resulted with bail-ending outcomes (any combination of final sentence, deferred sentence, remand in custody/care/hospital, or warrant without bail), but none includes the URGENT result. The user should receive a defendant-level warning before sharing so they can add URGENT if needed.

**Why this priority**: Core business requirement — guards against conditional bail ending without the URGENT result being recorded, which would leave custody services uninformed.

**Independent Test**: Submit a validation request containing a defendant with one or more conditional-bail offences, all resulted with bail-ending results and no URGENT result. Verify a defendant-level WARNING is returned with the prescribed message.

**Acceptance Scenarios**:

1. **Given** a defendant has offences with conditional bail remand status and **When** all of those offences are resulted with a final result (result category F) and none carries the URGENT result, **Then** a defendant-level WARNING is returned: _"The defendant's conditional bail has ended. You may need to add the URGENT result and select "Bail conditions cancelled" on one of the offences before sharing."_

2. **Given** a defendant has conditional-bail offences and **When** all of those offences receive a sentence-deferred result (DS) and none carries URGENT, **Then** the same defendant-level WARNING is returned.

3. **Given** a defendant has conditional-bail offences and **When** all of those offences receive a remand-in-custody/care/YDA/hospital result (RI, RIYDA, RIH, RIB, RILA, RILAB, or REMYD) and none carries URGENT, **Then** the same defendant-level WARNING is returned.

4. **Given** a defendant has conditional-bail offences and **When** all of those offences receive a warrant-without-bail result (WOFN) and none carries URGENT, **Then** the same defendant-level WARNING is returned.

5. **Given** a defendant has four conditional-bail offences with a mix of bail-ending result types (e.g. Offence 1 final, Offence 2 DS, Offence 3 RI, Offence 4 RI) and none carries URGENT, **Then** the same defendant-level WARNING is returned (mixed bail-ending types are treated identically).

---

### User Story 2 - Warning Suppressed When URGENT is Present (Priority: P1)

A Crown Court user results conditional-bail offences with bail-ending results and also includes the URGENT result on at least one of those offences. The warning must not fire — the user has already taken the required action.

**Why this priority**: Equally critical to P1 — false positives undermine trust and create unnecessary noise for users who have correctly recorded URGENT.

**Independent Test**: Submit the same validation request as Story 1 but with URGENT added to at least one conditional-bail offence. Verify no WARNING is returned for this rule.

**Acceptance Scenarios**:

1. **Given** a defendant has conditional-bail offences all resulted with bail-ending results, **When** at least one of those offences also carries the URGENT result, **Then** no WARNING is returned for this rule.

---

### User Story 3 - No Warning When Bail Has Not Fully Ended (Priority: P2)

A Crown Court user results a hearing where not all conditional-bail offences have been given bail-ending results — at least one conditional-bail offence still has bail continuing. No warning should fire.

**Why this priority**: Prevents premature warnings when conditional bail is still active for some offences.

**Independent Test**: Submit a validation request where a conditional-bail offence receives a non-bail-ending result (e.g., adjourned, bail continued). Verify no WARNING is returned.

**Acceptance Scenarios**:

1. **Given** a defendant has conditional-bail offences where at least one is not resulted with a bail-ending result (bail still active for that offence), **When** validating, **Then** no WARNING is returned.

2. **Given** a defendant has no offences with conditional bail remand status, **When** validating, **Then** no WARNING is returned.

---

### User Story 4 - Per-Defendant Evaluation in Multi-Defendant Hearings (Priority: P2)

When a hearing contains multiple defendants, the warning is evaluated independently for each defendant and fires only for those who meet the criteria.

**Why this priority**: Multi-defendant hearings are common in Crown Court. Incorrect cross-defendant firing would be a significant quality defect.

**Independent Test**: Submit a hearing with two defendants — one qualifying (all conditional-bail offences ended, no URGENT) and one not qualifying (no conditional bail or URGENT present). Verify the WARNING appears only against the qualifying defendant.

**Acceptance Scenarios**:

1. **Given** a hearing has Defendant A (all conditional-bail offences resulted with bail-ending results, no URGENT) and Defendant B (no conditional-bail offences), **When** validating, **Then** only Defendant A receives the WARNING.

---

### Edge Cases

- A defendant whose conditional-bail offences are a subset of all their offences: only the conditional-bail offences are evaluated; other offences are ignored for this rule.
- A conditional-bail offence with no result line recorded yet: the offence has not received a bail-ending result, so the warning does not fire.
- URGENT present on a non-conditional-bail offence but absent from all conditional-bail offences: the warning still fires (URGENT on non-conditional-bail offences does not suppress the warning).
- The same defendant appears in multiple hearings: this rule evaluates only the current hearing's result lines.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST issue a defendant-level WARNING when ALL of a defendant's offences that carry a conditional bail remand status are resulted with bail-ending results (final result Category F, sentence deferred DS, remand-in-custody family RI/RIYDA/RIH/RIB/RILA/RILAB/REMYD, or warrant without bail WOFN) and NONE of those offences carries the URGENT result.
- **FR-002**: The WARNING message text MUST be exactly: _"The defendant's conditional bail has ended. You may need to add the URGENT result and select "Bail conditions cancelled" on one of the offences before sharing."_
- **FR-003**: The warning MUST be issued at defendant level, not per offence.
- **FR-004**: The system MUST suppress the warning when at least one of the defendant's conditional-bail offences carries the URGENT result.
- **FR-005**: The system MUST suppress the warning when a defendant has no offences with conditional bail remand status.
- **FR-006**: The system MUST suppress the warning when at least one conditional-bail offence has not received a bail-ending result in the current hearing.
- **FR-007**: The warning MUST be advisory only (severity WARNING); the user MUST be able to proceed and share results without resolving it.
- **FR-008**: The system MUST evaluate this rule independently for each defendant in the hearing.
- **FR-009**: The system MUST derive conditional bail status from the remand status field on each offence (provided by the hearing data sourced from CHD-2485).

### Key Entities

- **Defendant**: Person charged. The warning is issued at this level. A hearing may have multiple defendants each evaluated independently.
- **Offence**: A charge linked to a defendant. Carries a remand status field that indicates whether the defendant was on conditional bail for that charge.
- **Result line**: An outcome recorded against an offence in this hearing. Carries a short code (e.g., COEW, IMP, DS, RI, WOFN, URGENT).
- **Conditional bail remand status**: A value on the offence remand status field indicating the defendant was released subject to bail conditions. Delivered via CHD-2485.
- **Bail-ending result**: A result that terminates conditional bail on an offence — any Category F final result, DS, any of the RI/RIYDA/RIH/RIB/RILA/RILAB/REMYD short codes, or WOFN.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The WARNING is returned for every defendant meeting the criteria across all five acceptance criteria (AC1–AC5 from CRA-22): sentenced/final, deferred, remanded in custody, warrant without bail, and mixed bail-ending types.
- **SC-002**: Zero false-positive warnings are generated when URGENT is present on any conditional-bail offence, or when no conditional bail is active, or when bail has not fully ended for all conditional-bail offences.
- **SC-003**: The rule evaluates independently per defendant — multi-defendant hearings produce warnings only for qualifying defendants.
- **SC-004**: The validation response continues to allow the user to share results without resolving this WARNING (severity is advisory, not blocking).

## Assumptions

- The conditional bail remand status field on each offence is delivered in the hearing result payload. This is gated on CHD-2485 (blocked as of 2026-07-16; Simon Bartlett confirmed dependency).
- The exact URGENT short code is `URGENT` (all uppercase, no variation).
- The bail-ending result short codes and Category F definition are stable and documented in the product's result code reference; any addition or removal requires a spec update.
- This feature covers back-end validation only. The UI rendering of the warning (bold black text, exclamation icon in a black circle, left-aligned below defendant/URN — AC7 of CRA-22) is the responsibility of the front-end team and is out of scope for this service.
- Simultaneous display of defendant-level and offence-level warnings from multiple rules (AC6 of CRA-22) is already supported by the existing validation framework; no additional back-end changes are required.
- The warning fires at the "Save and continue" validation point, consistent with all other validation rules in this service.
