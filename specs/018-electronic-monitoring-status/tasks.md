# Tasks: Electronic Monitoring Status Validation (CRA-38)

**Input**: Design documents from `specs/018-electronic-monitoring-status/`  
**Branch**: `dev/CRA-38-electronic-monitoring-status`  
**TDD**: REQUIRED (Constitution Principle VIII) — failing test before every production class

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete task dependencies)
- **[US]**: User story label (US1–US4)
- Exact file paths required for every task

---

## Phase 1: Setup (YAML Rule + DB Migration — Blocking Prerequisites)

**Purpose**: Create the YAML contract first (Principle I) and DB migration before any Java.  
**⚠️ CRITICAL**: No Java code may be written until T001 (the YAML) is in place.

- [X] T001 Create YAML rule file `src/main/resources/rules/DR-EMON-017.yaml` with rule id `DR-EMON-017`, priority `18000`, `preprocessing.type = "electronic-monitoring-status"`, short-code lists `continuingEmPromptRefs` (ELMONDET, EMREQ, EMREQV, EMSO), `endingEmShortCodes` (EMONE), `exemptMainResultShortCodes` (NHMC, NHCC, A, ADJNN, ADJAPP), one condition `AC2` with expression `unresolvedEmCount > 0`, severity `ERROR`, offence-level `messageTemplate`, page-level `errorMessageTemplate` with `${defendantNames}`, and `affectedOffenceSet: "unresolvedOffenceIds"`

