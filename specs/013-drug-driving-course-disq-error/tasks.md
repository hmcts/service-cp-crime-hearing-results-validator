# Tasks: DR-DISQ-013 Drug Driving Course-Reduction Disqualification Error

**Input**: Design documents from `specs/013-drug-driving-course-disq-error/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

**TDD is NON-NEGOTIABLE** (Constitution Principle VIII): every implementation task is preceded by
a failing-test task. Tests must compile, run, and fail for the correct reason (assertion failure,
not a missing class) before production code is written.

**AC → Test coverage matrix** (every AC covered at all three levels):

| AC | Unit (`DrugDrivingCourseReductionPreprocessorTest`) | IT (`DrugDrivingCourseReductionRuleIT`) | API (`DrugDrivingCourseReductionApiHttpLiveTest`) |
|----|----|----|-----|
| AC1 DDRCO triggers ERROR | `rt88584_with_ddrco_*_should_qualify` | `ac1_rt88584_with_ddrco_should_produce_error` | `ac1_rt88584_with_ddrco_should_return_error` |
| AC1 DDRCOT triggers ERROR | `rt88584_with_ddrcot_should_qualify` | `ac1_rt88584_with_ddrcot_should_produce_error` | `ac1_ddrcot_triggers_same_error` |
| AC1 all 5 offence codes | `all_five_offence_codes_with_ddrco_should_qualify` | `ac1_rt88586_rt88584b_rt88340_ra88045_should_produce_error` | — |
| AC2 exact message | — | `ac2_error_message_text_should_match_exactly` | `ac2_error_message_text_should_match_exactly` |
| AC3 sharing blocked | — | `ac3_error_severity_is_error_and_blocks_sharing` | `ac3_response_errors_section_is_non_empty` |
| AC4 resolve (remove → clear) | `rt88584_with_ddo_should_not_qualify` | `ac4_replacing_ddrco_with_ddo_should_produce_no_error` | `ac4_ddo_on_triggering_offence_should_not_produce_error` |
| AC5 one error per offence | `two_triggering_offences_both_qualify_independently` | `ac5_two_triggering_offences_produce_two_errors` | `ac5_two_triggering_offences_produce_two_errors` |
| AC6 DDRCO on non-triggering offence | `non_triggering_offence_with_ddrco_should_not_qualify` | `ac6_ddrco_on_non_triggering_offence_should_not_produce_error` | `ac6_ddrco_on_non_triggering_offence_should_not_produce_error` |
| AC6 DDO on triggering offence | `triggering_offence_with_ddo_should_not_qualify` | `ac6_ddo_on_triggering_offence_should_not_produce_error` | `ac6_ddo_on_triggering_offence_should_not_produce_error` |
| AC7 amend and reshare | (same preprocessor path as AC1) | `ac7_amendment_with_ddrco_on_triggering_offence_produces_same_error` | `ac7_amendment_with_ddrco_produces_error` |

---

## Phase 1: Foundation (Blocking Prerequisites)

**Purpose**: Schema and context record changes that both tests and implementation depend on. Must
complete before any test can compile.

**⚠️ CRITICAL**: No unit test or implementation can compile until T001 and T002 are done.

- [X] T001 Add `List<String> courseReductionShortCodes` field to `PreprocessingDefinition` record in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` — place after `youthNotificationShortCodes`; `@Builder` handles the rest, existing callers are unaffected
- [X] T002 [P] Create `DrugDrivingContext` record in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DrugDrivingContext.java` — fields: `offenceId` (String), `qualifyingCount` (long), `qualifyingOffenceIds` (List<String>), `allOffenceIds` (List<String>); `toCelContext()` returns `Map.of("qualifyingCount", qualifyingCount)`; `getOffenceIdSet()` switches on `"qualifyingOffenceIds"` and `"allOffenceIds"`; `defendantName()` returns null

**Checkpoint**: Foundation ready — unit tests and preprocessor skeleton can now compile.

---

## Phase 2: US1 + US2 — Rule Trigger, Blocking, and Resolving (Priority: P1) 🎯 MVP

**Goal**: A drug driving offence with DDRCO or DDRCOT raises a named ERROR that blocks sharing;
removing or replacing the result clears the error.

**Independent Test**: POST `{RT88584 offence + DDRCO result}` to `/api/validation/validate` →
`$.errors.validationIssues` has exactly one issue with `ruleId=DR-DISQ-013` and the prescribed
message. Replace DDRCO with DDO → `$.errors.validationIssues` is empty.

### TDD — Write Failing Unit Tests First (US1 + US2)

> **Write these tests FIRST. They must compile and FAIL (assertion) before T008.**

- [X] T003 Create failing unit test class `DrugDrivingCourseReductionPreprocessorTest` in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/DrugDrivingCourseReductionPreprocessorTest.java` with the following `@Nested` test methods — class must compile against a stub preprocessor (stub throws `UnsupportedOperationException`):
  - **AC1** `rt88584_with_ddrco_category_f_should_qualify` — RT88584 + DDRCO (cat F) → `qualifyingCount=1`
  - **AC1** `rt88584_with_ddrco_category_a_should_qualify` — RT88584 + DDRCO (cat A, non-F) → `qualifyingCount=1` (no category filter)
  - **AC1** `rt88584_with_ddrcot_should_qualify` — RT88584 + DDRCOT → `qualifyingCount=1`
  - **AC1** `all_five_offence_codes_with_ddrco_should_qualify` — RT88586, RT88584B, RT88340, RA88045 each with DDRCO → `qualifyingCount=1`
  - **AC4** `rt88584_with_ddo_should_not_qualify` — RT88584 + DDO → `qualifyingCount=0`
  - **AC4** `rt88584_with_no_results_should_not_qualify` — RT88584 with no result lines → `qualifyingCount=0`
  - **AC5** `two_triggering_offences_both_qualify_independently` — 2 triggering offences, each with DDRCO → 2 contexts, both `qualifyingCount=1`
  - **AC6** `non_triggering_offence_with_ddrco_should_not_qualify` — RT88026 + DDRCO → `qualifyingCount=0`
  - **AC6** `triggering_offence_with_ddo_should_not_qualify` — RT88584 + DDO → `qualifyingCount=0`
  - **AC6** `offence_code_matching_is_case_insensitive` — `rt88584` (lowercase) + DDRCO → `qualifyingCount=1`
  - **AC6** `short_code_matching_is_case_insensitive` — RT88584 + `ddrco` (lowercase) → `qualifyingCount=1`
  - **Baseline** `yaml_contains_exactly_five_relevant_offence_codes` — loads `DR-DISQ-013.yaml` via `RuleDefinitionLoader`, asserts `relevantOffenceCodes.size() == 5`
  - **Baseline** `yaml_contains_exactly_two_course_reduction_short_codes` — asserts `courseReductionShortCodes.size() == 2` and contains DDRCO and DDRCOT

