# Tasks: No Conviction Warning (DR-CONV-006)

**Input**: Design documents from `specs/006-no-conviction-warning/`
**Branch**: `DD-43039-no-conviction-warning`

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on in-progress tasks)
- **[Story]**: User story this task belongs to (US1, US2, US3)
- Exact file paths given for every task

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Rule YAML, context record, and shared test-data builders. Unlike `DR-CTL-001`, there is
no upstream API blocker here — `ResultLineDto.category` and `OffenceDto.isConvicted` already exist,
and `PreprocessingDefinition.excludedFinalShortCodes` already exists (added for `DR-DISQ-001`), so
every task in this phase can start immediately.

- [X] T001 [P] Create `src/main/resources/rules/DR-CONV-006.yaml` with `preprocessing.type: "no-conviction-check"`, `excludedFinalShortCodes` (`wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`), and condition `AC1: unconvictedSentenceCount > 0` at `severity: WARNING` with the exact message from FR-006, `affectedOffenceSet: "warningOffenceIds"`, `validationLevel: OFFENCE` (YAML/CEL Rule-First — Constitution Principle I). *Later extended (post-T008) to add `err`, `errf`, `dead` to `excludedFinalShortCodes` — see `research.md` Decision 2 update.*
- [X] T002 [P] Write failing unit tests for `NoConvictionContext` record in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/NoConvictionContextTest.java` — verify `toCelContext()` returns all four keys (`unconvictedSentenceCount`, `finalCategoryCount`, `excludedFinalCount`, `convictedCount`) with correct values; verify `getOffenceIdSet("warningOffenceIds")` and `getOffenceIdSet("allOffenceIds")` return the correct lists; verify `defendantName()` returns null; verify `getOffenceIdSet("<unknown>")` throws `IllegalArgumentException` — confirm tests FAIL before proceeding
- [X] T003 [P] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/NoConvictionContext.java` record (fields: `offenceId: String`, `unconvictedSentenceCount: long`, `finalCategoryCount: long`, `excludedFinalCount: long`, `convictedCount: long`, `warningOffenceIds: List<String>`, `allOffenceIds: List<String>`; implements `RuleEvaluationContext`) — run T002 tests and confirm they now PASS
- [X] T004 ~~Add `resultLineWithCategory(...)` and `offenceWithConviction(...)` builder overloads~~ — **not needed**: `javap` confirmed `ResultLineDto.category(...)` and `OffenceDto.isConvicted(...)` are already fluent instance setters (openapi-generator output), so existing tests chain them directly (e.g. `resultLine(...).category(F)`, `offence(...).isConvicted(false)`), matching `DisqualificationExtendedTestPreprocessorTest`'s established style. No `ValidationRuleTestHelper.java` change was made.

**Checkpoint**: YAML rule, context record, and test builders ready — preprocessor implementation and tests may now begin.

---

## Phase 2: User Story 1 — Warning displayed for sentenced offence with no conviction (Priority: P1) 🎯 MVP

**Goal**: An offence with a final (`category='F'`), non-excluded result and no conviction produces the exact WARNING message, scoped to that offence only.

**Independent Test**: `gradle test --tests "...NoConvictionPreprocessorTest"` positive path and bypass cases pass; `gradle test --tests "...NoConvictionWarningIntegrationTest"` positive-path test passes.

> **TDD: Complete T005 and T006 (write tests) before T007 (implementation). Confirm tests FAIL
> with an assertion failure (not a compilation error) before writing any preprocessor code.**

- [X] T005 [US1] Write failing unit tests for `NoConvictionPreprocessor` in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/NoConvictionPreprocessorTest.java`:
  - positive: offence with one `category=F` result line, short code not in the excluded set (e.g. `COEW`), `isConvicted=false` → `unconvictedSentenceCount=1`, `warningOffenceIds=[offenceId]`
  - bypass: `category=F` result line whose short code is in the excluded set — parameterised across all excluded codes currently in `DR-CONV-006.yaml` (`wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`, `err`, `errf`, `dead`, sourced from the YAML via `RuleDefinitionLoader` rather than duplicated as a literal — see `NoConvictionPreprocessorTest`), including mixed-case input (e.g. `WDRN`) to prove case-insensitive matching — → `unconvictedSentenceCount=0`
  - bypass: offence has no `category=F` result line at all (only `A`/`I`, or none) → `unconvictedSentenceCount=0`
  - multi-offence isolation: two offences in one request, only one qualifies → only the qualifying offence's context has `unconvictedSentenceCount=1`
  confirmed FAIL (compile error — class didn't exist) before proceeding
- [X] T006 [US1] Write failing integration test for the positive path in `src/test/java/uk/gov/hmcts/cp/integration/NoConvictionWarningIntegrationTest.java` — POST to `/api/validation/validate` with one offence (`isConvicted=false`) and one `category=F` result line with a non-excluded short code (e.g. `COEW`); assert `$.warnings[?(@.ruleId=='DR-CONV-006')]` has size 1, message matches FR-006 exactly, `$.errors` is empty
- [X] T007 [US1] Implement `src/main/java/uk/gov/hmcts/cp/services/rules/cel/NoConvictionPreprocessor.java` — Spring `@Component`, qualifier `"no-conviction-check"`, reads `excludedFinalShortCodes` from `PreprocessingDefinition` via `PreprocessorHelper.upperSet`/`hasUpperCode` (not private re-implementations — see `research.md` Decision 6), computes per offence: `finalNonExcluded = any category=F line with short code not in excluded set`, `isConvicted = Boolean.TRUE.equals(offence.getIsConvicted())`, `unconvictedSentence = finalNonExcluded && !isConvicted`; produces one `NoConvictionContext` per offence; no `relevantOffenceCodes` gate (applies to every offence — FR-009)
- [X] T008 [US1] Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.NoConvictionPreprocessorTest" --tests "uk.gov.hmcts.cp.integration.NoConvictionWarningIntegrationTest"` — T005 and T006 pass

