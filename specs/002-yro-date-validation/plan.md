# Implementation Plan: YRO Date Validation

**Branch**: `DD-41654-yro-date-validation` | **Date**: 2026-05-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/004-yro-date-validation/spec.md`

## Summary

Adds `DR-YRO-001.yaml` — a Youth Rehabilitation Order end-date validation rule covering AC2 (YRO end date must not precede any linked curfew requirement end date).

> **Design note (supersedes the original plan).** The first revision proposed reusing the
> `community-order-end-date` preprocessor with no new Java. That was reversed in favour of a dedicated
> `youth-rehabilitation-order` preprocessor (`YouthRehabilitationPreprocessor`) and context
> (`YouthRehabilitationContext`) for clean separation of YRO-specific logic. To avoid duplication with
> `CommunityOrderEndDatePreprocessor`, the shared, stateless helpers (short-code normalisation/matching,
> result-line grouping, defendant-name assembly, prompt-date parsing, requirement-date comparison) were
> extracted into `PreprocessorHelper` and are now used by all preprocessors. So this feature **does add
> Java production code**; Principle III (data-driven dispatch via `PreprocessorRegistry`) and Principle
> VII (SLF4J-only) still hold.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Caffeine (rule override cache), TestContainers PostgreSQL 15.3 (integration tests)
**Storage**: `validation_rule` table (PostgreSQL) for runtime severity overrides — no migration needed
**Testing**: `gradle test` (JUnit 5 + Mockito + AssertJ + TestContainers), `gradle api` (live docker-compose stack)
**Target Platform**: Azure-hosted Spring Boot service (local port 4550)
**Project Type**: Web service (validation API)
**Performance Goals**: Standard Spring Boot throughput; no rule-specific performance target
**Constraints**: Common logic is shared via `PreprocessorHelper`
**Scale/Scope**: One new YAML rule file; one new preprocessor + context; one shared helper; unit + integration tests

## Constitution Check

| Principle | Status | Notes |
|---|---|---|
| I — YAML/CEL Rule-First | ⚠️ PARTIAL | Not a pure-YAML addition: a dedicated `youth-rehabilitation-order` preprocessor + `YouthRehabilitationContext` were added. New rule *conditions* remain YAML/CEL-driven |
| II — Constructor Injection & Immutable DTOs | ✅ PASS | New context is a record; `PreprocessorHelper` is a stateless static utility (mirrors `SeverityCeiling`); no field injection |
| III — Layered Architecture & Preprocessor Dispatch | ✅ PASS | New preprocessor registers via `preprocessing.type: "youth-rehabilitation-order"` and dispatches through `PreprocessorRegistry` |
| IV — Spec-Driven Build Loop | ✅ PASS | Spec → Plan → Tasks → Implement → code-reviewer → qa → spec-validator loop applies |
| V — HMCTS Standards Compliance | ✅ PASS | No new technology; same Gradle/Spring Boot/Java 25 stack |
| VI — Severity Ceiling, Never Promote | ✅ PASS | All conditions declared as ERROR; DB override can only cap downward |
| VII — No System.out — SLF4J Only | ✅ PASS | New preprocessor/helper use SLF4J (`@Slf4j` on `PreprocessorHelper` for prompt-parse warnings) |
| VIII — TDD | ✅ APPLICABLE | Integration + preprocessor/helper unit tests cover the new code |

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

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── YouthRehabilitationPreprocessor.java           # NEW — youth-rehabilitation-order preprocessor (AC2)
├── YouthRehabilitationContext.java                # NEW — CEL context record for the rule
└── PreprocessorHelper.java                         # NEW — shared static helpers (used by all preprocessors)

src/test/java/uk/gov/hmcts/cp/integration/
└── YroEndDateValidationIntegrationTest.java       # NEW — TDD integration tests (Jira Scenarios 1–14)

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
├── YouthRehabilitationPreprocessorTest.java       # NEW — preprocessor unit tests
├── YouthRehabilitationContextTest.java            # NEW — context unit tests
└── PreprocessorHelperTest.java                     # NEW — shared-helper unit tests
```

**Java production source changes.** New: `YouthRehabilitationPreprocessor`, `YouthRehabilitationContext`, `PreprocessorHelper`. Existing preprocessors (`CommunityOrderEndDatePreprocessor`, `CustodialPreprocessor`, `DisqualificationExtendedTestPreprocessor`, `CtlMissingPreprocessor`) were refactored to delegate their duplicated helpers to `PreprocessorHelper` (behaviour-preserving). Unchanged code paths:

| Class | Role |
|---|---|
| `PreprocessorRegistry` | Dispatches on `preprocessing.type = "youth-rehabilitation-order"` |
| `CelValidationRule` | Evaluates CEL conditions against context; auto-discovers YAML |
| `ValidationRuleAutoConfiguration` | Discovers `DR-YRO-001.yaml` at startup via `classpath*:rules/DR-*.yaml` |

## Rule Design: DR-YRO-001

### Preprocessing configuration

```yaml
preprocessing:
  type: "youth-rehabilitation-order"
  communityOrderShortCodes: [YROEW, YRONI, YROFEW, YROISS, YROINI]
  curfewShortCodes:          [YRC2]
  curfewTagShortCodes:       [YRC1]
  furtherCurfewShortCodes:   [YRC3]
```

### Conditions

| Condition ID | CEL expression | Severity | Trigger |
|---|---|---|---|
| AC2a | `curViolationCount > 0` | ERROR | YRC2 end date after YRO end date |
| AC2b | `cureViolationCount > 0` | ERROR | YRC1 end-of-tag after YRO end date |
| AC2c | `curaViolationCount > 0` | ERROR | YRC3 end date after YRO end date |

### Message templates

| Condition | `messageTemplate` (inline per-offence) | `errorMessageTemplate` (summary banner) |
|---|---|---|
| AC2a | "The end date of the order must match or be longer than the end date of Youth Rehabilitation Requirement: Curfew" | Same + ". This affects ${defendantNames}." |
| AC2b | "The end date of the order must match or be longer than the end date of Youth Rehabilitation Requirement: Curfew with electronic monitoring" | Same + ". This affects ${defendantNames}." |
| AC2c | "The end date of the order must match or be longer than the end date of Youth Rehabilitation Requirement: Further curfew requirement made" | Same + ". This affects ${defendantNames}." |

### Rule priority

`5000` — evaluated after DR-COEW-001 (priority 4000); leaves room for future YRO rules.

## Test Plan (for `YroEndDateValidationIntegrationTest.java`)

All tests extend `IntegrationTestBase`. The integration scenarios map directly onto the Jira DD-41654
acceptance scenarios covering AC2 plus combined display cases. Preprocessor and shared-helper
logic is additionally covered by `YouthRehabilitationPreprocessorTest` and `PreprocessorHelperTest`.
(An earlier duplicate IT, `YroDateValidationRuleIntegrationTest`, covered only AC2 and was removed
in favour of this superset.)

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

### Combined and response-structure scenarios

| Test ID | Scenario | Expected |
|---|---|---|
| T010 | Error summary includes defendant name (${defendantNames} resolved) | `errorMessage` contains defendant full name |
| T011 | Inline message scoped to violating offence only | `affectedOffences` in issue contains only the breaching offenceId |
| T012 | Valid YRO with all curfew requirements within order end date | `isValid: true`, no errors |

## Complexity Tracking

No constitution violations. No complexity tracking entry required.
