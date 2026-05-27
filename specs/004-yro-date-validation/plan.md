# Implementation Plan: YRO Date Validation

**Branch**: `DD-41654-yro-date-validation` | **Date**: 2026-05-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/004-yro-date-validation/spec.md`

## Summary

Adds `DR-YRO-001.yaml` — a Youth Rehabilitation Order end-date validation rule covering AC2 (YRO end date must not precede any linked curfew requirement end date) and AC3 (YRO containing an unpaid work requirement must span at least 12 calendar months from the hearing date). The rule reuses the existing `community-order-end-date` preprocessor (`CommunityOrderEndDatePreprocessor`) via YAML configuration; **no new Java production code is required**.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Caffeine (rule override cache), TestContainers PostgreSQL 15.3 (integration tests)
**Storage**: `validation_rule` table (PostgreSQL) for runtime severity overrides — no migration needed
**Testing**: `gradle test` (JUnit 5 + Mockito + AssertJ + TestContainers), `gradle api` (live docker-compose stack)
**Target Platform**: Azure-hosted Spring Boot service (local port 4550)
**Project Type**: Web service (validation API)
**Performance Goals**: Standard Spring Boot throughput; no rule-specific performance target
**Constraints**: Constitution Principle I — new rule MUST NOT require Java changes; existing preprocessor type covers YRO codes
**Scale/Scope**: One new YAML rule file; one new integration test class

## Constitution Check

| Principle | Status | Notes |
|---|---|---|
| I — YAML/CEL Rule-First | ✅ PASS | Pure YAML addition; preprocessor reused via `preprocessing.type: "community-order-end-date"` |
| II — Constructor Injection & Immutable DTOs | ✅ PASS | No new Java classes; existing code is already compliant |
| III — Layered Architecture & Preprocessor Dispatch | ✅ PASS | YAML `preprocessing.type` dispatches to registered bean via `PreprocessorRegistry` |
| IV — Spec-Driven Build Loop | ✅ PASS | Spec → Plan → Tasks → Implement → code-reviewer → qa → spec-validator loop applies |
| V — HMCTS Standards Compliance | ✅ PASS | No new technology; same Gradle/Spring Boot/Java 25 stack |
| VI — Severity Ceiling, Never Promote | ✅ PASS | All conditions declared as ERROR; DB override can only cap downward |
| VII — No System.out — SLF4J Only | ✅ PASS | No new production Java; existing preprocessor uses `@Slf4j` |
| VIII — TDD | ✅ APPLICABLE | Integration test must be written as a failing test before YAML is created |

## Project Structure

### Documentation (this feature)

```text
specs/004-yro-date-validation/
├── plan.md              # This file
├── research.md          # Phase 0 — preprocessor reuse decision, prompt-ref mapping
├── data-model.md        # Phase 1 — entities, violation logic, CEL variable mapping
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
src/main/resources/rules/
└── DR-YRO-001.yaml                                # NEW — YRO date validation rule

src/test/java/uk/gov/hmcts/cp/integration/
└── YroDateValidationRuleIntegrationTest.java      # NEW — TDD integration tests
```

**No new Java production source files.** Existing code paths involved (no changes):

| Class | Role |
|---|---|
| `CommunityOrderEndDatePreprocessor` | Processes YRO + YRC1/YRC2/YRC3/YRUP1 lines into `CommunityOrderContext`; reused unchanged |
| `CommunityOrderContext` | Carries CEL violation counts and offence-id sets; reused unchanged |
| `PreprocessorRegistry` | Dispatches on `preprocessing.type = "community-order-end-date"` |
| `CelValidationRule` | Evaluates CEL conditions against context; auto-discovers YAML |
| `ValidationRuleAutoConfiguration` | Discovers `DR-YRO-001.yaml` at startup via `classpath*:rules/DR-*.yaml` |

## Rule Design: DR-YRO-001

### Preprocessing configuration

```yaml
preprocessing:
  type: "community-order-end-date"
  communityOrderShortCodes: [YROEW, YRONI, YROFEW, YROISS, YROINI]
  curfewShortCodes:          [YRC2]
  curfewTagShortCodes:       [YRC1]
  furtherCurfewShortCodes:   [YRC3]
  alcoholAbstinenceShortCodes: []          # not applicable to YRO
  unpaidWorkShortCodes:      [YRUP1]
