# Data Model: CRA-362 – Pre-Release Bail Condition Warning (DR-BAIL-016)

**Branch**: `dev/CRA-362-pre-release-bail-condition-warning` | **Date**: 2026-11-09

## New Java Components

### 1. `PreReleaseBailContext` (new record — per offence)

```java
package uk.gov.hmcts.cp.services.rules.cel;

/**
 * Per-offence context produced by {@link PreReleaseBailPreprocessor} for rule DR-BAIL-016.
 * One instance per offence that qualifies (current remand status C/L/S + conditional bail
 * result + trigger bail condition present). Carries counts for CEL evaluation:
 *   triggerCount  — 1 if all trigger conditions met, 0 otherwise
 *   adultCount    — 1 if defendant is 18+ on hearing date, 0 otherwise
 *   youthCount    — 1 if defendant is <18, 0 otherwise
 *   noDobCount    — 1 if defendant has no date of birth, 0 otherwise
 *   ccicCount     — 1 if the qualifying result is CCIC, 0 otherwise
 *   ccscCount     — 1 if the qualifying result is CCSC, 0 otherwise
 */
public record PreReleaseBailContext(
        String offenceId,
        long triggerCount,
        long adultCount,
        long youthCount,
        long noDobCount,
        long ccicCount,
        long ccscCount
) implements RuleEvaluationContext {

    static final String OFFENCE_ID_SET = "offenceId";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "triggerCount", triggerCount,
                "adultCount",   adultCount,
                "youthCount",   youthCount,
                "noDobCount",   noDobCount,
                "ccicCount",    ccicCount,
                "ccscCount",    ccscCount);
    }

    @Override
    public List<String> getOffenceIdSet(String setName) {
        if (OFFENCE_ID_SET.equals(setName)) {
            return List.of(offenceId);
        }
        throw new IllegalArgumentException("Unknown offence set: " + setName);
    }

    @Override
    public String defendantName() { return null; }

    @Override
    public List<String> allOffenceIds() { return List.of(offenceId); }
}
```

### 2. `PreReleaseBailPreprocessor` (new @Component)

```
package uk.gov.hmcts.cp.services.rules.cel
@Component
QUALIFIER = "pre-release-bail-condition"

CUSTODIAL_STATUSES = Set.of(BailStatusEnum.C, BailStatusEnum.L, BailStatusEnum.S)
PORO1_CODE = "PORO1"
TRIGGER_STEMS = Set.of("passport", "suret", "securit", "asbort", "echniw", "sicrwydd")
COMBINING_MARKS = Pattern.compile("\\p{M}")

preprocess(request, config):
  conditionalBailCodes = upperSet(config.conditionalBailResultCodes())
  crownCourtBailCodes  = upperSet(config.crownCourtBailCodes())
  allConditionalCodes  = union(conditionalBailCodes, crownCourtBailCodes)
  triggerCondCodes     = upperSet(config.triggerBailConditionCodes())

  offenceById  = index(request.getOffences(),   OffenceDto::getOffenceId)
  defendantById = index(request.getDefendants(), DefendantDto::getDefendantId)
  resultsByOffence = groupResultsByOffence(request)

  result = LinkedHashMap<String, PreReleaseBailContext>

  for offenceId, lines in resultsByOffence:
    offence = offenceById.get(offenceId)
    if offence == null: continue
    if offence.bailStatus NOT IN CUSTODIAL_STATUSES: continue

    // Check whether any line changes remand status to conditional bail
    conditionalBailCode = firstMatchingCode(lines, allConditionalCodes)
    if conditionalBailCode == null: continue

    // Check for a trigger bail condition
    hasTrigger = anyShortCodeIn(lines, triggerCondCodes)
                 OR anyPoro1WithKeyword(lines)
    if !hasTrigger: continue

    // Determine age category from defendant
    defendant = defendantById.get(offence.getDefendantId())
    dob = defendant?.getDateOfBirth()
    hearingDay = request.getHearingDay()
    age = computeAge(dob, hearingDay)  // null if dob null or hearingDay null

    adultCount  = (age != null && age >= 18) ? 1L : 0L
    youthCount  = (age != null && age <  18) ? 1L : 0L
    noDobCount  = (age == null) ? 1L : 0L
    ccicCount   = "CCIC".equals(conditionalBailCode.toUpperCase()) ? 1L : 0L
    ccscCount   = "CCSC".equals(conditionalBailCode.toUpperCase()) ? 1L : 0L

    result.put(offenceId, new PreReleaseBailContext(
        offenceId, 1L, adultCount, youthCount, noDobCount, ccicCount, ccscCount))

  return result

anyPoro1WithKeyword(lines):
  for line in lines where line.shortCode.upper == PORO1_CODE:
    for prompt in line.getPrompts() (or empty):
      if containsTriggerKeyword(prompt.getPromptValue()): return true
  return false

containsTriggerKeyword(text):
  if text == null or blank: return false
  normalized = Normalizer.normalize(text, NFD)
  normalized = COMBINING_MARKS.matcher(normalized).replaceAll("").toLowerCase(ROOT)
  return TRIGGER_STEMS.any(stem -> normalized.contains(stem))

computeAge(dob, hearingDay):
  if dob == null or hearingDay == null: return null
  return Period.between(dob, hearingDay).getYears()
```

### 3. `PreprocessingDefinition` (modified record — add 3 new fields)

