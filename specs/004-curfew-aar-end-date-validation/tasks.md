# Tasks: Curfew and AAR Requirement End-Date Period Validation (DR-COEW-002)

**Input**: Design documents from `specs/004-curfew-aar-end-date-validation/`
**Branch**: `DD-41655-curfew-aar-end-date-validation`
**Constitution**: TDD is mandatory — every test task MUST be run and confirmed failing before its paired implementation task.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no blocking inter-dependency)
- **[US1]**: Curfew requirement end date vs duration (CUR, CURE, YRC2, YRC1)
- **[US2]**: AAR requirement end date vs duration (AAR, CO-only)
- **[US3]**: Error response format carrying computed expected date

---

## Phase 1: Setup (YAML-first + library prerequisite)

**Purpose**: Author the YAML rule before any Java (Constitution Principle I) and lock in the upstream library version that exposes DURATION prompt nested structure.

**⚠️ T001 must be done first (Principle I). T002 must be done before any preprocessor work.**

- [X] T001 Author `src/main/resources/rules/DR-COEW-002.yaml` with rule id `DR-COEW-002`, priority `4500`, `preprocessing.type: "curfew-period-check"`, short-code lists, and single condition `AC1` (`violationCount > 0`, `messageTemplate` and `errorMessageTemplate` using `${expectedEndDate}`, `affectedOffenceSet: "violatedOffenceIds"`) — exact YAML in `plan.md` section R-1
- [X] T002 Bump `api-cp-crime-hearing-results-validator` version from `0.1.7` to `0.1.8` in `gradle/libs.versions.toml` and confirm `gradle dependencies` resolves the new version (blocking dependency — see `research.md` Decision 6)

---

## Phase 2: Foundational (Framework Changes)

**Purpose**: Small, backward-compatible framework enhancements that unlock `${expectedEndDate}` template resolution and `affectedDefendants` population for OFFENCE-level ERRORs. All five user stories depend on these being complete.

**⚠️ CRITICAL**: No user-story implementation can pass tests until this phase is complete.

- [X] T003 [P] Add `default Map<String, String> stringVariables() { return Map.of(); }` to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/RuleEvaluationContext.java` (backward compatible; existing impls inherit the default)
- [X] T004 [P] Add `private List<String> yroShortCodes;` field to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` (alongside existing short-code list fields)
- [X] T005 [P] Write **failing** tests for string variable `${expectedEndDate}` resolution in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/MessageTemplateResolverTest.java` — scenarios: single variable substituted; multiple variables substituted in one call; template with no `${key}` token unchanged; empty `stringVariables` map leaves template unchanged. **Confirm tests FAIL before T006.**
- [X] T006 Extend `src/main/java/uk/gov/hmcts/cp/services/rules/cel/MessageTemplateResolver.java` — add overloaded `resolve(…, Map<String, String> stringVariables)` that delegates to existing 5-arg method then iterates the map replacing each `${key}` token; keep original 5-arg signature intact. (TDD green for T005 — depends on T003)
- [X] T007 [P] Write **failing** unit tests for `CurfewPeriodContext` in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodContextTest.java` — scenarios: `toCelContext()` returns `{"violationCount": 1L}`; `getOffenceIdSet("violatedOffenceIds")` returns singleton list; `getOffenceIdSet("anyOtherName")` also returns singleton (no throw); `stringVariables()` returns `{"expectedEndDate": "30/07/2026"}`; `defendantId()` returns injected defendant id; `allOffenceIds()` returns singleton. **Confirm tests FAIL before T008.**
- [X] T008 Implement `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodContext.java` as a record `(String defendantId, String defendantName, String offenceId, long violationCount, String expectedEndDate)` implementing `RuleEvaluationContext` with `toCelContext()`, `getOffenceIdSet()`, `allOffenceIds()`, `defendantId()`, and `stringVariables()` per `data-model.md`. (TDD green for T007 — depends on T003)
- [X] T009a [P] Write **failing** unit tests in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRuleTest.java` covering the two T009 framework changes: (a) when a context's `stringVariables()` returns `{"expectedEndDate": "30/07/2026"}`, verify `messageResolver.resolve()` is called with that map as the 6th argument; (b) when an OFFENCE-level ERROR condition fires and `context.defendantId()` returns a non-null id, verify `ValidationIssue.affectedDefendants` contains that id; (c) when `context.defendantId()` returns null, verify `affectedDefendants` is NOT set (backward-compatible path). **Confirm tests FAIL before T009.**
- [X] T009 Update `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRule.java` — (1) pass `context.stringVariables()` as the 6th argument to all `messageResolver.resolve(…)` call sites; (2) in the OFFENCE-level path add: `if (context.defendantId() != null && isError) { issueBuilder.affectedDefendants(offenceDisplayHelper.buildAffectedDefendants(List.of(context.defendantId()), null)); }`. Also update **all existing `messageResolver.resolve()` stub/verify calls in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRuleTest.java`** from the 5-arg signature to the 6-arg signature (pass `Map.of()` as the new argument where the test context has no string variables) to prevent Mockito verification failures on existing tests. (TDD green for T009a — depends on T003, T006)
- [X] T010 Add skeleton `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodPreprocessor.java` — `@Component`, `@Slf4j`, `type()` returns `"curfew-period-check"`, `preprocess()` returns `new LinkedHashMap<>()` (empty map) — sufficient for Spring Boot context to start and DR-COEW-002.yaml to resolve the preprocessor qualifier (depends on T001, T004, T008)

