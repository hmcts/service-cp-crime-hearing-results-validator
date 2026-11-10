# Feature Specification: Pre-Release Bail Condition Warning

**Feature Branch**: `dev/CRA-362-pre-release-bail-condition-warning`
**Created**: 2026-11-09
**Status**: Draft
**Jira**: CRA-362

## Overview

When a remand result changes a defendant's status from custodial remand (Custody, Care of Local Authority, or Youth Detention Accommodation) to conditional bail, and the bail conditions entered are of a kind typically imposed as a pre-release condition, the caseworker may have selected the wrong remand result. This feature adds an advisory WARNING (non-blocking — it does not prevent sharing) displayed at offence level against each triggering offence, so the caseworker can review before sharing.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 – Adult defendant warning on conditional bail result (Priority: P1)

A caseworker results an adult defendant (18 or over on the hearing date) on an offence where the current remand status is Custody (C), Care of Local Authority (L), or Youth Detention Accommodation (S). They enter a conditional bail result code (RC, RCBV, BAILREVC, REMCB, REMCBY, or RCCCB) and also add a pre-release-type bail condition (e.g. PASS1–PASS8, PORSC, PORS). When they save and continue to Manage Hearings, Warning A appears against each such offence.

**Why this priority**: Core path — adult defendants are the most common case; this directly prevents sharing a likely incorrect remand result.

**Independent Test**: Submit a validation request with an adult defendant, offence bailStatus=C, result shortCode=RC, and a PASS1 bail condition result line. Assert Warning A appears at offence level and sharing is not blocked.

**Acceptance Scenarios**:

1. **Given** defendant is adult (18+), offence bailStatus ∈ {C, L, S}, result ∈ {RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB}, and a trigger bail condition shortCode ∈ {PASS1–PASS8, PORSC, PORS} is present, **When** validation is run, **Then** Warning A is shown at offence level and sharing proceeds normally (warning is advisory)
2. **Given** the same trigger conditions, **When** result shortCode is changed to a non-conditional-bail code (e.g. RIB), **Then** no warning is shown
3. **Given** the same trigger conditions, **When** offence bailStatus is not in {C, L, S} (e.g. B), **Then** no warning is shown

---

### User Story 2 – Youth defendant warning on conditional bail result (Priority: P1)

Identical to User Story 1 but the defendant is under 18 on the hearing date. Warning B is shown instead of Warning A, and the suggested alternative result is RILAB (youth-specific).

**Why this priority**: Equal priority to adult path — youth defendants have a different suggested alternative and the distinction must be clear.

**Independent Test**: Submit a request with a youth defendant (DOB within last 17 years), offence bailStatus=C, result=RC, trigger bail condition. Assert Warning B (not A) appears.

**Acceptance Scenarios**:

1. **Given** defendant is under 18 on hearing date, trigger conditions met (bailStatus ∈ {C,L,S}, conditional bail result, trigger bail condition), **When** validation runs, **Then** Warning B is shown at offence level
2. **Given** defendant is under 18 and trigger conditions met, **When** result is CCIC, **Then** Warning D is shown (not Warning B — CCIC overrides age-based selection)

---

### User Story 3 – No date of birth recorded (Priority: P2)

The defendant has no date of birth on record. All trigger conditions are met with a conditional bail result (RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB). Warning C (combined adult/youth message) is shown, advising the caseworker to check whether RIB or RILAB applies.

**Why this priority**: Important edge case — missing DOB is a known data quality gap during transition period.

**Independent Test**: Submit a request with no defendant DOB, offence bailStatus=C, result=RC, trigger bail condition. Assert Warning C appears.

**Acceptance Scenarios**:

1. **Given** defendant has no DOB, all other trigger conditions met with RC/RCBV/BAILREVC/REMCB/REMCBY/RCCCB result, **When** validation runs, **Then** Warning C is shown at offence level
2. **Given** defendant has no DOB, result is CCIC, **Then** Warning D is shown (CCIC result type overrides DOB-absent path)

---

### User Story 4 – Crown Court transfer results (CCIC / CCSC) (Priority: P2)

The offence remand status is custodial (C, L, or S) and the new result is CCIC (Sent to Crown Court for trial on conditional bail) or CCSC (Committed to Crown Court for sentence on conditional bail). A trigger bail condition is present. Warning D (CCIC) or Warning E (CCSC) is shown regardless of defendant age or DOB availability.

