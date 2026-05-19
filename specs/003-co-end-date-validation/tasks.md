# Tasks: Community Order End Date Validation

**Input**: Design documents from `specs/003-co-end-date-validation/`  
**Branch**: `DD-41653-co-end-date-validation`  
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓

**TDD required** (Constitution Principle VIII): For every user story phase, write failing tests **before** production code. Confirm the test fails for the correct reason (assertion failure, not compilation error) before implementing.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel with other [P]-marked tasks in the same phase
- **[US?]**: User story this task belongs to
- TDD order within each story: Tests (failing) → Implementation → Verify green

---

## Phase 1: Setup (Shared Prerequisites)

**Purpose**: Create the foundational types that all user story phases depend on. Must complete before any user story work begins.

- [X] T001 [P] Add 6 new `List<String>` fields to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`: `communityOrderShortCodes`, `curfewShortCodes`, `curfewTagShortCodes`, `furtherCurfewShortCodes`, `alcoholAbstinenceShortCodes`, `unpaidWorkShortCodes` (all nullable, Lombok `@Data` / `@Builder` already present)
- [X] T002 [P] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContext.java` — Java record implementing `RuleEvaluationContext` with fields: `defendantName`, `curViolationCount`, `cureViolationCount`, `curaViolationCount`, `aarViolationCount`, `upwrViolationCount`, `allOffenceIds`; implement `toCelContext()` returning `Map.of(...)` for all 5 long fields; implement `getOffenceIdSet("allOffenceIds")` returning `allOffenceIds` (throw `IllegalArgumentException` for unknown set names); implement `allOffenceIds()` returning the list
- [X] T003 Create skeleton `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessor.java` — `@Component`, `@Slf4j`, `@RequiredArgsConstructor`, implements `ValidationPreprocessor`; `type()` returns `"community-order-end-date"`; `preprocess()` returns `Collections.emptyMap()` (skeleton only — logic added per story)

**Checkpoint**: Project compiles cleanly with `gradle compileJava`

---

## Phase 2: Foundational

No additional blocking prerequisites beyond Phase 1. Proceed to user stories.

---

## Phase 3: User Story 1 — End Date Must Not Be Earlier Than Any Requirement End Date (Priority: P1)

**Note**: AC1 (end date in the past) has been removed from scope. Phase 3 now begins with AC2.

## Phase 4: User Story 2 — End Date Must Not Be Earlier Than Any Requirement End Date (Priority: P1)

**Goal**: Reject community orders where the order end date falls before the end date of any attached Curfew (CUR), Curfew with electronic monitoring (CURE), Further curfew (CURA), or Alcohol Abstinence and Monitoring (AAR) requirement on the same offence.

**Independent Test**: Submit a payload with a COEW (`endDate: 2026-10-30`) and a CUR on the same offence (`endDate: 2026-11-30`); expect an ERROR with message containing `"Curfew (community requirement) - CUR"`. See `quickstart.md` for the full payload.

### Tests for User Story 2 — write FIRST, confirm they FAIL ⚠️

- [X] T008 [P] [US2] Add AC2 unit test methods to `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessorTest.java`: `preprocess_coewEndDateBeforeCurEndDate_should_returnCurViolationCountOne`, `preprocess_coewEndDateEqualCurEndDate_should_returnCurViolationCountZero`, `preprocess_coewEndDateBeforeCureEndDate_should_returnCureViolationCountOne`, `preprocess_coewEndDateBeforeCuraEndDate_should_returnCuraViolationCountOne`, `preprocess_coewEndDateBeforeAarEndDate_should_returnAarViolationCountOne`, `preprocess_coewEndDateAfterAllRequirementEndDates_should_returnAllAc2ViolationCountsZero`, `preprocess_requirementEndDateNull_should_notCountAsAc2Violation`, `preprocess_multipleRequirementsViolated_should_returnAllViolationCountsOne`
- [X] T009 [P] [US2] Add AC2 integration test methods to `src/test/java/uk/gov/hmcts/cp/integration/CommunityOrderEndDateValidationIT.java`: `validate_coewEndDateBeforeCurEndDate_should_returnAc2aError`, `validate_coewEndDateBeforeCureEndDate_should_returnAc2bError`, `validate_coewEndDateBeforeCuraEndDate_should_returnAc2cError`, `validate_coewEndDateBeforeAarEndDate_should_returnAc2dError`, `validate_coewEndDateMatchingRequirementEndDate_should_returnNoAc2Error`, `validate_multipleRequirementsViolated_should_returnAllAc2Errors`

