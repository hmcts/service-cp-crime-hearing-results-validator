# Feature Specification: Pre-Release Bail Condition Error

**Feature Branch**: `CRA-363-pre-release-bail-error`
**Created**: 2026-10-27
**Status**: Draft
**Jira**: CRA-363
**Spec Folder**: `specs/016-pre-release-bail-condition-error`
**SQL Migration**: `V1.017__pre_release_bail_condition_error.sql`
**Rule Short Code**: `PRBCE` (≤5 chars)

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Adult defendant triggers error (remand on conditional bail) (Priority: P1)

A court clerk enters a remand-on-conditional-bail result (RC, RCBV, BAILREVC, REMCB, REMCBY, or RCCCB) as the parent result on an offence for a defendant aged 18 or over, and also enters one or more pre-release bail condition results as child results on the same offence. The system must block sharing and display Error A.

**Why this priority**: Core blocking scenario. Pre-release conditions on a conditional bail result are legally contradictory; the defendant could otherwise be erroneously released on bail without meeting conditions that the court required to be satisfied before release.

**Independent Test**: Submit a hearing with a single adult defendant whose offence carries RC as parent and PREBC as child. Verify ERROR response with Error A message and no Share button.

**Acceptance Scenarios**:

1. **Given** the defendant is 18 or over on the hearing date, **And** offence has parent result RC / RCBV / BAILREVC / REMCB / REMCBY / RCCCB, **And** one or more pre-release bail condition results (PREBC, PRBCO, PRBCP1–PRBCP8, PRBCS1–PRBCS3, PRBCSE1, PRBCSE2, PRBCT) are child results on the same offence, **When** "Save and continue" is selected, **Then** the user remains on the Enter results screen, Error A is shown in the summary ("There is a problem") and inline above the affected result, and sharing is prevented.

---

### User Story 2 — Youth defendant triggers error (remand on conditional bail) (Priority: P1)

A court clerk enters a remand-on-conditional-bail parent result for a defendant under 18 and adds pre-release bail conditions as child results. The system must block sharing and display the youth-specific Error B.

**Why this priority**: Equals P1 with adult scenario — the legal risk (defendant released without meeting pre-release conditions) is the same; the alternative result differs (RILAB rather than RIB).

**Independent Test**: Submit a hearing with a single youth defendant whose offence carries REMCBY as parent and PRBCS1 as child. Verify ERROR response with Error B message.

**Acceptance Scenarios**:

1. **Given** the defendant is under 18 on the hearing date, **And** offence has a remand-on-conditional-bail parent result, **And** one or more pre-release bail condition child results on the same offence, **When** "Save and continue" is selected, **Then** Error B is shown in summary and inline; sharing is prevented.

---

### User Story 3 — CCIC parent result triggers age-neutral error (Error D) (Priority: P2)

A court clerk enters CCIC (Sent to Crown Court for trial on conditional bail) as the parent result and adds pre-release bail condition child results. The system must display Error D regardless of the defendant's age.

**Why this priority**: CCIC is a distinct parent result type requiring a different alternative (CCIIB); same legal risk but different suggested correction.

**Independent Test**: Submit with CCIC parent and PRBCO child. Verify Error D, same message for adult and youth.

**Acceptance Scenarios**:

1. **Given** offence has CCIC as parent result, **And** one or more pre-release bail condition child results on the same offence, **And** the defendant is of any age (or no date of birth), **When** "Save and continue" is selected, **Then** Error D is shown in summary and inline; sharing is prevented.

---

### User Story 4 — CCSC parent result triggers age-neutral error (Error E) (Priority: P2)

A court clerk enters CCSC (Committed to Crown Court for sentence on conditional bail) with pre-release bail condition child results. Error E is shown regardless of age.

**Why this priority**: Parallel to User Story 3 for CCSC; different suggested alternative (CCSIB).

