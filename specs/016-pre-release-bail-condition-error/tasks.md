# Tasks: Pre-Release Bail Condition Error (DR-BAIL-015 / CRA-363)

**Input**: Design documents from `specs/016-pre-release-bail-condition-error/`
**Branch**: `CRA-363-pre-release-bail-error`
**Tests**: TDD mandatory (Constitution Principle VIII) + 100% AC coverage explicitly required by spec FR-007.

**Organization**: Constitution Principle I (YAML/CEL Rule-First) requires YAML before Java.
TDD (Principle VIII) requires each failing test to be written and confirmed failing BEFORE the production code that satisfies it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared dependencies)
- **[Story]**: User story from spec.md (US1–US10)

---

## Phase 1: YAML Rule Contract (Constitution Principle I — YAML First)

**Purpose**: Define the rule contract (id, conditions, preprocessing type, short-code lists) in YAML before any Java changes. The YAML is the authoritative source of truth.

- [X] T001 Create `src/main/resources/rules/DR-BAIL-015.yaml` with rule id `DR-BAIL-015`, priority `15000`, enabled `true`, preprocessing type `pre-release-bail-condition`, all 6 `remandConditionalBailShortCodes` (RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB), `ccicShortCodes: [CCIC]`, `ccscShortCodes: [CCSC]`, all 16 `preReleaseBailConditionShortCodes` (PREBC, PRBCO, PRBCP1–PRBCP8, PRBCS1–PRBCS3, PRBCSE1, PRBCSE2, PRBCT), and all 7 conditions (AC_ADULT, AC_YOUTH, AC_MIXED_ADULT, AC_MIXED_YOUTH, AC_NODOB, AC_CCIC, AC_CCSC) with exact error message texts from spec.md (Errors A–E), `affectedOffenceSet: "triggeredOffenceId"`, `validationLevel: OFFENCE`; use `errorMessageTemplate` on every condition. Full YAML content is in `specs/016-pre-release-bail-condition-error/data-model.md`.

---

## Phase 2: Foundational (Core Java Types — TDD)

**Purpose**: Extend `PreprocessingDefinition`, implement the context record and preprocessor. These are shared prerequisites for ALL user stories.

**⚠️ CRITICAL**: All phases 3–10 depend on this phase being complete. Write tests FIRST; confirm they fail for the correct reason (ClassNotFoundException / compilation error → not yet an assertion failure) before implementing.

- [X] T002 Extend `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`: append four new fields at the end of the record — `List<String> remandConditionalBailShortCodes`, `List<String> ccicShortCodes`, `List<String> ccscShortCodes`, `List<String> preReleaseBailConditionShortCodes` — with inline comments identifying each as used by `PreReleaseBailConditionPreprocessor`. Ensure the Lombok `@Builder` annotation still applies (it is on the record already) and that the new fields default-null safely for all existing preprocessors that don't supply them.

- [X] T003 Write failing unit test `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailContextTest.java`: `@Nested @DisplayName` groups for `toCelContext`, `getOffenceIdSet`, `defendantId`, `defendantName`, `allOffenceIds`. Test that `toCelContext()` returns all 8 keys (isRemandBail, isCcic, isCcsc, isAdult, isYouth, hasDob, hearingHasRemandAdult, hearingHasRemandYouth) with correct long values; that `getOffenceIdSet("triggeredOffenceId")` returns a single-element list; that `getOffenceIdSet("unknown")` throws `IllegalArgumentException`. Confirm test FAILS with `ClassNotFoundException` before T004.

