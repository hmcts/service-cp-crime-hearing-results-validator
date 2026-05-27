# Tasks: YRO Date Validation (DR-YRO-001)

**Input**: Design documents from `specs/004-yro-date-validation/`
**Branch**: `DD-41654-yro-date-validation`
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Data model**: [data-model.md](data-model.md) | **Research**: [research.md](research.md)

**TDD mandate (Constitution Principle VIII)**: Failing tests MUST be confirmed before any YAML condition that satisfies them is created.

**Deliverables** (two files only — no new Java production classes):
- `src/test/java/uk/gov/hmcts/cp/integration/YroDateValidationRuleIntegrationTest.java` (new)
- `src/main/resources/rules/DR-YRO-001.yaml` (new)

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Parallelizable (no inter-dependency at that point in execution)
- **[US1]**: User Story 1 — AC2 curfew end-date validation (YRC1/YRC2/YRC3)
- **[US2]**: User Story 2 — AC3 unpaid work 12-month validation (YRUP1)

---

## Phase 1: Setup

**Purpose**: Confirm clean baseline before any changes are made.

- [ ] T001 Run `gradle test` from the repo root and confirm all existing tests pass; note the passing test count

---

## Phase 2: Foundational — TDD Test Class Skeleton

**Purpose**: Create the integration test class with all failing stubs. **MUST be complete before any YAML is written.**

⚠️ **CRITICAL (TDD)**: Tests must compile and produce assertion failures — not compilation errors. At this stage `DR-YRO-001.yaml` does not exist, so the service returns no YRO validation issues; assertions expecting errors will correctly fail.

- [ ] T002 Create `src/test/java/uk/gov/hmcts/cp/integration/YroDateValidationRuleIntegrationTest.java` extending `IntegrationTestBase` with: class-level `@DisplayName("DR-YRO-001 — YRO Date Validation")` annotation; two `@Nested` inner classes (`Ac2CurfewEndDate` and `Ac3UnpaidWorkDuration`); one `@Test` stub per scenario from plan.md Test Plan (T001–T017 in that plan) with descriptive `@DisplayName`; all stub bodies calling `Assertions.fail("not yet implemented")`. Do not create `DR-YRO-001.yaml` yet.
- [ ] T003 Run `gradle test --tests "uk.gov.hmcts.cp.integration.YroDateValidationRuleIntegrationTest"` and verify: (a) class compiles without errors; (b) every stub fails with an assertion error, not a compilation error. Record output. **Do not proceed until both conditions are confirmed.**

---

## Phase 3: User Story 1 — AC2 Curfew End Date Validation (Priority: P1) 🎯 MVP

**Goal**: Block sharing when a YRO's end date precedes the end date of any linked curfew requirement (YRC1, YRC2, or YRC3). Each requirement type triggers an independent ERROR condition.

**Independent Test**: `POST /api/validation/validate` with a YROEW result (end date 2026-10-30) and a YRC2 child result (end date 2026-11-30) on the same offence for the same defendant. Expect `isValid: false`, `ruleId: "DR-YRO-001"`, `severity: "ERROR"`, message containing "Youth Rehabilitation Requirement: Curfew".

### Tests for User Story 1

> **Implement these fully and confirm they FAIL before creating DR-YRO-001.yaml (T009)**

- [ ] T004 [US1] In `YroDateValidationRuleIntegrationTest.java`, implement AC2a test bodies in `Ac2CurfewEndDate`: (1) YRC2 end date after YRO end date → AC2a ERROR on the offence (plan T001); (2) YRC2 end date equal to YRO end date → no error (plan T005); (3) YRC2 end date before YRO end date → no error (plan T006). Use `MockMvc` POST with `DraftValidationRequest` JSON containing `hearingDay`, `defendants` list, `offences` list, and `resultLines` with `shortCode` YROEW (prompts: `endDate`) and YRC2 (prompts: `endDate`).
- [ ] T005 [US1] Implement AC2b test body: YRC1 `endDateOfTagging` after YRO end date → AC2b ERROR (`cureViolationOffenceIds` scoped to the breaching offence) (plan T002). The YRC1 result line prompt ref is `endDateOfTagging`.
- [ ] T006 [US1] Implement AC2c test body: YRC3 end date after YRO end date → AC2c ERROR (`curaViolationOffenceIds`) (plan T003).
- [ ] T007 [US1] Implement multi-requirement and multi-defendant test bodies: (1) all three curfew types breach simultaneously → AC2a + AC2b + AC2c all fire independently (plan T004); (2) two defendants, only one has curfew breach → only the affected defendant appears in `affectedDefendants` (plan T007); (3) YRO with no curfew child results → no AC2 error (plan T008).
- [ ] T008 [US1] Run `gradle test --tests "uk.gov.hmcts.cp.integration.YroDateValidationRuleIntegrationTest"` and confirm all AC2 test methods fail with assertion failures. **Do not proceed to T009 until all fail for the correct reason.**

