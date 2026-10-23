# Tasks: Non-Endorsable Offence Warning

**Input**: Design documents from `specs/015-non-endorsable-offence-warning/`
**Branch**: `CRA-337-non-endorsable-offence-warning`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ quickstart.md ✅

**Organisation**: TDD is mandatory (Constitution Principle VIII). Tests are written first and must fail before production code is added. YAML rule is written before any Java (Principle I).

**Design**: Single rule `DR-END-014` with two CEL conditions (AC1 endorsement, AC2 disqualification) backed by one unified preprocessor `NonEndorsableOffencePreprocessor` (type `non-endorsable-offence`). Both families share a single ref-data call per offence.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared dependencies)
- **[Story]**: Which user story this task belongs to (US1–US6)
- Exact file paths included in every task description

---

## Phase 1: Foundational — YAML Rule (Principle I Gate)

**Purpose**: YAML contract must exist before any Java is written. This phase BLOCKS all other work.

**⚠️ CRITICAL**: Constitution Principle I — YAML is the contract. No preprocessor or test may be written until T001 is done.

- [X] T001 Create `src/main/resources/rules/DR-END-014.yaml` — single rule with two conditions: AC1 (`endorsementCount > 0`, WARNING, "This offence is not endorsable. Remove the endorsement result.", `affectedOffenceSet: "nonEndorsableEndorsementOffenceIds"`) and AC2 (`disqualificationCount > 0`, WARNING, "This offence is not endorsable. Remove the disqualification result, or record a disqualification for non-endorsable offence result instead.", `affectedOffenceSet: "nonEndorsableDisqualificationOffenceIds"`); `preprocessing.type: "non-endorsable-offence"`; `endorsementShortCodes: [LEP, LEN, LEA]`; `disqualificationTriggerShortCodes: [DDO, DDOL, DDOR, DDOTE, DDOTEL, DDD, DDDL, DDDT, DDDTL, DDDTO, DDP, DDPL, DDPR, DDPTE, DDPTEL, DDRCOT]`; `priority: 14000`; `validationLevel: OFFENCE` — full YAML body in data-model.md

**Checkpoint**: YAML rule committed. Phase 2 and Phase 3 may now begin.

---

## Phase 2: Foundational — SQL Migration

**Purpose**: DB row for DR-END-014 needed by integration test context (`IntegrationTestBase` applies migrations). BLOCKS integration tests.

- [X] T002 [P] Create `src/main/resources/db/migration/V1.015__insert_dr_end_014.sql` — `INSERT INTO validation_rule (id, enabled, severity) VALUES ('DR-END-014', true, 'WARNING');`

**Checkpoint**: Migration committed. Integration tests may now run against the test database.

---

## Phase 3: User Stories 1 & 2 — Core Preprocessor (Priority: P1) 🎯 MVP

**Goal**: `NonEndorsableOffencePreprocessor` correctly classifies each offence — endorsementCount, disqualificationCount — driving both DR-END-014 conditions. US1 (endorsement trigger) and US2 (disqualification trigger) share the same preprocessor and context record; they are tested independently within this phase.

**Independent Test**: `gradle test --tests "*.NonEndorsableOffencePreprocessorTest"` passes all unit scenarios; `gradle test --tests "*.NonEndorsableOffenceRuleIT"` produces WARNINGs with the correct messages for non-endorsable offences and no warnings for endorsable ones.

### Failing Tests — Write FIRST, confirm FAIL before T007 ⚠️

- [X] T003 [US1] [US2] Write `src/test/java/uk/gov/hmcts/cp/services/rules/cel/NonEndorsableOffencePreprocessorTest.java` — first create empty skeleton stubs of `NonEndorsableOffencePreprocessor` and `NonEndorsableOffenceContext` so the test compiles; then write unit test scenarios:
  - **Endorsement (US1)**: (a) LEP on non-endorsable (flag=false) → endorsementCount=1, disqualificationCount=0; (b) LEN on non-endorsable → context returned; (c) LEA on non-endorsable → context returned; (d) LEP on endorsable (flag=true) → empty map; (e) LEP on absent-flag offence (Optional.empty()) → endorsementCount=1 (fail-open); (f) no trigger code (IMP) → empty map, no ref-data call; (g) no result lines → empty map, no ref-data call
  - **Disqualification (US2)**: (h) DDO on non-endorsable → disqualificationCount=1, endorsementCount=0; (i) DDRCOT on non-endorsable → context returned; (j) DDRN on non-endorsable → empty map, no ref-data call (not in trigger set); (k) DDO on endorsable (flag=true) → empty map
  - **Both families (US3)**: (l) LEP + DDO on same non-endorsable → endorsementCount=1, disqualificationCount=1, single ref-data call
  - **Multi-offence (US5)**: (m) three offences — non-endorsable+LEP, endorsable+LEP, non-endorsable+IMP → context only for the first offence
  - **Context contracts**: (n) `toCelContext()` exposes `endorsementCount` and `disqualificationCount`; (o) `getOffenceIdSet("nonEndorsableEndorsementOffenceIds")` returns offenceId when endorsementCount>0, empty otherwise; (p) `getOffenceIdSet("nonEndorsableDisqualificationOffenceIds")` returns offenceId when disqualificationCount>0; (q) unknown setName → empty; (r) `defendantName()` → null; (s) `allOffenceIds()` → List.of(offenceId)
  — mock `ReferencedataOffenceClient`

