# Data Model: Community Order End Date Validation (DR-COEW-001)

**Branch**: `DD-41653-community-order-date-validation`  
**Date**: 2026-05-20

---

## Existing Entities (unchanged)

### `DraftValidationRequest` (upstream library `0.1.6`)

| Field | Type | Notes |
|-------|------|-------|
| `hearingId` | `String` | Required |
| `hearingDay` | `LocalDate` | Date of the court hearing |
| `resultLines` | `List<ResultLineDto>` | Flat list of all result lines across all defendants/offences |
| `defendants` | `List<DefendantDto>` | For display name lookup |
| `offences` | `List<OffenceDto>` | For offence display ordering |

### `ResultLineDto` (upstream library `0.1.6`)

| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | Result line identifier |
| `shortCode` | `String` | e.g. `COEW`, `CUR`, `AAR` |
| `defendantId` | `String` | Groups line to a defendant |
| `offenceId` | `String` | Groups line to an offence |
| `prompts` | `List<Prompt>` | **NEW in 0.1.6** — carries date values entered by clerk |
| `category` | `CategoryEnum` | `A`, `I`, or `F` — not used by this rule |
| `isConcurrent` | `Boolean` | Not used by this rule |
| `consecutiveToOffence` | `String` | Not used by this rule |

### `Prompt` (upstream library `0.1.6` — new standalone model)

| Field | Type | Notes |
|-------|------|-------|
| `promptRef` | `String` | Key identifying which prompt (e.g. `"endDate"`, `"endDateOfTag"`, `"until"`) |
| `promptValue` | `String` | Clerk-entered value as string (ISO-8601 date, e.g. `"2026-11-30"`) |

**Prompt ref keys used by DR-COEW-001:**

| Result short code | `promptRef` | Meaning |
|-------------------|-------------|---------|
| COEW, COS, CONI | `"endDate"` | Community order end date |
| CUR | `"endDate"` | Curfew requirement end date |
| CURE | `"endDateOfTagging"` | Curfew with tag — end date of tag |
| CURA | `"endDate"` | Further curfew end date |
| AAR | `"until"` | Alcohol abstinence until date |

---

## New Entities

### `PreprocessingDefinition` — new fields (modified existing class)

Five new `List<String>` fields added alongside the existing ones:

| New Field | YAML Key | Example values |
|-----------|----------|----------------|
| `communityOrderShortCodes` | `communityOrderShortCodes` | `[COEW, COS, CONI]` |
| `curfewShortCodes` | `curfewShortCodes` | `[CUR]` |
| `curfewTagShortCodes` | `curfewTagShortCodes` | `[CURE]` |
| `furtherCurfewShortCodes` | `furtherCurfewShortCodes` | `[CURA]` |
| `alcoholAbstinenceShortCodes` | `alcoholAbstinenceShortCodes` | `[AAR]` |

These fields are only populated when `preprocessing.type = "community-order-end-date"`. Other preprocessors leave them null/empty.

---

### `CommunityOrderContext` — new record

Per-defendant context produced by `CommunityOrderEndDatePreprocessor`.

```
CommunityOrderContext
  ├── defendantName: String           — display name for ${defendantName} in message templates
  ├── curViolationCount: long         — offences where CUR end date > order end date
  ├── cureViolationCount: long        — offences where CURE end date of tag > order end date
  ├── curaViolationCount: long        — offences where CURA end date > order end date
  ├── aarViolationCount: long         — offences where AAR until date > order end date
  ├── curViolationOffenceIds: List<String>   — offence IDs for AC2a (CUR) violations
  ├── cureViolationOffenceIds: List<String>  — offence IDs for AC2b (CURE) violations
  ├── curaViolationOffenceIds: List<String>  — offence IDs for AC2c (CURA) violations
  ├── aarViolationOffenceIds: List<String>   — offence IDs for AC2d (AAR) violations
  └── allOffenceIds: List<String>            — all offence IDs for this defendant
```

**CEL variable map** (`toCelContext()`):

| CEL variable | Type | YAML condition |
|---|---|---|
| `curViolationCount` | `Long` | AC2a: `curViolationCount > 0` |
| `cureViolationCount` | `Long` | AC2b: `cureViolationCount > 0` |
| `curaViolationCount` | `Long` | AC2c: `curaViolationCount > 0` |
| `aarViolationCount` | `Long` | AC2d: `aarViolationCount > 0` |

**Named offence-id sets** (`getOffenceIdSet(setName)`):

