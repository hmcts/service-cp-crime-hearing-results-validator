# Tasks: CTL Missing Warning (DR-CTL-001)

**Input**: Design documents from `specs/002-ctl-missing-warning/`
**Branch**: `DD-41663-ctl-missing-warning`

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on in-progress tasks)
- **[Story]**: User story this task belongs to (US1, US2, US3)
- Exact file paths given for every task

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: External API dependency and local infrastructure changes that must be in place before
any Java implementation of the preprocessor can compile.

**⚠️ CRITICAL**: T001 is an external blocker. T003–T005 can proceed independently while T001 is in
flight (they do not reference new OffenceDto fields). All tasks from T006 onward require T001.

- [X] T001 Raise upstream change: add `hasExistingCtlRecord: Boolean` and `isConvicted: Boolean` to `OffenceDto` in `api-cp-crime-hearing-results-validator`; once the new JAR is published update `libs.versions.toml` or `gradle.properties` to reference the new version
- [X] T002 Add `remandShortCodes: List<String>` and `ctlShortCodes: List<String>` fields to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`

**Checkpoint**: T001 (new JAR available) and T002 complete — Java implementation and tests may begin.

---

## Phase 2: YAML Rule + Context Record (Parallel, No Upstream Dependency)

**Purpose**: The YAML rule file and the `CtlOffenceContext` record do not depend on the new OffenceDto
fields. They can be authored immediately (and TDD unit tests written for the context record) while
T001 is in flight.

- [X] T003 [P] Create `src/main/resources/rules/DR-CTL-001.yaml` with `preprocessing.type: "ctl-missing"`, `remandShortCodes`, `ctlShortCodes`, and condition `AC1: ctlWarningCount > 0` at `severity: WARNING` with the exact message from FR-007 (YAML/CEL Rule-First — Constitution Principle I)
- [X] T004 [P] Write failing unit tests for `CtlOffenceContext` record in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CtlOffenceContextTest.java` — verify `toCelContext()` returns `{"ctlWarningCount": 1}` when count is 1 and `{"ctlWarningCount": 0}` when 0; verify `getOffenceIdSet("warningOffenceIds")` and `getOffenceIdSet("allOffenceIds")` return the correct lists; verify `defendantName()` returns null; verify `getOffenceIdSet("<unknown>")` throws `IllegalArgumentException` — confirm tests FAIL before proceeding
- [X] T005 [P] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CtlOffenceContext.java` record (fields: `offenceId: String`, `ctlWarningCount: long`, `warningOffenceIds: List<String>`, `allOffenceIds: List<String>`; implements `RuleEvaluationContext`) — run T004 tests and confirm they now PASS

**Checkpoint**: YAML rule exists, `CtlOffenceContext` record implemented and unit-tested.

---

## Phase 3: User Story 1 — Warning fired when all four conditions met (Priority: P1) 🎯 MVP

**Goal**: A hearing with a remand-type result on an unconvicted, CTL-less offence produces the exact
warning message at WARNING severity.

**Independent Test**: `gradle test --tests "...CtlMissingPreprocessorTest"` positive path passes;
`gradle test --tests "...CtlMissingWarningIntegrationTest"` positive-path test passes.

**⚠️ Requires T001 (upstream JAR) to be complete before starting T006.**

> **TDD: Complete T006 and T007 (write tests) before T008 (implementation). Confirm tests FAIL
> with an assertion failure (not a compilation error) before writing any preprocessor code.**

- [X] T006 [US1] Write failing unit tests for `CtlMissingPreprocessor` positive path in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CtlMissingPreprocessorTest.java` — offence with `shortCode=RI`, `hasExistingCtlRecord=false`, `isConvicted=false`, no CTL result line → `ctlWarningCount=1`, `warningOffenceIds=[offenceId]`; also cover each of the seven trigger short codes (`RI`, `RIYDA`, `RIH`, `RIB`, `RILA`, `RILAB`, `REMYD`) via a parameterised test; confirm tests FAIL before proceeding
- [X] T007 [US1] Write failing integration test for positive trigger path in `src/test/java/uk/gov/hmcts/cp/integration/CtlMissingWarningIntegrationTest.java` — POST to `/api/validation/validate` with one offence (`hasExistingCtlRecord=false`, `isConvicted=false`) and one result line (`shortCode=RI`); assert `$.warnings[?(@.ruleId=='DR-CTL-001')]` has size 1, message matches FR-007 exactly, `$.errors` is empty; confirm test FAILS before proceeding
- [X] T008 [US1] Implement `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CtlMissingPreprocessor.java` — Spring `@Component`, qualifier `"ctl-missing"`, reads `remandShortCodes` and `ctlShortCodes` from `PreprocessingDefinition`, evaluates four-condition logic per offence (has remand result AND no existing CTL AND no CTL result AND not convicted), produces one `CtlOffenceContext` per offence
- [X] T009 [US1] Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CtlMissingPreprocessorTest" --tests "uk.gov.hmcts.cp.integration.CtlMissingWarningIntegrationTest"` — verify T006 and T007 tests pass; fix any issues in T008 until green