- [X] T002 Add three new `@Nullable List<String>` fields to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`: `continuingEmPromptRefs`, `endingEmShortCodes`, `exemptMainResultShortCodes` (with matching `@JsonProperty` / Jackson mapping so the YAML fields bind correctly)

- [X] T003 Create Liquibase migration `src/main/resources/db/migration/V1.019__insert_dr_emon_017.sql` — inserts `('DR-EMON-017', true, 'ERROR')` into the `validation_rule` table

**Checkpoint**: YAML rule is readable by `RuleDefinitionLoader`; PreprocessingDefinition compiles; SQL migration file exists. `gradle compileJava` is green.

---

## Phase 2: Foundational — Context Record (Shared Infrastructure, All Stories)

**Purpose**: The `ElectronicMonitoringContext` record is the shared output type used by US1–US4.

- [X] T004 Write failing unit tests for `ElectronicMonitoringContext` in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/ElectronicMonitoringContextTest.java` covering: `toCelContext()` returns `Map.of("unresolvedEmCount", n)`, `getOffenceIdSet("unresolvedOffenceIds")` returns the list, unknown set name throws `IllegalArgumentException`, `defendantName()` returns stored name, `allOffenceIds()` returns `unresolvedOffenceIds`; run `gradle test --tests "...ElectronicMonitoringContextTest"` and confirm it fails to compile (class doesn't exist yet)

- [X] T005 Create Java record `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ElectronicMonitoringContext.java` implementing `RuleEvaluationContext` with fields `String defendantId`, `String defendantName`, `long unresolvedEmCount`, `List<String> unresolvedOffenceIds`; implement `toCelContext()`, `getOffenceIdSet()`, `allOffenceIds()`, `defendantId()`; run `gradle test --tests "...ElectronicMonitoringContextTest"` and confirm all tests pass

**Checkpoint**: `ElectronicMonitoringContextTest` is green.

---

## Phase 3: User Story 1 + User Story 2 — Core Preprocessor Logic (Priority: P1) 🎯 MVP

**Goal**: Preprocessor correctly identifies unresolved EM-flagged offences (US1) and passes all resolved combinations cleanly (US2).  
**Independent Test**: `gradle test --tests "...ElectronicMonitoringPreprocessorTest"` green with zero failures.

### Tests (write first — MUST FAIL before T009)

- [X] T006 [US1] Write failing unit test cases in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/ElectronicMonitoringPreprocessorTest.java` for **AC2 error scenarios**: (a) EM-flagged offence with final result (IMP, category F) and no EMONE and no continuing EM prompt → unresolvedEmCount=1; (b) EM-flagged offence with non-exempt interim result (RC, category I) and no EM → error; (c) COEW (category F) on EM-flagged offence with no EMONE → error; (d) `type()` returns `"electronic-monitoring-status"`; run and confirm compilation failure (class not yet created)

- [X] T007 [US1] Add failing unit test cases in the same `ElectronicMonitoringPreprocessorTest.java` for **AC3 scenarios**: (a) NHMC as child of RC (both category I, only RC has non-exempt shortCode) → NOT resolved → error; (b) NHCC as child of non-exempt main result → error

- [X] T008 [P] [US2] Add failing unit test cases in `ElectronicMonitoringPreprocessorTest.java` for **AC1 happy paths — all nine example combinations**: (a) all EMONE; (b) all RC with ELMONDET promptRef="Yes"; (c) all COEW with EMREQ promptRef="Yes"; (d) mixed COEW+EMREQ=Yes plus EMONE; (e) mixed RC+ELMONDET=Yes plus EMONE; (f) mixed RC+ELMONDET=Yes plus EMONE plus blank; (g) all blank (no results); (h) all exempt main results (NHMC/A/ADJNN each as sole category-I result); (i) ancillary-only (ODIR/SMP, all category A)

### Implementation

- [X] T009 Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ElectronicMonitoringPreprocessor.java` as a Spring `@Component` implementing `ValidationPreprocessor`; `type()` returns `"electronic-monitoring-status"`; implement `preprocess()` using `PreprocessorHelper.groupResultsByOffence()`, `PreprocessorHelper.buildDefendantNames()`, the resolution algorithm from `plan.md` (blank→resolved, EMONE→resolved, continuing-EM-prompt→resolved, exempt-main-result→resolved, ancillary-only→resolved, else unresolved); return one `ElectronicMonitoringContext` per defendant with ≥1 unresolved offence; run `gradle test --tests "...ElectronicMonitoringPreprocessorTest"` and confirm all T006/T007/T008 tests now pass

- [X] T010 [US1] Add additional unit tests in `ElectronicMonitoringPreprocessorTest.java` for edge cases: (a) EMREQ prompt with promptValue="No" → NOT a continuing result → error; (b) EMSO prompt with promptValue="Yes" → resolved; (c) EMREQV prompt with promptValue="Yes" → resolved; (d) offence has both IMP and EMONE → resolved; (e) offence with IMP + ELMONDET=Yes → resolved; (f) NHMC as the only category-I result (no other category-I) → resolved; run tests and confirm green

- [X] T011 [P] [US2] Add unit test to `ElectronicMonitoringPreprocessorTest.java` verifying that a hearing with zero result lines on an EM-flagged offence (blank) produces no context (resolved); and that multiple EM-flagged offences, only some unresolved, correctly lists only the unresolved offenceIds in the context

**Checkpoint**: `ElectronicMonitoringPreprocessorTest` fully green. `gradle test --tests "...ElectronicMonitoring*"` passes.

---

## Phase 4: User Story 3 — No Validation When No EM Flag (Priority: P2)

**Goal**: Preprocessor returns empty map when no offence has `hasActiveElectronicMonitoring=true`.  
**Independent Test**: `gradle test --tests "...ElectronicMonitoringPreprocessorTest.noEmFlaggedOffences*"` passes.

- [X] T012 [P] [US3] Add failing test method `noEmFlaggedOffences_shouldReturnEmptyContext` to `ElectronicMonitoringPreprocessorTest.java`: hearing with final results on offences all having `hasActiveElectronicMonitoring=false` (or null) → `preprocess()` returns empty map; confirm test initially fails only on assertion (not compile error) with the already-compiled class, then verify it passes after reviewing the preprocessor's early-exit logic (should already be in T009 implementation)

**Checkpoint**: AC9 test passes. No EM-related issues produced for non-EM hearings.

---

## Phase 5: User Story 4 — Multi-Defendant Defendant Names in Error (Priority: P2)

**Goal**: Error message correctly appends "This affects [name(s)]" when hearing has >1 defendant.  
**Independent Test**: Unit test with 2- and 3-defendant hearings confirms name formatting.

- [X] T013 [P] [US4] Add unit test methods to `ElectronicMonitoringPreprocessorTest.java` for **AC4**: (a) single-defendant hearing → `ElectronicMonitoringContext.defendantName()` populated, `${defendantNames}` suppressed by `MessageTemplateResolver`; (b) two-defendant hearing where both trigger → contexts for each have distinct `defendantId`/`defendantName`; (c) two-defendant hearing where only one triggers → only the affected defendant's context is emitted; (d) three-defendant hearing where all three trigger → three separate contexts

- [X] T014 [US4] Verify the YAML `errorMessageTemplate` in `DR-EMON-017.yaml` includes `${defendantNames}` and that the existing `MessageTemplateResolver.resolveDefendantNames()` produces "This affects A and B" for 2 defendants and "This affects A, B, C" for 3 defendants (via a focused unit test against `MessageTemplateResolver` itself, in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/ElectronicMonitoringMessageTemplateTest.java`)

**Checkpoint**: All multi-defendant scenarios pass unit tests.

---

## Phase 6: Integration Tests (End-to-End via TestContainers + MockMvc)

**Purpose**: Prove the full YAML→Preprocessor→CEL→Response pipeline works end-to-end against a real PostgreSQL container.

- [X] T015 Write integration test class `src/test/java/uk/gov/hmcts/cp/integration/ElectronicMonitoringStatusIT.java` extending `IntegrationTestBase`; cover: (a) AC2 — EM-flagged offence with IMP result (category F), no EMONE, no continuing prompt → `$.errors[?(@.ruleId=='DR-EMON-017')]` has size 1 with correct offenceId AND message text equals "Electronic monitoring is in place on this offence. Add a result that continues electronic monitoring, or end it with 'Electronic Monitoring End - notify contractor (EMONE)'."; (b) AC1 — EM-flagged offence with EMONE result → errors empty; (c) AC1 — EM-flagged offence with RC result that has ELMONDET="Yes" prompt → errors empty; (d) AC3 — RC + NHMC (NHMC as category I alongside RC) → error fires; (e) AC9 — no EM-flagged offences → errors empty; (f) AC4 — two defendants, both unresolved → error message contains both names; (g) all-blank EM offences → no error (resolved by blank rule); run `gradle test --tests "...ElectronicMonitoringStatusIT"` and confirm green

- [X] T016 Add integration test scenario for the remaining AC1 combinations to `ElectronicMonitoringStatusIT.java`: (a) EMREQ="Yes" prompt → resolved; (b) EMREQ="No" prompt (no EMONE, no other resolution) → error fires (continuing result with No does NOT resolve); (c) EMSO="Yes" prompt → resolved; (d) all exempt main results (NHMC/A/ADJNN each alone) → resolved; (e) ancillary-only (ODIR, category A) → resolved; (f) mixed continue+end+blank across three offences → resolved; run `gradle test --tests "...ElectronicMonitoringStatusIT"` and confirm all pass

**Checkpoint**: `gradle test` is fully green, covering unit + all integration tests.

---

## Phase 7: API Tests + Local HTTP File

**Purpose**: Live-HTTP coverage against the full docker-compose stack; local IntelliJ scratch file for manual testing.

- [X] T017 Create `src/apiTest/java/uk/gov/hmcts/cp/http/ElectronicMonitoringStatusApiHttpLiveTest.java` with: (a) positive error case — EM-flagged offence with IMP result, no EMONE → response has `DR-EMON-017` ERROR; (b) happy path — EM-flagged offence with EMONE → no DR-EMON-017 issues; (c) no EM flag → no issues; run `gradle api` to confirm green

- [X] T018 [P] Create local test file `specs/018-electronic-monitoring-status/CRA-38.http` with named HTTP requests for all ACs: `### AC2 — Error: EM-flagged offence with IMP, no EMONE`, `### AC1 — Pass: EM-flagged offence with EMONE`, `### AC1 — Pass: EM-flagged offence with RC+ELMONDET=Yes`, `### AC3 — Error: RC + NHMC as child`, `### AC9 — Pass: No EM-flagged offence`, `### AC4 — Error: Two defendants both unresolved (names in message)`, each as a complete `POST http://localhost:4550/api/validation/validate` request with inline JSON body

**Checkpoint**: `gradle api` passes. CRA-38.http covers all ACs and runs cleanly in IntelliJ HTTP client.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T019 [P] Update `README.md` — add `DR-EMON-017` row to the validation rules table with rule ID, title, severity (ERROR), and brief description

- [X] T020 [P] Update `CHANGELOG.md` — add CRA-38 entry under the current (unreleased) version section: "feat: Add DR-EMON-017 electronic monitoring status validation rule (CRA-38)"

- [X] T021 Run `gradle build` (Checkstyle + PMD + all tests) and fix any Checkstyle or PMD violations in the new files; `maxWarnings = 0` must be satisfied

- [X] T022 Run `gradle jacocoTestReport` and verify 100% instruction and branch coverage on `ElectronicMonitoringPreprocessor` and `ElectronicMonitoringContext`; add any missing test cases to close coverage gaps

- [X] T023 Spawn `code-reviewer` agent (read-only) to review the final diff: check for logic errors, null safety, layering violations, `System.out` usage, severity-ceiling promotion, and adherence to conventions in `.claude/rules/`; apply all NEEDS CHANGES findings

- [X] T024 Spawn `qa` agent to verify TDD discipline (failing test before production code), test method naming convention (`{action}_{scenario}_should_{expectation}`), `@Nested`/`@DisplayName` usage, and that every AC from spec.md is exercised by at least one test; apply all FAIL findings

- [X] T025 Spawn `spec-validator` agent (read-only) to verify `DR-EMON-017.yaml`: schema compliance, `preprocessing.type = "electronic-monitoring-status"` resolves to a registered bean, every CEL expression compiles, `${defendantNames}` placeholder is valid, `affectedOffenceSet` matches a named set on the context record; apply all DRIFT DETECTED findings

---

## Dependencies & Execution Order

### Phase Dependencies

| Phase | Depends On | Notes |
|-------|-----------|-------|
| Phase 1 (Setup) | Nothing | Start immediately |
| Phase 2 (Context) | Phase 1 (T002 must compile) | PreprocessingDefinition needed before context |
| Phase 3 (Core preprocessor) | Phase 2 (T005 green) | Context record must exist |
| Phase 4 (No EM flag) | Phase 3 (T009 implemented) | Early-exit already in preprocessor |
| Phase 5 (Multi-defendant) | Phase 3 (T009 implemented) | Preprocessor emits per-defendant context |
| Phase 6 (Integration tests) | Phase 3–5 green | Full pipeline must work |
| Phase 7 (API + HTTP) | Phase 6 green | Requires docker-compose stack |
| Phase 8 (Polish) | Phase 7 green | Final quality gates |

### Within Each Phase

- Tests MUST be written and confirmed to fail (or not compile) before the production class is created
- T006, T007, T008 can be written in parallel (all additions to the same test class — write sequentially to avoid file conflicts)
- T015 and T016 can be batched: write both together before running `gradle test`
- T019 and T020 are always parallelisable (different files)

### User Story Dependencies

- **US1 + US2 (P1)**: Can start after Phase 2 (T005 green)
- **US3 (P2)**: Requires Phase 3 T009; likely already passes after T009
- **US4 (P2)**: Requires Phase 3 T009; independently verifiable via T013/T014

---

## Parallel Execution Examples

### Phase 3 test writing (all to same file — do sequentially)

```text
T006 → T007 → T008 (all in ElectronicMonitoringPreprocessorTest.java)
Then: T009 (production class) to make T006/T007/T008 pass
Then: T010, T011 (more tests) → verify still green
```

### Phase 6 integration tests

```text
T015 and T016 → write both → run once: gradle test --tests "...ElectronicMonitoringStatusIT"
```

### Phase 7 parallelisable

```text
T017 (API test)  ← write in parallel with →  T018 (.http file)
Then: gradle api (runs both)
```

### Phase 8 parallelisable tasks

```text
T019 (README) ← parallel with → T020 (CHANGELOG)
Then: T021 (gradle build) → T022 (jacoco) → T023/T024/T025 (review agents)
```

---

## Implementation Strategy

### MVP First (US1 + US2 — the error and the happy path)

1. Complete Phase 1 (YAML + migration + PreprocessingDefinition)
2. Complete Phase 2 (ElectronicMonitoringContext record)
3. Complete Phase 3 (Core preprocessor + all AC1/AC2/AC3 unit tests)
4. **STOP and VALIDATE**: Run `gradle test --tests "...ElectronicMonitoring*"` — must be green
5. Add Phases 4–5 (US3, US4) — these are low-risk additions to the already-tested preprocessor
6. Phases 6–8 close out integration, API, and quality gates

### Incremental Delivery

1. YAML + DB migration → rule skeleton visible at startup
2. Context record + preprocessor → rule fires correctly in unit tests
3. Integration tests → full pipeline proven
4. API tests + .http file → manual + CI coverage
5. README + CHANGELOG + build checks → merge-ready

---

## Notes

- `[P]` tasks operate on different files and have no dependencies on incomplete tasks in the same phase — they can be run in parallel only within that constraint
- Every new test method MUST follow naming convention `{action}_{scenario}_should_{expectation}` (e.g., `preprocess_emFlaggedOffenceWithImpResult_shouldReturnUnresolvedContext`)
- Use `@Nested` + `@DisplayName` to group unit test scenarios (e.g., `@Nested @DisplayName("AC1 — resolved combinations")`)
- Do not add per-rule override/severity-ceiling tests (framework-level only, see `ValidationRuleOverrideIntegrationTest.java`)
- Do not add `System.out` or `System.err` anywhere
- Commit style: Conventional Commits (`feat:`, `test:`, `chore:`)
