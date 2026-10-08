# Tasks: Endorsable Offence Warning (CRA-336)

**Input**: Design documents from `specs/012-endorsable-offence-warning/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓

**Organization**: Tasks grouped by user story for independent implementation and testing.
**TDD**: Constitution Principle VIII — failing tests authored BEFORE production code. Each US phase opens with tests, then implementation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared dependency)
- **[Story]**: User story this task belongs to (US1–US5 from spec.md)

---

## Phase 1: YAML Rule (Constitution Principle I — YAML First)

**Purpose**: The YAML file is the contract. Write it before any Java.

- [x] T001 Create `src/main/resources/rules/DR-ENDORSEMENT-012.yaml` with rule id, title, priority 12000, preprocessing type `endorsable-offence`, full `excludedFinalShortCodes` list (24 codes), full `satisfyingShortCodes` list (21 codes), and AC1 CEL condition `qualifyingCount > 0` / severity WARNING / message "This offence is endorsable. Add an endorsement, disqualification or special reasons result." / affectedOffenceSet `endorsableOffenceIds` / validationLevel OFFENCE — see `specs/012-endorsable-offence-warning/plan.md` Appendix for both code lists

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared data structures and reference-data client changes that the preprocessor depends on. Must complete before any preprocessor work.

**⚠️ CRITICAL**: No preprocessor or test implementation can compile until this phase is complete.

- [x] T002 Add `satisfyingShortCodes` (`List<String>`) field to the `PreprocessingDefinition` record in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` — append after `alcoholAbstinenceShortCodes`, before `qualifyingMisCode`; add the section comment `// Endorsable-offence-specific short-code lists`
- [x] T003 [P] Add `Integer endorsableFlag` field to the `ReferencedataOffenceResponse` record in `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceResponse.java` — append as the last record component before the nested record definitions; `@JsonIgnoreProperties(ignoreUnknown = true)` is already present at class level
- [x] T004 [P] Create `EndorsableOffenceContext` record in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/EndorsableOffenceContext.java` — fields: `String offenceId`, `long qualifyingCount`; implements `RuleEvaluationContext`; `toCelContext()` returns `Map.of("qualifyingCount", qualifyingCount)`; `getOffenceIdSet("endorsableOffenceIds")` returns `List.of(offenceId)` when `qualifyingCount == 1` else `List.of()`; `defendantName()` returns `null`; `allOffenceIds()` returns `List.of(offenceId)`; declare package-private constant `static final String ENDORSABLE_OFFENCE_IDS = "endorsableOffenceIds"`
- [x] T005 Add `getEndorsableFlag(String offenceCode)` method to `ReferencedataOffenceClient` in `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceClient.java` — annotated `@Cacheable(value = "referencedataOffences", key = "'ef:' + #offenceCode", unless = "#result == null")`; returns `Optional<Integer>`; fails-open (returns `Optional.empty()` on any failure); reads `offence.endorsableFlag()` from response; cache key prefix `"ef:"` avoids cross-contamination with `"ci:"` (custodial) and bare (misCode) entries

**Checkpoint**: `gradle compileJava` passes cleanly — preprocessor scaffolding can now compile.

---

## Phase 3: User Story 1 — Core Warning Trigger (Priority: P1) 🎯 MVP

**Goal**: When an endorsable offence has a non-excluded final result and no satisfying result, return `WARNING` with the correct message against that offence.

**Independent Test**: `gradle test --tests "*.EndorsableOffencePreprocessorTest"` plus `gradle test --tests "*.EndorsableOffenceRuleIT#*AC1*"` both green.

### Tests for User Story 1 — write and FAIL before implementation

> **Write these tests FIRST. Run `gradle test --tests "*.EndorsableOffencePreprocessorTest"` and confirm COMPILATION FAILURE (class does not exist yet). After T007 (preprocessor stub), confirm ASSERTION FAILURE, not compilation error.**

- [x] T006 [US1] Create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/EndorsableOffencePreprocessorTest.java` with `@ExtendWith(MockitoExtension.class)`; mock `ReferencedataOffenceClient`; nested class `@Nested @DisplayName("AC1 — endorsable offence, final result, no satisfying result")` with test method `preprocess_endorsableOffenceWithFinalResultAndNoSatisfyingCode_should_returnQualifyingCount1()` — stub `getEndorsableFlag("RT88010")` to return `Optional.of(1)`; build request with one Category-F result `IMP` (non-excluded) and no satisfying code; assert `qualifyingCount == 1`
- [x] T007 [US1] Create minimal stub `EndorsableOffencePreprocessor` in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/EndorsableOffencePreprocessor.java` — `@Slf4j @Component @RequiredArgsConstructor`; `type()` returns `"endorsable-offence"`; `preprocess()` returns empty map — verify the T006 test now FAILS for the CORRECT REASON (assertion `qualifyingCount != 1`, not a compilation error)

### Implementation for User Story 1

- [x] T008 [US1] Implement full `EndorsableOffencePreprocessor.preprocess()` in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/EndorsableOffencePreprocessor.java`: inject `ReferencedataOffenceClient`; for each offence, call `getEndorsableFlag(offenceCode)` — if not `Optional.of(1)` emit `qualifyingCount=0`; group result lines by offence via `PreprocessorHelper.groupResultsByOffence`; filter Category-F lines; check any non-excluded final exists (`hasUpperCode` against `upperSet(config.excludedFinalShortCodes())`); check any satisfying code across ALL lines (`anyShortCodeIn` against `upperSet(config.satisfyingShortCodes())`); emit `qualifyingCount = 1` only when endorsable AND non-excluded final present AND no satisfying; all short-code comparisons case-insensitive via `upperSet` / `hasUpperCode` / `anyShortCodeIn` from `PreprocessorHelper`
- [x] T009 [US1] Run `gradle test --tests "*.EndorsableOffencePreprocessorTest"` — confirm all US1 unit tests pass; fix any issues before proceeding
- [x] T010 [US1] Create `src/test/java/uk/gov/hmcts/cp/integration/EndorsableOffenceRuleIT.java` extending `IntegrationTestBase`; `@BeforeEach void resetWireMock()` calls `REFERENCEDATA_OFFENCE_WIRE_MOCK.resetAll()`; helper `stubEndorsable(String offenceCode, int flag)` stubs `REFERENCEDATA_OFFENCE_WIRE_MOCK` with JSON `{"offences":[{"offenceId":"ref-id","endorsableFlag":<flag>}]}` for query param `cjsoffencecode=<offenceCode>`; nested `@DisplayName("AC1 — endorsable, final non-excluded, no satisfying → warning")` test `givenEndorsableOffenceWithFinalResultAndNoSatisfyingCode_should_returnWarning()`: stub offenceCode `RT88010` with flag 1; POST request with Category-F `IMP` result; assert `$.warnings[?(@.ruleId=='DR-ENDORSEMENT-012')]` has size 1; assert message equals exactly `"This offence is endorsable. Add an endorsement, disqualification or special reasons result."`; assert severity equals `"WARNING"` (covers SC-007)
- [x] T011 [US1] Run `gradle test --tests "*.EndorsableOffenceRuleIT"` for AC1 test — confirm it passes; fix any issues

