---

description: "Tasks for DD-41656 — Extended Test Disqualification Warning"
---

# Tasks: Extended Test Disqualification Warning (DD-41656)

**Input**: Design documents from `/specs/001-extended-test-disq-warning/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: TDD is mandatory per Constitution Principle VIII (NON-NEGOTIABLE). Every implementation task is preceded by a failing test task.

**Organization**: Tasks are grouped by user story so each story is independently implementable, testable, and deployable. Phase 2 contains the Constitution-Principle-III prerequisite refactor that all user stories depend on.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3) — Setup / Foundational / Polish phases carry no story label
- File paths are absolute-relative to the repository root

## Path Conventions

Single-module Spring Boot service:

- Production code: `src/main/java/uk/gov/hmcts/cp/...`
- YAML rules: `src/main/resources/rules/`
- Unit tests: `src/test/java/uk/gov/hmcts/cp/...`
- Integration tests: `src/test/java/uk/gov/hmcts/cp/services/rules/cel/integration/...` (extend `IntegrationTestBase`)
- Live API tests: `src/apiTest/java/uk/gov/hmcts/cp/...` (run via `gradle api`)

---

## Phase 1: Setup

**Purpose**: Confirm baseline build is green before refactoring; no scaffolding required (existing project).

- [ ] T001 Verify baseline build is clean on `DD-41656-results-validation-warning`: run `gradle build` and confirm zero failures, zero Checkstyle warnings, zero PMD violations.
- [ ] T002 Verify baseline DR-SENT-002 tests pass: `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CelValidationRuleTest" --tests "uk.gov.hmcts.cp.services.rules.cel.CustodialPreprocessorTest"`.

**Checkpoint**: Baseline is green. Any later red state is attributable to a task in this list.

---

## Phase 2: Foundational — Preprocessor Registry Refactor

**Purpose**: Constitution Principle III requires removing the hard-wired `CustodialPreprocessor` from `CelValidationRule` and `ValidationRuleAutoConfiguration` before a second preprocessor type ships. This phase performs that refactor with **no behaviour change** — DR-SENT-002 must produce byte-identical output before and after.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete and `gradle test` is green.

### Foundational tests (TDD — write failing first)

