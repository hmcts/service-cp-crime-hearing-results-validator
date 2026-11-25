# Tasks: CRA-362 – Pre-Release Bail Condition Warning (DR-BAIL-016)

**Input**: Design documents from `specs/017-pre-release-bail-condition-warning/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

**Tests**: TDD is **non-negotiable** (Constitution Principle VIII). Every test task MUST run and FAIL for the correct reason (assertion failure, not compilation error) before the matching implementation task is started.

**Organization**: Phases 1–2 are setup and foundational. Phases 3–5 map to spec user stories by priority. Phase 6 covers integration and API testing. Phase 7 is polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelisable (different files, no unfinished dependencies)
- **[Story]**: User story label (US1–US7 from spec.md)
- File paths are always relative to repo root

---

## Phase 1: Setup (YAML Rule + Config)

**Purpose**: Lay the YAML contract and DB migration **before** any Java is written (Constitution Principle I — YAML/CEL Rule-First).

- [x] T001 Create `src/main/resources/rules/DR-BAIL-016.yaml` with all 5 conditions (AC1=Warning A, AC2=Warning B, AC6=Warning C, AC2A=Warning D, AC2B=Warning E), `priority: 17000`, `type: pre-release-bail-condition`, code lists per data-model.md
- [x] T002 [P] Create `src/main/resources/db/migration/V1.018__insert_dr_bail_016.sql` — single INSERT for `DR-BAIL-016` with `enabled=true`, `severity='WARNING'`
- [x] T003 [P] Extend `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` — add 3 new `List<String>` fields: `conditionalBailResultCodes`, `crownCourtBailCodes`, `triggerBailConditionCodes` (Lombok `@Builder`, append after existing fields)

**Checkpoint**: YAML rule is readable; PreprocessingDefinition compiles with new fields. `gradle compileJava` passes.

---

## Phase 2: Foundational (Context Record)

**Purpose**: The `PreReleaseBailContext` record is a prerequisite for the preprocessor and all tests. Must exist before any other Java work.

⚠️ **TDD**: Write the failing test first (T004), confirm it fails at assertion level, then implement (T005).

- [x] T004 Write failing `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailContextTest.java` — unit tests for `toCelContext()` (all 6 vars), `getOffenceIdSet("offenceId")`, `getOffenceIdSet("unknown")` throws, `allOffenceIds()`, `defendantName()` returns null; run `gradle test --tests "*PreReleaseBailContextTest"` → confirm FAIL
- [x] T005 Implement `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailContext.java` — Java record implementing `RuleEvaluationContext`, 6 `long` fields, `toCelContext()` returns 6-entry map, `getOffenceIdSet("offenceId")` returns `List.of(offenceId)`, `defendantName()` null, `allOffenceIds()` singleton; run `gradle test --tests "*PreReleaseBailContextTest"` → GREEN

**Checkpoint**: `PreReleaseBailContext` unit tests green.

---

## Phase 3: US1 + US2 + US6 + US7 – Adult/Youth/Sharing/No-warning Core Logic (Priority: P1) 🎯 MVP

**Goal**: Preprocessor detects the core trigger (custodial remand status C/L/S + named conditional bail result + named trigger bail condition) and correctly classifies adult vs youth vs non-trigger. Warning does not block sharing. No warning when trigger is absent.

**Maps to**: US1 (adult warning), US2 (youth warning), US6 (non-blocking), US7 (no false positives)

**Independent Test**: `gradle test --tests "*PreReleaseBailPreprocessorTest"` green for adult/youth/no-trigger/coexistence scenarios; `gradle build` passes.

### Tests (write before implementation — TDD)

- [x] T006 [US1] [US2] [US6] [US7] Write failing `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailPreprocessorTest.java` with `@Nested` groups and `@DisplayName`:
  - `[US1]` Adult (DOB=1990-01-01, hearingDay=2026-11-09), offence bailStatus=C, result=RC, PASS5 → `triggerCount=1, adultCount=1, youthCount=0, noDobCount=0, ccicCount=0, ccscCount=0`
  - `[US1]` Adult, result=RCBV, BAILREVC, REMCB, REMCBY, RCCCB — each variant produces adultCount=1
  - `[US1]` Adult, trigger conditions PASS1–PASS8, PORSC, PORS — each named code produces triggerCount=1
  - `[US2]` Youth (DOB=2010-01-01), bailStatus=S, result=REMCB, PORS → `youthCount=1, adultCount=0`
  - `[US6]` Multiple offences same defendant — each qualifying offence gets its own context entry
  - `[US7]` bailStatus=B → no context entry produced
  - `[US7]` bailStatus=C + result=RIB (non-conditional) → no context entry
  - `[US7]` bailStatus=C + result=RC but no trigger bail condition → no context entry
  - `[US7]` Null/empty offences and result lines → empty map returned
  - Run `gradle test --tests "*PreReleaseBailPreprocessorTest"` → confirm FAIL (class not found)

### Implementation

- [x] T007 Implement `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailPreprocessor.java`:
  - `@Component`, `type()` returns `"pre-release-bail-condition"`, no injected dependencies
  - `preprocess()` builds upper-cased code sets from config, indexes offences and defendants, calls `groupResultsByOffence`
  - Core filter: `offence.bailStatus ∉ CUSTODIAL_STATUSES` → skip; no conditional bail result code → skip; no trigger condition → skip
  - Age classification via `Period.between(dob, hearingDay).getYears()` (null-safe — no DOB → noDobCount=1)
  - `ccicCount`/`ccscCount` from which conditional bail code matched
  - Emits one `PreReleaseBailContext` per qualifying offence
  - Run `gradle test --tests "*PreReleaseBailPreprocessorTest"` → GREEN for US1/US2/US6/US7 scenarios

**Checkpoint**: `gradle test --tests "*PreReleaseBail*"` GREEN. Adult and youth warnings correctly identified; sharing not blocked; no false positives. Constitution Principle VIII satisfied for core scenarios.

---

## Phase 4: US3 + US4 + US5 – No-DOB, Crown Court, PORO1 (Priority: P2)

**Goal**: Extend preprocessor to handle no date of birth (Warning C), Crown Court transfer results (CCIC → Warning D, CCSC → Warning E), and the PORO1 free-text "Other" bail condition with accent/case-insensitive keyword matching (Warning A/B/C/D/E depending on result and age).

**Maps to**: US3 (no DOB), US4 (CCIC/CCSC), US5 (PORO1 keyword matching)

**Independent Test**: `gradle test --tests "*PreReleaseBailPreprocessorTest"` green for all no-DOB, CCIC, CCSC, and PORO1 scenarios.

### Tests (extend the test class — TDD)

- [x] T008 [US3] [US4] [US5] Extend `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailPreprocessorTest.java` with new `@Nested` groups:
  - `[US3]` No DOB (`defendant.dateOfBirth = null`), result=RC, PASS5 → `noDobCount=1, adultCount=0, youthCount=0`
  - `[US3]` Null hearingDay, result=RC, PASS5 → `noDobCount=1` (treat as unknown age)
  - `[US4]` Adult, bailStatus=C, result=CCIC, PASS1 → `ccicCount=1, adultCount=1`
  - `[US4]` Youth, bailStatus=L, result=CCIC, PORSC → `ccicCount=1, youthCount=1`
  - `[US4]` No DOB, result=CCIC, PASS3 → `ccicCount=1, noDobCount=1`
  - `[US4]` Adult, bailStatus=S, result=CCSC, PASS8 → `ccscCount=1, adultCount=1`
  - `[US5]` PORO1 prompt text "needs a passport" → `triggerCount=1` (stem "passport")
  - `[US5]` PORO1 prompt "PASSPORT" (uppercase) → `triggerCount=1` (case-insensitive)
  - `[US5]` PORO1 prompt "provide surety" → `triggerCount=1` (stem "suret")
  - `[US5]` PORO1 prompt "security deposit" → `triggerCount=1` (stem "securit")
  - `[US5]` PORO1 prompt "pasbort" (Welsh) → `triggerCount=1` (stem "asbort")
  - `[US5]` PORO1 prompt "mechnïwr" (Welsh) → `triggerCount=1` (stem "echniw" after NFD)
  - `[US5]` PORO1 prompt "mechniwr" (Welsh no accent) → `triggerCount=1`
  - `[US5]` PORO1 prompt "sicrwydd" (Welsh) → `triggerCount=1`
  - `[US5]` PORO1 prompt "report to probation officer" → `triggerCount=0` (no keyword)
  - `[US5]` PORO1 null prompt value → `triggerCount=0` (null-safe)
  - `[US5]` PORO1 prompt text "" (blank) → `triggerCount=0`
  - Run `gradle test --tests "*PreReleaseBailPreprocessorTest"` → confirm extended tests FAIL

### Implementation

- [x] T009 [P] Implement `containsTriggerKeyword(String text)` static utility in `PreReleaseBailPreprocessor`:
  - `Normalizer.normalize(text, Normalizer.Form.NFD)` → strip `\p{M}` via compiled `Pattern` → `toLowerCase(Locale.ROOT)`
  - Stem set: `Set.of("passport", "suret", "securit", "asbort", "echniw", "sicrwydd")`
  - `contains(stem)` for each; return true on first match
  - Null/blank guard: return false immediately
- [x] T010 [P] Add `anyPoro1WithKeyword(List<ResultLineDto> lines)` to `PreReleaseBailPreprocessor`:
  - Filter lines where `shortCode.upper == "PORO1"`
  - For each matching line, test `containsTriggerKeyword(prompt.getPromptValue())` over `line.getPrompts()`
  - Return true on first match; return false if no PORO1 lines or no keyword found
- [x] T011 Add no-DOB and Crown Court classification to `PreReleaseBailPreprocessor.preprocess()`:
  - Integrate `anyPoro1WithKeyword(lines)` into the existing trigger-condition check (OR with named codes)
  - Ensure `noDobCount=1` when `dob == null` or `hearingDay == null`
  - Ensure `ccicCount`/`ccscCount` correctly set from `crownCourtBailCodes` upper set
  - Run `gradle test --tests "*PreReleaseBailPreprocessorTest"` → ALL tests GREEN
- [x] T012 Run `gradle checkstyleMain pmdMain` and fix any findings in `PreReleaseBailPreprocessor.java` and `PreReleaseBailContext.java`

**Checkpoint**: ALL preprocessor unit tests green (US1–US7 coverage). No Checkstyle/PMD violations.

---

## Phase 5: Integration Tests – All ACs End-to-End (Priority: P1)

**Goal**: Full end-to-end verification via MockMvc + TestContainers — proves YAML, preprocessor, context, CEL expressions, and DB migration wire together correctly.

**Maps to**: US1–US7 (all acceptance criteria)

**Independent Test**: `gradle test --tests "*PreReleaseBailConditionRuleIT"` green.

### Tests (write before verifying green — TDD)

- [x] T013 Write `src/test/java/uk/gov/hmcts/cp/integration/PreReleaseBailConditionRuleIT.java` extending `IntegrationTestBase`:
  - `[US1]` Adult (DOB 18+), bailStatus=C, result=RC, PASS5 → 1 WARNING `DR-BAIL-016` condition AC1, Warning A message, `isValid=true`
  - `[US1]` Adult, result=RCBV — same Warning A
  - `[US2]` Youth (DOB <18), bailStatus=S, result=REMCB, PORS → Warning B, `isValid=true`
  - `[US3]` No DOB defendant, result=RC, PORSC → Warning C, `isValid=true`
  - `[US4-CCIC]` Any age, bailStatus=L, result=CCIC, PASS1 → Warning D, `isValid=true`
  - `[US4-CCSC]` Any age, bailStatus=C, result=CCSC, PASS8 → Warning E, `isValid=true`
  - `[US5-EN]` PORO1 prompt "provide a passport" → same Warning A as named bail condition
  - `[US5-CY]` PORO1 prompt "mechnïwr" → Warning A
  - `[US5-NONE]` PORO1 prompt "report to probation" → NO DR-BAIL-016 warning
  - `[US6]` Sharing not blocked — `isValid=true` on all WARNING scenarios
  - `[US7-status]` offence bailStatus=B, result=RC, PASS5 → NO DR-BAIL-016 warning
  - `[US7-result]` bailStatus=C, result=RIB, PASS5 → NO DR-BAIL-016 warning
  - `[US7-condition]` bailStatus=C, result=RC, no trigger bail code → NO warning
  - `[US4A-coexist]` Two rules fire simultaneously → both appear, neither suppressed (`isValid=true`)
  - `[FR-010]` For any triggering scenario assert the response top-level `errors` and `warnings` lists do NOT contain a DR-BAIL-016 entry at hearing or defendant level — DR-BAIL-016 issues MUST appear only inside `affectedOffences` entries (offence-level only)
  - Run `gradle test --tests "*PreReleaseBailConditionRuleIT"` → FAIL initially (rule not wired / no DB row)

### Verify integration

- [x] T014 Run `gradle test --tests "*PreReleaseBailConditionRuleIT"` after all production code is in place → GREEN; if failing, diagnose and fix (check YAML preprocessor type, DB migration applied, context keys match YAML condition variables)

**Checkpoint**: IT green. Full AC coverage verified end-to-end.

---

## Phase 6: API Live Tests + HTTP Scratch File

**Goal**: Cover key ACs against the real docker-compose stack; provide an IDE-runnable scratch file for manual verification of all scenarios.

**Maps to**: US1–US7

### API Test (live HTTP)

- [x] T015 Write `src/apiTest/java/uk/gov/hmcts/cp/http/PreReleaseBailConditionApiHttpLiveTest.java`:
  - `[US1]` Adult, bailStatus=C, result=RC, PASS5 → Warning A
  - `[US2]` Youth, bailStatus=S, result=REMCB, PORS → Warning B
  - `[US3]` No DOB, result=RCBV, PORSC → Warning C
  - `[US4]` result=CCIC, PASS1 → Warning D
  - `[US4]` result=CCSC, PASS8 → Warning E
  - `[US5]` PORO1 prompt "passport" → Warning A
  - `[US6]` Any WARNING scenario → `isValid=true` (sharing not blocked)
  - `[US7]` bailStatus=B → no DR-BAIL-016 warning
  - Follow `SexualOffenceNotificationApiHttpLiveTest` patterns; `baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082")`