- [X] T004 Create failing integration test class `DrugDrivingCourseReductionRuleIT` in `src/test/java/uk/gov/hmcts/cp/integration/DrugDrivingCourseReductionRuleIT.java` extending `IntegrationTestBase` with the following `@Nested` test methods:
  - **AC1** `ac1_rt88584_with_ddrco_should_produce_error` — `$.errors.validationIssues[?(@.ruleId=='DR-DISQ-013')]` has size 1
  - **AC1** `ac1_rt88584_with_ddrcot_should_produce_error`
  - **AC1** `ac1_rt88586_should_produce_error` — second triggering offence code
  - **AC1** `ac1_rt88584b_should_produce_error`
  - **AC1** `ac1_rt88340_should_produce_error`
  - **AC1** `ac1_ra88045_should_produce_error`
  - **AC2** `ac2_error_message_text_should_match_exactly` — assert `affectedOffences[0].message` equals `"You cannot add a disqualification with a reduction for attending a course to a drug driving offence. Remove this result and add the correct disqualification."` (exact string)
  - **AC3** `ac3_error_severity_is_error_and_blocks_sharing` — assert issue `severity` is `"ERROR"` and `$.errors.validationIssues` is non-empty (ERROR level means sharing blocked)
  - **AC4** `ac4_replacing_ddrco_with_ddo_should_produce_no_error` — RT88584 + DDO → `$.errors.validationIssues` empty
  - **AC4** `ac4_rt88584_with_no_results_should_produce_no_error`
  - **AC5** `ac5_two_triggering_offences_with_ddrco_produce_two_errors` — 2 offences, each with DDRCO → 2 issues, each scoped to its offence
  - **AC6** `ac6_ddrco_on_non_triggering_offence_should_not_produce_error` — RT88026 + DDRCO → no DR-DISQ-013 errors
  - **AC6** `ac6_ddo_on_triggering_offence_should_not_produce_error` — RT88584 + DDO → no DR-DISQ-013 errors
  - **AC7** `ac7_amendment_with_ddrco_on_triggering_offence_produces_same_error` — same request shape as AC1; assert error output is identical regardless of any `isAmendment` flags in the request

