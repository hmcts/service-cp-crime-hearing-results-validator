# Feature Specification: Electronic Monitoring Status Validation

**Feature Branch**: `dev/CRA-38-electronic-monitoring-status`  
**Created**: 2026-11-18  
**Status**: Draft  
**Jira**: CRA-38

## User Scenarios & Testing *(mandatory)*

### User Story 1 – Blocked from sharing when EM status is unresolved (Priority: P1)

A caseworker on the Enter Results screen has entered a final or non-exempt interim result on an offence that was flagged for electronic monitoring at a previous hearing. The result neither continues nor ends the electronic monitoring. When the caseworker clicks "Save and continue", the service returns a validation error blocking sharing — displaying an error summary at the top of the screen and an inline error above each affected offence.

**Why this priority**: Prevents sharing hearing results when the EM contractor cannot know whether to continue or stop monitoring, which could cause unlawful detention or unexpected early release.

**Independent Test**: Submit a request with one EM-flagged offence carrying a final result (e.g. IMP) and no EMONE / EM-continuing child result; expect an ERROR issue in the response.

**Acceptance Scenarios**:

1. **Given** a defendant has an EM-flagged offence with a final result (e.g. IMP) and no continuing/ending EM result, **When** the validation request is submitted, **Then** an ERROR issue is returned identifying the affected offence
2. **Given** a defendant has an EM-flagged offence with an interim result (e.g. RC) that is NOT an exempt main result and has no EM continuing/ending child result, **When** the validation request is submitted, **Then** an ERROR issue is returned
3. **Given** NHMC or NHCC appears as a child of a non-exempt main result (not as the main result itself), **When** the validation request is submitted, **Then** the offence is NOT considered to have an exempt main result and the ERROR is returned (AC3)

---

### User Story 2 – No error when every EM-flagged offence is resolved (Priority: P1)

A caseworker has handled all EM-flagged offences appropriately — each one either has a continuing EM result, an ending (EMONE) result, an exempt main result, only ancillary results, or no result at all. The validation must pass cleanly.

**Why this priority**: False positives block sharing legitimate results and erode trust in the validator.

**Independent Test**: Submit a request with all EM-flagged offences resolved (various combinations); expect no issues related to electronic monitoring.

**Acceptance Scenarios**:

1. **Given** all EM-flagged offences have EMONE (ending result), **When** validation runs, **Then** no EM error is returned (AC1)
2. **Given** all EM-flagged offences have a continuing result (ELMONDET/EMREQ/EMREQV/EMSO child with "Yes"), **When** validation runs, **Then** no EM error is returned (AC1)
3. **Given** an EM-flagged offence has an exempt main result (NHMC, NHCC, A, ADJNN, ADJAPP) entered directly as the main result, **When** validation runs, **Then** no EM error is returned (AC1)
4. **Given** an EM-flagged offence has only ancillary results (ODIR, ORDA, SMP) and no final/interim result, **When** validation runs, **Then** no EM error is returned (AC1)
5. **Given** an EM-flagged offence has no result at all, **When** validation runs, **Then** no EM error is returned (AC1)
6. **Given** mixed combinations (some ended, some continued, some blank), **When** validation runs, **Then** no EM error is returned (AC1)

---

### User Story 3 – No validation when no offence carries the EM flag (Priority: P2)

If none of the offences in the hearing carry the EM flag, the validation rule must do nothing. Results entered on non-EM offences must never trigger an EM error.

**Why this priority**: Avoids spurious errors on the vast majority of hearings that have no prior EM imposition.

**Independent Test**: Submit a request with offences that have no EM flag and various results; expect no EM issues.

**Acceptance Scenarios**:

1. **Given** no offence in the hearing has the EM flag, **When** validation runs regardless of results entered, **Then** no electronic monitoring issue is returned (AC9)

---

### User Story 4 – Multi-defendant error message names affected defendants (Priority: P2)

When a hearing has multiple defendants and the EM error is triggered for one or more of them, the error summary message includes a "This affects …" suffix listing the affected defendants by name.

**Why this priority**: Caseworkers handling multi-defendant hearings need to know which defendant's offences require attention.

**Independent Test**: Submit a two-defendant request where only one defendant triggers the EM error; verify the error message ends with "This affects [name]" (single defendant suffix omitted when only one defendant exists in the hearing).

**Acceptance Scenarios**:

1. **Given** the hearing has two defendants and both trigger the error, **When** validation runs, **Then** the message ends with "This affects [Name1] and [Name2]" (AC4)
2. **Given** the hearing has three or more defendants and all trigger the error, **When** validation runs, **Then** the message ends with "This affects [Name1], [Name2] and [Name3]" (AC4)
3. **Given** the hearing has only one defendant and the error is triggered, **When** validation runs, **Then** the message does NOT include "This affects …" (AC4)
4. **Given** multiple defendants exist but only one triggers the error, **When** validation runs, **Then** only the affected defendant is named (AC4)

---

### Edge Cases