- [X] T004 Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailContext.java`: Java record implementing `RuleEvaluationContext` with fields `String offenceId`, `String defendantId`, `String defendantName`, `long isRemandBail`, `long isCcic`, `long isCcsc`, `long isAdult`, `long isYouth`, `long hasDob`, `long hearingHasRemandAdult`, `long hearingHasRemandYouth`; `toCelContext()` returns an 8-entry `Map.of(...)` of all long fields; `getOffenceIdSet("triggeredOffenceId")` returns `List.of(offenceId)` and throws `IllegalArgumentException` for any other name; `defendantId()`, `defendantName()`, `allOffenceIds()` return their respective fields. Run `gradle test --tests "*.PreReleaseBailContextTest"` — all green.

- [X] T005 Write failing unit test `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailConditionPreprocessorTest.java` with `@Nested @DisplayName` groups covering: (a) `type()` returns `"pre-release-bail-condition"`; (b) returns empty map when resultLines is null or empty; (c) returns empty map when no offence has both a conditional-bail parent AND a pre-release child; (d) adult defendant with RC parent + PREBC child → one context with isRemandBail=1, isAdult=1, isYouth=0, hasDob=1, hearingHasRemandAdult=1, hearingHasRemandYouth=0; (e) youth defendant with REMCBY parent + PRBCO child → isYouth=1, isAdult=0, hasDob=1, hearingHasRemandYouth=1; (f) no DOB defendant with RC parent → hasDob=0, isAdult=0, isYouth=0; (g) CCIC parent + PREBC child → isCcic=1, isRemandBail=0; (h) CCSC parent + PREBC child → isCcsc=1; (i) mixed adult+youth hearing → hearingHasRemandAdult=1 AND hearingHasRemandYouth=1 on both contexts; (j) pre-release code on DIFFERENT offence from parent → no context emitted; (k) `@ParameterizedTest @ValueSource` over all 6 remand parent codes (RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB) — each triggers isRemandBail=1; (l) `@ParameterizedTest @ValueSource` over all 16 pre-release child codes (PREBC, PRBCO, PRBCP1–PRBCP8, PRBCS1–PRBCS3, PRBCSE1, PRBCSE2, PRBCT) — each triggers the rule; (m) RICD parent + PREBC child → no context emitted; (n) unconditional bail parent (e.g. UB) + PREBC child → no context emitted. Confirm FAILS with ClassNotFoundException before T006.

- [X] T006 Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailConditionPreprocessor.java`: `@Component` implementing `ValidationPreprocessor`; `QUALIFIER = "pre-release-bail-condition"`; `type()` returns `QUALIFIER`. Implement `preprocess()` using the three-pass algorithm from `specs/016-pre-release-bail-condition-error/data-model.md`: (Pass 1) scan result lines into `parentCodeByOffence`, `preReleaseOffences`, `defendantByOffence`; (Pass 2) compute hearing-wide `hearingHasRemandAdult` / `hearingHasRemandYouth` flags; (Pass 3) emit one `PreReleaseBailContext` per triggered offence. Build defendant name from `PreprocessorHelper.buildDefendantNames(request)`. Use `PreprocessorHelper.upperSet()` for case-insensitive short-code matching. Age check: `Period.between(dob, hearingDay).getYears() >= 18` (fail-safe to `false` when either date is null). Run `gradle test --tests "*.PreReleaseBailConditionPreprocessorTest"` — all green.

- [X] T007 Create `src/main/resources/db/migration/V1.017__pre_release_bail_condition_error.sql`:
  ```sql
  INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by)
  VALUES ('DR-BAIL-015', true, 'ERROR', now(), 'system');
  ```

**Checkpoint**: Run `gradle build`. YAML rule loads at boot (no unresolvable preprocessor qualifier), unit tests pass, SQL migration file is syntactically valid. Foundation is complete — IT tests can now be written.

---

## Phase 3: User Stories 1 & 2 — Adult and Youth Remand Bail Errors (Priority: P1) 🎯 MVP

**Goal**: The service returns ERROR with the correct age-specific message (Error A for adults, Error B for youths) when a remand-on-conditional-bail parent result is paired with a pre-release bail condition child result on the same offence.

**Independent Test**: POST to `/api/validation/validate` with RC parent + PREBC child for an adult defendant → `isValid: false`, `errors.validationIssues[0].ruleId = "DR-BAIL-015"`, error message contains "Remanded in custody with bail direction (RIB)".

### Tests for User Stories 1 & 2 ⚠️ Write FIRST — confirm FAIL before implementing

- [X] T008 [US1] Write failing IT test `src/test/java/uk/gov/hmcts/cp/integration/PreReleaseBailConditionRuleIT.java` extending `IntegrationTestBase`; add method `adultDefendant_rcParent_prebcChild_shouldRaiseErrorA()`: POST a hearing with adult defendant (DOB = hearingDay minus 25 years), offence1 with result lines shortCode=RC and shortCode=PREBC; assert `status 200`, `isValid: false`, `errors.validationIssues` has one entry with `ruleId=DR-BAIL-015`, `severity=ERROR`; `errors.errorMessages` has one entry containing "Remanded in custody with bail direction (RIB)" and NOT containing "RILAB"; assert inline `affectedOffences` message also contains "Remanded in custody with bail direction (RIB)". Confirm FAILS with a meaningful assertion failure (not a boot error) — the YAML + preprocessor from Phase 2 are already in place.

