# Implementation Plan: Imprisonment Result Age Restriction (DD-42950)

**Branch**: `DD-42950-imprisonment-age-restriction` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/DD-42950-imprisonment-age-restriction/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Add a new validation rule, `DR-AGE-001`, that raises a blocking `ERROR` when a defendant under
21 years of age (as of the hearing date) has an imprisonment-type result (`IMP`, `EXTIVS`,
`SPECC`) recorded against one or more of their offences. The rule follows the existing
YAML+CEL, preprocessor-registry pattern (Principle I / III): a new `ValidationPreprocessor`
(`AgeRestrictedImprisonmentPreprocessor`, qualifier `age-restricted-imprisonment`) groups result
lines per defendant (reusing the master-defendant grouping already proven in
`CustodialPreprocessor`), and a new context record (`AgeRestrictedResultContext`) exposes a
single boolean CEL variable, `isUnder21`, so the CEL condition stays a trivial comparison
(`isUnder21 == 1`) per the "no branching in CEL" convention.

**Blocking external dependency (must be resolved before this rule can activate — see Technical
Context and research.md R1)**: the defendant's date of birth is not present anywhere in the
current validation contract. `DefendantDto` (from the external
`api-cp-crime-hearing-results-validator` dependency, currently pinned at `0.2.4` in
`gradle/libs.versions.toml`) exposes only `defendantId`, `firstName`, `lastName`,
`masterDefendantId`. Per Constitution Principle I, this repository does not own that DTO and
must not add the field here. This plan proceeds on the same precedent set by
`specs/001-extended-test-disq-warning` (which added `category` to `ResultLineDto` upstream
before consuming it): the code in this repository can be written and merged now, expecting a
`dateOfBirth` field once the upstream contract is bumped, but the rule will remain permanently
dormant (fail-safe, per FR-011) until that version bump lands.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL evaluator), Lombok,
`libs.api.hearing.results.validator` (external DTO jar — **requires a version bump beyond the
current `0.2.4` pin to expose `DefendantDto.dateOfBirth`; tracked as an upstream prerequisite,
not implemented in this repo**)
**Storage**: PostgreSQL 15.3 (existing `validation_rule` table only — the new rule is registered
there for the runtime severity-ceiling mechanism; no new tables or migrations)
**Testing**: JUnit 5 + Mockito + AssertJ (unit, `gradle test`), MockMvc + TestContainers
(integration, extends `IntegrationTestBase`), live API tests (`gradle api`)
**Target Platform**: Linux container on Kubernetes (HMCTS CPP estate), local port 4550
**Project Type**: Single Spring Boot web service (existing project; no new services/repos)
**Performance Goals**: No new dedicated target — must not regress the existing `/validate`
endpoint's latency, since the new preprocessor runs once per request alongside existing rules
**Constraints**: Severity ceiling never promotes (Principle VI); SLF4J-only logging (Principle
VII); no wildcard imports; TDD red-green-refactor (Principle VIII); CEL expressions limited to
simple comparisons, branching lives in the preprocessor (existing "Out-of-Scope" convention)
**Scale/Scope**: One new rule YAML, one new preprocessor class, one new context record, unit +
integration tests. No changes to `ValidationRuleAutoConfiguration`, `PreprocessorRegistry`, or
`DefaultValidationService` — all are already data-driven/rule-agnostic (confirmed by direct code
inspection; the "hardwired to a single preprocessor" limitation described in
`.claude/rules/design_rules.md` is **stale** — the registry refactor already shipped, evidenced by
`CtlMissingPreprocessor` and `DisqualificationExtendedTestPreprocessor` already dispatching
through it. Worth a follow-up doc fix, out of scope here.)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. YAML/CEL Rule-First | **PASS** | New rule lives entirely in `DR-AGE-001.yaml`; DTO change (`dateOfBirth`) explicitly deferred to the upstream repo, not made here. |
| II. Constructor Injection & Immutable DTOs | **PASS** | `AgeRestrictedResultContext` is a Java record; `AgeRestrictedImprisonmentPreprocessor` needs no injected collaborators (matches `CustodialPreprocessor`'s zero-arg constructor shape). |
| III. Layered Architecture & Data-Driven Preprocessor Dispatch | **PASS** | Dispatches via the existing `PreprocessorRegistry` by `preprocessing.type`; no change to `CelValidationRule` or the registry. This is the 4th preprocessor — confirms the registry is genuinely data-driven, not hardwired. |
| IV. Spec-Driven Build Loop | **PASS (procedural)** | Implementation MUST go through code-reviewer → qa → spec-validator before merge, per workflow.md. |
| V. HMCTS Standards Compliance | **PASS** | Gradle, Java 25, `uk.gov.hmcts.cp` package, SLF4J — no deviation. |
| VI. Severity Ceiling, Never Promote | **PASS** | Rule authored at its maximum severity, `ERROR`; DB ceiling can only cap it downward to `WARNING`, never promote. |
| VII. No `System.out`/`System.err` | **PASS** | No console I/O introduced. |
| VIII. Test-Driven Development | **PASS (procedural)** | Tasks phase MUST order failing tests before production code for the new preprocessor and rule. |

No violations. Complexity Tracking table is not required.

## Project Structure

### Documentation (this feature)

```text
specs/DD-42950-imprisonment-age-restriction/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   ├── DR-AGE-001.yaml           # Draft of the new rule file
│   └── upstream-dependency.md    # Required DefendantDto field addition (external repo)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/resources/rules/
└── DR-AGE-001.yaml                                    # NEW — rule definition

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── AgeRestrictedImprisonmentPreprocessor.java          # NEW — ValidationPreprocessor impl
└── AgeRestrictedResultContext.java                     # NEW — RuleEvaluationContext record

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
├── AgeRestrictedImprisonmentPreprocessorTest.java      # NEW — unit tests
└── AgeRestrictedResultContextTest.java                 # NEW — unit tests (toCelContext, sets)

src/test/java/uk/gov/hmcts/cp/services/integration/
└── AgeRestrictedImprisonmentRuleIT.java                # NEW — rule-specific IT only
                                                         # (no override/severity-ceiling IT —
                                                         # that is proven once in
                                                         # ValidationRuleOverrideIntegrationTest,
                                                         # per design_rules.md)
```

**Structure Decision**: Existing single-service layout is unchanged. The new rule slots into the
established `rules/`, `services/rules/cel/` packages alongside `DR-SENT-002`, `DR-CTL-001`,
`DR-DISQ-001` and their preprocessors — no new modules, packages, or build targets.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

No violations recorded — table intentionally omitted.
