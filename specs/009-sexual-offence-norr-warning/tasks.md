---

description: "Task list for Sexual Offence Notification Requirement Warning"
---

# Tasks: Sexual Offence Notification Requirement Warning

**Input**: Design documents from `/specs/009-sexual-offence-norr-warning/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included and REQUIRED. This repo's constitution mandates TDD (Principle VIII,
NON-NEGOTIABLE) for every behaviour change — write the failing test first, confirm it fails for
the correct reason, then implement.

**Organization**: Tasks are grouped by user story (spec.md: US1 Adult, US2 Youth, US3 Combined
display) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every task includes exact file path(s)

## Path Conventions

Single Spring Boot project (this repo's existing structure — see plan.md "Project Structure").
All paths are relative to the repository root.

---

## Phase 1: Setup

**Purpose**: Confirm the target locations for new files exist per plan.md before any code lands.

- [x] T001 Confirm `src/main/java/uk/gov/hmcts/cp/services/referencedata/` (already scaffolded, currently empty — see research.md) and `src/main/java/uk/gov/hmcts/cp/services/rules/cel/`, `src/main/resources/rules/`, `wiremock/mappings/` exist; create any that don't. No production code in this task.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The new external dependency (client, config, cache) and shared test scaffolding that
every user story needs — without this, no offence can ever be classified as a relevant sexual
offence, so neither US1 nor US2 can ever fire.

**⚠️ CRITICAL**: Do NOT create `src/main/resources/rules/DR-SEX-008.yaml` in this phase — `ValidationRuleAutoConfiguration` constructs a `CelValidationRule` bean per YAML file at boot and fails fast if its `preprocessing.type` doesn't resolve in `PreprocessorRegistry`. The rule YAML is created in Phase 3 (US1), after the preprocessor exists and is registered — see contracts/DR-SEX-008.yaml.

- [x] T002 [P] Add the `referencedata.offences.http.*` config block to `src/main/resources/application.yaml`, per data-model.md's `ReferencedataOffenceProperties` table: `enabled` (default `true`), `offence-url-template` (default `${CP_BASE_URL:http://localhost:8080}/referencedataoffences-query-api/query/api/rest/referencedataoffences/offences/{offenceId}`), `accept-header` (`application/vnd.referencedataoffences.offence+json`), `connect-timeout-ms` (`2000`), `read-timeout-ms` (`3000`)
- [x] T003 [P] Register a new `referencedataOffences` named Caffeine cache in `src/main/java/uk/gov/hmcts/cp/config/CacheConfig.java`, keyed by `offenceId`, TTL from a new `@Value("${referencedata.offences.cache.ttl-seconds:3600}")` parameter, following the exact pattern already used for `ruleOverrides`/`featureFlags` in that class
- [x] T004 [P] Create `wiremock/mappings/referencedataoffences-stub.json` modeled on `wiremock/mappings/identity-stub.json`, stubbing `GET /referencedataoffences-query-api/query/api/rest/referencedataoffences/offences/{offenceId}` with a `misCode: "SEX"` response body per contracts/referencedata-offences-integration.md
- [x] T005 [P] Extend `src/test/java/uk/gov/hmcts/cp/integration/IntegrationTestBase.java` with a second static `WireMockServer` (dynamic port), a `@DynamicPropertySource` overriding `referencedata.offences.http.offence-url-template`, and a `stubReferencedataOffenceResponse(String offenceId, String misCode)` helper, mirroring the existing `IDENTITY_WIRE_MOCK`/`stubIdentityResponse` pattern in that class
- [x] T006 [P] Write failing unit tests in `src/test/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceClientTest.java` (using a mocked `RestTemplate`/`MockRestServiceServer`) covering the failure-mode table in contracts/referencedata-offences-integration.md: 200 with `misCode` present (returns populated `Optional`, cached on a repeat call for the same `offenceId`), 200 with `misCode` null/absent (`Optional.empty()`), 404 (`Optional.empty()`, WARN logged), timeout (`Optional.empty()`, WARN logged), malformed JSON/connection error (`Optional.empty()`, WARN logged). Confirm the tests fail for the right reason (the client class doesn't exist yet), not a stray compile error unrelated to the missing class.
- [x] T007 [P] Implement `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceResponse.java` — record with exactly `offenceId` and `misCode` fields, per data-model.md
- [x] T008 [P] Implement `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceProperties.java` — constructor-injected binding of the `referencedata.offences.http.*` block added in T002
- [x] T009 Implement `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceClient.java` — `RestTemplate` with a dedicated `SimpleClientHttpRequestFactory` (timeouts from T008's properties), a public `Optional<String> lookupMisCode(String offenceId)` method annotated `@Cacheable("referencedataOffences")`, fail-open catch-all (any exception, non-2xx, or missing `misCode`) returning `Optional.empty()` with an SLF4J WARN log line naming the `offenceId` and failure reason. Run T006 and confirm all cases pass green. (Depends on T006, T007, T008.)
- [x] T010 [P] Add `qualifyingMisCode` (`String`), `adultNotificationShortCodes` (`List<String>`), `youthNotificationShortCodes` (`List<String>`) fields to `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` — additive only, no existing field removed or renamed

**Checkpoint**: Foundation ready — the external dependency is wired, cached, fail-open, and fully unit-tested; test scaffolding is in place. User story implementation can now begin.

---

## Phase 3: User Story 1 - Convicted sexual offence missing notification requirement result - Adult (Priority: P1) 🎯 MVP

**Goal**: A convicted relevant sexual offence (misCode "SEX") charged against an Adult (18+ at hearing date) with no `NORRR` result produces an offence-level `WARNING` with the exact adult wording; sharing remains available.

**Independent Test**: Construct a hearing with one relevant sexual offence, a convicted result, a defendant aged 18+ at the hearing date, and no `NORRR` result line; call `/api/validation/validate` and assert the `DR-SEX-008` warning is returned with the adult message. Re-submit with a `NORRR` result line and assert no warning.

### Tests for User Story 1 ⚠️

> Write these tests FIRST, ensure they FAIL before implementation

- [x] T011 [P] [US1] Write failing unit tests in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/SexualOffenceNotificationContextTest.java` for Adult scenarios: `toCelContext()` returns `isYouth=0` for an 18+ defendant and for a null date of birth (fail-safe default, per data-model.md R6); `getOffenceIdSet("offenceId")` returns exactly the one offence id; `getOffenceIdSet("unknown")` throws `IllegalArgumentException`
- [x] T012 [P] [US1] Write failing unit tests in `src/test/java/uk/gov/hmcts/cp/services/rules/cel/SexualOffenceNotificationPreprocessorTest.java` for Adult scenarios: convicted + `misCode "SEX"` + no `NORRR` result → context produced with `hasQualifyingNotification=false`; same but with a `NORRR` result line (any casing) → `hasQualifyingNotification=true`; offence not convicted → no context entry; offence `misCode` not `"SEX"` (mocked client) → no context entry; reference-data lookup fails open (mocked client returns `Optional.empty()`) → no context entry; defendant `dateOfBirth` null → treated as Adult

### Implementation for User Story 1

- [x] T013 [US1] Implement `src/main/java/uk/gov/hmcts/cp/services/rules/cel/SexualOffenceNotificationContext.java` (record: `offenceId`, `defendantName`, `isYouth`, `hasQualifyingNotification`, implementing `RuleEvaluationContext`) per data-model.md — make T011 pass
- [x] T014 [US1] Implement `src/main/java/uk/gov/hmcts/cp/services/rules/cel/SexualOffenceNotificationPreprocessor.java` (qualifier `sexual-offence-notification-requirement`, constructor-injects `ReferencedataOffenceClient`) — for each offence: check `isConvicted`, call `lookupMisCode` against `config.qualifyingMisCode()`, compute `isYouth` via `Period.between(dateOfBirth, hearingDay).getYears() < 18` (null-safe, defaults to Adult per data-model.md R6), compute `hasQualifyingNotification` against `adultNotificationShortCodes` or `youthNotificationShortCodes` (case-insensitive) based on `isYouth` — make T012 pass. (Depends on T009, T010, T013.)
- [x] T015 [US1] Create `src/main/resources/rules/DR-SEX-008.yaml` per contracts/DR-SEX-008.yaml, with **only** the `AC1` (Adult) condition for now — `preprocessing.type: "sexual-offence-notification-requirement"`, `qualifyingMisCode: "SEX"`, `adultNotificationShortCodes: [NORRR]`, `youthNotificationShortCodes: [NORRR, NORPGP]` (both short-code lists are configured now even though only `AC1` consumes the adult one yet — no separate YAML edit needed for that part in US2)
- [x] T016 [US1] Write failing then passing integration test `src/test/java/uk/gov/hmcts/cp/integration/SexualOffenceNotificationRuleIT.java` (extends `IntegrationTestBase`) — Adult scenarios: `NORRR` missing → `WARNING` with the exact FR-006 message text, `validationLevel: OFFENCE`, `severity: WARNING`; `NORRR` present → no `DR-SEX-008` issue; multiple offences, only one breaching → warning scoped to that offence only; use T005's `stubReferencedataOffenceResponse` and T004's stub
- [x] T017 [US1] Write failing then passing live API test `src/apiTest/java/uk/gov/hmcts/cp/http/SexualOffenceNotificationApiHttpLiveTest.java` — Adult scenario against the docker-compose stack (`gradle api`)
- [x] T018 [US1] Confirm `src/test/java/uk/gov/hmcts/cp/config/ValidationRuleAutoConfigurationTest.java` picks up `DR-SEX-008` automatically (if it enumerates all `rules/DR-*.yaml` files) or extend it if it hardcodes an expected rule-id list

**Checkpoint**: User Story 1 is fully functional and independently testable — the Adult warning fires and clears correctly, sharing is never blocked.

---

## Phase 4: User Story 2 - Convicted sexual offence missing notification requirement result - Youth (Priority: P1)

**Goal**: The same offence, charged against a Youth defendant (under 18 at hearing date), is only missing its notification requirement if neither `NORRR` nor `NORPGP` is present, with youth-specific wording.

**Independent Test**: Construct a hearing with a relevant, convicted sexual offence, a defendant under 18 at the hearing date, and neither a `NORRR` nor `NORPGP` result line; assert the youth-specific warning fires. Assert it clears with either code present, and separately with both.

### Tests for User Story 2 ⚠️

> Write these tests FIRST, ensure they FAIL before implementation

- [x] T019 [P] [US2] Add failing unit tests to `src/test/java/uk/gov/hmcts/cp/services/rules/cel/SexualOffenceNotificationContextTest.java` for Youth scenarios: `toCelContext()` returns `isYouth=1` for a defendant under 18 at hearing date; a defendant exactly 18 on the hearing date returns `isYouth=0` (Adult boundary, per spec.md edge cases)
- [x] T020 [P] [US2] Add failing unit tests to `src/test/java/uk/gov/hmcts/cp/services/rules/cel/SexualOffenceNotificationPreprocessorTest.java` for Youth scenarios: convicted + `misCode "SEX"` + under-18 defendant + neither `NORRR` nor `NORPGP` → `isYouth=true, hasQualifyingNotification=false`; same but with `NORRR` only → `hasQualifyingNotification=true`; same but with `NORPGP` only → `hasQualifyingNotification=true`; same but with both → `hasQualifyingNotification=true`

### Implementation for User Story 2

- [x] T021 [US2] Run T019/T020 against the T013/T014 implementation from US1; the age/dual-code-set logic was already built holistically in US1 (data-model.md's context handles both branches from the start) — this task is to confirm green, and fix any gap the Youth-scenario tests expose in `SexualOffenceNotificationContext.java`/`SexualOffenceNotificationPreprocessor.java`
- [x] T022 [US2] Edit `src/main/resources/rules/DR-SEX-008.yaml` to add the `AC1A` (Youth) condition per contracts/DR-SEX-008.yaml, alongside the existing `AC1` condition
- [x] T023 [US2] Extend `src/test/java/uk/gov/hmcts/cp/integration/SexualOffenceNotificationRuleIT.java` with Youth scenarios: neither code present → `WARNING` with the exact FR-007 message text; `NORRR` only, `NORPGP` only, and both present → no `DR-SEX-008` issue in each case
- [x] T024 [US2] Extend `src/apiTest/java/uk/gov/hmcts/cp/http/SexualOffenceNotificationApiHttpLiveTest.java` with a Youth scenario

**Checkpoint**: User Stories 1 AND 2 both work independently — Adult and Youth warnings fire and clear correctly per their own rules.

---

## Phase 5: User Story 3 - Warnings shown together, sharing never blocked (Priority: P2)

**Goal**: Confirm this rule's warnings coexist with other rules' offence-level and defendant-level warnings in one validation response, with none suppressed and sharing never blocked.

**Independent Test**: Construct a hearing where `DR-SEX-008` and at least one other rule's condition (e.g. `DR-SENT-001`'s `AC4`, a `DEFENDANT`-level `WARNING`) are both met; assert the response's `warnings` list contains every triggered issue and no `ERROR` is present.

### Tests for User Story 3 ⚠️

> Write this test FIRST — per plan.md, no new production code is expected for this story

- [x] T025 [US3] Add a failing then passing integration test to `src/test/java/uk/gov/hmcts/cp/integration/SexualOffenceNotificationRuleIT.java` (or a new `SexualOffenceNotificationCombinedWarningsIT` if mixing rule setups gets unwieldy in one file) constructing a hearing that triggers `DR-SEX-008` (offence-level) together with an existing `DEFENDANT`-level warning from another rule; assert both appear in `warnings`, assert a scenario with multiple offence-level warnings shows all of them, assert a scenario with multiple defendant-level warnings shows all of them, and assert no `ERROR` is present so sharing is not blocked

**Checkpoint**: All three user stories are independently functional; combined-warning display is confirmed to need no new aggregation code.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation accuracy, live-test infrastructure, and the mandatory build/review loop.

- [x] T026 [P] Update `.claude/rules/design_rules.md`'s "Seven preprocessors are registered today" line to eight, adding `SexualOffenceNotificationPreprocessor` to the named list
- [x] T027 [P] Update `CLAUDE.md`'s matching preprocessor count/list in the "Rule execution flow" section (`Rule execution flow` bullet naming the seven registered preprocessors)
- [x] T028 Investigate whether the `gradle api` docker-compose stack already proxies/stubs `cpp-context-referencedata-offences`-shaped requests (per contracts/referencedata-offences-integration.md's open item); if not, add a compose service entry or WireMock stub so `SexualOffenceNotificationApiHttpLiveTest` (T017/T024) can run in that environment
- [x] T029 Run `gradle build` (Checkstyle Google + PMD + unit/integration tests, `maxWarnings = 0`) and `gradle jacocoTestReport`; confirm zero warnings and green tests, and reasonable coverage on the new classes
- [x] T030 Run `gradle api` against the docker-compose stack; confirm the live API tests (T017, T024) pass
- [x] T031 Execute the manual verification steps in `quickstart.md`, including the fail-open scenario (stub returns 404 / is unreachable), and confirm the documented expected behaviour holds
- [x] T032 Run the mandatory build loop (code-reviewer → qa → spec-validator agents per `.claude/rules/workflow.md`) and resolve all findings before merge

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories (no offence can ever be classified without the client/cache/config from this phase)
- **User Story 1 (Phase 3)**: Depends on Foundational. This is the MVP — do this first.
- **User Story 2 (Phase 4)**: Depends on Foundational **and** on US1's `SexualOffenceNotificationContext`/`Preprocessor`/`DR-SEX-008.yaml` already existing (US2 edits the same YAML file and extends the same Java classes rather than creating parallel ones) — sequential after US1, not independently startable from a clean Foundational state, though independently *testable* once built.
- **User Story 3 (Phase 5)**: Depends on US1 (needs `DR-SEX-008` to exist to combine it with another rule's warning). Can run any time after US1; does not depend on US2.
- **Polish (Phase 6)**: Depends on all desired user stories being complete.

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Constitution Principle VIII)
- Context/record before preprocessor before YAML rule before integration/live tests
- Story complete (checkpoint) before moving to the next priority

### Parallel Opportunities

- T002, T003, T004, T005, T006, T007, T008, T010 (Phase 2) can all run in parallel — different files, no cross-dependencies among them (T009 depends on T006/T007/T008 and must wait)
- T011 and T012 (Phase 3 tests) can run in parallel — different files
- T019 and T020 (Phase 4 tests) can run in parallel — different files
- T026 and T027 (Phase 6 docs) can run in parallel — different files

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Launch these together once T001 (Setup) is done:
Task: "Add referencedata.offences.http.* config block to src/main/resources/application.yaml"
Task: "Register referencedataOffences named Caffeine cache in src/main/java/uk/gov/hmcts/cp/config/CacheConfig.java"
Task: "Create wiremock/mappings/referencedataoffences-stub.json"
Task: "Extend IntegrationTestBase.java with a second WireMockServer + stub helper"
Task: "Write failing ReferencedataOffenceClientTest.java"
Task: "Implement ReferencedataOffenceResponse.java record"
Task: "Implement ReferencedataOffenceProperties.java"
Task: "Add new fields to PreprocessingDefinition.java"
# T009 (the client implementation) waits for T006/T007/T008 to land, then makes T006 green.
```

## Parallel Example: User Story 1

```bash
# Launch both test files together:
Task: "Write failing SexualOffenceNotificationContextTest.java (Adult scenarios)"
Task: "Write failing SexualOffenceNotificationPreprocessorTest.java (Adult scenarios)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — new external dependency, blocks everything)
3. Complete Phase 3: User Story 1 (Adult warning)
4. **STOP and VALIDATE**: run `quickstart.md` steps 1–5, confirm `DR-SEX-008` fires and clears correctly for Adult defendants, and that sharing is never blocked
5. Deploy/demo if ready — this alone delivers the core safeguarding-significant warning for the majority (adult) case

### Incremental Delivery

1. Setup + Foundational → external dependency wired, cached, fail-open, unit-tested
2. Add User Story 1 → Adult warning works end-to-end → validate → demo (MVP!)
3. Add User Story 2 → Youth warning added to the same rule → validate → demo
4. Add User Story 3 → regression-only confirmation that combined warnings still display correctly → validate → demo
5. Polish → docs accuracy, live-test infra, full build loop, mandatory code-reviewer/qa/spec-validator pass

### Notes

- Unlike a typical spec-kit feature, US1 and US2 here are **not** fully independent at the code
  level — they share one preprocessor, one context record, and one YAML rule file (research.md
  R3: one rule, two conditions, because they share the same convicted/misCode precondition and
  differ only in the notification-code set and message). US1 builds the complete Java logic (both
  age branches) as a matter of course; US2's job is primarily to add the second CEL condition and
  its own test coverage, not to duplicate infrastructure. This is called out explicitly so
  `/speckit-implement` doesn't try to force an artificial separation that would mean re-touching
  the same files twice for no benefit.
- User Story 3 intentionally has no "Implementation" subsection — per plan.md, it verifies
  existing, unmodified response-aggregation behaviour.