- [X] T005 Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.DrugDrivingCourseReductionPreprocessorTest"` — confirm tests **FAIL** with assertion errors (not compilation errors); if any fail to compile, fix stubs only, not logic

### Implementation for US1 + US2

- [X] T006 Create stub `DrugDrivingCourseReductionPreprocessor` in `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DrugDrivingCourseReductionPreprocessor.java` — `@Slf4j @Component`, qualifier `"drug-driving-course-reduction"`, `preprocess()` returns an empty map (stub to unblock IT compilation)
- [X] T007 Create `DR-DISQ-013.yaml` in `src/main/resources/rules/DR-DISQ-013.yaml` — full YAML per data-model.md: `priority: 13000`, `preprocessing.type: drug-driving-course-reduction`, five `relevantOffenceCodes`, two `courseReductionShortCodes`, single AC1 condition `qualifyingCount > 0` at `severity: ERROR`, `validationLevel: OFFENCE`, exact `messageTemplate` text
- [X] T008 Create Liquibase migration `src/main/resources/db/migration/V1.014__insert_dr_disq_013.sql` — `INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by) VALUES ('DR-DISQ-013', true, 'ERROR', now(), 'system');`
- [X] T009 Update `src/main/resources/rules/priority.txt` — append `"DR-DISQ-013"` to the comma-separated list (priority position 13000)
- [X] T010 Implement full `DrugDrivingCourseReductionPreprocessor.preprocess()` — for each offence: `relevant = offenceCode ∈ upperSet(relevantOffenceCodes)`, `hasCourseReduction = anyShortCodeIn(allLines, upperSet(courseReductionShortCodes))`, `qualifying = relevant && hasCourseReduction`; emit `DrugDrivingContext(offenceId, qualifying ? 1 : 0, qualifying ? [offenceId] : [], [offenceId])`

- [X] T011 Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.DrugDrivingCourseReductionPreprocessorTest"` — confirm all unit tests **PASS**; fix implementation if any fail
- [X] T012 Run `gradle test --tests "uk.gov.hmcts.cp.integration.DrugDrivingCourseReductionRuleIT"` — confirm all IT tests **PASS**; fix implementation or YAML if any fail

**Checkpoint**: US1 and US2 fully functional. `gradle test` green on both test classes.

---

## Phase 3: US3 — No False Positives (Priority: P2)

**Goal**: Confirm DDRCO/DDRCOT on non-triggering offences and other disqualification results on
triggering offences produce no error from this rule.

**Independent Test**: POST `{RT88026 + DDRCO}` → `$.errors.validationIssues` empty; POST `{RT88584 + DDO}` → `$.errors.validationIssues` empty.

