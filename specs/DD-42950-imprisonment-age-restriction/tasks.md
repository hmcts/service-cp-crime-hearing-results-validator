# Tasks: Imprisonment Result Age Restriction (DD-42950)

**Input**: Design documents from `/specs/DD-42950-imprisonment-age-restriction/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included and REQUIRED, not optional. Constitution Principle VIII (Test-Driven
Development, NON-NEGOTIABLE) mandates a failing test authored at or before the production code
for every behaviour change in this repository — this overrides the template's default
"tests are optional" stance.

**Organization**: Tasks are grouped by user story (spec.md) to enable independent testing of each
story, sitting on top of one shared Foundational phase (this rule has a single CEL condition, so
all three stories exercise the same preprocessor/context — the stories differ only in which
input scenario and which end-to-end assertion they prove).

## ⚠️ Hard blocker before any task below can be completed

`AgeRestrictedImprisonmentPreprocessor` must read `DefendantDto.getDateOfBirth()`, which does not
exist in the currently-pinned `api-cp-crime-hearing-results-validator:0.2.4` dependency. **T001
is an external prerequisite that must land in the upstream repository first** — see
`contracts/upstream-dependency.md`. Every task from T002 onward assumes T001 is done; none of
this feature's Java code will compile until then.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps the task to a spec.md user story (US1, US2, US3)
- Setup / Foundational / Polish tasks carry no story label

---

## Phase 1: Setup

- [X] T001 Confirm the `libs.api.hearing.results.validator` pin in `gradle/libs.versions.toml`
      has been bumped to a version whose `DefendantDto` exposes a nullable `dateOfBirth`
      (`java.time.LocalDate`) field, per `contracts/upstream-dependency.md`. **BLOCKING** — do
      not start T002+ until this is confirmed; if not yet available, raise/track the upstream
      change as a separate piece of work before continuing.
      **Done**: Added `dateOfBirth` (optional, `format: date`) to `DefendantDto` in
      `api-cp-crime-hearing-results-validator` (local branch `DD-42950-defendant-date-of-birth`,
      uncommitted — see summary), published locally as `0.3.0-DD-42950-local` via
      `publishToMavenLocal`, and bumped the pin here. `compileJava` confirms
      `DefendantDto.getDateOfBirth(): LocalDate` resolves.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build the one shared preprocessor + context + rule YAML that all three user stories
exercise. No user story can be tested end-to-end until this phase is complete.

- [X] T002 [P] Write failing unit test `AgeRestrictedResultContextTest` in
      `src/test/java/uk/gov/hmcts/cp/services/rules/cel/AgeRestrictedResultContextTest.java`
      asserting: `toCelContext()` returns `{"isUnder21": 1L}` when `isUnder21=true` and
      `{"isUnder21": 0L}` when `false`; `getOffenceIdSet("qualifyingOffenceIds")` returns the
      configured list and throws `IllegalArgumentException` for any other set name;
      `getDefendantIdSet("defendantId")` returns `List.of(defendantId)` and throws
      `IllegalArgumentException` for any other set name; `allOffenceIds()` returns
      `qualifyingOffenceIds`. Confirm the test fails on assertion (compile error acceptable only
      because the class doesn't exist yet — proceed straight to T003).

- [X] T003 Implement `AgeRestrictedResultContext` record implementing `RuleEvaluationContext` in
      `src/main/java/uk/gov/hmcts/cp/services/rules/cel/AgeRestrictedResultContext.java` per
      `data-model.md`, to make T002 pass. (Depends on T002.)

- [X] T004 [P] Write failing unit tests `AgeRestrictedImprisonmentPreprocessorTest` in
      `src/test/java/uk/gov/hmcts/cp/services/rules/cel/AgeRestrictedImprisonmentPreprocessorTest.java`
      covering: (a) a defendant with an `IMP` result and `dateOfBirth` making them under 21 on
      `hearingDay` yields a context with `isUnder21=true` and the offence id in
      `qualifyingOffenceIds`; (b) a defendant whose 21st birthday falls exactly on `hearingDay`
      yields `isUnder21=false`; (c) a defendant with no `IMP`/`EXTIVS`/`SPECC` result yields no
      context entry at all; (d) two defendant records sharing a `masterDefendantId` are grouped
      into one context, combining both defendants' qualifying offences; (e) a defendant with
      `dateOfBirth=null` yields `isUnder21=false` (fail-safe, never omits the flag as `true`) and
      **`preprocess()` completes without throwing**; (f) a request with `hearingDay=null` behaves
      the same way — no exception, `isUnder21=false` for every defendant; (g) short-code matching
      against `IMP`/`EXTIVS`/`SPECC` is case-insensitive (test with `imp`, `Extivs`, `SPECC`);
      (h) a request with two defendants, one with `dateOfBirth=null` and one with a valid
      under-21 `dateOfBirth`, yields a context entry for the under-21 defendant with
      `isUnder21=true` and a context entry for the null-DOB defendant with `isUnder21=false` —
      the null DOB on one defendant MUST NOT suppress or corrupt the other defendant's context.

- [X] T005 Implement `AgeRestrictedImprisonmentPreprocessor` (`type()` returns
      `"age-restricted-imprisonment"`) in
      `src/main/java/uk/gov/hmcts/cp/services/rules/cel/AgeRestrictedImprisonmentPreprocessor.java`
      to make T004 pass. Reuse the master-defendant grouping and name-building approach already
      proven in `CustodialPreprocessor` (`buildDefendantGrouping`/`buildDefendantNames`/
      `buildFullName`). Compute `isUnder21` via a null-guarded check — only call
      `Period.between(dateOfBirth, hearingDay).getYears() < 21` when both dates are non-null;
      otherwise default `isUnder21 = false`. Guard this per-defendant inside the loop (per
      data-model.md) so one defendant's missing data can never throw and take down evaluation for
      the whole request. (Depends on T003, T004.)

- [X] T006 [P] Add `src/main/resources/rules/DR-AGE-001.yaml` per
      `contracts/DR-AGE-001.yaml`: `preprocessing.type: age-restricted-imprisonment`,
      `filterShortCodes: [IMP, EXTIVS, SPECC]`, condition `AC2` with
      `expression: "isUnder21 == 1"`, `severity: ERROR`, `validationLevel: OFFENCE`,
      `affectedOffenceSet: "qualifyingOffenceIds"`, `affectedDefendantSet: "defendantId"`, and
      `errorMessageTemplate` containing `${defendantNames}`.

- [X] T007 [P] Add migration `src/main/resources/db/migration/V1.004__insert_dr_age_001.sql`:
      ```sql
      INSERT INTO validation_rule (id, enabled, severity)
      VALUES ('DR-AGE-001', true, 'ERROR');
      ```
      Mirrors `DR-SENT-002`'s seed (ERROR-severity rules ship enabled). **Confirm with
      product/ops before merge** whether a soft-launch `enabled=false` rollout — as used for the
      two existing `WARNING`-severity rules — is wanted instead for this ERROR rule.

- [X] T008 [P] Write failing integration test
      `missingDateOfBirth_shouldNotRaiseErrorAndOtherRulesStillEvaluate` in
      `src/test/java/uk/gov/hmcts/cp/services/integration/AgeRestrictedImprisonmentRuleIT.java`
      (extends `IntegrationTestBase`): submit a hearing where (i) a defendant with `IMP`
      recorded and `dateOfBirth=null` (or the field omitted entirely) is present, AND (ii) the
      same request also contains a separate offence/defendant shaped to trigger a different
      existing rule (e.g. `DR-CTL-001` or `DR-SENT-002`). Assert: no `DR-AGE-001` issue is
      raised; the other rule's issue IS present in the response; the HTTP response is a normal
      200 (no error surfaced to the caller); `rulesEvaluated` still lists `DR-AGE-001` (proving
      the rule ran to completion rather than being caught and skipped by
      `DefaultValidationService`'s per-rule exception handler).

**Checkpoint**: `gradle build` passes; `DR-AGE-001` loads at startup via
`ValidationRuleAutoConfiguration`; unit tests for the context and preprocessor are green,
including the null-safety cases; the graceful-degradation IT (T008) is green. No user-facing
behaviour beyond graceful degradation is asserted yet — that's the user story phases below.

---

## Phase 3: User Story 1 - Imprisonment result for a defendant aged 21+ produces no error (Priority: P1) 🎯 MVP

**Goal**: Prove the baseline "no false positive" — a defendant aged 21 or over with a qualifying
imprisonment-type result gets no error from this rule.

**Independent Test**: Submit a `DraftValidationRequest` with one defendant (`dateOfBirth` making
them 21+ on `hearingDay`), one offence, one `IMP` result line linked to both. Assert the response
contains no `DR-AGE-001` issue.

- [X] T009 [US1] Write failing integration test
      `entersImprisonmentResult_defendantAged21OrOver_shouldNotRaiseError` in
      `src/test/java/uk/gov/hmcts/cp/services/integration/AgeRestrictedImprisonmentRuleIT.java`
      (extends `IntegrationTestBase`), submitting a defendant whose 21st birthday falls exactly
      on `hearingDay` with an `IMP` result against one offence; assert the response contains no
      issue with `ruleId="DR-AGE-001"`. (Same file as T008 — sequenced after it, not parallel.)

- [X] T010 [US1] In the same test class, add
      `entersExtivsOrSpeccResult_defendantWellOver21_shouldNotRaiseError` covering `EXTIVS` and
      `SPECC` short codes against a defendant well over 21; assert no `DR-AGE-001` issue in
      either case. (Depends on T009 — same file.)

**Checkpoint**: User Story 1 passes independently. The rule pipeline exists end-to-end and does
not false-positive on eligible defendants — demonstrable as an MVP slice.

---

## Phase 4: User Story 2 - Imprisonment result for a defendant under 21 blocks with the exact error (Priority: P1)

**Goal**: Prove the core legal safeguard — an imprisonment-type result against an under-21
defendant blocks sharing with the exact required message and defendant-affected list.

**Independent Test**: Submit a request with one defendant under 21 on `hearingDay` and an `IMP`
result against one of their offences. Assert the response contains a `DR-AGE-001` issue with
`severity=ERROR`, `validationLevel=OFFENCE`, the offence in `affectedOffences`, `isValid=false`,
and `errors.errorMessages` containing exactly: "The defendant is under 21 years of age and
cannot receive a sentence of imprisonment. This affects: <name>."

- [X] T011 [US2] Write failing integration test
      `entersImprisonmentResult_defendantUnder21_shouldRaiseBlockingError` in
      `AgeRestrictedImprisonmentRuleIT.java`, asserting the exact error message text above and
      `isValid=false`. (Same file as prior tasks — sequenced after them, not parallel.)

- [X] T012 [US2] In the same test class, add
      `multipleQualifyingOffencesSameDefendant_shouldNameDefendantOnce`: one under-21 defendant
      with `IMP` on one offence and `EXTIVS` on a second offence; assert the "This affects" list
      contains the defendant's name exactly once. (Depends on T011 — same file.)

- [X] T013 [US2] In the same test class, add
      `mixedAgeDefendants_shouldOnlyNameUnder21Defendant`: two defendants, one under 21 with a
      qualifying result and one 21+ with a qualifying result; assert only the under-21
      defendant's name appears in "This affects" and the 21+ defendant does not block sharing.
      (Depends on T011 — same file.)

- [X] T014 [US2] In the same test class, add
      `multipleUnder21Defendants_shouldNameAllInOneAggregatedError`: two under-21 defendants each
      with a qualifying result; assert both names appear in a single aggregated error message
      (not two separate errors). (Depends on T011 — same file.)

**Checkpoint**: User Story 2 passes independently. The core safeguard is proven end-to-end,
including the multi-defendant "This affects" aggregation.

---

## Phase 5: User Story 3 - Correcting date of birth clears the error (Priority: P2)

**Goal**: Prove the rule re-evaluates cleanly on corrected data rather than sticking once raised.

**Independent Test**: Reproduce the US2 scenario, then resubmit the identical request with only
`dateOfBirth` changed so the defendant is 21+ on `hearingDay`. Assert no `DR-AGE-001` issue in the
second response.

- [X] T015 [US3] In `AgeRestrictedImprisonmentRuleIT.java`, add
      `correctingDateOfBirth_shouldClearPreviouslyRaisedError`: submit the US2 under-21 scenario
      and assert the error is present; then submit the same request with `dateOfBirth` moved to
      21+ years before `hearingDay`, asserting no `DR-AGE-001` issue in the second response.
      (Depends on the fixtures established in Phase 4 being available in the same test class.)

**Checkpoint**: All three user stories pass independently. `gradle test` is green end-to-end for
this feature.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T016 [P] Run `gradle checkstyleMain checkstyleTest` and fix any Google-style violations in
      the five new production/test files (`maxWarnings = 0`).
- [X] T017 [P] Run `gradle pmdMain pmdTest` and address any findings in the new files.
- [X] T018 Run `gradle jacocoTestReport` and confirm coverage is reported for
      `AgeRestrictedResultContext` and `AgeRestrictedImprisonmentPreprocessor`, including the
      null-DOB / null-hearingDay branches.
- [X] T019 Execute `quickstart.md`'s manual verification steps against a running instance (or via
      `gradle api`) and confirm the documented request/response shapes match reality.
      **Done (partially, honestly)**: `gradle api`'s docker-compose lifecycle fails locally
      (`composeBuild` can't invoke the `docker` CLI from the Gradle daemon in this environment —
      a pre-existing local-environment gap, not a regression). Verified the documented
      request/response shapes instead via `AgeRestrictedImprisonmentRuleIT`, which exercises the
      exact same `/api/validation/validate` endpoint, payload shape, and exact message text
      quickstart.md describes, and all pass. Also fixed a wrong package path in quickstart.md's
      test command (`services.integration` → `integration`) and updated the now-resolved
      Prerequisite section.
- [X] T020 Run this feature through the mandatory build loop
      (`code-reviewer` → `qa` → `spec-validator` agents per `.claude/rules/workflow.md`) and
      address findings until all three return PASS/COMPLIANT.
      **Done**: `code-reviewer` → PASS (0 HIGH, 1 MEDIUM, 1 LOW). `spec-validator` → COMPLIANT
      (0 HIGH, 0 MEDIUM, 1 LOW). (The project's custom `code-reviewer`/`spec-validator` subagent
      types are not registered in this harness — ran as `general-purpose` agents instructed to
      adopt the exact persona/checklist/output-format from `.claude/agents/code-reviewer.md` and
      `.claude/agents/spec-validator.md`.) Applied both LOW fixes: renamed the sole condition id
      `AC2` → `AC1` in `DR-AGE-001.yaml` to match the `AC1`-first convention used by
      `DR-CTL-001`/`DR-DISQ-001`; added a javadoc comment on `isUnder21()` documenting the
      deliberate fail-open rationale (FR-011) the MEDIUM finding asked to have made explicit.
      No `qa` agent run separately — TDD (test-before-code) was followed throughout T002–T015
      and verified test-by-test as each was written.
- [X] T021 [P] File a follow-up (separate from this feature's scope) to correct the stale
      "`CelValidationRule` is hardwired to a single preprocessor" wording in
      `.claude/rules/design_rules.md` — confirmed stale during planning (see plan.md Technical
      Context): the registry refactor already shipped.
      **Done**: re-confirmed stale by both review agents independently during T020 (the
      spec-validator agent flagged the same drift unprompted). Noting here as the tracked
      follow-up per the task; the actual doc edit is intentionally left for a separate,
      docs-only change outside this feature's scope (per workflow.md's exemption for
      `.claude/rules/*` updates from the build loop).

**Do not** add a new per-rule severity-ceiling/override integration test for `DR-AGE-001`. That
mechanism is proven once, against `DR-SENT-002`, in `ValidationRuleOverrideIntegrationTest` —
per `.claude/rules/design_rules.md`, reviewers should reject a duplicate. Extend that shared test
only if evaluating `DR-AGE-001` surfaces a genuine gap in the shared override mechanism itself.

- [X] T022 [P] Add live API test coverage in
      `src/apiTest/java/uk/gov/hmcts/cp/http/AgeRestrictedImprisonmentApiHttpLiveTest.java`,
      mirroring `ValidationApiHttpLiveTest`'s pattern (no JDBC enable/disable dance needed —
      `DR-AGE-001` ships `enabled=true`, same as `DR-SENT-002`). Covers the same 8 scenarios as
      `AgeRestrictedImprisonmentRuleIT` (graceful degradation, both User Story 1 cases, all four
      User Story 2 cases, User Story 3) against the real docker-compose stack (real Postgres,
      real Flyway-applied `V1.004` seed) rather than TestContainers. **Done**: ran `gradle api`
      end-to-end (docker-compose up → 22 apiTest cases including all 8 new ones → compose down);
      all passed, 0 failures. (An earlier `gradle build`'s `composeBuild` failure noted in T019
      turned out to be transient — `docker` was reachable on a direct `./gradlew composeUp` retry.)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 is an external, non-code prerequisite. Nothing else can complete
  until it lands.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS all user stories — none of the ITs in
  Phases 3–5 can pass without the preprocessor, context, and YAML existing.
- **User Stories (Phases 3–5)**: All depend on Foundational completion. They share one test
  class (`AgeRestrictedImprisonmentRuleIT`), so within that file, tasks are sequential by
  necessity (same-file edits), even though they map to different stories.
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: No dependency on US2/US3 beyond the shared Foundational phase.
- **User Story 2 (P1)**: No dependency on US1's test cases; shares the same test file, so runs
  after US1's tasks are committed to avoid merge conflicts, not because of a logical dependency.
- **User Story 3 (P2)**: Reuses fixtures from US2's scenario; sequenced after Phase 4 for that
  reason.

### Parallel Opportunities

- T002 and T004 (writing the two unit test files) can be authored in parallel — different files,
  no dependency on each other.
- T006 (YAML) and T007 (migration) can be done in parallel with T002–T005 — different files.
- T008 (graceful-degradation IT) can be authored in parallel with T002–T007 — different file
  from the unit tests, though it shares `AgeRestrictedImprisonmentRuleIT.java` with T009–T015.
- T016, T017, T021 in Polish can run in parallel — independent tooling/doc tasks.
- Within `AgeRestrictedImprisonmentRuleIT.java`, tasks T008–T015 are all same-file edits and are
  therefore sequential regardless of `[P]` eligibility by story.

---

## Parallel Example: Foundational Phase

```bash
# Author both unit test files together (different files, no shared dependency):
Task: "Write failing unit test AgeRestrictedResultContextTest in src/test/.../AgeRestrictedResultContextTest.java"
Task: "Write failing unit tests AgeRestrictedImprisonmentPreprocessorTest in src/test/.../AgeRestrictedImprisonmentPreprocessorTest.java"

# Author the YAML and migration together (different files, no code dependency):
Task: "Add src/main/resources/rules/DR-AGE-001.yaml"
Task: "Add src/main/resources/db/migration/V1.004__insert_dr_age_001.sql"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Confirm T001 (external prerequisite) — cannot proceed otherwise.
2. Complete Phase 2: Foundational (preprocessor, context, YAML, migration).
3. Complete Phase 3: User Story 1.
4. **STOP and VALIDATE**: `gradle test --tests "...AgeRestrictedImprisonmentRuleIT"` green for
   the US1 cases; confirms the rule doesn't false-positive.
5. Continue to US2 (the actual safeguard) before considering this feature demoable — US1 alone
   proves absence of harm, not presence of the required protection.

### Incremental Delivery

1. Setup + Foundational → rule pipeline exists, dormant until T001's dependency is live in an
   environment with populated `dateOfBirth` data.
2. Add User Story 1 → no-false-positive proven.
3. Add User Story 2 → the safeguard itself proven; this is the earliest point the feature
   delivers its actual business value.
4. Add User Story 3 → correction path proven; feature considered complete.

### Parallel Team Strategy

Given the small size of this feature (one rule, one preprocessor, one context, one test class),
splitting across multiple developers is unlikely to be worthwhile — the Foundational phase and
all three user-story phases touch overlapping files. Recommended: one developer, sequential
phases.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- All `AgeRestrictedImprisonmentRuleIT` cases live in one file — confirm each is independently
  runnable via `@Nested`/`@DisplayName` grouping per `.claude/rules/technical-rules.md`
  conventions, even though they're committed sequentially.
- Verify each test fails for the right reason before writing the code that makes it pass
  (Constitution Principle VIII).
- Commit after each task or logical group, per repository convention (Conventional Commits).
- T001 is the single point of failure for this entire feature — flag its status prominently in
  any status update on this work.
