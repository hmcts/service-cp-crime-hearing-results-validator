# Tasks: YRO Curfew Requirement Duration Validation (DR-YRO-001 extension)

**Branch**: `dev/DD-42850-YRO-Duration`
**Input**: Design documents from `specs/003-yro-curfew-duration-validation/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

**TDD is mandatory** (Constitution Principle VIII). Every test task MUST be written and confirmed
failing before the corresponding implementation task begins.

**YAML-first** (Constitution Principle I). `DR-YRO-001.yaml` conditions are authored before any
Java change.

This feature extends the already-shipped `DR-YRO-001` rule (`YouthRehabilitationPreprocessor`,
`YouthRehabilitationContext`) with two new conditions (`DUR-YRC2`, `DUR-YRC1`), mirroring the
already-shipped Community Order requirement-duration validation (Jira `DD-41655`,
`DR-COEW-001` conditions `DUR-CUR`/`DUR-CURE`) in the sibling repo. See plan.md/research.md/data-model.md
for full design rationale.

---

## Phase 1: Setup Baseline

**Purpose**: Confirm the build is green before any changes land so regressions are detectable immediately.

- [ ] T001 Run `gradle clean test` and confirm BUILD SUCCESSFUL with 3 rules (`DR-SENT-002`, `DR-DISQ-001`, `DR-YRO-001`) before any code changes

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared framework primitives that every new duration-mismatch condition needs — the
`${calculatedEndDate}` message-template mechanism, the YAML contract for the two new conditions, and
a shared period-parsing helper. Neither US1 nor US2 can emit a correctly-worded error without these
landing first.

⚠️ **CRITICAL**: After T002 lands, `curDurationMismatchCount`/`cureDurationMismatchCount` are
referenced by CEL expressions in YAML but do not yet exist on
`YouthRehabilitationContext.toCelContext()` — the CEL evaluator will throw on
`DUR-YRC2`/`DUR-YRC1`. This is the intended TDD signal; it is not fixed until Phase 3/4.

- [ ] T002 Append 2 new conditions (`DUR-YRC2`, `DUR-YRC1`) to `src/main/resources/rules/DR-YRO-001.yaml` with ERROR severity, `validationLevel: OFFENCE`, and the exact `messageTemplate`/`errorMessageTemplate`/`affectedOffenceSet`/`calculatedValueSet` values from data-model.md
- [ ] T003 [P] Add `calculatedValueSet` field (`String`) to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionDefinition.java` following the existing Lombok `@Data @Builder` pattern
- [ ] T004 [P] Add a default method `getCalculatedValue(String setName, String offenceId)` to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/RuleEvaluationContext.java` that throws `IllegalArgumentException("Unknown calculated-value set: " + setName)`, mirroring the existing `getOffenceIdSet` default-throw pattern
- [ ] T005 [P] Write `resolve_should_replace_extraPlaceholder_tokens` and `resolve_should_leave_other_placeholders_unaffected_when_extraPlaceholders_used` test cases in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/MessageTemplateResolverTest.java` for a new 6-arg `resolve(...)` overload taking `Map<String, String> extraPlaceholders` — run `gradle test --tests "*.MessageTemplateResolverTest"` and confirm compilation failure (overload does not exist yet)
- [ ] T006 Implement the 6-arg `resolve(...)` overload in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/MessageTemplateResolver.java`: delegates to the existing 5-arg overload, then replaces `${key}` for each `extraPlaceholders` entry — run T005 tests and confirm they pass; confirm all pre-existing `MessageTemplateResolverTest` cases still pass unmodified
- [ ] T007 [P] Write `parsePromptPeriod_should_parse_bare_integer_as_days`, `_should_parse_days_weeks_and_months_suffixes`, `_should_default_unrecognised_unit_to_days`, `_should_return_null_and_warn_for_blank_value`, and `_should_return_null_and_warn_for_non_numeric_value` test cases in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessorHelperTest.java` — run and confirm compilation failure (method does not exist yet)
- [ ] T008 Implement `parsePromptPeriod(ResultLineDto, String promptRef, String offenceId)` and a private `record ParsedPeriod(long amount, ChronoUnit unit)` in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessorHelper.java`, matching pattern `^(\d+)\s*(Days?|Weeks?|Months?)$` (case-insensitive, default `DAYS` for a bare integer), following the same null/blank/unparseable → `WARN`-and-return-null semantics as the existing `parsePromptDate` — run T007 tests and confirm they pass
- [ ] T009 Write `loadFromYaml_should_parse_dr_yro_001_duration_conditions_with_calculatedValueSet` in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/RuleDefinitionTest.java` asserting `DR-YRO-001.yaml` (after T002) loads 5 conditions total and that `DUR-YRC2`/`DUR-YRC1` each parse `calculatedValueSet` to the expected set name — run and confirm it passes against T002's YAML
- [ ] T010 Update the OFFENCE-level branch of `evaluate()` in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRule.java`: in the per-offence message lambda, when `condition.getCalculatedValueSet() != null`, build `Map.of("calculatedEndDate", context.getCalculatedValue(condition.getCalculatedValueSet(), id))` (via a small `calculatedValuePlaceholder(String)` helper returning `Map.of()` when the value is `null`) and pass it to the new 6-arg `resolve(...)`; when `null`, call the existing 5-arg `resolve(...)` unchanged — confirm existing `CelValidationRuleTest`/`CelValidationRuleScenarioTest`/`CelValidationRuleOverrideTest` suites remain green (no `calculatedValueSet` is configured on any pre-existing condition across all three rules, so behaviour must be identical for AC2a/b/c, DR-SENT-002, and DR-DISQ-001)

**Checkpoint**: T002–T010 complete. `gradle test --tests "*.MessageTemplateResolverTest" --tests "*.PreprocessorHelperTest" --tests "*.RuleDefinitionTest" --tests "*.CelValidationRuleTest" --tests "*.CelValidationRuleScenarioTest" --tests "*.CelValidationRuleOverrideTest"` all green. `ValidationRuleAutoConfigurationTest`'s rule-count assertion is unaffected (still 3 rules — only `DR-YRO-001`'s condition count changes). Full `gradle test` will still show `YouthRehabilitationContext` missing the 2 new CEL variables — expected red until Phase 3/4.

---

## Phase 3: User Story 1 — Curfew (YRC2) Requirement Duration Mismatch (Priority: P1) 🎯 MVP

**Goal**: Detect when a YRC2 requirement's "End date" does not equal "Start date" + "Curfew period"
− 1 day, independent of the existing order-end-date (AC2) check, and emit one ERROR per affected
offence with the correctly calculated end date inline.

**Independent Test**: POST a YROEW result with a YRC2 child result whose `startDate`/`curfewPeriod`/`endDate`
prompts don't satisfy the duration formula → response contains an error with `ruleId: "DR-YRO-001"`,
condition `DUR-YRC2`, an inline message containing the correct calculated date (`dd/MM/yyyy`), and a
top-summary message matching spec FR-003 exactly.

> **Write tests FIRST — confirm they FAIL before implementation (T011, T013)**

### Tests for User Story 1

- [ ] T011 [P] [US1] Extend `src/test/java/uk/gov/hmcts/cp/services/rules/cel/YouthRehabilitationContextTest.java`: `toCelContext()` includes `curDurationMismatchCount`; `getOffenceIdSet("curDurationMismatchOffenceIds")` returns the correct list; `getCalculatedValue("curCalculatedEndDateByOffenceId", offenceId)` returns the expected `dd/MM/yyyy` string and throws `IllegalArgumentException` for an unknown set name — run and confirm compilation failure (fields don't exist yet)
- [ ] T013 [P] [US1] Extend `src/test/java/uk/gov/hmcts/cp/services/rules/cel/YouthRehabilitationPreprocessorTest.java` with YRC2 duration scenarios: end date equals Start date + period − 1 → no violation; end date one day early → violation with `curCalculatedEndDateByOffenceId` set to the correct `dd/MM/yyyy` date; end date one day late (clerk forgot to subtract 1 day) → violation; missing/unparseable `startDate`, `curfewPeriod`, or `endDate` → skip gracefully, `WARN` logged, no violation; the duration check still runs when the YRO's own order end date prompt is missing/unparseable (must NOT be gated by the existing AC2 `orderEndDate == null` early-continue); a period value with a unit suffix (e.g. `"3 Weeks"`, `"1 Months"` across a month-end boundary) computes the calendar-correct expected date — run and confirm failure (no production logic yet)

### Implementation for User Story 1

- [ ] T012 [US1] Add fields `curDurationMismatchCount: long`, `curDurationMismatchOffenceIds: List<String>`, `curCalculatedEndDateByOffenceId: Map<String, String>` to the `YouthRehabilitationContext` record in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/YouthRehabilitationContext.java`; add the new CEL entry to `toCelContext()`; add the new case to `getOffenceIdSet()`; implement `getCalculatedValue(setName, offenceId)` with a switch over `"curCalculatedEndDateByOffenceId"` (throw for unknown names) — run T011 tests and confirm they pass
- [ ] T014 [US1] In `src/main/java/uk/gov/hmcts/cp/services/rules/cel/YouthRehabilitationPreprocessor.java`: add prompt-ref constants `PROMPT_START_DATE = "startDate"` and `PROMPT_CURFEW_PERIOD = "curfewPeriod"`; add an unconditional (not gated by the AC2 `orderEndDate == null` continue) per-offence YRC2 duration check using `PreprocessorHelper.parsePromptDate`/`parsePromptPeriod` — compute `expected = startDate.plus(period.amount(), period.unit()).minusDays(1)` (formatted `dd/MM/yyyy`), and if `!endDate.isEqual(expected)` add the offence to `curDurationMismatchOffenceIds` and record `expected` in `curCalculatedEndDateByOffenceId` — run T013 tests and confirm all pass; re-run the existing `YouthRehabilitationPreprocessorTest` AC2 nested classes (`Ac2aYrc2`, `Ac2AllTypesSimultaneously`, etc.) in full to confirm zero regressions from the loop change