**Checkpoint**: User Story 1 independently functional. Positive-path warning fires correctly end-to-end.

---

## Phase 4: User Story 2 — Warning suppressed for all bypass conditions (Priority: P2)

**Goal**: Each of the four bypass conditions individually suppresses the warning.

**Independent Test**: `gradle test --tests "...CtlMissingPreprocessorTest"` bypass cases pass;
`gradle test --tests "...CtlMissingWarningIntegrationTest"` bypass ITs pass.

> **TDD: Write T010 and T011 (tests) before checking if implementation already handles them.
> Run them — they should pass immediately (preprocessor from T008 implements all conditions).
> If any fail, fix T008 before proceeding.**

- [X] T010 [US2] Add failing unit tests for all four bypass conditions to `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CtlMissingPreprocessorTest.java`:
  - bypass 1: `hasExistingCtlRecord=true` → `ctlWarningCount=0`
  - bypass 2: CTL result line present (`shortCode=CTL`) → `ctlWarningCount=0`
  - bypass 3: `isConvicted=true` → `ctlWarningCount=0`
  - bypass 4: no trigger result present → `ctlWarningCount=0`
- [X] T011 [US2] Add integration tests for all four bypass scenarios to `src/test/java/uk/gov/hmcts/cp/integration/CtlMissingWarningIntegrationTest.java` — for each bypass, assert `$.warnings[?(@.ruleId=='DR-CTL-001')]` is empty and `$.errors` is empty
- [X] T012 [US2] Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CtlMissingPreprocessorTest" --tests "uk.gov.hmcts.cp.integration.CtlMissingWarningIntegrationTest"` — verify all bypass tests pass; fix T008 if any fail

**Checkpoint**: All four bypass conditions verified at unit and integration level.

---

## Phase 5: User Story 3 — Warning is advisory only, not blocking (Priority: P3)

**Goal**: The warning is returned at WARNING severity, not ERROR. Sharing is not blocked.

**Independent Test**: Assertion already embedded in T007; add explicit severity assertion if missing.

- [X] T013 [US3] Add assertion to `CtlMissingWarningIntegrationTest.java` (if not already present from T007) that `$.warnings[0].severity` equals `"WARNING"` and `$.errors` is empty for the positive-path scenario — run and confirm green
- [X] T014 [US3] Verify `DR-CTL-001.yaml` `severity: WARNING` field is set correctly (compile check passes via `gradle test`; no ERROR issues emitted)

**Checkpoint**: Severity verified end-to-end. All three user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Static analysis, build verification, and spec validation.