- **Continuing result with "No" selected**: An offence has EMREQ as a child result but the value is "No" — this is NOT a continuing result; the error must fire if no other resolution exists.
- **EMONE entered alongside a final result**: An offence with both IMP and EMONE — EMONE is the ending result so the offence is resolved; no error.
- **Multiple EM-flagged offences, only some unresolved**: Only the unresolved offences appear in the affectedOffenceSet; resolved offences are not highlighted.
- **Ancillary-only with EM flag**: ODIR or SMP on an EM-flagged offence — ancillary-only resolves the offence; no error.
- **Exempt result as child**: NHMC entered as a child of RC (not as the main result) — does NOT resolve the offence; error must fire (AC3).
- **No offences at all**: Hearing with zero offences — no EM validation is performed.
- **All EM-flagged offences, all blank**: No results entered on any EM-flagged offence — all are resolved (blank = no result = resolved); no error.
- **Five defendants all unresolved**: Message ends with "This affects John Smith, Jane Jones, Sam Patel, Alex Lee and Kim Davis" — confirming the N≥3 comma-and format scales beyond 3 names.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The validation rule MUST identify all offences in the request that carry the EM flag (i.e., the offence has previously had electronic monitoring imposed and not yet ended).
- **FR-002**: If no offence in the request carries the EM flag, the rule MUST return no issues (early exit).
- **FR-003**: For each EM-flagged offence, the rule MUST classify it as **resolved** if any of the following is true:
  - The offence has no result entered at all
  - The offence carries an ending result (short code `EMONE`)
  - The offence carries a continuing result: a child result with short code `ELMONDET`, `EMREQ`, `EMREQV`, or `EMSO` where the value is "Yes"
  - The offence's main result is an exempt main result (short code `NHMC`, `NHCC`, `A`, `ADJNN`, or `ADJAPP`) entered directly as the main result — not as a child of another result
  - The offence has only ancillary results (no final or interim main result)
- **FR-004**: For each EM-flagged offence that is NOT resolved, the rule MUST generate an ERROR-severity issue on that offence.
- **FR-005**: The error message template for the per-offence issue MUST be: `"Electronic monitoring is in place on this offence. Add a result that continues electronic monitoring, or end it with 'Electronic Monitoring End - notify contractor (EMONE)'."`
- **FR-006**: The error message template for the per-defendant summary MUST be: `"Electronic monitoring is in place on offences. Add a result that continues electronic monitoring, or end it with 'Electronic Monitoring End - notify contractor (EMONE)'."` with the defendant-name suffix logic applied by the existing `MessageTemplateResolver` / defendant-aggregation mechanism.
- **FR-007**: The rule MUST include an `affectedOffenceSet` listing all offences that triggered the error for a given defendant, so the front end can link the summary entry to the first affected offence.
- **FR-008**: The existing defendant-name suffix mechanism (`This affects …`) MUST be applied when the hearing has more than one defendant.
- **FR-009**: An exempt main result entered as a **child** of another main result (e.g. NHMC under RC) MUST NOT resolve the offence.
- **FR-010**: A "continuing result" with the EM child result set to "No" MUST NOT resolve the offence.

### Key Entities

- **EM-flagged offence**: an offence whose `hasActiveElectronicMonitoring` field is `true` (Boolean field on `OffenceDto`, nullable — treat null as false; populated from `emStatus` on the defendant tracking status loaded at request time).
- **Continuing result short codes**: `ELMONDET`, `EMREQ`, `EMREQV`, `EMSO` (child result, value = "Yes").
- **Ending result short code**: `EMONE`.
- **Exempt main result short codes**: `NHMC`, `NHCC`, `A`, `ADJNN`, `ADJAPP` (only when entered as the main result, not as a child).
- **Ancillary result short codes** (examples): `ODIR`, `ORDA`, `SMP`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every EM-flagged offence with a non-resolving result produces exactly one ERROR issue in the validation response — no duplicates, no omissions.
- **SC-002**: Hearings with no EM-flagged offences produce zero EM-related validation issues, regardless of what results are entered.
- **SC-003**: All nine AC1 "must pass" example combinations produce a clean validation response (zero errors).
- **SC-004**: The error message wording exactly matches the specified strings for both summary-level and offence-level messages.
- **SC-005**: Multi-defendant hearings include the "This affects …" suffix with correct name formatting (comma-separated with "and" before the last name for 3+ defendants, e.g. "This affects A, B and C"; "and"-separated for exactly 2, e.g. "This affects A and B"; absent for 1 defendant).
- **SC-006**: An exempt main result entered as a child (not as the main result) does not suppress the error (AC3 scenario verified by test).
- **SC-007**: Unit test coverage ≥ 100% for the new preprocessor and YAML rule conditions; integration test coverage proves end-to-end behaviour; API test covers the happy path and the primary error path.

## Assumptions

- The EM flag on each offence is provided in the `DraftValidationRequest` as a boolean or equivalent indicator, populated from the `emStatus` defendant tracking data loaded by the calling service. No additional upstream call is needed inside this validator.
- The request structure exposes child results on a result line, allowing the preprocessor to inspect whether `ELMONDET`/`EMREQ`/`EMREQV`/`EMSO` is present and what its value is.
- AC5 (GDS focus management), AC10 (legacy EM panel removal), AC11 (EM flag maintenance), and AC12 (warrant-of-arrest panel) are frontend concerns and are **out of scope** for this backend validation service.
- AC6 (shown alongside other errors), AC7 (Share hidden while error exists), and AC8 (error cleared when corrected) are handled by the existing validation framework and do not require new code — they are verified by existing framework-level tests and by the multi-rule integration tests.
- The rule short code is `EMON`; the YAML file is `DR-EMON-017.yaml`; the Liquibase migration is `V1.019__insert_dr_emon_017.sql`; the spec folder is `specs/018-electronic-monitoring-status`.
- Priority 18000 places this rule after the existing rules (highest existing priority is below 18000).