### Implementation for User Story 1

- [ ] T009 [US1] Create `src/main/resources/rules/DR-YRO-001.yaml` with the following content (see plan.md "Rule Design" for exact text):
  - `rule.id: "DR-YRO-001"`, `title: "Youth Rehabilitation Order End Date Validation"`, `priority: 5000`, `enabled: true`
  - `preprocessing.type: "community-order-end-date"` with `communityOrderShortCodes: [YROEW, YRONI, YROFEW, YROISS, YROINI]`, `curfewShortCodes: [YRC2]`, `curfewTagShortCodes: [YRC1]`, `furtherCurfewShortCodes: [YRC3]`, `unpaidWorkShortCodes: [YRUP1]`
  - Condition AC2a: `expression: "curViolationCount > 0"`, `severity: ERROR`, `affectedOffenceSet: "curViolationOffenceIds"`, `messageTemplate` and `errorMessageTemplate` naming "Youth Rehabilitation Requirement: Curfew"
  - Condition AC2b: `expression: "cureViolationCount > 0"`, `affectedOffenceSet: "cureViolationOffenceIds"`, messages naming "Youth Rehabilitation Requirement: Curfew with electronic monitoring"
  - Condition AC2c: `expression: "curaViolationCount > 0"`, `affectedOffenceSet: "curaViolationOffenceIds"`, messages naming "Youth Rehabilitation Requirement: Further curfew requirement made"
- [ ] T010 [US1] Run `gradle test --tests "uk.gov.hmcts.cp.integration.YroDateValidationRuleIntegrationTest"` and confirm all AC2 test methods pass. Diagnose and fix any YAML schema errors, CEL expression issues, or message template mismatches before proceeding.

**Checkpoint**: AC2 curfew end-date validation is fully functional and independently testable.

---

## Phase 4: User Story 2 — AC3 Unpaid Work Duration Validation (Priority: P2)

**Goal**: Block sharing when a YRO containing a YRUP1 (unpaid work) requirement has an end date less than `hearingDay + 12 months − 1 day`.

**Independent Test**: `POST /api/validation/validate` with hearing date 2026-05-20, YROEW end date 2027-05-18, and a YRUP1 child result. Expect `isValid: false`, message "The end date of the order must be at least 12 months as it includes an unpaid work requirement".

### Tests for User Story 2

> **Implement and confirm FAILING before adding the AC3 condition to DR-YRO-001.yaml (T013)**

- [ ] T011 [US2] In `YroDateValidationRuleIntegrationTest.java`, implement AC3 test bodies in `Ac3UnpaidWorkDuration`:
  - YRUP1 present; YRO end date 2027-05-18 (hearing 2026-05-20) → AC3 ERROR (plan T009)
  - YRUP1 present; YRO end date equals `hearingDay + 12m − 1d` (= 2027-05-19) → no error (plan T010)
  - YRUP1 present; YRO end date beyond 12m → no error (plan T011)
  - YRO without YRUP1 child result, short end date → no AC3 error (plan T012)
  - Two defendants; only one has YRUP1 with short duration → only that defendant in error; `affectedDefendants` list contains only the violating defendant (plan T013)
- [ ] T012 [US2] Run `gradle test --tests "uk.gov.hmcts.cp.integration.YroDateValidationRuleIntegrationTest"` and confirm AC3 test methods fail with assertion failures (`DR-YRO-001.yaml` has AC2 conditions but no AC3 condition yet). **Do not proceed until confirmed.**

### Implementation for User Story 2

- [ ] T013 [US2] Add the AC3 condition to `src/main/resources/rules/DR-YRO-001.yaml` after the AC2c entry:
  - `id: "AC3"`, `name: "Unpaid work order shorter than 12 months"`, `expression: "upwrViolationCount > 0"`, `severity: ERROR`
  - `messageTemplate: "The end date of the order must be at least 12 months as it includes an unpaid work requirement"`
  - `errorMessageTemplate: "The end date of the order must be at least 12 months as it includes an unpaid work requirement. This affects ${defendantNames}."`
  - `affectedOffenceSet: "upwrViolationOffenceIds"`
- [ ] T014 [US2] Run `gradle test --tests "uk.gov.hmcts.cp.integration.YroDateValidationRuleIntegrationTest"` and confirm all AC3 tests pass and all AC2 tests continue to pass.

**Checkpoint**: AC2 and AC3 both fully functional and tested. The complete rule is in place.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Combined scenarios, full build validation, and build-loop agent review.