### Integration test for User Story 1

- [ ] T015 [US1] Add a nested class to `src/test/java/uk/gov/hmcts/cp/integration/YroEndDateValidationIntegrationTest.java` covering: a YRC2 duration mismatch on a YROEW result blocks navigation (`isValid: false`), the `DR-YRO-001`/`DUR-YRC2` error's offence message contains the correct calculated date, `errorMessages[]` contains the exact FR-003 summary text with the affected defendant name, and a YRC2 result whose end date exactly matches the formula produces no error — run and confirm pass

**Checkpoint**: `gradle test --tests "*.YouthRehabilitationContextTest" --tests "*.YouthRehabilitationPreprocessorTest" --tests "*.YroEndDateValidationIntegrationTest"` green — YRC2 duration mismatch detected end-to-end (`DUR-YRC2` fires), existing AC2a/b/c scenarios unaffected. **MVP deliverable.**

---

## Phase 4: User Story 2 — Curfew with Electronic Monitoring (YRC1) Requirement Duration Mismatch (Priority: P1)

**Goal**: Detect when a YRC1 requirement's "End date of tagging" does not equal "Start date of
tagging" + "Curfew and electronic monitoring period" − 1 day.

**Independent Test**: POST a YRONI result with a YRC1 child result whose
`startDateOfTagging`/`curfewAndElectronicMonitoringPeriod`/`endDateOfTagging` prompts don't satisfy
the duration formula → response contains an error with condition `DUR-YRC1` and the correct
calculated date inline.