**Checkpoint**: User Story 1 independently functional. Positive-path warning fires correctly end-to-end.

---

## Phase 3: User Story 2 — Warning cleared once the offence is convicted (Priority: P1)

**Goal**: When `isConvicted` becomes true — whether via a guilty plea (AC1A) or a guilty verdict following a not-guilty plea (AC1B) — the warning no longer fires, even though the qualifying final result is still present.

**Independent Test**: `gradle test --tests "...NoConvictionPreprocessorTest"` conviction-clears-warning case passes; `gradle test --tests "...NoConvictionWarningIntegrationTest"` conviction-clears-warning IT passes.

> **TDD: Write T009 and T010 (tests) before checking whether T007 already handles them. Run
> them — they should pass immediately, since `NoConvictionPreprocessor` (T007) already reads
> `isConvicted`. If either fails, fix T007 before proceeding.**

- [X] T009 [US2] Add a failing unit test to `NoConvictionPreprocessorTest.java` — same offence shape as the T005 positive case (qualifying final, non-excluded result) but `isConvicted=true` → `unconvictedSentenceCount=0`. One test case stands in for both AC1A and AC1B: this service observes only the single `isConvicted` boolean, not which upstream event (plea vs. verdict) set it — see `research.md` Decision 3.
- [X] T010 [US2] Add an integration test to `NoConvictionWarningIntegrationTest.java` — same request as the T006 positive-path test but with `isConvicted=true` on the offence → assert `$.warnings[?(@.ruleId=='DR-CONV-006')]` is empty and `$.errors` is empty
- [X] T011 [US2] Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.NoConvictionPreprocessorTest" --tests "uk.gov.hmcts.cp.integration.NoConvictionWarningIntegrationTest"` — T009 and T010 pass

**Checkpoint**: Conviction-clears-warning verified at unit and integration level; both AC1A and AC1B are covered by the same assertion.

---

## Phase 4: User Story 3 — Warning is advisory only; sharing is not blocked (Priority: P2)

**Goal**: The warning is returned at WARNING severity, never ERROR, and does not appear in the response's error list.

**Independent Test**: Assertion already embedded in T006 (`$.errors` empty); add an explicit severity assertion if not already present.

- [X] T012 [US3] Add an assertion to the T006 positive-path test in `NoConvictionWarningIntegrationTest.java` (if not already present) that `$.warnings[0].severity` equals `"WARNING"` and `$.errors` is empty — confirmed green
- [X] T013 [US3] Verify `DR-CONV-006.yaml`'s `severity: WARNING` field is set correctly (compile check via `gradle test`; no ERROR-level issues emitted for this rule)

**Checkpoint**: Severity verified end-to-end. All three user stories independently functional.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Static analysis, build verification, and spec validation.

