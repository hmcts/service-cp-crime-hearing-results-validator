# Tasks: Community Order End Date Validation (DR-COEW-001)

**Branch**: `DD-41653-community-order-date-validation`  
**Input**: Design documents from `specs/003-community-order-date-validation/`  
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅

**TDD is mandatory** (Constitution Principle VIII). Every test task MUST be written and confirmed failing before the corresponding implementation task begins.

**YAML-first** (Constitution Principle I). `DR-COEW-001.yaml` is authored before any Java change.

---

## Phase 1: Setup Baseline

**Purpose**: Confirm the build is green before any changes land so regressions are detectable immediately.

- [X] T001 Run `./gradlew clean test` and confirm BUILD SUCCESSFUL with 1 rule (DR-SENT-002) before any code changes

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: YAML contract and data-class changes that every subsequent task depends on.

⚠️ **CRITICAL**: After T002 lands, `ValidationRuleAutoConfigurationTest` will start failing (red) because `"community-order-end-date"` has no registered preprocessor. This is the intended TDD signal — do not fix it until T009.

- [X] T002 Create `src/main/resources/rules/DR-COEW-001.yaml` with rule id `DR-COEW-001`, priority 4000, preprocessing type `community-order-end-date`, and all 5 conditions (AC2a/CUR, AC2b/CURE, AC2c/CURA, AC2d/AAR, AC3/UPWR) with ERROR severity and exact message templates from spec FR-003/FR-004/FR-006 — see data-model.md for full YAML skeleton
- [X] T003 [P] Add 6 new `List<String>` fields to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`: `communityOrderShortCodes`, `curfewShortCodes`, `curfewTagShortCodes`, `furtherCurfewShortCodes`, `alcoholAbstinenceShortCodes`, `unpaidWorkShortCodes` — use the existing `@Data @Builder @NoArgsConstructor @AllArgsConstructor` Lombok pattern

**Checkpoint**: T002 + T003 complete. Build has 1 failing test (ValidationRuleAutoConfigurationTest — expected red). All other tests still green.

---

## Phase 3: User Story 1 — Requirement End Date Cannot Exceed Order End Date (AC2) (Priority: P1) 🎯 MVP

**Goal**: Detect when any community order requirement (CUR, CURE, CURA, AAR) has an end date strictly later than its parent community order's end date, and emit one ERROR per violating requirement type per defendant.

**Independent Test**: POST to `POST /api/validation/validate` with a COEW result (endDate prompt) and a CUR child result (endDate prompt later than order end date) → response must contain `isValid: false`, one error with ruleId `DR-COEW-001`, message containing "Curfew (community requirement)", and `affectedOffences` listing the offence.

> **Write tests FIRST — confirm they FAIL before implementation (T004, T006, T008)**

### Tests for User Story 1

- [X] T004 [US1] Write `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContextTest.java` covering: `toCelContext()` returns map with all 5 keys (`curViolationCount`, `cureViolationCount`, `curaViolationCount`, `aarViolationCount`, `upwrViolationCount`); `getOffenceIdSet()` returns correct list for each of the 6 named sets; throws `IllegalArgumentException` for unknown set name — run `./gradlew test --tests "*.CommunityOrderContextTest"` and confirm compilation failure (class does not exist yet)

- [X] T006 [US1] Write `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessorTest.java` covering AC2 scenarios: CUR end date after order end date → `curViolationCount=1`, `curViolationOffenceIds` contains offence; equal dates → no violation; CURE endDateOfTag after order → `cureViolationCount=1`; CURA endDate after order → `curaViolationCount=1`; AAR until date after order → `aarViolationCount=1`; multiple offences, only one violating → count=1 and correct offence ID; multiple defendants, only affected one has non-zero counts; null/blank promptValue → skip gracefully, count=0 — run `./gradlew test --tests "*.CommunityOrderEndDatePreprocessorTest"` and confirm compilation failure

- [X] T008 [US1] Write `src/test/java/uk/gov/hmcts/cp/integration/CommunityOrderEndDateRuleIntegrationTest.java` extending `IntegrationTestBase` with `@Nested` classes for Scenarios 6–13 from spec (AC2): Scenario 6 (COEW + CUR violation), Scenario 7 (COS + CURE violation, multiple offences), Scenario 8 (CONI + CURA violation), Scenario 9 (COEW + AAR violation), Scenario 10 (valid: order end ≥ CUR end → no error), Scenario 11 (COEW + CUR + AAR both violating → two errors with correct requirement names), Scenario 12 (3 defendants, 2 affected → assert `affectedDefendants[0].defendantId` on each error matches the triggering defendant; valid defendants must not appear), Scenario 13 (errors exist → share button suppressed i.e. `isValid: false`) — use `$.errors[?(@.ruleId=='DR-COEW-001')]` JsonPath filter; run `./gradlew test --tests "*.CommunityOrderEndDateRuleIntegrationTest"` and confirm test failure (no preprocessor registered yet)

### Implementation for User Story 1

- [X] T005 [US1] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContext.java` as a Java record implementing `RuleEvaluationContext` with fields: `String defendantName`, `long curViolationCount`, `long cureViolationCount`, `long curaViolationCount`, `long aarViolationCount`, `long upwrViolationCount`, `List<String> curViolationOffenceIds`, `List<String> cureViolationOffenceIds`, `List<String> curaViolationOffenceIds`, `List<String> aarViolationOffenceIds`, `List<String> upwrViolationOffenceIds`, `List<String> allOffenceIds`; implement `toCelContext()` returning 5-entry map; implement `getOffenceIdSet(String)` via switch expression for all 6 named sets; `defendantName()` and `allOffenceIds()` come free from record components — run T004 tests and confirm they pass