> **Write tests FIRST — confirm they FAIL before implementation (T016, T018)**

### Tests for User Story 2

- [ ] T016 [P] [US2] Extend `YouthRehabilitationContextTest.java`: `toCelContext()` includes `cureDurationMismatchCount`; `getOffenceIdSet("cureDurationMismatchOffenceIds")`; `getCalculatedValue("cureCalculatedEndDateByOffenceId", offenceId)` — run and confirm compilation failure
- [ ] T018 [P] [US2] Extend `YouthRehabilitationPreprocessorTest.java` with YRC1 duration scenarios mirroring T013 but using `startDateOfTagging`/`curfewAndElectronicMonitoringPeriod`/`endDateOfTagging` — run and confirm failure

### Implementation for User Story 2

- [ ] T017 [US2] Add `cureDurationMismatchCount`, `cureDurationMismatchOffenceIds`, `cureCalculatedEndDateByOffenceId` to `YouthRehabilitationContext.java` (same shape as T012); extend `getCalculatedValue()`'s switch with the new case — run T016 tests and confirm they pass
- [ ] T019 [US2] In `YouthRehabilitationPreprocessor.java`: add prompt-ref constants `PROMPT_START_DATE_OF_TAGGING = "startDateOfTagging"` and `PROMPT_CURFEW_TAG_PERIOD = "curfewAndElectronicMonitoringPeriod"`; add the YRC1 duration-check branch (same shape as T014, reusing `parsePromptPeriod`) — run T018 tests and confirm all pass; re-run US1 unit and integration tests to confirm zero regressions

