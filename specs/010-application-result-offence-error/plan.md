# Implementation Plan: Application Results Recorded Against Offences – ERROR Validation

**Branch**: `010-application-result-offence-error` | **Date**: 2026-09-16 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/010-application-result-offence-error/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Add a new validation rule, `DR-APP-009`, that raises a blocking `ERROR` for every result line
recorded against an offence whose `shortCode` belongs to a fixed, application-only set
(`ARBSFG`, `ARBSPG`, `ARBSR`, `VT`, `G`, `LAREP`, `LAREPCC`, `ORDC`, `LATG`, `LATR`, `LAWD`,
`RFSD`, `SMAR`). The rule follows the existing YAML+CEL, preprocessor-registry pattern
(Principle I / III): a new `ValidationPreprocessor` (`ApplicationResultOffencePreprocessor`,
qualifier `application-result-offence`) emits one context **per breaching result-line
occurrence** — not per offence or per defendant — so that two application-only results on the
same offence, or across several offences and defendants, each surface their own inline error
(AC2), while the page-level error groups by breaching result label via the existing
`ruleId::errorMessage` mechanism in `DefaultValidationService` (no new service-layer code).

Unlike `specs/007-imprisonment-age-restriction` (DD-42950), this feature needs **no upstream DTO
change**: `ResultLineDto` already carries `offenceId`, `shortCode`, and `label` — see
research.md R1. It does need two small, non-blocking extensions to the rule engine itself:

1. (research.md R5) The page-level `errorMessageTemplate` currently has no mechanism to embed a
   per-context computed value (only the inline `messageTemplate` does, via `calculatedValueSet`
   and the hardcoded `${calculatedEndDate}` token). This rule's page-level message must name the
   breaching result's label (`${resultLabel}`), so the existing calculated-value mechanism is
   generalised to also resolve on `errorMessageTemplate`, and the hardcoded token name is
   parameterised.
