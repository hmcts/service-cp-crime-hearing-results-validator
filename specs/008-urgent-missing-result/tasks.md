---
description: "Task list for DR-URG-008 — URGENT Result Missing Warning (CRA-22)"
---

# Tasks: URGENT Result Missing Warning (CRA-22 / DR-URG-008)

**Input**: Design documents from `specs/008-urgent-missing-result/`
**Branch**: `CRA-22-urgent-missing-result`
**Date**: 2026-09-09

**Pre-flight confirmed**:
- `gradle/libs.versions.toml` already references `api-cp-crime-hearing-results-validator:0.2.8-cra-22`
- `OffenceDto$BailStatusEnum.class` confirmed present in jar (CHD-2485 delivered)
- No `ConditionalBail*.java`, `DR-URG-008.yaml`, or `V1.009*.sql` files exist yet

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US4)

---

## Phase 1: Setup ✅ COMPLETE

**No action required.** `api-cp-crime-hearing-results-validator:0.2.8-cra-22` is already in
`gradle/libs.versions.toml`. `BailStatusEnum.B` (conditional bail) is available.

---

## Phase 2: Foundational (Blocking Prerequisite)

**Purpose**: Extend the shared `PreprocessingDefinition` record so `ConditionalBailPreprocessor`
can receive its `bailEndingShortCodes` list from YAML. All existing preprocessors are unaffected —
`@Builder` defaults the new field to `null`.

**⚠️ CRITICAL**: Must be complete before implementing `ConditionalBailPreprocessor` or the YAML rule.

- [ ] T001 Add `List<String> bailEndingShortCodes` field to the `@Builder` record in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` (append after `alcoholAbstinenceShortCodes`; no existing tests break)

**Checkpoint**: `gradle test` still green (no regressions from the new field).

---

## Phase 3: US1 + US2 (P1) — Core Rule: Warning Fires + URGENT Suppression 🎯 MVP

**Goal**: DR-URG-008 returns a defendant-level WARNING when all conditional-bail offences have
bail-ending results and no URGENT is present (US1), and suppresses the warning when URGENT is
recorded on at least one conditional-bail offence (US2).

**Independent Test**:
```bash
gradle test --tests "*ConditionalBailContextTest" \
            --tests "*ConditionalBailPreprocessorTest" \
            --tests "*UrgentMissingWarningIntegrationTest"
```
US1: response contains a `WARNING` at `DEFENDANT` level with the prescribed message.
US2: same request with URGENT added → no warning returned.

### TDD — ConditionalBailContext ⚠️ Skeleton stub first, then test (assertion failure, not compile failure — Principle VIII)

> **Java TDD note**: The class must exist (as an empty stub) before the test can compile and fail at
> assertion level. "Class not found" is a build error, not a red-green cycle. Create the stub first,
> then write the test, then implement.

- [ ] T002 [US1] Create a skeleton `ConditionalBailContext.java` record stub — just the record signature and method stubs (methods throw `UnsupportedOperationException` or return `null`/`0`) — then write `ConditionalBailContextTest` covering: `toCelContext()` returns correct `conditionalBailOffenceCount`/`bailEndedCount`/`hasUrgentCount` longs; `getDefendantIdSet("defendantId")` returns a single-element list; `getDefendantIdSet("unknown")` throws `IllegalArgumentException`; `allOffenceIds()` returns configured list; `defendantName()` returns name. Test must compile and fail at assertion level. Files: stub at `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailContext.java`; test at `src/test/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailContextTest.java`

- [ ] T003 [US1] Fill in the full `ConditionalBailContext` implementation: fields `defendantId`, `defendantName`, `conditionalBailOffenceCount`, `bailEndedCount`, `hasUrgentCount`, `allOffenceIds`; `toCelContext()` returns `Map.of` of the three longs; `getDefendantIdSet("defendantId")` returns `List.of(defendantId)`, unknown key throws; `getOffenceIdSet("allOffenceIds")` returns `allOffenceIds`, unknown key throws in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailContext.java` → run `ConditionalBailContextTest` green

### TDD — ConditionalBailPreprocessor ⚠️ Skeleton stub first, then ALL scenarios (assertion failure, not compile failure)

> Create an empty `ConditionalBailPreprocessor` stub (returns empty map) before writing any tests, so
> all test scenarios compile and fail at assertion level. Write all scenarios before implementing.