### Integration test for User Story 2

- [ ] T020 [US2] Add a nested class to `YroEndDateValidationIntegrationTest.java` covering: a YRC1 duration mismatch on a YRONI result blocks navigation, the `DUR-YRC1` error's offence message contains the correct calculated date, and a YRC1 result whose end date of tagging exactly matches the formula produces no error — run and confirm pass; re-run the full integration suite to confirm zero regressions on US1 and the existing AC2 scenarios

**Checkpoint**: YRC1 duration mismatch (`DUR-YRC1`) detected end-to-end; US1 and existing AC2a/b/c
scenarios unaffected.

---

## Phase 5: User Story 3 — Duration-Mismatch Errors Combine With Other Validation Errors (Priority: P2)

**Goal**: Confirm the two new duration-mismatch conditions integrate correctly with the existing
error-summary/inline-error mechanism and with the existing order-end-date check — verification only,
no new production behaviour (per spec FR-006).

**Independent Test**: POST a hearing that triggers a YRC2 duration mismatch on one defendant and a
YRC1 duration mismatch on another, and a hearing where one offence fails both the existing AC2 check
and a `DUR-YRC2` duration-mismatch check → all/both errors appear correctly scoped.

> **Tests FIRST — confirm they FAIL if response aggregation is wrong (T021, T022)**

- [ ] T021 [P] [US3] Add a nested class `DurationMismatchCombinedWithEachOther` to `YroEndDateValidationIntegrationTest.java`: one defendant with a YRC2 duration mismatch and another with a YRC1 duration mismatch in the same hearing → response contains both errors, each with its own correct affected-offence/defendant scoping, and `isValid: false`
- [ ] T022 [P] [US3] Add a nested class `DurationMismatchCombinedWithOrderEndDateCheck` to the same integration test file: one offence where the YRC2 requirement fails both the existing AC2a order-end-date check and the new `DUR-YRC2` duration-mismatch check simultaneously → response contains two separate errors (`AC2a` and `DUR-YRC2`) both referencing that offence, per spec FR-007 and User Story 3 Scenario 2

**Checkpoint**: All User Story 3 scenarios pass; no change to any existing AC2a/b/c scenario's
pass/fail outcome (spec SC-005).

---

## Phase 6: Polish & Quality Gates

**Purpose**: Final quality gate and live-HTTP API test coverage for the full feature.