### HTTP Scratch File

- [x] T016 [P] Create `specs/017-pre-release-bail-condition-warning/CRA-362.http`:
  - Header: baseUrl, validateUrl, userId variables; rule description; link to `DR-BAIL-016.yaml`
  - AC1 (adult, RC + PASS5): labelled `### AC1 — Adult conditional bail + passport condition → Warning A`
  - AC2 (youth, REMCB + PORS): → Warning B
  - AC6 (no DOB, RC + PORSC): → Warning C
  - AC2A (CCIC + PASS1): → Warning D
  - AC2B (CCSC + PASS8): → Warning E
  - AC3-EN (PORO1 prompt "needs a surety"): → Warning A
  - AC3-CY (PORO1 prompt "mechnïwr"): → Warning A
  - AC3-NONE (PORO1 prompt "report to probation"): → NO warning
  - AC4 (share after warning): → `isValid=true`
  - AC5-status (bailStatus=B): → NO DR-BAIL-016 warning
  - AC5-result (result=RIB): → NO DR-BAIL-016 warning
  - AC5-no-condition (result=RC, no bail condition): → NO DR-BAIL-016 warning

**Checkpoint**: `gradle api` passes against the docker-compose stack.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, final build validation, build loop agents.

- [x] T017 Update `README.md` — add `DR-BAIL-016` row to the validation rules table (rule ID, title, severity=WARNING, priority=17000, preprocessor=pre-release-bail-condition, description of trigger)
- [x] T018 Run `gradle build` — zero Checkstyle warnings, zero PMD violations, all tests pass
- [x] T019 Spawn `code-reviewer` agent on all new/modified Java files (`PreReleaseBailContext.java`, `PreReleaseBailPreprocessor.java`, `PreprocessingDefinition.java`); apply any NEEDS CHANGES findings
- [x] T020 [P] Spawn `qa` agent to verify TDD discipline (failing test before production code) and 100% branch coverage across unit + IT; apply any FAIL findings
- [x] T021 [P] Spawn `spec-validator` agent on `DR-BAIL-016.yaml` to confirm: schema compliance, `preprocessing.type` resolves to registered bean, all 5 CEL expressions compile, all placeholders in message templates resolve; apply any DRIFT DETECTED findings
- [x] T022 Re-run `gradle build` after agent fixes → confirm GREEN