**Checkpoint**: Run `gradle test` — existing tests must stay green; `CurfewPeriodContextTest` must pass; `MessageTemplateResolverTest` new cases must pass.

---

## Phase 3: User Story 1 — Curfew Requirement End Date Validation (Priority: P1) 🎯 MVP

**Goal**: Detect when a CUR, CURE, YRC2, or YRC1 requirement's recorded end date does not equal `startDate + period (DURATION) − 1 day` and emit a `DR-COEW-002` ERROR with the computed expected date.

**Independent Test**: Submit a `POST /validate` request containing a COEW parent with a CUR child where `endDate ≠ startDate + curfewPeriod − 1 day`. Response must contain one `errors[]` entry with `ruleId = "DR-COEW-002"`, `severity = "ERROR"`, `message` containing the correct expected date in `DD/MM/YYYY`, and `affectedOffences` pointing to the violated offence.

### Tests for User Story 1 ⚠️ Write and confirm FAILING before T012/T014

- [X] T011 [US1] Write **failing** unit tests for CUR/YRC2 violation scenarios in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodPreprocessorTest.java` — scenarios: end date off by 1 day (too late) → context emitted with correct `expectedEndDate`; end date off by 1 day (too early) → context emitted; exact match → no context emitted; period in days; period in weeks (`plusWeeks` arithmetic); period in months (`plusMonths` arithmetic); missing `startDate` prompt → skip + WARN; missing `curfewPeriod` prompt → skip + WARN; DURATION child list empty → skip + WARN; unknown unit string in child `promptRef` → skip + WARN; **CUR line with no CO or YRO parent result on the same offence → no context emitted, no exception**; **`curfewPeriod` quantity = 0 → skip + WARN**; **`curfewPeriod` quantity is negative → skip + WARN**; CUR under YROEW parent → violation detected. **Confirm FAIL before T012.**
- [X] T013 [US1] Write **failing** unit tests for CURE/YRC1 violation scenarios in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodPreprocessorTest.java` — scenarios: CURE end date mismatch under COEW; CURE correct → no context; YRC1 mismatch under YROEW; missing `startDateOfTagging` → skip; missing `curfewAndElectronicMonitoringPeriod` → skip. **Confirm FAIL before T014.**

### Implementation for User Story 1

- [X] T012 [US1] Implement CUR/YRC2 period check in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodPreprocessor.java` — replace empty-map skeleton with: short-code set normalisation from config; grouping by defendant then offence; `parseDuration()` helper reading DURATION child `promptRef` → `ChronoUnit` and `promptValue` → long; `parseDate()` helper; expected-date calculation `startDate.plus(qty, unit).minusDays(1)`; emit `CurfewPeriodContext` using the actual short code as the key prefix — `line.getShortCode().toUpperCase() + ":" + defendantId + ":" + offenceId` (e.g. `"CUR:<defendantId>:<offenceId>"` for CUR lines and `"YRC2:<defendantId>:<offenceId>"` for YRC2 lines) so violations of different short codes on the same (defendant, offence) get distinct map entries when `endDate ≠ expectedEnd`. (TDD green for T011 — depends on T010)
- [X] T014 [US1] Add CURE/YRC1 period check to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodPreprocessor.java` — same structure as CUR/YRC2 but using `"startDateOfTagging"`, `"curfewAndElectronicMonitoringPeriod"`, `"endDateOfTagging"` prompt refs; key prefix is the actual short code — `line.getShortCode().toUpperCase() + ":" + defendantId + ":" + offenceId` (e.g. `"CURE:<defendantId>:<offenceId>"` for CURE lines and `"YRC1:<defendantId>:<offenceId>"` for YRC1 lines) so CURE and YRC1 violations on the same (defendant, offence) each produce a distinct map entry. (TDD green for T013)