- [ ] T003 [P] Failing test: create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessorRegistryTest.java` covering: (a) registry resolves `custodial-concurrent-consecutive` qualifier to `CustodialPreprocessor`, (b) registry throws `IllegalStateException` with the unknown qualifier in the message when asked for a missing type. Test must fail on compilation initially (registry class does not exist).
- [ ] T004 Failing test: modify `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRuleTest.java` so the System Under Test is constructed with a `PreprocessorRegistry` (test double or real with single mock preprocessor) instead of a `CustodialPreprocessor`. All existing assertions for DR-SENT-002 must still hold. Test must fail on compilation initially (constructor signature has not changed).

### Foundational implementation

- [ ] T005 [P] Create sealed interface `src/main/java/uk/gov/hmcts/cp/services/rules/cel/RuleEvaluationContext.java` declaring `Map<String, Long> toCelContext()`, `List<String> getOffenceIdSet(String setName)`, `String defendantName()`, `List<String> allOffenceIds()`. Use `permits DefendantContext, DisqualificationContext`. (`DisqualificationContext` does not exist yet — compile will fail until T018.)
- [ ] T006 Modify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DefendantContext.java` to add `implements RuleEvaluationContext`. No body changes — record accessors already match the interface.
- [ ] T007 [P] Create interface `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ValidationPreprocessor.java` with: `String type()`, `Map<String, ? extends RuleEvaluationContext> preprocess(DraftValidationRequest request, PreprocessingDefinition config)`.
- [ ] T008 Modify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CustodialPreprocessor.java`: `implements ValidationPreprocessor`; add `@Override public String type() { return "custodial-concurrent-consecutive"; }`; widen the existing `preprocess` return type from `Map<String, DefendantContext>` to `Map<String, ? extends RuleEvaluationContext>`. No algorithm change.
- [ ] T009 [P] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessorRegistry.java` as a `@Component` accepting `List<ValidationPreprocessor>` in its constructor and exposing `ValidationPreprocessor require(String type)` that throws `IllegalStateException` with the missing qualifier in the message. Forbid duplicate qualifiers at construction time (throw `IllegalStateException`).
- [ ] T010 Modify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRule.java`: replace the `CustodialPreprocessor preprocessor` field with `PreprocessorRegistry registry`; in `evaluate`, look up the preprocessor via `registry.require(ruleDefinition.getPreprocessing().getType())`; iterate `Map<String, ? extends RuleEvaluationContext>`; replace `context.defendantName()` calls (already on the interface) and `context.getOffenceIdSet(...)` calls — both unchanged at the call site. The constructor signature changes; update all callers.
- [ ] T011 Modify `src/main/java/uk/gov/hmcts/cp/config/ValidationRuleAutoConfiguration.java`: replace the `CustodialPreprocessor` parameter with `PreprocessorRegistry`; pass it to `new CelValidationRule(...)`. Remove the `CustodialPreprocessor` import.
- [ ] T012 Run `gradle test` and confirm: `T003` green, `T004` green, `CustodialPreprocessorTest` green (no behaviour change), all existing DR-SENT-002 integration tests green. If any DR-SENT-002 regression appears, fix in this phase before proceeding.
- [ ] T013 Run `gradle build` to confirm Checkstyle Google (`maxWarnings=0`) and PMD (`ignoreFailures=false`) are green after the refactor.

**Checkpoint**: Registry-based dispatch is in place. DR-SENT-002 produces identical output. Adding a new preprocessor `@Component` with a new qualifier is now sufficient to ship a new rule (no further `CelValidationRule` changes).

---

## Phase 3: User Story 1 — Warn when relevant offence has final result but no extended test disqualification (Priority: P1) 🎯 MVP

**Goal**: A hearing containing a relevant Road Traffic Act 1988 offence (one of `RT88046`, `RT88526`, `RT88026`, `RT88530`, `RT88531`) that has at least one non-excluded result line and no `DDOTE` / `DDOTEL` recorded against it produces exactly one `WARNING` `ValidationIssue` with the exact AC1A message text and the offence id linked via `affectedOffences[0].offenceId`.

**Independent Test**: `POST /api/validation/validate` with a payload containing one defendant, one offence with `offenceCode: "RT88026"`, and one result line with `shortCode: "COEW"` against that offence. The response must contain exactly one issue with `ruleId: "DR-DISQ-001"`, `severity: "WARNING"`, the literal message text, and `affectedOffences[0].offenceId` equal to that offence id.

### Tests for User Story 1 (TDD — write failing first) ⚠️

- [ ] T014 [P] [US1] Failing unit test: create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/DisqualificationExtendedTestPreprocessorTest.java` with `@Nested` class `RelevanceGate` covering: (a) `RT88026 + COEW + no DDOTE` → `qualifyingCount == 1` and `qualifyingOffenceIds == [offenceId]`; (b) all five relevant Home Office codes individually → `relevantCount == 1`. Test must fail on compilation (preprocessor class does not exist).
- [ ] T015 [P] [US1] Failing unit test: in the same file, `@Nested` class `NonRelevantOffences` — offence with `offenceCode: "TH68001"` → `relevantCount == 0` and `qualifyingCount == 0`.
- [ ] T016 [P] [US1] Failing unit test: in the same file, `@Nested` class `OffenceWithoutResults` — relevant offence id with no result lines in the request → `qualifyingCount == 0` (the "no final result yet" edge case).
- [ ] T017 [US1] Failing unit test: in the same file, `@Nested` class `CaseInsensitivity` — offence with `offenceCode: "rt88026"` and result with `shortCode: "coew"` → `qualifyingCount == 1`.
- [ ] T018 [P] [US1] Failing integration test: create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/integration/DisqualificationExtendedTestRuleIT.java` extending `IntegrationTestBase`, with `@Nested` class `WarnsOnQualifyingOffence` performing the Independent Test (POST `/api/validation/validate` with `RT88026 + COEW`), asserting `ruleId`, `severity`, exact message text, and `affectedOffences[0].offenceId`. Will fail because the rule YAML does not exist yet.

### Implementation for User Story 1

- [ ] T019 [US1] Create record `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DisqualificationContext.java` per data-model.md, implementing `RuleEvaluationContext` with `defendantName()` returning `null`, `toCelContext()` exposing the four `Long` variables, and `getOffenceIdSet` switch-expression over `qualifyingOffenceIds` and `allOffenceIds`. (Once this exists the `permits` clause from T005 compiles cleanly.)
- [ ] T020 [US1] Modify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` to add three nullable list fields: `relevantOffenceCodes`, `excludedFinalShortCodes`, `extendedTestShortCodes`. Existing fields are unchanged. Verify the SnakeYAML deserialiser accepts the new keys (existing rule continues to deserialise without the new keys present).
- [ ] T021 [US1] Create `@Component` `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DisqualificationExtendedTestPreprocessor.java` per data-model.md: `type() = "disqualification-extended-test"`; build `Map<String, List<ResultLineDto>>` grouped by offence id; for each offence emit one `DisqualificationContext` with the four counts. All short-code and offence-code comparisons MUST use `toUpperCase(Locale.ROOT)` once at the top, matching `CustodialPreprocessor`'s style. Use `@Slf4j` for any debug logging (Constitution VII — SLF4J only).
- [ ] T022 [US1] Create `src/main/resources/rules/DR-DISQ-001.yaml` per `contracts/rule-DR-DISQ-001.md`: id `DR-DISQ-001`, priority 2000, `preprocessing.type: disqualification-extended-test`, the three list fields populated with the canonical short-code casings from the contract, single condition with expression `qualifyingCount > 0`, severity `WARNING`, exact AC1A `messageTemplate`, `affectedOffenceSet: qualifyingOffenceIds`.
- [ ] T023 [US1] Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.DisqualificationExtendedTestPreprocessorTest" --tests "uk.gov.hmcts.cp.services.rules.cel.integration.DisqualificationExtendedTestRuleIT"` and confirm all US1 unit + integration tests are green.