```java
// Add after existing fields (Lombok @Builder handles these automatically):
List<String> conditionalBailResultCodes,  // RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB
List<String> crownCourtBailCodes,         // CCIC, CCSC
List<String> triggerBailConditionCodes    // PASS1–PASS8, PORSC, PORS
```

---

## YAML Rule: `DR-BAIL-016.yaml`

```yaml
rule:
  id: "DR-BAIL-016"
  title: "Pre-release bail condition entered for defendant in custody"
  description: >-
    Advisory warning when bail conditions typically used as pre-release conditions
    are entered alongside a conditional bail result for a defendant currently in
    custody, care of local authority, or youth detention accommodation. Does not
    block sharing.
  priority: 17000
  enabled: true

  preprocessing:
    type: "pre-release-bail-condition"
    conditionalBailResultCodes:
      - RC
      - RCBV
      - BAILREVC
      - REMCB
      - REMCBY
      - RCCCB
    crownCourtBailCodes:
      - CCIC
      - CCSC
    triggerBailConditionCodes:
      - PASS1
      - PASS2
      - PASS3
      - PASS4
      - PASS5
      - PASS6
      - PASS7
      - PASS8
      - PORSC
      - PORS

  conditions:
    - id: "AC1"
      name: "Adult on conditional bail with pre-release condition"
      expression: "triggerCount > 0 && ccicCount == 0 && ccscCount == 0 && adultCount > 0"
      severity: WARNING
      messageTemplate: >-
        Do not share until you have checked the remand result. This result releases the
        defendant from custody. If any bail conditions are pre-release conditions, use
        'Remanded in custody with bail direction (RIB)' instead.
      affectedOffenceSet: "offenceId"
      validationLevel: OFFENCE

    - id: "AC2"
      name: "Youth on conditional bail with pre-release condition"
      expression: "triggerCount > 0 && ccicCount == 0 && ccscCount == 0 && youthCount > 0"
      severity: WARNING
      messageTemplate: >-
        Do not share until you have checked the remand result. This result releases the
        defendant on bail. If any bail conditions are pre-release conditions, use 'Remand
        in care of Local Authority with bail direction (RILAB)' instead.
      affectedOffenceSet: "offenceId"
      validationLevel: OFFENCE

    - id: "AC6"
      name: "Unknown age on conditional bail with pre-release condition"
      expression: "triggerCount > 0 && ccicCount == 0 && ccscCount == 0 && noDobCount > 0"
      severity: WARNING
      messageTemplate: >-
        Do not share until you have checked the remand result. This result releases the
        defendant on bail. If any bail conditions are pre-release conditions, use
        'Remanded in custody with bail direction (RIB)' for an adult or 'Remand in care
        of Local Authority with bail direction (RILAB)' for a youth instead.
      affectedOffenceSet: "offenceId"
      validationLevel: OFFENCE

    - id: "AC2A"
      name: "CCIC result with pre-release bail condition"
      expression: "triggerCount > 0 && ccicCount > 0"
      severity: WARNING
      messageTemplate: >-
        Do not share until you have checked the remand result. This result releases the
        defendant on bail. If any bail conditions are pre-release conditions, use 'Sent to
        Crown Court in custody for trial with bail direction (CCIIB)' instead.
      affectedOffenceSet: "offenceId"
      validationLevel: OFFENCE

    - id: "AC2B"
      name: "CCSC result with pre-release bail condition"
      expression: "triggerCount > 0 && ccscCount > 0"
      severity: WARNING
      messageTemplate: >-
        Do not share until you have checked the remand result. This result releases the
        defendant on bail. If any bail conditions are pre-release conditions, use
        'Committed to Crown Court for sentence - in custody with bail direction (CCSIB)'
        instead.
      affectedOffenceSet: "offenceId"
      validationLevel: OFFENCE
```

---

## DB Migration: `V1.018__insert_dr_bail_016.sql`

```sql
INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by)
VALUES ('DR-BAIL-016', true, 'WARNING', now(), 'system');
```

---

## CEL Condition Mutual Exclusion Analysis

For any single `PreReleaseBailContext` (one offence), exactly one of the five conditions fires:

| adultCount | youthCount | noDobCount | ccicCount | ccscCount | Fires  |
|-----------|-----------|-----------|----------|----------|--------|
| 1 | 0 | 0 | 0 | 0 | AC1    |
| 0 | 1 | 0 | 0 | 0 | AC2    |
| 0 | 0 | 1 | 0 | 0 | AC6    |
| * | * | * | 1 | 0 | AC2A   |
| * | * | * | 0 | 1 | AC2B   |

Notes:
- CCIC/CCSC override age selection because AC2A/AC2B expressions only check `ccicCount`/`ccscCount` and `triggerCount`, not age counts. AC1/AC2/AC6 guard against `ccicCount == 0 && ccscCount == 0`.
- `adultCount + youthCount + noDobCount == 1` always (mutually exclusive age buckets).
- For CCIC: AC1/AC2/AC6 all suppress themselves (`ccicCount == 0` guard fails), only AC2A fires.

---

## Package Location

All new Java files in:
`src/main/java/uk/gov/hmcts/cp/services/rules/cel/`

No new packages. No controller changes. No Spring config changes (preprocessor is auto-registered via `@Component` and `PreprocessorRegistry`).
