# Data Model: Curfew and AAR Requirement End-Date Period Validation (DR-COEW-002)

**Branch**: `DD-41655-curfew-aar-end-date-validation`
**Date**: 2026-05-27

---

## Existing Entities (unchanged)

### `DraftValidationRequest` (upstream library)

| Field | Type | Notes |
|-------|------|-------|
| `hearingDay` | `LocalDate` | Used as the AAR period base date |
| `resultLines` | `List<ResultLineDto>` | Flat list of all result lines |
| `defendants` | `List<DefendantDto>` | For display-name lookup |
| `offences` | `List<OffenceDto>` | For offence display ordering |

### `ResultLineDto` (upstream library)

| Field | Type | Notes |
|-------|------|-------|
| `shortCode` | `String` | e.g. `CUR`, `CURE`, `YRC2`, `YRC1`, `AAR`, `COEW` |
| `defendantId` | `String` | Groups line to a defendant |
| `offenceId` | `String` | Groups line to an offence |
| `prompts` | `List<Prompt>` | Carries date and duration values entered by clerk |

---

## Modified Entities

### `Prompt` (upstream library — **requires version bump to 0.1.8**)

Two new fields are added to the existing `Prompt` class. Existing fields (`promptRef`, `promptValue`) are unchanged and continue to carry DATE and INT values as before.

| Field | Type | Notes |
|-------|------|-------|
| `promptRef` | `String` | Key identifying which prompt (existing) |
| `promptValue` | `String` | Value for DATE and INT types (existing) |
| `type` | `String` | **NEW** — `"DATE"`, `"INT"`, or `"DURATION"` |
| `childPrompts` | `List<Prompt>` | **NEW** — populated for `DURATION` type only; empty list otherwise |

**DURATION prompt structure** (CUR/YRC2 and CURE/YRC1 period):

```
Prompt {
  promptRef  = "curfewPeriod"                          // or "curfewAndElectronicMonitoringPeriod"
  type       = "DURATION"
  childPrompts = [
    Prompt {
      promptRef  = "Days"                              // or "Weeks" or "Months"
      promptValue = "21"                               // integer quantity as string
      type       = "INT"
    }
  ]
}
```

**INT prompt structure** (AAR period — no change to existing model; `promptValue` carries the integer):

```
Prompt {
  promptRef  = "numberOfDaysToAbstainFromConsumingAnyAlcohol"
  promptValue = "120"                                  // integer days as string
  type       = "INT"                                   // (new field; safe to ignore if null)
}
```

### `PreprocessingDefinition` (this service)

One new field added alongside existing short-code lists:

| New Field | YAML Key | Example values |
|-----------|----------|----------------|
| `yroShortCodes` | `yroShortCodes` | `[YROEW, YRONI, YROFEW, YROISS, YROINI]` |

This field allows the preprocessor to distinguish CO parents (existing `communityOrderShortCodes`) from YRO parents, which is required to gate AAR validation to CO-only.

### `RuleEvaluationContext` (this service — interface change)

One new default method added. **Backward compatible**: all existing implementations inherit the default and are unaffected.

```java
/**
 * Returns a map of arbitrary string values resolved as ${key} placeholders by
 * MessageTemplateResolver. Default returns an empty map.
 */
default Map<String, String> stringVariables() {
    return Map.of();
}
```

### `MessageTemplateResolver` (this service)