- [X] T007 [US1] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessor.java` as `@Component` implementing `ValidationPreprocessor` with `type()` returning `"community-order-end-date"`; implement `preprocess()`: group result lines by defendantId, skip defendants with no community order result lines; for each defendant iterate offences, parse order endDate from prompts (promptRef=`"endDate"`), compare each requirement line's date (CUR→`"endDate"`, CURE→`"endDateOfTag"`, CURA→`"endDate"`, AAR→`"until"`) against order end date using `requirementDate.isAfter(orderEndDate)` for violation; parse dates via `LocalDate.parse(promptValue)` with WARN log and skip on failure; build and return `CommunityOrderContext` per defendant — run T006 tests and confirm all AC2 tests pass

- [X] T009 [US1] Update `src/test/java/uk/gov/hmcts/cp/config/ValidationRuleAutoConfigurationTest.java`: add `new CommunityOrderEndDatePreprocessor()` to the `PreprocessorRegistry` constructor list in all three test methods; update `should_create_one_rule_per_yaml_file` assertion from `hasSize(3)` to `hasSize(4)` and add `"DR-COEW-001"` to `containsExactlyInAnyOrder(...)` — run `./gradlew test --tests "*.ValidationRuleAutoConfigurationTest"` and confirm all 3 tests pass

**Checkpoint**: Run `./gradlew test --tests "*.CommunityOrderContextTest" --tests "*.CommunityOrderEndDatePreprocessorTest" --tests "*.ValidationRuleAutoConfigurationTest" --tests "*.CommunityOrderEndDateRuleIntegrationTest"` — all AC2 tests green. T008 integration tests for Scenarios 6–13 should all pass.

---

## Phase 4: User Story 2 — Unpaid Work Requirement Demands 12-Month Minimum Order Duration (AC3) (Priority: P1)

**Goal**: Detect when a community order with UPWR child result has an end date less than 12 calendar months from the hearing date (`hearingDay`), and emit one ERROR per affected defendant.

**Independent Test**: POST with a COEW result (endDate prompt = 13/04/2027) + UPWR child result, `hearingDay = 2026-05-14` → response must contain `isValid: false`, error with ruleId `DR-COEW-001`, message containing "unpaid work requirement". Same request with endDate = 13/05/2027 → no error.

> **Write tests FIRST — confirm they FAIL (red) before implementation (T010)**

### Tests for User Story 2

- [X] T010 [US2] Extend `CommunityOrderEndDatePreprocessorTest` with AC3 nested class covering: COEW + UPWR with order end date 13/04/2027, hearingDay 14/05/2026 → `upwrViolationCount=1`; boundary pass: end date 13/05/2027, hearingDay 14/05/2026 → `upwrViolationCount=0`; boundary pass: end date 14/05/2027, hearingDay 14/05/2026 → `upwrViolationCount=0`; boundary fail: end date 12/05/2027, hearingDay 14/05/2026 → `upwrViolationCount=1`; UPWR absent → `upwrViolationCount=0`; two defendants both with UPWR, one under 12 months → only that defendant's count = 1 — run `./gradlew test --tests "*.CommunityOrderEndDatePreprocessorTest"` and confirm new AC3 tests fail

- [X] T012 [US2] Extend `CommunityOrderEndDateRuleIntegrationTest` with AC3 nested class covering Scenarios 14–18 from spec: Scenario 14 (COEW + UPWR, 13/04/2027, hearing 14/05/2026 → error), Scenario 15 (COS + UPWR, 13/05/2027, hearing 14/05/2026 → no error), Scenario 16 (CONI + UPWR, 20/05/2027, hearing 14/05/2026 → no error), Scenario 17 (2 defendants, only one under 12 months → one error), Scenario 18 (mixed AC2 + AC3 failures, valid defendant unaffected) — run `./gradlew test --tests "*.CommunityOrderEndDateRuleIntegrationTest"` and confirm AC3 tests fail

### Implementation for User Story 2

- [X] T011 [US2] Extend `CommunityOrderEndDatePreprocessor` with AC3 logic in `preprocess()`: for each offence with a community order result, if any UPWR result line is present check `orderEndDate.isBefore(request.getHearingDay().plusMonths(12).minusDays(1))`; if true add offenceId to `upwrViolationOffenceIds` and increment `upwrViolationCount`; `hearingDay` accessed via `request.getHearingDay()` — run T010 tests and confirm all AC3 preprocessor tests pass, then run T012 integration tests and confirm all AC3 integration scenarios pass

**Checkpoint**: Run `./gradlew test` — all tests green including all 18 AC2+AC3 integration scenarios.

---

## Phase 5: User Story 3 — Error Summary at Top of Screen (AC4) (Priority: P2)

**Goal**: Verify the validation response carries all data the UI needs to render the "There is a Problem" top-of-screen error summary: correct message text per error, correct defendant name in `${defendantName}` placeholder, correct `affectedOffences` per issue.

**Independent Test**: POST with a multi-defendant hearing (one with AC2, one with AC3) → response errors list each defendant separately; `${defendantName}` resolves to defendant display name; no errors reference defendants with valid community orders.

> **Tests FIRST — confirm they FAIL before message template fix (T013)**

- [X] T013 [P] [US3] Add nested class `ErrorSummaryResponseStructure` to `CommunityOrderEndDateRuleIntegrationTest` covering: single error message text matches exact string from FR-004 (`"The end date of the order must match or be longer than the end date of Curfew (community requirement)"`); `${defendantName}` placeholder is resolved with actual defendant name (not literal `${defendantName}`); multiple distinct errors (AC2 + AC3) both appear in response; valid defendant name does not appear in any error message — verify message templates in `DR-COEW-001.yaml` include `${defendantName}` where required and run tests to confirm. Also assert `affectedDefendants[0].defendantId` on each error matches the defendant whose result triggered it (the `CelValidationRule` framework change populates this field for all rules)

**Checkpoint**: All error summary response assertions pass; `./gradlew test` still green.

---

## Phase 6: User Story 4 — Inline Error Per Offence (AC5) (Priority: P2)

**Goal**: Verify the validation response contains `affectedOffences` scoped to the specific offences that triggered each error (not all offences for the defendant), enabling the UI to render inline errors above the correct result.

**Independent Test**: POST with defendant having 3 offences where only offence 2 has a CUR violation → error's `affectedOffences` contains only offence 2's ID, not offences 1 or 3.

> **Tests FIRST — confirm they FAIL if affectedOffenceSet is misconfigured (T014)**

- [X] T014 [P] [US4] Add nested class `InlineErrorOffenceScoping` to `CommunityOrderEndDateRuleIntegrationTest` covering: 1 defendant with 3 offences, only offence 2 has CUR violation → `affectedOffences` on that error contains exactly offence 2's ID; 1 defendant with 2 offences both having CUR violations → `affectedOffences` contains both; mixed: offence 1 has CUR violation, offence 2 has AAR violation → two separate errors each with the correct single offence ID; UPWR violation on offence 1 only → `upwrViolationOffenceIds` scoped to offence 1 — verify `affectedOffenceSet` names in `DR-COEW-001.yaml` match the named sets on `CommunityOrderContext`

**Checkpoint**: Offence-scoping tests pass; inline error data is correctly scoped per violation type.

---

## Phase 7: Polish & Quality Gates

**Purpose**: Final quality gate — static analysis, checkstyle, and full build clean.

- [X] T015 Run `./gradlew checkstyleMain` and fix all checkstyle violations (Google style, `maxWarnings = 0`) in new/modified Java files: `PreprocessingDefinition.java`, `CommunityOrderContext.java`, `CommunityOrderEndDatePreprocessor.java`
- [X] T016 [P] Run `./gradlew pmdMain` and fix all PMD violations in new/modified Java files
- [X] T017 [P] Verify no `System.out` / `System.err` / `printStackTrace` calls in new Java (grep check)
- [X] T018 Run `./gradlew clean build` — confirm BUILD SUCCESSFUL with all 4 rules discovered and all tests passing
- [X] T019 [P] Run `./gradlew jacocoTestReport` and verify new classes have meaningful coverage (preprocessor logic, context dispatch, date boundary cases)

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup/Baseline)
    └── Phase 2 (Foundational: YAML + PreprocessingDefinition)  [BLOCKS ALL]
            ├── Phase 3 (US1 — AC2)   ← MVP deliverable
            │       └── Phase 4 (US2 — AC3)   ← builds on same preprocessor
            │               ├── Phase 5 (US3 — AC4 response structure)
            │               └── Phase 6 (US4 — AC5 offence scoping)
            └── Phase 7 (Polish)      ← after all phases complete
```