**Final Checkpoint**: `gradle build` ✅ · `gradle api` ✅ · All agents PASS/COMPLIANT ✅

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately; T001, T002, T003 are all parallelisable
- **Phase 2 (Foundational)**: Requires T003 (PreprocessingDefinition) — T004 (failing test) can start as soon as T003 compiles
- **Phase 3 (US1/US2/US6/US7)**: Requires T005 (PreReleaseBailContext) before writing T006 (preprocessor tests)
- **Phase 4 (US3/US4/US5)**: Requires T007 (core preprocessor) — T008 (extended tests) extend T006's class
- **Phase 5 (IT)**: Requires T001 (YAML) + T002 (migration) + T011 (full preprocessor) + T005 (context)
- **Phase 6 (API + HTTP)**: Requires Phase 5 to be complete
- **Phase 7 (Polish)**: Requires all phases complete

### User Story Dependencies

- **US1 + US2 + US6 + US7 (P1)**: Can start after Foundational (Phase 2) — these drive Phase 3
- **US3 + US4 + US5 (P2)**: Can start after Phase 3 (core preprocessor exists to extend)
- **All stories share one preprocessor** — no cross-story conflicts; story distinction is in test scenarios, not in separate implementation files

### Within Each Phase (TDD enforcement)

1. Write failing test → run `gradle test --tests "..."` → confirm FAIL (assertion, not compilation)
2. Write production code → run same test → confirm GREEN
3. Run `gradle checkstyleMain pmdMain` → fix violations
4. Move to next task