| `setName` | Returns |
|-----------|---------|
| `"curViolationOffenceIds"` | `curViolationOffenceIds` |
| `"cureViolationOffenceIds"` | `cureViolationOffenceIds` |
| `"curaViolationOffenceIds"` | `curaViolationOffenceIds` |
| `"aarViolationOffenceIds"` | `aarViolationOffenceIds` |
| `"allOffenceIds"` | `allOffenceIds` |

---

### `CommunityOrderEndDatePreprocessor` — new Spring component

**Qualifier**: `"community-order-end-date"` (used as `preprocessing.type` in `DR-COEW-001.yaml`)

**Processing algorithm**:

```
Input: DraftValidationRequest request, PreprocessingDefinition config
Output: Map<String, CommunityOrderContext>  (key = defendantId)

1. Normalise all short-code sets from config to uppercase Sets
2. Group all ResultLineDtos by defendantId → Map<defendantId, List<ResultLineDto>>
3. Build defendantId → DefendantDto lookup from request.getDefendants()
4. For each (defendantId, lines) in grouped map:
   a. Skip defendants with no community-order result lines (no COEW/COS/CONI)
   b. For each offence of this defendant (by offenceId):
      i.  Find community order line for this offence (shortCode in communityOrderShortCodes)
          → parse order end date from prompts where promptRef = "endDate"
          → if none / date unparseable: skip this offence
      ii. Find requirement lines for this offence:
          CUR lines  → parse "endDate" prompt   → if > orderEndDate: add offenceId to curViolationIds
          CURE lines → parse "endDateOfTag"      → if > orderEndDate: add to cureViolationIds
          CURA lines → parse "endDate"           → if > orderEndDate: add to curaViolationIds
          AAR lines  → parse "until"             → if > orderEndDate: add to aarViolationIds
   c. Build CommunityOrderContext with accumulated counts and id sets
5. Return map (includes defendants with all-zero counts; CEL conditions won't fire for them)
```

**Date comparison semantics**:
- AC2 (requirement end date check): violation when `requirementDate.isAfter(orderEndDate)` — equal is valid.

---

## Response: `ValidationIssue` (emitted per triggered condition)

Each triggered YAML condition produces one `ValidationIssue` entry in the `errors` list of `DraftValidationResponse`. Fields relevant to this rule:

| Field | Type | Populated by |
|-------|------|-------------|
| `ruleId` | `String` | `"DR-COEW-001"` |
| `severity` | `SeverityEnum` | `ERROR` (ceiling can lower at runtime) |
| `message` | `String` | Expanded `messageTemplate` from YAML |
| `affectedOffences` | `List<AffectedOffence>` | `offenceDisplayHelper.buildAffectedOffences(affectedIds, offenceMap)` — scoped to the violation type's offence-id set |
| `affectedDefendants` | `List<AffectedDefendant>` | `[{ defendantId: "<id>" }]` — the defendant whose context triggered this condition; populated via framework change to `CelValidationRule` (previously null for all rules; now consistently set) |

The UI uses `affectedDefendants[].defendantId` to look up the defendant display name and render "This affects: <<name>>" in the error summary box.

---

## Rule YAML — `DR-COEW-001.yaml`

```yaml
rule:
  id: "DR-COEW-001"
  title: "Community Order End Date Validation"
  priority: 4000
  enabled: true

  preprocessing:
    type: "community-order-end-date"
    communityOrderShortCodes: [COEW, COS, CONI]
    curfewShortCodes:          [CUR]
    curfewTagShortCodes:       [CURE]
    furtherCurfewShortCodes:   [CURA]
    alcoholAbstinenceShortCodes: [AAR]

  conditions:
    - id: "AC2a"
      name: "Curfew requirement exceeds order end date"
      expression: "curViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of Curfew (community requirement)
      affectedOffenceSet: "curViolationOffenceIds"

    - id: "AC2b"
      name: "Curfew with tag requirement exceeds order end date"
      expression: "cureViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of Curfew with electronic monitoring
      affectedOffenceSet: "cureViolationOffenceIds"

    - id: "AC2c"
      name: "Further curfew requirement exceeds order end date"
      expression: "curaViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of Further curfew requirement made
      affectedOffenceSet: "curaViolationOffenceIds"

    - id: "AC2d"
      name: "Alcohol abstinence requirement exceeds order end date"
      expression: "aarViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of Alcohol abstinence and monitoring
      affectedOffenceSet: "aarViolationOffenceIds"
```

---

## Extension (2026-07-06, Jira `DD-41655`) — Requirement Duration End Date Validation

Adds three new conditions to `DR-COEW-001` (CUR, CURE, AAR only — `CURA` excluded, see
research.md Decision 12) checking a requirement's own recorded end date against its calculated
duration, independent of the AC2 order-end-date comparison above.