**Independent Test**: Submit with CCSC parent and PRBCSE1 child. Verify Error E.

**Acceptance Scenarios**:

1. **Given** offence has CCSC as parent result, **And** one or more pre-release bail condition child results on the same offence, **When** "Save and continue" is selected, **Then** Error E is shown in summary and inline; sharing is prevented.

---

### User Story 5 — Mixed adult/youth hearing (remand on conditional bail) (Priority: P2)

A hearing has multiple defendants, at least one adult and at least one youth, both with the error condition triggered via a remand-on-conditional-bail parent result. The error summary shows the single combined message Error C; inline errors remain age-specific (Error A for adults, Error B for youths).

**Why this priority**: Multi-defendant hearings require combined summary messaging to avoid duplicate or contradictory messages in the error summary, while preserving per-defendant guidance inline.

**Independent Test**: Submit with two defendants — one adult (RC parent, PREBC child), one youth (REMCBY parent, PRBCS2 child). Verify Error C in summary with both names, Error A inline for adult, Error B inline for youth.

**Acceptance Scenarios**:

1. **Given** the hearing has at least one adult and one youth both meeting the error condition via a remand-on-conditional-bail result, **When** "Save and continue" is selected, **Then** a single Error C entry appears in the error summary listing all affected defendants; inline errors are Error A above the adult's result and Error B above the youth's result.

---

### User Story 6 — Defendant with no date of birth (Priority: P2)

Where the defendant's date of birth is not recorded and the error condition is met via a remand-on-conditional-bail parent result, Error C (combined) is shown both in the summary and inline.

**Why this priority**: Age cannot be determined; the system must fall back to the combined message to avoid giving age-specific guidance that could be wrong.

**Independent Test**: Submit with defendant (no DOB) with RC parent and PREBC child. Verify Error C in both summary and inline.

**Acceptance Scenarios**:

1. **Given** the defendant has no date of birth, **And** the error condition is met with a remand-on-conditional-bail parent result, **When** "Save and continue" is selected, **Then** Error C is displayed in the error summary and above each affected result for that defendant.

---

### User Story 7 — Multiple defendant names appended to error summary (Priority: P3)

When the hearing has more than one defendant and the error condition is triggered for multiple defendants, each error summary entry appends the affected defendant names.

**Why this priority**: The GDS error summary pattern requires clear attribution of errors to defendants in multi-defendant hearings.

**Independent Test**: Submit with three defendants all triggering Error A. Verify summary entry ends with "This affects [Name1], [Name2] and [Name3]".

**Acceptance Scenarios**:

1. **Given** two defendants trigger the same error, **When** "Save and continue" is selected, **Then** the summary entry ends with "This affects [Name1] and [Name2]".
2. **Given** three or more defendants trigger the same error, **When** "Save and continue" is selected, **Then** names are separated by ", " (Oxford-comma style with "and" before the last).
3. **Given** only one defendant in the hearing, **When** "Save and continue" is selected, **Then** the "This affects" suffix is not appended.

---

### User Story 8 — Multiple conditional bail result types on the same hearing (Priority: P3)

A hearing has defendants with different types of conditional bail parent results (e.g. RC on one and CCIC on another). Each triggered error type produces a separate entry in the error summary.

**Why this priority**: The suggested correction differs by parent result type; a single merged message would provide wrong guidance.

**Independent Test**: Submit with one defendant (RC parent, PREBC child) and another (CCIC parent, PRBCO child). Verify two summary entries: Error A/B/C (as applicable) and Error D.

**Acceptance Scenarios**:

1. **Given** the hearing has error conditions triggered via both a remand-on-conditional-bail result and a CCIC result, **When** "Save and continue" is selected, **Then** the error summary contains one entry for each error type (e.g. Error A and Error D), each with the relevant defendant names appended.

---

### User Story 9 — Error cleared when results are corrected (Priority: P1)

