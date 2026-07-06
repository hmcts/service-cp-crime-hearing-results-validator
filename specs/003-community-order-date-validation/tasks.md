# Tasks: Community Order End Date Validation (DR-COEW-001)

**Branch**: `DD-41653-community-order-date-validation`  
**Input**: Design documents from `specs/003-community-order-date-validation/`  
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅

**TDD is mandatory** (Constitution Principle VIII). Every test task MUST be written and confirmed failing before the corresponding implementation task begins.

**YAML-first** (Constitution Principle I). `DR-COEW-001.yaml` is authored before any Java change.

> **Extension (2026-07-06, Jira `DD-41655`, branch `DD-41655-requirement-duration-validation`,
> based on `team/DD-41653`)**: Phases 1–6 and T001–T019 below are already shipped (User Stories
> 1–3, AC2/AC4/AC5). Phases 7–12 (T020 onward) add User Stories 4–7 (requirement duration end date
> validation) on top of that shipped work — see the [Extension](#phase-7-foundational-extension-jira-dd-41655--blocking-prerequisites-for-us4-7)
> heading below.

---

## Phase 1: Setup Baseline

**Purpose**: Confirm the build is green before any changes land so regressions are detectable immediately.

- [X] T001 Run `./gradlew clean test` and confirm BUILD SUCCESSFUL with 1 rule (DR-SENT-002) before any code changes

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: YAML contract and data-class changes that every subsequent task depends on.

⚠️ **CRITICAL**: After T002 lands, `ValidationRuleAutoConfigurationTest` will start failing (red) because `"community-order-end-date"` has no registered preprocessor. This is the intended TDD signal — do not fix it until T009.

- [X] T002 Create `src/main/resources/rules/DR-COEW-001.yaml` with rule id `DR-COEW-001`, priority 4000, preprocessing type `community-order-end-date`, and all 4 conditions (AC2a/CUR, AC2b/CURE, AC2c/CURA, AC2d/AAR) with ERROR severity and exact message templates from spec FR-003/FR-004 — see data-model.md for full YAML skeleton
- [X] T003 [P] Add 5 new `List<String>` fields to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`: `communityOrderShortCodes`, `curfewShortCodes`, `curfewTagShortCodes`, `furtherCurfewShortCodes`, `alcoholAbstinenceShortCodes` — use the existing `@Data @Builder @NoArgsConstructor @AllArgsConstructor` Lombok pattern

**Checkpoint**: T002 + T003 complete. Build has 1 failing test (ValidationRuleAutoConfigurationTest — expected red). All other tests still green.

---

## Phase 3: User Story 1 — Requirement End Date Cannot Exceed Order End Date (AC2) (Priority: P1) 🎯 MVP

**Goal**: Detect when any community order requirement (CUR, CURE, CURA, AAR) has an end date strictly later than its parent community order's end date, and emit one ERROR per violating requirement type per defendant.

**Independent Test**: POST to `POST /api/validation/validate` with a COEW result (endDate prompt) and a CUR child result (endDate prompt later than order end date) → response must contain `isValid: false`, one error with ruleId `DR-COEW-001`, message containing "Curfew (community requirement)", and `affectedOffences` listing the offence.

> **Write tests FIRST — confirm they FAIL before implementation (T004, T006, T008)**

### Tests for User Story 1

- [X] T004 [US1] Write `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContextTest.java` covering: `toCelContext()` returns map with all 4 keys (`curViolationCount`, `cureViolationCount`, `curaViolationCount`, `aarViolationCount`); `getOffenceIdSet()` returns correct list for each of the 5 named sets; throws `IllegalArgumentException` for unknown set name — run `./gradlew test --tests "*.CommunityOrderContextTest"` and confirm compilation failure (class does not exist yet)

- [X] T006 [US1] Write `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessorTest.java` covering AC2 scenarios: CUR end date after order end date → `curViolationCount=1`, `curViolationOffenceIds` contains offence; equal dates → no violation; CURE endDateOfTagging after order → `cureViolationCount=1`; CURA endDate after order → `curaViolationCount=1`; AAR until date after order → `aarViolationCount=1`; multiple offences, only one violating → count=1 and correct offence ID; multiple defendants, only affected one has non-zero counts; null/blank promptValue → skip gracefully, count=0 — run `./gradlew test --tests "*.CommunityOrderEndDatePreprocessorTest"` and confirm compilation failure

- [X] T008 [US1] Write `src/test/java/uk/gov/hmcts/cp/integration/CommunityOrderEndDateRuleIntegrationTest.java` extending `IntegrationTestBase` with `@Nested` classes for Scenarios 6–13 from spec (AC2): Scenario 6 (COEW + CUR violation), Scenario 7 (COS + CURE violation, multiple offences), Scenario 8 (CONI + CURA violation), Scenario 9 (COEW + AAR violation), Scenario 10 (valid: order end ≥ CUR end → no error), Scenario 11 (COEW + CUR + AAR both violating → two errors with correct requirement names), Scenario 12 (3 defendants, 2 affected → assert `affectedDefendants[0].defendantId` on each error matches the triggering defendant; valid defendants must not appear), Scenario 13 (errors exist → `isValid: false`) — use `$.errors[?(@.ruleId=='DR-COEW-001')]` JsonPath filter; run `./gradlew test --tests "*.CommunityOrderEndDateRuleIntegrationTest"` and confirm test failure (no preprocessor registered yet)

### Implementation for User Story 1

- [X] T005 [US1] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContext.java` as a Java record implementing `RuleEvaluationContext` with fields: `String defendantName`, `long curViolationCount`, `long cureViolationCount`, `long curaViolationCount`, `long aarViolationCount`, `List<String> curViolationOffenceIds`, `List<String> cureViolationOffenceIds`, `List<String> curaViolationOffenceIds`, `List<String> aarViolationOffenceIds`, `List<String> allOffenceIds`; implement `toCelContext()` returning 4-entry map; implement `getOffenceIdSet(String)` via switch expression for all 5 named sets; `defendantName()` and `allOffenceIds()` come free from record components — run T004 tests and confirm they pass

- [X] T007 [US1] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessor.java` as `@Component` implementing `ValidationPreprocessor` with `type()` returning `"community-order-end-date"`; implement `preprocess()`: group result lines by defendantId, skip defendants with no community order result lines; for each defendant iterate offences, parse order endDate from prompts (promptRef=`"endDate"`), compare each requirement line's date (CUR→`"endDate"`, CURE→`"endDateOfTagging"`, CURA→`"endDate"`, AAR→`"until"`) against order end date using `requirementDate.isAfter(orderEndDate)` for violation; parse dates via `LocalDate.parse(promptValue)` with WARN log and skip on failure; build and return `CommunityOrderContext` per defendant — run T006 tests and confirm all AC2 tests pass

- [X] T009 [US1] Update `src/test/java/uk/gov/hmcts/cp/config/ValidationRuleAutoConfigurationTest.java`: add `new CommunityOrderEndDatePreprocessor()` to the `PreprocessorRegistry` constructor list in all three test methods; update `should_create_one_rule_per_yaml_file` assertion from `hasSize(3)` to `hasSize(4)` and add `"DR-COEW-001"` to `containsExactlyInAnyOrder(...)` — run `./gradlew test --tests "*.ValidationRuleAutoConfigurationTest"` and confirm all 3 tests pass

**Checkpoint**: Run `./gradlew test --tests "*.CommunityOrderContextTest" --tests "*.CommunityOrderEndDatePreprocessorTest" --tests "*.ValidationRuleAutoConfigurationTest" --tests "*.CommunityOrderEndDateRuleIntegrationTest"` — all AC2 tests green. T008 integration tests for Scenarios 6–13 should all pass.

---

## Phase 4: User Story 2 — Error Summary at Top of Screen (AC4) (Priority: P2)

**Goal**: Verify the validation response carries all data the UI needs to render the "There is a Problem" top-of-screen error summary: correct message text per error, correct defendant name in `${defendantName}` placeholder, correct `affectedOffences` per issue.

**Independent Test**: POST with a multi-defendant hearing with AC2 violations → response errors list each defendant separately; `${defendantName}` resolves to defendant display name; no errors reference defendants with valid community orders.

> **Tests FIRST — confirm they FAIL before message template fix (T013)**

- [X] T013 [P] [US3] Add nested class `ErrorSummaryResponseStructure` to `CommunityOrderEndDateRuleIntegrationTest` covering: single error message text matches exact string from FR-004 (`"The end date of the order must match or be longer than the end date of Curfew (community requirement)"`); `${defendantName}` placeholder is resolved with actual defendant name (not literal `${defendantName}`); multiple distinct AC2 errors (e.g. AC2a + AC2d) both appear in response; valid defendant name does not appear in any error message — verify message templates in `DR-COEW-001.yaml` include `${defendantName}` where required and run tests to confirm. Also assert `affectedDefendants[0].defendantId` on each error matches the defendant whose result triggered it (the `CelValidationRule` framework change populates this field for all rules)

**Checkpoint**: All error summary response assertions pass; `./gradlew test` still green.

---

## Phase 5: User Story 3 — Inline Error Per Offence (AC5) (Priority: P2)

**Goal**: Verify the validation response contains `affectedOffences` scoped to the specific offences that triggered each error (not all offences for the defendant), enabling the UI to render inline errors above the correct result.

**Independent Test**: POST with defendant having 3 offences where only offence 2 has a CUR violation → error's `affectedOffences` contains only offence 2's ID, not offences 1 or 3.

> **Tests FIRST — confirm they FAIL if affectedOffenceSet is misconfigured (T014)**

- [X] T014 [P] [US4] Add nested class `InlineErrorOffenceScoping` to `CommunityOrderEndDateRuleIntegrationTest` covering: 1 defendant with 3 offences, only offence 2 has CUR violation → `affectedOffences` on that error contains exactly offence 2's ID; 1 defendant with 2 offences both having CUR violations → `affectedOffences` contains both; mixed: offence 1 has CUR violation, offence 2 has AAR violation → two separate errors each with the correct single offence ID — verify `affectedOffenceSet` names in `DR-COEW-001.yaml` match the named sets on `CommunityOrderContext`

**Checkpoint**: Offence-scoping tests pass; inline error data is correctly scoped per violation type.

---

## Phase 6: Polish & Quality Gates

**Purpose**: Final quality gate — static analysis, checkstyle, and full build clean.

- [X] T015 Run `./gradlew checkstyleMain` and fix all checkstyle violations (Google style, `maxWarnings = 0`) in new/modified Java files: `PreprocessingDefinition.java`, `CommunityOrderContext.java`, `CommunityOrderEndDatePreprocessor.java`
- [X] T016 [P] Run `./gradlew pmdMain` and fix all PMD violations in new/modified Java files
- [X] T017 [P] Verify no `System.out` / `System.err` / `printStackTrace` calls in new Java (grep check)
- [X] T018 Run `./gradlew clean build` — confirm BUILD SUCCESSFUL with all 4 rules discovered and all tests passing
- [X] T019 [P] Run `./gradlew jacocoTestReport` and verify new classes have meaningful coverage (preprocessor logic, context dispatch, date boundary cases)

---

## Phase 7: Foundational Extension (Jira `DD-41655`) — Blocking Prerequisites for US4–7

**Purpose**: Shared framework primitives that every new duration-mismatch condition needs — the
`${calculatedEndDate}` message-template mechanism and the YAML contract for the three new
conditions. None of US4/5/6 can emit a correctly-worded error without these landing first.

⚠️ **CRITICAL**: After T020 lands, `curDurationMismatchCount`/`cureDurationMismatchCount`/
`aarDurationMismatchCount` are referenced by CEL expressions in YAML but do not yet exist on
`CommunityOrderContext.toCelContext()` — the CEL evaluator will throw on `DUR-CUR`/`DUR-CURE`/
`DUR-AAR`. This is the intended TDD signal; it is not fixed until Phase 8 (T027).

- [ ] T020 Append 3 new conditions (`DUR-CUR`, `DUR-CURE`, `DUR-AAR`) to `src/main/resources/rules/DR-COEW-001.yaml` with ERROR severity, `validationLevel: OFFENCE`, and the exact `messageTemplate`/`errorMessageTemplate`/`affectedOffenceSet`/`calculatedValueSet` values from data-model.md § Extension
- [ ] T021 [P] Add `calculatedValueSet` field (`String`) to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionDefinition.java` following the existing Lombok `@Data @Builder` pattern
- [ ] T022 [P] Add a default method `getCalculatedValue(String setName, String offenceId)` to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/RuleEvaluationContext.java` that throws `IllegalArgumentException("Unknown calculated-value set: " + setName)`, mirroring the existing `getOffenceIdSet` default-less pattern (see data-model.md § Extension)
- [ ] T023 [P] Write `resolve_should_replace_extraPlaceholder_tokens` and `resolve_should_leave_other_placeholders_unaffected_when_extraPlaceholders_used` test cases in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/MessageTemplateResolverTest.java` for a new 6-arg `resolve(...)` overload taking `Map<String, String> extraPlaceholders` — run `./gradlew test --tests "*.MessageTemplateResolverTest"` and confirm compilation failure (overload does not exist yet)
- [ ] T024 Implement the 6-arg `resolve(...)` overload in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/MessageTemplateResolver.java`: delegates to the existing 5-arg overload, then replaces `${key}` for each `extraPlaceholders` entry — run T023 tests and confirm they pass; confirm all pre-existing `MessageTemplateResolverTest` cases still pass unmodified
- [ ] T025 Write `loadFromYaml_should_parse_new_duration_conditions_with_calculatedValueSet` in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/RuleDefinitionTest.java` asserting `DR-COEW-001.yaml` (after T020) loads 7 conditions total and that `DUR-CUR`/`DUR-CURE`/`DUR-AAR` each parse `calculatedValueSet` to the expected set name — run and confirm it passes against T020's YAML
- [ ] T026 Update the OFFENCE-level branch of `evaluate()` in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRule.java`: in the per-offence message lambda, when `condition.getCalculatedValueSet() != null`, build `Map.of("calculatedEndDate", context.getCalculatedValue(condition.getCalculatedValueSet(), id))` and pass it to the new 6-arg `resolve(...)`; when `null`, call the existing 5-arg `resolve(...)` unchanged — confirm existing `CelValidationRuleTest`/`CelValidationRuleScenarioTest`/`CelValidationRuleOverrideTest` suites remain green (no `calculatedValueSet` is configured on any pre-existing condition, so behaviour must be identical for AC2a–d and DR-SENT-002)

**Checkpoint**: T020–T026 complete. `./gradlew test --tests "*.MessageTemplateResolverTest" --tests "*.RuleDefinitionTest" --tests "*.CelValidationRuleTest" --tests "*.CelValidationRuleScenarioTest" --tests "*.CelValidationRuleOverrideTest"` all green. `ValidationRuleAutoConfigurationTest`'s condition-count assertions (if any) and full `./gradlew test` will still show `CommunityOrderContext` missing the 3 new CEL variables — expected red until Phase 8.

---

## Phase 8: User Story 4 — Curfew (Non-EM) Requirement Duration Mismatch (Priority: P1)

**Goal**: Detect when a CUR requirement's "End date" does not equal "Start date" + "Curfew period" − 1 day, independent of the AC2 order-end-date check, and emit one ERROR per affected offence with the correctly calculated end date inline.

**Independent Test**: POST a COEW result with a CUR child result whose `startDate`/`curfewPeriod`/`endDate` prompts don't satisfy the duration formula → response contains an error with ruleId `DR-COEW-001`, conditionId `DUR-CUR`, inline message containing the correct calculated date, and top-summary message matching spec FR-018 exactly.

> **Write tests FIRST — confirm they FAIL before implementation (T027, T029)**

### Tests for User Story 4

- [ ] T027 [P] [US4] Extend `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContextTest.java`: `toCelContext()` includes `curDurationMismatchCount`; `getOffenceIdSet("curDurationMismatchOffenceIds")` returns the correct list; `getCalculatedValue("curCalculatedEndDateByOffenceId", offenceId)` returns the expected ISO date string and throws `IllegalArgumentException` for an unknown set name — run and confirm compilation failure (fields don't exist yet)
- [ ] T029 [P] [US4] Extend `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessorTest.java` with CUR duration scenarios: end date == startDate + period − 1 → no violation; end date one day early → violation with `curCalculatedEndDateByOffenceId` set to the correct date; end date one day late (i.e. clerk forgot to subtract 1 day) → violation; missing/unparseable `startDate`, `curfewPeriod`, or `endDate` → skip gracefully, `WARN` logged, no violation; the duration check still runs when the community order's own `endDate` prompt is missing/unparseable (must NOT be gated by the existing AC2 `orderEndDate == null` early-continue) — run and confirm failure (no production logic yet)

### Implementation for User Story 4

- [ ] T028 [US4] Add fields `curDurationMismatchCount: long`, `curDurationMismatchOffenceIds: List<String>`, `curCalculatedEndDateByOffenceId: Map<String, String>` to the `CommunityOrderContext` record in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContext.java`; add the new CEL entry to `toCelContext()`; add the new case to `getOffenceIdSet()`; implement `getCalculatedValue(setName, offenceId)` with a switch over `"curCalculatedEndDateByOffenceId"` (throw for unknown names) — run T027 tests and confirm they pass
- [ ] T030 [US4] In `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessor.java`: add a private `parsePromptInt(ResultLineDto, String promptRef, String offenceId)` helper (same defensive null/blank/unparseable → `WARN`-and-skip semantics as `parseDateValue`); add hardcoded prompt-ref constants `PROMPT_START_DATE = "startDate"` and `PROMPT_CURFEW_PERIOD = "curfewPeriod"`; restructure the per-offence loop so the CUR duration check runs unconditionally (not gated by the AC2 `orderEndDate == null` continue) — for each CUR line, parse `startDate`/`curfewPeriod`/`endDate`, compute `expected = startDate.plusDays(period - 1)`, and if `!endDate.isEqual(expected)` add the offence to `curDurationMismatchOffenceIds` and record `expected` in `curCalculatedEndDateByOffenceId` — run T029 tests and confirm all pass; re-run `CommunityOrderEndDateRuleIntegrationTest` (US1) in full to confirm zero regressions from the loop restructuring

**Checkpoint**: `./gradlew test --tests "*.CommunityOrderContextTest" --tests "*.CommunityOrderEndDatePreprocessorTest" --tests "*.CommunityOrderEndDateRuleIntegrationTest"` green — CUR duration mismatch detected end-to-end (`DUR-CUR` fires), AC2 (US1) scenarios unaffected.

---

## Phase 9: User Story 5 — Curfew with Electronic Monitoring Duration Mismatch (Priority: P1)

**Goal**: Detect when a CURE requirement's "End date of tagging" does not equal "Start date of tagging" + "Curfew and electronic monitoring period" − 1 day.

**Independent Test**: POST a COS result with a CURE child result whose `startDateOfTagging`/`curfewAndElectronicMonitoringPeriod`/`endDateOfTagging` prompts don't satisfy the duration formula → response contains an error with conditionId `DUR-CURE` and the correct calculated date inline.

> **Write tests FIRST — confirm they FAIL before implementation (T031, T033)**

### Tests for User Story 5

- [ ] T031 [P] [US5] Extend `CommunityOrderContextTest.java`: `toCelContext()` includes `cureDurationMismatchCount`; `getOffenceIdSet("cureDurationMismatchOffenceIds")`; `getCalculatedValue("cureCalculatedEndDateByOffenceId", offenceId)` — run and confirm compilation failure
- [ ] T033 [P] [US5] Extend `CommunityOrderEndDatePreprocessorTest.java` with CURE duration scenarios mirroring T029 but using `startDateOfTagging`/`curfewAndElectronicMonitoringPeriod`/`endDateOfTagging` — run and confirm failure

### Implementation for User Story 5

- [ ] T032 [US5] Add `cureDurationMismatchCount`, `cureDurationMismatchOffenceIds`, `cureCalculatedEndDateByOffenceId` to `CommunityOrderContext.java` (same shape as T028); extend `getCalculatedValue()`'s switch with the new case — run T031 tests and confirm they pass
- [ ] T034 [US5] In `CommunityOrderEndDatePreprocessor.java`: add prompt-ref constants `PROMPT_START_DATE_OF_TAGGING = "startDateOfTagging"` and `PROMPT_CURFEW_TAG_PERIOD = "curfewAndElectronicMonitoringPeriod"`; add the CURE duration-check branch (same shape as T030, reusing `parsePromptInt`) — run T033 tests and confirm all pass; re-run US1 and US4 integration tests to confirm zero regressions

**Checkpoint**: CURE duration mismatch (`DUR-CURE`) detected end-to-end; US1 and US4 unaffected.

---

## Phase 10: User Story 6 — Alcohol Abstinence Monitoring Duration Mismatch (Priority: P1)

**Goal**: Detect when an AAR requirement's "Until" date does not equal the hearing date + "Number of days to abstain from consuming any alcohol" − 1 day.

**Independent Test**: POST a hearing with `hearingDay` set and a CONI result with an AAR child result whose `numberOfDaysToAbstain`/`until` prompts don't satisfy the duration formula → response contains an error with conditionId `DUR-AAR` and the correct calculated date inline.

> **Write tests FIRST — confirm they FAIL before implementation (T035, T037)**

### Tests for User Story 6

- [ ] T035 [P] [US6] Extend `CommunityOrderContextTest.java`: `toCelContext()` includes `aarDurationMismatchCount`; `getOffenceIdSet("aarDurationMismatchOffenceIds")`; `getCalculatedValue("aarCalculatedEndDateByOffenceId", offenceId)` — run and confirm compilation failure
- [ ] T037 [P] [US6] Extend `CommunityOrderEndDatePreprocessorTest.java` with AAR duration scenarios: `until` == `request.getHearingDay()` + days − 1 → no violation; mismatch (early or late) → violation with correct calculated `until`; missing/unparseable `numberOfDaysToAbstain` → skip, `WARN` logged; missing `request.getHearingDay()` → skip AAR duration check gracefully, no NPE — run and confirm failure

### Implementation for User Story 6

- [ ] T036 [US6] Add `aarDurationMismatchCount`, `aarDurationMismatchOffenceIds`, `aarCalculatedEndDateByOffenceId` to `CommunityOrderContext.java`; extend `getCalculatedValue()`'s switch — run T035 tests and confirm they pass
- [ ] T038 [US6] In `CommunityOrderEndDatePreprocessor.java`: parse `request.getHearingDay()` once per `preprocess()` call (outer scope, not per-offence); add prompt-ref constant `PROMPT_DAYS_TO_ABSTAIN = "numberOfDaysToAbstain"`; add the AAR duration-check branch using the hearing day as the start point instead of a per-line prompt — run T037 tests and confirm all pass; re-run US1, US4, US5 integration tests to confirm zero regressions

**Checkpoint**: All three duration-mismatch conditions (`DUR-CUR`, `DUR-CURE`, `DUR-AAR`) detected end-to-end. `./gradlew test` fully green.

---

## Phase 11: User Story 7 — Duration-Mismatch Errors Combine With Other Validation Errors (Priority: P2)

**Goal**: Confirm the new duration-mismatch conditions integrate correctly with the already-shipped error-summary/inline-error display mechanism (User Stories 2/3) — no new UI-facing behaviour, verification only (per spec FR-020).

**Independent Test**: POST a hearing that triggers a CUR duration mismatch on one defendant and an AAR duration mismatch on another, and a hearing where one offence fails both AC2 and a duration-mismatch check → both/all errors appear correctly scoped.

> **Tests FIRST — confirm they FAIL if response aggregation is wrong (T039, T040)**

- [ ] T039 [P] [US7] Add nested class `DurationMismatchCombinedWithEachOther` to `src/test/java/uk/gov/hmcts/cp/integration/CommunityOrderEndDateRuleIntegrationTest.java`: one defendant with a CUR duration mismatch and another with an AAR duration mismatch in the same hearing → response contains both errors, each with its own correct `affectedDefendants`/`affectedOffences`, and `isValid: false`
- [ ] T040 [P] [US7] Add nested class `DurationMismatchCombinedWithOrderEndDateCheck` to the same integration test file: one offence where the CUR requirement fails both the AC2 order-end-date check and the `DUR-CUR` duration-mismatch check simultaneously → response contains two separate errors (`AC2a` and `DUR-CUR`) both referencing that offence, per spec FR-021 and User Story 7 Scenario 2

**Checkpoint**: All User Story 7 scenarios pass; no change to any US1–3 scenario's pass/fail outcome (spec SC-009).

---

## Phase 12: Polish & Quality Gates (Extension)

**Purpose**: Final quality gate for the `DD-41655` extension — mirrors Phase 6 for the new/modified files only.

- [ ] T041 Run `./gradlew checkstyleMain` and fix all violations in files touched by this extension: `ConditionDefinition.java`, `RuleEvaluationContext.java`, `MessageTemplateResolver.java`, `CelValidationRule.java`, `CommunityOrderContext.java`, `CommunityOrderEndDatePreprocessor.java`
- [ ] T042 [P] Run `./gradlew pmdMain` and fix all violations in the same files
- [ ] T043 [P] Verify no `System.out` / `System.err` / `printStackTrace` in the new/modified code (grep check)
- [ ] T044 Run `./gradlew clean build` — confirm BUILD SUCCESSFUL, still 1 rule (`DR-COEW-001`) discovered with 7 conditions total, all tests passing
- [ ] T045 [P] Run `./gradlew jacocoTestReport` and verify the new duration-mismatch branches (equal, early, late, missing-field skip paths) have meaningful coverage
- [ ] T046 Verify the four new hardcoded prompt-ref keys (`startDate`, `curfewPeriod`, `startDateOfTagging`, `curfewAndElectronicMonitoringPeriod`, `numberOfDaysToAbstain`) against the real upstream `api-cp-crime-hearing-results-validator` schema/Swagger or a real dev-environment payload (research.md Decision 10 / plan.md Risk Register) — update the hardcoded constants and re-run T029/T033/T037 if any name differs

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup/Baseline)
    └── Phase 2 (Foundational: YAML + PreprocessingDefinition)  [BLOCKS ALL]
            ├── Phase 3 (US1 — AC2)   ← MVP deliverable
            │       ├── Phase 4 (US2 — AC4 response structure)
            │       └── Phase 5 (US3 — AC5 offence scoping)
            └── Phase 6 (Polish)      ← after all phases complete