- [ ] T004 [US1] Create a skeleton `ConditionalBailPreprocessor.java` stub (`@Component`, `type()` returns `"conditional-bail-urgent-check"`, `preprocess()` returns empty map), then write failing `ConditionalBailPreprocessorTest` — fire scenarios: AC1 (all conditional-bail offences → Category F result, no URGENT → fires), AC2 (all → DS, no URGENT → fires), AC3 (all → RI family, no URGENT → fires), AC4 (all → WOFN, no URGENT → fires), AC5 (mixed bail-ending types F+DS+RI, no URGENT → fires). Tests must compile and fail at assertion level. Files: stub at `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailPreprocessor.java`; test at `src/test/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailPreprocessorTest.java`

- [ ] T005 [P] [US2] Add suppression, edge-case, and null-safety scenarios to `ConditionalBailPreprocessorTest`: URGENT present on one conditional-bail offence → does NOT fire; not all conditional-bail offences have bail-ending results → does NOT fire; no conditional-bail offences (null or non-B bailStatus) → does NOT fire; multi-defendant → only qualifying defendant produces context where CEL fires; null offences list, null resultLines, null bailStatus (each safe no-op); **EC3**: defendant has CB offence (bail-ended, no URGENT) + non-CB offence with URGENT result → `hasUrgentCount` is 0 → warning fires (URGENT on non-CB offence must NOT suppress); **EC1**: defendant has CB offence (NOT bail-ended) + non-CB offence with bail-ending result → only CB offences counted → `bailEndedCount < conditionalBailOffenceCount` → warning does NOT fire; CB offence with zero result lines → treated as not bail-ended → warning does NOT fire in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailPreprocessorTest.java`

- [ ] T006 [US1] Create `ConditionalBailPreprocessor` `@Component` with qualifier `"conditional-bail-urgent-check"`: build offenceMap from `request.getOffences()`; build resultsByOffence via `PreprocessorHelper.groupResultsByOffence`; build defendant groups via `PreprocessorHelper.groupLinesByDedupedDefendant`; build `bailEndingUpper` via `PreprocessorHelper.upperSet(config.bailEndingShortCodes())`; for each defendant group filter to `BailStatusEnum.B` offences; count `bailEndedCount` (category==F OR shortCode∈bailEndingUpper); set `hasUrgentCount` (1 if any conditional-bail offence line has URGENT, else 0); emit one `ConditionalBailContext` per defendant group in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailPreprocessor.java` → run `ConditionalBailPreprocessorTest` green

### YAML Rule and Flyway Migration

- [ ] T007 [P] [US1] Create `DR-URG-008.yaml` with `id: DR-URG-008`, `priority: 8000`, `preprocessing.type: conditional-bail-urgent-check`, `bailEndingShortCodes: [DS, RI, RIYDA, RIH, RIB, RILA, RILAB, REMYD, WOFN]`, condition AC1 CEL expression `conditionalBailOffenceCount > 0 && bailEndedCount == conditionalBailOffenceCount && hasUrgentCount == 0`, `severity: WARNING`, `validationLevel: DEFENDANT`, `messageTemplate` (exact prescribed text), `affectedDefendantSet: "defendantId"` in `src/main/resources/rules/DR-URG-008.yaml`

- [ ] T008 [P] [US1] Create Flyway migration inserting `('DR-URG-008', true, 'WARNING', now(), 'system')` into `validation_rule` in `src/main/resources/db/migration/V1.009__insert_dr_urg_008.sql`

### Integration Test (US1 + US2 scenarios)

- [ ] T009 [US1] Write `UrgentMissingWarningIntegrationTest` extending `IntegrationTestBase`: POST `/validate` with a request containing a defendant whose conditional-bail offences are all resulted with bail-ending results and no URGENT → assert response `errors` list is empty (rule is advisory; FR-007/SC-004), `warnings` contains exactly one issue at `DEFENDANT` level with `severity=WARNING` and the exact prescribed message in `src/test/java/uk/gov/hmcts/cp/integration/UrgentMissingWarningIntegrationTest.java`

- [ ] T010 [US2] Add URGENT-suppression scenario to `UrgentMissingWarningIntegrationTest`: same request as T009 but with URGENT added to one conditional-bail offence → assert zero issues returned for this rule in `src/test/java/uk/gov/hmcts/cp/integration/UrgentMissingWarningIntegrationTest.java`

