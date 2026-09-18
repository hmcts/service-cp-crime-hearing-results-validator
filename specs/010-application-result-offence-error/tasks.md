# Tasks: Application Results Recorded Against Offences – ERROR Validation

**Input**: Design documents from `/specs/010-application-result-offence-error/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included and REQUIRED, not optional. Constitution Principle VIII (Test-Driven
Development, NON-NEGOTIABLE) mandates a failing test authored at or before the production code
for every behaviour change in this repository — this overrides the template's default
"tests are optional" stance.

**Organization**: Tasks are grouped by user story (spec.md) to enable independent testing of each
story, sitting on top of one shared Foundational phase (the preprocessor, context, and both
engine fixes are common to all three stories — the stories differ only in which input scenario
and which end-to-end assertion they prove).

## No blocking external dependency (contrast with 007-imprisonment-age-restriction)

Unlike that feature, nothing here waits on an upstream DTO change — `ResultLineDto` already
carries every field this rule needs (`offenceId`, `shortCode`, `label`, `defendantId`). See
research.md R1. All tasks below can proceed immediately.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps the task to a spec.md user story (US1, US2, US3)
- Setup / Foundational / Polish tasks carry no story label

---

## Phase 1: Setup

- [X] T001 Confirm `gradle build` runs cleanly on the current branch
      (`010-application-result-offence-error`) before any change, so later failures are
      attributable to this feature's own commits. No file changes.
      **Done**: `gradle build`'s `composeUp` fails locally on a pre-existing port-5432 conflict
      (unrelated docker environment issue, same class of gap noted in
      `specs/007-imprisonment-age-restriction`). Used `gradle test` instead — unit + integration
      suite is green (`BUILD SUCCESSFUL`) before any change in this feature.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build both shared engine fixes, the preprocessor, the context, and the rule YAML
that all three user stories exercise. No user story can be tested end-to-end until this phase is
complete.

### Engine generalisation A — calculated-value placeholder (research.md R5)

- [X] T002 [P] Write failing unit test coverage in
      `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRuleTest.java` for the
      generalised calculated-value placeholder mechanism:
      (a) a condition with `calculatedValueSet` set but **no**
      `calculatedValuePlaceholderName` still expands `${calculatedEndDate}` in the inline
      `messageTemplate` exactly as today (regression guard for `DR-COEW-005`/`DR-YRO-004`
      behaviour);
      (b) a condition with `calculatedValueSet` **and** `calculatedValuePlaceholderName` set to
      e.g. `"resultLabel"` expands `${resultLabel}` (not `${calculatedEndDate}`) in the inline
      `messageTemplate`;
      (c) a condition with `calculatedValueSet` + `calculatedValuePlaceholderName` **and** an
      `errorMessageTemplate` containing the same custom token expands it correctly in the
      page-level `errorMessage` too;
      (d) a condition with an `errorMessageTemplate` but **no** `calculatedValueSet` (e.g.
      `DR-SENT-001` AC2's or `DR-AGE-007`'s shape) is completely unaffected — `errorMessage` is
      built exactly as today, with no exception and no accidental placeholder substitution
      attempted.
      Confirm each new/changed assertion fails for the right reason (assertion failure, not a
      missing symbol, for (a)/(d); compile-then-assertion-failure for (b)/(c) since the new
      field doesn't exist yet).

- [X] T003 Add `calculatedValuePlaceholderName` (`String`, nullable) to `ConditionDefinition` in
      `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionDefinition.java`. (Depends on
      T002.)

- [X] T004 In `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRule.java`:
      rename/generalise the private `calculatedValuePlaceholder(String)` helper to
      `calculatedValuePlaceholder(String placeholderName, String calculatedValue)`, defaulting
      `placeholderName` to `"calculatedEndDate"` when `condition.calculatedValuePlaceholderName()`
      is null; update the existing `messageTemplate` call site to pass the resolved name through;
      and extend the `errorMessage` build step so that when `condition.calculatedValueSet() !=
      null` and `offenceIdsForTemplate` is non-empty, it resolves
      `context.getCalculatedValue(calculatedValueSet, offenceIdsForTemplate.get(0))` and passes
      it as an extra placeholder into the `errorMessageTemplate` resolve call via the same
      helper — mirroring the existing per-offence resolution shape used for `messageTemplate`.
      Make T002 pass without changing the observable behaviour of any existing rule YAML that
      omits `calculatedValuePlaceholderName` and/or `errorMessageTemplate`/`calculatedValueSet`
      combinations it doesn't already use. (Depends on T003.)

- [X] T005 [P] Run `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CommunityOrderEndDatePreprocessorTest"`
      and `gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.YouthRehabilitationPreprocessorTest"`
      (and their corresponding rule/integration tests if any exist) to confirm T003/T004
      introduced no regression in the two existing rules that use `calculatedValueSet`. No file
      changes — this is a verification task; if it fails, fix forward in T004 before continuing.

