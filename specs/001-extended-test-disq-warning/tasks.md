---

description: "Tasks for DD-41656 — Extended Test Disqualification Warning"
---

# Tasks: Extended Test Disqualification Warning (DD-41656)

**Input**: Design documents from `/specs/001-extended-test-disq-warning/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

## Revision — 2026-04-28

The original 001 implementation (Phases 1–6, T001–T046) is complete and shipped to the DD-41656 branch. The 2026-04-28 spec revision (gate on `category = 'F'` rather than the short-code-set inference) introduces a coordinated four-repo change. Those tasks live in **Phase 7** below.

The Phase 6 checkpoint *"Feature is complete, reviewed, and ready to merge"* is **superseded** — the feature is not ready to merge until Phase 7 lands. Specifically:

- Phase 7 supersedes the test fixtures from T014–T032 in two narrow ways: (a) all relevant-offence test payloads must add `category` to result lines, and (b) the unit tests asserting the "any non-excluded line counts as final" behaviour are either deleted or rewritten to reflect the new gate.
- Phase 7 adds a new headline **US4** (adjournment / no F line → no warning) per the spec's 2026-04-28 revision.
- Phase 7 spans four repositories. Branch creation in the three external repos is an explicit task in this list, executed during implementation.

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

- [X] T001 Verify baseline build is clean on `DD-41656-results-validation-warning`: run `gradle build` and confirm zero failures, zero Checkstyle warnings, zero PMD violations.
- [X] T002 Verify baseline DR-SENT-002 tests pass: `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CelValidationRuleTest" --tests "uk.gov.hmcts.cp.services.rules.cel.CustodialPreprocessorTest"`.

**Checkpoint**: Baseline is green. Any later red state is attributable to a task in this list.

---

## Phase 2: Foundational — Preprocessor Registry Refactor

**Purpose**: Constitution Principle III requires removing the hard-wired `CustodialPreprocessor` from `CelValidationRule` and `ValidationRuleAutoConfiguration` before a second preprocessor type ships. This phase performs that refactor with **no behaviour change** — DR-SENT-002 must produce byte-identical output before and after.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete and `gradle test` is green.

### Foundational tests (TDD — write failing first)