### New prompt ref keys (hardcoded — unverified, see research.md Decision 10)

| Result short code | New `promptRef` | Meaning |
|---|---|---|
| CUR | `"startDate"` | Start date of the curfew requirement |
| CUR | `"curfewPeriod"` | Curfew period, integer days |
| CURE | `"startDateOfTagging"` | Start date of tagging |
| CURE | `"curfewAndElectronicMonitoringPeriod"` | Curfew and electronic monitoring period, integer days |
| AAR | `"numberOfDaysToAbstain"` | Number of days to abstain from consuming any alcohol |

AAR's start point is `DraftValidationRequest.hearingDay` (already modelled, no new prompt needed).

### `CommunityOrderContext` — new fields (extends existing record)

```
CommunityOrderContext  (additions)
  ├── curDurationMismatchCount: long             — CUR end date != Start date + period − 1
  ├── cureDurationMismatchCount: long            — CURE end date of tagging != Start date of tagging + period − 1
  ├── aarDurationMismatchCount: long             — AAR until != hearing date + days to abstain − 1
  ├── curDurationMismatchOffenceIds: List<String>
  ├── cureDurationMismatchOffenceIds: List<String>
  ├── aarDurationMismatchOffenceIds: List<String>
  ├── curCalculatedEndDateByOffenceId: Map<String, String>   — offenceId → ISO-8601 correct end date
  ├── cureCalculatedEndDateByOffenceId: Map<String, String>
  └── aarCalculatedEndDateByOffenceId: Map<String, String>
```

`toCelContext()` gains three new entries: `curDurationMismatchCount`, `cureDurationMismatchCount`,
`aarDurationMismatchCount` (all `Long`). `getOffenceIdSet(setName)` gains three new cases:
`"curDurationMismatchOffenceIds"`, `"cureDurationMismatchOffenceIds"`, `"aarDurationMismatchOffenceIds"`.

**New**: `getCalculatedValue(setName, offenceId)` (overrides a new `RuleEvaluationContext` default
method) — switches on `setName` across the three `*CalculatedEndDateByOffenceId` maps and returns
`map.get(offenceId)` (or `null` if absent, meaning the template resolver leaves the `${calculatedEndDate}`
token unexpanded — this should not happen if the offence is present in the corresponding
`*DurationMismatchOffenceIds` list, which it always is by construction).

### `CommunityOrderEndDatePreprocessor` — algorithm addition

The existing per-offence loop currently does `if (orderEndDate == null) continue;` before running
any check — this early-exit must move so it **only** gates the pre-existing AC2a–d order-vs-requirement
checks. The new duration checks do not need the order's own end date at all (CUR/CURE compare
against their own Start date; AAR compares against the hearing date) and must run even when the
community order's end date prompt is missing/unparseable.

```
Additional per-offence steps (independent of orderEndDate):

CUR line present:
  startDate   = parsePromptDate(line, "startDate")
  period      = parsePromptInt(line, "curfewPeriod")
  endDate     = parsePromptDate(line, "endDate")             # already parsed for AC2a
  if startDate, period, endDate all present:
      expected = startDate.plusDays(period - 1)
      if !endDate.isEqual(expected):
          add offenceId to curDurationMismatchOffenceIds
          curCalculatedEndDateByOffenceId[offenceId] = expected.toString()

CURE line present: same shape, using "startDateOfTagging" / "curfewAndElectronicMonitoringPeriod" / "endDateOfTagging"

AAR line present: same shape, using requestHearingDay (parsed once, outer scope) /
  "numberOfDaysToAbstain" / "until"
```

`parsePromptInt` is a new helper alongside the existing `parsePromptDate`, with the same
defensive null/blank/unparseable → log `WARN` and skip semantics as Decision 6.

### `ConditionDefinition` — new field

| New Field | YAML Key | Purpose |
|-----------|----------|---------|
| `calculatedValueSet` | `calculatedValueSet` | Names which `*CalculatedEndDateByOffenceId` map to look up when resolving this condition's `${calculatedEndDate}` token (`null` for conditions that don't use it) |

### `RuleEvaluationContext` — new default method

```java
default String getCalculatedValue(String setName, String offenceId) {
    throw new IllegalArgumentException("Unknown calculated-value set: " + setName);
}
```

Mirrors `getOffenceIdSet` — contexts that don't support computed values inherit the throwing
default; `CommunityOrderContext` overrides it.

### `MessageTemplateResolver` — new overload

