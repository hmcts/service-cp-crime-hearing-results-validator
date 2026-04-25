# Implementation Plan: Extended Test Disqualification Warning (DD-41656)

**Branch**: `DD-41656-results-validation-warning` | **Date**: 2026-04-25 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-extended-test-disq-warning/spec.md`

## Summary

Add a new YAML+CEL validation rule that raises a non-blocking `WARNING` when a hearing contains an offence with one of five Road Traffic Act 1988 Home Office codes (`RT88046`, `RT88526`, `RT88026`, `RT88530`, `RT88531`) that has a final result not in the excluded list (`wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`) and no `DDOTE` / `DDOTEL` disqualification result line linked to that offence. The warning text is fixed and the rule is per-offence (one warning per qualifying offence).

The rule cannot reuse `CustodialPreprocessor` — its outputs (`noInfoCount`, `hasBothCount`, etc.) are concurrent/consecutive-specific and group by defendant, whereas this rule is per-offence and needs different counts and offence-id sets. A new `ValidationPreprocessor` is therefore required, which under **Constitution Principle III** means the hard-wired `CustodialPreprocessor` field in `CelValidationRule` and `ValidationRuleAutoConfiguration` MUST first be replaced with a registry-based dispatch driven by the YAML `preprocessing.type` field. The plan therefore has two ordered parts:

1. **Refactor**: introduce a `ValidationPreprocessor` interface, a sealed `RuleEvaluationContext` type (`toCelContext()` + `getOffenceIdSet(String)`), and a Spring-aware `PreprocessorRegistry` that resolves preprocessors by qualifier. Wire `CelValidationRule` and `ValidationRuleAutoConfiguration` through the registry. Move `CustodialPreprocessor` and `DefendantContext` behind the interface, qualifier `custodial-concurrent-consecutive`. No behaviour change; existing tests stay green.
2. **Add the new rule**: implement `DisqualificationExtendedTestPreprocessor` (qualifier `disqualification-extended-test`) producing a `DisqualificationContext` with counts and a `qualifyingOffenceIds` set, then add `src/main/resources/rules/DR-DISQ-001.yaml` with one CEL condition firing on `qualifyingCount > 0`. No change to the upstream API contract.

The trigger point in the UI (Save and continue / Manage hearing tab) is wiring outside this service.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Caffeine cache, Logback + LogstashEncoder, SnakeYAML, Lombok, Spring Data JPA + PostgreSQL driver
**External DTOs**: `libs.api.hearing.results.validator` (root package `uk.gov.hmcts.cp.openapi.model`) — `DraftValidationRequest`, `OffenceDto`, `ResultLineDto`, `DefendantDto`, `ValidationIssue`, `AffectedOffence`. Spec source: `/home/sachin/moj/api-cp-crime-hearing-results-validator/src/main/resources/openapi/openapi-spec.yml`. **No upstream change is required** for this feature — `OffenceDto.offenceCode`, `ResultLineDto.shortCode`, and `ResultLineDto.offenceId` are already present.
**Storage**: PostgreSQL 15.3 (TestContainers in tests, real instance in non-prod). Only the existing `validation_rule` table is touched — a new row keyed by the rule's id (e.g. `DR-DISQ-001`) controls runtime enable/severity.
**Testing**: JUnit 5 + Mockito + AssertJ for unit tests; MockMvc for controller tests; WireMock for external-service stubs; TestContainers for `*IT` integration tests (extend `IntegrationTestBase`); `gradle api` for live API tests via docker-compose; Gatling for performance.
**Target Platform**: Linux server, AKS (Azure Kubernetes Service), behind the CPP STE gateway. Default port 4550.
**Project Type**: Single-module Spring Boot web service.
**Performance Goals**: Validation latency must remain indistinguishable to a user from the existing single-rule pipeline (rule evaluation is in-memory, CEL expressions are cached per (expression, context-keys) tuple).
**Constraints**:

- Spring Boot 4 / Java 25 baseline (Constitution V).
- Constructor injection only; `@Autowired` fields forbidden (Constitution II).
- All in-process value types are Java records (Constitution II).
- SLF4J only — no `System.out` / `System.err` / `printStackTrace` (Constitution VII).
- Severity ceiling caps downward; never promotes (Constitution VI).
- `gradle build` must pass with Checkstyle Google `maxWarnings=0`, PMD `ignoreFailures=false`, all unit + integration tests green (Constitution V + Workflow).
- TDD is mandatory: failing test first, then production code (Constitution VIII).
- No wildcard imports.
- Base scan package is `uk.gov.hmcts.cp` — any new HMCTS library imports must be checked for stray `@Component` beans and excluded if necessary.

**Scale/Scope**: Single new rule + one prerequisite refactor. The refactor touches `CelValidationRule`, `ValidationRuleAutoConfiguration`, `CustodialPreprocessor`, `DefendantContext` (about 5 files). The new rule adds approx. 4 files: 1 YAML, 1 preprocessor, 1 context record, 1 entry in the sealed type. Test scope: ≥6 new unit tests for the preprocessor, ≥1 integration `*IT` test for the rule end-to-end through the controller, plus regression tests for the registry-based dispatch.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Compliance | Notes |
|-----------|------------|-------|
| **I. YAML/CEL Rule-First** | PASS | The new rule lives in `src/main/resources/rules/DR-DISQ-001.yaml`. CEL condition is a single boolean expression. Severity, message text, and the relevant offence codes / excluded codes / disqualification codes are all in the YAML — BAs can amend without touching Java. The new preprocessor publishes a documented set of CEL variables that the YAML references by name. |
| **II. Constructor Injection & Immutable DTOs** | PASS | `DisqualificationExtendedTestPreprocessor` uses constructor injection (no fields needed beyond Spring's stereotype). `DisqualificationContext` is a Java record with `toCelContext()` and `getOffenceIdSet()`. `RuleEvaluationContext` is a sealed interface (Constitution II's "sealed interfaces for polymorphic types"). |
| **III. Layered Architecture & Data-Driven Preprocessor Dispatch** | PASS *(after the prerequisite refactor)* | The current `CelValidationRule` hard-wires `CustodialPreprocessor` — Principle III explicitly calls this out as a transitional state that "MUST be removed before any second preprocessor type ships". This plan removes that wiring **first**, then adds the new preprocessor. After the refactor, dispatch is by YAML `preprocessing.type` via a Spring registry, which is exactly what Principle III mandates. |
| **IV. Spec-Driven Build Loop** | PASS | This plan is the `/speckit-plan` output following `/speckit-specify`. `/speckit-tasks` will produce `tasks.md` next; `/speckit-implement` runs the build loop with `code-reviewer`, `qa`, `spec-validator` agents. |
| **V. HMCTS Standards Compliance** | PASS | Gradle (no Maven). Spring Boot 4 / Java 25. Root package `uk.gov.hmcts.cp`. SLF4J + Logback. No new external dependencies. The `validation_rule` table row is added by the existing migration mechanism (Liquibase) — not a new migration, just data. |
| **VI. Severity Ceiling, Never Promote** | PASS | The single condition has `severity: WARNING` in the YAML — already at the lower end of the scale. The DB ceiling never promotes WARNING to ERROR; the existing `SeverityCeiling.resolve()` is reused unchanged. |
| **VII. No `System.out` / `System.err` — SLF4J Only** | PASS | New code uses `@Slf4j` (Lombok). Tests use SLF4J too. No print statements. |
| **VIII. Test-Driven Development** | PASS for US1, WAIVED for US2/US3 — see Complexity Tracking | US1's failing tests precede the US1 production code (T021). US2 and US3 ship test coverage *over* the gates already present in T021, so their "failing tests" pass on first run. This is an explicit, narrow waiver — see the Complexity Tracking row below for the engineering rationale. |

**Gate result**: PASS. The Principle III refactor is **part of this plan**, not a deviation requiring a Complexity Tracking entry — Principle III itself states the refactor must happen at the moment a second preprocessor is introduced. Treating it as a complexity violation would be wrong; treating it as a prerequisite phase is right.

## Project Structure

### Documentation (this feature)

```text
specs/001-extended-test-disq-warning/
├── plan.md              # This file
├── spec.md              # Feature spec (already exists)
├── research.md          # Phase 0 output (this command)
├── data-model.md        # Phase 1 output (this command)
├── quickstart.md        # Phase 1 output (this command)
├── contracts/
│   └── rule-DR-DISQ-001.md   # Rule contract (YAML schema + CEL variables)
├── checklists/
│   └── requirements.md  # From /speckit-specify (already exists)
└── tasks.md             # From /speckit-tasks (NOT created by this command)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/uk/gov/hmcts/cp/
│   │   ├── config/
│   │   │   └── ValidationRuleAutoConfiguration.java          # MODIFIED: inject PreprocessorRegistry instead of CustodialPreprocessor
│   │   └── services/rules/
│   │       └── cel/
│   │           ├── ValidationPreprocessor.java               # NEW: interface (preprocess returns Map<String, RuleEvaluationContext>)
│   │           ├── PreprocessorRegistry.java                 # NEW: resolves preprocessor by qualifier from Spring context
│   │           ├── RuleEvaluationContext.java                # NEW: sealed interface (toCelContext + getOffenceIdSet)
│   │           ├── CelValidationRule.java                    # MODIFIED: dispatch via registry, accept polymorphic context
│   │           ├── CustodialPreprocessor.java                # MODIFIED: implements ValidationPreprocessor, qualifier "custodial-concurrent-consecutive"
│   │           ├── DefendantContext.java                     # MODIFIED: implements RuleEvaluationContext (existing record permitted via "permits" clause)
│   │           ├── DisqualificationExtendedTestPreprocessor.java  # NEW: qualifier "disqualification-extended-test"
│   │           └── DisqualificationContext.java              # NEW: record implementing RuleEvaluationContext
│   └── resources/rules/
│       ├── DR-SENT-002.yaml                                  # UNCHANGED
│       └── DR-DISQ-001.yaml                                  # NEW: the rule
└── test/
    └── java/uk/gov/hmcts/cp/services/rules/cel/
        ├── PreprocessorRegistryTest.java                     # NEW
        ├── DisqualificationExtendedTestPreprocessorTest.java # NEW (>=6 scenarios)
        ├── CelValidationRuleTest.java                        # MODIFIED: registry-based fixtures
        └── ../integration/
            └── DisqualificationExtendedTestRuleIT.java       # NEW: end-to-end through MockMvc
