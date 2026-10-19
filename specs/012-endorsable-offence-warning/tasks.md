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

- [x] T001 Create `src/main/resources/rules/DR-END-012.yaml` with rule id, title, priority 12000, preprocessing type `endorsable-offence`, full `excludedFinalShortCodes` list (24 codes), full `satisfyingShortCodes` list (21 codes), and AC1 CEL condition `qualifyingCount > 0` / severity WARNING / message "This offence is endorsable. Add an endorsement, disqualification or special reasons result." / affectedOffenceSet `endorsableOffenceIds` / validationLevel OFFENCE — see `specs/012-endorsable-offence-warning/plan.md` Appendix for both code lists

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared data structures and reference-data client changes that the preprocessor depends on. Must complete before any preprocessor work.

**⚠️ CRITICAL**: No preprocessor or test implementation can compile until this phase is complete.

- [x] T002 Add `satisfyingShortCodes` (`List<String>`) field to the `PreprocessingDefinition` record in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` — append after `alcoholAbstinenceShortCodes`, before `qualifyingMisCode`; add the section comment `// Endorsable-offence-specific short-code lists`
- [x] T003 [P] Add `Boolean endorsableFlag` field to the `ReferencedataOffenceResponse` record in `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceResponse.java` — append as the last record component before the nested record definitions; `@JsonIgnoreProperties(ignoreUnknown = true)` is already present at class level; Javadoc `@param endorsableFlag` — "boolean — {@code true} when endorsable, {@code false} or absent when not"
- [x] T004 [P] Create `EndorsableOffenceContext` record in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/EndorsableOffenceContext.java` — fields: `String offenceId`, `long qualifyingCount`; implements `RuleEvaluationContext`; `toCelContext()` returns `Map.of("qualifyingCount", qualifyingCount)`; `getOffenceIdSet("endorsableOffenceIds")` returns `List.of(offenceId)` when `qualifyingCount == 1` else `List.of()`; `defendantName()` returns `null`; `allOffenceIds()` returns `List.of(offenceId)`; declare package-private constant `static final String ENDORSABLE_OFFENCE_IDS = "endorsableOffenceIds"`
- [x] T005 Add `getEndorsableFlag(String offenceCode)` method to `ReferencedataOffenceClient` in `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceClient.java` — annotated `@Cacheable(value = "referencedataOffences", key = "'ef:' + #offenceCode", unless = "#result == null")`; returns `Optional<Boolean>`; fails-open (returns `Optional.empty()` on any failure); reads `offence.endorsableFlag()` from response (returns `Optional.empty()` when `endorsableFlag()` is null); cache key prefix `"ef:"` avoids cross-contamination with `"ci:"` (custodial) and bare (misCode) entries

**Checkpoint**: `gradle compileJava` passes cleanly — preprocessor scaffolding can now compile.

---

## Phase 3: User Story 1 — Core Warning Trigger (Priority: P1) 🎯 MVP

**Goal**: When an endorsable offence has a non-excluded final result and no satisfying result, return `WARNING` with the correct message against that offence.

**Independent Test**: `gradle test --tests "*.EndorsableOffencePreprocessorTest"` plus `gradle test --tests "*.EndorsableOffenceRuleIT#*AC1*"` both green.

### Tests for User Story 1 — write and FAIL before implementation

> **Write these tests FIRST. Run `gradle test --tests "*.EndorsableOffencePreprocessorTest"` and confirm COMPILATION FAILURE (class does not exist yet). After T007 (preprocessor stub), confirm ASSERTION FAILURE, not compilation error.**

- [x] T006 [US1] Create `src/test/java/uk/gov/hmcts/cp/services/rules/cel/EndorsableOffencePreprocessorTest.java` with `@ExtendWith(MockitoExtension.class)`; mock `ReferencedataOffenceClient`; nested class `@Nested @DisplayName("AC1 — endorsable offence, final result, no satisfying result")` with test method `preprocess_endorsableOffenceWithFinalResultAndNoSatisfyingCode_should_returnQualifyingCount1()` — stub `getEndorsableFlag("RT88010")` to return `Optional.of(true)`; build request with one Category-F result `IMP` (non-excluded) and no satisfying code; assert `qualifyingCount == 1`
- [x] T007 [US1] Create minimal stub `EndorsableOffencePreprocessor` in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/EndorsableOffencePreprocessor.java` — `@Slf4j @Component @RequiredArgsConstructor`; `type()` returns `"endorsable-offence"`; `preprocess()` returns empty map — verify the T006 test now FAILS for the CORRECT REASON (assertion `qualifyingCount != 1`, not a compilation error)

