# Feature Specification: Endorsement/Disqualification Missing on Endorsable Offences

**Feature Branch**: `CRA-336-endorsable-offence-warning`
**Created**: 2026-10-08
**Status**: Draft
**Jira**: CRA-336

## User Scenarios & Testing *(mandatory)*

### User Story 1 – Advisory warning for endorsable offence with no satisfying result (Priority: P1)

A court clerk records a final result against an endorsable offence (one where the offence reference data has `endorsable_flag = 1`) but does not add any endorsement, driving disqualification, or special reasons result. The service displays an advisory warning at offence level prompting the clerk to add the missing result. The clerk can still save and continue without resolving the warning.

**Why this priority**: This is the primary purpose of the rule — alerting clerks to a likely data quality gap before the record is shared.

**Independent Test**: Can be fully tested by submitting a validation request containing one endorsable offence with a non-excluded final result and no satisfying result, and asserting the warning is returned against that offence.

**Acceptance Scenarios**:

1. **Given** an offence where `endorsable_flag` is 1, **When** the user records a final result (Category F – Final) that is not in the excluded results list and no satisfying result is recorded against that offence, **Then** the warning "This offence is endorsable. Add an endorsement, disqualification or special reasons result." is returned against that offence, and the result is WARNING severity (not ERROR).
2. **Given** the warning is already displayed against an offence, **When** the user adds a satisfying result to that offence, **Then** the warning is no longer returned.

---

### User Story 2 – Warning suppressed when a satisfying result is present (Priority: P2)

A court clerk has already recorded an endorsement, disqualification, or special reasons result against an endorsable offence alongside a final result. No warning is shown because the satisfying result is present.

**Why this priority**: Prevents false positives — the rule must not fire when the offence is correctly recorded.

**Independent Test**: Can be fully tested by submitting a validation request with an endorsable offence that has both a final result and a satisfying result code (e.g. LEP, DDO, NESR), and asserting no warning is returned.

**Acceptance Scenarios**:

1. **Given** an offence where `endorsable_flag` is 1, **When** a final result that is not in the excluded list is recorded and at least one satisfying result (endorsement, disqualification, or special reasons) is also recorded against that offence, **Then** the warning is not returned.
2. **Given** an offence where `endorsable_flag` is 1, **When** the only result recorded is from the excluded results list, **Then** the warning is not returned (excluded results indicate the offence was not proceeded with or is administrative). *(Note: this scenario is enforced by the excluded-result check — the preprocessor short-circuits before the satisfying-result check when only excluded final results are present.)*

---

### User Story 3 – Non-endorsable offence is never warned (Priority: P2)

A court clerk records a final result against an offence whose `endorsable_flag` is 0 or absent. No endorsable-offence warning is returned regardless of which results are present.

**Why this priority**: Offence endorsability is a legal property; incorrectly warning on non-endorsable offences would mislead clerks and undermine trust in the validation service.

**Independent Test**: Can be fully tested by submitting a validation request with `endorsable_flag = 0` (or missing) on the offence and any final result, asserting no warning is returned.

**Acceptance Scenarios**:

1. **Given** an offence where `endorsable_flag` is 0 or has no value, **When** the user records any final result, **Then** no endorsable-offence warning is returned.

---

### User Story 4 – Multiple offences assessed independently (Priority: P3)

A hearing has several offences, some endorsable and some not. The service warns only against those endorsable offences that lack a satisfying result; each offence is assessed independently.

**Why this priority**: Hearing data regularly contains multiple charges; a case-level check would be too coarse and would either miss or misattribute warnings.

**Independent Test**: Can be fully tested by submitting a request with one endorsable offence lacking a satisfying result and one non-endorsable offence, and asserting only the endorsable offence carries the warning.

**Acceptance Scenarios**:

1. **Given** a hearing with two or more offences, at least one of which has `endorsable_flag = 1`, **When** the user records final results, **Then** the warning is returned only against each endorsable offence that lacks a satisfying result — not against non-endorsable offences or endorsable offences that already have a satisfying result.

---

### User Story 5 – Coexistence with other validation warnings (Priority: P3)

An offence or case already carries warnings from other validation rules. When the endorsable-offence rule also fires, both sets of warnings are returned without either suppressing or replacing the other.

**Why this priority**: Validation rules are additive; any rule that silently suppresses others is a defect.

**Independent Test**: Can be fully tested by constructing a request that triggers at least one other known validation rule and the endorsable-offence rule simultaneously, then asserting both warning sets are present in the response.

**Acceptance Scenarios**:

1. **Given** an offence or case that meets conditions for warnings from other validation rules, **And** the conditions for the endorsable-offence warning are also met, **When** validation runs, **Then** the endorsable-offence warning is returned in addition to (not instead of) the other warnings, and each offence-level warning is attributed to its correct offence.

---

### Edge Cases