- [X] T009 [P] [US1] Add IT method `adultDefendant_allRemandParentCodes_shouldRaiseErrorA`: `@ParameterizedTest @ValueSource(strings = {"RC","RCBV","BAILREVC","REMCB","REMCBY","RCCCB"})` — each code with adult defendant + PREBC child → ERROR, Error A text.

- [X] T010 [P] [US2] Add IT method `youthDefendant_rcParent_prebcChild_shouldRaiseErrorB()`: same structure but DOB = hearingDay minus 15 years; assert error message contains "Remand in care of Local Authority with bail direction (RILAB)" and NOT "RIB)'" (excluding the RILAB part). Confirm FAILS before production is complete — here it should already pass if DR-BAIL-015.yaml and preprocessor are correctly implemented. If it passes immediately, that is correct TDD behaviour (the YAML condition AC_YOUTH is already written).

- [X] T011 [P] [US2] Add IT method `youthDefendant_allRemandParentCodes_shouldRaiseErrorB`: `@ParameterizedTest @ValueSource(strings = {"RC","RCBV","BAILREVC","REMCB","REMCBY","RCCCB"})` — each code with youth + PREBC child → Error B text.

- [X] T012 [P] [US1] Add IT method `allPreReleaseCodes_withAdultAndRemandParent_shouldTriggerRule`: `@ParameterizedTest @ValueSource(strings = {"PREBC","PRBCO","PRBCP1","PRBCP2","PRBCP3","PRBCP4","PRBCP5","PRBCP6","PRBCP7","PRBCP8","PRBCS1","PRBCS2","PRBCS3","PRBCSE1","PRBCSE2","PRBCT"})` — each code with RC parent + adult → ERROR DR-BAIL-015.

**Checkpoint**: Run `gradle test --tests "*.PreReleaseBailConditionRuleIT"`. US1 and US2 test methods all pass. The rule correctly fires Error A for adults and Error B for youths.

---

## Phase 4: User Stories 3 & 4 — CCIC and CCSC Errors (Priority: P2)

**Goal**: The service returns Error D for CCIC parent results and Error E for CCSC parent results, regardless of defendant age.

**Independent Test**: POST with CCIC parent + PREBC child → error message contains "Sent to Crown Court in custody for trial with bail direction (CCIIB)".

### Tests for User Stories 3 & 4 ⚠️ Write FIRST

- [X] T013 [P] [US3] Add IT method `ccicParent_adultDefendant_withPreRelease_shouldRaiseErrorD()`: POST with CCIC parent + PRBCO child + adult defendant; assert `isValid: false`, DR-BAIL-015 error, message contains "Sent to Crown Court in custody for trial with bail direction (CCIIB)".

- [X] T014 [P] [US3] Add IT method `ccicParent_youthDefendant_withPreRelease_shouldRaiseErrorD()`: same but youth DOB; assert same Error D text — verifies "any age" requirement (AC2A).

- [X] T015 [P] [US4] Add IT method `ccscParent_adultDefendant_withPreRelease_shouldRaiseErrorE()`: POST with CCSC parent + PRBCS1 child + adult; assert message contains "Committed to Crown Court for sentence - in custody with bail direction (CCSIB)".

- [X] T016 [P] [US4] Add IT method `ccscParent_youthDefendant_withPreRelease_shouldRaiseErrorE()`: same but youth; same Error E text — verifies "any age" (AC2B).

**Checkpoint**: `gradle test --tests "*.PreReleaseBailConditionRuleIT#ccic*" "*.PreReleaseBailConditionRuleIT#ccsc*"` — all pass.

---

## Phase 5: User Stories 5 & 6 — Mixed Adult/Youth and No-DOB Errors (Priority: P2)

**Goal**: Mixed adult+youth hearing with remand bail shows one Error C in the summary with both defendant names, but age-specific Error A/B inline. No-DOB defendant shows Error C both in summary and inline.

**Independent Test**: POST with adult (RC + PREBC) and youth (REMCBY + PRBCO) in the same hearing → `errors.errorMessages` has exactly ONE entry (Error C text), inline issues show Error A above adult's offence and Error B above youth's.

### Tests for User Stories 5 & 6 ⚠️ Write FIRST