Once the error has been displayed, the court clerk corrects it by either replacing the parent result with the appropriate in-custody-with-bail-direction result, or removing the pre-release child result(s). On the next "Save and continue" no error appears and sharing is enabled.

**Why this priority**: The error must be resolvable — clerks need a clear path to a valid submission.

**Independent Test**: Reproduce the AC1 error, then re-submit with parent changed to RIB. Verify success with Share button visible.

**Acceptance Scenarios**:

1. **Given** the error is displayed, **When** the parent result is changed to RIB (adult), RILAB (youth), CCIIB (CCIC), or CCSIB (CCSC) on every affected offence and "Save and continue" is selected, **Then** no error is displayed and the Share button is available.
2. **Given** the error is displayed, **When** all pre-release bail condition child results are removed and any conditions re-entered as ordinary bail conditions, and "Save and continue" is selected, **Then** no error is displayed.

---

### User Story 10 — Error not shown when condition is not met (Priority: P1)

When the trigger condition is not present — parent result is not a conditional bail parent result, no pre-release child results, or the pre-release result is on a different offence — no error is raised.

**Why this priority**: False positives block valid submissions; the negative case is as important as the positive.

**Independent Test**: Submit with RIB parent and PREBC child — no error. Submit with RC parent and non-pre-release child — no error. Submit with RC on offence A and PREBC on offence B — no error.

**Acceptance Scenarios**:

1. **Given** the parent result is RIB, RILAB, CCIIB, or CCSIB (not a conditional bail parent result), **And** pre-release bail condition child results are present on the offence, **When** "Save and continue" is selected, **Then** no error is raised.
2. **Given** the parent result is a conditional bail parent result, **And** no pre-release bail condition child results are on the same offence, **When** "Save and continue" is selected, **Then** no error is raised.
3. **Given** the parent result is a conditional bail parent result on offence A, **And** pre-release bail condition child results are on offence B (different offence), **When** "Save and continue" is selected, **Then** no error is raised.

---

### Edge Cases