**Checkpoint**: US1 is fully functional. Run both test classes green. DR-ENDORSEMENT-012 warning fires correctly for AC1.

---

## Phase 4: User Story 2 — Satisfying Result Suppression (Priority: P2)

**Goal**: When any satisfying code (endorsement, disqualification, or special reasons) is present, the warning is NOT returned.

**Independent Test**: Unit tests for satisfying codes from all three families pass; IT for AC2 passes.

### Tests for User Story 2

- [x] T012 [P] [US2] Add `@Nested @DisplayName("AC2 — satisfying result present → no warning")` to `EndorsableOffencePreprocessorTest.java`; add parameterized test `preprocess_endorsableOffenceWithSatisfyingCode_should_returnQualifyingCount0(String satisfyingCode)` with `@ValueSource` covering one code from each family: `LEP` (endorsement), `DDO` (obligatory disq), `DDD` (discretionary disq), `DDP` (totting disq), `DDRCOT` (course reduction), `NESR` (special reasons); stub `getEndorsableFlag` to `Optional.of(1)`; build request with Category-F `IMP` plus the satisfying code; assert `qualifyingCount == 0`
- [x] T013 [P] [US2] Add nested class `@DisplayName("AC2 — satisfying result present → no warning")` IT to `EndorsableOffenceRuleIT.java`; test `givenEndorsableFinalResultWithSatisfyingCode_should_returnNoWarning()`: stub offenceCode `RT88010AC2` flag 1 (distinct code — avoids Caffeine cache cross-contamination with T010); POST with Category-F `IMP` AND `LEP`; assert `$.warnings[?(@.ruleId=='DR-ENDORSEMENT-012')]` is empty

### Verify