Phase 7 (Foundational Extension: calculatedEndDate mechanism + YAML)  [BLOCKS US4–7]
    (depends on Phase 6 having already shipped — DR-COEW-001 and CommunityOrderContext exist)
            ├── Phase 8  (US4 — CUR duration)    ← smallest new increment
            │       └── Phase 9  (US5 — CURE duration)   [same file, sequential after US4]
            │               └── Phase 10 (US6 — AAR duration)  [same file, sequential after US5]
            │                       └── Phase 11 (US7 — combined display, verification only)
            └── Phase 12 (Polish Extension)      ← after Phases 8–11 complete
```

### Within Each Phase (TDD order)

```
Test task (red) → Implementation task (green) → Integration test (green) → next phase
```

### User Story Dependencies

- **US1 (AC2)**: Depends on Phase 2 (YAML + PreprocessingDefinition). Core MVP.
- **US2 (AC4)**: Depends on US1 complete (verifies response structure).
- **US3 (AC5)**: Depends on US1 complete (verifies offence-scoped `affectedOffences`). Can run in parallel with US2.
- **US4 (CUR duration)**: Depends on Phase 7 (foundational extension) and Phase 6 (US1's `CommunityOrderContext`/`CommunityOrderEndDatePreprocessor` already exist). Smallest independently-valuable increment of the extension.
- **US5 (CURE duration)**: Depends on US4 — same two files (`CommunityOrderContext.java`, `CommunityOrderEndDatePreprocessor.java`) are extended again, so it runs sequentially after US4 lands, not in parallel.
- **US6 (AAR duration)**: Depends on US5 — same reason (shared files).
- **US7 (combined display)**: Depends on US4+US5+US6 all complete (needs all three condition types available to exercise combinations) and on US1 (for the AC2+duration combined scenario).

### Task-Level Dependencies

| Task | Depends On |
|------|-----------|
| T002 | T001 |
| T003 | T001 |
| T004 | T002, T003 |
| T005 | T004 (must see red first) |
| T006 | T003 |
| T007 | T005, T006 (must see red first) |
| T008 | T007 |
| T009 | T007, T008 |
| T013 | T009 |
| T014 | T009 |
| T015–T019 | T013, T014 |
| T020 | T009 (Phase 6 complete) |
| T021, T022 | T020 |
| T023 | T021, T022 |
| T024 | T023 (must see red first) |
| T025 | T020, T024 |
| T026 | T024 |
| T027 | T025, T026 |
| T029 | T027 |
| T028 | T027 (must see red first) |
| T030 | T028, T029 (must see red first) |
| T031 | T030 |
| T033 | T031 |
| T032 | T031 (must see red first) |
| T034 | T032, T033 (must see red first) |
| T035 | T034 |
| T037 | T035 |
| T036 | T035 (must see red first) |
| T038 | T036, T037 (must see red first) |
| T039, T040 | T038 |
| T041–T046 | T039, T040 |

### Parallel Opportunities

- T003 can start in parallel with T002 (different files)
- T004 and T006 can start in parallel after T002 + T003 (different test files)
- T013 and T014 can run in parallel (different nested test classes)
- T015, T016, T017, T019 can all run in parallel in Phase 6
- T021 and T022 can run in parallel (different files); T023 can start alongside them (test file, no implementation dependency until T024)
- T027 (context test) can be written in parallel with T029 (preprocessor test) — both red until T028/T030 land
- T039 and T040 can run in parallel (different nested test classes in the same file — coordinate to avoid merge conflicts, or land sequentially)
- T042, T043, T045 can all run in parallel in Phase 12
- **Not parallel**: US5/US6 implementation tasks (T032/T034, T036/T038) cannot run in parallel with each other or with US4's (T028/T030) — all four requirement types share the same two files (`CommunityOrderContext.java`, `CommunityOrderEndDatePreprocessor.java`), so concurrent edits would conflict. Their *test* tasks (T027/T031/T035) can be drafted in parallel even though implementation must land sequentially.

---

## Parallel Example: Phase 3 (US1)

```
# After Phase 2 complete, run in parallel:
T004: Write CommunityOrderContextTest          (src/test/.../CommunityOrderContextTest.java)
T006: Write CommunityOrderEndDatePreprocessorTest  (src/test/.../CommunityOrderEndDatePreprocessorTest.java)