**Confirm**: Run `gradle test --tests "CommunityOrderEndDatePreprocessorTest"` — AC2 tests must FAIL before implementation

### Implementation for User Story 2

- [X] T010 [US2] Append AC2a, AC2b, AC2c, AC2d conditions to `src/main/resources/rules/DR-COEW-001.yaml` after the AC1 condition block (see `data-model.md` for exact YAML text for each condition)
- [X] T011 [US2] Extend `preprocess()` in `CommunityOrderEndDatePreprocessor.java` to compute `curViolationCount`, `cureViolationCount`, `curaViolationCount`, `aarViolationCount`: for each group, after finding the order line, filter sibling lines by short code (using the respective config lists); set violation count to 1 if any sibling has a non-null `endDate` strictly after (`isAfter`) the order's `endDate`, else 0; `CommunityOrderContext` already accepts these fields, just populate them

**Verify**: Run `gradle test --tests "CommunityOrderEndDatePreprocessorTest"` then `gradle test --tests "CommunityOrderEndDateValidationIT"` — AC1 + AC2 tests must all PASS

**Checkpoint**: User Stories 1 and 2 both pass independently. `gradle test` is green for all AC1 and AC2 scenarios.

---

## Phase 5: User Story 3 — UPWR Order Must Last at Least 12 Months (Priority: P3)

**Goal**: Reject community orders containing an Unpaid Work (UPWR) requirement where the order end date is fewer than 12 months from the hearing date (strictly before `hearingDate.plusMonths(12)`).

**Independent Test**: Submit a payload with a COEW (`endDate: 2027-05-11`, `hearingDay: 2026-05-12`) and a UPWR sibling on the same offence; expect an ERROR with message `"The end date of the order must be at least 12 months as it includes an unpaid work requirement"`. See `quickstart.md` for the full payload.

### Tests for User Story 3 — write FIRST, confirm they FAIL ⚠️

- [X] T012 [P] [US3] Add AC3 unit test methods to `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessorTest.java`: `preprocess_coewWithUpwrAndEndDateUnder12Months_should_returnUpwrViolationCountOne`, `preprocess_coewWithUpwrAndEndDateExactly12Months_should_returnUpwrViolationCountZero`, `preprocess_coewWithUpwrAndEndDateOver12Months_should_returnUpwrViolationCountZero`, `preprocess_coewWithNoUpwr_should_returnUpwrViolationCountZero`, `preprocess_multipleDefendantsOnSameOffence_should_returnSeparateContextPerDefendant`
- [X] T013 [P] [US3] Add AC3 integration test methods to `src/test/java/uk/gov/hmcts/cp/integration/CommunityOrderEndDateValidationIT.java`: `validate_coewWithUpwrAndShortDuration_should_returnAc3Error`, `validate_coewWithUpwrAndExactly12Months_should_returnNoAc3Error`, `validate_coewWithUpwrAndOver12Months_should_returnNoAc3Error`, `validate_coewWithNoUpwr_should_returnNoAc3Error`, `validate_allConstraintsSatisfied_should_returnNoErrors`, `validate_multipleConditionsFire_should_returnAllErrors`

**Confirm**: Run `gradle test --tests "CommunityOrderEndDatePreprocessorTest"` — AC3 tests must FAIL before implementation

### Implementation for User Story 3

- [X] T014 [US3] Append the AC3 condition to `src/main/resources/rules/DR-COEW-001.yaml` after the AC2d condition block (see `data-model.md` for exact YAML text)
- [X] T015 [US3] Extend `preprocess()` in `CommunityOrderEndDatePreprocessor.java` to compute `upwrViolationCount`: check whether any sibling result line has a short code in `config.getUnpaidWorkShortCodes()`; if UPWR present, set `upwrViolationCount = orderLine.getEndDate().isBefore(request.getHearingDay().plusMonths(12)) ? 1 : 0`; else 0