- [X] T003 [P] Failing test: create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessorRegistryTest.java` covering: (a) registry resolves `custodial-concurrent-consecutive` qualifier to `CustodialPreprocessor`, (b) registry throws `IllegalStateException` with the unknown qualifier in the message when asked for a missing type. Test must fail on compilation initially (registry class does not exist).
- [X] T004 Failing test: modify `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRuleTest.java` so the System Under Test is constructed with a `PreprocessorRegistry` (test double or real with single mock preprocessor) instead of a `CustodialPreprocessor`. All existing assertions for DR-SENT-002 must still hold. Test must fail on compilation initially (constructor signature has not changed).

### Foundational implementation

- [X] T005 [P] Create sealed interface `src/main/java/uk/gov/hmcts/cp/services/rules/cel/RuleEvaluationContext.java` declaring `Map<String, Long> toCelContext()`, `List<String> getOffenceIdSet(String setName)`, `String defendantName()`, `List<String> allOffenceIds()`. Use `permits DefendantContext, DisqualificationContext`. (`DisqualificationContext` does not exist yet — compile will fail until T018.)
- [X] T006 Modify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DefendantContext.java` to add `implements RuleEvaluationContext`. No body changes — record accessors already match the interface.
- [X] T007 [P] Create interface `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ValidationPreprocessor.java` with: `String type()`, `Map<String, ? extends RuleEvaluationContext> preprocess(DraftValidationRequest request, PreprocessingDefinition config)`.
- [X] T008 Modify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CustodialPreprocessor.java`: `implements ValidationPreprocessor`; add `@Override public String type() { return "custodial-concurrent-consecutive"; }`; widen the existing `preprocess` return type from `Map<String, DefendantContext>` to `Map<String, ? extends RuleEvaluationContext>`. No algorithm change.
- [X] T009 [P] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessorRegistry.java` as a `@Component` accepting `List<ValidationPreprocessor>` in its constructor and exposing `ValidationPreprocessor require(String type)` that throws `IllegalStateException` with the missing qualifier in the message. Forbid duplicate qualifiers at construction time (throw `IllegalStateException`).
- [X] T010 Modify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRule.java`: replace the `CustodialPreprocessor preprocessor` field with `PreprocessorRegistry registry`; in `evaluate`, look up the preprocessor via `registry.require(ruleDefinition.getPreprocessing().getType())`; iterate `Map<String, ? extends RuleEvaluationContext>`; replace `context.defendantName()` calls (already on the interface) and `context.getOffenceIdSet(...)` calls — both unchanged at the call site. The constructor signature changes; update all callers.
- [X] T011 Modify `src/main/java/uk/gov/hmcts/cp/config/ValidationRuleAutoConfiguration.java`: replace the `CustodialPreprocessor` parameter with `PreprocessorRegistry`; pass it to `new CelValidationRule(...)`. Remove the `CustodialPreprocessor` import.
- [X] T012 Run `gradle test` and confirm: `T003` green, `T004` green, `CustodialPreprocessorTest` green (no behaviour change), all existing DR-SENT-002 integration tests green. If any DR-SENT-002 regression appears, fix in this phase before proceeding.
- [X] T013 Run `gradle build` to confirm Checkstyle Google (`maxWarnings=0`) and PMD (`ignoreFailures=false`) are green after the refactor.

**Checkpoint**: Registry-based dispatch is in place. DR-SENT-002 produces identical output. Adding a new preprocessor `@Component` with a new qualifier is now sufficient to ship a new rule (no further `CelValidationRule` changes).

---

## Phase 3: User Story 1 — Warn when relevant offence has final result but no extended test disqualification (Priority: P1) 🎯 MVP

**Goal**: A hearing containing a relevant Road Traffic Act 1988 offence (one of `RT88046`, `RT88526`, `RT88026`, `RT88530`, `RT88531`) that has at least one non-excluded result line and no `DDOTE` / `DDOTEL` recorded against it produces exactly one `WARNING` `ValidationIssue` with the exact AC1A message text and the offence id linked via `affectedOffences[0].offenceId`.

**Independent Test**: `POST /api/validation/validate` with a payload containing one defendant, one offence with `offenceCode: "RT88026"`, and one result line with `shortCode: "COEW"` against that offence. The response must contain exactly one issue with `ruleId: "DR-DISQ-001"`, `severity: "WARNING"`, the literal message text, and `affectedOffences[0].offenceId` equal to that offence id.

### Tests for User Story 1 (TDD — write failing first) ⚠️

- [X] T014 [P] [US1] Failing unit test: create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/DisqualificationExtendedTestPreprocessorTest.java` with `@Nested` class `RelevanceGate` covering: (a) `RT88026 + COEW + no DDOTE` → `qualifyingCount == 1` and `qualifyingOffenceIds == [offenceId]`; (b) all five relevant Home Office codes individually → `relevantCount == 1`; (c) Per-offence anchoring with multiple defendants (covers analyze-report finding C3): two `DefendantDto` entries (d1 and d2) both linked to one `RT88026` offence + single `COEW` result line + no DDOTE → `qualifyingCount == 1` and `qualifyingOffenceIds.size() == 1`. Demonstrates the spec's "Per-offence evaluation" assumption — two defendants on the same offence yield exactly one warning, not one per defendant. Test must fail on compilation (preprocessor class does not exist).
- [X] T015 [P] [US1] Failing unit test: in the same file, `@Nested` class `NonRelevantOffences` — offence with `offenceCode: "TH68001"` → `relevantCount == 0` and `qualifyingCount == 0`.
- [X] T016 [P] [US1] Failing unit test: in the same file, `@Nested` class `OffenceWithoutResults` — relevant offence id with no result lines in the request → `qualifyingCount == 0` (the "no final result yet" edge case).
- [X] T017 [US1] Failing unit test: in the same file, `@Nested` class `CaseInsensitivity` — offence with `offenceCode: "rt88026"` and result with `shortCode: "coew"` → `qualifyingCount == 1`.
- [X] T018 [P] [US1] Failing integration test: create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/integration/DisqualificationExtendedTestRuleIT.java` extending `IntegrationTestBase`, with two `@Nested` classes: (a) `WarnsOnQualifyingOffence` performing the Independent Test (POST `/api/validation/validate` with `RT88026 + COEW`), asserting `ruleId`, `severity`, exact message text, and `affectedOffences[0].offenceId`; (b) `MultiDefendantSameOffence` (covers analyze-report finding C3): POST a payload with two defendants both linked to a single `RT88026` offence and one `COEW` result line, assert response contains exactly one `DR-DISQ-001` issue (not duplicated per defendant), with `affectedOffences[0].offenceId` matching the shared offence id. Will fail because the rule YAML does not exist yet.

### Implementation for User Story 1

- [X] T019 [US1] Create record `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DisqualificationContext.java` per data-model.md, implementing `RuleEvaluationContext` with `defendantName()` returning `null`, `toCelContext()` exposing the four `Long` variables, and `getOffenceIdSet` switch-expression over `qualifyingOffenceIds` and `allOffenceIds`. (Once this exists the `permits` clause from T005 compiles cleanly.)
- [X] T020 [US1] Modify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` to add three nullable list fields: `relevantOffenceCodes`, `excludedFinalShortCodes`, `extendedTestShortCodes`. Existing fields are unchanged. Verify the SnakeYAML deserialiser accepts the new keys (existing rule continues to deserialise without the new keys present).
- [X] T021 [US1] Create `@Component` `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DisqualificationExtendedTestPreprocessor.java` per data-model.md: `type() = "disqualification-extended-test"`; build `Map<String, List<ResultLineDto>>` grouped by offence id; for each offence emit one `DisqualificationContext` with the four counts. All short-code and offence-code comparisons MUST use `toUpperCase(Locale.ROOT)` once at the top, matching `CustodialPreprocessor`'s style. Use `@Slf4j` for any debug logging (Constitution VII — SLF4J only).
- [X] T022 [US1] Create `src/main/resources/rules/DR-DISQ-001.yaml` per `contracts/rule-DR-DISQ-001.md`: id `DR-DISQ-001`, priority 2000, `preprocessing.type: disqualification-extended-test`, the three list fields populated with the canonical short-code casings from the contract, single condition with expression `qualifyingCount > 0`, severity `WARNING`, exact AC1A `messageTemplate`, `affectedOffenceSet: qualifyingOffenceIds`.
- [X] T023 [US1] Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.DisqualificationExtendedTestPreprocessorTest" --tests "uk.gov.hmcts.cp.services.rules.cel.integration.DisqualificationExtendedTestRuleIT"` and confirm all US1 unit + integration tests are green.

**Checkpoint**: User Story 1 (P1) is fully functional. The MVP can be deployed: the warning fires for relevant offences with non-excluded final results and no `DDOTE`/`DDOTEL`. (Note: at this point, suppression on excluded codes (US2) and on `DDOTE`/`DDOTEL` (US3) is already implemented because the preprocessor in T021 carries all four gates — US2 and US3 are about *test coverage* of those gates, not net-new production code. This is intentional: shipping the gates separately would mean shipping a knowingly noisy MVP.)

---

## Phase 4: User Story 2 — Suppress the warning when the final result is an excluded outcome (Priority: P2)

**Goal**: For each of the nine excluded final-result short codes (`wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`), and for mixed-case variants of each, the rule produces zero issues against the affected offence.