- [X] T014 [P] Add a multi-line-per-offence edge case to `NoConvictionPreprocessorTest.java` — an offence with multiple result lines where only one is a qualifying `category=F`, non-excluded line — assert `unconvictedSentenceCount=1` (verifies the "any line" semantics, not "all lines")
- [X] T015 Run `gradle checkstyleMain` — resolve any Google style violations (`maxWarnings = 0`)
- [X] T016 Run `gradle pmdMain` — resolve any PMD findings (`ignoreFailures = false`)
- [X] T017 Run `gradle build` — full build (Checkstyle + PMD + all tests) is green
- [X] T018 Run `code-reviewer`, `spec-validator`, and `qa` agents against the DR-CONV-006 change set — `spec-validator`: COMPLIANT (0 findings); `qa`: PASS (55/55 tests, 3 low-risk coverage-gap notes); `code-reviewer`: PASS (2 LOW findings). Fixes applied: reworded `NoConvictionContext`'s misleading Javadoc (count fields are not all 0/1); added three tests closing every qa-flagged gap — `mixing_final_and_non_final_lines_should_ignore_the_non_final_one` (F + non-F lines on one offence), `every_offence_meeting_the_condition_should_warn_independently` (all offences in a hearing qualifying simultaneously), and `convicted_offence_with_no_final_result_yet_should_not_warn` (convicted before any final result is recorded — the spec.md edge case with zero prior coverage). Full `gradle build` re-run green after fixes.
- [X] T019 **(discovered during implementation, not in the original plan)** Add Flyway migration `src/main/resources/db/migration/V1.006__insert_dr_conv_006.sql` seeding `('DR-CONV-006', false, 'WARNING')` into `validation_rule` — every prior rule (`DR-DISQ-001`, `DR-CTL-001`, `DR-YRO-001`) ships disabled-by-default via its own migration; this was missed in `plan.md`/`data-model.md` and only surfaced when `gradle build` failed. Updated four dependent tests that hardcode the full rule list/count: `ValidationRuleAutoConfigurationTest` (manual `PreprocessorRegistry` construction + exact rule count), `ValidationRulesControllerIntegrationTest` and `ValidationRulesApiHttpLiveTest` (`/api/validation/rules` count/enabledCount/rule-id list), and `CrossRuleRegressionIntegrationTest`/`ValidationControllerIntegrationTest` (`$.rulesEvaluated` exact list — every registered rule appears here regardless of DB-enabled state)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (T001–T004)**: No blockers — start immediately, all four tasks can run in parallel
- **Phase 2 (T005–T008)**: Requires T001, T003, T004
- **Phase 3 (T009–T011)**: Requires Phase 2 completion (same preprocessor, same test files)
- **Phase 4 (T012–T013)**: Requires Phase 2 (T006 covers most of US3 already)
- **Phase 5 (T014–T018)**: Requires all implementation complete

### User Story Dependencies

- **US1 (P1)**: Requires Phase 1 complete — no dependency on US2 or US3
- **US2 (P1)**: Requires US1 implementation complete (Phase 2); conviction-check logic is in the same preprocessor
- **US3 (P2)**: Requires US1 YAML (T001) and IT assertions from T006; minimal extra work

### Within Each Phase (TDD order)

- Tests MUST be written and FAIL before implementation
- `NoConvictionContext` record before `NoConvictionPreprocessor` (preprocessor returns context instances)
- YAML rule before Java preprocessor (Constitution Principle I: YAML is the contract)
- Unit tests before integration tests within a story

---

## Parallel Opportunities

```bash
# Phase 1 — all four tasks can start immediately:
Task T001: Create DR-CONV-006.yaml
Task T002: Write NoConvictionContextTest.java (failing tests)
Task T004: Add resultLineWithCategory/offenceWithConviction to ValidationRuleTestHelper.java

# After T003 (NoConvictionContext record) and T004 (test helpers) are done:
Task T005: Write NoConvictionPreprocessorTest.java (positive + bypass cases, failing)
Task T006: Write NoConvictionWarningIntegrationTest.java (positive path, failing)
# Then T007: Implement NoConvictionPreprocessor.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (T001–T004) — YAML, context record, and test builders in place
2. Complete Phase 2 (T005–T008) — preprocessor implemented, positive-path and bypass tests green
3. **STOP and VALIDATE**: run `gradle test` — US1 fully functional
4. Demonstrate: POST with a `category=F`, non-excluded result and `isConvicted=false` → `DR-CONV-006` WARNING returned

### Incremental Delivery

1. Phase 1 → Foundation ready
2. Phase 2 → US1 warning fires correctly (MVP)
3. Phase 3 → US2 conviction-clears-warning verified (regression safety, covers AC1A + AC1B)
4. Phase 4 → US3 severity advisory confirmed
5. Phase 5 → Checkstyle + PMD + spec-validator all green

---

## Notes

- `[P]` tasks touch different files and have no dependency on in-progress tasks — safe to parallelize
- All `[US*]` label tasks are traceable to a specific user story in `spec.md`
- Integration tests use `IntegrationTestBase` (MockMvc + TestContainers PostgreSQL + WireMock)
- Integration test class: `NoConvictionWarningIntegrationTest.java` (matches `*IntegrationTest.java` pattern)
- No upstream blocker and no `PreprocessingDefinition` changes — unlike `DR-CTL-001`, every task here can start on day one
- Do NOT add a per-rule DB severity ceiling override test — this is already covered framework-wide in `ValidationRuleOverrideIntegrationTest.java` (see `design_rules.md`)