```

**Structure Decision**: This is a single-module Spring Boot service. All Java production code lives under `src/main/java/uk/gov/hmcts/cp/`. YAML rule files live under `src/main/resources/rules/`. Tests live under `src/test/java/uk/gov/hmcts/cp/` mirroring the production package layout, with integration tests in a `.integration.` sub-package. No new top-level modules or directories are introduced.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| **Principle VIII (TDD), narrow waiver for US2/US3** — the four-gate logic (relevance, final-result presence, excluded codes, DDOTE/DDOTEL absence) ships in a single preprocessor in T021 (US1). US2 (T024–T028) and US3 (T030–T033) add *test coverage* of the excluded-codes and DDOTE gates respectively; those tests pass on first run rather than failing red, because the gates already exist in production. | Shipping the gates incrementally would mean shipping a knowingly noisy MVP — US1 alone would warn on every relevant offence regardless of result code, until US2 lands suppression for excluded codes (wdrn, dism, etc.), then US3 lands DDOTE/DDOTEL suppression. That's a worse user experience than holding the MVP back until all four gates are live. | (b) Splitting T021 across user-story phases so each gate adds production code in its own phase would restore strict TDD ordering for US2/US3 but ship a knowingly-noisy MVP for hours/days between phases. The product cost outweighs the process purity. (a) Picked. |

The Principle III refactor of preprocessor dispatch is mandated by the principle itself, not a deviation, so it is not a complexity violation and is not listed here.