- [ ] T015 In `YroDateValidationRuleIntegrationTest.java`, replace the remaining `fail("not yet implemented")` stubs with full test bodies for combined and response-structure scenarios (plan.md T014–T017):
  - Same defendant triggers both AC2a and AC3 simultaneously → both errors present in response, independently scoped
  - `errorMessageTemplate` expands `${defendantNames}` to the defendant's full name (`firstName + " " + lastName`)
  - Inline `messageTemplate` (per `affectedOffences` entry) is scoped to only the breaching offenceId, not all offences
  - Fully valid YRO (all requirement dates within order date; YRUP1 order >= 12m) → `isValid: true`, no errors, `rulesEvaluated` contains `"DR-YRO-001"`
- [ ] T016 Run `gradle test` (full suite) and confirm: all 17 YRO scenarios pass; no pre-existing tests regress.
- [ ] T017 Run `gradle build` and confirm Checkstyle (Google style, `maxWarnings = 0`), PMD, and all tests pass clean.
- [ ] T018 [P] Spawn `spec-validator` agent (read-only) on `src/main/resources/rules/DR-YRO-001.yaml`: verify rule schema matches `RuleDefinition`, `preprocessing.type: "community-order-end-date"` resolves to a registered `ValidationPreprocessor` bean, all 4 CEL expressions (`curViolationCount > 0`, `cureViolationCount > 0`, `curaViolationCount > 0`, `upwrViolationCount > 0`) compile, all `affectedOffenceSet` names are valid in `CommunityOrderContext.getOffenceIdSet()`. Expect COMPLIANT.
- [ ] T019 [P] Spawn `code-reviewer` agent (read-only) on `src/main/resources/rules/DR-YRO-001.yaml` and `src/test/java/uk/gov/hmcts/cp/integration/YroDateValidationRuleIntegrationTest.java`: check for logic errors, severity-ceiling promotions, `System.out` usage, null-safety, and layering violations. Expect PASS.
- [ ] T020 Spawn `qa` agent: verify TDD discipline — commit history shows failing tests authored before YAML conditions; confirm test coverage spans all 17 plan scenarios; run `gradle test`. Expect PASS.
- [ ] T021 Resolve any NEEDS CHANGES or FAIL findings from T018–T020. Re-run the relevant agent until PASS/COMPLIANT. Repeat for each finding.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all implementation
- **US1 (Phase 3)**: Depends on Phase 2 — AC2 tests must be failing before YAML is written
- **US2 (Phase 4)**: Depends on Phase 3 — YAML file must exist (from T009); AC3 condition is added incrementally
- **Polish (Phase 5)**: Depends on Phase 4 — all 4 conditions in YAML and all tests green

### User Story Dependencies

- **US1 (AC2)**: Can start after Foundation
- **US2 (AC3)**: Depends on US1's YAML creation (T009); AC3 is added to the same file

### Within Each Phase (TDD discipline)

- Tests for a condition MUST be confirmed failing before the YAML condition that satisfies them is added
- AC2 tests (T004–T008) must fail before `DR-YRO-001.yaml` is created (T009)
- AC3 tests (T011–T012) must fail before AC3 condition is added (T013)

### Parallel Opportunities

- T018 (`spec-validator`) and T019 (`code-reviewer`) can run in parallel — both read-only agents

---

## Parallel Example: User Story 1

```
# After T003 (all stubs confirmed failing), these test-body tasks can proceed together:
T004: AC2a test bodies (YRC2 scenarios) — Ac2CurfewEndDate nested class
T005: AC2b test body (YRC1 scenario)   — Ac2CurfewEndDate nested class
T006: AC2c test body (YRC3 scenario)   — Ac2CurfewEndDate nested class
T007: Multi-defendant test bodies      — Ac2CurfewEndDate nested class

# Note: all four modify the same file — coordinate to avoid edit conflicts if pairing.

# T018 and T019 (Phase 5) run in parallel:
T018: spec-validator agent
T019: code-reviewer agent
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Baseline
2. Phase 2: Test class skeleton — all stubs fail
3. Phase 3 (T004–T010): AC2 implemented and green
4. **STOP and VALIDATE**: `gradle test` — AC2 clean
5. AC2 is a shippable increment (3 of 4 conditions)

### Incremental Delivery

1. Phase 1–2: Foundation → failing test baseline
2. Phase 3: AC2 → tests green → working increment
3. Phase 4: AC3 added to same YAML → all tests green
4. Phase 5: Combined scenarios + build loop → merge-ready

---

## Notes

- `DR-YRO-001.yaml` is built incrementally: AC2a/AC2b/AC2c first (T009), then AC3 (T013)
- No Java production source files are created or modified — the only production deliverable is `DR-YRO-001.yaml`
- See plan.md "Rule Design — Message templates" for the exact `messageTemplate` and `errorMessageTemplate` strings
- See plan.md "Test Plan" (T001–T017) for full test scenario specifications including request shapes and expected response fields
- `alcoholAbstinenceShortCodes` need not appear in `DR-YRO-001.yaml` — the preprocessor handles a null/missing list as an empty set