- [X] T017 [US5] Add IT method `mixedAdultYouth_remandBail_summaryShowsErrorC_inlineIsAgeSpecific()`: POST with two defendants — adult (RC + PREBC) and youth (REMCBY + PRBCO); assert `errors.errorMessages` has size 1 (not 2), the single message contains "RIB" AND "RILAB" (Error C combined text); assert `errors.validationIssues` has two entries, one per offence, with inline messages: adult offence message contains "RIB)" and NOT "RILAB", youth offence message contains "RILAB)" and NOT includes "RIB)'" (excluding the RIB part from Error C context).

- [X] T018 [US6] Add IT method `noDobDefendant_remandBail_shouldRaiseErrorC()`: POST with defendant (no dateOfBirth field), RC parent + PREBC child; assert `isValid: false`, `errors.errorMessages` has one entry containing both "RIB" and "RILAB" (Error C text); inline message above the offence also contains "RIB" and "RILAB".

**Checkpoint**: `gradle test --tests "*.PreReleaseBailConditionRuleIT#mixed*" "*.PreReleaseBailConditionRuleIT#noDob*"` — all pass.

---

## Phase 6: User Stories 7 & 8 — Multi-Defendant Names and Multiple Parent Types (Priority: P3)

**Goal**: Error summary entries append "This affects [name(s)]" for multi-defendant hearings; separate summary entries for each distinct error message type (A/B/C, D, E) when multiple parent result types are triggered in one hearing.

**Independent Test (US7)**: POST with two adults both triggering Error A → `errors.errorMessages[0]` ends with "This affects John Smith and Jane Jones".

**Independent Test (US8)**: POST with one defendant with RC+PREBC and another with CCIC+PREBC → `errors.errorMessages` has two entries: one Error A/C text and one Error D text.

### Tests for User Stories 7 & 8 ⚠️ Write FIRST

- [X] T019 [P] [US7] Add IT method `twoAdultDefendants_bothTriggerErrorA_namesAppendedWithAnd()`: POST with two adult defendants each on separate offences, both RC+PREBC; assert `errors.errorMessages[0]` contains "This affects" followed by both defendant names joined by "and".

- [X] T020 [P] [US7] Add IT method `threeDefendants_allTriggerSameError_namesCommaSeparated()`: POST with three adult defendants all triggering Error A; assert `errors.errorMessages[0]` contains defendant names separated by ", " (AC3 formatting).

- [X] T021 [P] [US7] Add IT method `singleDefendant_noThisAffectsSuffix()`: POST with single adult defendant triggering Error A; assert `errors.errorMessages[0]` does NOT contain "This affects".

- [X] T022 [P] [US8] Add IT method `remandBailAndCcicOnSameHearing_separateSummaryEntries()`: POST with adult defendant1 (RC + PREBC) and defendant2 (CCIC + PRBCO); assert `errors.errorMessages` has exactly 2 entries — one containing "RIB" (Error A), one containing "CCIIB" (Error D); each with appropriate defendant name suffix.

- [X] T023 [P] [US8] Add IT method `remandBailAndCcscOnSameHearing_separateSummaryEntries()`: POST with defendant1 (REMCB + PREBC, adult) and defendant2 (CCSC + PRBCS1); assert two summary entries with Error A and Error E texts.

**Checkpoint**: `gradle test --tests "*.PreReleaseBailConditionRuleIT"` — all tests pass.

---

## Phase 7: User Stories 9 & 10 — Error Cleared and Negative Cases (Priority: P1)

**Goal (US9)**: After correcting the parent result to RIB/RILAB/CCIIB/CCSIB (or removing the pre-release child), resubmission returns `isValid: true`. **Goal (US10)**: Non-trigger cases produce no DR-BAIL-015 error.

### Tests for User Stories 9 & 10 ⚠️ Write FIRST

- [X] T024 [P] [US9] Add IT method `adultParentChangedToRib_shouldClearError()`: first establish error (RC + PREBC + adult), then POST with parent changed to RIB and same PREBC child; assert `isValid: true`, no DR-BAIL-015 issues.

- [X] T025 [P] [US9] Add IT method `youthParentChangedToRilab_shouldClearError()`: RC → RILAB + PRBCO + youth; assert `isValid: true`.

- [X] T026 [P] [US9] Add IT method `ccicParentChangedToCciib_shouldClearError()`: CCIC → CCIIB + PREBC; assert `isValid: true`.

- [X] T027 [P] [US9] Add IT method `ccscParentChangedToCcsib_shouldClearError()`: CCSC → CCSIB + PRBCS2; assert `isValid: true`.