**Checkpoint**: User Story 1 (P1) is fully functional. The MVP can be deployed: the warning fires for relevant offences with non-excluded final results and no `DDOTE`/`DDOTEL`. (Note: at this point, suppression on excluded codes (US2) and on `DDOTE`/`DDOTEL` (US3) is already implemented because the preprocessor in T021 carries all four gates — US2 and US3 are about *test coverage* of those gates, not net-new production code. This is intentional: shipping the gates separately would mean shipping a knowingly noisy MVP.)

---

## Phase 4: User Story 2 — Suppress the warning when the final result is an excluded outcome (Priority: P2)

**Goal**: For each of the nine excluded final-result short codes (`wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`), and for mixed-case variants of each, the rule produces zero issues against the affected offence.

**Independent Test**: `POST /api/validation/validate` with a payload identical to US1's Independent Test except the result line's short code is replaced with `wdrn`. The response contains zero `DR-DISQ-001` issues for that offence.

### Tests for User Story 2 (TDD — write failing first) ⚠️

- [ ] T024 [P] [US2] Failing unit test: in `DisqualificationExtendedTestPreprocessorTest.java`, add `@Nested` class `ExcludedFinalSuppression` with a `@ParameterizedTest` over the nine excluded codes — for each, a payload `RT88026 + <excludedCode>` → `qualifyingCount == 0` and `excludedFinalCount == 1`.
- [ ] T025 [US2] Failing unit test: in the same `@Nested` class, add `mixedCaseExcludedSuppresses` covering `WDRN`, `Wdrn`, `WdRn`, `WDRNOff`, `IREMFILE` → `qualifyingCount == 0`.
- [ ] T026 [P] [US2] Failing integration test: in `DisqualificationExtendedTestRuleIT.java`, add `@Nested` class `SuppressedByExcludedFinal` with at least three scenarios: `wdrn` (lowercase), `WDRNOFF` (uppercase), `Dism` (mixed). Each must produce zero `DR-DISQ-001` issues.
- [ ] T027 [US2] Failing integration test: in the same file, add `excludedAndDdoteBoth` — a hearing with two relevant offences, one with `wdrn`, one with `COEW + DDOTE`. Both should suppress; assert zero `DR-DISQ-001` issues.

### Implementation for User Story 2

- [ ] T028 [US2] Run all US2 tests (`T024`–`T027`). They are expected to pass against the preprocessor implementation from `T021`. If any fail, fix the preprocessor — most likely cause is a missing `toUpperCase(Locale.ROOT)` somewhere in the excluded-code comparison.
- [ ] T029 [US2] Run `gradle test` to confirm the full unit + integration test suite is green.