**Why this priority**: Crown Court transfer paths have distinct suggested alternatives (CCIIB / CCSIB) and must be handled separately from the standard conditional bail path.

**Independent Test**: Submit a request with offence bailStatus=C, result=CCIC, trigger bail condition, adult defendant. Assert Warning D (not Warning A) appears.

**Acceptance Scenarios**:

1. **Given** offence bailStatus ∈ {C,L,S}, result=CCIC, trigger bail condition present, **When** validation runs, **Then** Warning D is shown regardless of defendant age
2. **Given** offence bailStatus ∈ {C,L,S}, result=CCSC, trigger bail condition present, **When** validation runs, **Then** Warning E is shown regardless of defendant age
3. **Given** result=CCIC or CCSC but offence bailStatus is not custodial, **Then** no warning is shown

---

### User Story 5 – "Other" free-text bail condition triggers warning (Priority: P2)

The caseworker selects the "Other" bail condition (PORO1) and enters free text that contains any of the trigger keywords (passport, surety, security or their Welsh equivalents), case-insensitively and regardless of diacritics. The same warning is shown as for the named trigger bail conditions.

**Why this priority**: Prevents caseworkers from bypassing the check by using the free-text "Other" condition.

**Independent Test**: Submit a request with result=RC, bailStatus=C, adult defendant, bail condition shortCode=PORO1 and prompt text containing "Passport". Assert Warning A appears.

**Acceptance Scenarios**:

1. **Given** bail condition shortCode=PORO1 and prompt text contains "passport" (case-insensitive), **When** validation runs, **Then** same warning fires as for named trigger conditions (Warning A/B/C/D/E depending on result and age)
2. **Given** PORO1 prompt text contains "surety" or "sureties", **When** validation runs, **Then** warning fires
3. **Given** PORO1 prompt text contains "security" or "securities", **When** validation runs, **Then** warning fires
4. **Given** PORO1 prompt text contains Welsh equivalents (pasbort/pasbortau/basbort, mechnïwr/mechnïwyr/fechnïwr/fechnïwyr, sicrwydd) with or without diacritics, **When** validation runs, **Then** warning fires
5. **Given** PORO1 prompt text does NOT contain any trigger keyword, **When** validation runs, **Then** no warning is shown for the PORO1 condition
6. **Given** matching is done on stems: "passport" (covers passport/passports), "suret" (covers surety/sureties), "securit" (covers security/securities), "asbort" (covers Welsh passport variants), "echniw"/"echnïw" (Welsh surety variants), "sicrwydd" (Welsh security)

---

### User Story 6 – Warning does not block sharing (Priority: P1)

The warning is advisory. Caseworkers can share results even when the warning is displayed. No other warnings for the same defendant or offence are suppressed by this warning.

**Why this priority**: Explicitly specified in AC4/AC4A — blocking behaviour would be a regression.

**Independent Test**: Submit a triggering request; assert the response severity is WARNING (not ERROR) and other co-existing warnings are present in full.

**Acceptance Scenarios**:

1. **Given** warning conditions are met, **When** caseworker shares results, **Then** sharing succeeds — warning does not create an error
2. **Given** warning conditions are met and another rule also fires a warning for the same offence, **When** validation runs, **Then** both warnings appear independently; neither suppresses the other

---

### User Story 7 – No warning when trigger conditions absent (Priority: P1)

No warning is shown when any of the trigger conditions is absent: offence remand status is not custodial; result does not change to conditional bail; no trigger bail condition; PORO1 without trigger keywords.

**Why this priority**: False positives would erode trust in the validation system.

**Independent Test**: Submit a request where offence bailStatus=B (bail), result=RC, trigger bail condition. Assert no warning for this rule.

**Acceptance Scenarios**:

1. **Given** offence bailStatus is not in {C, L, S}, **When** validation runs, **Then** no warning
2. **Given** result shortCode is not in conditional-bail set (e.g. RIB, RILAB, CCIIB, CCSIB or unconditional bail), **When** validation runs, **Then** no warning
3. **Given** no trigger bail condition present (no PASS1–8, PORSC, PORS, and no PORO1 with keyword), **When** validation runs, **Then** no warning
4. **Given** PORO1 is present but its free text contains none of the trigger keywords, **When** validation runs, **Then** no warning