> US3 test scenarios (`ac6_*`) are already written and passing in T004/T012. This phase adds no
> new Java files — it validates the negative paths are green and adds them to the API test suite
> in Phase 5.

- [X] T013 Run `gradle test --tests "uk.gov.hmcts.cp.integration.DrugDrivingCourseReductionRuleIT.Ac6NoFalsePositives"` (or equivalent `@Nested` class name) — confirm both AC6 IT scenarios pass; if the nested class name differs, adjust the filter

**Checkpoint**: US3 confirmed passing at IT level. API-level AC6 coverage added in Phase 5.

---

## Phase 4: US4 — Amend and Reshare (Priority: P2)

**Goal**: Confirm the same ERROR is produced when DDRCO/DDRCOT is added to a triggering offence
via the amendment path, consistent with existing amend-and-reshare error behaviour.

**Independent Test**: POST same payload as AC1 with any `isAmendment`-style fields set → same
ERROR returned. The amendment-flow UI behaviour (hiding Request approval / Validate amendments) is
a front-end concern gated on CRA-282 and is not tested here.

> AC7 IT scenario (`ac7_amendment_with_ddrco_on_triggering_offence_produces_same_error`) is
> already written and passing from T004/T012. This phase verifies it and adds API coverage in
> Phase 5.

- [X] T014 Run `gradle test --tests "uk.gov.hmcts.cp.integration.DrugDrivingCourseReductionRuleIT"` in full — confirm AC7 scenario passes with the same `ruleId`, `severity`, `message`, and `offenceId` as the AC1 scenario

**Checkpoint**: US4 confirmed passing at IT level.

---

## Phase 5: API Tests (Live HTTP — All ACs)

**Goal**: Cover every AC with a live HTTP test against the docker-compose stack.

- [X] T015 Create `DrugDrivingCourseReductionApiHttpLiveTest` in `src/apiTest/java/uk/gov/hmcts/cp/http/DrugDrivingCourseReductionApiHttpLiveTest.java` extending no base class (follows `DisqualificationExtendedTestApiHttpLiveTest` pattern — `RestTemplate`, `baseUrl` from `System.getProperty("app.baseUrl", "http://localhost:8082")`); include all methods below:
  - **AC1** `ac1_rt88584_with_ddrco_should_return_error` — `$.errors.validationIssues` size 1, `ruleId=DR-DISQ-013`, `severity=ERROR`, `affectedOffences[0].offenceId=off1`
  - **AC1** `ac1_ddrcot_triggers_same_error` — same as above with DDRCOT
  - **AC2** `ac2_error_message_text_should_match_exactly` — assert `affectedOffences[0].message` equals exact prescribed string
  - **AC3** `ac3_response_errors_section_is_non_empty` — assert `$.isValid = false` (errors present means invalid)
  - **AC4** `ac4_ddo_on_triggering_offence_should_not_produce_error` — RT88584 + DDO → errors empty, `isValid=true`
  - **AC5** `ac5_two_triggering_offences_produce_two_errors` — 2 offences (RT88584 + RT88586, each with DDRCO) → 2 issues, each with its own `offenceId`
  - **AC6** `ac6_ddrco_on_non_triggering_offence_should_not_produce_error` — RT88026 + DDRCO → errors empty
  - **AC6** `ac6_ddo_on_triggering_offence_should_not_produce_error` — RT88584 + DDO → errors empty
  - **AC7** `ac7_amendment_with_ddrco_produces_error` — same payload as AC1 → same error (validates amendment path at live-HTTP level)

- [X] T016 Start the docker-compose stack and run `gradle api --tests "uk.gov.hmcts.cp.http.DrugDrivingCourseReductionApiHttpLiveTest"` — all tests must **PASS**; investigate any failures against the running service logs

