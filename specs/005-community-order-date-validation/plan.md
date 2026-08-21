# Implementation Plan: Community Order End Date Validation

**Branch**: `dev/DD-42678-co-end-date-rule` | **Date**: 2026-05-20 | **Updated**: 2026-08-05
**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md) | **Data model**: [data-model.md](data-model.md)

> This plan was originally authored against `team/DD-41653` (rule id `DR-COEW-001`). It has been
> retrofitted here to describe what actually shipped on `dev/DD-42678-co-end-date-rule`: the same
> rule, renumbered `DR-COEW-005`, refactored to share plumbing with `YouthRehabilitationPreprocessor`
> via a new `PreprocessorHelper`, and extended with `masterDefendantId` grouping. See the
> [Retrofit Notes](#retrofit-notes-2026-08-05) section at the end. It was subsequently extended
> with the DD-41655 requirement duration end date checks (DUR-CUR/CURE/AAR) ported from PR #116
> onto `DR-COEW-005` — see [Port Notes](#port-notes-2026-08-05--dd-41655-requirement-duration-end-date-validation) at the very end.

---

## Summary

Implement the `DR-COEW-005` validation rule that detects community order date errors at "Save and continue":

- **AC2** — A community order's end date is earlier than the end date of any attached requirement (CUR, CURE, CURA, or AAR).
- **DUR-CUR/CURE/AAR** — A CUR, CURE, or AAR requirement's own recorded end date does not match its calculated duration (start date + period − 1 day, or hearing date + days − 1 day for AAR), independent of and additive to AC2.

The implementation follows the established YAML+CEL rule engine pattern: a new `DR-COEW-005.yaml` rule file, a new `CommunityOrderEndDatePreprocessor` Spring component, a new `CommunityOrderContext` record, and minor extensions to `PreprocessingDefinition`. The preprocessor delegates its shared plumbing (short-code matching, defendant grouping, name assembly, prompt-date parsing) to `PreprocessorHelper`, which is also used by `YouthRehabilitationPreprocessor`. AC1 ("end date must be in the future") is **out of scope** — handled by a separate ticket.

---

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), `api-cp-crime-hearing-results-validator:0.1.6` (provides `DraftValidationRequest`, `ResultLineDto`, `Prompt`), Lombok, Jackson (YAML deserialisation)
**Storage**: PostgreSQL — existing `validation_rule` table for runtime severity overrides; no new tables or migrations beyond the seed row for `DR-COEW-005`
**Testing**: JUnit 5 + Mockito + AssertJ (unit), MockMvc + TestContainers + WireMock (integration)
**Target Platform**: Azure-hosted Spring Boot service, local port 4550
**Project Type**: Web service — stateless validation API
**Performance Goals**: No change to existing Gatling assertion thresholds; rule evaluation is per-request and stateless
**Constraints**: Checkstyle Google (`maxWarnings = 0`), PMD (`ignoreFailures = false`), no wildcard imports
**Scale/Scope**: One new YAML rule, one new preprocessor, one new context record, shared-helper extraction, five new fields on `PreprocessingDefinition` (two of which — `curfewShortCodes`, `curfewTagShortCodes`, `furtherCurfewShortCodes` — are also consumed by `YouthRehabilitationPreprocessor`)

---

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| **I — YAML/CEL Rule-First** | ✅ PASS | `DR-COEW-005.yaml` is authored before any Java change; all conditions and short-code lists live in YAML |
| **II — Constructor Injection & Immutable DTOs** | ✅ PASS | `CommunityOrderContext` is a Java record; `CommunityOrderEndDatePreprocessor` uses `@Component` with no field injection; `PreprocessorHelper` is a stateless static-method utility class |
| **III — Layered Architecture & Preprocessor Dispatch** | ✅ PASS | New preprocessor plugs in via `PreprocessorRegistry` using the `type()` qualifier; zero changes to `CelValidationRule` |
| **IV — Spec-Driven Build Loop** | ✅ PASS | This plan document; tasks.md and implement follow; `code-reviewer`, `qa`, `spec-validator` gate before ship |
| **V — HMCTS Standards** | ✅ PASS | Java 25, Spring Boot 4, Gradle, SLF4J logging only |
| **VI — Severity Ceiling** | ✅ PASS | All four conditions set `severity: ERROR`; ceiling can lower to WARNING at runtime but never promotes |
| **VII — No System.out** | ✅ PASS | All diagnostic output via SLF4J (`@Slf4j`, used in `PreprocessorHelper`) |
| **VIII — TDD** | ✅ PASS | Plan mandates failing tests authored before production code for every behaviour |

No violations. Complexity Tracking section not required.

---

## Project Structure

### Documentation (this feature)

```text
specs/005-community-order-date-validation/
├── spec.md          ✅ complete
├── research.md      ✅ complete
├── data-model.md     ✅ complete
├── plan.md          ✅ this file
└── tasks.md         ✅ complete (retrofit)
```

### Source Code Changes

```text
src/main/resources/rules/
└── DR-COEW-005.yaml                                           [NEW]

src/main/resources/db/migration/
└── V1.005__insert_dr_coew_005.sql                              [NEW — seeds enabled: true]

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── CelValidationRule.java                                     [MODIFY (DD-41655) — calculatedValueSet branch feeding ${calculatedEndDate}]
├── ConditionDefinition.java                                   [MODIFY (DD-41655) — +calculatedValueSet field]
├── RuleEvaluationContext.java                                 [MODIFY (DD-41655) — +getCalculatedValue default method]
├── MessageTemplateResolver.java                               [MODIFY (DD-41655) — +6-arg resolve(...) overload with extraPlaceholders]
├── PreprocessingDefinition.java                                [MODIFY — 5 new fields, 3 shared with YRO]
├── CommunityOrderEndDatePreprocessor.java                     [NEW; MODIFY (DD-41655) — +duration-mismatch checks]
└── CommunityOrderContext.java                                 [NEW; MODIFY (DD-41655) — +9 duration-mismatch fields]

src/test/java/uk/gov/hmcts/cp/
├── services/rules/cel/
│   ├── CommunityOrderEndDatePreprocessorTest.java             [NEW — unit, includes masterDefendantId grouping; MODIFY (DD-41655) — +DUR-CUR/CURE/AAR nested classes]
│   ├── CommunityOrderContextTest.java                         [NEW — unit; MODIFY (DD-41655) — +duration-mismatch nested class]
│   ├── MessageTemplateResolverTest.java                        [MODIFY (DD-41655) — +extraPlaceholders coverage]
│   └── RuleDefinitionTest.java                                 [MODIFY (DD-41655) — +calculatedValueSet parsing coverage]
├── integration/
│   ├── CommunityOrderEndDateRuleIntegrationTest.java          [NEW — integration; MODIFY (DD-41655) — +User Stories 4-7]
│   ├── ValidationControllerIntegrationTest.java               [MODIFY — rule count]
│   ├── ValidationRulesControllerIntegrationTest.java          [MODIFY — rule count]
│   ├── ValidationRulesUpdateIntegrationTest.java              [MODIFY — rule count]
│   └── CrossRuleRegressionIntegrationTest.java                [MODIFY — rule count]
└── config/
    └── ValidationRuleAutoConfigurationTest.java                [MODIFY — +1 preprocessor, rule count]

src/apiTest/java/uk/gov/hmcts/cp/http/
└── CommunityOrderEndDateApiHttpLiveTest.java                   [MODIFY (DD-41655) — +DUR-CUR live scenario]
```

**No changes to**: `ValidationPreprocessor` interface, `PreprocessorRegistry`, `DefaultValidationService`, `ValidationController` (the `affectedDefendants` population described below was already present on `main`/`dev/DD-42678-co-end-date-rule` by the time this rule was ported — no framework change was required here, unlike on the original `team/DD-41653` branch where it was introduced alongside this rule). `RuleEvaluationContext`, `MessageTemplateResolver`, and `CelValidationRule` were additive-only extended by the later DD-41655 port (see [Port Notes](#port-notes-2026-08-05--dd-41655-requirement-duration-end-date-validation)) — no existing behaviour changed, only new optional fields/overloads added.

> **`affectedDefendants` on `ValidationIssue`**: Each emitted `ValidationIssue` includes
> `affectedDefendants: [{ defendantId }]` — the defendant(s) whose context triggered the
> condition. The UI uses this to look up the defendant's display name and render the
> "This affects: ..." line.

---

## Phase 0: Research (complete)

All unknowns resolved. See [research.md](research.md) for full decisions, including Decision 8
(the rename to `DR-COEW-005` and the `PreprocessorHelper` extraction).

Key resolved items:
- `List<Prompt> prompts` confirmed on `ResultLineDto` in v0.1.6; `Prompt` has `getPromptRef()` / `getPromptValue()`
- Prompt ref keys hardcoded: `endDate`, `endDateOfTag`, `until`
- Multiple violations: separate condition per requirement type (one `ValidationIssue` each)
- Share button: hearing-level (hidden if any defendant has errors)
- AC1: out of scope
- Rule ID: `DR-COEW-005` (migration slots 001–004 already claimed on this branch)
- Grouping: per-defendant, folding `defendantId`s that share a `masterDefendantId`

---

## Phase 1: Design & Contracts

### 1a. Data model

See [data-model.md](data-model.md) for full entity definitions, CEL variable map, and named offence-id sets.

**Summary of new/changed types:**

| Type | Change | Location |
|------|--------|----------|
| `PreprocessingDefinition` | +5 `List<String>` fields (3 shared with `YouthRehabilitationPreprocessor`) | `cel/PreprocessingDefinition.java` |
| `PreprocessorHelper` | NEW static-utility class — short-code matching, grouping, dedupe-by-`masterDefendantId`, prompt-date parsing | `cel/PreprocessorHelper.java` |
| `CommunityOrderContext` | NEW record, implements `RuleEvaluationContext` | `cel/CommunityOrderContext.java` |
| `CommunityOrderEndDatePreprocessor` | NEW `@Component`, type `"community-order-end-date"`, delegates to `PreprocessorHelper` | `cel/CommunityOrderEndDatePreprocessor.java` |
| `DR-COEW-005.yaml` | NEW rule, priority 4000, 4 conditions | `resources/rules/DR-COEW-005.yaml` |
| `V1.005__insert_dr_coew_005.sql` | NEW migration, seeds `enabled: true` | `resources/db/migration/` |

### 1b. Interface contracts

No new API surface. The existing `POST /api/validation/validate` endpoint is unchanged. New `ValidationIssue` entries of type ERROR appear in the `errors` list of `DraftValidationResponse` when AC2 violations are detected — this is an additive, backward-compatible change.

Each emitted `ValidationIssue` carries:
- `affectedOffences` — offences scoped to the specific violation (existing field, set by all rules)
- `affectedDefendants` — `[{ defendantId: "<id>" }]` — the defendant whose context triggered this issue, folded to the `masterDefendantId` group where applicable

The UI resolves the defendant display name from `affectedDefendants[].defendantId` and renders "This affects: <<name>>" in the error summary.

### 1c. Agent context

`CLAUDE.md` should be updated to point to this plan when this feature is the active point of work.

---

## Implementation Sequence (for /speckit-tasks)

See [tasks.md](tasks.md) for the full retrofit task list. Summary:

### Step 1 — YAML Rule First (Constitution Principle I)

Author `DR-COEW-005.yaml` with all 4 conditions before writing any Java.

### Step 2 — Extend `PreprocessingDefinition`

Add the 5 new `List<String>` fields (3 shared with the YRO preprocessor).

### Step 3 — Extract `PreprocessorHelper`

Pull short-code matching, result-line grouping, `masterDefendantId` dedupe-key building, defendant-name assembly, and prompt-date parsing into a shared static-utility class, reused by both `CommunityOrderEndDatePreprocessor` and `YouthRehabilitationPreprocessor`.

### Step 4 — `CommunityOrderContext` record (test-first)

1. Write `CommunityOrderContextTest` — assert `toCelContext()` returns correct map and `getOffenceIdSet()` dispatches correctly.
2. Write `CommunityOrderContext.java` to pass.

### Step 5 — `CommunityOrderEndDatePreprocessor` (test-first)

1. Write `CommunityOrderEndDatePreprocessorTest` covering AC2a–d, boundary equality, multiple defendants, multiple violations, missing/null prompts, and `masterDefendantId` grouping.
2. Write `CommunityOrderEndDatePreprocessor.java` (delegating to `PreprocessorHelper`) to pass all tests.

### Step 6 — Integration test (test-first, end-to-end)

1. Write `CommunityOrderEndDateRuleIntegrationTest` extending `IntegrationTestBase` covering Scenarios 1–9 from the spec, error-summary response structure, and offence-scoping.
2. Run integration tests to confirm all pass.

### Step 7 — Update rule-count assertions

Update `ValidationRuleAutoConfigurationTest`, `ValidationControllerIntegrationTest`, `ValidationRulesControllerIntegrationTest`, `ValidationRulesUpdateIntegrationTest`, and `CrossRuleRegressionIntegrationTest` for the new rule count and `DR-COEW-005` id.

### Step 8 — Quality gates

```bash
gradle test                    # all unit + integration tests green
gradle checkstyleMain          # zero warnings
gradle pmdMain                 # no violations
gradle build                   # full build clean
```

---

## Complexity Tracking

*(No constitution violations — section left empty per template instructions)*

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `promptRef` key names differ from assumed values (`endDate`, `endDateOfTag`, `until`) | Low | High | Verified against real request payloads; unit tests fail fast if keys don't match |
| `promptValue` not present for all result lines in test data | Medium | Low | Preprocessor skips null/blank values and logs WARN; integration tests use explicit test data |
| Community order and requirement result lines not co-located on same offence | Low | High | Confirmed by architecture: all result lines share `offenceId` |
| Shared `PreprocessorHelper` regresses `YouthRehabilitationPreprocessor` behaviour | Medium | Medium | `PreprocessorRegistryTest` and both preprocessors' unit suites re-run after extraction; no behavioural change intended, pure code move |
| Rule-id renumbering (`DR-COEW-001` → `DR-COEW-005`) missed in a test or migration | Low | Medium | `git grep 'COEW-001'` returned no hits on this branch after the rename commit; confirmed via `ValidationRuleAutoConfigurationTest` and `CommunityOrderEndDateRuleIntegrationTest` |

---

## Retrofit Notes (2026-08-05)

This spec folder did not exist on `dev/DD-42678-co-end-date-rule` even though the corresponding
code had already shipped (commits `1ace279`, `d00bc34`, `37c7600`, `46763eb`, `9ab8a30`). It was
authored after the fact by porting `specs/005-community-order-date-validation/` from
`team/DD-41653` and updating it to match what actually landed here:

1. Rule id `DR-COEW-001` → `DR-COEW-005` (migration `V1.005`, not `V1.004`).
2. DB seed `enabled: false` → `enabled: true`.
3. `CommunityOrderEndDatePreprocessor` refactored to delegate shared logic to a new
   `PreprocessorHelper`, also used by `YouthRehabilitationPreprocessor`.
4. Added `masterDefendantId`-based defendant grouping (FR-005, Scenario 9), not present in the
   original `team/DD-41653` design.
5. `PreprocessingDefinition`'s `curfewShortCodes`, `curfewTagShortCodes`, and
   `furtherCurfewShortCodes` fields are now shared between this rule and
   `YouthRehabilitationPreprocessor` rather than being community-order-exclusive.

No further code changes are required by this retrofit — it is documentation-only, added to close
the gap identified when comparing `team/DD-41653` against `dev/DD-42678-co-end-date-rule`.

---

## Port Notes (2026-08-05) — DD-41655 Requirement Duration End Date Validation

`PR #116` (`DD-41655-requirement-duration-validation`, merged upstream against the older
`team/DD-41655-CO-duration-validation` → `DR-COEW-001` lineage) added three new conditions
(DUR-CUR, DUR-CURE, DUR-AAR) checking that a CUR/CURE/AAR requirement's own recorded end date
matches its calculated duration. That branch was never merged into `dev/DD-42678-co-end-date-rule`
directly — by the time DD-41655 needed porting here, this branch's history had already diverged
too far (rule renumbered to `DR-COEW-005`, `PreprocessorRegistry` dispatch added, `masterDefendantId`
grouping added) for a mechanical `git merge`/cherry-pick of PR #116's seven commits to apply
cleanly. Instead, PR #116's functional diff was re-applied by hand onto the current structure:

1. `DR-COEW-005.yaml` gained three new conditions (DUR-CUR, DUR-CURE, DUR-AAR) — same expressions,
   messages, and severities as PR #116's `DR-COEW-001.yaml`, unchanged apart from the rule id.
2. `CommunityOrderContext` gained 9 new fields (3 counts, 3 offence-id lists, 3 calculated-end-date
   maps) and a `getCalculatedValue(setName, offenceId)` override — same shape as PR #116.
3. `CommunityOrderEndDatePreprocessor` gained the duration-mismatch checks, re-homed onto this
   branch's `masterDefendantId`-based grouping and `PreprocessorHelper` delegation (PR #116 grouped
   by raw `defendantId` only, since `masterDefendantId` grouping didn't exist yet on its lineage).
4. Period-value parsing (`ParsedPeriod`, `PERIOD_PATTERN`, `parsePromptPeriod`) stayed private to
   `CommunityOrderEndDatePreprocessor`, matching PR #116's original shape — an earlier draft of
   this port promoted a generic `parsePromptInt` into `PreprocessorHelper`, but that was reverted
   once the unit-aware period-parsing fix (see below) made a shared *integer* parser the wrong
   abstraction; the `"<n> <unit>"` format is specific to this duration calculation.
5. `ConditionDefinition` gained `calculatedValueSet`; `RuleEvaluationContext` gained a default
   `getCalculatedValue(...)` method; `MessageTemplateResolver` gained a 6-arg `resolve(...)`
   overload accepting `extraPlaceholders`; `CelValidationRule` gained the branch that resolves
   `calculatedValueSet` per offence into the `${calculatedEndDate}` placeholder — all four changes
   are identical in shape to PR #116's, modulo this branch's richer `recordIssue(...)` signature
   (condition description + validation level, added independently of DD-41655) being left intact.
6. Test suites (`CommunityOrderContextTest`, `CommunityOrderEndDatePreprocessorTest`,
   `MessageTemplateResolverTest`, `RuleDefinitionTest`, `CommunityOrderEndDateRuleIntegrationTest`,
   `CommunityOrderEndDateApiHttpLiveTest`) were extended with PR #116's equivalent scenarios,
   adapted to this branch's fixtures (`DR-COEW-005`, dedupe-key grouping helpers).

No DB migration was needed — conditions live entirely in YAML; the existing `V1.005` override row
governs all seven `DR-COEW-005` conditions uniformly. See spec.md's "Session 2026-08-05 — port
requirement duration end date validation (DD-41655) from PR #116" clarification and data-model.md
for the full field/condition reference.

**Two post-merge fixes carried forward.** PR #116's own branch lineage (`team/DD-41655-CO-duration-validation`)
had two further merged PRs discovered *after* PR #116 landed, each fixing a real defect confirmed
against a live payload. Both defects would have made a duration-mismatch condition silently never
fire — no test failure, no exception, just a dead condition — so this port carries the *fixed*
behaviour directly rather than the code PR #116 originally shipped:

1. **Period value format** (PR #127, commit `1dd5dba`): `curfewPeriod` and
   `curfewAndElectronicMonitoringPeriod` are sent as `"<n> <unit>"` (e.g. `"90 Days"`,
   `"1 Months"`, `"1 weeks"`), not bare integers. `CommunityOrderEndDatePreprocessor` parses these
   unit-aware (Days/Weeks/Months, case-insensitive, calendar-correct via `LocalDate.plus`), with a
   bare-integer fallback for backward compatibility and warn-and-skip guards for an unrecognised
   unit, numeric overflow, or an out-of-range calculated date. See research.md Decision 9.
2. **AAR prompt-ref key** (PR #133, commit `6199910`): the real key is
   `numberOfDaysToAbstainFromConsumingAnyAlcohol`, not `numberOfDaysToAbstain`. See research.md
   Decision 9.

A code-review pass on the first version of this port (before these two fixes were incorporated)
caught both regressions by cross-referencing this repository's own git history — see the review
transcript for this change.

**One additional defect found and fixed during the port itself (not present in any upstream
commit).** The port's initial period-overflow guard caught only `DateTimeException` around
`startDate.plus(amount, unit)`. A follow-up review verified that `LocalDate.plusDays`/`plusWeeks`
throw `ArithmeticException` (via `Math.addExact`), not `DateTimeException`, for an `amount` near
`Long.MAX_VALUE` — `plusMonths` happens to still surface `DateTimeException` for the same input
range, so the gap was unit-dependent and easy to miss. An uncaught `ArithmeticException` would have
failed the entire validation request (HTTP 500 via the global exception handler) for one malformed
period value, rather than degrading to skip just that duration check. Fixed by broadening the catch
to `DateTimeException | ArithmeticException`, with a regression test using a period value at
`Long.MAX_VALUE` exactly.