---

### Edge Cases

- Defendant with no offence-level remand status (`OffenceDto.bailStatus == null`): offence is treated as non-custodial — no warning is shown (fail-open). `DefendantDto` has no remand-status field, so no fallback is possible or attempted.
- Multiple offences for the same defendant: each qualifying offence shows the warning independently (warning is at offence level)
- Offence has multiple result lines including both a conditional bail result and a trigger bail condition: the combination triggers the warning
- Multiple trigger bail conditions on the same offence: the warning fires once per offence (not per bail condition)
- Mixed hearing with multiple defendants: warnings for each defendant are shown against that defendant's offences only; other defendants' offences are unaffected
- CCIC/CCSC results override age-based warning selection — Warning D or E is always shown regardless of adult/youth/no-DOB status

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST display a WARNING (non-blocking) at offence level when: (a) the offence current remand status is C, L, or S; AND (b) the result entered changes the remand status to conditional bail; AND (c) at least one trigger bail condition is present for that offence
- **FR-002**: The system MUST classify the warning message based on result code and defendant age:
  - Result RC/RCBV/BAILREVC/REMCB/REMCBY/RCCCB + adult (18+) → Warning A
  - Result RC/RCBV/BAILREVC/REMCB/REMCBY/RCCCB + youth (<18) → Warning B
  - Result RC/RCBV/BAILREVC/REMCB/REMCBY/RCCCB + no DOB recorded → Warning C
  - Result CCIC (any age/DOB status) → Warning D
  - Result CCSC (any age/DOB status) → Warning E
- **FR-003**: The system MUST recognise the following shortCodes as trigger bail conditions: PASS1, PASS2, PASS3, PASS4, PASS5, PASS6, PASS7, PASS8, PORSC, PORS
- **FR-004**: The system MUST recognise PORO1 as a trigger bail condition when the associated free-text prompt contains any of the following trigger stems, matched case-insensitively and accent-insensitively anywhere in the text: "passport", "suret", "securit", "asbort", "echnïw"/"echniw", "sicrwydd"
- **FR-005**: The system MUST NOT show a warning when the offence remand status is not in {C, L, S}
- **FR-006**: The system MUST NOT show a warning when the result code is not a conditional bail result (i.e. not in {RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB, CCIC, CCSC})
- **FR-007**: The system MUST NOT show a warning when no trigger bail condition is present and PORO1 (if present) does not contain a trigger keyword
- **FR-008**: The WARNING MUST NOT block sharing — the response status when only this warning fires must allow the result to be shared
- **FR-009**: This warning MUST NOT suppress any other concurrently triggered warning for the same defendant or offence
- **FR-010**: The system MUST show the warning at offence level only (not at the top-level defendant or hearing level)
- **FR-011**: When `OffenceDto.bailStatus` is null (no offence-level remand status recorded), the system MUST treat the offence as not custodial and produce no warning (fail-open behaviour). No upstream DTO change is required; `DefendantDto` carries no remand-status field.
- **FR-012**: Age is determined by defendant date of birth relative to the hearing date; when date of birth is absent, Warning C applies for RC/RCBV/BAILREVC/REMCB/REMCBY/RCCCB results

### Warning Message Text

- **Warning A** (adult, conditional bail result): "Do not share until you have checked the remand result. This result releases the defendant from custody. If any bail conditions are pre-release conditions, use 'Remanded in custody with bail direction (RIB)' instead."
- **Warning B** (youth, conditional bail result): "Do not share until you have checked the remand result. This result releases the defendant on bail. If any bail conditions are pre-release conditions, use 'Remand in care of Local Authority with bail direction (RILAB)' instead."
- **Warning C** (no DOB, conditional bail result): "Do not share until you have checked the remand result. This result releases the defendant on bail. If any bail conditions are pre-release conditions, use 'Remanded in custody with bail direction (RIB)' for an adult or 'Remand in care of Local Authority with bail direction (RILAB)' for a youth instead."
- **Warning D** (CCIC, any age): "Do not share until you have checked the remand result. This result releases the defendant on bail. If any bail conditions are pre-release conditions, use 'Sent to Crown Court in custody for trial with bail direction (CCIIB)' instead."
- **Warning E** (CCSC, any age): "Do not share until you have checked the remand result. This result releases the defendant on bail. If any bail conditions are pre-release conditions, use 'Committed to Crown Court for sentence - in custody with bail direction (CCSIB)' instead."

