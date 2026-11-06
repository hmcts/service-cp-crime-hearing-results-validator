# Feature Specification: Non-Endorsable Offence Warning

**Feature Branch**: `CRA-337-non-endorsable-offence-warning`
**Created**: 2026-10-20
**Status**: In Progress
**Jira**: CRA-337 — 21A. Results validation – Endorsement/Disqualification on non-endorsable offences
**Input**: Warns when an endorsement or endorsable-offence disqualification result is recorded against an offence the reference data marks as non-endorsable (endorsable_flag = 0 or absent).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Endorsement result on non-endorsable offence (Priority: P1)

A Legal Adviser or Court Clerk records an endorsement result (LEP, LEN, or LEA) against an offence that the offence reference data marks as non-endorsable (`endorsable_flag` is 0 or absent). When they select "Save and continue", the system displays an offence-level warning against that offence telling them to remove the endorsement result. The warning is advisory: the user can still save and share without making any change.

**Why this priority**: This is the core endorsement-family AC. Without it, endorsement results can be silently recorded against non-endorsable offences, producing legally incorrect records with no prompt to correct them.

**Independent Test**: Construct a hearing with a single non-endorsable offence carrying one of LEP, LEN, or LEA; call the validation endpoint; assert a WARNING is returned for that offence with the endorsement variant wording. Separately assert that an endorsable offence with the same result produces no warning.

**Acceptance Scenarios** (maps to Jira AC1a):

1. **Given** an offence where `endorsable_flag` is 0 or has no value, **When** a user records an endorsement family result (LEP, LEN, or LEA) against that offence and selects "Save and continue", **Then** a WARNING is displayed against that offence reading "This offence is not endorsable. Remove the endorsement result.", **And** the user can continue to save and share without removing the result.
2. **Given** the same offence carries multiple endorsement family results (e.g. LEP and LEA), **When** validation runs, **Then** the endorsement warning still appears exactly once for that offence.
3. **Given** an offence where `endorsable_flag` is 1, **When** a user records LEP, LEN, or LEA against that offence, **Then** no endorsement warning is displayed. *(Jira AC2)*

---

### User Story 2 - Disqualification result on non-endorsable offence (Priority: P1)

A Legal Adviser or Court Clerk records an endorsable-offence disqualification result against an offence the reference data marks as non-endorsable. When they select "Save and continue", the system displays an offence-level warning against that offence telling them to remove the disqualification result or use a non-endorsable offence disqualification instead. The user can still save and share.

**Disqualification trigger codes**:
- Obligatory variants: DDO, DDOL, DDOR, DDOTE, DDOTEL
- Discretionary variants: DDD, DDDL, DDDT, DDDTL, DDDTO
- Points (totting) variants: DDP, DDPL, DDPR, DDPTE, DDPTEL
- Reduction for course: DDRCOT

**Why this priority**: Equal priority to User Story 1 — the disqualification family covers a wider set of result codes and a distinct policy path (non-endorsable disqualification results exist as the correct alternative), so the message must specifically guide the user to the right substitute.

**Independent Test**: Construct a hearing with a single non-endorsable offence carrying one of the disqualification trigger codes (e.g. DDO); call the validation endpoint; assert a WARNING is returned with the disqualification variant wording. Separately assert that DDRN (non-endorsable disqualification) against the same offence produces no warning.

**Acceptance Scenarios** (maps to Jira AC1b):

1. **Given** an offence where `endorsable_flag` is 0 or has no value, **When** a user records any disqualification trigger code against that offence, **Then** a WARNING is displayed against that offence reading "This offence is not endorsable. Remove the disqualification result, or record a disqualification for non-endorsable offence result instead.", **And** the user can continue to save.
2. **Given** an offence where `endorsable_flag` is 0 or has no value, **When** a user records a non-endorsable disqualification result (DDRN, DDRNL, DDRA, DDRAL, DDRV, or DDRVL) against that offence, **Then** no disqualification warning is displayed. *(Jira AC3)*
3. **Given** an offence where `endorsable_flag` is 1, **When** a user records any disqualification trigger result against that offence, **Then** no disqualification warning is displayed. *(Jira AC2)*

---

### User Story 3 - Both families fire against the same offence (Priority: P1)

A Legal Adviser records both an endorsement family result and a disqualification family result against the same non-endorsable offence. Both warnings — endorsement variant and disqualification variant — are displayed independently against that offence.

**Why this priority**: The two warnings are logically independent; suppressing one when both fire would mislead the user about which results need correcting. This AC must be tested explicitly because it exercises the multi-condition path.

**Independent Test**: Construct a hearing where one non-endorsable offence has both LEP and DDO; call the validation endpoint; assert two distinct WARNINGs are returned for that offence — one with endorsement wording, one with disqualification wording.

**Acceptance Scenarios** (maps to Jira AC1c):

