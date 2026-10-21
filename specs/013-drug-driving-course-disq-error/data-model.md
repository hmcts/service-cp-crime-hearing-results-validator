# Data Model: DR-DISQ-013 Drug Driving Course-Reduction Disqualification Error

## New Java Record: DrugDrivingContext

```java
package uk.gov.hmcts.cp.services.rules.cel;

public record DrugDrivingContext(
    String offenceId,
    long qualifyingCount,           // 1 if drug driving offence has DDRCO/DDRCOT; 0 otherwise
    List<String> qualifyingOffenceIds,  // [offenceId] when qualifyingCount == 1; else empty
    List<String> allOffenceIds          // always [offenceId]
) implements RuleEvaluationContext {

    @Override public String defendantName() { return null; }

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("qualifyingCount", qualifyingCount);
    }

    @Override
    public List<String> getOffenceIdSet(String setName) {
        return switch (setName) {
            case "qualifyingOffenceIds" -> qualifyingOffenceIds;
            case "allOffenceIds"        -> allOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }
}
```

**CEL variable exposed**: `qualifyingCount` (Long, always 0 or 1 per offence context).

## PreprocessingDefinition — new field

Add to the existing `PreprocessingDefinition` record in
`src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`:

```java
// Drug-driving-course-reduction-specific short codes (used by DrugDrivingCourseReductionPreprocessor)
List<String> courseReductionShortCodes,
```

No existing callers are affected — Lombok `@Builder` leaves the new field as `null` for all existing
builder usages; the new preprocessor guards against null with `upperSet()`.

## New Preprocessor: DrugDrivingCourseReductionPreprocessor

**Qualifier**: `"drug-driving-course-reduction"`
**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/DrugDrivingCourseReductionPreprocessor.java`

**Algorithm** (per offence):
1. Build `relevantCodes = upperSet(config.relevantOffenceCodes())`
2. Build `courseReductionCodes = upperSet(config.courseReductionShortCodes())`
3. For each offence in request:
   a. `relevant = offenceCode != null && relevantCodes.contains(offenceCode.toUpperCase())`
   b. `lines = resultsByOffence.getOrDefault(offenceId, [])`
   c. `hasCourseReduction = anyShortCodeIn(lines, courseReductionCodes)` — checks ALL lines (no category filter)
   d. `qualifying = relevant && hasCourseReduction`
4. Emit `DrugDrivingContext(offenceId, qualifying ? 1 : 0, qualifying ? [offenceId] : [], [offenceId])`

## YAML Rule: DR-DISQ-013.yaml

```yaml
rule:
  id: "DR-DISQ-013"
  title: "Drug driving offence with course-reduction disqualification"
  description: >-
    Blocks sharing when DDRCO or DDRCOT is recorded against a drug driving offence.
    These results are unlawful on drug driving offences and must be replaced before
    results are shared with the DVLA and the defendant.
  priority: 13000
  enabled: true

  preprocessing:
    type: "drug-driving-course-reduction"
    relevantOffenceCodes:
      - RT88584   # Drive motor vehicle with proportion of specified controlled drug above specified limit (s.5A)
      - RT88586   # Attempt to drive with specified controlled drug above specified limit
      - RT88584B  # Aid/abet driving with specified controlled drug above specified limit
      - RT88340   # Drive a vehicle whilst unfit through drugs (s.4)
      - RA88045   # Cause death by careless driving while over specified limit – specified controlled drug
    courseReductionShortCodes:
      - DDRCO     # Driving Disq (obligatory) – Reduction for course
      - DDRCOT    # Obligatory disqualification until extended test passed – reduction for course

  conditions:
    - id: "AC1"
      name: "Drug driving offence with course-reduction disqualification"
      expression: "qualifyingCount > 0"
      severity: ERROR
      messageTemplate: >-
        You cannot add a disqualification with a reduction for attending a course
        to a drug driving offence. Remove this result and add the correct
        disqualification.
      affectedOffenceSet: "qualifyingOffenceIds"
      validationLevel: OFFENCE
```

## Liquibase Migration: V1.014__insert_dr_disq_013.sql

```sql
INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by)
VALUES ('DR-DISQ-013', true, 'ERROR', now(), 'system');
```

## priority.txt update

Append `"DR-DISQ-013"` to the existing comma-separated list in
`src/main/resources/rules/priority.txt`.

## Entities unchanged

No changes to `DraftValidationRequest`, `OffenceDto`, `ResultLineDto`, or any other DTO — these
live in the external `api-cp-crime-hearing-results-validator` dependency and are not modified by
this feature (Constitution Principle I, clause on upstream DTOs).

No new database table or column. The `validation_rule` table schema is unchanged; only a new row
is inserted.