### Implementation for User Story 1

- [x] T008 [US1] Implement full `EndorsableOffencePreprocessor.preprocess()` in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/EndorsableOffencePreprocessor.java`: inject `ReferencedataOffenceClient`; for each offence call `getEndorsableFlag(offenceCode)`; check `Boolean.TRUE.equals(endorsableFlag.orElse(null))` — if false emit `qualifyingCount=0`; group result lines by offence via `PreprocessorHelper.groupResultsByOffence`; filter Category-F lines; check any non-excluded final exists (`hasUpperCode` against `upperSet(config.excludedFinalShortCodes())`); check any satisfying code across ALL lines (`anyShortCodeIn` against `upperSet(config.satisfyingShortCodes())`); emit `qualifyingCount = 1` only when endorsable AND non-excluded final present AND no satisfying; all short-code comparisons case-insensitive via `upperSet` / `hasUpperCode` / `anyShortCodeIn` from `PreprocessorHelper`
- [x] T009 [US1] Run `gradle test --tests "*.EndorsableOffencePreprocessorTest"` — confirm all US1 unit tests pass; fix any issues before proceeding
- [x] T010 [US1] Create `src/test/java/uk/gov/hmcts/cp/integration/EndorsableOffenceRuleIT.java` extending `IntegrationTestBase`; `@BeforeEach void resetWireMock()` calls `REFERENCEDATA_OFFENCE_WIRE_MOCK.resetAll()`; helper `stubEndorsable(String offenceCode, boolean flag)` stubs `REFERENCEDATA_OFFENCE_WIRE_MOCK` with JSON `{"offences":[{"offenceId":"ref-id","endorsableFlag":<flag>}]}` for query param `cjsoffencecode=<offenceCode>` (Java string-concatenates boolean as `true`/`false` automatically); nested `@DisplayName("AC1 — endorsable, final non-excluded, no satisfying → warning")` test `givenEndorsableOffenceWithFinalResultAndNoSatisfyingCode_should_returnWarning()`: stub offenceCode `RT88010` with `true`; POST request with Category-F `IMP` result; assert `$.warnings[?(@.ruleId=='DR-END-012')]` has size 1; assert message equals exactly `"This offence is endorsable. Add an endorsement, disqualification or special reasons result."`; assert severity equals `"WARNING"` (covers SC-007)
- [x] T011 [US1] Run `gradle test --tests "*.EndorsableOffenceRuleIT"` for AC1 test — confirm it passes; fix any issues

**Checkpoint**: US1 is fully functional. Run both test classes green. DR-END-012 warning fires correctly for AC1.

---

## Phase 4: User Story 2 — Satisfying Result Suppression (Priority: P2)

**Goal**: When any satisfying code (endorsement, disqualification, or special reasons) is present, the warning is NOT returned.

**Independent Test**: Unit tests for satisfying codes from all three families pass; IT for AC2 passes.

### Tests for User Story 2

- [x] T012 [P] [US2] Add `@Nested @DisplayName("AC2 — satisfying result present → no warning")` to `EndorsableOffencePreprocessorTest.java`; add parameterized test `preprocess_endorsableOffenceWithSatisfyingCode_should_returnQualifyingCount0(String satisfyingCode)` with `@ValueSource` covering one code from each family: `LEP` (endorsement), `DDO` (obligatory disq), `DDD` (discretionary disq), `DDP` (totting disq), `DDRCOT` (course reduction), `NESR` (special reasons); stub `getEndorsableFlag` to `Optional.of(true)`; build request with Category-F `IMP` plus the satisfying code; assert `qualifyingCount == 0`
- [x] T013 [P] [US2] Add nested class `@DisplayName("AC2 — satisfying result present → no warning")` IT to `EndorsableOffenceRuleIT.java`; test `givenEndorsableFinalResultWithSatisfyingCode_should_returnNoWarning()`: stub offenceCode `RT88010AC2` flag `true` (distinct code — avoids Caffeine cache cross-contamination with T010); POST with Category-F `IMP` AND `LEP`; assert `$.warnings[?(@.ruleId=='DR-END-012')]` is empty

### Verify

- [x] T014 [US2] Run `gradle test --tests "*.EndorsableOffencePreprocessorTest"` and `gradle test --tests "*.EndorsableOffenceRuleIT"` — all US2 tests green

**Checkpoint**: US1 + US2 complete. Warning fires when it should; suppressed when a satisfying code is present.

---

## Phase 5: User Story 3 — Non-Endorsable and Excluded Result Suppression (Priority: P2)

**Goal**: Warning is never returned for non-endorsable offences (AC4) or when only excluded final results are present (AC3).

**Independent Test**: Unit tests for AC3 and AC4; IT for AC4 (non-endorsable offence via WireMock) pass.

### Tests for User Story 3

- [x] T015 [P] [US3] Add `@Nested @DisplayName("AC3 — excluded result only → no warning")` to `EndorsableOffencePreprocessorTest.java`; parameterized test covering at least: `WDRN`, `DISM`, `DISC`, `ERR` (4 representative excluded codes); stub `getEndorsableFlag` to `Optional.of(true)`; build request with only that excluded Category-F result; assert `qualifyingCount == 0`
- [x] T016 [P] [US3] Add `@Nested @DisplayName("AC4 — non-endorsable offence → no warning")` to `EndorsableOffencePreprocessorTest.java`; test `preprocess_nonEndorsableOffence_should_returnQualifyingCount0()`: stub `getEndorsableFlag` to `Optional.of(false)`; any final result; assert `qualifyingCount == 0`; also test `preprocess_endorsableFlagAbsent_should_returnQualifyingCount0()`: stub to `Optional.empty()`; assert `qualifyingCount == 0`
- [x] T017 [US3] Add nested IT `@DisplayName("AC4 — non-endorsable offence → no warning")` to `EndorsableOffenceRuleIT.java`; unique offenceCode `CD98075`; stub flag `false`; POST with Category-F `IMP`; assert no DR-END-012 warning; add nested IT for AC3: stub flag `true`, result `WDRN` (excluded); assert no warning

### Verify

- [x] T018 [US3] Run `gradle test --tests "*.EndorsableOffencePreprocessorTest"` and `gradle test --tests "*.EndorsableOffenceRuleIT"` — all US3 tests green

**Checkpoint**: US1 + US2 + US3 complete. Rule correctly scopes to endorsable offences only and ignores excluded results.

---

## Phase 6: User Story 4 — Multiple Offences Assessed Independently (Priority: P3)

**Goal**: Each offence in a multi-offence hearing is assessed independently; warnings are attributed to the correct offence(s).

**Independent Test**: Unit test with two offences (one endorsable without satisfying, one non-endorsable) returns `qualifyingCount=1` only for the endorsable offence; IT asserts the warning is returned only against the qualifying offence id.

### Tests for User Story 4

- [x] T019 [P] [US4] Add `@Nested @DisplayName("AC6 — multiple offences assessed independently")` to `EndorsableOffencePreprocessorTest.java`; test `preprocess_mixedEndorsableAndNonEndorsableOffences_should_returnWarningOnlyForEndorsable()`: two offences — `off1` (endorsable flag `true`, Category-F `IMP`, no satisfying) and `off2` (flag `false`, Category-F `IMP`); assert map size 2; `map.get("off1").qualifyingCount() == 1`; `map.get("off2").qualifyingCount() == 0`
- [x] T020 [US4] Add nested IT `@DisplayName("AC6 — multiple offences, warning only on endorsable")` to `EndorsableOffenceRuleIT.java`; stub `end-ac6-end` flag `true` and `end-ac6-non` flag `false`; POST with two offences, both with Category-F `IMP`; assert exactly one DR-END-012 warning and that its `affectedOffences[0].offenceId` is `off1`

### Verify

- [x] T021 [US4] Run `gradle test --tests "*.EndorsableOffencePreprocessorTest"` and `gradle test --tests "*.EndorsableOffenceRuleIT"` — all US4 tests green

**Checkpoint**: US1–US4 complete. Per-offence independent assessment confirmed.

---

## Phase 7: User Story 5 — Coexistence with Other Validation Warnings (Priority: P3)

**Goal**: DR-END-012 warnings appear alongside warnings from other rules without suppression.

**Independent Test**: IT that triggers DR-END-012 and one other active rule simultaneously; both warnings present in response.

### Test for User Story 5

- [x] T022 [US5] Add nested IT `@DisplayName("AC7 — coexists with other validation warnings")` to `EndorsableOffenceRuleIT.java`; construct a request that triggers DR-END-012 (endorsable offence flag `true`, Category-F non-excluded result, no satisfying code) AND DR-SENT-001 (two concurrent-only IMP results for a second defendant — no primary sentence); assert `$.warnings[?(@.ruleId=='DR-END-012')]` has size 1 AND `$.warnings[?(@.ruleId=='DR-SENT-001')]` has size 1; assert neither warning is absent or suppressed

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

## Phase 9: IT Test Gap Remediation (identified 2026-10-12)

**Purpose**: Two gaps found during v2-PDF AC review — AC5 has no explicit IT class; AC4 "flag absent" test was stubbing a `false` value instead of a truly absent field. Both fixed for full AC→IT traceability per the plan.md IT Test Coverage Matrix.

**Independent Test**: `gradle test --tests "*.EndorsableOffenceRuleIT"` green with the two new/fixed tests.

### AC4 absent-flag stub fix

- [x] T028 [US3] In `src/test/java/uk/gov/hmcts/cp/integration/EndorsableOffenceRuleIT.java` add a second private helper `stubEndorsableAbsent(String offenceCode)` that stubs with JSON `{"offences":[{"offenceId":"ref-id"}]}` (no `endorsableFlag` field); update `givenEndorsableFlagAbsent_should_returnNoWarning` inside `Ac4NonEndorsable` to call `stubEndorsableAbsent("end-ac4-absent")` so the field is genuinely absent rather than `false`

### AC5 explicit IT class

- [x] T029 [US2] In `src/test/java/uk/gov/hmcts/cp/integration/EndorsableOffenceRuleIT.java` add `Ac5SatisfyingResultClears` nested class immediately after `Ac4NonEndorsable` with test `givenEndorsableOffenceWithFinalAndSatisfyingResult_should_returnNoWarning()`: stub `"end-ac5-clr"` flag `true`, POST with `IMP` + `DDO`, assert no DR-END-012 warning

### Verify

- [x] T030 Run `gradle test --tests "*.EndorsableOffenceRuleIT"` — `Ac5SatisfyingResultClears` and fixed `Ac4NonEndorsable.givenEndorsableFlagAbsent` both pass
- [x] T031 Run `gradle build` — full build clean with no regressions

**Checkpoint**: All 7 Jira ACs (AC1–AC7) have a named, dedicated `@Nested` class in `EndorsableOffenceRuleIT`. IT Test Coverage Matrix in plan.md is fully satisfied.

---

## Phase 10: Liquibase Migration Seed

**Purpose**: Seed the `DR-END-012` row into the `validation_rule` override table so `ValidationRulesControllerIntegrationTest` and the rules-list API return all 9 rules (including DR-END-012).

- [x] T032 Create `src/main/resources/db/migration/V1.013__insert_dr_endorsement_012.sql` — single INSERT: `INSERT INTO validation_rule (id, enabled, severity) VALUES ('DR-END-012', true, 'WARNING');`

### Verify

- [x] T033 Run `gradle clean build` — full build green including Flyway migration, `ValidationRulesControllerIntegrationTest.list_rules_should_return_ok_with_rules()` (9 rules, DR-END-012 last in priority order), all ACs in `EndorsableOffenceRuleIT` pass

**Checkpoint**: `endorsable_flag` is consistently Boolean throughout. JSON stubs return `true`/`false`. Preprocessor checks `Boolean.TRUE.equals(endorsableFlag.orElse(null))`. All tests green. Full build passes.

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
- **Phase 9 (IT Gap Remediation)**: Depends on Phase 8 — additive test-only changes; no production code changes
- **Phase 10 (Migration Seed)**: Depends on Phase 9 — DB seed row for rules-list API

### User Story Dependencies

- **US1 (P1)**: No dependency on other stories — deliverable alone as MVP
- **US2 (P2)**: Preprocessor already handles AC2 after US1 implementation; only test tasks
- **US3 (P2)**: Preprocessor already handles AC3/AC4 after US1; only test tasks — parallel with US2
- **US4 (P3)**: Preprocessor already handles multi-offence after US1; only test tasks
- **US5 (P3)**: IT only; depends on at least one other rule being active (DR-SENT-001 used)

### Within Each User Story (TDD Order)

1. Write failing tests — confirm they fail for the correct reason (assertion, not compilation)
2. Implement (or verify existing preprocessor handles the AC)
3. Run tests — confirm they pass
4. Commit before moving to the next story

### Parallel Opportunities

- Within Phase 2: T003 and T004 touch different files → can run in parallel
- Within Phase 4: T012 (unit) and T013 (IT) touch different files → can run in parallel
- Within Phase 5: T015 and T016 (both unit test additions) → can run in parallel
- Within Phase 9: T028 and T029 touch the same file → must run sequentially

---

## Parallel Example: Phase 3 US1

```text
In parallel (different files):
  T006: EndorsableOffencePreprocessorTest.java  — AC1 unit test (stub Optional.of(true))
  T004: EndorsableOffenceContext.java            — context record

