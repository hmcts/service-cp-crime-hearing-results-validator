# Tasks: Restraining Order – Multiple Protected Persons Warning

**Input**: Design documents from `specs/010-restrao-multiple-protected-persons/`
**Prerequisites**: plan.md ✅, spec.md ✅, data-model.md ✅, research.md ✅

**Tests**: TDD is mandatory (Constitution Principle VIII). Unit and integration tests are included and written before production code.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared-state dependencies)
- **[Story]**: Which user story this task belongs to (US1–US6)
- Exact file paths are included in all task descriptions

---

## Phase 1: Setup (Baseline Verification)

**Purpose**: Confirm the existing test suite is green before introducing any change.

- [x] T001 Verify baseline by running `gradle test` and confirming all existing tests pass with zero failures

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Create the YAML rule contract first (Constitution Principle I: YAML-first) and the `RestrainingOrderContext` record. Both are prerequisites for the preprocessor and all unit/integration tests. No user story work can begin until this phase is complete.

**⚠️ CRITICAL**: Phases 3–8 are blocked until T002 and T003 are both complete.

- [x] T002 Create `src/main/resources/rules/DR-RESTRAO-010.yaml` with: `rule.id = "DR-RESTRAO-010"`, `priority = 10000`, `enabled = true`, `preprocessing.type = "restrao-multiple-protected-persons"`, `preprocessing.filterShortCodes: [RESTRAO]`, condition `id = "AC1"` with `expression = "multiplePersonsCount > 0"`, `severity = WARNING`, `messageTemplate = "A restraining order result can only include one protected person's details. Add a separate restraining order result for each protected person."`, `affectedOffenceSet = "breachingOffenceIds"` — exact schema per data-model.md
- [x] T003 [P] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/RestrainingOrderContext.java` as a Java record implementing `RuleEvaluationContext` with fields `String offenceId`, `long multiplePersonsCount`, `List<String> breachingOffenceIds`, `List<String> allOffenceIds`; implement `toCelContext()` returning `Map.of("multiplePersonsCount", multiplePersonsCount)`, `getOffenceIdSet(String)` switching on `"breachingOffenceIds"` and `"allOffenceIds"` (throw `IllegalArgumentException` for unknown names), and `defendantName()` returning `null` — exact spec per data-model.md

**Checkpoint**: YAML rule auto-discovered at startup; context record compiles — user story phases can now begin.

---

## Phase 3: User Story 1 – Single RESTRAO with Separator Characters (Priority: P1) 🎯 MVP

**Goal**: Detect `&`, `,`, and `/` anywhere in the RESTRAO `"protectedPersonsName"` prompt value and raise one offence-level WARNING per breaching offence.

**Independent Test**: POST a `DraftValidationRequest` containing one RESTRAO result line with `promptRef = "protectedPersonsName"` and `promptValue = "John Smith & Jane Smith"` and assert exactly one `ValidationIssue` with `severity = WARNING` and `ruleId = "DR-RESTRAO-010"` is returned.

> **TDD: Write the failing tests in T004–T005 BEFORE implementing the preprocessor body in T006. Confirm tests fail at assertion level (not compilation error) before proceeding.**

- [x] T004 [US1] Create stub files to enable TDD compilation: (a) create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/RestrainingOrderMultiplePersonsPreprocessor.java` as a `@Component` implementing `ValidationPreprocessor` with `type()` returning `"restrao-multiple-protected-persons"` and a stub `preprocess()` returning `Collections.emptyMap()`; (b) create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/RestrainingOrderMultiplePersonsPreprocessorTest.java` as an `@ExtendWith(MockitoExtension.class)` class with one placeholder `@Test void placeholder() {}` — confirm `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.RestrainingOrderMultiplePersonsPreprocessorTest"` compiles and the placeholder passes
- [x] T005 [US1] Replace the placeholder in `RestrainingOrderMultiplePersonsPreprocessorTest.java` with failing unit tests covering: `ampersandInName_should_produceMultiplePersonsCount1`, `commaInName_should_produceMultiplePersonsCount1`, `slashInName_should_produceMultiplePersonsCount1`, `blankName_should_produceMultiplePersonsCount0`, `nullPrompts_should_produceMultiplePersonsCount0`, `noRestraoLines_should_returnEmptyMap` — run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.RestrainingOrderMultiplePersonsPreprocessorTest"` and confirm all six tests FAIL at assertion level
- [x] T006 [US1] Implement the full `preprocess()` body in `RestrainingOrderMultiplePersonsPreprocessor.java` per the data-model.md algorithm: `upperSet` via `PreprocessorHelper.upperSet(config.filterShortCodes())`, `resultsByOffence` via `PreprocessorHelper.groupResultsByOffence(request)`, per-offence loop building `RestrainingOrderContext`; add private `isMultiplePersons(ResultLineDto)` with `static final String PROMPT_PROTECTED_PERSON_NAME = "protectedPersonsName"` (confirmed from `cpp-apitests/hearing.save-draft-for-RESTRAO.json`), calling `findPromptValue(line, PROMPT_PROTECTED_PERSON_NAME)` and detecting `name.contains("&") || name.contains(",") || name.contains("/")` (PATTERN_AND regex added in T008); add private `findPromptValue()` as specified in data-model.md — run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.RestrainingOrderMultiplePersonsPreprocessorTest"` and confirm all US1 tests pass