- [x] T014 [US2] Run `gradle test --tests "*.EndorsableOffencePreprocessorTest"` and `gradle test --tests "*.EndorsableOffenceRuleIT"` — all US2 tests green

**Checkpoint**: US1 + US2 complete. Warning fires when it should; suppressed when a satisfying code is present.

---

## Phase 5: User Story 3 — Non-Endorsable and Excluded Result Suppression (Priority: P2)

**Goal**: Warning is never returned for non-endorsable offences (AC4) or when only excluded final results are present (AC3).

**Independent Test**: Unit tests for AC3 and AC4; IT for AC4 (non-endorsable offence via WireMock) pass.

### Tests for User Story 3

- [x] T015 [P] [US3] Add `@Nested @DisplayName("AC3 — excluded result only → no warning")` to `EndorsableOffencePreprocessorTest.java`; parameterized test covering at least: `WDRN`, `DISM`, `DISC`, `ERR` (4 representative excluded codes); stub `getEndorsableFlag` to `Optional.of(1)`; build request with only that excluded Category-F result; assert `qualifyingCount == 0`
- [x] T016 [P] [US3] Add `@Nested @DisplayName("AC4 — non-endorsable offence → no warning")` to `EndorsableOffencePreprocessorTest.java`; test `preprocess_nonEndorsableOffence_should_returnQualifyingCount0()`: stub `getEndorsableFlag` to `Optional.of(0)`; any final result; assert `qualifyingCount == 0`; also test `preprocess_endorsableFlagAbsent_should_returnQualifyingCount0()`: stub to `Optional.empty()`; assert `qualifyingCount == 0`
- [x] T017 [US3] Add nested IT `@DisplayName("AC4 — non-endorsable offence → no warning")` to `EndorsableOffenceRuleIT.java`; unique offenceCode `CD98075`; stub flag 0; POST with Category-F `IMP`; assert no DR-ENDORSEMENT-012 warning; add nested IT for AC3: stub flag 1, result `WDRN` (excluded); assert no warning

### Verify

- [x] T018 [US3] Run `gradle test --tests "*.EndorsableOffencePreprocessorTest"` and `gradle test --tests "*.EndorsableOffenceRuleIT"` — all US3 tests green

**Checkpoint**: US1 + US2 + US3 complete. Rule correctly scopes to endorsable offences only and ignores excluded results.

---

## Phase 6: User Story 4 — Multiple Offences Assessed Independently (Priority: P3)

**Goal**: Each offence in a multi-offence hearing is assessed independently; warnings are attributed to the correct offence(s).

**Independent Test**: Unit test with two offences (one endorsable without satisfying, one non-endorsable) returns `qualifyingCount=1` only for the endorsable offence; IT asserts the warning is returned only against the qualifying offence id.

### Tests for User Story 4

- [x] T019 [P] [US4] Add `@Nested @DisplayName("AC6 — multiple offences assessed independently")` to `EndorsableOffencePreprocessorTest.java`; test `preprocess_mixedEndorsableAndNonEndorsableOffences_should_returnWarningOnlyForEndorsable()`: two offences — `off1` (endorsable flag 1, Category-F `IMP`, no satisfying) and `off2` (flag 0, Category-F `IMP`); assert map size 2; `map.get("off1").qualifyingCount() == 1`; `map.get("off2").qualifyingCount() == 0`
- [x] T020 [US4] Add nested IT `@DisplayName("AC6 — multiple offences, warning only on endorsable")` to `EndorsableOffenceRuleIT.java`; stub `off1Code` (unique, flag 1) and `off2Code` (unique, flag 0); POST with two offences, both with Category-F `IMP`; assert exactly one DR-ENDORSEMENT-012 warning and that its `affectedOffenceIds` contains `off1` only

### Verify

- [x] T021 [US4] Run `gradle test --tests "*.EndorsableOffencePreprocessorTest"` and `gradle test --tests "*.EndorsableOffenceRuleIT"` — all US4 tests green

**Checkpoint**: US1–US4 complete. Per-offence independent assessment confirmed.

---

## Phase 7: User Story 5 — Coexistence with Other Validation Warnings (Priority: P3)

**Goal**: DR-ENDORSEMENT-012 warnings appear alongside warnings from other rules without suppression.

**Independent Test**: IT that triggers DR-ENDORSEMENT-012 and one other active rule simultaneously; both warnings present in response.

### Test for User Story 5

