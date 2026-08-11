# Tasks: Community Order End Date Validation (DR-COEW-005)

**Branch**: `dev/DD-42678-co-end-date-rule`
**Input**: Design documents from `specs/005-community-order-date-validation/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅

> This tasks list is a **retrofit**, authored 2026-08-05 after the fact. The work below already
> shipped on this branch across commits `1ace279` → `d00bc34` → `37c7600` → `46763eb` → `9ab8a30`.
> All tasks are marked `[X]` to reflect the current state of the branch; they are recorded here so
> the spec-kit trail (spec → plan → tasks) exists alongside the code, matching this repo's
> mandatory workflow (`.claude/rules/workflow.md`).

**TDD is mandatory** (Constitution Principle VIII). Every test task listed below was written and
confirmed failing before its corresponding implementation task, per the commit history.

**YAML-first** (Constitution Principle I). `DR-COEW-005.yaml` was authored before any Java change.

---

## Phase 1: Setup Baseline

- [X] T001 Confirm `./gradlew clean test` is green before any changes land (baseline at the time this rule was ported: `DR-SENT-002`, `DR-DISQ-001`, `DR-CTL-001`, `DR-YRO-001` already present and passing — 4 rules)

---

## Phase 2: Foundational (Blocking Prerequisites)

- [X] T002 Create `src/main/resources/rules/DR-COEW-005.yaml` with rule id `DR-COEW-005`, priority 4000, preprocessing type `community-order-end-date`, and all 4 conditions (AC2a/CUR, AC2b/CURE, AC2c/CURA, AC2d/AAR) with ERROR severity and exact message templates from spec FR-003/FR-004 — see data-model.md for full YAML
- [X] T003 [P] Add 5 new `List<String>` fields to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`: `communityOrderShortCodes`, `curfewShortCodes`, `curfewTagShortCodes`, `furtherCurfewShortCodes`, `alcoholAbstinenceShortCodes` (the latter three are shared with `YouthRehabilitationPreprocessor`)
- [X] T003a Create `src/main/resources/db/migration/V1.005__insert_dr_coew_005.sql` seeding `('DR-COEW-005', true, 'ERROR')` into `validation_rule`

**Checkpoint**: T002 + T003 + T003a complete. Rule discoverable at startup; DB override seeded enabled.

---

## Phase 3: User Story 1 — Requirement End Date Cannot Exceed Order End Date (AC2) (Priority: P1) 🎯 MVP

**Goal**: Detect when any community order requirement (CUR, CURE, CURA, AAR) has an end date strictly later than its parent community order's end date, and emit one ERROR per violating requirement type per defendant (or defendant group, see US1a).

### Tests for User Story 1

- [X] T004 [US1] `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContextTest.java` covering: `toCelContext()` returns map with all 4 keys (`curViolationCount`, `cureViolationCount`, `curaViolationCount`, `aarViolationCount`); `getOffenceIdSet()` returns correct list for each of the 5 named sets; throws `IllegalArgumentException` for unknown set name
- [X] T006 [US1] `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessorTest.java` covering AC2 scenarios: CUR end date after order end date → `curViolationCount=1`; equal dates → no violation; CURE endDateOfTag after order → `cureViolationCount=1`; CURA endDate after order → `curaViolationCount=1`; AAR until date after order → `aarViolationCount=1`; multiple offences, only one violating; multiple defendants, only affected one has non-zero counts; null/blank promptValue → skip gracefully, count=0
- [X] T008 [US1] `src/test/java/uk/gov/hmcts/cp/integration/CommunityOrderEndDateRuleIntegrationTest.java` extending `IntegrationTestBase` with `@Nested` classes for Scenarios 6–13 (renumbered from the original spec) — CO + CUR/CURE/CURA/AAR violations, valid boundary (equal dates), two violations on one order, multiple defendants with mixed validity, and Share-button suppression — using `$.errors.validationIssues[?(@.ruleId=='DR-COEW-005')]` JsonPath filter

### Implementation for User Story 1

- [X] T005 [US1] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContext.java` as a Java record implementing `RuleEvaluationContext`
- [X] T007 [US1] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessor.java` as `@Component` implementing `ValidationPreprocessor` with `type()` returning `"community-order-end-date"`, delegating shared logic to `PreprocessorHelper`
- [X] T009 [US1] Update `src/test/java/uk/gov/hmcts/cp/config/ValidationRuleAutoConfigurationTest.java`: add `new CommunityOrderEndDatePreprocessor()` to the `PreprocessorRegistry` constructor list; update rule-count assertion to `hasSize(5)` and add `"DR-COEW-005"` to `containsExactlyInAnyOrder(...)`

**Checkpoint**: AC2 detection green; 5 rules discovered at startup.

---

## Phase 3a: Shared Preprocessor Plumbing Extraction

**Goal**: Avoid duplicating short-code matching, defendant grouping, and prompt-date parsing between `CommunityOrderEndDatePreprocessor` and `YouthRehabilitationPreprocessor`.