- What happens when `endorsable_flag` is absent from the offence data? → Treat as non-endorsable; no warning.
- What happens when a result code appears in both the excluded list and the satisfying list? → The excluded list takes precedence (excluded results mean the offence was not proceeded with, making endorsability moot).
- What happens when a case has no final result at all (only interim results)? → The rule does not fire; it applies only when a Category F – Final result is present.
- What happens when multiple satisfying results are present? → Any one satisfying result is sufficient to suppress the warning.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The service MUST assess each offence in the validation request independently. If no Category F – Final result with a non-excluded code is present for an offence, no warning is returned for that offence and the `endorsable_flag` lookup is not performed. Otherwise the service checks `endorsable_flag` and satisfying result presence to determine whether a warning is warranted.
- **FR-002**: The service MUST return a WARNING-severity issue against an offence when all three conditions are true: (a) `endorsable_flag` is 1; (b) the offence has at least one final result whose code is not in the excluded results list; and (c) none of the results recorded against that offence is a satisfying result.
- **FR-003**: The service MUST NOT return the endorsable-offence warning for an offence when `endorsable_flag` is 0 or absent.
- **FR-004**: The service MUST NOT return the endorsable-offence warning for an offence when the only final result(s) present are in the excluded results list (WDRN, WDRNOFF, DISM, DINE, DINI, DISCH, DISC, CTROF, IREMFILE, ERR, ERRF, DHD, ONI, DCS, DCCFSA, DCCFSTA, CQUASH, IQUASH, RESTRAO, STAYP, RBBH, SOCOR, PDW, RBBO).
- **FR-005**: The service MUST NOT return the endorsable-offence warning for an offence when at least one satisfying result is present. Satisfying results are: endorsement codes (LEP, LEN, LEA), driving disqualification codes (DDO, DDOL, DDOR, DDOTE, DDOTEL, DDD, DDDL, DDDT, DDDTL, DDDTO, DDP, DDPL, DDPR, DDPTE, DDPTEL, DDRCOT), and special reasons codes (NESR, NDSR).
- **FR-006**: The warning MUST be advisory only — severity is WARNING, not ERROR — and MUST NOT prevent the user from saving or sharing.
- **FR-007**: Each offence in the hearing MUST be assessed independently; the warning for one offence MUST NOT affect the assessment of any other offence.
- **FR-008**: The endorsable-offence warning MUST coexist with warnings and errors from other validation rules; it MUST NOT suppress, replace, or alter any other validation issue.
- **FR-009**: The warning message text MUST be exactly: "This offence is endorsable. Add an endorsement, disqualification or special reasons result."

### Key Entities

- **Offence**: A charge in the hearing. Carries an `endorsable_flag` (1 = endorsable, 0 or absent = non-endorsable) from the offence reference data. Assessed independently for this rule.
- **Final result**: A result whose definition is recorded with Category F – Final in the results reference data. Triggers the endorsability check.
- **Excluded result**: A result code indicating the offence was not proceeded with or is administrative. Its presence exempts the offence from the endorsable-offence warning even if `endorsable_flag` is 1.
- **Satisfying result**: A result code from one of three families — endorsement (LEP, LEN, LEA), driving disqualification (DDO, DDOL, DDOR, DDOTE, DDOTEL, DDD, DDDL, DDDT, DDDTL, DDDTO, DDP, DDPL, DDPR, DDPTE, DDPTEL, DDRCOT), or special reasons (NESR, NDSR). Any one satisfying result suppresses the warning for its offence.
- **Validation issue (WARNING)**: An advisory issue returned in the validation response against a specific offence. Does not block saving or sharing.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When an endorsable offence has a final result and no satisfying result, the advisory warning is returned in 100% of such cases in automated testing.
- **SC-002**: When a satisfying result is present, the warning is absent in 100% of such cases in automated testing.
- **SC-003**: When an excluded result is the only final result, the warning is absent in 100% of such cases.
- **SC-004**: When `endorsable_flag` is 0 or absent, the warning is never returned regardless of results present.
- **SC-005**: The warning for one offence does not affect the outcome for any other offence — verified by a test with at least two offences of different endorsability in the same hearing.
- **SC-006**: The endorsable-offence warning is returned alongside warnings from at least one other validation rule without either being suppressed, confirmed by an integration test.
- **SC-007**: The warning severity is WARNING (not ERROR) in all cases — the validation response allows saving and sharing.

## Assumptions

- The `endorsable_flag` is sourced from the offence reference data **service** (`cpp-context-referencedata-offences`) and looked up per offence code at validation time; it is not carried on the validation request payload. ~99.7% of offence reference data rows carry a populated value; the remaining ~0.3% are treated as non-endorsable (no `details_json` fallback required).
- Endorsability is determined solely by `endorsable_flag`; DVLA code presence is NOT a proxy for endorsability and MUST NOT be used as such.
- The validation check fires when the user selects "Save and continue", consistent with all other results-validation rules in this service.
- The excluded and satisfying result code lists are sourced from the Jira specification (CRA-336) and are considered complete and stable; any future changes to these lists will require a spec and rule update.
- Result category (F – Final vs. other) is available on each result in the validation request payload.
- A result code that satisfies the check for one offence has no bearing on any other offence in the hearing.
- This rule produces only WARNING-severity issues; the severity ceiling model in the service may cap it further (never promote it) at runtime.