2. (research.md R8 — identified during `/speckit.analyze`) This rule's per-breach-occurrence
   context design (needed for AC2's separate-inline-error-per-breach requirement) means the same
   defendant can be appended twice into the same page-level "This affects" name list — once per
   breach occurrence sharing the same breaching label — which `DefaultValidationService.
   appendDefendantName` does not currently guard against (unconditional `list.add(name)`, no
   dedup, no blank-name skip). This is fixed once, at that single call site, which is also what
   lets an unresolvable defendant name be omitted from the list (per spec.md's edge case) rather
   than rendered as an empty or placeholder entry.

Both are gaps in shared, rule-agnostic engine code, not new business logic, and are fixed in
this same change per Constitution Principle I.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL evaluator), Lombok,
`libs.api.hearing.results.validator` (external DTO jar, pinned `26.25` — **no version bump
required**; `ResultLineDto.offenceId`/`shortCode`/`label` and `DefendantDto` already carry
everything this rule needs)
**Storage**: PostgreSQL 15.3 — no new tables. One new Flyway migration
(`V1.010__insert_dr_app_009.sql`) inserts the rule's row into the existing `validation_rule`
table, exactly as every prior rule's migration (`V1.002`–`V1.009`) has done; this is required for
`RuleOverrideService`/the severity-ceiling mechanism to resolve `DR-APP-009` at all.
**Testing**: JUnit 5 + Mockito + AssertJ (unit, `gradle test`), MockMvc + TestContainers
(integration, extends `IntegrationTestBase`), live API tests (`gradle api`)
**Target Platform**: Linux container on Kubernetes (HMCTS CPP estate), local port 4550
**Project Type**: Single Spring Boot web service (existing project; no new services/repos)
**Performance Goals**: No new dedicated target — must not regress the existing `/validate`
endpoint's latency; the new preprocessor is a single linear pass over `resultLines`, no different
in cost from existing short-code-filtering preprocessors (`DisqualificationExtendedTestPreprocessor`)
**Constraints**: Severity ceiling never promotes (Principle VI); SLF4J-only logging (Principle
VII); no wildcard imports; TDD red-green-refactor (Principle VIII); CEL expressions limited to a
trivial boolean, all branching (short-code membership, label resolution) lives in the
preprocessor
**Scale/Scope**: One new rule YAML, one new DB migration, one new preprocessor class, one new
context record, one small generalisation of `CelValidationRule`'s calculated-value placeholder
resolution (extended to also apply to `errorMessageTemplate`, and parameterised so the token name
is no longer hardcoded to `calculatedEndDate`), one small fix to `DefaultValidationService.
appendDefendantName` (dedup + blank-name guard, research.md R8), unit + integration tests. No
changes to `ValidationRuleAutoConfiguration`, `PreprocessorRegistry`, or the overall shape of
`DefaultValidationService`'s aggregation loop beyond that one guarded helper method.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. YAML/CEL Rule-First | **PASS** | New rule lives entirely in `DR-APP-009.yaml`. No DTO change needed (research.md R1). The two Java-side changes (generalising calculated-value placeholder resolution, R5; dedup/blank-name guard in `appendDefendantName`, R8) are the kind of context/message-model gaps Principle I explicitly requires to be fixed in the same change when an existing mechanism doesn't yet stretch to a new rule's needs — neither is new business logic, and neither weakens the "no Java for a new rule that fits an existing preprocessor" guarantee for future rules. |
| II. Constructor Injection & Immutable DTOs | **PASS** | `ApplicationResultBreachContext` is a Java record; `ApplicationResultOffencePreprocessor` needs no injected collaborators (matches the zero-arg-constructor shape of `DisqualificationExtendedTestPreprocessor`). |
| III. Layered Architecture & Data-Driven Preprocessor Dispatch | **PASS** | Dispatches via the existing `PreprocessorRegistry` by `preprocessing.type`; no change to the registry itself. This is the ninth preprocessor. |
| IV. Spec-Driven Build Loop | **PASS (procedural)** | Implementation MUST go through code-reviewer → qa → spec-validator before merge, per workflow.md. |
| V. HMCTS Standards Compliance | **PASS** | Gradle, Java 25, `uk.gov.hmcts.cp` package, SLF4J — no deviation. |
| VI. Severity Ceiling, Never Promote | **PASS** | Rule authored at its maximum severity, `ERROR`; DB ceiling can only cap it downward to `WARNING`, never promote. |
| VII. No `System.out`/`System.err` | **PASS** | No console I/O introduced. |
| VIII. Test-Driven Development | **PASS (procedural)** | Tasks phase MUST order failing tests before production code for the new preprocessor, context, rule, the `CelValidationRule` generalisation, and the `DefaultValidationService.appendDefendantName` fix. |

No violations. Complexity Tracking table is not required.

## Project Structure

### Documentation (this feature)

```text
specs/010-application-result-offence-error/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── DR-APP-009.yaml           # Draft of the new rule file
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/resources/rules/
└── DR-APP-009.yaml                                      # NEW — rule definition

src/main/resources/db/migration/
└── V1.010__insert_dr_app_009.sql                         # NEW — seeds validation_rule row

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── ApplicationResultOffencePreprocessor.java             # NEW — ValidationPreprocessor impl
├── ApplicationResultBreachContext.java                   # NEW — RuleEvaluationContext record
├── ConditionDefinition.java                              # MODIFIED — see research.md R5
└── CelValidationRule.java                                # MODIFIED — see research.md R5
                                                            # (generalise calculated-value
                                                            # placeholder resolution to also
                                                            # apply to errorMessageTemplate, and
                                                            # to a configurable token name)

src/main/java/uk/gov/hmcts/cp/services/impl/
└── DefaultValidationService.java                         # MODIFIED — see research.md R8
                                                            # (dedup + blank-name guard in
                                                            # appendDefendantName only)

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
├── ApplicationResultOffencePreprocessorTest.java         # NEW — unit tests
├── ApplicationResultBreachContextTest.java                # NEW — unit tests (toCelContext, sets)
└── CelValidationRuleTest.java                             # MODIFIED — cover the generalised
                                                            # placeholder resolution (existing
                                                            # ${calculatedEndDate} behaviour MUST
                                                            # remain green, unchanged)

src/test/java/uk/gov/hmcts/cp/services/impl/
└── DefaultValidationServiceTest.java                     # MODIFIED — cover the generalised
                                                            # appendDefendantName dedup/blank-name
                                                            # guard (existing aggregation
                                                            # behaviour MUST remain green,
                                                            # unchanged)

src/test/java/uk/gov/hmcts/cp/services/integration/
└── ApplicationResultOffenceRuleIT.java                   # NEW — rule-specific IT only
                                                            # (no override/severity-ceiling IT —
                                                            # that is proven once in
                                                            # ValidationRuleOverrideIntegrationTest,
                                                            # per design_rules.md)
```

**Structure Decision**: Existing single-service layout is unchanged. The new rule slots into the
established `rules/`, `services/rules/cel/` packages alongside the eight existing rules and
their preprocessors — no new modules, packages, or build targets. The two shared-engine changes
(`CelValidationRule` / `ConditionDefinition` per R5; `DefaultValidationService.
appendDefendantName` per R8) are both additive and backward compatible: every existing rule YAML
omits the new optional field and keeps its current `${calculatedEndDate}` behaviour unchanged,
and every existing rule's aggregation calls only ever hit the new guards as a no-op (see
research.md R5/R8 and data-model.md for the exact defaults).

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

No violations recorded — table intentionally omitted.