**Checkpoint**: All ACs covered at all three test levels (unit, IT, API).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T017 Run `gradle checkstyleMain checkstyleTest` — fix any Google-style violations (maxWarnings=0); common issues: missing blank line after `{`, import order, line length
- [X] T018 Run `gradle pmdMain pmdTest` — fix any PMD violations (`ignoreFailures=false`)
- [X] T019 Run `gradle build` — full build (Checkstyle + PMD + all tests) must be **GREEN**; fix any remaining issues
- [X] T020 Run `gradle jacocoTestReport` — review coverage report; new preprocessor and context should reach 100% instruction coverage from the unit tests

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Foundation)**: No dependencies — start immediately
- **Phase 2 (US1+US2)**: Depends on Phase 1 — BLOCKS everything else
- **Phase 3 (US3)**: Depends on Phase 2 (uses same test class)
- **Phase 4 (US4)**: Depends on Phase 2 (uses same IT class)
- **Phase 5 (API)**: Depends on Phases 2–4 (requires running stack with rule seeded)
- **Phase 6 (Polish)**: Depends on all prior phases

### User Story Dependencies

- **US1 + US2 (P1)**: Can start after Foundation — no dependency on US3/US4
- **US3 (P2)**: Validates negative paths — depends on US1/US2 preprocessor being implemented
- **US4 (P2)**: Same code path as US1/US2 — depends on Phase 2 implementation
- **API tests**: Depend on full stack (Phase 2 implementation + migration)

### Within Each Phase

- T003 (failing unit tests) MUST precede T010 (implementation) — TDD gate
- T004 (failing IT tests) MUST precede T010 (implementation) — TDD gate
- T007 (YAML) and T008 (migration) and T009 (priority.txt) MUST precede T012 (IT tests run)
- T006 (stub preprocessor) MUST precede T004 compilation
- T010 (full preprocessor) MUST precede T011 and T012

### Parallel Opportunities

Within Phase 1:
- T001 (`PreprocessingDefinition`) and T002 (`DrugDrivingContext`) are independent — run in parallel

Within Phase 2 TDD block:
- T003 (unit test class) and T004 (IT test class) are independent files — write in parallel
- T007 (YAML), T008 (migration), T009 (priority.txt) are independent — write in parallel after T006 stub

---

## Parallel Example: Phase 2 (US1+US2)

```bash
# Write both test classes in parallel (T003 + T004):
Task: "Write DrugDrivingCourseReductionPreprocessorTest"
Task: "Write DrugDrivingCourseReductionRuleIT"

# Once stub preprocessor exists (T006), write rule artefacts in parallel (T007+T008+T009):
Task: "Create DR-DISQ-013.yaml"
Task: "Create V1.014__insert_dr_disq_013.sql"
Task: "Update priority.txt"
```

---

## Implementation Strategy

### MVP (US1 + US2 only — Phases 1 + 2)

1. Phase 1: Foundation (`PreprocessingDefinition` + `DrugDrivingContext`)
2. Phase 2 TDD: Write failing unit + IT tests
3. Phase 2 Impl: Stub → YAML + migration → full preprocessor
4. **STOP and VALIDATE**: `gradle test` green, quickstart.md scenario works end-to-end
5. Deploy if ready — AC1/AC2/AC3/AC4/AC5 all covered

### Full Delivery

1. MVP above
2. Phase 3 validation (US3 false-positive IT tests already written, confirm green)
3. Phase 4 validation (US4 amend IT test already written, confirm green)
4. Phase 5: API live tests
5. Phase 6: Polish + `gradle build` clean

---

## Notes

- `[P]` = different files, no blocking dependencies — safe to implement in parallel
- AC2 (exact message text) is tested at IT and API level only — message lives in YAML, not Java
- AC3 (sharing blocked) is validated by asserting `severity=ERROR` and non-empty `$.errors.validationIssues` at IT + API level
- AC7 is a no-op at the preprocessor level (same code path as AC1); the IT + API tests confirm
  it behaviorally
- The `spec-validator` agent must pass on `DR-DISQ-013.yaml` before T012 (check: schema, CEL
  compile, `preprocessing.type` resolves to `DrugDrivingCourseReductionPreprocessor`)