**Checkpoint**: User Story 2 is verifiably suppressed. The rule correctly treats withdrawn / dismissed / discharged / discontinued / count-on-file / indictment-on-file outcomes as non-warnings.

---

## Phase 5: User Story 3 — Suppress the warning when DDOTE or DDOTEL is already recorded (Priority: P2)

**Goal**: When the user has added a `DDOTE` or `DDOTEL` result line against the relevant offence (in any letter case), the rule produces zero issues against that offence. A second relevant offence in the same hearing without `DDOTE`/`DDOTEL` continues to warn.

**Independent Test**: `POST /api/validation/validate` with a payload identical to US1's Independent Test plus a second result line with `shortCode: "DDOTE"` against the same offence id. The response contains zero `DR-DISQ-001` issues for that offence.

### Tests for User Story 3 (TDD — write failing first) ⚠️

- [ ] T030 [P] [US3] Failing unit test: in `DisqualificationExtendedTestPreprocessorTest.java`, add `@Nested` class `ExtendedTestSuppression` covering: (a) `RT88026 + COEW + DDOTE` → `qualifyingCount == 0` and `disqExtTestCount == 1`; (b) `RT88026 + COEW + DDOTEL` → `qualifyingCount == 0`; (c) DDOTE recorded against a *different* offence in the same hearing → does NOT suppress the warning on the first offence (regression for the spec's "different offence" edge case).
- [ ] T031 [US3] Failing unit test: in the same `@Nested` class, add `mixedCaseDdoteSuppresses` covering `ddote`, `DdOtE`, `ddotel`, `DDoTeL` → `qualifyingCount == 0`.
- [ ] T032 [P] [US3] Failing integration test: in `DisqualificationExtendedTestRuleIT.java`, add `@Nested` class `SuppressedByDdote` with two scenarios: (a) single offence with `COEW + DDOTE` → zero issues; (b) two offences, one with `DDOTE` and one without → exactly one `DR-DISQ-001` issue, linked to the offence missing `DDOTE`.

### Implementation for User Story 3

- [ ] T033 [US3] Run all US3 tests (`T030`–`T032`). They are expected to pass against the preprocessor from `T021`. If any fail, fix the preprocessor.
- [ ] T034 [US3] Run `gradle test` to confirm the full unit + integration test suite is green.

**Checkpoint**: All three user stories are independently verified. The rule fires on qualifying offences and is correctly suppressed by both excluded final results and existing extended-test disqualifications.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Cross-rule regression coverage, static analysis, performance, docs, manual smoke test.

- [ ] T034a [P] Framework-level (not per-rule) tightening of `ValidationRuleOverrideIntegrationTest.java`. The existing IT covers only the happy path — seeded `DR-SENT-002` row with `enabled=true, severity=ERROR` produces an ERROR. Extend it with three additional `@Test` methods exercising the **mechanism**, against the existing `DR-SENT-002` rule (NOT against DR-DISQ-001):

    1. `validate_with_disabled_rule_should_emit_no_issues_for_that_rule` — set `enabled=false` for `DR-SENT-002`, POST a payload that would otherwise produce an ERROR, assert zero `DR-SENT-002` issues, then restore `enabled=true` (or rely on `@DirtiesContext`).
    2. `validate_with_db_severity_lower_than_yaml_should_cap_downward` — set `severity='WARNING'` for `DR-SENT-002`, POST the same payload, assert the issue is still emitted but at `WARNING`, not `ERROR`.
    3. `validate_with_db_severity_higher_than_yaml_should_be_no_op` — set `severity='ERROR'` against a YAML-level WARNING condition, assert severity stays at `WARNING` (Constitution Principle VI — never promote).

    Once these three exist, severity-ceiling and runtime-toggle behaviour is proven once at the framework level. New rules (DR-DISQ-001 and any future rule) inherit this coverage without per-rule duplication. This task addresses the analyze-report finding C1 (and the user's policy decision: framework-once, not rule-by-rule).
- [ ] T035 [P] Cross-rule regression test: create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/integration/CrossRuleRegressionIT.java` (or add a `@Nested` class to `DisqualificationExtendedTestRuleIT.java`) covering the scenario from `research.md` R10 — a hearing that triggers both `DR-SENT-002` ERROR and `DR-DISQ-001` WARNING. Assert both issues are present, with correct `ruleId`, `severity`, and `affectedOffences`. Proves the rules evaluate independently per Constitution Principle III and FR-011.
- [ ] T036 Update `README.md` "Current Rules" table to add a row: `DR-DISQ-001 | Extended test disqualification check`. (Markdown-only edit — exempt from the build loop per Constitution Principle IV.)
- [ ] T037 [P] Run `gradle checkstyleMain pmdMain` and confirm zero warnings / zero violations across all modified files.
- [ ] T038 [P] Run `gradle jacocoTestReport` and inspect coverage for `DisqualificationExtendedTestPreprocessor.java` and the new context record — target ≥85% line coverage on production code added in this feature (project default).
- [ ] T039 Run `gradle build` for the canonical green-build gate (Checkstyle Google `maxWarnings=0` + PMD `ignoreFailures=false` + unit + integration tests). Required before any PR.
- [ ] T040 Run `gradle api` to execute live API tests against the docker-compose stack — confirms the rule is discovered and reachable end-to-end.
- [ ] T041 Manually walk through `quickstart.md` against a locally running service (`gradle bootRun`): hit each of the four curl scenarios (warns / excluded suppresses / DDOTE suppresses / two qualifying offences) and the DB-override scenario; confirm responses match the expected outputs.
- [ ] T042 [P] Run `gradle gatlingRun-uk.gov.hmcts.cp.simulation.CapacitySimulation -Dgatling.baseUrl=http://localhost:4550` and compare the latency report to a recent baseline. Per `SC-005`, no regression.
- [ ] T043 Run the `spec-validator` reviewer agent against `src/main/resources/rules/DR-DISQ-001.yaml` (per Workflow's Spec → Code Review → QA → Spec-Validate loop). Expected: COMPLIANT.
- [ ] T044 Run the `code-reviewer` reviewer agent against the diff. Expected: PASS.
- [ ] T045 Run the `qa` reviewer agent against the diff. Expected: PASS (verifies failing-test-before-prod-code commit ordering per Constitution VIII).
- [ ] T046 Open the PR. Description MUST cite Constitution Principles I, III, VI, VIII (the principles this change touches) per Workflow / Governance.

**Checkpoint**: Feature is complete, reviewed, and ready to merge.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001–T002 — no internal dependencies; can run immediately.
- **Foundational (Phase 2)**: requires Setup green. Internal order: T003+T004 (failing tests, parallel) → T005+T007+T009 (interfaces and registry, parallel) → T006+T008 (modify existing classes, sequential because they touch files referenced by T010) → T010 → T011 → T012 → T013. **BLOCKS all user stories.**
- **User Story 1 (Phase 3 / P1)**: requires Foundational green. Internal order: T014–T018 (failing tests) → T019+T020+T021 (production) → T022 (YAML) → T023 (test green).
- **User Story 2 (Phase 4 / P2)**: requires US1 green. T024–T027 (failing tests) → T028–T029 (verify pass).
- **User Story 3 (Phase 5 / P2)**: requires US1 green. Independent of US2. T030–T032 (failing tests) → T033–T034 (verify pass).
- **Polish (Phase 6)**: requires US1 + US2 + US3 green.

### User Story Dependencies

- **US1 (P1)**: depends on Phase 2 only. Can ship as MVP.
- **US2 (P2)**: depends on US1. (Tests added to US1's test files; the gates suppression logic ships in US1 already, but until the US2 test coverage exists, the suppression is unverified.)
- **US3 (P2)**: depends on US1. Independent of US2.

### Within Each User Story

- Tests MUST be written and seen to FAIL before the corresponding production change is committed (Constitution VIII; the `qa` reviewer agent gates on this).
- Models / records before services / preprocessors.
- YAML rule file is the last production artefact added in US1 — it is the fully-loaded rule that the integration test then exercises end-to-end.

### Parallel Opportunities

- **Phase 2**: T003, T005, T007, T009 are [P] — different new files, no inter-dependencies.
- **Phase 3**: T014, T015, T016, T018 are [P] — T014–T016 are in the same file but no, wait, T014/T015/T016 add new `@Nested` classes to the same file (`DisqualificationExtendedTestPreprocessorTest.java`), so they are NOT [P] across each other. T018 is in a different file, so it IS [P] vs T014–T017. The marks above reflect this. Within T019/T020/T021 production work, T019 (new file `DisqualificationContext.java`) is [P] vs T020 (modify `PreprocessingDefinition.java`), but T021 reads the `PreprocessingDefinition` shape so it cannot run before T020 — sequential.
- **Phase 4 / Phase 5**: US2 and US3 can be developed in parallel by different developers (different `@Nested` classes within the same test files; merge resolved by the test runner).
- **Phase 6**: T035 ([P], new file), T037 ([P], read-only checks), T038 ([P], read-only), T042 ([P], external) are independent.

---

## Parallel Example: Phase 2 (Foundational)

```bash
# After T001 + T002 are green, launch the failing-tests + new-files in parallel:
Task: "Failing test: PreprocessorRegistryTest in src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessorRegistryTest.java"   # T003
Task: "Create RuleEvaluationContext sealed interface in src/main/java/uk/gov/hmcts/cp/services/rules/cel/RuleEvaluationContext.java"  # T005
Task: "Create ValidationPreprocessor interface in src/main/java/uk/gov/hmcts/cp/services/rules/cel/ValidationPreprocessor.java"      # T007
Task: "Create PreprocessorRegistry @Component in src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessorRegistry.java"        # T009
```

After those land, T006 + T008 + T010 + T011 are sequential because they touch files that other tasks reference.

## Parallel Example: User Story 1 (P1)

```bash
# After Phase 2 is green, launch the failing tests in parallel:
Task: "DisqualificationExtendedTestPreprocessorTest @Nested RelevanceGate"     # T014 (same file)
Task: "DisqualificationExtendedTestRuleIT @Nested WarnsOnQualifyingOffence"    # T018 (different file — [P] with T014)

# Then production in parallel where files are independent:
Task: "DisqualificationContext record in src/main/java/uk/gov/hmcts/cp/services/rules/cel/DisqualificationContext.java"  # T019 [P]
Task: "PreprocessingDefinition: add three optional list fields"                                                          # T020 (sequential before T021)
```

---

## Implementation Strategy

### MVP First (Phase 2 + User Story 1)

1. Phase 1 — Setup baseline green.
2. Phase 2 — Foundational refactor (no behaviour change). DR-SENT-002 must remain byte-identical.
3. Phase 3 — User Story 1.
4. **STOP and VALIDATE**: hit the four `quickstart.md` curl scenarios; the warning case must fire, the excluded-code and DDOTE cases must already suppress (preprocessor in T021 has all four gates). MVP is shippable here even before US2/US3 test coverage is added.

### Incremental Delivery

The recommended PR breakdown is two PRs:

- **PR 1**: Phase 1 + Phase 2 (registry refactor only). Title: `refactor(rules): dispatch preprocessors via PreprocessorRegistry`. Description cites Constitution III. No behaviour change; review focuses on the no-regression contract.
- **PR 2**: Phase 3 + Phase 4 + Phase 5 + Phase 6 (the new rule and its full test coverage). Title: `feat(rules): add DR-DISQ-001 extended test disqualification warning`. Description cites Constitution I (YAML-first), VI (severity ceiling), VIII (TDD).

### Parallel Team Strategy

With two developers:

1. Both pair on Phase 2 (small, high-risk). Land PR 1.
2. Once PR 1 is merged: Developer A picks up US1 (T014–T023). Developer B starts US2 + US3 test scaffolding (T024–T032) on top of US1's branch via stacked PRs.
3. Polish (Phase 6) runs as a final pass once all three user stories are green.

---

## Notes

- [P] = different files, no dependencies on incomplete tasks.
- TDD is mandatory: every implementation task must be preceded by a failing test that fails for the *correct* reason (assertion failure, not compilation error). The `qa` reviewer agent gates on this.
- US2 and US3 phases consist primarily of test coverage; the production code that satisfies them ships in US1's preprocessor (T021). This is intentional — shipping the gates incrementally would mean shipping a knowingly noisy MVP.
- Commit boundaries: at minimum, one commit per phase. Within Phase 2, commit T003+T005+T007+T009 together (failing tests + scaffolding compiles), then the rest as a second commit; this keeps the no-behaviour-change story intact.
- Per project memory `feedback_run_checkstyle.md`: run `gradle checkstyleMain` after every Java change before committing.
- Per Constitution VII: no `System.out.println`, no `e.printStackTrace()`, in either production or test code.