**Checkpoint**: Separator-character detection is unit-tested and passing. US1 is independently functional.

---

## Phase 4: User Story 2 – Whole-Word "and" Detection (Priority: P1)

**Goal**: Trigger the warning when "and" is a standalone whole word (case-insensitive); suppress it when "and" is embedded within a longer word such as "Alexandra" or "Anderson".

**Independent Test**: Submit `"John Smith and Jane Smith"` → one WARNING; submit `"Alexandra Sanderson"` → zero warnings; submit `"John Smith AND Jane Smith"` → one WARNING.

> **TDD: Add US2 failing tests in T007 BEFORE extending the preprocessor in T008.**

- [x] T007 [US2] Add four failing unit tests to `RestrainingOrderMultiplePersonsPreprocessorTest.java`: `wholeWordAnd_lowercase_should_produceMultiplePersonsCount1`, `wholeWordAnd_uppercase_should_produceMultiplePersonsCount1`, `andAsSubstringInAlexandraSanderson_should_produceMultiplePersonsCount0`, `andAsSubstringInAmandaAnderson_should_produceMultiplePersonsCount0` — run tests and confirm all four FAIL at assertion level
- [x] T008 [US2] Add `static final Pattern PATTERN_AND = Pattern.compile("(?i)\\w+\\s+and\\s+\\w+")` to `RestrainingOrderMultiplePersonsPreprocessor` and extend `isMultiplePersons()` to include `|| PATTERN_AND.matcher(name).find()` — pattern requires "and" to be flanked by a word group on both sides; leading/trailing "and" (e.g. `"and Smith"`, `"John and"`) does NOT trigger — run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.RestrainingOrderMultiplePersonsPreprocessorTest"` and confirm all US1 + US2 tests pass

**Checkpoint**: Full detection logic (separator chars + "and" between two word groups) implemented and unit-tested. All Phase 3 and Phase 4 unit tests green.

---

## Phase 5: User Story 3 – Multiple RESTRAO Results Evaluated Independently (Priority: P2)

**Goal**: When a hearing contains RESTRAO results on multiple offences, each offence is evaluated independently — the warning appears only against the breaching offence, not against clean offences.

**Independent Test**: POST a request with two RESTRAO result lines on two separate offences — one with `"protectedPersonsName" = "John Smith & Jane Smith"` and one with `"protectedPersonsName" = "Jane Smith"` — and assert exactly one WARNING referencing the breaching offenceId only.

- [x] T009 [US3] Create `src/test/java/uk/gov/hmcts/cp/integration/RestrainingOrderMultiplePersonsIntegrationTest.java` extending `IntegrationTestBase`; add `@Test` method `twoRestraoOffences_oneBreaching_shouldWarnOnBreachingOffenceOnly` that posts a `DraftValidationRequest` with two offences each having one RESTRAO result line (first offence: `promptRef = "protectedPersonsName"`, `promptValue = "John Smith & Jane Smith"`; second offence: `promptRef = "protectedPersonsName"`, `promptValue = "Jane Smith"`) and asserts: exactly one `ValidationIssue` is returned, `severity = WARNING`, `ruleId = "DR-RESTRAO-010"`, and `affectedOffenceIds` contains the first offenceId only — build test using `ValidationRuleTestHelper.resultLineWithPrompt()` — run `gradle test --tests "uk.gov.hmcts.cp.integration.RestrainingOrderMultiplePersonsIntegrationTest"` and confirm pass