Then sequential:
  T007: EndorsableOffencePreprocessor.java (stub) — verify T006 fails for assertion reason
  T008: EndorsableOffencePreprocessor.java (impl) — Boolean.TRUE.equals(endorsableFlag.orElse(null))
  T010: EndorsableOffenceRuleIT.java AC1          — IT stub uses boolean flag=true
  T011: gradle test --tests "*.EndorsableOffenceRuleIT"
```

---

## Implementation Strategy

### MVP (User Story 1 Only)

1. Complete Phase 1 (YAML) — T001
2. Complete Phase 2 (Foundational) — T002–T005
3. Complete Phase 3 (US1) — T006–T011
4. **STOP and VALIDATE**: `gradle test` green, DR-END-012 warning fires on AC1
5. Deploy / demo if ready

### Incremental Delivery

- After US1: Core warning fires; non-endorsable and satisfying-code cases handled by preprocessor logic (not yet test-covered but functionally correct)
- After US2: Satisfying code suppression test-verified
- After US3: Non-endorsable and excluded-result suppression test-verified
- After US4: Multi-offence independence test-verified
- After US5: Coexistence confirmed; ready for merge
- After Phase 9: All 7 Jira ACs have dedicated IT classes; full AC traceability
- After Phase 10: Flyway seed in place; rules-list API returns 9 rules correctly

---

## Notes

- `endorsableFlag` is `Boolean` (nullable) in `ReferencedataOffenceResponse` — `true` = endorsable, `false` = non-endorsable, `null` = absent
- Preprocessor condition: `Boolean.TRUE.equals(endorsableFlag.orElse(null))` — fail-open when flag is `null`/absent
- WireMock stubs use JSON boolean literals (`true`/`false`), never integers (`1`/`0`)
- `[P]` tasks modify different files with no in-flight dependencies — safe to parallelize
- `[Story]` label maps each task to its spec.md user story for traceability
- TDD discipline: T006 test must be AUTHORED before T008 implementation (Constitution VIII)
- Never hardcode short-code sets in Java — they live entirely in the YAML
- Use distinct `offenceCode` values per IT test method to avoid Caffeine cache cross-contamination (cache lifetime = Spring context lifetime for ITs)
- `endorsable_flag` absent / non-TRUE → fail-open (no warning)
- Flyway migration is V1.013 (slots V1.010–V1.012 reserved for branches merging before this one)