### Parallel Opportunities

| Parallelisable group | Tasks |
|---------------------|-------|
| Phase 1 Setup | T002, T003 (independent files) |
| Phase 4 implementation | T009, T010 (independent methods) |
| Phase 6 | T016 (HTTP file, independent of API test T015) |
| Phase 7 agents | T020, T021 (independent agents) |

---

## Parallel Example: Phase 4

```bash
# T009 and T010 can be implemented concurrently:
Task: "containsTriggerKeyword() static method in PreReleaseBailPreprocessor"
Task: "anyPoro1WithKeyword() method in PreReleaseBailPreprocessor"
# Then T011 integrates them into preprocess()
```

---

## Implementation Strategy

### MVP (US1 — Adult Warning, Phases 1–3)

1. T001 → T003 (Setup)
2. T004 → T005 (Context record)
3. T006 → T007 (Core preprocessor: adult/youth/no-trigger)
4. **STOP and VALIDATE**: `gradle test --tests "*PreReleaseBail*"` → US1/US2/US6/US7 proven
5. Continue to Phases 4–7 for full AC coverage

### Incremental Delivery

1. Phase 1 (YAML + migration) → YAML reviewable by BAs immediately
2. Phase 2 (context record) → foundation unit-tested
3. Phase 3 (core preprocessor) → P1 stories covered
4. Phase 4 (extensions) → P2 stories covered
5. Phase 5 (IT) → end-to-end proven
6. Phase 6 (API + HTTP) → live-stack verified, IDE-runnable for manual testing
7. Phase 7 (polish) → build loop clean, agents PASS, merge-ready