**Checkpoint**: Per-offence independence verified end-to-end via integration test.

---

## Phase 6: User Story 4 – Warning Is Advisory; Sharing Not Blocked (Priority: P2)

**Goal**: Confirm that the rule emits `severity = WARNING` (never `ERROR`) so the Share action remains available.

**Independent Test**: Trigger the rule and assert `severity == "WARNING"` in the response; confirm no issue with `ruleId = "DR-RESTRAO-010"` has `severity == "ERROR"`.

- [x] T010 [P] [US4] Add `@Test` method `singleRestraoWithTrigger_shouldProduceWarningNotError` to `RestrainingOrderMultiplePersonsIntegrationTest.java` — POST a request with one RESTRAO result line where `promptRef = "protectedPersonsName"` and `promptValue = "John Smith, Jane Smith"` and assert the returned issue has `severity = WARNING` and that no issue from `DR-RESTRAO-010` has `severity = ERROR` — run `gradle test --tests "uk.gov.hmcts.cp.integration.RestrainingOrderMultiplePersonsIntegrationTest"` and confirm pass

**Checkpoint**: Advisory-only semantics confirmed; sharing path unblocked.

---

## Phase 7: User Story 5 – Resolving the Warning by Splitting Results (Priority: P3)

**Goal**: After splitting a breaching RESTRAO result into two separate RESTRAO results (one name each), a re-submission produces no warning from DR-RESTRAO-010.

**Independent Test**: POST a request with two clean RESTRAO results on the same offence (`"John Smith"` and `"Jane Smith"` as separate result lines, each with their own `protectedPersonsName` prompt) and assert zero issues from DR-RESTRAO-010.

- [x] T011 [P] [US5] Add `@Test` method `twoCleanRestraoResultsOnSameOffence_shouldProduceNoWarning` to `RestrainingOrderMultiplePersonsIntegrationTest.java` — POST a request with two RESTRAO result lines on the same offence, each with a clean single name (`promptRef = "protectedPersonsName"`, `promptValue = "John Smith"` and `promptValue = "Jane Smith"` respectively), and assert zero `ValidationIssue` entries with `ruleId = "DR-RESTRAO-010"` — run tests and confirm pass

**Checkpoint**: Round-trip resolution verified; warning correctly absent after split.

---

## Phase 8: User Story 6 – Amend and Reshare After Initial Share (Priority: P3)

**Goal**: Validate that the check is stateless — it fires identically when the same breaching payload is submitted a second time, simulating an amend-and-reshare flow.

**Independent Test**: POST the same breaching payload twice in succession; assert both responses contain an identical WARNING with the same message text.

- [x] T012 [P] [US6] Add `@Test` method `reshareWithTrigger_shouldBehaveIdenticallyToFirstShare` to `RestrainingOrderMultiplePersonsIntegrationTest.java` — POST a breaching RESTRAO payload (`promptRef = "protectedPersonsName"`, `promptValue = "John Smith & Jane Smith"`) twice and assert both responses contain exactly one WARNING with `ruleId = "DR-RESTRAO-010"` and the same message text — run tests and confirm pass

**Checkpoint**: Stateless re-evaluation on reshare confirmed.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Static analysis gate and full build verification before merging.

- [x] T013 Run `gradle checkstyleMain` and fix all violations (zero-warnings policy, Google checks `config/checkstyle/google_checks.xml`)
- [x] T014 [P] Run `gradle pmdMain` and fix all violations (`ignoreFailures = false`, `.github/pmd-ruleset.xml`)
- [x] T015 Run `gradle build` (full: compile + Checkstyle + PMD + all unit + all integration tests) and confirm green with zero failures

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS all story phases**
- **Phase 3 (US1, P1)**: Depends on Phase 2 — implements the preprocessor core
- **Phase 4 (US2, P1)**: Depends on Phase 3 — extends the same preprocessor source file; must be sequential
- **Phases 5–6 (US3, US4, P2)**: Both depend on Phase 4; T009 and T010 can run in parallel (different test methods on the same IT class)
- **Phases 7–8 (US5, US6, P3)**: Both depend on Phase 6; T011 and T012 can run in parallel (different test methods on the same IT class)
- **Phase 9 (Polish)**: Depends on all story phases complete