- [ ] T023 Run `gradle checkstyleMain` and fix all violations in files touched by this feature: `ConditionDefinition.java`, `RuleEvaluationContext.java`, `MessageTemplateResolver.java`, `PreprocessorHelper.java`, `CelValidationRule.java`, `YouthRehabilitationContext.java`, `YouthRehabilitationPreprocessor.java`
- [ ] T024 [P] Run `gradle pmdMain` and fix all violations in the same files
- [ ] T025 [P] Verify no `System.out` / `System.err` / `printStackTrace` in the new/modified code (grep check)
- [ ] T026 Run `gradle clean build` — confirm BUILD SUCCESSFUL, still 3 rules discovered (`DR-SENT-002`, `DR-DISQ-001`, `DR-YRO-001`), `DR-YRO-001` now has 5 conditions total, all tests passing
- [ ] T027 [P] Run `gradle jacocoTestReport` and verify the new duration-mismatch branches (equal, early, late, missing-field skip paths, unit-suffix period parsing) have meaningful coverage
- [ ] T028 Add `src/apiTest/java/uk/gov/hmcts/cp/http/YroCurfewDurationApiHttpLiveTest.java`, mirroring `YroEndDateApiHttpLiveTest.java`'s pattern (JDBC toggle of the `validation_rule` row for `DR-YRO-001` with a 2-second sleep either side for the Caffeine cache TTL, `RestTemplate` POSTs against the docker-compose stack): cover a YRC2 duration mismatch, a YRC1 duration mismatch, the exact match (no-error) case for each, and the combined AC2+duration case — run `gradle api` and confirm all pass
- [ ] T029 Verify the four new hardcoded prompt-ref keys (`startDate`, `curfewPeriod`, `startDateOfTagging`, `curfewAndElectronicMonitoringPeriod`) against the real upstream `api-cp-crime-hearing-results-validator` schema/Swagger or a real dev-environment payload (research.md Decision 2 / plan.md Risk Register) — update the hardcoded constants and re-run T013/T018/T028 if any name differs
- [ ] T030 Run through `quickstart.md` manually against a local `gradle bootRun` instance to confirm the documented curl example produces the documented response

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup Baseline)
    └── Phase 2 (Foundational: calculatedEndDate mechanism + YAML + parsePromptPeriod)  [BLOCKS ALL]
            ├── Phase 3 (US1 — YRC2 duration)   ← MVP deliverable
            │       └── Phase 4 (US2 — YRC1 duration)   [same 2 files, sequential after US1]
            │               └── Phase 5 (US3 — combined display, verification only)
            └── Phase 6 (Polish)      ← after Phases 3–5 complete
```

### Within Each Phase (TDD order)

```
Test task (red) → Implementation task (green) → Integration test (green) → next phase
```

### User Story Dependencies

- **US1 (YRC2 duration)**: Depends on Phase 2 (foundational framework additions). Smallest
  independently-valuable increment — MVP.
- **US2 (YRC1 duration)**: Depends on US1 — the same two files (`YouthRehabilitationContext.java`,
  `YouthRehabilitationPreprocessor.java`) are extended again, so it runs sequentially after US1
  lands, not in parallel.
- **US3 (combined display)**: Depends on US1 + US2 both complete (needs both condition types
  available to exercise combinations) and on the existing AC2 checks (for the AC2+duration combined
  scenario).

### Task-Level Dependencies

| Task | Depends On |
|------|-----------|
| T003, T004 | T002 |
| T005 | — (can start immediately, different file from T002–T004) |
| T006 | T005 (must see red first) |
| T007 | — (can start immediately, different file) |
| T008 | T007 (must see red first) |
| T009 | T002, T003 |
| T010 | T004, T006 |
| T011 | T009, T010 (Phase 2 complete) |
| T013 | T008, T011 |
| T012 | T011 (must see red first) |
| T014 | T012, T013 (must see red first) |
| T015 | T014 |
| T016 | T014 |
| T018 | T016 |
| T017 | T016 (must see red first) |
| T019 | T017, T018 (must see red first) |
| T020 | T019 |
| T021, T022 | T015, T020 |
| T023–T030 | T021, T022 |

### Parallel Opportunities

- T003 and T004 can run in parallel (different files); T005 and T007 can start alongside them
  (independent test files, no shared dependency until their respective implementation tasks)
- T011 (context test) can be written in parallel with T013 (preprocessor test) — both red until
  T012/T014 land
- T016 (context test) can be written in parallel with T018 (preprocessor test) — both red until
  T017/T019 land
- T021 and T022 can run in parallel (different nested test classes in the same file — coordinate to
  avoid merge conflicts, or land sequentially)
- T024, T025, T027 can all run in parallel in Phase 6
- **Not parallel**: US2's implementation tasks (T017/T019) cannot run in parallel with US1's
  (T012/T014) — both requirement types share the same two files
  (`YouthRehabilitationContext.java`, `YouthRehabilitationPreprocessor.java`), so concurrent edits
  would conflict. Their *test* tasks (T011/T016) can be drafted in parallel even though
  implementation must land sequentially.

---

## Parallel Example: Phase 2 (Foundational)

```
# After T002 lands, run in parallel:
T003: Add calculatedValueSet field       (src/main/.../ConditionDefinition.java)
T004: Add getCalculatedValue default     (src/main/.../RuleEvaluationContext.java)
T005: Write MessageTemplateResolverTest  (src/test/.../MessageTemplateResolverTest.java)
T007: Write PreprocessorHelperTest       (src/test/.../PreprocessorHelperTest.java)