- [X] T004 [US1] [US2] Write `src/test/java/uk/gov/hmcts/cp/integration/NonEndorsableOffenceRuleIT.java` — integration tests extending `IntegrationTestBase` covering all six ACs from Jira CRA-337:
  - **AC1a (US1)**: LEP on non-endorsable → WARNING DR-END-014, severity WARNING, validationLevel OFFENCE, affectedOffences[0].message = "This offence is not endorsable. Remove the endorsement result."
  - **AC1a (US1)**: LEN on non-endorsable → WARNING DR-END-014
  - **AC1a (US1)**: LEA on non-endorsable → WARNING DR-END-014
  - **AC1a (US1)**: absent endorsable_flag → WARNING via fail-open
  - **AC2 (US1)**: LEP on endorsable → no DR-END-014 warning
  - **AC4 (US4)**: IMP on non-endorsable (no trigger) → no DR-END-014 warning
  - **AC1b (US2)**: DDO on non-endorsable → WARNING DR-END-014, message = "This offence is not endorsable. Remove the disqualification result, or record a disqualification for non-endorsable offence result instead."
  - **AC1b (US2)**: DDRCOT on non-endorsable → WARNING DR-END-014
  - **AC2 (US2)**: DDO on endorsable → no warning
  - **AC3 (US2)**: DDRN on non-endorsable → no warning
  - **AC3 (US2)**: DDRVL on non-endorsable → no warning
  - **AC1c (US3)**: LEP + DDO on same non-endorsable → exactly two DR-END-014 warnings
  - **AC5 (US5)**: three offences, endorsement — only non-endorsable+trigger warns
  - **AC5 (US5)**: three offences, disqualification — only non-endorsable+trigger warns
  - **AC6 (US6)**: endorsement warning + DR-SENT-001 custodial warning both appear
  — WireMock stubs via `stubEndorsableFlagResponse(offenceCode, flag)`; unique offence codes per test

- [X] T005 [US1] Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.NonEndorsableOffencePreprocessorTest"` — confirm ALL tests FAIL for correct reason (assertion error or compilation error from skeleton). Do NOT proceed to T007 until failure confirmed.

- [X] T006 [US1] [US2] Run `gradle test --tests "uk.gov.hmcts.cp.integration.NonEndorsableOffenceRuleIT"` — confirm tests FAIL (assertion error or Spring context-load failure — preprocessor bean not yet registered). Do NOT proceed to T007 until failure confirmed.

### Implementation — US1 & US2 Core

- [X] T007 [P] [US1] [US2] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/NonEndorsableOffenceContext.java` — Java record implementing `RuleEvaluationContext`; fields: `String offenceId`, `long endorsementCount`, `long disqualificationCount`; `toCelContext()` returns `Map.of("endorsementCount", endorsementCount, "disqualificationCount", disqualificationCount)`; `getOffenceIdSet("nonEndorsableEndorsementOffenceIds")` returns `List.of(offenceId)` when endorsementCount > 0; `getOffenceIdSet("nonEndorsableDisqualificationOffenceIds")` returns `List.of(offenceId)` when disqualificationCount > 0; all other setNames return `List.of()`; `defendantName()` returns null; `allOffenceIds()` returns `List.of(offenceId)` — full shape in data-model.md

- [X] T008 [US1] [US2] Create `src/main/java/uk/gov/hmcts/cp/services/rules/cel/NonEndorsableOffencePreprocessor.java` — `@Component`, `type()` returns `"non-endorsable-offence"`; explicit constructor injecting `ReferencedataOffenceClient`; reads `endorsementShortCodes` and `disqualificationTriggerShortCodes` from `PreprocessingDefinition`; per offence: check `hasEndorsementTrigger` and `hasDisqualificationTrigger`; if neither → skip ref-data call entirely; else call `getEndorsableFlag(offenceCode)` once; if flag absent or FALSE → emit `NonEndorsableOffenceContext(offenceId, hasEndorsementTrigger ? 1L : 0L, hasDisqualificationTrigger ? 1L : 0L)`; if flag TRUE → no context for this offence — algorithm in data-model.md

- [X] T009 [P] [US1] [US2] Add `endorsementShortCodes` and `disqualificationTriggerShortCodes` fields to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` — two new `List<String>` fields in the existing record, with `@Builder` compatibility

