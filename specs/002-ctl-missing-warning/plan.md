# Implementation Plan: CTL Missing Warning (DR-CTL-001)

**Branch**: `DD-41663-ctl-missing-warning` | **Date**: 2026-05-06 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/002-ctl-missing-warning/spec.md`

## Summary

Implement validation rule DR-CTL-001 that warns when a remand-type result (`RI`, `RIYDA`, `RIH`, `RIB`, `RILA`, `RILAB`, `REMYD`) is entered against an offence that has no existing CTL record, no CTL result in the current hearing, and is not convicted. The warning is advisory (WARNING severity, non-blocking). The rule follows the existing YAML+CEL rule engine pattern: a new `CtlMissingPreprocessor` computes a per-offence `ctlWarningCount` flag; the YAML rule `DR-CTL-001.yaml` evaluates `ctlWarningCount > 0`. **This rule is blocked on upstream API changes** — `OffenceDto` must gain `hasExistingCtlRecord` and `isConvicted` boolean fields before Java implementation can compile.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Lombok, `api-cp-crime-hearing-results-validator` (external DTO JAR — version bump required)
**Storage**: PostgreSQL 15.3 (TestContainers for integration tests); `validation_rule` table for runtime severity overrides
**Testing**: JUnit 5 + Mockito + AssertJ (unit), TestContainers + MockMvc (integration), RestTemplate (API tests)
**Target Platform**: Azure-hosted Spring Boot service, port 4550
**Project Type**: Web service (validation microservice)
**Performance Goals**: Consistent with existing rules; per-request evaluation is O(offences × conditions), no cross-rule coupling
**Constraints**: Gradle build must pass Checkstyle Google (`maxWarnings = 0`) and PMD with `ignoreFailures = false`
**Scale/Scope**: Single new rule + preprocessor; no schema migrations; one upstream JAR dependency bump

## Constitution Check

*GATE: Must pass before implementation. Re-checked after design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I — YAML/CEL Rule-First | PASS | New rule starts with `DR-CTL-001.yaml`; new preprocessor required (gap in existing preprocessors), added in same change per Principle I |
| II — Constructor Injection & Immutable DTOs | PASS | Preprocessor uses constructor injection; `CtlOffenceContext` is a Java record |
| III — Layered Architecture & Data-Driven Dispatch | PASS | New `@Component` preprocessor with unique qualifier `"ctl-missing"`, registered via `PreprocessorRegistry`; no hard-wiring into `CelValidationRule` |
| IV — Spec-Driven Build Loop | PASS | Spec → Plan → Tasks → Implement → Analyse flow followed |
| V — HMCTS Standards | PASS | Java 25, Spring Boot 4, Gradle, SLF4J; no Maven |
| VI — Severity Ceiling, Never Promote | PASS | Rule severity is WARNING; DB ceiling can only lower, never raise |
| VII — No System.out | PASS | Only SLF4J logging permitted |
| VIII — TDD | PASS | Failing tests authored before production code (task ordering enforces this) |

No violations. Complexity Tracking table omitted (no exceptions required).

## Project Structure

### Documentation (this feature)

```text
specs/002-ctl-missing-warning/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit-tasks — not yet created)
```

### Source Code (repository root)

```text
src/main/resources/rules/
└── DR-CTL-001.yaml                                                   (NEW)

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── CtlOffenceContext.java                                            (NEW)
├── CtlMissingPreprocessor.java                                       (NEW)
└── PreprocessingDefinition.java                                      (MODIFIED — 2 new fields)

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
├── CtlOffenceContextTest.java                                        (NEW — unit)
├── CtlMissingPreprocessorTest.java                                   (NEW — unit)
└── integration/
    └── CtlMissingRuleIT.java                                         (NEW — integration)

Upstream (separate repository — api-cp-crime-hearing-results-validator):
    OffenceDto.java                                                    (MODIFIED — 2 new Boolean fields)
```

### Dependency map

```
DR-CTL-001.yaml
    └── preprocessing.type: "ctl-missing"
            └── CtlMissingPreprocessor (@Component)
                    ├── reads: PreprocessingDefinition.remandShortCodes
                    ├── reads: PreprocessingDefinition.ctlShortCodes
                    ├── reads: OffenceDto.hasExistingCtlRecord  ← BLOCKED on upstream JAR
                    ├── reads: OffenceDto.isConvicted           ← BLOCKED on upstream JAR
                    └── yields: CtlOffenceContext (ctlWarningCount, warningOffenceIds)
                            └── CelExpressionEvaluator evaluates: "ctlWarningCount > 0"
```

## Complexity Tracking

No violations requiring justification. Table omitted per plan template guidance.