**Independent Test**: `POST /api/validation/validate` with a payload identical to US1's Independent Test except the result line's short code is replaced with `wdrn`. The response contains zero `DR-DISQ-001` issues for that offence.

### Tests for User Story 2 (TDD — write failing first) ⚠️

- [X] T024 [P] [US2] Failing unit test: in `DisqualificationExtendedTestPreprocessorTest.java`, add `@Nested` class `ExcludedFinalSuppression` with a `@ParameterizedTest` over the nine excluded codes — for each, a payload `RT88026 + <excludedCode>` → `qualifyingCount == 0` and `excludedFinalCount == 1`. Also include a negative case `nonExcludedFinalCodeDoesNotSuppress` (covers analyze-report finding C2): payload `RT88026 + IMP` (imprisonment — a real platform code that is not on the excluded list and not DDOTE/DDOTEL) → `qualifyingCount == 1` and `excludedFinalCount == 0`. Demonstrates the suppression is bounded to the listed codes; the gate is exclusion-list-based, not inclusion-list-based, so the rule continues to fire on any future final-result code added to the platform that isn't explicitly excluded.
- [X] T025 [US2] Failing unit test: in the same `@Nested` class, add `mixedCaseExcludedSuppresses` covering `WDRN`, `Wdrn`, `WdRn`, `WDRNOff`, `IREMFILE` → `qualifyingCount == 0`.
- [X] T026 [P] [US2] Failing integration test: in `DisqualificationExtendedTestRuleIT.java`, add `@Nested` class `SuppressedByExcludedFinal` with at least three scenarios: `wdrn` (lowercase), `WDRNOFF` (uppercase), `Dism` (mixed). Each must produce zero `DR-DISQ-001` issues.
- [X] T027 [US2] Failing integration test: in the same file, add `excludedAndDdoteBoth` — a hearing with two relevant offences, one with `wdrn`, one with `COEW + DDOTE`. Both should suppress; assert zero `DR-DISQ-001` issues.

### Implementation for User Story 2

- [X] T028 [US2] Run all US2 tests (`T024`–`T027`). They are expected to pass against the preprocessor implementation from `T021`. If any fail, fix the preprocessor — most likely cause is a missing `toUpperCase(Locale.ROOT)` somewhere in the excluded-code comparison.
- [X] T029 [US2] Run `gradle test` to confirm the full unit + integration test suite is green.

**Checkpoint**: User Story 2 is verifiably suppressed. The rule correctly treats withdrawn / dismissed / discharged / discontinued / count-on-file / indictment-on-file outcomes as non-warnings.

---

## Phase 5: User Story 3 — Suppress the warning when DDOTE or DDOTEL is already recorded (Priority: P2)

**Goal**: When the user has added a `DDOTE` or `DDOTEL` result line against the relevant offence (in any letter case), the rule produces zero issues against that offence. A second relevant offence in the same hearing without `DDOTE`/`DDOTEL` continues to warn.

**Independent Test**: `POST /api/validation/validate` with a payload identical to US1's Independent Test plus a second result line with `shortCode: "DDOTE"` against the same offence id. The response contains zero `DR-DISQ-001` issues for that offence.

### Tests for User Story 3 (TDD — write failing first) ⚠️