---

## Phase 8: v2 Delta — Remove AC7 / Revert remandStatus (2026-11-17)

**Trigger**: Jira spec updated to v2. AC7 ("Production data check — remandStatus fallback") removed by product. The initial implementation overshot the spec; the following tasks bring the code back in line.

**All Phase 1–7 tasks above are DONE.** Only these v2 revert tasks remain.

### TDD step — write the new expected behaviour first

- [x] T023 In `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailPreprocessorTest.java` add a new `@Nested @DisplayName("NullBailStatusEdgeCases")` class (separate from `Ac7RemandStatusFallback` — this class must NOT be removed by T026) containing one test: given `offence.bailStatus=null`, `defendant.remandStatus=C`, `result=RC`, `trigger=PASS5`, the preprocessor produces **no** context entry (fail-open, FR-011). Run `gradle test --tests "*PreReleaseBailPreprocessorTest*NullBailStatus*"` → test must **FAIL** with the current code (current fallback emits a warning for remandStatus=C, so the "no warning" assertion fails). After T025 reverts the fallback, re-run → must **PASS**.

### Revert production code

- [x] T024 Revert `gradle/libs.versions.toml`: change `api-hearing-results-validator` back from `26.34` to `26.33`. Run `gradle compileJava` → must compile (api-cp-crime-hearing-results-validator:26.33 does NOT have `remandStatus` on `DefendantDto`).
- [x] T025 Simplify `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailPreprocessor.java`:
  - Replace `resolveEffectiveStatus(offence, defendant)` to return `offence.getBailStatus()` only — remove all defendant-fallback logic
  - Remove `java.util.Arrays` import (no longer used)
  - Remove `import uk.gov.hmcts.cp.openapi.model.DefendantDto.RemandStatusEnum` import
  - Remove the two-line cross-enum bridge comment (the bridge no longer exists)
  - Run `gradle compileJava` → GREEN