### Warning Matrix

| Result code(s)                              | Adult (18+) | Youth (<18) | No DOB    |
|---------------------------------------------|-------------|-------------|-----------|
| RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB   | Warning A   | Warning B   | Warning C |
| CCIC                                        | Warning D   | Warning D   | Warning D |
| CCSC                                        | Warning E   | Warning E   | Warning E |

### Key Entities

- **Offence**: Has a current remand status (bailStatus C/L/S/B) and an offence identifier. The trigger checks this status to determine if the defendant is currently in custody/care/detention.
- **Result line**: A result shortCode on an offence. Conditional bail result codes (RC, RCBV, etc.) change the remand status to conditional bail. Trigger bail condition shortCodes (PASS1–8, PORSC, PORS, PORO1) indicate a pre-release-type condition was entered.
- **PORO1 prompt**: Free text entered alongside the PORO1 "Other" bail condition. Trigger keywords in this text activate the same logic as named bail condition codes.
- **Defendant**: Has an optional date of birth used to determine adult/youth status relative to the hearing date.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Warning A is shown for every adult-defendant offence meeting trigger conditions; zero false positives and zero false negatives across all AC1 test cases
- **SC-002**: Warning B is shown for every youth-defendant offence meeting trigger conditions; Warning C when no DOB; zero misclassifications
- **SC-003**: Warning D is shown for every CCIC-triggered offence regardless of defendant age or DOB; Warning E for every CCSC-triggered offence
- **SC-004**: PORO1 keyword matching correctly fires for all trigger stems (passport, suret, securit, asbort, echnïw/echniw, sicrwydd) in both English and Welsh, case-insensitively and accent-insensitively; correctly suppresses the warning when none is present
- **SC-005**: Warning does not block sharing — response allows sharing in 100% of cases where only this rule fires
- **SC-006**: When no trigger condition is met, the rule produces zero warnings — 100% true-negative rate across AC5 test scenarios
- **SC-007**: Other concurrent warnings for the same defendant or offence are unaffected — co-existence verified in AC4A test scenario
- **SC-008**: 100% test coverage (unit, integration, API) across all AC1–AC7 scenarios

---

## Assumptions

- **Offence-level remand status** is carried by `OffenceDto.bailStatus` (BailStatusEnum: C=Custody, L=Remanded into care of Local Authority, S=Remanded to youth detention accommodation). When null, the offence is treated as non-custodial — no warning is shown (fail-open). `DefendantDto` has no remand-status field; no runtime fallback is possible. The AC7 production data check (ensuring `bailStatus` is populated) is an operational/DBA concern outside the validator service.
- **Bail condition result lines** are recorded as separate `ResultLineDto` entries on the same offence, with `shortCode` values matching the bail condition codes (PASS1–PASS8, PORSC, PORS, PORO1). They coexist with the main remand result line on the same offence.
- **PORO1 free text** is passed via `ResultLineDto.prompts` with a known `promptRef`; the `promptValue` contains the caseworker's free-text entry.
- **Defendant date of birth** is available via `DefendantDto.dateOfBirth` (nullable, `LocalDate`). Absent DOB is treated as Warning C (fail-safe, not fail-open) for RC/RCBV/BAILREVC/REMCB/REMCBY/RCCCB results.
- **Age calculation** uses the hearing date (`DraftValidationRequest.hearingDay`) as the reference date.
- **Accent-insensitive matching** for Welsh terms is implemented via Unicode NFD normalisation followed by stripping of combining diacritical marks, then stem search.
- Warning severity is WARNING in the YAML; the DB migration inserts the row with `severity = 'WARNING'`.
- Rule ID: **DR-BAIL-016**, priority: **17000**, migration: **V1.018__insert_dr_bail_016.sql**.
- Spec folder: **017-pre-release-bail-condition-warning**, spec number: **017**.
- New preprocessor: **PreReleaseBailPreprocessor** with qualifier `"pre-release-bail-condition"`, producing **PreReleaseBailContext** per offence.
- PreprocessingDefinition fields to be added: `conditionalBailResultCodes`, `crownCourtBailCodes`, `triggerBailConditionCodes`, `otherBailConditionCode`.