- [X] T010 [US1] Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.NonEndorsableOffencePreprocessorTest"` — all tests must PASS; if not, fix production code and re-run

- [X] T011 [US1] [US2] Run `gradle test --tests "uk.gov.hmcts.cp.integration.NonEndorsableOffenceRuleIT"` — all tests must PASS; if not, fix production code and re-run

**Checkpoint**: DR-END-014 fully functional. Both test classes green. US1 (AC1a, AC2) and US2 (AC1b, AC3) proven. `gradle test` green.

---

## Phase 4: User Stories 3–6 — Both Families, Stateless Clearing, Multi-Offence, Coexistence (P1/P2)

**Goal**: Confirm AC1c (both families fire together), AC4 (stateless clearing), AC5 (per-offence independence), and AC6 (coexistence) via live API tests. No additional production code is required — coverage is proven through the IT and API test layers.

**Independent Test**: `gradle test` green. `gradle api` green (live HTTP confirms both warnings appear simultaneously on a single non-endorsable offence).

### API Tests & Smoke Artefacts

- [X] T012 [P] [US3] [US4] [US5] [US6] Create `src/apiTest/java/uk/gov/hmcts/cp/http/NonEndorsableOffenceApiHttpLiveTest.java` — live HTTP tests against docker-compose stack covering:
  - (a) Endorsement trigger (LEP on `live-ne-end-trigger`) → DR-END-014 WARNING, severity WARNING, validationLevel OFFENCE, endorsement message (**AC1a**)
  - (b) Endorsable offence (LEP on `live-ne-endorsable`) → no DR-END-014 warning (**AC2**)
  - (c) Disqualification trigger (DDO on `live-ne-disq-trigger`) → DR-END-014 WARNING, disqualification message (**AC1b**)
  - (d) Both families (LEP + DDO on `live-ne-both-off`) → DR-END-014 appears twice in warnings list (**AC1c**)
  - Does NOT toggle rule enable/disable state (per design_rules.md)

- [X] T013 [US3] [US4] [US5] [US6] Update `src/apiTest/java/uk/gov/hmcts/cp/http/ValidationRulesApiHttpLiveTest.java` — add DR-END-014 to the `containsExactlyInAnyOrder` rule-list assertion; update rule count to 10

- [X] T014 [P] [US3] Create `wiremock/mappings/referencedataoffences-non-endorsable-stub.json` — WireMock mapping for docker-compose stack: offence codes matching `live-ne-end-trigger`, `live-ne-disq-trigger`, `live-ne-both-off` → `endorsableFlag: false`; `live-ne-endorsable` → `endorsableFlag: true`

- [X] T015 [P] [US3] Create `specs/015-non-endorsable-offence-warning/CRA-337.http` — IntelliJ HTTP scratch file with named requests for AC1a (LEP on non-endorsable), AC1b (DDO on non-endorsable), AC1c (LEP+DDO on same offence), AC2 (trigger on endorsable), AC3 (DDRN on non-endorsable) — base request bodies on quickstart.md

**Checkpoint**: `gradle api` green (pending T016 fix). Both conditions appear correctly in live responses.

---

## Phase 5: Outstanding Fix — API Live Test Rule ID

**Purpose**: The API live test was initially written expecting a separate DR-END-015 rule. The consolidated design uses a single DR-END-014 with two conditions. This fix aligns the live test with reality.

- [X] T016 Fix `src/apiTest/java/uk/gov/hmcts/cp/http/NonEndorsableOffenceApiHttpLiveTest.java`:
  - Remove constant `RULE_ID_015 = "DR-END-015"`
  - In `DisqualificationTrigger.ddoOnNonEndorsableOffence_shouldRaiseDisqualificationWarning()`: assert `ruleIdsOf(json.get(WARNINGS)).contains(RULE_ID_014)` (DR-END-014 AC2)
  - In `BothFamilies.lepAndDdo_bothOnNonEndorsableOffence_shouldRaiseBothWarnings()`: replace the two-rule assertion with `assertThat(ruleIds.stream().filter(id -> id.equals(RULE_ID_014)).count()).isEqualTo(2)` — both conditions fire under the same rule DR-END-014