```

### Conditions

| Condition ID | CEL expression | Severity | Trigger |
|---|---|---|---|
| AC2a | `curViolationCount > 0` | ERROR | YRC2 end date after YRO end date |
| AC2b | `cureViolationCount > 0` | ERROR | YRC1 end-of-tag after YRO end date |
| AC2c | `curaViolationCount > 0` | ERROR | YRC3 end date after YRO end date |
| AC3 | `upwrViolationCount > 0` | ERROR | YRUP1 present; YRO end date < hearingDay + 12m − 1d |

### Message templates

| Condition | `messageTemplate` (inline per-offence) | `errorMessageTemplate` (summary banner) |
|---|---|---|
| AC2a | "The end date of the order must match or be longer than the end date of Youth Rehabilitation Requirement: Curfew" | Same + ". This affects ${defendantNames}." |
| AC2b | "The end date of the order must match or be longer than the end date of Youth Rehabilitation Requirement: Curfew with electronic monitoring" | Same + ". This affects ${defendantNames}." |
| AC2c | "The end date of the order must match or be longer than the end date of Youth Rehabilitation Requirement: Further curfew requirement made" | Same + ". This affects ${defendantNames}." |
| AC3 | "The end date of the order must be at least 12 months as it includes an unpaid work requirement" | Same + ". This affects ${defendantNames}." |

### Rule priority

`5000` — evaluated after DR-COEW-001 (priority 4000); leaves room for future YRO rules.

## Test Plan (for `YroDateValidationRuleIntegrationTest.java`)

All tests extend `IntegrationTestBase`. Tests must be written as **failing tests first** (TDD, Principle VIII) before the YAML is created.

### AC2 scenarios

| Test ID | Scenario | Expected |
|---|---|---|
| T001 | YRC2 end date after YRO end date (single defendant, single offence) | AC2a ERROR on the offence |
| T002 | YRC1 end-of-tag after YRO end date | AC2b ERROR |
| T003 | YRC3 end date after YRO end date | AC2c ERROR |
| T004 | All three curfew requirements breach simultaneously | AC2a + AC2b + AC2c each fire independently |
| T005 | YRC2 end date equals YRO end date | No error (boundary: equal is valid) |
| T006 | YRC2 end date before YRO end date | No error |
| T007 | Two defendants; only one has curfew breach | Only affected defendant in error; other unaffected |
| T008 | YRO with no curfew child requirements | No AC2 error |

### AC3 scenarios

| Test ID | Scenario | Expected |
|---|---|---|
| T009 | YRUP1 present; YRO end date < hearingDay + 12m − 1d | AC3 ERROR |
| T010 | YRUP1 present; YRO end date = hearingDay + 12m − 1d | No error (boundary: equal is valid) |
| T011 | YRUP1 present; YRO end date > hearingDay + 12m − 1d | No error |
| T012 | YRO without YRUP1 and short end date | No AC3 error |
| T013 | Two defendants; only one has YRUP1 with short duration | Only affected defendant in error |

### Combined and response-structure scenarios

| Test ID | Scenario | Expected |
|---|---|---|
| T014 | Same defendant has both AC2a breach and AC3 breach | Both conditions fire; independent errors |
| T015 | Error summary includes defendant name (${defendantNames} resolved) | `errorMessage` contains defendant full name |
| T016 | Inline message scoped to violating offence only | `affectedOffences` in issue contains only the breaching offenceId |
| T017 | Valid YRO with all curfew requirements within order end date and YRUP1 within 12m | `isValid: true`, no errors |

## Complexity Tracking

No constitution violations. No complexity tracking entry required.