# After T004 red confirmed:
T005: Implement CommunityOrderContext           (src/main/.../CommunityOrderContext.java)

# After T005 green + T006 red confirmed:
T007: Implement CommunityOrderEndDatePreprocessor (src/main/.../CommunityOrderEndDatePreprocessor.java)

# After T007 green:
T008: Write integration tests (red)             (src/test/.../CommunityOrderEndDateRuleIntegrationTest.java)
T009: Update ValidationRuleAutoConfigurationTest (src/test/.../ValidationRuleAutoConfigurationTest.java)
```

---

## Parallel Example: Phase 8 (US4) — Extension

```
# After Phase 7 complete, run in parallel:
T027: Extend CommunityOrderContextTest with CUR duration cases   (src/test/.../CommunityOrderContextTest.java)
T029: Extend CommunityOrderEndDatePreprocessorTest with CUR duration cases (src/test/.../CommunityOrderEndDatePreprocessorTest.java)

# After T027 red confirmed:
T028: Add CUR duration fields to CommunityOrderContext           (src/main/.../CommunityOrderContext.java)

# After T028 green + T029 red confirmed:
T030: Add CUR duration branch to CommunityOrderEndDatePreprocessor (src/main/.../CommunityOrderEndDatePreprocessor.java)

# After T030 green — re-run full regression before starting Phase 9:
./gradlew test --tests "*.CommunityOrderEndDateRuleIntegrationTest"
```

Phases 9 and 10 (US5, US6) repeat this exact same shape, substituting the CURE and AAR
prompt refs/field names respectively — they cannot start until the prior phase's implementation
task is green, since all three share `CommunityOrderContext.java` and
`CommunityOrderEndDatePreprocessor.java`.

---

## Implementation Strategy

### MVP: Phases 1–3 (AC2 only — US1)

1. T001: Baseline green build
2. T002–T003: Foundational (YAML + data class)
3. T004–T009: US1 complete (AC2 detection + tests)
4. **STOP and VALIDATE**: `./gradlew test` green; CUR/CURE/CURA/AAR violations detected; 4 rules discovered
5. Deploy/demo if ready

### Incremental Delivery

1. MVP (above) → AC2 violations detected ✅
2. Add Phases 4–5 (US2/US3) → response structure and offence scoping verified for UI rendering ✅
3. Phase 6 → full build clean ✅
4. Phase 7 (Extension foundational) → `${calculatedEndDate}` mechanism + YAML conditions in place ✅
5. Phase 8 (US4) → CUR duration mismatch detected — smallest valuable increment of the extension ✅
6. Phase 9 (US5) → CURE duration mismatch detected ✅
7. Phase 10 (US6) → AAR duration mismatch detected — all three duration checks now live ✅
8. Phase 11 (US7) → combined-error display verified (no new UI code) ✅
9. Phase 12 → full build clean, prompt-ref keys verified against the real upstream contract ✅

### Extension MVP: Phases 7–8 (US4 only)

If the extension needs to ship incrementally, Phase 8 (US4 — CUR duration only) is the smallest
independently deployable slice: it proves the `${calculatedEndDate}` mechanism end-to-end for one
requirement type before CURE/AAR are added. CURE and AAR (Phases 9–10) then repeat the identical
pattern with no new architectural risk.

---

## Notes

- `[P]` = can run in parallel (different files, no inter-task dependency)
- `[USn]` = maps task to user story for traceability
- TDD: every test task must run and **fail** before its implementation task starts
- YAML first: T002 (`DR-COEW-001.yaml`) must be authored before any Java class
- After T002 lands, `ValidationRuleAutoConfigurationTest` enters a deliberate red state until T009
- Integration tests use `$.errors[?(@.ruleId=='DR-COEW-001')]` JsonPath to isolate rule-specific errors
- Do not add `System.out` / `System.err` anywhere — SLF4J only (Constitution Principle VII)
- Commit after each logical group using Conventional Commits: `feat:`, `test:`, `chore:`

### Extension (Jira `DD-41655`) — additional notes

- After T020 lands, `DUR-CUR`/`DUR-CURE`/`DUR-AAR` CEL expressions reference CEL variables that
  don't exist on `CommunityOrderContext` yet — deliberate red state until T028/T032/T036 land.
- `CommunityOrderContext.java` and `CommunityOrderEndDatePreprocessor.java` are touched by every one
  of T028/T030 (US4), T032/T034 (US5), and T036/T038 (US6) — these MUST land sequentially, not in
  parallel, to avoid merge conflicts on the same class.
- The four new prompt-ref keys (`startDate`, `curfewPeriod`, `startDateOfTagging`,
  `curfewAndElectronicMonitoringPeriod`, `numberOfDaysToAbstain`) are **unverified assumptions**
  (research.md Decision 10) — T046 exists specifically to close this out; do not treat the
  extension as "done" until it is checked off.
- `CURA` is deliberately untouched by this extension (research.md Decision 12) — no `DUR-CURA`
  condition exists or is expected.