1. **Given** an offence where `endorsable_flag` is 0, **When** a user records both an endorsement family result (LEP, LEN, or LEA) and a disqualification trigger code against that offence, **Then** both the endorsement warning and the disqualification warning are displayed against that offence independently.

---

### User Story 4 - Warning clears when the trigger result is removed or replaced (Priority: P1)

A user who originally triggered a warning removes the trigger result (or, for the disqualification variant, replaces it with a non-endorsable disqualification result). On the next "Save and continue", that specific warning is no longer displayed.

**Why this priority**: Stateless validation means the warning is recalculated on every call. AC4 is satisfied naturally by the stateless engine; a re-validation request without the trigger result must produce no warning for that offence.

**Independent Test**: Send a validation request with no trigger results against a non-endorsable offence; assert no DR-END-014 warning. Replace the trigger result with DDRN; assert the disqualification warning clears.

**Acceptance Scenarios** (maps to Jira AC4):

1. **Given** a warning is displayed against an offence, **When** the user removes the trigger result that caused it and re-validates, **Then** that warning is no longer present in the response.
2. **Given** a disqualification trigger result (e.g. DDO) is replaced with DDRN (non-endorsable disqualification), **When** the user re-validates, **Then** the disqualification warning is no longer present.

---

### User Story 5 - Multiple offences assessed independently (Priority: P2)

A hearing has more than one offence. Trigger results are recorded against some offences but not others, and some offences are endorsable while others are not. Warnings appear only where the trigger condition is met for each specific offence.

**Why this priority**: Per-offence scoping is a correctness invariant. Failing it would produce false positives or false negatives across the whole hearing.

**Independent Test**: Construct a hearing with three offences: (a) non-endorsable with LEP, (b) endorsable with LEP, (c) non-endorsable with no trigger result. Assert warnings appear only for offence (a).

**Acceptance Scenarios** (maps to Jira AC5):

1. **Given** a hearing with multiple offences, some endorsable and some not, and trigger results recorded only against specific non-endorsable offences, **When** validation runs, **Then** warnings are displayed only against the non-endorsable offences carrying a trigger result, and each warning is scoped to the offence below which it appears.

---

### User Story 6 - Coexistence with other validation warnings (Priority: P2)

An offence or hearing already triggers warnings from other validation rules. The conditions for a non-endorsable offence warning are also met. All warnings are shown together; none suppresses, replaces, or alters another.

**Why this priority**: Additive warning behaviour is a system-wide invariant. Its correctness is validated here to confirm this rule does not break the contract established by earlier rules.

**Independent Test**: Construct a hearing that triggers at least one warning from an existing rule (e.g. DR-SENT-001) and also triggers this rule's endorsement warning; assert all expected warnings appear in the response.

**Acceptance Scenarios** (maps to Jira AC6):

1. **Given** entered results trigger warnings from both this rule and at least one other validation rule, **When** validation runs, **Then** all triggered warnings are present in the response, each offence-level warning is attached to its relevant offence, and no warning is suppressed or altered by the presence of another.

---

### Edge Cases

- What happens when an offence has `endorsable_flag` absent (reference data answered successfully but the field is null or missing)? The check treats absent as non-endorsable (same as `0`), so warnings fire — this is the Jira "has no value" case.
- What happens when the reference data lookup fails for an offence (timeout, non-2xx, malformed body, empty `offences` list, lookup disabled, or no `offenceCode` on the offence)? Endorsability is **unknown**, so no warning fires for that offence (fail-open, matching DR-SEX-008). A reference-data outage must not raise warnings against every genuinely endorsable motoring offence.
- What happens when a user replaces a disqualification trigger result with DDRN? The disqualification warning clears because DDRN is not in the trigger set.
- What happens when a result is removed entirely? The corresponding warning clears because the trigger result is no longer present (stateless validation).
- What happens when the same result code appears multiple times against the same offence? Each family fires at most once per offence regardless of how many individual result lines carry the trigger code.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The validation service MUST produce an offence-level WARNING for any non-endorsable offence (endorsable_flag = 0 or absent) that has at least one endorsement family result (LEP, LEN, LEA) recorded against it, with message "This offence is not endorsable. Remove the endorsement result."
- **FR-002**: The validation service MUST produce an offence-level WARNING for any non-endorsable offence (endorsable_flag = 0 or absent) that has at least one disqualification trigger result (DDO, DDOL, DDOR, DDOTE, DDOTEL, DDD, DDDL, DDDT, DDDTL, DDDTO, DDP, DDPL, DDPR, DDPTE, DDPTEL, DDRCOT) recorded against it, with message "This offence is not endorsable. Remove the disqualification result, or record a disqualification for non-endorsable offence result instead."
- **FR-003**: Both warnings MUST be produced independently — if both families fire for the same offence, two separate warnings are shown for that offence.
- **FR-004**: The warnings MUST be scoped per offence — each warning is associated with the specific offence it was triggered by.
- **FR-005**: No warning MUST be produced when the offence has `endorsable_flag` = 1 and any trigger result is recorded.
- **FR-006**: No disqualification warning MUST be produced when the only disqualification results recorded against a non-endorsable offence are non-endorsable disqualification results (DDRN, DDRNL, DDRA, DDRAL, DDRV, DDRVL).
- **FR-007**: The warnings MUST be advisory — they MUST NOT prevent the user from saving or sharing the hearing result.
- **FR-008**: The validation check fires on "Save and continue", regardless of result category (Final or ancillary) and regardless of what other results are recorded for the same offence.
- **FR-009**: Warnings from this rule MUST coexist with, and MUST NOT suppress or alter, warnings produced by other validation rules.
- **FR-010**: The endorsability of an offence is determined by `endorsable_flag` in offence reference data; the service MUST consult reference data per offence at validation time.
- **FR-011**: When reference data answers successfully but `endorsable_flag` is null or missing, the offence MUST be treated as non-endorsable. When the lookup itself fails (timeout, non-2xx, malformed body, empty `offences` list, lookup disabled, or no `offenceCode`), endorsability is unknown and the offence MUST NOT produce a warning (fail-open, consistent with DR-SEX-008).
- **FR-012**: The reference data lookup MUST be skipped entirely when no trigger result (from either family) is recorded against the offence, to avoid unnecessary external calls.