**Checkpoint**: Run `gradle test --tests "*.CurfewPeriodPreprocessorTest"` — all CUR/CURE/YRC2/YRC1 tests green.

---

## Phase 4: User Story 2 — AAR Requirement End Date Validation (Priority: P1)

**Goal**: Detect when an AAR requirement's recorded `until` date does not equal `hearingDate + numberOfDaysToAbstainFromConsumingAnyAlcohol − 1 day` (CO parents only) and emit a `DR-COEW-002` ERROR.

**Independent Test**: Submit a `POST /validate` request containing a COEW parent with an AAR child where `until ≠ hearingDate + days − 1`. Response must contain one `errors[]` entry with `message` containing the correct expected date. Submit same request with a YROEW-only parent — no error must be returned.

### Tests for User Story 2 ⚠️ Write and confirm FAILING before T016

- [X] T015 [US2] Write **failing** unit tests for AAR scenarios in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodPreprocessorTest.java` — scenarios: `until` date correct → no context; `until` date wrong → context emitted with correct `expectedEndDate`; AAR under YROEW only (no CO parent) → NOT checked (CO-only rule); AAR under CONI parent → checked; `hearingDay` null on request → skip + WARN; missing `numberOfDaysToAbstainFromConsumingAnyAlcohol` prompt → skip + WARN; missing `until` prompt → skip + WARN; **`numberOfDaysToAbstainFromConsumingAnyAlcohol` value = "0" → skip + WARN**; **`numberOfDaysToAbstainFromConsumingAnyAlcohol` value is negative (e.g. "-1") → skip + WARN**; two offences, one correct one wrong → exactly one context emitted for the wrong offence. **Confirm FAIL before T016.**

### Implementation for User Story 2

- [X] T016 [US2] Add AAR period check to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodPreprocessor.java` — inside the `hasCo` guard: `parseInt()` helper reading INT `promptValue`; expected date `hearingDay.plusDays(days).minusDays(1)`; date formatted with `DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.UK)`; emit context keyed `"AAR:<defendantId>:<offenceId>"` when `until ≠ expectedEnd`. (TDD green for T015)

**Checkpoint**: Run `gradle test --tests "*.CurfewPeriodPreprocessorTest"` — all tests including AAR scenarios green. Run `gradle test --tests "*.CurfewPeriodContextTest"` — still green.

---

## Phase 5: User Story 3 — Error Response Format Verification (Priority: P2)

**Goal**: Confirm the full end-to-end response carries: `ruleId = "DR-COEW-002"`, `severity = "ERROR"`, `message` containing `"End date based on entered period: DD/MM/YYYY"`, `affectedOffences` with the violated offence, and `affectedDefendants` with the defendant whose result triggered the violation.

**Independent Test**: Execute the integration test suite via `gradle test --tests "*.CurfewPeriodRuleIntegrationTest"` — all 12 scenarios green.

### Tests for User Story 3 ⚠️ Write and confirm FAILING before full verification

- [X] T017 [P] [US3] Write **failing** integration test scenarios S1–S6 (CUR day-period mismatch, CUR day-period correct, CURE week-period mismatch, YRC2 under YROEW, YRC1 correct, AAR correct) in `src/test/java/uk/gov/hmcts/cp/integration/CurfewPeriodRuleIntegrationTest.java` — extends `IntegrationTestBase`; uses MockMvc `POST /validate`; asserts `$.errors[0].ruleId`, `$.errors[0].message`, `$.errors[0].affectedOffences[0].offenceId`, `$.errors[0].affectedDefendants[0].defendantId`. **Confirm FAIL (returns 0 errors or wrong message) before implementation is complete.**
- [X] T018 [P] [US3] Write **failing** integration test scenarios S7–S12 (AAR under COEW wrong date, AAR under COEW correct, AAR under YROEW-only wrong date → no error, two defendants one violated, CUR period in months correct, CUR period in months wrong) in `src/test/java/uk/gov/hmcts/cp/integration/CurfewPeriodRuleIntegrationTest.java`. **Confirm FAIL before full verification.**

### Verification for User Story 3

- [X] T019 [US3] Run `gradle test --tests "*.CurfewPeriodRuleIntegrationTest"` and confirm all 12 scenarios pass green

**Checkpoint**: All user stories independently verifiable. End-to-end error format confirmed.

---

## Phase N: Polish & Build Quality

**Purpose**: Static analysis, style enforcement, and mandatory build-loop agent review.