### Engine generalisation B — defendant-name dedup and blank-name guard (research.md R8)

> **Identified during `/speckit.analyze`**: the per-breach-occurrence context design in
> Engine generalisation C below means the same defendant can be appended twice into the same
> page-level "This affects" name list (once per breach occurrence sharing the same breaching
> label). `DefaultValidationService.appendDefendantName` has no dedup and no blank-name guard
> today. Fixing this once, at that single call site, is what makes spec.md's "same code, two
> offences, same defendant" edge case (FR-005's "de-duplicated" requirement) and its "no name
> recorded" edge case (omit, don't render a placeholder) both hold.

- [X] T006 [P] Write failing unit test coverage in
      `src/test/java/uk/gov/hmcts/cp/services/impl/DefaultValidationServiceTest.java` for
      `appendDefendantName`'s dedup and blank-name guard (test via the public
      `evaluateRulesWithMdc`/`validate` entry point, mocking two `ValidationRule`s or one rule
      returning two `ValidationIssueResult`s, per this test class's existing conventions):
      (a) two `ValidationIssueResult`s from the same rule with an identical `errorMessage` and an
      identical, non-blank `affectedDefendantName` produce that name **exactly once** in the
      corresponding `errors.errorMessages` entry's resolved "This affects" text (not twice);
      (b) two such results with the identical `errorMessage` but **different**, non-blank
      `affectedDefendantName`s still produce **both** names, in first-seen order;
      (c) a result whose `affectedDefendantName` is `null` is silently omitted from the "This
      affects" text (not rendered as an empty entry, and does not push the message onto the
      "standalone" path — confirm `${defendantNames}`-bearing templates still resolve correctly
      when named);
      (d) a result whose `affectedDefendantName` is `""` (blank) is likewise omitted;
      (e) a hearing with **more than one defendant** where the *only* `ValidationIssueResult`
      contributing to a given `ruleId::errorMessage` group has a blank/`null`
      `affectedDefendantName` (i.e. every name for that group is blank) resolves that group's
      `${defendantNames}` clause to empty/stripped **without throwing** — this is the regression
      test for the `NullPointerException` risk identified in a second `/speckit.analyze` pass
      (data-model.md's companion `getOrDefault` fix); with only the guard from (c)/(d) in place
      and not this companion fix, this exact case throws;
      (f) the existing single-append-per-templateKey scenarios already covered by this test
      class for `DR-SENT-001`/`DR-AGE-007`-shaped rules remain green, unchanged.
      Confirm each fails for the right reason before implementing.

- [X] T007 In `src/main/java/uk/gov/hmcts/cp/services/impl/DefaultValidationService.java`, make
      **two** changes per data-model.md's exact diff: (1) change `appendDefendantName` to skip a
      `null`/blank `name`, and to skip adding `name` when it is already present in that
      `ruleId`'s name list; (2) in the loop that builds `errorMessages` from
      `errorBaseByTemplate`/`errorNamesByTemplate`, change
      `errorNamesByTemplate.get(entry.getKey())` to
      `errorNamesByTemplate.getOrDefault(entry.getKey(), List.of())` — required because change
      (1) means a group with every name blank never gets a map entry at all, and `.get()` alone
      would return `null` into `resolveDefendantNames`, NPE-ing on `names.isEmpty()`. Make T006
      pass in full — including (e) — without changing any other aggregation behaviour
      (standalone messages, warnings, `rulesEvaluated`, or any other rule's existing passing
      tests). (Depends on T006.)

### Engine generalisation C — new context and preprocessor

- [X] T008 [P] Write failing unit test `ApplicationResultBreachContextTest` in
      `src/test/java/uk/gov/hmcts/cp/services/rules/cel/ApplicationResultBreachContextTest.java`
      asserting: `toCelContext()` returns `{"hasBreach": 1L}`;
      `getOffenceIdSet("breachOffenceId")` returns `List.of(offenceId)` and throws
      `IllegalArgumentException` for any other set name; `allOffenceIds()` returns
      `List.of(offenceId)`; `getDefendantIdSet("defendantId")` returns `List.of(defendantId)` and
      throws `IllegalArgumentException` for any other set name;
      `getCalculatedValue("resultLabelByOffenceId", offenceId)` returns `resultLabel` for this
      context's own `offenceId` and `null` for a different offence id;
      `getCalculatedValue("resultLabelByOffenceId", ...)` with any other set name throws
      `IllegalArgumentException`. Confirm the test fails on assertion (or a compile error because
      the class doesn't exist yet — proceed straight to T009).

- [X] T009 Implement `ApplicationResultBreachContext` record implementing
      `RuleEvaluationContext` in
      `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ApplicationResultBreachContext.java` per
      data-model.md, to make T008 pass. `defendantName` MUST resolve to `""` (never `null`, never
      a placeholder such as `"Unknown"`) when the defendant cannot be resolved — see
      data-model.md and research.md R8 for why. (Depends on T008.)

- [X] T010 [P] Write failing unit tests `ApplicationResultOffencePreprocessorTest` in
      `src/test/java/uk/gov/hmcts/cp/services/rules/cel/ApplicationResultOffencePreprocessorTest.java`
      covering:
      (a) a result line with `shortCode: "LAWD"` and `offenceId` set yields exactly one context
      keyed by that line's `resultLineId`, with `resultLabel` equal to the line's `label`;
      (b) a result line whose `shortCode` is not in the configured `applicationOnlyShortCodes`
      set yields no context;
      (c) a result line with a `shortCode` in the configured set but a null/blank `offenceId`
      yields no context (defensive guard per research.md R1);
      (d) two result lines with different application-only short codes against the **same**
      `offenceId` yield two separate context entries (keyed by their distinct `resultLineId`s),
      each with its own `resultLabel`;
      (e) matching is case-insensitive (`lawd`, `Lawd`, `LAWD` all match);
      (f) a result line with a blank/null `label` falls back to the `shortCode` itself as
      `resultLabel` (never `null` or an empty string in the message);
      (g) a result line whose `defendantId` does not match any `DefendantDto` in the request
      resolves `defendantName` to `""` (empty string) rather than throwing or defaulting to a
      placeholder name such as `"Unknown"` — this defendant is then dropped entirely from any
      aggregated "This affects" list by the aggregation-layer guard (T006/T007), never rendered
      as an empty or placeholder name;
      (h) a request containing one malformed result line (e.g. null `resultLineId`) alongside an
      otherwise-valid breaching line does not throw, and the valid line's context is still
      produced — `preprocess()` must never let one bad line suppress every other breach in the
      same request;
      (i) a request with no application-only short codes present anywhere yields an empty map;
      (j) two result lines with the **same** application-only code against **different**
      `offenceId`s for the **same** `defendantId` yield two separate context entries (one per
      offence) — the preprocessor itself performs no dedup; collapsing the defendant's name in
      the aggregated page-level message despite two contexts existing is exercised end-to-end by
      T026, not asserted at this unit level.
      Confirm each fails for the right reason before implementing.

- [X] T011 Implement `ApplicationResultOffencePreprocessor` (`type()` returns
      `"application-result-offence"`) in
      `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ApplicationResultOffencePreprocessor.java`
      to make T010 pass, per data-model.md's algorithm. Guard per result line inside the loop so
      one malformed line can never throw out of `preprocess()` (mirrors the existing convention
      already documented for `AgeRestrictedImprisonmentPreprocessor`). (Depends on T009, T010.)

### Rule YAML and DB seed

- [X] T012 [P] Add `src/main/resources/rules/DR-APP-009.yaml` per
      `contracts/DR-APP-009.yaml`: `preprocessing.type: application-result-offence`,
      `applicationOnlyShortCodes: [ARBSFG, ARBSPG, ARBSR, VT, G, LAREP, LAREPCC, ORDC, LATG,
      LATR, LAWD, RFSD, SMAR]`, condition `AC1` with `expression: "hasBreach == 1"`,
      `severity: ERROR`, `validationLevel: OFFENCE`, `affectedOffenceSet: "breachOffenceId"`,
      `affectedDefendantSet: "defendantId"`, `calculatedValueSet: "resultLabelByOffenceId"`,
      `calculatedValuePlaceholderName: "resultLabel"`, `messageTemplate` and
      `errorMessageTemplate` exactly as drafted in the contract. (Depends on T004, T011 being
      available for the rule to actually load/evaluate in Foundational verification below, but
      the file itself has no code dependency and can be authored in parallel.)

- [X] T013 [P] Add migration `src/main/resources/db/migration/V1.010__insert_dr_app_009.sql`:
      ```sql
      INSERT INTO validation_rule (id, enabled, severity)
      VALUES ('DR-APP-009', true, 'ERROR');
      ```
      Mirrors `DR-AGE-007`'s and `DR-SEX-008`'s seed (ERROR-severity rules ship enabled).
      **Confirm with product/ops before merge** whether a soft-launch `enabled=false` rollout is
      wanted instead, given this is a new blocking ERROR affecting live Enter Results usage.

### Foundational verification

- [X] T014 Write failing integration test
      `nonBreachingResultLine_shouldNotRaiseErrorAndOtherRulesStillEvaluate` in
      `src/test/java/uk/gov/hmcts/cp/services/integration/ApplicationResultOffenceRuleIT.java`
      (extends `IntegrationTestBase`): submit a hearing where (i) one offence carries only
      non-application-only result codes (e.g. `IMP`), AND (ii) a separate offence/defendant in
      the same request is shaped to trigger a different existing rule (e.g. `DR-CTL-003` or
      `DR-SENT-001`). Assert: no `DR-APP-009` issue is raised; the other rule's issue IS present
      in the response; the HTTP response is a normal 200; `rulesEvaluated` still lists
      `DR-APP-009` (proving the rule ran to completion, not skipped by
      `DefaultValidationService`'s per-rule exception handler). Then implement/confirm whatever
      is needed to make it pass (this should already be true once T009–T013 are in place — this
      task is the checkpoint proving the wiring end-to-end, per `ValidationRuleAutoConfiguration`
      auto-discovering `DR-APP-009.yaml` at startup).

**Checkpoint**: `gradle build` passes; `DR-APP-009` loads at startup via
`ValidationRuleAutoConfiguration`; unit tests for the context, preprocessor, the generalised
`CelValidationRule` placeholder mechanism, and the `DefaultValidationService` dedup/blank-name
guard are all green; the existing `DR-COEW-005`/`DR-YRO-004` regression check (T005) is green;
the graceful/no-op IT (T014) is green.

---

## Phase 3: User Story 1 - Single application result recorded against an offence raises an ERROR (Priority: P1) 🎯 MVP

**Goal**: Prove the core safeguard — one application-only result recorded against one offence
blocks sharing with the exact required page-level and inline error text, and the affected
offence/defendant are correctly identified.

**Independent Test**: Submit a `DraftValidationRequest` with a single defendant, a single
offence, and one result line with `shortCode: "LAWD"`, `label: "Legal Aid Withdrawn"` against
that offence. Assert the response contains a `DR-APP-009` issue at `ERROR`/`OFFENCE` naming the
offence, with `isValid=false`, and the exact inline and page-level message text (with no
"This affects:" clause, since there is only one defendant).

- [X] T015 [US1] Write failing integration test
      `applicationOnlyResultAgainstOffence_singleDefendant_shouldRaiseBlockingErrorWithNoAffectsClause`
      in `ApplicationResultOffenceRuleIT.java` (same file as T014 — sequenced after it, not
      parallel): one defendant, one offence, one `LAWD` result line against it. Assert:
      `validationIssues` contains `ruleId="DR-APP-009"`, `severity="ERROR"`,
      `validationLevel="OFFENCE"`, the offence present in `affectedOffences` with message
      `"Remove Legal Aid Withdrawn from this offence. It is an application result, so it can
      only be added to an application."`; `isValid=false`; `errors.errorMessages` contains
      exactly `"Legal Aid Withdrawn is an application result. It cannot be added to an offence.
      Remove it from the offence and add an application to the hearing."` with **no** trailing
      "This affects" sentence (single-defendant hearing).

- [X] T016 [US1] In the same test class, add
      `applicationOnlyResultAgainstOffence_multipleDefendants_shouldNameAffectedDefendant`: a
      hearing with two defendants, only one of whom has the breaching `LAWD` result against
      their offence. Assert the same error text as T015 but with the trailing
      `"This affects: <that defendant's name>."` clause present, and that the other, unaffected
      defendant's name does not appear. (Depends on T015 — same file.)

- [X] T017 [US1] In the same test class, add
      `applicationOnlyResultAgainstOffence_forEachCodeInScope_shouldRaiseError`, parameterised
      (JUnit 5 `@ParameterizedTest`/`@ValueSource` or a manual loop) over all thirteen codes
      (`ARBSFG`, `ARBSPG`, `ARBSR`, `VT`, `G`, `LAREP`, `LAREPCC`, `ORDC`, `LATG`, `LATR`, `LAWD`,
      `RFSD`, `SMAR`) each with a representative `label`, asserting each individually raises a
      `DR-APP-009` issue naming that code's own label. (Depends on T015 — same file.)

- [X] T018 [US1] In the same test class, add
      `validResultAgainstOffence_shouldNotRaiseError`: an offence carrying only a non-application
      short code (e.g. `IMP`) plus a valid result; assert no `DR-APP-009` issue is raised, so
      legitimate results are never mistaken for breaches. (Depends on T015 — same file; largely
      overlaps with T014 but scoped as this story's own explicit "no false positive" check per
      spec.md's edge cases.)

- [X] T019 [US1] In the same test class, add
      `removingTheBreachingResult_shouldClearThePreviouslyRaisedError`: submit the T015 scenario
      and assert the error is present; then resubmit the identical request with the `LAWD`
      result line removed from the offence, asserting no `DR-APP-009` issue in the second
      response (covers spec.md User Story 1's scenario 7 — resolving the breach clears the
      error). (Depends on T015 — same file.)

- [X] T020 [US1] In the same test class, add
      `mixedApplicationOnlyAndValidResultsOnSameOffence_shouldRaiseErrorOnlyForApplicationOnlyResult`:
      one offence carrying both a valid result (e.g. `IMP`) and one application-only result
      (e.g. `LAWD`), same defendant. Assert exactly one `DR-APP-009` issue is raised for that
      offence, naming only `"Legal Aid Withdrawn"`, and that the valid result plays no part in
      the response (covers spec.md's edge case: "an offence carries both an application-only
      result and other, valid results → only the application-only results raise errors").
      (Depends on T015 — same file.)

**Checkpoint**: User Story 1 passes independently. The core safeguard, its exact message text,
the single-vs-multi-defendant "This affects" behaviour, the valid/breach boundary on a mixed
offence, and remediation are all proven end-to-end — demonstrable as an MVP slice.

---

## Phase 4: User Story 2 - Multiple application results against offences: every breach is reported (Priority: P1)

**Goal**: Prove that a single validation pass reports every breach — whether several breaches on
one offence, breaches spread across several offences for one defendant, or breaches spread
across several defendants — with correct per-breach inline positioning and a correctly
aggregated, deduplicated, comma-separated "This affects" list.

**Independent Test**: Submit a hearing with (a) two application-only result lines against the
same offence, and (b) a third application-only result line against a different offence for a
different defendant. Assert three separate inline errors are returned (two against the first
offence, one against the second), and that the page-level errors correctly attribute defendants
per breaching label.

- [X] T021 [US2] Write failing integration test
      `multipleApplicationOnlyResultsOnSameOffence_shouldRaiseSeparateInlineErrorPerResult` in
      `ApplicationResultOffenceRuleIT.java` (same file — sequenced after Phase 3's tasks): one
      defendant, one offence, two result lines (`LAWD` and `RFSD`) both against that offence.
      Assert `affectedOffences` for that offence carries **two** distinct messages (one naming
      "Legal Aid Withdrawn", one naming "Application refused"), and `errors.errorMessages`
      contains two distinct page-level entries, one per label. (Depends on T020 — same file.)

- [X] T022 [US2] In the same test class, add
      `applicationOnlyResultsAcrossMultipleOffencesSameDefendant_shouldRaiseErrorPerOffence`: one
      defendant with two offences, each carrying a different application-only result. Assert two
      separate inline errors (one per offence) and confirm the defendant's name appears exactly
      once per distinct page-level label (not duplicated) in "This affects". (Depends on T021 —
      same file.)

- [X] T023 [US2] In the same test class, add
      `applicationOnlyResultsAcrossMultipleDefendants_shouldListAllAffectedDefendantsCommaSeparated`:
      three defendants; two of them each have an offence carrying the **same** application-only
      result code (e.g. both `LAWD`), the third defendant has no breach. Assert a single
      page-level entry for that label with `"This affects: <defendant A> and <defendant B>."`
      (comma-and-formatting per `MessageTemplateResolver.resolveDefendantNames`), and that the
      third, unaffected defendant is not named. (Depends on T021 — same file.)

- [X] T024 [US2] In the same test class, add
      `partiallyResolvedBreaches_shouldStillBlockOnRemainingBreach`: the T021 two-breach-on-one-
      offence scenario, resubmitted with only the `LAWD` result line removed (leaving `RFSD` in
      place). Assert the `RFSD` issue is still present (sharing remains blocked) and the `LAWD`
      issue is gone. (Depends on T021 — same file; covers spec.md User Story 2 scenario 4.)

- [X] T025 [US2] In the same test class, add
      `allBreachesResolved_shouldClearAllApplicationResultOffenceErrors`: the T021 scenario,
      resubmitted with both result lines removed. Assert no `DR-APP-009` issue remains in the
      response. (Depends on T021 — same file; covers spec.md User Story 2 scenario 5.)

- [X] T026 [US2] In the same test class, add
      `sameApplicationOnlyCodeAcrossTwoOffencesSameDefendant_shouldNameDefendantOnceInAggregatedMessage`:
      one defendant, two offences, **both** carrying the **same** application-only code (e.g.
      both `LAWD`). Assert: two separate inline errors, one on each offence (`affectedOffences`
      carries an entry for each); but exactly **one** page-level `errors.errorMessages` entry
      naming `"Legal Aid Withdrawn"`, with the defendant's name appearing **exactly once** — not
      twice — in its "This affects" clause. This is the end-to-end regression test for
      research.md R8 / T006–T007's dedup fix, and directly covers spec.md's edge case: "the same
      application-only result code appears on two different offences for the same defendant ...
      the defendant is named once in the This affects: list." (Depends on T021 — same file.)

**Checkpoint**: User Story 2 passes independently. Complete-in-one-pass reporting, correct inline
positioning per breach, and correct — deduplicated — defendant aggregation are all proven
end-to-end.

---

## Phase 5: User Story 3 - Amendment of an already-shared hearing (Priority: P2)

**Goal**: Prove the same rule fires unchanged on the amendment path, and blocks the
validate-and-reshare flow the same way it blocks the original share.

**Independent Test**: Submit the same validation request shape used for an amendment of an
already-shared hearing, with an application-only result against an offence, and assert the
identical `DR-APP-009` error is returned.

- [X] T027 [US3] Write failing integration test
      `amendmentRequestWithApplicationOnlyResultAgainstOffence_shouldRaiseSameBlockingError` in
      `ApplicationResultOffenceRuleIT.java` (same file — sequenced after Phase 4's tasks): submit
      a `DraftValidationRequest` shaped as this service's existing amendment-validation path
      (same endpoint/contract used for a previously-shared hearing's re-validation — confirm the
      exact existing convention by checking how `ValidationController`/`ValidationApi` model an
      amendment request today, since `DraftValidationRequest` carries no explicit
      "is-amendment" flag observed in the DTO) with an application-only result against an
      offence. Assert the response carries the identical `ruleId`, `severity`, message text, and
      blocking (`isValid=false`) outcome as the equivalent original-share scenario in T015.
      (Depends on T020/T026 — same file, and reuses the same rule/preprocessor; no new
      production code is expected to be needed if T004–T013 are correct, since this service does
      not currently special-case "amendment" differently from any other validation request — flag
      in the PR description if that assumption turns out to be wrong.)

**Checkpoint**: All three user stories pass independently. `gradle test` is green end-to-end for
this feature.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T028 [P] Run `gradle checkstyleMain checkstyleTest` and fix any Google-style violations in
      every new/modified production and test file (`maxWarnings = 0`).
- [X] T029 [P] Run `gradle pmdMain pmdTest` and address any findings in the new/modified files.
- [X] T030 Run `gradle jacocoTestReport` and confirm coverage is reported for
      `ApplicationResultBreachContext`, `ApplicationResultOffencePreprocessor`, the generalised
      branches of `CelValidationRule` (both the existing-behaviour and new-behaviour paths from
      T002), and the generalised branches of `DefaultValidationService.appendDefendantName`
      (dedup and blank-name-skip paths from T006).
- [X] T031 Execute `quickstart.md`'s manual verification steps against a running instance (or via
      `gradle api`) and confirm the documented request/response shapes match reality.
- [X] T032 Run this feature through the mandatory build loop
      (`code-reviewer` → `qa` → `spec-validator` agents per `.claude/rules/workflow.md`) and
      address findings until all three return PASS/COMPLIANT. Pay particular attention to the
      `CelValidationRule`/`ConditionDefinition` change (T003/T004) and the
      `DefaultValidationService.appendDefendantName` change (T007) — both are shared,
      rule-agnostic code touched by a single-rule feature, and should be reviewed for backward
      compatibility with every existing rule YAML and every existing rule's aggregation
      behaviour, not just this feature's own.
- [X] T033 [P] Add live API test coverage in
      `src/apiTest/java/uk/gov/hmcts/cp/http/ApplicationResultOffenceApiHttpLiveTest.java`,
      mirroring the pattern used for `DR-AGE-007`/`DR-SEX-008` live tests (no JDBC enable/disable
      dance needed — `DR-APP-009` ships `enabled=true`, same as those rules, unless T013's
      product/ops confirmation says otherwise). Cover representative scenarios from each user
      story against the real docker-compose stack (real Postgres, real Flyway-applied `V1.010`
      seed) rather than TestContainers.

**Do not** add a new per-rule severity-ceiling/override integration test for `DR-APP-009`. That
mechanism is proven once, against `DR-SENT-001`, in `ValidationRuleOverrideIntegrationTest` — per
`.claude/rules/design_rules.md`, reviewers should reject a duplicate. Extend that shared test
only if evaluating `DR-APP-009` surfaces a genuine gap in the shared override mechanism itself.
Likewise, do not add a new live-HTTP PATCH round-trip test for the rule-update write path — that
is covered once in `ValidationRulesApiHttpLiveTest`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS all user stories — none of the ITs in
  Phases 3–5 can pass without both engine fixes, the preprocessor, the context, and the YAML
  existing.
- **User Stories (Phases 3–5)**: All depend on Foundational completion. They share one test
  class (`ApplicationResultOffenceRuleIT`), so within that file, tasks are sequential by
  necessity (same-file edits), even though they map to different stories.
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: No dependency on US2/US3 beyond the shared Foundational phase.
- **User Story 2 (P1)**: No dependency on US1's test cases; shares the same test file, so runs
  after US1's tasks are committed to avoid merge conflicts, not because of a logical dependency.
- **User Story 3 (P2)**: Reuses the rule/preprocessor proven by US1/US2; sequenced after them in
  the shared test file for the same file-conflict reason, not a logical dependency.

### Parallel Opportunities

- T002 (engine placeholder-generalisation unit tests), T006 (aggregation dedup unit tests), and
  T008 (new context unit tests) can all be authored in parallel — three different files, no
  dependency on each other.
- T005 (calculated-value regression verification) can run in parallel with T006–T011 once T004
  lands — different concern, no shared file.
- T012 (YAML) and T013 (migration) can be done in parallel with T008–T011 — different files.
- T028, T029, T033 in Polish can run in parallel — independent tooling/test tasks.
- Within `ApplicationResultOffenceRuleIT.java`, tasks T014–T027 are all same-file edits and are
  therefore sequential regardless of `[P]` eligibility by story.

---

## Parallel Example: Foundational Phase

```bash
# Author all three Foundational test files together (different files, no shared dependency):
Task: "Write failing unit test coverage for calculated-value placeholder generalisation in src/test/.../CelValidationRuleTest.java"
Task: "Write failing unit test coverage for appendDefendantName dedup/blank-name guard in src/test/.../DefaultValidationServiceTest.java"
Task: "Write failing unit test ApplicationResultBreachContextTest in src/test/.../ApplicationResultBreachContextTest.java"

# Author the YAML and migration together (different files, no code dependency):
Task: "Add src/main/resources/rules/DR-APP-009.yaml"
Task: "Add src/main/resources/db/migration/V1.010__insert_dr_app_009.sql"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (both engine fixes, preprocessor, context, YAML, migration) —
   this is the largest phase, since it also carries the two shared-code changes.
3. Complete Phase 3: User Story 1.
4. **STOP and VALIDATE**:
   `gradle test --tests "...ApplicationResultOffenceRuleIT"` green for the US1 cases; confirms
   the core safeguard (single breach → blocking error with correct text) works.
5. Continue to US2 before considering this feature demoable — US1 alone does not prove complete,
   single-pass reporting of multiple breaches (nor the dedup fix), which is an explicit,
   equally-weighted P1 acceptance criterion (AC2).

### Incremental Delivery

1. Setup + Foundational → rule pipeline exists and loads at startup.
2. Add User Story 1 → the core safeguard proven (single breach, exact message text, "This
   affects" single/multi-defendant behaviour, mixed-offence boundary, remediation clears the
   error).
3. Add User Story 2 → complete-in-one-pass reporting and deduplicated aggregation proven; this
   is the point both P1 acceptance criteria (AC1 and AC2) are fully satisfied.
4. Add User Story 3 → the amendment path proven; feature considered complete.

### Parallel Team Strategy

Given the small size of this feature (two shared-engine fixes, one preprocessor, one context, one
test class), splitting across multiple developers is unlikely to be worthwhile for the
user-story phases — they share one file. The Foundational phase's three independent tracks
(calculated-value generalisation T002–T005, aggregation dedup fix T006–T007, new
preprocessor/context T008–T011) could be split across up to three developers if staffed,
converging before T014.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- All `ApplicationResultOffenceRuleIT` cases live in one file — group with
  `@Nested`/`@DisplayName` per `.claude/rules/technical-rules.md` conventions, even though
  they're committed sequentially.
- Verify each test fails for the right reason before writing the code that makes it pass
  (Constitution Principle VIII) — for T002 and T006 specifically, confirm the *new* assertions
  you're adding fail only because the new behaviour doesn't exist yet, not because you've broken
  the *existing* passing behaviour they also guard.
- Commit after each task or logical group, per repository convention (Conventional Commits).
- T002–T007 (the two engine fixes) are the highest-risk part of this feature precisely because
  they touch shared, rule-agnostic code used by every other rule in the service — treat T005's
  and T006's regression assertions as non-negotiable before moving on to T008.
- T026 is the single most important task in this feature to get right: it is the only place the
  full defendant-name-dedup path (context construction → aggregation → formatting) is exercised
  end-to-end. Do not skip or weaken it.