- [x] T022 [US5] Add nested IT `@DisplayName("AC7 — coexists with other validation warnings")` to `EndorsableOffenceRuleIT.java`; construct a request that triggers DR-ENDORSEMENT-012 (endorsable offence, Category-F non-excluded result, no satisfying code) AND at least one other active rule (e.g. DR-DISQ-002: use a `relevantOffenceCodes` offence with no extended-test code); assert both rule IDs appear in the warnings list; assert neither warning is absent or has been suppressed

### Verify

- [x] T023 [US5] Run `gradle test --tests "*.EndorsableOffenceRuleIT"` — AC7 test green

**Checkpoint**: All 5 user stories complete and independently verified.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Static analysis, build validation, and spec conformance.

- [x] T024 Run `gradle checkstyleMain` — fix any Google-style violations (no wildcard imports, line length, etc.) in all new/modified files
- [x] T025 Run `gradle pmdMain` — fix any PMD violations in `EndorsableOffencePreprocessor.java`, `EndorsableOffenceContext.java`, `ReferencedataOffenceClient.java`
- [x] T026 Run `gradle test` — full unit + integration test suite green; fix any regressions in existing rules
- [x] T027 Run `gradle build` — full build (Checkstyle + PMD + compile + test) clean

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (YAML)**: No dependencies — start immediately (Principle I: YAML first)
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all preprocessor compilation
- **Phase 3 (US1)**: Depends on Phase 2 — MVP, must complete first
- **Phase 4 (US2)**: Depends on Phase 3 preprocessor implementation
- **Phase 5 (US3)**: Can run in parallel with Phase 4 after Phase 3 is complete
- **Phase 6 (US4)**: Depends on Phase 3 preprocessor implementation
- **Phase 7 (US5)**: Depends on Phase 3 (any working rule trigger)
- **Phase 8 (Polish)**: Depends on all user story phases

### User Story Dependencies

- **US1 (P1)**: No dependency on other stories — deliverable alone as MVP
- **US2 (P2)**: Preprocessor already handles AC2 after US1 implementation; only test tasks
- **US3 (P2)**: Preprocessor already handles AC3/AC4 after US1; only test tasks — parallel with US2
- **US4 (P3)**: Preprocessor already handles multi-offence after US1; only test tasks
- **US5 (P3)**: IT only; depends on at least one other rule being active (DR-DISQ-002 suitable)

### Within Each User Story (TDD Order)

1. Write failing tests — confirm they fail for the correct reason (assertion, not compilation)
2. Implement (or verify existing preprocessor handles the AC)
3. Run tests — confirm they pass
4. Commit before moving to the next story

### Parallel Opportunities

Within Phase 2: T003 and T004 touch different files → can run in parallel
Within Phase 4: T012 (unit) and T013 (IT) touch different files → can run in parallel
Within Phase 5: T015 and T016 (both unit test additions) → can run in parallel

---

## Parallel Example: Phase 2 Foundational

```text
In parallel:
  T003: ReferencedataOffenceResponse.java — add endorsableFlag field
  T004: EndorsableOffenceContext.java — new record
Then sequential:
  T002: PreprocessingDefinition.java — add satisfyingShortCodes
  T005: ReferencedataOffenceClient.java — add getEndorsableFlag() (depends on T003)
```

---

## Implementation Strategy

### MVP (User Story 1 Only)

1. Complete Phase 1 (YAML) — T001
2. Complete Phase 2 (Foundational) — T002–T005
3. Complete Phase 3 (US1) — T006–T011
4. **STOP and VALIDATE**: `gradle test` green, DR-ENDORSEMENT-012 warning fires on AC1
5. Deploy / demo if ready

### Incremental Delivery

- After US1: Core warning fires; non-endorsable and satisfying-code cases handled by preprocessor logic (not yet test-covered but functionally correct)
- After US2: Satisfying code suppression test-verified
- After US3: Non-endorsable and excluded-result suppression test-verified
- After US4: Multi-offence independence test-verified
- After US5: Coexistence confirmed; ready for merge

---

## Notes

- `[P]` tasks modify different files with no in-flight dependencies — safe to parallelize
- `[Story]` label maps each task to its spec.md user story for traceability
- TDD discipline: T006 test must be AUTHORED before T008 implementation (Constitution VIII)
- Never hardcode short-code sets in Java — they live entirely in the YAML
- Use distinct `offenceCode` values per IT test method to avoid Caffeine cache cross-contamination (cache lifetime = Spring context lifetime for ITs)
- `endorsable_flag` absent / non-1 → fail-open (no warning); matches `getCustodialIndicator` precedent