`resolve()` is extended to apply `stringVariables()` substitutions **after** the existing `${offenceNumber}` and `${defendantName}` substitutions. Any `${key}` token whose key appears in the `stringVariables` map is replaced with the corresponding value. Unknown tokens are left unchanged (safe for templates that don't use them).

New `resolve()` signature (additional parameter):

```java
public String resolve(
    String template,
    String defendantName,
    List<String> affectedOffenceIds,
    Map<String, OffenceDto> offenceMap,
    List<String> allOffenceIds,
    Map<String, String> stringVariables   // NEW
)
```

### `CelValidationRule.evaluate()` (this service — two framework changes)

1. **Pass `context.stringVariables()` to `messageResolver.resolve()`** — both for `messageTemplate` and `errorMessageTemplate` resolution paths.

2. **Set `affectedDefendants` on OFFENCE-level ERRORs when `context.defendantId()` is non-null** — adds the following logic to the OFFENCE-level path:

```java
if (context.defendantId() != null && isError) {
    issueBuilder.affectedDefendants(
        offenceDisplayHelper.buildAffectedDefendants(
            List.of(context.defendantId()), null));
}
```

Backward compatible: existing contexts (`CommunityOrderContext`, `DefendantContext`, etc.) return `null` from `defendantId()` (the interface default), so no change to their issued `ValidationIssue` objects.

---

## New Entities

### `CurfewPeriodContext` — new record

Per-violation context produced by `CurfewPeriodPreprocessor`. Each instance represents exactly one violation: one defendant, one offence, one requirement type, one computed expected end date.

```
CurfewPeriodContext
  ├── defendantId: String         — used by CelValidationRule to set affectedDefendants
  ├── defendantName: String       — used by ${defendantName} placeholder (if needed)
  ├── offenceId: String           — the single offence with the violation
  ├── violationCount: long        — always 1L (context only exists for violations)
  └── expectedEndDate: String     — computed correct end date, formatted "dd/MM/yyyy"
```

**CEL variable map** (`toCelContext()`):

| CEL variable | Type | Value |
|---|---|---|
| `violationCount` | `Long` | Always `1L` |

**String variables** (`stringVariables()`):

| Key | Value |
|-----|-------|
| `expectedEndDate` | Computed correct end date in `dd/MM/yyyy` format |

**`getOffenceIdSet(setName)`**: returns `List.of(offenceId)` for any `setName` (only one offence per context).

**`allOffenceIds()`**: returns `List.of(offenceId)`.

**`defendantId()`**: overrides default; returns `defendantId` field.

---

### `CurfewPeriodPreprocessor` — new Spring component

**Qualifier**: `"curfew-period-check"` (used as `preprocessing.type` in `DR-COEW-002.yaml`)

**Processing algorithm**:

```
Input: DraftValidationRequest request, PreprocessingDefinition config
Output: Map<String, CurfewPeriodContext>   (key = "<TYPE>:<defendantId>:<offenceId>")

1. Normalise short-code sets from config (all uppercase):
     coShortCodes   = communityOrderShortCodes   {COEW, COS, CONI}
     yroShortCodes  = yroShortCodes              {YROEW, YRONI, YROFEW, YROISS, YROINI}
     allParentCodes = coShortCodes ∪ yroShortCodes
     curCodes       = curfewShortCodes           {CUR, YRC2}
     cureCodes      = curfewTagShortCodes        {CURE, YRC1}
     aarCodes       = alcoholAbstinenceShortCodes{AAR}

2. Build defendantId → defendantName lookup from request.getDefendants()

3. Group result lines by defendantId → Map<defendantId, List<ResultLineDto>>

4. For each (defendantId, defendantLines):
     Group defendantLines by offenceId → Map<offenceId, List<ResultLineDto>>

     For each (offenceId, offenceLines):
       hasCo  = offenceLines.any(shortCode in coShortCodes)
       hasYro = offenceLines.any(shortCode in yroShortCodes)
       if !hasCo && !hasYro: skip (no qualifying parent order)

       For each curLine (shortCode in curCodes):
         startDate = parseDate(curLine, "startDate")
         period    = parseDuration(curLine, "curfewPeriod")    // → (unit, quantity)
         endDate   = parseDate(curLine, "endDate")
         if any is null: log WARN, skip this line
         expectedEnd = startDate.plus(quantity, unit).minusDays(1)
         if endDate != expectedEnd:
           key = "CUR:" + defendantId + ":" + offenceId
           emit CurfewPeriodContext(defendantId, name, offenceId, 1L, format(expectedEnd))
           // If key already exists (two CUR lines on same offence), overwrite
           //   (first violation wins; edge case, not expected in practice)

       For each cureLine (shortCode in cureCodes):
         startDate = parseDate(cureLine, "startDateOfTagging")
         period    = parseDuration(cureLine, "curfewAndElectronicMonitoringPeriod")
         endDate   = parseDate(cureLine, "endDateOfTagging")
         if any is null: log WARN, skip
         expectedEnd = startDate.plus(quantity, unit).minusDays(1)
         if endDate != expectedEnd:
           key = "CURE:" + defendantId + ":" + offenceId
           emit CurfewPeriodContext(...)

       if hasCo (AAR is CO-only):
         For each aarLine (shortCode in aarCodes):
           if request.getHearingDay() == null: log WARN, skip
           days    = parseInt(aarLine, "numberOfDaysToAbstainFromConsumingAnyAlcohol")
           untilDate = parseDate(aarLine, "until")
           if days is null or untilDate is null: log WARN, skip
           expectedEnd = hearingDay.plusDays(days).minusDays(1)
           if untilDate != expectedEnd:
             key = "AAR:" + defendantId + ":" + offenceId
             emit CurfewPeriodContext(defendantId, name, offenceId, 1L, format(expectedEnd))

5. Return map (empty if no violations; contains only violated (type,defendant,offence) triples)
```

**Prompt parsing helpers**:

| Method | Input | Reads | Returns |
|--------|-------|-------|---------|
| `parseDate(line, promptRef)` | DATE prompt | `prompt.getPromptValue()` | `LocalDate` (ISO-8601) or `null` |
| `parseDuration(line, promptRef)` | DURATION prompt | `prompt.getChildPrompts().get(0)` | `DurationValue(unit, quantity)` or `null` |
| `parseInt(line, promptRef)` | INT prompt | `prompt.getPromptValue()` | `Integer` or `null` |

`DurationValue` is a local record:
```java
private record DurationValue(ChronoUnit unit, long quantity) {}
```

Unit mapping: `"Days"` → `ChronoUnit.DAYS`, `"Weeks"` → `ChronoUnit.WEEKS`, `"Months"` → `ChronoUnit.MONTHS`. Unknown unit → log WARN, return null.

---

## Rule YAML — `DR-COEW-002.yaml`

```yaml
rule:
  id: "DR-COEW-002"
  title: "Curfew and Alcohol Abstinence Requirement Period End Date Validation"
  description: >-
    Validates that the end date recorded for a Curfew requirement (CUR, CURE, YRC2, YRC1)
    equals the start date plus the entered period minus one day, and that the "Until" date
    for an Alcohol Abstinence and Monitoring requirement (AAR) equals the hearing date plus
    the number of abstinence days minus one day.
  priority: 4500
  enabled: true

  preprocessing:
    type: "curfew-period-check"
    communityOrderShortCodes:
      - COEW
      - COS
      - CONI
    yroShortCodes:
      - YROEW
      - YRONI
      - YROFEW
      - YROISS
      - YROINI
    curfewShortCodes:
      - CUR
      - YRC2
    curfewTagShortCodes:
      - CURE
      - YRC1
    alcoholAbstinenceShortCodes:
      - AAR

  conditions:
    - id: "AC1"
      name: "Requirement end date does not match entered period"
      expression: "violationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date does not match the period entered. End date based on
        entered period: ${expectedEndDate}
      errorMessageTemplate: >-
        The end date does not match the period entered. End date based on
        entered period: ${expectedEndDate}
      affectedOffenceSet: "violatedOffenceIds"
```

---

## Response: `ValidationIssue` (emitted per triggered context)

Each `CurfewPeriodContext` that exists (i.e., each violation) triggers the single `AC1` condition and produces one `ValidationIssue`.

| Field | Type | Value |
|-------|------|-------|
| `ruleId` | `String` | `"DR-COEW-002"` |
| `severity` | `SeverityEnum` | `ERROR` (ceiling can lower at runtime) |
| `validationLevel` | `ValidationLevelEnum` | `OFFENCE` |
| `message` | `String` | `"The end date does not match the period entered. End date based on entered period: DD/MM/YYYY"` |
| `affectedOffences` | `List<AffectedOffence>` | Singleton — the one offence with the violation |
| `affectedDefendants` | `List<AffectedDefendant>` | `[{ defendantId: "<id>" }]` — populated via the new framework change in `CelValidationRule` |

---

## Relationships

```
DraftValidationRequest
  ├── hearingDay ────────────────────────── base date for AAR calculation
  ├── resultLines: List<ResultLineDto>
  │     └── prompts: List<Prompt>
  │           ├── DURATION prompt ────────── curfew period (CUR/YRC2, CURE/YRC1)
  │           │     └── childPrompts[0]
  │           │           ├── promptRef ──── unit: "Days" | "Weeks" | "Months"
  │           │           └── promptValue ── integer quantity as string
  │           ├── DATE prompts ───────────── startDate, startDateOfTagging, endDate,
  │           │                              endDateOfTagging, until (ISO-8601 string)
  │           └── INT prompt ─────────────── numberOfDaysToAbstainFromConsumingAnyAlcohol
  └── defendants: List<DefendantDto> ─────── display name lookup

CurfewPeriodPreprocessor
  ├── reads: DraftValidationRequest (hearingDay + resultLines + defendants)
  ├── config: PreprocessingDefinition (5 short-code list fields + new yroShortCodes)
  └── produces: Map<"TYPE:defendantId:offenceId", CurfewPeriodContext>
        one entry per violation

CurfewPeriodContext  (one per violation)
  ├── violationCount = 1L ──────→ CEL variable map  (triggers "violationCount > 0")
  ├── expectedEndDate ─────────→ stringVariables()  (resolves ${expectedEndDate})
  ├── offenceId ───────────────→ getOffenceIdSet()  (returns singleton for affectedOffences)
  └── defendantId ─────────────→ defendantId()      (used by CelValidationRule for affectedDefendants)

DR-COEW-002.yaml
  └── 1 condition "AC1" → CelValidationRule → ValidationIssue (ERROR) per triggered context
        ├── affectedOffences    ── singleton offence
        └── affectedDefendants  ── [{ defendantId }] (new framework change)
```

---

## Files changed vs added

### New files

| File | Role |
|------|------|
| `src/main/resources/rules/DR-COEW-002.yaml` | YAML rule definition (Constitution Principle I — authored first) |
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodContext.java` | Context record per violation |
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodPreprocessor.java` | Preprocessor (qualifier `"curfew-period-check"`) |
| `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodContextTest.java` | Unit tests for context |
| `src/test/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodPreprocessorTest.java` | Unit tests for preprocessor |
| `src/test/java/uk/gov/hmcts/cp/integration/CurfewPeriodRuleIntegrationTest.java` | End-to-end integration tests |

### Modified files

| File | Change |
|------|--------|
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/RuleEvaluationContext.java` | Add `default Map<String, String> stringVariables()` |
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/MessageTemplateResolver.java` | Resolve `${key}` tokens from `stringVariables` map |
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRule.java` | Pass `context.stringVariables()` to resolver; set `affectedDefendants` for OFFENCE-level ERRORs with non-null `defendantId()` |
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java` | Add `yroShortCodes: List<String>` field |
| `src/test/java/uk/gov/hmcts/cp/services/rules/cel/MessageTemplateResolverTest.java` | Tests for string variable resolution |
| `gradle/libs.versions.toml` | Bump `api-cp-crime-hearing-results-validator` to `0.1.8` |

### Upstream library (separate PR — blocking)

| Repository | Change |
|------------|--------|
| `api-cp-crime-hearing-results-validator` | Add `type: String` and `childPrompts: List<Prompt>` to `Prompt.java`; release `0.1.8` |
