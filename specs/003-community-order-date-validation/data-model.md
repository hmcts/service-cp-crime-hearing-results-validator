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