### Within Each Phase (TDD order)

```
Test task (red) → Implementation task (green) → Integration test (green) → next phase
```

### User Story Dependencies

- **US1 (AC2)**: Depends on Phase 2 (YAML + PreprocessingDefinition). Core MVP.
- **US2 (AC3)**: Depends on US1 (same preprocessor, extends it). Cannot start before T007.
- **US3 (AC4)**: Depends on US1 + US2 complete (verifies combined response structure).
- **US4 (AC5)**: Depends on US1 + US2 complete (verifies offence-scoped `affectedOffences`). Can run in parallel with US3.

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
| T010 | T007 |
| T011 | T010 (must see red first) |
| T012 | T011 |
| T013 | T012 |
| T014 | T012 |
| T015–T019 | T013, T014 |

### Parallel Opportunities

- T003 can start in parallel with T002 (different files)
- T004 and T006 can start in parallel after T002 + T003 (different test files)
- T013 and T014 can run in parallel (different nested test classes)
- T015, T016, T017, T019 can all run in parallel in Phase 7

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

## Implementation Strategy

### MVP: Phases 1–3 (AC2 only — US1)

1. T001: Baseline green build
2. T002–T003: Foundational (YAML + data class)
3. T004–T009: US1 complete (AC2 detection + tests)
4. **STOP and VALIDATE**: `./gradlew test` green; CUR/CURE/CURA/AAR violations detected; 4 rules discovered
5. Deploy/demo if ready

### Incremental Delivery

1. MVP (above) → AC2 violations detected ✅
2. Add Phase 4 (US2) → AC3 UPWR violations detected ✅
3. Add Phases 5–6 (US3/US4) → response structure verified for UI rendering ✅
4. Phase 7 → full build clean ✅

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