- Defendant with no date of birth + remand-on-conditional-bail parent result → Error C (not age-specific).
- All 16 pre-release bail condition short codes (PREBC, PRBCO, PRBCP1–PRBCP8, PRBCS1–PRBCS3, PRBCSE1, PRBCSE2, PRBCT) individually trigger the error.
- RICD (Remand in Customs detention) — excluded despite being flagged B in reference data; must not trigger the rule.
- Extradition "on conditional bail" results (E1CB, E1CONCB, E2CB, etc.) — set post-hearing custody status A, not B; must not trigger the rule.
- Pre-release bail condition result on a different offence from the parent result — no error.
- Multiple pre-release bail condition child results on the same offence — still one error entry per offence, not one per child result.
- Error co-exists with other validation rule errors (AC5A) — both errors shown; neither suppresses the other.
- Single-defendant hearing — "This affects" suffix omitted.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST raise an ERROR when a conditional bail parent result (RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB, CCIC, or CCSC) is paired with one or more pre-release bail condition child results on the same offence.
- **FR-002**: The system MUST display Error A (adult message) when the triggered offence belongs to an adult defendant (18 or over) and the parent result is a remand-on-conditional-bail result (RC, RCBV, BAILREVC, REMCB, REMCBY, or RCCCB).
- **FR-003**: The system MUST display Error B (youth message) when the triggered offence belongs to a youth defendant (under 18) and the parent result is a remand-on-conditional-bail result.
- **FR-004**: The system MUST display Error C (combined message) both in the error summary and inline when (a) the hearing has mixed adult and youth defendants both triggering the error via a remand-on-conditional-bail result, or (b) the defendant has no date of birth recorded.
- **FR-005**: The system MUST display Error D (CCIC message) in the error summary and inline for any offence with CCIC as parent result paired with pre-release bail condition child results, regardless of the defendant's age.
- **FR-006**: The system MUST display Error E (CCSC message) in the error summary and inline for any offence with CCSC as parent result paired with pre-release bail condition child results, regardless of the defendant's age.
- **FR-007**: In a mixed adult/youth hearing where the remand-on-conditional-bail error is triggered, the error summary MUST show a single Error C entry; the inline error above each result MUST remain age-specific (Error A for adults, Error B for youths).
- **FR-008**: Where a hearing has more than one defendant and multiple defendants trigger the same error type, the error summary entry for that type MUST append "This affects [name(s)]" using "and" for two defendants and ", " separators for three or more; the suffix MUST be omitted when there is only one defendant in the hearing.
- **FR-009**: Where multiple error types are triggered in the same hearing (e.g. Error A/C from a remand result AND Error D from CCIC on another defendant/offence), the error summary MUST contain one entry per distinct error type, each with the relevant defendant name(s) appended.
- **FR-010** `[Frontend — Common Platform UI scope]`: The error summary MUST follow the GDS error summary pattern: heading "There is a problem", focus moves to the summary on page load, each summary entry is a link to the first affected result.
- **FR-011** `[Frontend — Common Platform UI scope]`: The inline error above each affected result MUST use the GOV.UK error message component and visually highlight the result as in error.
- **FR-012**: This error MUST be displayed alongside any other validation rule errors active for the same hearing; no error is suppressed or replaced by another.
- **FR-013** `[Frontend — Common Platform UI scope]`: Sharing (the Share button on Manage Hearings) MUST be hidden while this error condition exists on one or more offences.
- **FR-014**: The error MUST be cleared (and sharing re-enabled) when the clerk corrects every affected offence by either (a) replacing the parent result with the appropriate in-custody-with-bail-direction alternative (RIB, RILAB, CCIIB, or CCSIB) or (b) removing all pre-release bail condition child results.
- **FR-015**: The rule MUST NOT trigger for results where the parent is RIB, RILAB, CCIIB, or CCSIB; where no pre-release bail condition results are present as children on the offence; or where the pre-release bail condition result is on a different offence from the conditional bail parent result.
- **FR-016**: The rule MUST NOT trigger for RICD (Remand in Customs detention) parent results.
- **FR-017**: The rule MUST NOT trigger for Extradition "on conditional bail" results (E1CB, E1CONCB, E2CB, etc.) as they set post-hearing custody status A, not B.
- **FR-018**: Welsh language versions of all five error messages (A–E) MUST be provided.
- **FR-019**: The DB migration `V1.017__pre_release_bail_condition_error.sql` MUST insert the rule record into the `validation_rule` table.
- **FR-020**: A `CRA-363.http` file MUST be created for local HTTP testing, covering every AC.

### Key Entities

- **Conditional bail parent result**: Any result whose short code is RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB, CCIC, or CCSC.
- **Remand-on-conditional-bail result**: Subset of the above: RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB (excludes CCIC and CCSC).
- **Pre-release bail condition result**: Any child result whose short code is one of: PREBC, PRBCO, PRBCP1, PRBCP2, PRBCP3, PRBCP4, PRBCP5, PRBCP6, PRBCP7, PRBCP8, PRBCS1, PRBCS2, PRBCS3, PRBCSE1, PRBCSE2, PRBCT.
- **Offence**: A charge in the hearing. Trigger is per-offence: parent result and child results must be on the **same** offence.
- **Defendant**: Carries a date of birth used to determine adult/youth. Absence of DOB → combined Error C.
- **Error message variants**: Error A (adult + remand), Error B (youth + remand), Error C (combined/no-DOB + remand), Error D (CCIC, any age), Error E (CCSC, any age).

---

## Error Message Reference