- [X] T030 [P] [US3] Failing unit test: in `DisqualificationExtendedTestPreprocessorTest.java`, add `@Nested` class `ExtendedTestSuppression` covering: (a) `RT88026 + COEW + DDOTE` → `qualifyingCount == 0` and `disqExtTestCount == 1`; (b) `RT88026 + COEW + DDOTEL` → `qualifyingCount == 0`; (c) DDOTE recorded against a *different* offence in the same hearing → does NOT suppress the warning on the first offence (regression for the spec's "different offence" edge case).
- [X] T031 [US3] Failing unit test: in the same `@Nested` class, add `mixedCaseDdoteSuppresses` covering `ddote`, `DdOtE`, `ddotel`, `DDoTeL` → `qualifyingCount == 0`.
- [X] T032 [P] [US3] Failing integration test: in `DisqualificationExtendedTestRuleIT.java`, add `@Nested` class `SuppressedByDdote` with two scenarios: (a) single offence with `COEW + DDOTE` → zero issues; (b) two offences, one with `DDOTE` and one without → exactly one `DR-DISQ-001` issue, linked to the offence missing `DDOTE`.

### Implementation for User Story 3

- [X] T033 [US3] Run all US3 tests (`T030`–`T032`). They are expected to pass against the preprocessor from `T021`. If any fail, fix the preprocessor.
- [X] T034 [US3] Run `gradle test` to confirm the full unit + integration test suite is green.

**Checkpoint**: All three user stories are independently verified. The rule fires on qualifying offences and is correctly suppressed by both excluded final results and existing extended-test disqualifications.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Cross-rule regression coverage, static analysis, performance, docs, manual smoke test.

- [X] T034a [P] Framework-level (not per-rule) tightening of `ValidationRuleOverrideIntegrationTest.java`. The existing IT covers only the happy path — seeded `DR-SENT-002` row with `enabled=true, severity=ERROR` produces an ERROR. Extend it with three additional `@Test` methods exercising the **mechanism**, against the existing `DR-SENT-002` rule (NOT against DR-DISQ-001):

    1. `validate_with_disabled_rule_should_emit_no_issues_for_that_rule` — set `enabled=false` for `DR-SENT-002`, POST a payload that would otherwise produce an ERROR, assert zero `DR-SENT-002` issues, then restore `enabled=true` (or rely on `@DirtiesContext`).
    2. `validate_with_db_severity_lower_than_yaml_should_cap_downward` — set `severity='WARNING'` for `DR-SENT-002`, POST the same payload, assert the issue is still emitted but at `WARNING`, not `ERROR`.
    3. `validate_with_db_severity_higher_than_yaml_should_be_no_op` — set `severity='ERROR'` against a YAML-level WARNING condition, assert severity stays at `WARNING` (Constitution Principle VI — never promote).

    Once these three exist, severity-ceiling and runtime-toggle behaviour is proven once at the framework level. New rules (DR-DISQ-001 and any future rule) inherit this coverage without per-rule duplication. This task addresses the analyze-report finding C1 (and the user's policy decision: framework-once, not rule-by-rule).
- [X] T035 [P] Cross-rule regression test: create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/integration/CrossRuleRegressionIT.java` (or add a `@Nested` class to `DisqualificationExtendedTestRuleIT.java`) covering the scenario from `research.md` R10 — a hearing that triggers both `DR-SENT-002` ERROR and `DR-DISQ-001` WARNING. Assert both issues are present, with correct `ruleId`, `severity`, and `affectedOffences`. Proves the rules evaluate independently per Constitution Principle III and FR-011.
- [X] T036 Update `README.md` "Current Rules" table to add a row: `DR-DISQ-001 | Extended test disqualification check`. (Markdown-only edit — exempt from the build loop per Constitution Principle IV.)
- [X] T037 [P] Run `gradle checkstyleMain pmdMain` and confirm zero warnings / zero violations across all modified files.
- [X] T038 [P] Run `gradle jacocoTestReport` and inspect coverage for `DisqualificationExtendedTestPreprocessor.java` and the new context record — target ≥85% line coverage on production code added in this feature (project default).
- [X] T039 Run `gradle build` for the canonical green-build gate (Checkstyle Google `maxWarnings=0` + PMD `ignoreFailures=false` + unit + integration tests). Required before any PR.
- [X] T040 Run `gradle api` to execute live API tests against the docker-compose stack — confirms the rule is discovered and reachable end-to-end.
- [X] T041 Manually walk through `quickstart.md` against a locally running service (`gradle bootRun`): hit each of the four curl scenarios (warns / excluded suppresses / DDOTE suppresses / two qualifying offences) and the DB-override scenario; confirm responses match the expected outputs.
- [ ] T042 [P] Run `gradle gatlingRun-uk.gov.hmcts.cp.simulation.CapacitySimulation -Dgatling.baseUrl=http://localhost:4550` and compare the latency report to a recent baseline. Per `SC-005`, no regression.
- [X] T043 Run the `spec-validator` reviewer agent against `src/main/resources/rules/DR-DISQ-001.yaml` (per Workflow's Spec → Code Review → QA → Spec-Validate loop). Expected: COMPLIANT.
- [X] T044 Run the `code-reviewer` reviewer agent against the diff. Expected: PASS.
- [X] T045 Run the `qa` reviewer agent against the diff. Expected: PASS (verifies failing-test-before-prod-code commit ordering per Constitution VIII).
- [ ] T046 Open the PR. Description MUST cite Constitution Principles I, III, VI, VIII (the principles this change touches) per Workflow / Governance.

**Checkpoint**: ~~Feature is complete, reviewed, and ready to merge.~~ **SUPERSEDED 2026-04-28** — original 001 implementation merged-able as a refactor + initial rule shape, but production behaviour requires Phase 7's `category = 'F'` gate to land before share is fit for purpose. See Phase 7 below.

---

## Phase 7: Revision (2026-04-28) — `category = 'F'` Cross-Repo Gate

**Purpose**: Replace the rule's short-code-set inference of "is this a final result?" with a direct read of `ResultLineDto.category = 'F'`. Spans four repositories under DD-41656 (no separate Jira). Adds positive coverage of BA scenario 5 (adjournment `'A'` on a relevant offence → no warning) as the headline new behaviour (US4).

**⚠️ CRITICAL ordering**:

1. The api repo's lib publish (T7-API-*) **must complete first** — without it, the validator service and cpp-context-hearing have no `category` field on `ResultLineDto` to read.
2. cpp-ui-hearing's TypeScript change has no Java-lib dependency and can run in parallel with the api lib publish.
3. The validator service (this repo) and cpp-context-hearing both pull the new lib version once published, then continue independently.

### Sub-phase 7.0: Branch creation (run during implementation)

**Purpose**: Create the per-repo working branches before any code change. All branches are off the integration base specified in [plan.md "Cross-Repo Coordination"](./plan.md#cross-repo-coordination-2026-04-28).

- [ ] T100 [BR] Confirm this repo (`service-cp-crime-hearing-results-validator`) is on `DD-41656-results-validation-warning`. Run `git status` and `git rev-parse --abbrev-ref HEAD` from `/home/sachin/moj/service-cp-crime-hearing-results-validator`. No new branch needed (already open from original 001 work).
- [ ] T101 [BR] In `/home/sachin/moj/cpp-context-hearing`: `git fetch origin && git switch -c DD-41656-results-validation-warning origin/team/DD-41715-results-validator && git push -u origin DD-41656-results-validation-warning`. Branches off `team/DD-41715-results-validator` to inherit the in-flight `ShareResultsCommandHandler` HTTP-call wiring (project memory `project_hearing_validator_integration.md`).
- [ ] T102 [BR] In `/home/sachin/moj/cpp-ui-hearing`: `git fetch origin && git switch -c DD-41656-results-validation-warning origin/team/result-validation && git push -u origin DD-41656-results-validation-warning`. Branches off `team/result-validation` to inherit the pre-share validation wiring.
- [ ] T103 [BR] In `/home/sachin/moj/api-cp-crime-hearing-results-validator`: confirm working from `main` (no feature branch — the ACR publish pipeline only runs on `main`). If a short-lived PR is preferred for review, create a feature branch and prepare to fast-merge after green CI.

**Checkpoint**: All four repos are on the correct branch. Phase 7 work begins.

### Sub-phase 7.A: api-cp-crime-hearing-results-validator — OpenAPI extension + lib publish

**Purpose**: Add `category: enum [A, I, F]` to `ResultLineDto` in the OpenAPI spec, bump lib version, publish to ACR.

- [ ] T104 [API] In `/home/sachin/moj/api-cp-crime-hearing-results-validator/src/main/resources/openapi/openapi-spec.yml`, add `category` to `ResultLineDto.properties` per `contracts/rule-DR-DISQ-001.md` "Upstream contract delta": `type: string`, `enum: [A, I, F]`, optional, with the BA-readable description. Validate the file parses cleanly with the project's OpenAPI generator.
- [ ] T105 [API] Bump the lib version in the api repo's build file (`build.gradle` or equivalent — confirm by inspection) per the repo's existing semver convention. Commit message: `feat(contract): add category enum to ResultLineDto for DD-41656`.
- [ ] T106 [API] Merge / push to `main` and confirm the ACR publish pipeline runs cleanly. Wait for the new lib version to appear in ACR.
- [ ] T107 [API] [P] Capture the published lib version (e.g. `libs.api.hearing.results.validator:1.X.Y`) in this spec's plan.md "Cross-Repo Coordination" table footnote, so downstream tasks know the exact version to pin.

**Checkpoint**: New lib version is published. Downstream repos can now bump their dependency.

### Sub-phase 7.B: cpp-context-hearing — parallel-mirror DTO + mapper + tests

**Purpose**: Add `category` to the locally hand-written `ResultLineDto` (parallel mirror), populate it from `SharedResultsCommandResultLineV2` in `ValidationRequestMapper`, and prove it via mapper unit tests. **Depends on T106.**

- [ ] T108 [CCH] [P] Failing unit test: in cpp-context-hearing, run `find hearing-command -name "ValidationRequestMapperTest*" -type f` to locate the existing test (likely co-located with `ValidationRequestMapper.java`). If it exists, add a new test method; if not, create the file alongside the production class and document the path in the PR description. Add a test asserting that `toValidationRequest` copies `category` from `SharedResultsCommandResultLineV2` onto each output `ResultLineDto`, for each of the three category values (`A`, `I`, `F`) and the null case. Test must fail until T109 + T110 are done.
- [ ] T109 [CCH] In cpp-context-hearing, edit `hearing-command/hearing-command-handler/src/main/java/uk/gov/moj/cpp/hearing/command/handler/service/validation/ResultLineDto.java` (the hand-written parallel mirror — **not** generated). Add `private String category` and a `withCategory(String)` builder method matching the existing fluent-builder style. Add a brief class-level Javadoc reminding future authors this DTO is a manual mirror of the OpenAPI contract at `api-cp-crime-hearing-results-validator/src/main/resources/openapi/openapi-spec.yml`.
- [ ] T110 [CCH] In cpp-context-hearing, edit `ValidationRequestMapper.toValidationRequest` to invoke `.withCategory(line.getCategory())` for each result line. Compile error if `SharedResultsCommandResultLineV2.getCategory()` is unavailable — confirm it exists at line 48 per the spec; if it doesn't, surface as a blocker before continuing.
- [ ] T111 [CCH] Bump cpp-context-hearing's `libs.api.hearing.results.validator` dependency version to the one published in T106 (only if cpp-context-hearing consumes the lib for its outbound call — confirm by searching for the dependency in the repo's build files). If the local hand-written DTO is the *only* consumer, no lib bump is needed in this repo.
- [ ] T112 [CCH] Run the cpp-context-hearing module build (`mvn -pl hearing-command/hearing-command-handler verify` or `gradle test` per the repo's tooling) and confirm T108 is green.

**Checkpoint**: cpp-context-hearing now ships `category` over the wire to the validator at share time.

### Sub-phase 7.C: cpp-ui-hearing — `buildResultLines` extension

**Purpose**: Map `category` from the resolved draft line onto the validation request body in the pre-share path. **Independent of T106 (pure TypeScript, no Java lib dep).**

- [ ] T113 [UI] [P] Failing unit test: in cpp-ui-hearing, locate or create a unit test for `buildResultLines` (`src/app/results/core/helpers/results-validation.ts`). Add a case asserting that `buildResultLines` includes `category` on each output line, sourced from `ResolvedDraftResultLine.category` (line 71 of `src/app/results/results.interfaces.ts`), for each of `A`, `I`, `F`, and the undefined case. Test must fail until T114.
- [ ] T114 [UI] In cpp-ui-hearing, extend `buildResultLines` in `src/app/results/core/helpers/results-validation.ts` to map `line.category` onto the request body. Preserve the existing line-shape; the new field should be additive.
- [ ] T115 [UI] Run the cpp-ui-hearing unit-test suite (`npm test` or per the repo's tooling), TypeScript compile, and lint. Confirm T113 is green and no other tests regress.

**Checkpoint**: cpp-ui-hearing now ships `category` over the wire to the validator at pre-share validation time.

### Sub-phase 7.D: service-cp-crime-hearing-results-validator (this repo) — preprocessor tightening + US4 + regression updates

**Purpose**: Bump the lib version, update `DisqualificationContext` and `DisqualificationExtendedTestPreprocessor` to gate on `category = 'F'`, add the US4 BA-scenario-5 IT, and update existing US1/US2/US3 test fixtures to include `category` on F lines. **Depends on T106.**

#### Lib version bump

- [ ] T116 [VAL] In this repo's `build.gradle`, bump `libs.api.hearing.results.validator` to the version from T107. Run `gradle dependencies | grep validator` to confirm the new version resolves and `gradle compileJava` to confirm the new `category` getter is on `ResultLineDto`.

#### Tests for User Story 4 (TDD — write failing first) ⚠️

**US4 (P1)**: Suppress the warning when no final result is recorded yet (BA scenarios doc, scenario 5). The headline behaviour change of the 2026-04-28 revision.

**Independent Test**: `POST /api/validation/validate` with a payload containing one defendant, one offence with `offenceCode: "RT88026"`, and one result line with `category: "A"` (e.g. `shortCode: "ADJN"`) against that offence. The response must contain zero `DR-DISQ-001` issues.

- [ ] T117 [P] [US4] Failing unit test: in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/DisqualificationExtendedTestPreprocessorTest.java`, add `@Nested class NoFinalLine` covering: (a) relevant offence + only `category='A'` line (`ADJN`) → `qualifyingCount == 0` and `finalCategoryCount == 0`; (b) relevant offence + only `category='I'` line → `qualifyingCount == 0` and `finalCategoryCount == 0`; (c) relevant offence + multiple lines, all `category` ∈ `{A, I}` → `qualifyingCount == 0`; (d) relevant offence + line with `category=null` → `qualifyingCount == 0` (FR-015 fail-safe); (e) relevant offence + line with `category="X"` (unrecognised) → `qualifyingCount == 0`. Test must fail until T120 + T121 land.
- [ ] T118 [P] [US4] Failing IT: in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/integration/DisqualificationExtendedTestRuleIT.java`, add `@Nested class AdjournmentDoesNotWarn` performing the BA-scenario-5 Independent Test (POST with `RT88026 + ADJN/category=A`). Assert response contains zero `DR-DISQ-001` issues. This is the canonical regression test for SC-002.
- [ ] T119 [P] [US4] Failing unit test: in `DisqualificationExtendedTestPreprocessorTest.java`, add `@Nested class CategoryF_GateBoundary` covering: (a) offence with two F lines, both `excluded shortCode` → `qualifyingCount == 0`, `finalCategoryCount == 2`, `excludedFinalCount == 2`; (b) offence with two F lines, one excluded one not → `qualifyingCount == 1` (at-least-one-non-excluded-F-line wins); (c) offence with `category='F'` line + `DDOTE` line on `category='I'` → `qualifyingCount == 0` (DDOTE on I-line still suppresses).

#### Update existing US1/US2/US3 test fixtures (regression)

- [ ] T120 [VAL] In `DisqualificationExtendedTestPreprocessorTest.java`, walk through every test method that constructs a `ResultLineDto` and add `category` to the fixture: `'F'` for any line representing a final outcome (`COEW`, `IMP`, `wdrn`, etc.); `'I'` for `DDOTE`/`DDOTEL` lines (per the contract — disqualifications are intermediary, not final-status-determining); `'A'` for any newly-added adjournment line. Existing assertions on `qualifyingCount`, `excludedFinalCount`, `disqExtTestCount` must still hold given the new gate semantics.
- [ ] T121 [VAL] [P] In `DisqualificationExtendedTestRuleIT.java`, do the same fixture update — every payload's `resultLines[].category` is set to `F`/`I`/`A` per the contract.
- [ ] T122 [VAL] [P] Verify no existing tests still assert the retired "any non-excluded line counts as final" behaviour. Run from the repo root: `grep -rn "excludedFinalCount\|qualifyingCount" src/test/`. For each match: confirm the assertion is still semantically correct under the new gate after T120/T121's `category='F'` fixture updates. Delete or rewrite anything that is not (likely candidates: tests whose docstring or method name references "novel short code" or "any non-excluded"). If no such tests are found, mark this task complete with a one-line note: `T122: searched — no superseded tests found; T120/T121 fixture updates were sufficient.`

#### Production code changes

- [ ] T123 [VAL] [US4] Modify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DisqualificationContext.java` per data-model.md: add `long finalCategoryCount` field (between `relevantCount` and `excludedFinalCount`); update `toCelContext()` to include `"finalCategoryCount"`; record constructor signature changes accordingly. Update any test factories that build `DisqualificationContext` instances directly (likely none — tests build via the preprocessor).
- [ ] T124 [VAL] [US4] Modify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DisqualificationExtendedTestPreprocessor.java` per data-model.md "Algorithm (revised 2026-04-28)": replace the `hasNonExcludedFinal` derivation with `finalLines = lines where category equals 'F' (case-insensitive, fail-safe on missing/malformed)`; compute `finalCategoryCount`; tighten `excludedFinalCount` to count only F lines with excluded shortCode; rewrite `qualifying = relevant && finalNonExcluded && !disqExtTest`. Add an INFO log line via `@Slf4j` once per request when an unrecognised non-A/I/F category value is observed (mention the value, the offence id, but NOT any PII or freeform label content). Constitution VII — SLF4J only.
- [ ] T125 [VAL] Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.DisqualificationExtendedTestPreprocessorTest" --tests "uk.gov.hmcts.cp.services.rules.cel.integration.DisqualificationExtendedTestRuleIT"`. Confirm all unit + integration tests are green, including T117–T119 (new) and T120–T122 (regression).
- [ ] T126 [VAL] Run `gradle build` for the canonical green-build gate (Checkstyle Google `maxWarnings=0` + PMD + full test suite). Required before opening a PR.
- [ ] T127 [VAL] Run `gradle api` to execute the live API tests against the docker-compose stack. New `category` field must round-trip cleanly through the deserialised request shape end-to-end.

#### Quickstart smoke + reviewer agents

- [ ] T128 [VAL] [P] Manually walk through the updated `quickstart.md` against `gradle bootRun` locally: hit the warns / excluded suppresses / DDOTE suppresses / **adjournment suppresses** / two qualifying offences / DB-override scenarios. Confirm responses match the expected outputs per quickstart.md.
- [ ] T129 [VAL] [P] Run the `spec-validator` reviewer agent against `src/main/resources/rules/DR-DISQ-001.yaml` (no schema change — still expected COMPLIANT) plus any updated YAML.
- [ ] T130 [VAL] [P] Run the `code-reviewer` reviewer agent against the diff. Expected: PASS. Watch for: `category` parsing being robust (case-insensitive, fail-safe on missing); INFO log not leaking PII; no orphaned references to the old inference logic.
- [ ] T131 [VAL] [P] Run the `qa` reviewer agent against the diff. Expected: PASS — verifies failing-test-before-prod-code commit ordering on T117/T118/T119 → T123/T124 (Constitution VIII).

### Sub-phase 7.E: PRs and integration

**Purpose**: Open four PRs (one per repo) with cross-references in each description so reviewers can traverse the change set.

- [ ] T132 [PR] Open PR in `api-cp-crime-hearing-results-validator` (or merge to `main` directly per repo policy). PR title: `feat(contract): add category enum to ResultLineDto for DD-41656`. Description cites the four-repo coordination plan and links to this `specs/001-extended-test-disq-warning/` directory.
- [ ] T133 [PR] Open PR in `cpp-context-hearing` (`DD-41656-results-validation-warning` → `team/DD-41715-results-validator`). PR title: `feat(validation): thread category through to validator request for DD-41656`. Description references T132 and the parallel-mirror DTO.
- [ ] T134 [PR] Open PR in `cpp-ui-hearing` (`DD-41656-results-validation-warning` → `team/result-validation`). PR title: `feat(results): include category on pre-share validation request for DD-41656`. Description references T132.
- [ ] T135 [PR] Open PR in this repo (`DD-41656-results-validation-warning` → `main`). PR title: `feat(rules): tighten DR-DISQ-001 to gate on category='F' for DD-41656`. Description cites Constitution Principles I, III, VI, VIII (per Workflow / Governance) and references the original 001 work plus T132/T133/T134.
- [ ] T136 [PR] [P] After all four PRs are open and green, run `/speckit-analyze` against this spec dir for cross-artifact consistency before merging. Expected: clean (or surfaced findings approved one at a time per project memory `feedback_speckit_analyze_review.md`).

**Checkpoint**: All four PRs are open, green, cross-referenced, and reviewer-approved. Merge order: T132 (api lib publish on `main`) → T133 + T134 + T135 (in any order, after each pulls the new lib version where applicable). Coordinate with the team to merge in a window where the four-way drift risk is minimised.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001–T002 — no internal dependencies; can run immediately.
- **Foundational (Phase 2)**: requires Setup green. Internal order: T003+T004 (failing tests, parallel) → T005+T007+T009 (interfaces and registry, parallel) → T006+T008 (modify existing classes, sequential because they touch files referenced by T010) → T010 → T011 → T012 → T013. **BLOCKS all user stories.**
- **User Story 1 (Phase 3 / P1)**: requires Foundational green. Internal order: T014–T018 (failing tests) → T019+T020+T021 (production) → T022 (YAML) → T023 (test green).
- **User Story 2 (Phase 4 / P2)**: requires US1 green. T024–T027 (failing tests) → T028–T029 (verify pass).
- **User Story 3 (Phase 5 / P2)**: requires US1 green. Independent of US2. T030–T032 (failing tests) → T033–T034 (verify pass).
- **Polish (Phase 6)**: requires US1 + US2 + US3 green.
- **Revision Phase 7 (2026-04-28)**: requires Phase 6 green (or at least US1 green; the registry refactor and rule scaffolding from Phase 2/3 are prerequisites). Internal order: 7.0 (branch creation) → 7.A (api lib publish, T104–T107) → **fan-out**: 7.B (cpp-context-hearing) and 7.D (this repo) both depend on T106; 7.C (cpp-ui-hearing) is independent of 7.A and can run in parallel; → 7.E (PRs and integration).

### User Story Dependencies

- **US1 (P1)**: depends on Phase 2 only. Can ship as MVP.
- **US2 (P2)**: depends on US1. (Tests added to US1's test files; the gates suppression logic ships in US1 already, but until the US2 test coverage exists, the suppression is unverified.)
- **US3 (P2)**: depends on US1. Independent of US2.
- **US4 (P1, NEW 2026-04-28)**: depends on US1 + Phase 7.A (lib publish). The new headline behaviour change. Tasks live in Phase 7.D (T117–T119 failing tests, T123/T124 production). The US1/US2/US3 fixture-update tasks (T120–T122) are technically regression upkeep, not US4 per se, but ship in the same Phase 7.D PR.

### Within Each User Story

- Tests MUST be written and seen to FAIL before the corresponding production change is committed (Constitution VIII; the `qa` reviewer agent gates on this).
- Models / records before services / preprocessors.
- YAML rule file is the last production artefact added in US1 — it is the fully-loaded rule that the integration test then exercises end-to-end.

### Parallel Opportunities

- **Phase 2**: T003, T005, T007, T009 are [P] — different new files, no inter-dependencies.
- **Phase 3**: T014, T015, T016, T018 are [P] — T014–T016 are in the same file but no, wait, T014/T015/T016 add new `@Nested` classes to the same file (`DisqualificationExtendedTestPreprocessorTest.java`), so they are NOT [P] across each other. T018 is in a different file, so it IS [P] vs T014–T017. The marks above reflect this. Within T019/T020/T021 production work, T019 (new file `DisqualificationContext.java`) is [P] vs T020 (modify `PreprocessingDefinition.java`), but T021 reads the `PreprocessingDefinition` shape so it cannot run before T020 — sequential.
- **Phase 4 / Phase 5**: US2 and US3 can be developed in parallel by different developers (different `@Nested` classes within the same test files; merge resolved by the test runner).
- **Phase 6**: T035 ([P], new file), T037 ([P], read-only checks), T038 ([P], read-only), T042 ([P], external) are independent.
- **Phase 7.0 (branches)**: T101 + T102 + T103 are [P] — three different repos, no inter-dependencies.
- **Phase 7.A vs 7.C**: api repo publish (T104–T107) and cpp-ui-hearing buildResultLines work (T113–T115) are fully parallel — UI has no Java-lib dependency.
- **Phase 7.B vs 7.D**: cpp-context-hearing (T108–T112) and this repo (T116–T127) both pull the new lib version after T106 lands, then run in parallel against each other.
- **Phase 7.D internal**: T117 + T118 + T119 are [P] — different test fixtures across two test files. T120 + T121 + T122 are [P] (regression updates to existing tests, two files). T123 + T124 are sequential (T124 reads the new field added in T123).
- **Phase 7.D reviewer agents**: T128 + T129 + T130 + T131 are all [P] — each agent reads independently.
- **Phase 7.E PRs**: T132 unblocks T133/T134/T135. After all four PRs are open, T136 (analyze) runs once.

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

## Parallel Example: Phase 7 Sub-phases

```bash
# After T101–T103 (branches) are done, the four-repo work fans out:

# Sub-phase 7.A (api repo): sequential — must publish before consumers can pull.
# T104 → T105 → T106 → T107.

# Sub-phase 7.C (cpp-ui-hearing): can start immediately (no Java lib dep).
Task: "T113 [UI] Failing buildResultLines test"
Task: "T114 [UI] Extend buildResultLines"
Task: "T115 [UI] Run UI test suite"

# After T106 lands, sub-phases 7.B and 7.D fan out in parallel:
Task: "T108–T112 [CCH] cpp-context-hearing parallel-mirror DTO + mapper + tests"
Task: "T116–T127 [VAL] this repo: lib bump + preprocessor tightening + US4 + regression"

# Within 7.D, the failing tests fan out:
Task: "T117 [P] [US4] NoFinalLine @Nested in PreprocessorTest"
Task: "T118 [P] [US4] AdjournmentDoesNotWarn @Nested in IT"
Task: "T119 [P] [US4] CategoryF_GateBoundary @Nested in PreprocessorTest"
```

---

## Implementation Strategy

### MVP First (Phase 2 + User Story 1)

1. Phase 1 — Setup baseline green.
2. Phase 2 — Foundational refactor (no behaviour change). DR-SENT-002 must remain byte-identical.
3. Phase 3 — User Story 1.
4. **STOP and VALIDATE**: hit the four `quickstart.md` curl scenarios; the warning case must fire, the excluded-code and DDOTE cases must already suppress (preprocessor in T021 has all four gates). MVP is shippable here even before US2/US3 test coverage is added.

### Incremental Delivery

The recommended PR breakdown for the original 001 work was two PRs:

- **PR 1**: Phase 1 + Phase 2 (registry refactor only). Title: `refactor(rules): dispatch preprocessors via PreprocessorRegistry`. Description cites Constitution III. No behaviour change; review focuses on the no-regression contract.
- **PR 2**: Phase 3 + Phase 4 + Phase 5 + Phase 6 (the new rule and its full test coverage). Title: `feat(rules): add DR-DISQ-001 extended test disqualification warning`. Description cites Constitution I (YAML-first), VI (severity ceiling), VIII (TDD).

For Phase 7 (2026-04-28 revision), the PR shape is **four PRs across four repos** (one per repo), per Phase 7.E:

- **PR 7.A** (`api-cp-crime-hearing-results-validator`, on `main`): contract-only, ships first.
- **PR 7.B** (`cpp-context-hearing`): consumes new lib + parallel-mirror DTO + mapper.
- **PR 7.C** (`cpp-ui-hearing`): TypeScript-only, independent of the lib publish, can ship in parallel with PR 7.A.
- **PR 7.D** (this repo, `service-cp-crime-hearing-results-validator`): consumes new lib + tightens preprocessor + adds US4 IT.

### Parallel Team Strategy

With two developers (original 001):

1. Both pair on Phase 2 (small, high-risk). Land PR 1.
2. Once PR 1 is merged: Developer A picks up US1 (T014–T023). Developer B starts US2 + US3 test scaffolding (T024–T032) on top of US1's branch via stacked PRs.
3. Polish (Phase 6) runs as a final pass once all three user stories are green.

For Phase 7 (2026-04-28 revision) with four developers (one per repo, ideal):

1. All four pair on T100–T103 (branch creation) and align on the published lib version target.
2. Developer A (api repo): drives 7.A end-to-end. This is the long pole — others wait for T106 before they can pull the new lib.
3. Developer C (cpp-ui-hearing): runs 7.C in parallel — TypeScript-only, no lib dep.
4. After T106 lands: Developer B (cpp-context-hearing) and Developer D (this repo) run 7.B and 7.D in parallel.
5. All four converge on 7.E (open and merge PRs in dependency order).

With one developer doing all four repos (realistic), execute strictly in order: 7.0 → 7.A → 7.C → 7.B → 7.D → 7.E. Total cost: two non-trivial Java-lib bumps and a TS update; each repo's CI runs are the limiting factor.

---

## Notes

- [P] = different files, no dependencies on incomplete tasks.
- TDD is mandatory: every implementation task must be preceded by a failing test that fails for the *correct* reason (assertion failure, not compilation error). The `qa` reviewer agent gates on this.
- US2 and US3 phases consist primarily of test coverage; the production code that satisfies them ships in US1's preprocessor (T021). This is intentional — shipping the gates incrementally would mean shipping a knowingly noisy MVP.
- Commit boundaries: at minimum, one commit per phase. Within Phase 2, commit T003+T005+T007+T009 together (failing tests + scaffolding compiles), then the rest as a second commit; this keeps the no-behaviour-change story intact.
- Per project memory `feedback_run_checkstyle.md`: run `gradle checkstyleMain` after every Java change before committing.
- Per Constitution VII: no `System.out.println`, no `e.printStackTrace()`, in either production or test code.
