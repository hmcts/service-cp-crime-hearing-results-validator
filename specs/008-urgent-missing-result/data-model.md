# Data Model: URGENT Result Missing Warning (CRA-22 / DR-URG-008)

**Branch**: `CRA-22-urgent-missing-result`
**Date**: 2026-09-02

---

## New: `ConditionalBailContext` record

**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailContext.java`
**Pattern**: Java record implementing `RuleEvaluationContext` — same shape as `DefendantContext`.

```
ConditionalBailContext
├── defendantId: String                     - deduplicated defendant key (masterDefendantId if present)
├── defendantName: String                   - display name ("First Last")
├── conditionalBailOffenceCount: long       - number of offences with conditional bail remand status
├── bailEndedCount: long                    - how many of those have at least one bail-ending result line
├── hasUrgentCount: long                    - 1 if any conditional-bail offence carries URGENT; 0 if not
└── allOffenceIds: List<String>             - all conditional-bail offence IDs (for message resolution)
```

**CEL context** (from `toCelContext()`):
```
{ "conditionalBailOffenceCount" → long,
  "bailEndedCount"              → long,
  "hasUrgentCount"              → long }
```

**Defendant set** (from `getDefendantIdSet("defendantId")`): `[defendantId]`

**Offence set** (from `getOffenceIdSet(...)`, used when `affectedOffenceSet` is set on the YAML condition):
- `"allOffenceIds"` → `allOffenceIds`

---

## Modified: `PreprocessingDefinition` record

**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`

Two new fields added (null-safe; unused by all existing preprocessors):

| New field                     | Type           | Used by                     | Purpose                                                          |
|-------------------------------|----------------|-----------------------------|------------------------------------------------------------------|
| `bailEndingShortCodes`        | `List<String>` | `ConditionalBailPreprocessor` | Non-Category-F codes that end conditional bail: DS, RI family, WOFN |
| `conditionalBailRemandStatus` | `String`       | `ConditionalBailPreprocessor` | Remand status string value indicating conditional bail on `OffenceDto` |

Existing fields are unaffected. The `@Builder` annotation on the record means the new fields default to `null` in all existing preprocessors.

---

## New: `ConditionalBailPreprocessor` component

**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/ConditionalBailPreprocessor.java`
**Type qualifier**: `"conditional-bail-urgent-check"`

**Processing flow** (per invocation):

```
1. Build offenceMap: Map<offenceId, OffenceDto> from request.getOffences()
2. Build offenceDefendantMap: Map<offenceId, defendantGroupKey> from result lines
   (uses PreprocessorHelper.groupLinesByDedupedDefendant for dedupe)
3. Build resultsByOffence: Map<offenceId, List<ResultLineDto>> from PreprocessorHelper.groupResultsByOffence
4. Build bailEndingUpper: Set<String> = PreprocessorHelper.upperSet(config.bailEndingShortCodes())
5. For each deduplicated defendant group (defendantId → defendantName):
   a. Collect offenceIds belonging to this defendant group
   b. Filter to conditional-bail offences:
      offence.getRemandStatus() [case-insensitive] == config.conditionalBailRemandStatus()
      (null remandStatus → skip offence; treats missing CHD-2485 data as "no conditional bail")
   c. conditionalBailOffenceCount = count of conditional-bail offences
   d. For each conditional-bail offence:
      - isBailEnding = any result line has (category == F) OR (shortCode ∈ bailEndingUpper)
      - hasUrgent   = any result line has (shortCode [upper] == "URGENT")
   e. bailEndedCount  = count of conditional-bail offences where isBailEnding
   f. hasUrgentCount  = 1 if any conditional-bail offence has hasUrgent; 0 otherwise
   g. Emit ConditionalBailContext(defendantId, defendantName, conditionalBailOffenceCount,
                                   bailEndedCount, hasUrgentCount, conditionalBailOffenceIds)
6. Return Map<defendantGroupKey, ConditionalBailContext>
```

**Null-safety rules**:
- `request.getOffences()` null → empty offenceMap
- `request.getResultLines()` null → handled by `PreprocessorHelper` (returns empty maps)
- `offence.getRemandStatus()` null → treated as "not conditional bail" (safe no-op until CHD-2485)
- `resultLine.getCategory()` null → not treated as Category F

**Constant**: `private static final String URGENT_CODE = "URGENT"` — hardcoded, not YAML-configurable.

---

## New: `DR-URG-008.yaml` rule file

**File**: `src/main/resources/rules/DR-URG-008.yaml`

```yaml
rule:
  id: "DR-URG-008"
  title: "Urgent Result Missing – Conditional Bail Ended"
  description: >-
    Warns when all of a defendant's offences that carried a conditional bail remand
    status have been resulted with bail-ending outcomes (final result, sentence deferred,
    remand in custody, or warrant without bail) and no URGENT result has been recorded.
  priority: 8000
  enabled: true

  preprocessing:
    type: "conditional-bail-urgent-check"
    bailEndingShortCodes:
      - DS
      - RI
      - RIYDA
      - RIH
      - RIB
      - RILA
      - RILAB
      - REMYD
      - WOFN
    conditionalBailRemandStatus: "CONDITIONAL_BAIL"

  conditions:
    - id: "AC1"
      name: "Conditional bail ended without URGENT result"
      expression: >-
        conditionalBailOffenceCount > 0
        && bailEndedCount == conditionalBailOffenceCount
        && hasUrgentCount == 0
      severity: WARNING
      validationLevel: DEFENDANT
      messageTemplate: >-
        The defendant's conditional bail has ended. You may need to add the URGENT result
        and select "Bail conditions cancelled" on one of the offences before sharing.
      affectedDefendantSet: "defendantId"
```

---

## New: Flyway migration

**File**: `src/main/resources/db/migration/V1.009__insert_dr_urg_008.sql`

```sql
INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by)
VALUES ('DR-URG-008', true, 'WARNING', now(), 'system');
```

---

## State transitions

No state is mutated. The rule evaluates a snapshot of hearing result lines and offence remand status at the point of the "Save and continue" validation call.

---

## External dependency: `OffenceDto.getRemandStatus()`

| Library                              | Current version | Required when   | Field added by |
|--------------------------------------|-----------------|-----------------|----------------|
| `api-cp-crime-hearing-results-validator` | 26.25       | CHD-2485 ships  | CHD-2485       |

Until CHD-2485 ships, `offence.getRemandStatus()` does not exist. The preprocessor handles this gracefully — null remand status means the offence is not treated as conditional bail, so `conditionalBailOffenceCount` is always 0 and the rule never fires. No false positives.