- [X] T010 [P] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessorHelper.java` — stateless static-utility class exposing `upperSet`, `upperOrNull`, `hasUpperCode`, `anyShortCodeIn`, `groupByDefendant`, `buildDefendantDedupeKeys`, `buildDefendantNames`, `buildFullName`, `parsePromptDate`, `isRequirementViolated`
- [X] T011 [P] `src/test/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessorHelperTest.java` covering each helper method in isolation, including `masterDefendantId` dedupe-key resolution (present, blank, absent)
- [X] T012 Refactor `CommunityOrderEndDatePreprocessor` to delegate to `PreprocessorHelper` instead of its own private copies of this logic; re-run T004/T006/T008 to confirm no regression

**Checkpoint**: `PreprocessorHelper` extracted; both `CommunityOrderEndDatePreprocessor` and `YouthRehabilitationPreprocessor` consume it; no behavioural change.

---

## Phase 3b: `masterDefendantId` Grouping (new capability beyond original AC2 scope)

**Goal**: Fold `defendantId`s that share a `masterDefendantId` into a single `CommunityOrderContext`, matching `CustodialPreprocessor`'s and `YouthRehabilitationPreprocessor`'s existing pattern (spec FR-005, Scenario 9).

- [X] T012a [US1a] Add `MasterDefendantIdGrouping` nested test class to `CommunityOrderEndDatePreprocessorTest`: CO and requirement lines split across two `defendantId`s sharing a `masterDefendantId` merge into one context; a defendant with a blank `masterDefendantId` falls back to its own `defendantId` as the group key
- [X] T012b [US1a] Wire `CommunityOrderEndDatePreprocessor.preprocess()` to fold groups via `PreprocessorHelper.buildDefendantDedupeKeys()` before accumulating violation counts

**Checkpoint**: `masterDefendantId` grouping tests pass; `CommunityOrderEndDatePreprocessorTest` fully green.

---

## Phase 4: User Story 2 — Error Summary at Top of Screen (AC4) (Priority: P2)

- [X] T013 [P] [US2] `ErrorSummaryResponseStructure` nested class in `CommunityOrderEndDateRuleIntegrationTest` covering: exact message text per FR-004; `${defendantName}`/`${defendantNames}` placeholder resolution; multiple distinct AC2 errors (e.g. AC2a + AC2d) both appear; valid defendant name never appears in an error message; `affectedDefendants[0].defendantId` matches the triggering defendant (or group)

**Checkpoint**: All error summary response assertions pass.

---

## Phase 5: User Story 3 — Inline Error Per Offence (AC5) (Priority: P2)

- [X] T014 [P] [US3] `InlineErrorOffenceScoping` nested class in `CommunityOrderEndDateRuleIntegrationTest` covering: 1 defendant with 3 offences, only offence 2 violating → `affectedOffences` contains exactly offence 2's ID; both offences violating → both IDs present; mixed CUR/AAR on different offences of the same defendant → two separate errors, each pinned to its own offence ID via `$.errors.validationIssues[?(@.affectedOffences[0].message == '...')]`

**Checkpoint**: Offence-scoping tests pass.

---

## Phase 6: Polish & Quality Gates

- [X] T015 `./gradlew checkstyleMain` clean for all new/modified files: `PreprocessingDefinition.java`, `PreprocessorHelper.java`, `CommunityOrderContext.java`, `CommunityOrderEndDatePreprocessor.java`
- [X] T016 [P] `./gradlew pmdMain` clean
- [X] T017 [P] No `System.out` / `System.err` / `printStackTrace` in new Java (grep check; `PreprocessorHelper` uses `@Slf4j` only)
- [X] T018 `./gradlew clean build` — BUILD SUCCESSFUL with all 5 rules discovered and all tests passing
- [X] T019 [P] `./gradlew jacocoTestReport` — meaningful coverage on `PreprocessorHelper`, `CommunityOrderEndDatePreprocessor`, `CommunityOrderContext`

---

## Phase 7: Retrofit Documentation *(2026-08-05, this pass)*

- [X] T020 Author `specs/005-community-order-date-validation/{spec,plan,research,data-model,tasks}.md` and `checklists/requirements.md` on `dev/DD-42678-co-end-date-rule`, ported from `team/DD-41653` and updated to describe the `DR-COEW-005` rename, `PreprocessorHelper` extraction, `masterDefendantId` grouping, and `enabled: true` default that actually shipped here

---

## Phase 8: User Story 4 — Requirement Duration End Date Validation (DD-41655) (Priority: P1)

**Goal**: Detect when a CUR/CURE/AAR requirement's own recorded end date does not match its calculated duration, independent of and additive to the Phase 3 (AC2) checks. Ported from PR #116 (`DD-41655-requirement-duration-validation`), which was never merged into this branch's history — see plan.md's [Port Notes](plan.md#port-notes-2026-08-05--dd-41655-requirement-duration-end-date-validation).

- [X] T021 Add three new conditions (DUR-CUR, DUR-CURE, DUR-AAR) to `src/main/resources/rules/DR-COEW-005.yaml` with ERROR severity, `calculatedValueSet`, and exact message templates from spec FR-015/FR-016
- [X] T022 [P] Add `calculatedValueSet` field to `ConditionDefinition.java`
- [X] T023 [P] Add default `getCalculatedValue(setName, offenceId)` method to `RuleEvaluationContext.java`
- [X] T024 [P] Add 6-arg `resolve(...)` overload (with `extraPlaceholders`) to `MessageTemplateResolver.java`
- [X] T025 [P] Add unit-aware period parsing (`ParsedPeriod`, `PERIOD_PATTERN`, `parsePromptPeriod`) local to `CommunityOrderEndDatePreprocessor.java` — kept out of `PreprocessorHelper` since the `"<n> <unit>"` format and `ChronoUnit` arithmetic are specific to this duration calculation, not a generic prompt-parsing primitive
- [X] T026 Write `CommunityOrderContextTest` duration-mismatch nested class (test-first), then extend `CommunityOrderContext.java` with 9 new fields and `getCalculatedValue(...)` override to pass
- [X] T027 Write `CommunityOrderEndDatePreprocessorTest` DUR-CUR/DUR-CURE/DUR-AAR nested classes plus a CURA-exclusion/multi-defendant cross-cutting class (test-first), then extend `CommunityOrderEndDatePreprocessor.java` with the duration-mismatch checks to pass
- [X] T028 Extend `CelValidationRule.java` with the `calculatedValueSet` branch resolving `${calculatedEndDate}` per offence
- [X] T029 Write `CommunityOrderEndDateRuleIntegrationTest` User Stories 4–7 (DUR-CUR, DUR-CURE, DUR-AAR, and combined-display scenarios)
- [X] T030 [P] Extend `RuleDefinitionTest` and `MessageTemplateResolverTest` with coverage for the new YAML fields/overload
- [X] T031 [P] Add a DUR-CUR scenario to `CommunityOrderEndDateApiHttpLiveTest.java` (live HTTP, `gradle api`)
- [X] T032 Update `specs/005-community-order-date-validation/{spec,plan,data-model}.md` with User Story 4, FR-015–FR-017, SC-008, A-012/A-013, and the Port Notes section
- [X] T033 Code-review pass (2 rounds) against this repo's own git history: carried forward two post-PR#116 fixes discovered in `team/DD-41655-CO-duration-validation` (unit-aware period parsing — commit `1dd5dba`; corrected AAR prompt-ref key — commit `6199910`) and fixed one additional overflow-handling gap found during the port itself (`ArithmeticException` alongside `DateTimeException` around `LocalDate.plus(...)`) — see plan.md Port Notes

**Checkpoint**: `./gradlew test checkstyleMain checkstyleTest pmdMain` all green; all 7 `DR-COEW-005` conditions covered end-to-end; no DB migration required.

---

## Dependencies & Execution Order

```
Phase 1 (Setup/Baseline)
    └── Phase 2 (Foundational: YAML + PreprocessingDefinition + migration)  [BLOCKS ALL]
            ├── Phase 3 (US1 — AC2)          ← MVP deliverable
            │       ├── Phase 3a (shared PreprocessorHelper extraction)
            │       ├── Phase 3b (masterDefendantId grouping)
            │       ├── Phase 4 (US2 — AC4 response structure)
            │       └── Phase 5 (US3 — AC5 offence scoping)
            └── Phase 6 (Polish)             ← after all phases complete