**Checkpoint**: All three test classes green — US1 and US2 independently verified.

---

## Phase 4: US3 + US4 (P2) — Suppression + Per-Defendant Evaluation

**Goal**: US3 — no warning fires when bail has not fully ended or no conditional bail exists.
US4 — multi-defendant hearings produce warnings only for the qualifying defendant.

**Independent Test**:
```bash
gradle test --tests "*UrgentMissingWarningIntegrationTest"
```
US3: non-bail-ending result present → no warning. No conditional-bail offences → no warning.
US4: two defendants, only qualifying one receives the warning.

### US3 — Bail Not Fully Ended / No Conditional Bail

- [ ] T011 [P] [US3] Add bail-not-fully-ended integration scenario to `UrgentMissingWarningIntegrationTest`: POST where one conditional-bail offence has a non-bail-ending result (e.g., adjourned) → assert no warning returned in `src/test/java/uk/gov/hmcts/cp/integration/UrgentMissingWarningIntegrationTest.java`

- [ ] T012 [P] [US3] Add no-conditional-bail integration scenario to `UrgentMissingWarningIntegrationTest`: POST where defendant has offences but none has `bailStatus=B` → assert no warning returned in `src/test/java/uk/gov/hmcts/cp/integration/UrgentMissingWarningIntegrationTest.java`

### US4 — Multi-Defendant

- [ ] T013 [US4] Add two-defendant integration scenario to `UrgentMissingWarningIntegrationTest`: Defendant A has all conditional-bail offences bail-ended with no URGENT (qualifies); Defendant B has no conditional-bail offences (does not qualify) → assert exactly one `DEFENDANT`-level WARNING scoped to Defendant A's id, none for Defendant B in `src/test/java/uk/gov/hmcts/cp/integration/UrgentMissingWarningIntegrationTest.java`

**Checkpoint**: Full integration test class green — US3 and US4 independently verified.

---

## Phase 5: Polish & Build Loop

**Purpose**: Satisfy the mandatory build loop (workflow.md): Code Review → QA → Spec Validate →
fix → repeat until all agents return PASS/COMPLIANT.

- [ ] T014 Run `gradle checkstyleMain pmdMain pmdTest` and fix any violations in `ConditionalBailPreprocessor.java`, `ConditionalBailContext.java`, and `PreprocessingDefinition.java` (Google style, `maxWarnings=0`, PMD `ignoreFailures=false`; `pmdTest` covers test sources per constitution pre-merge checklist)

- [ ] T015 Run `gradle build` (full: compile + checkstyle + PMD + unit + integration tests) — must exit 0

- [ ] T016 Spawn `code-reviewer` agent on `ConditionalBailPreprocessor.java`, `ConditionalBailContext.java`, `PreprocessingDefinition.java`, and `DR-URG-008.yaml` — fix all NEEDS CHANGES findings, then re-run until agent returns PASS (Principle IV)

- [ ] T017 Spawn `qa` agent to verify TDD discipline (failing tests committed before production code) and test completeness across `ConditionalBailContextTest.java`, `ConditionalBailPreprocessorTest.java`, and `UrgentMissingWarningIntegrationTest.java` — fix any FAIL findings, then re-run until agent returns PASS (Principle IV)

- [ ] T018 Spawn `spec-validator` agent on `src/main/resources/rules/DR-URG-008.yaml` — verify CEL expression compiles, schema compliance, `preprocessing.type: conditional-bail-urgent-check` resolves to registered bean — fix any DRIFT DETECTED findings, then re-run until agent returns COMPLIANT (Principle IV)

- [ ] T019 Run `gradle test --tests "*CrossRuleRegressionIntegrationTest"` to confirm DR-URG-008 does not interfere with existing rules

- [ ] T020 [P] Write `UrgentMissingWarningApiHttpLiveTest` with live HTTP scenarios: POST fires warning (all bail ended, no URGENT); POST with URGENT present returns no warning in `src/apiTest/java/uk/gov/hmcts/cp/http/UrgentMissingWarningApiHttpLiveTest.java`

- [ ] T021 Run `gradle api` — full live API test suite green

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 2 (Foundational)**: No dependencies — start immediately
- **Phase 3 (US1+US2)**: Depends on Phase 2 (T001) — `PreprocessingDefinition` must have `bailEndingShortCodes` before writing the preprocessor
- **Phase 4 (US3+US4)**: Depends on Phase 3 completion (integration test class exists; no new production code)
- **Phase 5 (Polish)**: Depends on all prior phases complete