### User Story Dependencies

- **US1 (P1)**: Requires Phase 2 — introduces the preprocessor
- **US2 (P1)**: Requires US1 complete — extends the same `RestrainingOrderMultiplePersonsPreprocessor.java`
- **US3 (P2)**: Requires US2 complete — adds integration test only
- **US4 (P2)**: Requires US2 complete — adds integration test only (can parallel with US3)
- **US5 (P3)**: Requires US4 complete — adds integration test only
- **US6 (P3)**: Requires US4 complete — adds integration test only (can parallel with US5)

### Within Each User Story

- Tests MUST be written and confirmed to FAIL before implementation (Constitution Principle VIII)
- Context record (T003) before preprocessor stub (T004)
- YAML (T002) before integration tests (T009+) — the rule is loaded at startup
- Unit tests before integration tests

### Parallel Opportunities

- T002 (YAML) and T003 (Context record) — different files, no dependency on each other
- T009 (US3 IT) and T010 (US4 IT) — different test methods appended to the same IT file
- T011 (US5 IT) and T012 (US6 IT) — different test methods appended to the same IT file
- T013 (Checkstyle) and T014 (PMD) — independent tools

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Both tasks can run simultaneously — different files, no shared dependency:
Task T002: Create src/main/resources/rules/DR-RESTRAO-010.yaml
Task T003: Create src/main/java/uk/gov/hmcts/cp/services/rules/cel/RestrainingOrderContext.java
```

## Parallel Example: Phases 5 + 6 (P2 Integration Tests)

```bash
# After Phase 4 is complete, both can start simultaneously:
Task T009: [US3] twoRestraoOffences_oneBreaching_shouldWarnOnBreachingOffenceOnly
Task T010: [US4] singleRestraoWithTrigger_shouldProduceWarningNotError
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 Only)

1. Complete Phase 1: Baseline verification
2. Complete Phase 2: YAML + Context record (CRITICAL — blocks everything)
3. Complete Phase 3: US1 separator-char detection (TDD)
4. Complete Phase 4: US2 whole-word "and" detection (TDD)
5. **STOP and VALIDATE**: Run `gradle test` — all unit tests pass; rule auto-loaded via integration context
6. Merge or demo P1 MVP; continue to P2/P3 stories when ready

### Incremental Delivery

1. Foundation (Phase 2) → Core detection (Phases 3–4) → Ship P1
2. Per-offence + advisory tests (Phases 5–6) → Ship P2
3. Round-trip + reshare tests (Phases 7–8) → Ship P3
4. Polish (Phase 9) before opening PR to main

### Parallel Team Strategy

After Phase 4 is complete, with two developers available:

- Developer A: T009 (US3) then T011 (US5)
- Developer B: T010 (US4) then T012 (US6)

---

## Notes

- `promptRef` resolved: `PROMPT_PROTECTED_PERSON_NAME = "protectedPersonsName"` — confirmed from `cpp-apitests/api-integration-test/src/test/resources/draftresults/hearing/hearing.save-draft-for-RESTRAO.json`; no open dependency
- Override / severity-ceiling integration tests are NOT included — the mechanism is proven once in `ValidationRuleOverrideIntegrationTest.java` per design_rules.md; do not add per-rule override ITs
- No new API test class — the framework-level PATCH write path is covered by `ValidationRulesApiHttpLiveTest`
- Test helper: use `ValidationRuleTestHelper.resultLineWithPrompt(id, "RESTRAO", offenceId, "protectedPersonsName", value)` to build result lines in tests
- [P] tasks = different files or independent test methods; safe to run in parallel
- Each user story phase is independently completable and testable via `gradle test`
- TDD discipline: test fails for the right reason → implement → test passes → refactor