### Remove AC7 tests

- [x] T026 [P] Remove `Ac7RemandStatusFallback` nested class and all its test methods from `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreReleaseBailPreprocessorTest.java`
- [x] T027 [P] In `src/test/java/uk/gov/hmcts/cp/integration/PreReleaseBailConditionRuleIT.java`: (a) Remove `Ac7RemandStatusFallback` nested class and all its test methods; (b) Add a new `@Nested @DisplayName("NullBailStatusFailOpen")` class containing one IT scenario: POST a request with `offence.bailStatus` field absent from JSON (not present in the offence object), `result=RC`, `trigger=PASS5`, defendant with `dateOfBirth` set → assert no DR-BAIL-016 warning is present in the response (FR-011 coverage at IT layer)
- [x] T028 [P] Remove `remandStatusFallback_custodialC_shouldEmitWarningA` test method from `src/apiTest/java/uk/gov/hmcts/cp/http/PreReleaseBailConditionApiHttpLiveTest.java`

### Verify

- [x] T029 Run `gradle clean build` → must be GREEN (all tests pass, Checkstyle/PMD clean)
- [x] T030 Spawn `code-reviewer` agent on `PreReleaseBailPreprocessor.java` to confirm no AC7 remnants and no new violations
- [x] T031 Spawn `spec-validator` agent on `DR-BAIL-016.yaml` to confirm rule still compliant post-revert

**Checkpoint**: `gradle clean build` ✅ · No `remandStatus` or `RemandStatusEnum` references remain in validator source · API lib pinned at 26.33

---

## Notes

- `[P]` tasks operate on different files — no merge conflicts when run concurrently
- `[Story]` label maps each task to the user story it satisfies for traceability
- TDD is enforced by the QA agent in Phase 7 — do not skip the failing-test step
- DB migration `V1.018` must be applied before the IT (TestContainers picks it up automatically via Flyway/Liquibase on `IntegrationTestBase` startup)
- `PreprocessingDefinition` new fields (`conditionalBailResultCodes`, `crownCourtBailCodes`, `triggerBailConditionCodes`) use Lombok `@Builder` — existing tests pass null for these fields and do not need updating
- PORO1 keyword matching is a pure static method — no state, no I/O, fully unit-testable in isolation
- After Phase 7, do NOT commit — leave all committing to the user (project convention)