- [X] T017 Run `gradle api` — full live API suite must be green; fix any remaining regressions

**Checkpoint**: `gradle api` green. All AC1a–AC1c live coverage confirmed.

---

## Phase 6: Polish & Quality

**Purpose**: Static analysis, coverage, and full build validation before PR.

- [X] T018 [P] Run `gradle checkstyleMain` — zero Checkstyle violations required (Google checks, `maxWarnings = 0`)
- [X] T019 [P] Run `gradle pmdMain` — zero PMD violations (`ignoreFailures = false`)
- [X] T020 Run `gradle test` — full unit + integration suite must be green
- [X] T021 Run `gradle jacocoTestReport` — review HTML coverage report at `build/reports/jacoco/test/html/index.html`; verify `NonEndorsableOffencePreprocessor` and `NonEndorsableOffenceContext` are fully covered by unit tests
- [X] T022 [P] Run spec-validator agent (read-only) against `src/main/resources/rules/DR-END-014.yaml` — verify: schema compliance, `preprocessing.type "non-endorsable-offence"` resolves to registered bean, CEL expressions `endorsementCount > 0` and `disqualificationCount > 0` compile, messageTemplate placeholders valid, severity WARNING consistent with SQL insert
- [X] T023 [P] Run code-reviewer agent (read-only) against all new Java files — verify: no `System.out`, no field `@Autowired`, records for DTOs, correct layer separation, no null-safety gaps in preprocessor logic, SLF4J only

**Checkpoint**: All agents return PASS / COMPLIANT. `gradle build` is green. PR ready.

---

## Phase 7: AC Coverage Gaps — API Live Tests & HTTP File (Phase H)

**Purpose**: Re-audit against Jira CRA-337 v2 PDF found AC3 and AC5 missing from the API live test,
and CRA-337.http has stale DR-END-015 comments + wrong offence codes + missing AC4/AC5/AC6 sections.

- [X] T024 Fix `specs/015-non-endorsable-offence-warning/CRA-337.http`:
  - Remove all DR-END-015 references in comments (AC1b says "DR-END-015", AC1c says "DR-END-014 AND DR-END-015")
  - Replace RT88026 (now mapped endorsable in WireMock) with `live-ne-end-trigger` for AC1a/AC1b/AC1c/AC3 non-endorsable scenarios and `live-ne-endorsable` for AC2 endorsable scenario; update `live-ne-both-off` for AC1c
  - Add AC4 section: IMP on non-endorsable offence (`live-ne-end-trigger`) → expect no DR-END-014 warning (trigger removed / non-trigger code)
  - Add AC5 section: two offences (`live-ne-end-trigger` with LEP, `live-ne-endorsable` with LEP) → expect exactly one DR-END-014 warning for the non-endorsable offence only
  - Add AC6 note: coexistence covered by IT (`NonEndorsableOffenceRuleIT.endorsementWarningAndCustodialWarning_shouldBothAppear`)

- [X] T025 [US2] Add AC3 nested class to `src/apiTest/java/uk/gov/hmcts/cp/http/NonEndorsableOffenceApiHttpLiveTest.java`:
  - New `@Nested @DisplayName("DR-END-014 — non-endorsable disqualification family (AC3)")` class `NonEndorsableDiqFamilyClear`
  - Test: `ddrnOnNonEndorsableOffence_shouldNotRaiseAnyWarning()` — request with offence `live-ne-disq-trigger` (non-endorsable) and `DDRN` short code → `ruleIdsOf(json.get(WARNINGS))` does NOT contain `DR-END-014`
  - Note: no WireMock stub needed — DDRN is not in the trigger set so no ref-data call fires; `live-ne-disq-trigger` resolves endorsableFlag:false anyway if called, which is irrelevant here

- [X] T026 [US5] Add AC5 nested class to `src/apiTest/java/uk/gov/hmcts/cp/http/NonEndorsableOffenceApiHttpLiveTest.java`:
  - New `@Nested @DisplayName("Multiple offences assessed independently (AC5)")` class `MultiOffenceIndependence`
  - Test: `lepOnNonEndorsableAndEndorsableOffence_shouldWarnOnlyForNonEndorsable()` — request with two offences: `live-ne-end-trigger` (non-endorsable, LEP) and `live-ne-endorsable` (endorsable, LEP) → exactly one DR-END-014 warning, `affectedOffences[0].offenceId` is `live-ne-end-trigger`
  - Use unique offence IDs per test (e.g. `live-ne-end-trigger` as both offenceId and offenceCode for the non-endorsable offence; `live-ne-endorsable` for the endorsable offence)

- [X] T027 Run `gradle api` — full live API suite must stay green (target: 85+ tests, 0 failures)