Phase 7 (Retrofit docs) — independent of the above; documents what already shipped
Phase 8 (DD-41655 duration validation, ported from PR #116) — depends on Phase 3/3a/3b (extends
    the same YAML, context, and preprocessor); independent of Phase 7
```

### Task-Level Dependencies

| Task | Depends On |
|------|-----------|
| T002 | T001 |
| T003, T003a | T001 |
| T004 | T002, T003 |
| T005 | T004 |
| T006 | T003 |
| T007 | T005, T006 |
| T008 | T007 |
| T009 | T007, T008 |
| T010, T011 | T007 (extraction happens after the preprocessor first works standalone) |
| T012 | T010, T011 |
| T012a | T012 |
| T012b | T012a |
| T013 | T009, T012b |
| T014 | T009, T012b |
| T015–T019 | T013, T014 |
| T020 | T001–T019 (documents the already-shipped state) |

---

## Notes

- `[P]` = can run in parallel (different files, no inter-task dependency)
- `[USn]` = maps task to user story for traceability; `US1a` = the `masterDefendantId` grouping capability added beyond the original AC2 scope during the port to this branch
- TDD: every test task ran and failed before its implementation task, per commit history (`d00bc34` = tests, `1ace279` = feat, `37c7600`/`46763eb` = strengthened tests, `9ab8a30` = rename/enable refactor)
- YAML first: T002 (`DR-COEW-005.yaml`) was authored before any Java class
- Rule id is `DR-COEW-005`, not `DR-COEW-001` — see research.md Decision 8
- Do not add `System.out` / `System.err` anywhere — SLF4J only (Constitution Principle VII)