### Within Phase 3

1. T001 (Phase 2) must complete first
2. **T007 + T008** — create `DR-URG-008.yaml` and the Flyway migration **before** any Java implementation (Principle I: YAML is the contract; Java implements it)
3. T002 → T003 (context record: skeleton stub → failing test → full implementation — TDD)
4. T004 + T005 together (preprocessor: skeleton stub → all test scenarios failing → then implement)
5. T006 (preprocessor full implementation — after T004+T005 are failing at assertion level)
6. T009 → T010 (integration test: baseline scenario then suppression)

### Parallel Opportunities

- T007 (YAML) and T008 (SQL migration) are independent — run in parallel
- T005 (suppression test scenarios) can be added in parallel with T004 (fire scenarios) if two developers — same file, coordinate
- T011 and T012 (both add scenarios to integration test) are logically parallel but touch the same file — coordinate or sequence
- T020 (API test) can be written in parallel with T014–T019 (build loop)

---

## Parallel Example: Phase 3 Implementation Day

```
Start of day — run in parallel (Principle I: YAML contract before Java):
  Session A (YAML + migration — no Java dependencies):
    T007: Create DR-URG-008.yaml  ← YAML first per Principle I
    T008: Create V1.009__insert_dr_urg_008.sql

  Session B (context record TDD):
    T002: Create ConditionalBailContext skeleton stub, then write failing test
    T003: Fill in ConditionalBailContext implementation → ConditionalBailContextTest green

After Session B completes T003, start preprocessor TDD:
  T004: Create ConditionalBailPreprocessor skeleton stub, then write failing fire scenarios
  T005: Add suppression / edge-case / null-safety scenarios to same test class
  T006: Implement ConditionalBailPreprocessor fully → ConditionalBailPreprocessorTest green
```

---

## Implementation Strategy

### MVP (US1 + US2 — P1 only)

1. Phase 2: Add `bailEndingShortCodes` to `PreprocessingDefinition`
2. Phase 3 entirely: context record + preprocessor (TDD) + YAML + migration + integration test
3. **STOP and VALIDATE**: `gradle test --tests "*ConditionalBailContextTest" "*ConditionalBailPreprocessorTest" "*UrgentMissingWarningIntegrationTest"` all green
4. Spawn code-reviewer + spec-validator; fix findings

### Incremental Delivery

1. Phase 2 + Phase 3 → MVP (US1+US2, P1) — conditional bail warning fires and is suppressible
2. Phase 4 → US3+US4 (P2) — suppression edge cases and multi-defendant verified via integration tests
3. Phase 5 → Full build loop and API live test → ship

### Key TDD Checkpoints

| After task | Command | Expected |
|------------|---------|----------|
| T002 (after stub + test) | `gradle test --tests "*ConditionalBailContextTest"` | **FAIL** (assertion failure — stub returns null/0, not expected values) |
| T003 | `gradle test --tests "*ConditionalBailContextTest"` | GREEN |
| T004+T005 (after stub + tests) | `gradle test --tests "*ConditionalBailPreprocessorTest"` | **FAIL** (assertion failure — stub returns empty map) |
| T006 | `gradle test --tests "*ConditionalBailPreprocessorTest"` | GREEN |
| T009 | `gradle test --tests "*UrgentMissingWarningIntegrationTest"` | GREEN |
| T015 | `gradle build` | GREEN (exit 0) |
| T021 | `gradle api` | GREEN |

---

## Notes

- **Override/severity-ceiling IT** is NOT included — already proven in `ValidationRuleOverrideIntegrationTest` (design_rules.md). Do not add per-rule override tests.
- **[P]** tasks touch different files or can be independently edited — confirm no file conflict before parallelising tasks that add scenarios to the same test class (T011/T012)
- URGENT code (`"URGENT"`) is hardcoded in `ConditionalBailPreprocessor` as a private constant — not YAML-configurable
- Conditional bail detection uses `OffenceDto.BailStatusEnum.B.equals(offence.getBailStatus())` — hardcoded enum, not a YAML string
- `getOffenceIdSet` on `ConditionalBailContext` is used if `affectedOffenceSet` is set on a YAML condition — DR-URG-008 uses `affectedDefendantSet` instead, but the method must still be implemented per the `RuleEvaluationContext` contract
