# Implementation Plan: No Conviction Warning (DR-CONV-006)

**Branch**: `DD-43039-no-conviction-warning` | **Date**: 2026-07-31 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/003-no-conviction-warning/spec.md`

## Summary

Implement validation rule `DR-CONV-006` that warns when an offence has a final result (`category='F'`) that is not one of a configured set of non-substantive disposal short codes, and the offence is not convicted. The warning is advisory (WARNING severity, non-blocking) and clears automatically once the offence's conviction indicator becomes true, regardless of whether that happened via a guilty plea (AC1A) or a guilty verdict following a not-guilty plea (AC1B) — this service only ever observes the single upstream `isConvicted` boolean, not the plea/verdict event that set it.

The rule follows the existing YAML+CEL rule engine pattern and is a near-direct recombination of two already-shipped rules: the "final, non-excluded result" gate is copied verbatim from `DR-DISQ-001` (same `excludedFinalShortCodes` values, same category-`F` semantics) with its offence-code allow-list (`relevantOffenceCodes`) dropped, and the "not convicted" check is copied verbatim from `DR-CTL-001` (`OffenceDto.isConvicted`). **Unlike both prior rules, this feature has no upstream API blocker** — `isConvicted` already exists on `OffenceDto` (added for `DR-CTL-001`) and `PreprocessingDefinition.excludedFinalShortCodes` already exists as a generic field (added for `DR-DISQ-001`), so no upstream JAR bump and no `PreprocessingDefinition` change are required.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Lombok, `api-cp-crime-hearing-results-validator` (external DTO JAR — current version already sufficient, no bump required)
**Storage**: PostgreSQL 15.3 (TestContainers for integration tests); `validation_rule` table for runtime severity overrides (framework-level, not re-tested per rule — see `.claude/rules/design_rules.md`)
**Testing**: JUnit 5 + Mockito + AssertJ (unit), TestContainers + MockMvc (integration)
**Target Platform**: Azure-hosted Spring Boot service, port 4550
**Project Type**: Web service (validation microservice)
**Performance Goals**: Consistent with existing rules; per-request evaluation is O(offences × conditions), no cross-rule coupling
**Constraints**: Gradle build must pass Checkstyle Google (`maxWarnings = 0`) and PMD with `ignoreFailures = false`
**Scale/Scope**: Single new rule + preprocessor + context record; no schema migrations; no upstream dependency changes

## Constitution Check

*GATE: Must pass before implementation. Re-checked after design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I — YAML/CEL Rule-First | PASS | New rule starts with `DR-CONV-006.yaml`; reuses the existing generic `excludedFinalShortCodes` field, no new preprocessing config schema needed |
| II — Constructor Injection & Immutable DTOs | PASS | Preprocessor uses constructor injection (none required — stateless `@Component`); `NoConvictionContext` is a Java record |
| III — Layered Architecture & Data-Driven Dispatch | PASS | New `@Component` preprocessor with unique qualifier `"no-conviction-check"`, registered via `PreprocessorRegistry` — the registry already exists and dispatches on `preprocessing.type` today (verified against current code; `.claude/rules/design_rules.md`'s "not yet on main" framing is stale and should be corrected separately) |
| IV — Spec-Driven Build Loop | PASS | Spec → Plan → Tasks → Implement → Analyse flow followed |
| V — HMCTS Standards | PASS | Java 25, Spring Boot 4, Gradle, SLF4J; no Maven |
| VI — Severity Ceiling, Never Promote | PASS | Rule severity is WARNING; DB ceiling can only lower, never raise |
| VII — No System.out | PASS | Only SLF4J logging permitted |
| VIII — TDD | PASS | Failing tests authored before production code (task ordering enforces this) |

No violations. Complexity Tracking table omitted (no exceptions required).

## Project Structure

### Documentation (this feature)

```text
specs/003-no-conviction-warning/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md         ← Phase 1 output
├── quickstart.md         ← Phase 1 output
├── checklists/
│   └── requirements.md
└── tasks.md              ← Phase 2 output (/speckit-tasks — not yet created)
```

### Source Code (repository root)

```text
src/main/resources/rules/
└── DR-CONV-006.yaml                                                  (NEW)

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── NoConvictionContext.java                                          (NEW)
└── NoConvictionPreprocessor.java                                     (NEW)

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
├── NoConvictionContextTest.java                                      (NEW — unit)
└── NoConvictionPreprocessorTest.java                                 (NEW — unit)

src/test/java/uk/gov/hmcts/cp/integration/
└── NoConvictionWarningIntegrationTest.java                           (NEW — integration)
```

No changes to `PreprocessingDefinition.java`, `PreprocessorRegistry.java`, `CelValidationRule.java`, or any upstream DTO — all of the shared framework pieces this rule needs already exist.

### Dependency map

```
DR-CONV-006.yaml
    └── preprocessing.type: "no-conviction-check"
            └── NoConvictionPreprocessor (@Component)
                    ├── reads: PreprocessingDefinition.excludedFinalShortCodes  (existing field, reused from DR-DISQ-001)
                    ├── reads: ResultLineDto.category == F                     (existing, reused from DR-DISQ-001)
                    ├── reads: OffenceDto.isConvicted                          (existing, reused from DR-CTL-001)
                    ├── uses:  PreprocessorHelper.upperSet/hasUpperCode/anyShortCodeIn
                    │          (fixes the minor duplication where DR-CTL-001 and DR-DISQ-001
                    │          each re-implemented private copies of these helpers instead of
                    │          calling the shared PreprocessorHelper — the new preprocessor
                    │          calls PreprocessorHelper directly)
                    └── yields: NoConvictionContext (unconvictedSentenceCount, warningOffenceIds)
                            └── CelExpressionEvaluator evaluates: "unconvictedSentenceCount > 0"
```

## Complexity Tracking

No violations requiring justification. Table omitted per plan template guidance.
