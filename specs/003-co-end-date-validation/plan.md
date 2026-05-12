# Implementation Plan: Community Order End Date Validation

**Branch**: `DD-41653-co-end-date-validation` | **Date**: 2026-05-12 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `specs/003-co-end-date-validation/spec.md`

## Summary

Adds validation rule `DR-COEW-001` to detect three categories of invalid community order end dates:
- **AC1**: Order end date is today or in the past
- **AC2**: Order end date is earlier than the end date of any attached requirement (CUR, CURE, CURA, AAR)
- **AC3**: Order end date is less than 12 months from the hearing date when an Unpaid Work (UPWR) requirement is present

Implemented as one new YAML rule file and one new preprocessor (`CommunityOrderEndDatePreprocessor`) with a new context record (`CommunityOrderContext`). `PreprocessingDefinition` receives six new fields (additive, backward-compatible). No DTO or API contract changes are required.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Lombok, Mockito + AssertJ (tests)  
**Storage**: No storage changes — validation_rule DB table applies via existing `RuleOverrideService` framework  
**Testing**: JUnit 5, Mockito, AssertJ, TestContainers PostgreSQL 15.3 (integration), WireMock  
**Target Platform**: Azure-hosted microservice (Linux, Kubernetes 4550)  
**Project Type**: Web service (Spring Boot validation microservice)  
**Performance Goals**: Preprocessing per rule < 1 ms for typical hearing sizes (< 20 result lines)  
**Constraints**: Checkstyle Google maxWarnings=0; PMD ignoreFailures=false; Java records for all DTOs/context types; SLF4J only  
**Scale/Scope**: One new rule, one new preprocessor class, one new context record, six additive fields on PreprocessingDefinition

## Constitution Check

*GATE: Must pass before implementation. Re-checked post-design.*

| # | Principle | Status | Evidence |
|---|-----------|--------|---------|
| I | YAML/CEL Rule-First | ✅ PASS | `DR-COEW-001.yaml` is the source of truth. Six conditions expressed as CEL. Preprocessor is general-purpose, not rule-specific logic. |
| II | Constructor Injection & Immutable DTOs | ✅ PASS | `CommunityOrderEndDatePreprocessor` will use `@RequiredArgsConstructor`. `CommunityOrderContext` is a Java record (immutable). |
| III | Layered Architecture & Data-Driven Dispatch | ✅ PASS | New preprocessor registered as `@Component` with qualifier `"community-order-end-date"`. `CelValidationRule` dispatches via registry — no hard-wiring. |
| IV | Spec-Driven Build Loop | ✅ PASS | Spec → Plan → Tasks → Implement → Review → QA → Spec-Validate → Ship cycle is followed. |
| V | HMCTS Standards Compliance | ✅ PASS | Root package `uk.gov.hmcts.cp`, Gradle build, SLF4J logging, no Maven. |
| VI | Severity Ceiling, Never Promote | ✅ PASS | All six conditions ship with severity `ERROR` in YAML. DB override may lower; never raises. |
| VII | No System.out/System.err | ✅ PASS | All logging via SLF4J `@Slf4j`. Enforced by Checkstyle. |
| VIII | Test-Driven Development | ✅ PASS | `CommunityOrderEndDatePreprocessorTest` and `CommunityOrderEndDateValidationIT` written before production code. |

**No violations. No complexity tracking entries required.**

## Project Structure

### Documentation (this feature)

```text
specs/003-co-end-date-validation/
├── spec.md              ← feature specification
├── plan.md              ← this file
├── research.md          ← Phase 0 decisions
├── data-model.md        ← data design and YAML schema
├── quickstart.md        ← manual testing guide
└── checklists/
    └── requirements.md  ← spec quality checklist
```

### Source Code

```text
src/main/resources/rules/
└── DR-COEW-001.yaml                              ← NEW: 1 rule, 6 conditions

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── CommunityOrderContext.java                    ← NEW: Java record (context)
├── CommunityOrderEndDatePreprocessor.java        ← NEW: preprocessor @Component
└── PreprocessingDefinition.java                  ← MODIFIED: 6 additive fields

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
└── CommunityOrderEndDatePreprocessorTest.java    ← NEW: unit tests (TDD)

src/test/java/uk/gov/hmcts/cp/services/rules/integration/
└── CommunityOrderEndDateValidationIT.java        ← NEW: integration tests (TDD)
```

**Structure Decision**: Single-project layout, extending existing `cel/` package. No new packages required.

## Phase 0: Research

Complete. See [research.md](research.md) for decisions D-001 through D-007.

**Key resolved decisions**:
- Parent-child relationship inferred from shared `(defendantId, offenceId)` — no DTO change needed
- All requirement dates use the single `endDate: LocalDate` field on `ResultLineDto`
- One YAML rule (DR-COEW-001) with 6 conditions — not three separate rules
- Per-offence-per-defendant context grouping
- Priority 4000 (after existing rules at 1000/2000/3000)

## Phase 1: Design

Complete. See [data-model.md](data-model.md) for full schema.

**Artefacts**:

### CommunityOrderContext (new record)

```java
package uk.gov.hmcts.cp.services.rules.cel;

record CommunityOrderContext(
    String defendantName,
    long pastEndDateCount,
    long curViolationCount,
    long cureViolationCount,
    long curaViolationCount,
    long aarViolationCount,
    long upwrViolationCount,
    List<String> allOffenceIds
) implements RuleEvaluationContext {

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
            "pastEndDateCount",   pastEndDateCount,
            "curViolationCount",  curViolationCount,
            "cureViolationCount", cureViolationCount,
            "curaViolationCount", curaViolationCount,
            "aarViolationCount",  aarViolationCount,
            "upwrViolationCount", upwrViolationCount
        );
    }

    @Override
    public List<String> getOffenceIdSet(String setName) {
        if ("allOffenceIds".equals(setName)) return allOffenceIds;
        throw new IllegalArgumentException("Unknown offence set: " + setName);
    }

    @Override
    public List<String> allOffenceIds() { return allOffenceIds; }
}
```