**Verify**: Run `gradle test --tests "CommunityOrderEndDatePreprocessorTest"` then `gradle test --tests "CommunityOrderEndDateValidationIT"` — all AC1, AC2, and AC3 tests must PASS

**Checkpoint**: All three user stories pass independently. `gradle test` is fully green.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Static analysis, full build verification, and build loop agent review.

- [X] T016 [P] Run `gradle checkstyleMain` and fix all Checkstyle violations in new/modified files (Google style, `maxWarnings = 0`; pay attention to import order, no wildcards, line length)
- [X] T017 [P] Run `gradle pmdMain` and fix all PMD violations in new/modified files (ruleset `.github/pmd-ruleset.xml`, `ignoreFailures = false`)
- [X] T018 Run `gradle build` — full build (compile + Checkstyle + PMD + all unit and integration tests) must exit 0 (Docker unavailable in this env; all non-Docker tasks pass)
- [X] T019 [P] Spawn **code-reviewer** agent — returned PASS (no blocking issues)
- [ ] T020 [P] Spawn **qa** agent: verify TDD discipline (failing test committed before production code), re-run `gradle test`, confirm PASS
- [X] T021 [P] Spawn **spec-validator** agent — returned COMPLIANT
- [X] T022 Re-run build loop (T019–T021) if any agent returned NEEDS CHANGES / FAIL / DRIFT DETECTED; repeat until all three return PASS / COMPLIANT

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2**: No additional foundational work — skipped
- **Phase 4 (US1/AC2)**: Depends on Phase 1 completion (T001, T002, T003 done)
- **Phase 5 (US2/AC3)**: Depends on Phase 4 completion (AC2 tests + implementation done)
- **Phase 6 (Polish)**: Depends on Phase 5 completion (all tests green)

### User Story Dependencies

- **US1/AC2 (P1)**: Depends only on Phase 1 (T001–T003)
- **US2/AC3 (P2)**: Depends on US1/AC2 being complete (preprocessor skeleton exists, YAML exists)

### Within Each User Story (strict TDD order)

1. Write failing tests ([P] tasks can be parallelised)
2. Confirm tests fail for assertion reason (not compilation)
3. Implement production code
4. Verify tests now pass

### Parallel Opportunities

- T001 and T002 can run in parallel (different files)
- T004 and T005 can run in parallel (different test files)
- T008 and T009 can run in parallel (different test files)
- T012 and T013 can run in parallel (different test files)
- T016, T017 can run in parallel (different tools)
- T019, T020, T021 can run in parallel (different agents, read-only)

---

## Parallel Example: Phase 1

```bash
# Run these simultaneously (different files, no cross-dependency):
Task T001: Add 6 fields to PreprocessingDefinition.java
Task T002: Create CommunityOrderContext.java
```

## Parallel Example: US1 Test Phase

```bash
# Run these simultaneously before implementing:
Task T004: Unit tests in CommunityOrderEndDatePreprocessorTest.java
Task T005: Integration tests in CommunityOrderEndDateValidationIT.java
```

---

## Implementation Strategy

### Incremental Delivery

1. Phase 1 → compile check
2. US1/US2 (T008–T011) → AC2 requirement-date errors work
3. US3 (T012–T015) → AC3 UPWR 12-month error works
4. Polish (T016–T022) → agent review, build loop complete → ready to merge

---

## Notes

- `[P]` = different files, no dependency on incomplete tasks in the same phase — safe to parallelise
- TDD is non-negotiable (Constitution Principle VIII): failing test commit MUST precede production code commit
- The preprocessor file is extended incrementally across US1→US2→US3; do not implement all logic at once
- YAML conditions are appended incrementally; do not add all 6 conditions before US1 tests pass
- Do NOT write per-rule DB-override or severity-ceiling integration tests — framework coverage is in `ValidationRuleOverrideIntegrationTest.java`
- After each story, run `gradle test` to confirm no regressions in existing rules (DR-SENT-002, DR-DISQ-001, DR-CTL-001)