- [X] T020 Run `gradle checkstyleMain` — fix any Checkstyle violations (Google style, `maxWarnings = 0`) in new and modified files
- [X] T021 [P] Run `gradle pmdMain` — fix any PMD violations (`ignoreFailures = false`) in new and modified files
- [X] T022 Run `gradle build` — confirm full clean build (compile + Checkstyle + PMD + all tests)
- [X] T023 Invoke **code-reviewer** agent (Read-only) — check logic correctness, null safety, layering, SLF4J-only, severity-ceiling compliance, no `System.out`, no wildcard imports; apply all NEEDS CHANGES findings
- [X] T024 Invoke **qa** agent (Read/Write/Bash) — verify TDD discipline (failing test authored before production code), run `gradle test`, confirm PASS
- [X] T025 Invoke **spec-validator** agent (Read-only) — check `DR-COEW-002.yaml` schema compliance, `preprocessing.type = "curfew-period-check"` resolves to registered bean, `violationCount > 0` CEL compiles, `${expectedEndDate}` placeholder resolvable, `affectedOffenceSet = "violatedOffenceIds"` handled by context; confirm COMPLIANT

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: T001 and T002 must complete; T003–T010 can then start in parallel
- **Phase 3 (US1)**: Phase 2 must be complete; T011 → T012, T013 → T014 within the phase
- **Phase 4 (US2)**: Phase 3 must be complete (shares preprocessor file); T015 → T016
- **Phase 5 (US3)**: Phases 3 and 4 must be complete; T017 and T018 can run in parallel
- **Phase N (Polish)**: Phase 5 must be complete

### Strict Within-Phase Ordering

```
T001 (YAML) → T002 (lib bump)
T003 [P], T004 [P] (can run together after T002)
T005 [P] (failing tests) → T006 (implementation, needs T003)
T007 [P] (failing tests) → T008 (implementation, needs T003)
T009a [P] (failing CelValidationRule tests) → T009 (CelValidationRule, needs T003 + T006)
T010 (preprocessor skeleton, needs T001 + T004 + T008)
T011 [P] (failing tests US1-CUR) → T012 (CUR implementation, needs T010)
T013 [P] (failing tests US1-CURE) → T014 (CURE implementation, needs T012)
T015 (failing tests US2-AAR) → T016 (AAR implementation, needs T014)
T017 [P], T018 [P] (failing ITs) — can be written once T010 exists
T019 (confirm ITs green — needs T016 + T017 + T018)
T020 → T021 [P] → T022 → T023 → T024 → T025
```

### TDD Pairs (test must FAIL before implementation)

| Failing test task | Implementation task |
|-------------------|---------------------|
| T005 | T006 |
| T007 | T008 |
| T009a | T009 |
| T011 | T012 |
| T013 | T014 |
| T015 | T016 |
| T017, T018 | T019 (verify green) |

---

## Parallel Opportunities

```bash
# Phase 2 — run together after T002:
T003  # RuleEvaluationContext.stringVariables()
T004  # PreprocessingDefinition.yroShortCodes
T005  # MessageTemplateResolverTest failing tests
T007  # CurfewPeriodContextTest failing tests

# Phase 5 — write ITs together:
T017  # IT scenarios S1–S6
T018  # IT scenarios S7–S12

# Polish — run together:
T020  # checkstyleMain
T021  # pmdMain
```

---

## Implementation Strategy

### MVP (US1 only — Curfew violations)

1. Complete Phase 1 (T001, T002)
2. Complete Phase 2 (T003–T010)
3. Complete Phase 3 (T011–T014) — CUR/CURE/YRC2/YRC1 violations detected
4. **STOP and validate**: `gradle test --tests "*.CurfewPeriodPreprocessorTest"` green
5. Extend with IT for curfew-only scenarios (T017 subset)

### Full Delivery

1. MVP above
2. Phase 4 (T015–T016) — AAR violations
3. Phase 5 (T017–T019) — full integration test suite green
4. Phase N (T020–T025) — clean build + agent review

---

## Notes

- **T001 is non-negotiable first** — YAML before any Java (Constitution Principle I)
- **T002 is blocking** — `CurfewPeriodPreprocessor` cannot compile against the DURATION prompt structure until `0.1.8` is available
- All TDD pairs: confirm the test **FAILS for the correct reason** (assertion failure, not compilation error) before writing production code
- The preprocessor map key `"TYPE:defendantId:offenceId"` is intentional — see `research.md` Decision 2
- `getOffenceIdSet()` in `CurfewPeriodContext` returns `List.of(offenceId)` for **any** set name — no switch needed
- Do not test override/severity-ceiling logic per-rule — covered framework-wide in `ValidationRuleOverrideIntegrationTest` (see `design_rules.md`)