### PreprocessingDefinition additions

Six new `List<String>` fields, all nullable, with Lombok `@Data` / `@Builder`:

```java
private List<String> communityOrderShortCodes;
private List<String> curfewShortCodes;
private List<String> curfewTagShortCodes;
private List<String> furtherCurfewShortCodes;
private List<String> alcoholAbstinenceShortCodes;
private List<String> unpaidWorkShortCodes;
```

### CommunityOrderEndDatePreprocessor skeleton

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityOrderEndDatePreprocessor implements ValidationPreprocessor {

    @Override
    public String type() { return "community-order-end-date"; }

    @Override
    public Map<String, CommunityOrderContext> preprocess(
            DraftValidationRequest request,
            PreprocessingDefinition config) {
        // See data-model.md algorithm
    }
}
```

### DR-COEW-001.yaml structure

See [data-model.md](data-model.md#dr-coew-001yaml-new-yaml-rule) for the full YAML.

### External contracts

No changes to the external API contract (`DraftValidationRequest`, `DraftValidationResponse`). The `api-cp-crime-hearing-results-validator` upstream dependency is unchanged.

## Implementation Order (TDD sequence)

Follow Red → Green → Refactor for each step.

### Step 1 — Failing unit tests (write first, confirm they fail)

File: `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessorTest.java`

Test scenarios:
- `preprocess_coewEndDateIsHearingDate_should_returnPastEndDateCountOne`
- `preprocess_coewEndDateInPast_should_returnPastEndDateCountOne`
- `preprocess_coewEndDateInFuture_should_returnPastEndDateCountZero`
- `preprocess_coewEndDateBeforeCurEndDate_should_returnCurViolationCountOne`
- `preprocess_coewEndDateBeforeCureEndDate_should_returnCureViolationCountOne`
- `preprocess_coewEndDateBeforeCuraEndDate_should_returnCuraViolationCountOne`
- `preprocess_coewEndDateBeforeAarEndDate_should_returnAarViolationCountOne`
- `preprocess_coewEndDateAfterAllRequirementEndDates_should_returnAllViolationCountsZero`
- `preprocess_coewWithUpwrAndEndDateUnder12Months_should_returnUpwrViolationCountOne`
- `preprocess_coewWithUpwrAndEndDateExactly12Months_should_returnUpwrViolationCountZero`
- `preprocess_coewWithUpwrAndEndDateOver12Months_should_returnUpwrViolationCountZero`
- `preprocess_coewWithNoUpwr_should_returnUpwrViolationCountZero`
- `preprocess_coewEndDateNull_should_returnEmptyContext`
- `preprocess_noCoewResultLine_should_returnEmptyContext`
- `preprocess_multipleDefendantsOnSameOffence_should_returnSeparateContextPerDefendant`
- `preprocess_cosResultLine_should_applyAllRulesLikeCoew`
- `preprocess_coniResultLine_should_applyAllRulesLikeCoew`
- `preprocess_requirementEndDateNull_should_notCountAsViolation`

### Step 2 — Failing integration tests (write first, confirm they fail)

File: `src/test/java/uk/gov/hmcts/cp/services/rules/integration/CommunityOrderEndDateValidationIT.java`

Test scenarios:
- `validate_coewEndDateIsToday_should_returnErrorAc1`
- `validate_coewEndDateInPast_should_returnErrorAc1`
- `validate_coewEndDateInFuture_should_returnNoAc1Error`
- `validate_coewEndDateBeforeCurEndDate_should_returnErrorAc2a`
- `validate_coewEndDateBeforeCureEndDate_should_returnErrorAc2b`
- `validate_coewEndDateBeforeCuraEndDate_should_returnErrorAc2c`
- `validate_coewEndDateBeforeAarEndDate_should_returnErrorAc2d`
- `validate_coewWithUpwrAndShortDuration_should_returnErrorAc3`
- `validate_coewWithUpwrAndExactly12Months_should_returnNoAc3Error`
- `validate_allConstraintsSatisfied_should_returnNoErrors`
- `validate_multipleConditionsFire_should_returnAllErrors`
- `validate_cosResultLine_should_applyRules`
- `validate_coniResultLine_should_applyRules`

### Step 3 — Production code (after tests are red)

1. Create `CommunityOrderContext.java` (record)
2. Add 6 fields to `PreprocessingDefinition.java`
3. Create `CommunityOrderEndDatePreprocessor.java`
4. Create `DR-COEW-001.yaml`

### Step 4 — Static analysis

```bash
gradle checkstyleMain pmdMain
```

Fix any violations before proceeding.

### Step 5 — Full build

```bash
gradle build
```

All tests must pass (unit + integration).

### Step 6 — Build loop agents

1. **code-reviewer** — reads new Java for layering, null safety, System.out, severity-ceiling
2. **qa** — verifies TDD discipline, runs `gradle test`
3. **spec-validator** — validates DR-COEW-001.yaml against RuleDefinition schema, CEL compilation, preprocessor type resolution

Repeat until all three return PASS / COMPLIANT.

## Out of Scope

- Changes to `ValidationController` or `DraftValidationResponse`
- Changes to `api-cp-crime-hearing-results-validator` upstream DTO
- Per-rule integration tests of DB severity override / caching (covered by `ValidationRuleOverrideIntegrationTest`)
- UI error placement (front-end concern)
- Independent DB override control per AC (single rule DR-COEW-001 covers all)