### Key Entities

- **Offence**: A charge in the hearing. Has an `id`, `offenceCode`, display order, and `endorsable_flag` sourced from offence reference data.
- **Result line**: A recorded outcome on an offence. Carries a `shortCode` (e.g. `LEP`, `DDO`) that determines which trigger family, if any, applies.
- **Endorsement family**: Result codes LEP, LEN, LEA — driving licence endorsement outcomes intended for endorsable offences only.
- **Disqualification trigger family**: Result codes DDO, DDOL, DDOR, DDOTE, DDOTEL, DDD, DDDL, DDDT, DDDTL, DDDTO, DDP, DDPL, DDPR, DDPTE, DDPTEL, DDRCOT — disqualification outcomes intended for endorsable offences.
- **Non-endorsable disqualification family**: Result codes DDRN, DDRNL, DDRA, DDRAL, DDRV, DDRVL — the correct disqualification codes for non-endorsable offences; these do NOT trigger a warning.
- **Validation rule DR-END-014**: Single YAML rule with two independent CEL conditions — AC1 (endorsement) and AC2 (disqualification) — evaluated against a unified per-offence context produced by `NonEndorsableOffencePreprocessor`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A validation request for a non-endorsable offence with an endorsement trigger result returns exactly one WARNING with the endorsement variant message for that offence, in 100% of cases.
- **SC-002**: A validation request for a non-endorsable offence with a disqualification trigger result returns exactly one WARNING with the disqualification variant message for that offence, in 100% of cases.
- **SC-003**: A validation request for a non-endorsable offence with both trigger families present returns exactly two WARNINGs for that offence (one per family, both under rule DR-END-014), in 100% of cases.
- **SC-004**: A validation request for an endorsable offence with any trigger result returns zero warnings related to endorsability, in 100% of cases.
- **SC-005**: A validation request for a non-endorsable offence with only non-endorsable disqualification results (DDRN/DDRNL/DDRA/DDRAL/DDRV/DDRVL) returns zero endorsability warnings, in 100% of cases.
- **SC-006**: When endorsability warnings coexist with warnings from other rules, all triggered warnings are present in the response — none is dropped or duplicated.
- **SC-007**: The check adds no measurable latency beyond the single offence reference data lookup per offence; no lookup is made when no trigger result is present.

## Assumptions

- Offence reference data is the authoritative source for `endorsable_flag`; the service retrieves this per offence via `ReferencedataOffenceClient` (introduced for DR-END-012 / CRA-336) and the same client is reused for this rule without a new external call type.
- Both the endorsement family and the disqualification trigger family are implemented as two independent boolean conditions within a single YAML rule file (DR-END-014) sharing a unified preprocessor (`NonEndorsableOffencePreprocessor`, type `non-endorsable-offence`). This consolidation reduces the reference-data call to one per offence when both families trigger.
- "Save and continue" maps to the existing validation endpoint call; no new API contract is required.
- Severity for all conditions is WARNING; the user can continue to save and share.
- Result category (Final or ancillary) is irrelevant to this check — all result lines for the offence are inspected.
- Mobile / UI rendering of GDS warning text is out of scope for this service; the service returns the warning text string and the UI renders it.
- A successful reference-data response with no `endorsable_flag` is treated as non-endorsable (warning fires). A failed lookup (timeout, non-2xx, malformed body, empty list, disabled) is fail-open: no warning, so an outage does not flood endorsable offences with false warnings. Failed lookups are not cached, so the next validation retries.