**Checkpoint**: `gradle api` green with new AC3 and AC5 live tests. All ACs covered at unit, integration, and API live test level. CRA-337.http usable for manual smoke testing against the local docker stack.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (YAML)**: No dependencies — start immediately. BLOCKS all production Java.
- **Phase 2 (SQL)**: Depends on Phase 1. BLOCKS integration tests.
- **Phase 3 (US1+US2 core)**: Depends on Phase 1 + Phase 2. T003–T006 (failing tests) before T007–T011 (production code).
- **Phase 4 (US3–US6 API)**: Depends on Phase 3 complete.
- **Phase 5 (Fix)**: Depends on Phase 4 analysis of live test output.
- **Phase 6 (Quality)**: T018/T019/T022/T023 run in parallel after Phase 3; T020/T021/T017 gate on Phase 5.

### User Story Dependencies

| Story | Priority | Depends on | Key Tests |
|-------|----------|-----------|-----------|
| US1 Endorsement trigger | P1 | Phase 2 | PreprocessorTest endorsement scenarios + RuleIT EndorsementTrigger |
| US2 Disqualification trigger | P1 | Phase 2 | PreprocessorTest disqualification scenarios + RuleIT DisqualificationTrigger |
| US3 Both families | P1 | US1 + US2 preprocessor complete | RuleIT BothFamiliesOnSameOffence + ApiHttpLiveTest BothFamilies |
| US4 Warning clears (stateless) | P1 | US1 + US2 | RuleIT nonTriggerCodeOnNonEndorsableOffence |
| US5 Multi-offence independence | P2 | US1 + US2 | RuleIT MultiOffenceIndependence |
| US6 Coexistence | P2 | US1 + US2 | RuleIT CoexistenceWithOtherRules |

### Parallel Opportunities

| Phase | Parallel Tasks |
|-------|---------------|
| Phase 3 | T003 + T004 written together; T007 (context record) + T009 (PreprocessingDefinition) can be written while T008 (preprocessor) is in review |
| Phase 4 | T012 + T013 + T014 + T015 all touch different files |
| Phase 6 | T018 + T019 + T022 + T023 all read-only / different concerns |

---

## Parallel Example: Phase 3 (Core Preprocessor)

```bash
# Step 1 — write all failing tests first (can be parallelised):
T003: NonEndorsableOffencePreprocessorTest.java (unit)
T004: NonEndorsableOffenceRuleIT.java (integration)

# Step 2 — implement production code (context record and PreprocessingDefinition are independent):
T007: NonEndorsableOffenceContext.java
T009: PreprocessingDefinition.java (add two fields)
T008: NonEndorsableOffencePreprocessor.java  ← depends on T007 and T009

# Step 3 — verify green:
T010: gradle test preprocessor unit tests
T011: gradle test integration IT
```

---

## Implementation Strategy

### MVP (US1 + US2 — Single Rule DR-END-014)

1. Complete Phase 1 (T001 — YAML with both conditions)
2. Complete Phase 2 (T002 — SQL migration)
3. Complete Phase 3 (T003–T011 — failing tests then production code)
4. **STOP and VALIDATE**: `gradle test --tests "*.NonEndorsableOffencePreprocessorTest"` and `"*.NonEndorsableOffenceRuleIT"` both green
5. DR-END-014 is live; AC1a, AC1b, AC1c, AC2, AC3, AC4, AC5, AC6 all proven at unit/integration level

### Incremental Delivery

1. Phase 1 + 2 → YAML + SQL in place
2. Phase 3 → all ACs proven by tests (single preprocessor handles US1–US6)
3. Phase 4 → live API coverage added
4. Phase 5 → API live test fix applied, `gradle api` green
5. Phase 6 → quality gates and agent review loop, PR ready

---

## Notes

- All new Java files in package `uk.gov.hmcts.cp.services.rules.cel` (preprocessor + context) or `uk.gov.hmcts.cp.http` (API tests)
- No wildcard imports — explicit import per class (CLAUDE.md)
- Test method naming: `{action}_{scenario}_should_{expectation}` (e.g. `lepOnNonEndorsableOffence_shouldYieldContextWithEndorsementCount`)
- `@Nested` + `@DisplayName` for grouped test scenarios
- Offence codes in tests must be unique per test method to prevent Caffeine cache pollution across tests
- DR-END-014 must NOT appear in `ValidationRuleOverrideIntegrationTest` — override/ceiling coverage is provided by the existing framework-level test for DR-SENT-001 (per design_rules.md)
- There is no DR-END-015 — the disqualification warning is condition AC2 inside DR-END-014