# After T005 red confirmed:
T006: Implement 6-arg resolve overload   (src/main/.../MessageTemplateResolver.java)

# After T007 red confirmed:
T008: Implement parsePromptPeriod        (src/main/.../PreprocessorHelper.java)

# After T003 lands:
T009: Write RuleDefinitionTest case      (src/test/.../RuleDefinitionTest.java)

# After T004 + T006 land:
T010: Wire CelValidationRule OFFENCE branch (src/main/.../CelValidationRule.java)
```

## Parallel Example: Phase 3 (US1)

```
# After Phase 2 complete, run in parallel:
T011: Extend YouthRehabilitationContextTest       (src/test/.../YouthRehabilitationContextTest.java)
T013: Extend YouthRehabilitationPreprocessorTest  (src/test/.../YouthRehabilitationPreprocessorTest.java)

# After T011 red confirmed:
T012: Extend YouthRehabilitationContext           (src/main/.../YouthRehabilitationContext.java)

# After T012 green + T013 red confirmed:
T014: Extend YouthRehabilitationPreprocessor      (src/main/.../YouthRehabilitationPreprocessor.java)

# After T014 green:
T015: Extend integration test                     (src/test/.../YroEndDateValidationIntegrationTest.java)
```

---

## Implementation Strategy

### MVP: Phases 1–3 (YRC2 duration only — US1)

1. Complete Phase 1: Setup Baseline
2. Complete Phase 2: Foundational (CRITICAL — blocks both user stories)
3. Complete Phase 3: User Story 1 (YRC2)
4. **STOP and VALIDATE**: Run `quickstart.md`'s manual verification against a real request
5. Deploy/demo if ready — `DUR-YRC2` alone already delivers spec FR-001/FR-003/FR-004 value

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add US1 (YRC2) → Test independently → Deploy/Demo (MVP!)
3. Add US2 (YRC1) → Test independently → Deploy/Demo
4. Add US3 (combined display verification) → Deploy/Demo
5. Polish & quality gates (Phase 6), including the live API test and prompt-ref-key verification

### Parallel Team Strategy

Given US1 and US2 share the same two production files
(`YouthRehabilitationContext.java`, `YouthRehabilitationPreprocessor.java`), true parallel
implementation across two developers is not recommended for this feature — implementation must land
sequentially (US1 then US2), though the two stories' *test* tasks can be drafted concurrently by
different people ahead of time.

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break independence
- T029 (prompt-ref-key verification against the real upstream contract) is the single highest-risk
  open item in this feature — see plan.md Risk Register. Do not treat T013/T018/T028 passing against
  assumed key names as final confirmation until T029 is done.