- [X] T028 [P] [US9] Add IT method `preReleaseChildRemoved_shouldClearError()`: POST without any pre-release bail condition short codes on the offence (only RC parent) → assert `isValid: true`, no DR-BAIL-015.

- [X] T029 [P] [US10] Add IT method `ribParent_withPreReleaseChild_shouldNotTrigger()`: POST with RIB + PREBC on same offence; assert no DR-BAIL-015 error (AC8 negative case).

- [X] T030 [P] [US10] Add IT method `conditionalBailParent_noPreReleaseChild_shouldNotTrigger()`: POST with RC parent only (no pre-release child codes); assert `isValid: true`.

- [X] T031 [P] [US10] Add IT method `preReleaseChildOnDifferentOffence_shouldNotTrigger()`: POST with RC on offence1 and PREBC on offence2 (different offence ids); assert no DR-BAIL-015 error.

- [X] T032 [P] [US10] Add IT method `noConditionalBailParent_shouldNotTrigger()`: POST with unconditional bail result (e.g. UB short code) + PREBC child; assert no DR-BAIL-015 error.

- [X] T033 [US10] Add IT method `alongsideOtherRuleErrors_neitherSuppressed_shouldRaiseBothErrors()`: POST with RC + PREBC triggering DR-BAIL-015 AND also a payload that triggers DR-SENT-001 (e.g. both concurrent and consecutive flags on a custodial result); assert `errors.validationIssues` contains entries for both DR-BAIL-015 and DR-SENT-001 (AC5A — rules don't suppress each other).

**Checkpoint**: `gradle test --tests "*.PreReleaseBailConditionRuleIT"` — ALL methods pass. Run full `gradle test` — no regressions.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: API live tests, local HTTP test file, README update, static analysis, full build verification.

- [X] T034 Create `src/apiTest/java/uk/gov/hmcts/cp/http/PreReleaseBailConditionApiHttpLiveTest.java` implementing live HTTP tests against the docker-compose stack (pattern: `ApplicationResultOffenceApiHttpLiveTest.java`): include test methods covering all 11 backend-testable ACs — AC1 (adult + RC + PREBC → Error A), AC2 (youth + REMCBY + PRBCO → Error B), AC2A (CCIC + PREBC → Error D), AC2B (CCSC + PRBCT → Error E), AC3 (two adults both trigger Error A → summary names both with "and"), AC4 (adult + youth both trigger → single Error C summary, Error A/B inline), AC4A (RC+PREBC and CCIC+PRBCO in same hearing → two summary entries), AC5A (RC+PREBC alongside DR-SENT-001 concurrent/consecutive IMP → both errors present), AC7 (RC+PREBC → Error A then RIB+PREBC → isValid:true), AC8 (IMP + PREBC → isValid:true), AC9 (no DOB + BAILREVC + PRBCSE1 → Error C). Use `@Test`, `RestTemplate`, `assertThat`, no mock frameworks.

- [X] T035 [P] Update `README.md`: add row `| DR-BAIL-015 | Pre-release bail conditions on a conditional bail result |` to the validation rules table (pattern: existing rows at lines 31–40).

- [X] T036 [P] Create `specs/016-pre-release-bail-condition-error/CRA-363.http`: HTTP request file covering all ACs with representative JSON payloads targeting `http://localhost:4550/api/validation/validate`. Include labelled sections: AC1 (adult + RC + PREBC), AC2 (youth + REMCBY + PRBCO), AC2A (CCIC + PRBCO), AC2B (CCSC + PRBCS1), AC4 (mixed adult+youth → Error C summary), AC5A (alongside DR-SENT-001), AC8-negative-1 (RIB + PREBC → no error), AC8-negative-2 (RC only → no error), AC8-negative-3 (PREBC on different offence → no error), AC9 (no DOB → Error C).

- [X] T037 Run `gradle checkstyleMain pmdMain` and fix any violations in the new Java files (`PreReleaseBailConditionPreprocessor.java`, `PreReleaseBailContext.java`, `PreprocessingDefinition.java`). Common issues: wildcard imports (forbidden), `PMD.AvoidInstantiatingObjectsInLoops` on loop bodies that call `new LinkedHashMap<>()` — suppress with `@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")` if justified.

- [X] T038 Run `gradle build` (full build: compile + Checkstyle + PMD + all unit and integration tests) and confirm all tests pass and no warnings/errors remain.

- [X] T039 Run `gradle jacocoTestReport` and verify DR-BAIL-015 code paths are covered; spot-check `PreReleaseBailConditionPreprocessor` and `PreReleaseBailContext` in the HTML report.

- [ ] T040 [P] Deliver FR-018 (Welsh translations): obtain approved Welsh language versions of Error A–E from the content team and replace the English-only `messageTemplate` and `errorMessageTemplate` values in `src/main/resources/rules/DR-BAIL-015.yaml`. No Java changes are required. Gate the PR merge on this delivery.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (YAML)**: No dependencies — start immediately (Constitution Principle I requires this first)
- **Phase 2 (Foundational)**: T002 (PreprocessingDefinition) can start alongside T001; T003–T006 must follow T002; T007 (SQL) can run in parallel with T003–T006
- **Phase 3 (US1+US2)**: Requires Phase 1 + Phase 2 complete
- **Phase 4 (US3+US4)**: Requires Phase 1 + Phase 2 complete; can run in parallel with Phase 3
- **Phase 5 (US5+US6)**: Requires Phase 1 + Phase 2 complete; can run after Phase 3 (shares IT class)
- **Phase 6 (US7+US8)**: Requires Phase 1 + Phase 2 complete; logically after Phase 3+5 (builds on multi-defendant and multi-error scenarios)
- **Phase 7 (US9+US10)**: Requires Phase 1 + Phase 2 complete; negative cases are independent
- **Phase 8 (Polish)**: Requires all prior phases complete

### User Story Dependencies

All user story phases (3–7) are independently executable after the foundational phase (2), because:
- They all share the same IT class but add new `@Test` methods
- The preprocessor and YAML are fully implemented in Phase 2
- IT methods do not depend on each other

### Within Each Phase

1. Test methods → written first, confirmed failing
2. Production code (Phase 2 only) → written to make tests pass
3. Phases 3–7: YAML + preprocessor already exist; IT methods should pass immediately if the YAML conditions are correct
4. If an IT method fails unexpectedly, debug the YAML CEL expression or preprocessor logic before proceeding

### Parallel Opportunities

- T001 (YAML) and T002 (PreprocessingDefinition) are independent
- T003 (ContextTest) and T007 (SQL) are independent of each other
- T004 (Context) depends on T003; T005 (PreprocessorTest) depends on T002 + T004
- T006 (Preprocessor) depends on T005
- Within Phase 8: T034, T035, T036 can all run in parallel

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Run together (independent):
T001: Create DR-BAIL-015.yaml
T002: Extend PreprocessingDefinition.java with 4 new fields
T007: Create V1.017__pre_release_bail_condition_error.sql

# Then sequentially:
T003 → T004 → T005 → T006
```

---

## Implementation Strategy

### MVP (User Stories 1 & 2 — Phase 1+2+3)

1. Complete Phase 1: Create `DR-BAIL-015.yaml`
2. Complete Phase 2: `PreprocessingDefinition` fields → `PreReleaseBailContext` (TDD) → `PreReleaseBailConditionPreprocessor` (TDD) → SQL migration
3. Complete Phase 3: IT tests for adult (Error A) and youth (Error B)
4. **STOP and VALIDATE**: `gradle test` — all green. The rule blocks sharing for the most common cases.

### Incremental Delivery

1. MVP (Phase 1+2+3) → adult and youth remand bail errors ✓
2. Phase 4: CCIC + CCSC errors → Error D and E covered ✓
3. Phase 5: Mixed + no-DOB → Error C covered ✓
4. Phase 6: Multi-defendant names + multiple parent types ✓
5. Phase 7: Negative cases and correction path ✓
6. Phase 8: API tests, README, CRA-363.http, full build ✓

---

## Notes

- **[P]** tasks = can run in parallel (different files or independent test methods in the same class)
- All test methods in phases 3–7 are added to the same `PreReleaseBailConditionRuleIT.java` class created in T008
- TDD discipline: each test method added in phases 3–7 should already pass (the YAML and preprocessor are fully implemented in Phase 2); if a method fails, the bug is in the YAML CEL expression or preprocessor, not missing production code
- `@SuppressWarnings` on PMD rules must be justified by a brief inline comment
- Do not add `@SuppressWarnings("PMD.OnlyOneReturn")` unless the early-return pattern genuinely improves readability (follow existing preprocessor conventions)
- Welsh translations (spec FR-018) are tracked separately; YAML `messageTemplate` and `errorMessageTemplate` use English only for now
- The `after_tasks` hook (`speckit.git.commit`) is optional; commit manually when ready using Conventional Commits format