- [X] T015 [P] Add multi-offence edge case to `CtlMissingWarningIntegrationTest.java` — two offences, one meets the warning condition and one does not; assert exactly one warning scoped to the breaching offence (verifies FR-008)
- [X] T016 Run `gradle checkstyleMain` — resolve any Google style violations (`maxWarnings = 0`)
- [X] T017 Run `gradle pmdMain` — resolve any PMD findings (`ignoreFailures = false`)
- [X] T018 Run `gradle build` — full build (Checkstyle + PMD + all tests) must be green
- [X] T019 Run spec-validator agent (read-only) against `src/main/resources/rules/DR-CTL-001.yaml` — confirm schema compliance, `preprocessing.type: "ctl-missing"` resolves to the registered `CtlMissingPreprocessor` bean, and the CEL expression `ctlWarningCount > 0` compiles

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (T001–T002)**: T001 is external — start immediately; T002 can run in parallel
- **Phase 2 (T003–T005)**: No upstream dependency — can start immediately alongside Phase 1
- **Phase 3 (T006–T009)**: Requires T001, T002, T003, T005 — blocked on upstream JAR
- **Phase 4 (T010–T012)**: Requires Phase 3 completion
- **Phase 5 (T013–T014)**: Requires Phase 3 (T007 covers most of US3 already)
- **Phase 6 (T015–T019)**: Requires all implementation complete

### User Story Dependencies

- **US1 (P1)**: Requires Phases 1 & 2 complete — no dependency on US2 or US3
- **US2 (P2)**: Requires US1 implementation complete (Phase 3); bypass logic is in same preprocessor
- **US3 (P3)**: Requires US1 YAML (T003) and IT assertion from T007; minimal extra work

### Within Each Phase (TDD order)

- Tests MUST be written and FAIL before implementation
- `CtlOffenceContext` record before `CtlMissingPreprocessor` (preprocessor returns context instances)
- YAML rule before Java preprocessor (Constitution Principle I: YAML is the contract)
- Unit tests before integration tests within a story

---

## Parallel Opportunities

```bash
# Phase 1 + Phase 2 can both start immediately:
Task T001: Raise upstream API change (external)
Task T002: Add fields to PreprocessingDefinition.java
Task T003: Create DR-CTL-001.yaml
Task T004: Write CtlOffenceContextTest.java (failing tests)

# After T001 resolves and T005 (CtlOffenceContext record) is done:
Task T006: Write CtlMissingPreprocessorTest.java (positive path, failing)
Task T007: Write CtlMissingWarningIntegrationTest.java (positive path, failing)
# Then T008: Implement CtlMissingPreprocessor.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (T001 + T002) — ensure upstream JAR available
2. Complete Phase 2 (T003–T005) — YAML + context record in place
3. Complete Phase 3 (T006–T009) — preprocessor implemented, positive-path tests green
4. **STOP and VALIDATE**: run `gradle test` — US1 fully functional
5. Demonstrate: POST with RI result → DR-CTL-001 WARNING returned

### Incremental Delivery

1. Phases 1 & 2 → Foundation ready
2. Phase 3 → US1 warning fires correctly (MVP)
3. Phase 4 → US2 bypass conditions verified (regression safety)
4. Phase 5 → US3 severity advisory confirmed
5. Phase 6 → Checkstyle + PMD + spec-validator all green

---

## Notes

- `[P]` tasks touch different files and have no dependency on in-progress tasks — safe to parallelize
- All `[US*]` label tasks are traceable to a specific user story in `spec.md`
- Integration tests use `IntegrationTestBase` (MockMvc + TestContainers PostgreSQL + WireMock)
- Integration test class: `CtlMissingWarningIntegrationTest.java` (matches `*IntegrationTest.java` pattern)
- T001 (upstream JAR) is on the critical path — chase early; all Phase 3+ work is blocked on it
- Do NOT add per-rule DB severity ceiling override tests — this is already covered framework-wide in `ValidationRuleOverrideIntegrationTest.java` (see `design_rules.md`)