```java
public String resolve(String template, String defendantName, List<String> affectedOffenceIds,
                       Map<String, OffenceDto> offenceMap, List<String> allOffenceIds,
                       Map<String, String> extraPlaceholders) {
    String result = resolve(template, defendantName, affectedOffenceIds, offenceMap, allOffenceIds);
    for (Map.Entry<String, String> entry : extraPlaceholders.entrySet()) {
        result = result.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    return result;
}
```

The existing 5-arg `resolve(...)` is unchanged; all current call sites are unaffected.

### `CelValidationRule` — wiring

In the OFFENCE-level (`isDefendantLevel == false`) branch, the per-offence message lambda passes an
extra-placeholder map when `condition.getCalculatedValueSet() != null`:

```java
id -> {
    Map<String, String> extra = condition.getCalculatedValueSet() != null
        ? Map.of("calculatedEndDate", context.getCalculatedValue(condition.getCalculatedValueSet(), id))
        : Map.of();
    return messageResolver.resolve(condition.getMessageTemplate(), context.defendantName(),
        List.of(id), offenceMap, context.allOffenceIds(), extra);
}
```

The `errorMessageTemplate` resolve call (top-of-screen summary) is **not** changed — it has no
per-offence `id` in scope, only the aggregate `offenceIdsForTemplate` list.

### Rule YAML — new conditions appended to `DR-COEW-001.yaml`

```yaml
    - id: "DUR-CUR"
      name: "Curfew requirement end date does not match calculated duration"
      expression: "curDurationMismatchCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date for the Curfew Requirement does not match the period of the requirement.
        The current recorded period would mean the end date should be ${calculatedEndDate}.
      errorMessageTemplate: >-
        The end date for the Curfew Requirement does not match the period of the requirement.
        This affects ${defendantNames}.
      affectedOffenceSet: "curDurationMismatchOffenceIds"
      calculatedValueSet: "curCalculatedEndDateByOffenceId"
      validationLevel: OFFENCE

    - id: "DUR-CURE"
      name: "Curfew with electronic monitoring end date of tagging does not match calculated duration"
      expression: "cureDurationMismatchCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date for the Curfew Requirement does not match the period of the requirement.
        The current recorded period would mean the end date should be ${calculatedEndDate}.
      errorMessageTemplate: >-
        The end date for the Curfew Requirement does not match the period of the requirement.
        This affects ${defendantNames}.
      affectedOffenceSet: "cureDurationMismatchOffenceIds"
      calculatedValueSet: "cureCalculatedEndDateByOffenceId"
      validationLevel: OFFENCE

    - id: "DUR-AAR"
      name: "Alcohol abstinence and monitoring until date does not match calculated duration"
      expression: "aarDurationMismatchCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date for the Alcohol Abstinence Monitoring Requirement does not match the period
        of the requirement. The current recorded period would mean the end date should be
        ${calculatedEndDate}.
      errorMessageTemplate: >-
        The end date for the Alcohol Abstinence Monitoring Requirement does not match the period
        of the requirement. This affects ${defendantNames}.
      affectedOffenceSet: "aarDurationMismatchOffenceIds"
      calculatedValueSet: "aarCalculatedEndDateByOffenceId"
      validationLevel: OFFENCE
```

No changes to `preprocessing:` block — `curfewShortCodes`, `curfewTagShortCodes`, and
`alcoholAbstinenceShortCodes` are reused as-is; `communityOrderShortCodes` and
`furtherCurfewShortCodes` are untouched (`CURA` excluded, see Decision 12).

---

## Relationships

```
DraftValidationRequest
  ├── resultLines: List<ResultLineDto>
  │     └── prompts: List<Prompt>
  │           ├── promptRef / promptValue ─── parsed by preprocessor for dates
  └── defendants: List<DefendantDto> ────── display name lookup

CommunityOrderEndDatePreprocessor
  ├── reads: DraftValidationRequest (resultLines + defendants)
  ├── config: PreprocessingDefinition (5 new short-code list fields)
  └── produces: Map<defendantId, CommunityOrderContext>

CommunityOrderContext  (one per defendant with ≥1 community order)
  ├── 4 violation counts  ──→ CEL variable map (toCelContext())
  ├── 4 violation offence-id sets ──→ getOffenceIdSet(name) per YAML condition
  └── allOffenceIds ──→ message template resolver ordering

DR-COEW-001.yaml
  └── 4 conditions → CelValidationRule → ValidationIssue (ERROR) per triggered condition
        ├── affectedOffences  ── scoped to violation offence-id set
        └── affectedDefendants ── [{ defendantId }] for the triggering defendant
```