| ID      | Trigger                                           | Message (English)                                                                                                                                                                                                                                                                                    |
|---------|---------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Error A | Adult (≥18), remand on conditional bail parent    | A conditional bail result cannot have pre-release bail conditions. If the court ordered pre-release conditions, use 'Remanded in custody with bail direction (RIB)' instead. If the conditions apply after release, remove the pre-release conditions and enter them as bail conditions.               |
| Error B | Youth (<18), remand on conditional bail parent    | A conditional bail result cannot have pre-release bail conditions. If the court ordered pre-release conditions, use 'Remand in care of Local Authority with bail direction (RILAB)' instead. If the conditions apply after release, remove the pre-release conditions and enter them as bail conditions. |
| Error C | Mixed adult+youth or no DOB, remand bail parent   | A conditional bail result cannot have pre-release bail conditions. If the court ordered pre-release conditions, use 'Remanded in custody with bail direction (RIB)' for an adult or 'Remand in care of Local Authority with bail direction (RILAB)' for a youth instead. If the conditions apply after release, remove the pre-release conditions and enter them as bail conditions. |
| Error D | CCIC parent, any age                              | A conditional bail result cannot have pre-release bail conditions. If the court ordered pre-release conditions, use 'Sent to Crown Court in custody for trial with bail direction (CCIIB)' instead. If the conditions apply after release, remove the pre-release conditions and enter them as bail conditions. |
| Error E | CCSC parent, any age                              | A conditional bail result cannot have pre-release bail conditions. If the court ordered pre-release conditions, use 'Committed to Crown Court for sentence - in custody with bail direction (CCSIB)' instead. If the conditions apply after release, remove the pre-release conditions and enter them as bail conditions. |

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of submissions with a conditional bail parent result paired with a pre-release bail condition child result on the same offence are blocked from sharing.
- **SC-002**: The correct age-specific or combined error message is displayed for every triggered offence on 100% of requests, with zero instances of the wrong message variant being shown.
- **SC-003**: The Share button is absent from Manage Hearings on 100% of hearings where the error condition remains unresolved.
- **SC-004**: The error is cleared and sharing is re-enabled on 100% of hearings where the clerk applies a valid correction to every affected offence.
- **SC-005**: The rule does not trigger on any of the defined negative cases (wrong parent type, no pre-release child, different offence) — zero false positives in the test suite.
- **SC-006**: All 16 pre-release bail condition short codes individually trigger the rule in unit tests.
- **SC-007**: Unit tests, integration tests (IT), and API tests collectively achieve 100% coverage of all backend-testable ACs (AC1, AC2, AC2A, AC2B, AC3, AC4, AC4A, AC5A, AC7, AC8, AC9). AC5 (GDS focus management and error summary links) and AC6 (Share button visibility on Manage Hearings) are UI rendering behaviours verified by the Common Platform UI service's own acceptance test suite.
- **SC-008**: The rule co-exists with all existing validation rules without suppression or interference, verified by at least one multi-rule integration test.

---

## Assumptions

- The YAML rule `DR-BAIL-015.yaml` (priority 15000) will use a preprocessor type that does not yet exist (`preReleaseBailCondition` or similar); a new `ValidationPreprocessor` `@Component` is required alongside the new context record.
- "Date of hearing" for adult/youth determination is the hearing date on the `DraftValidationRequest`, consistent with all other age-dependent rules in this service.
- The `validation_rule` DB row for this rule uses rule id `DR-BAIL-015`; severity ceiling is `ERROR` (no downgrade expected at launch).
- RICD is excluded from the parent result set; this aligns with a known reference data anomaly raised with the data team.
- Extradition results (E1CB, E1CONCB, E2CB, etc.) are excluded because their post-hearing custody status is A, not B.
- Welsh translations for all five error messages will be provided by the content team before the PR is merged; placeholder Welsh text may be used in the spec but must be replaced with approved translations in implementation.
- The `CRA-363.http` file will target `localhost:4550` (default local port) and cover all ACs with representative payloads.
- README update is in scope: the new rule must be added to the validation rules table in `README.md`.
- The `after_specify` git commit hook is optional and will be executed at the user's discretion.
