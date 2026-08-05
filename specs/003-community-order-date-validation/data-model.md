# Data Model: Community Order End Date Validation (DR-COEW-005)

**Branch**: `dev/DD-42678-co-end-date-rule`
**Date**: 2026-05-20 · **Updated**: 2026-08-05

---

## Existing Entities (unchanged)

### `DraftValidationRequest` (upstream library `0.1.6`)

| Field | Type | Notes |
|-------|------|-------|
| `hearingId` | `String` | Required |
| `hearingDay` | `LocalDate` | Date of the court hearing |
| `resultLines` | `List<ResultLineDto>` | Flat list of all result lines across all defendants/offences |
| `defendants` | `List<DefendantDto>` | For display name lookup and `masterDefendantId` grouping |
| `offences` | `List<OffenceDto>` | For offence display ordering |

### `ResultLineDto` (upstream library `0.1.6`)

| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | Result line identifier |
| `shortCode` | `String` | e.g. `COEW`, `CUR`, `AAR` |
| `defendantId` | `String` | Groups line to a defendant |
| `offenceId` | `String` | Groups line to an offence |
| `prompts` | `List<Prompt>` | Carries date values entered by clerk |
| `category` | `CategoryEnum` | `A`, `I`, or `F` — not used by this rule |
| `isConcurrent` | `Boolean` | Not used by this rule |
| `consecutiveToOffence` | `String` | Not used by this rule |

### `Prompt` (upstream library `0.1.6`)

| Field | Type | Notes |
|-------|------|-------|
| `promptRef` | `String` | Key identifying which prompt (e.g. `"endDate"`, `"endDateOfTag"`, `"until"`) |
| `promptValue` | `String` | Clerk-entered value as string (ISO-8601 date, e.g. `"2026-11-30"`) |

**Prompt ref keys used by DR-COEW-005:**

| Result short code | `promptRef` | Meaning |
|-------------------|-------------|---------|
| COEW, COS, CONI | `"endDate"` | Community order end date |
| CUR | `"endDate"` | Curfew requirement end date |
| CURE | `"endDateOfTag"` | Curfew with tag — end date of tag |
| CURA | `"endDate"` | Further curfew end date |
| AAR | `"until"` | Alcohol abstinence until date |

### `DefendantDto` (upstream library `0.1.6`)

| Field | Type | Notes |
|-------|------|-------|
| `defendantId` | `String` | Unique per defendant entry in the request |
| `masterDefendantId` | `String` | Optional — links two `defendantId`s that represent the same person across cases; consumed by `PreprocessorHelper.buildDefendantDedupeKeys()` |
| `firstName` / `lastName` | `String` | Used to build the display name for `${defendantNames}` |

---

## New / Shared Entities

### `PreprocessingDefinition` — new fields (modified existing class)

Five new `List<String>` fields, alongside the existing ones:

| New Field | YAML Key | Example values | Shared with |
|-----------|----------|-----------------|-------------|
| `communityOrderShortCodes` | `communityOrderShortCodes` | `[COEW, COS, CONI]` | community-order only |
| `curfewShortCodes` | `curfewShortCodes` | `[CUR]` | `YouthRehabilitationPreprocessor` |
| `curfewTagShortCodes` | `curfewTagShortCodes` | `[CURE]` | `YouthRehabilitationPreprocessor` |
| `furtherCurfewShortCodes` | `furtherCurfewShortCodes` | `[CURA]` | `YouthRehabilitationPreprocessor` |
| `alcoholAbstinenceShortCodes` | `alcoholAbstinenceShortCodes` | `[AAR]` | community-order only |

These fields are only populated when the rule's `preprocessing.type` requests them via its YAML block. Other preprocessors leave unused fields null/empty.

---

### `PreprocessorHelper` — new stateless utility class

Shared static helpers, used by both `CommunityOrderEndDatePreprocessor` and `YouthRehabilitationPreprocessor`:

| Method | Purpose |
|--------|---------|
| `upperSet(List<String>)` | Null-safe upper-case a short-code list into an immutable `Set<String>` |
| `upperOrNull(String)` | Upper-case a single value, or `null` |
| `hasUpperCode(ResultLineDto, Set<String>)` | True if the line's short code (case-insensitive) is in the set |
| `anyShortCodeIn(List<ResultLineDto>, Set<String>)` | True if any line's short code is in the set |
| `groupByDefendant(DraftValidationRequest)` | Groups all result lines by raw `defendantId`, preserving order |
| `buildDefendantDedupeKeys(DraftValidationRequest)` | Maps each `defendantId` → its dedupe key: `masterDefendantId` if present and non-blank, else the `defendantId` itself |
| `buildDefendantNames(DraftValidationRequest)` | Maps each `defendantId` → its display full name |
| `buildFullName(DefendantDto)` | Concatenates first/last name, tolerating either being null |
| `parsePromptDate(ResultLineDto, promptRef, offenceId)` | Parses the first matching prompt's value as `LocalDate`; logs `WARN` and returns `null` on missing/blank/unparseable |
| `isRequirementViolated(lines, codes, promptRef, orderEndDate, offenceId)` | True if any matching line's prompt date is strictly after `orderEndDate` |

---

### `CommunityOrderContext` — new record

Per-defendant-group context produced by `CommunityOrderEndDatePreprocessor`. The map key is the
defendant-group key from `PreprocessorHelper.buildDefendantDedupeKeys()` (either a shared
`masterDefendantId` or a standalone `defendantId`).

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
  └── allOffenceIds: List<String>            — all offence IDs for this defendant group
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

**Qualifier**: `"community-order-end-date"` (used as `preprocessing.type` in `DR-COEW-005.yaml`)

**Processing algorithm**:

```
Input: DraftValidationRequest request, PreprocessingDefinition config
Output: Map<String, CommunityOrderContext>  (key = defendant-group key)

1. Normalise all short-code sets from config to uppercase Sets (PreprocessorHelper.upperSet)
2. Build defendantId → dedupe-key map from request.getDefendants() (PreprocessorHelper.buildDefendantDedupeKeys)
3. Group all ResultLineDtos by raw defendantId (PreprocessorHelper.groupByDefendant),
   then fold groups sharing a dedupe key together into one line list per group key
4. Build group-key → display-name lookup (first non-"Unknown" name seen for the group)
5. For each (groupKey, lines) in the folded map:
   a. Skip groups with no community-order result lines (no COEW/COS/CONI) — PreprocessorHelper.hasUpperCode
   b. For each offence of this group (by offenceId):
      i.  Find the community order line for this offence (shortCode in communityOrderShortCodes)
          → parse order end date from prompts where promptRef = "endDate"
          → if none / date unparseable: skip this offence
      ii. For each requirement type, PreprocessorHelper.isRequirementViolated(...) checks whether
          any matching line's prompt date is strictly after the order end date:
          CUR  → "endDate"       → curViolationIds
          CURE → "endDateOfTag"  → cureViolationIds
          CURA → "endDate"       → curaViolationIds
          AAR  → "until"         → aarViolationIds
   c. Build CommunityOrderContext with accumulated counts and id sets
6. Return map (includes groups with all-zero counts; CEL conditions won't fire for them)
```

**Date comparison semantics**:
- AC2 (requirement end date check): violation when `requirementDate.isAfter(orderEndDate)` — equal is valid.

**Grouping semantics**:
- Two `defendantId`s that share a non-blank `masterDefendantId` are folded into a single
  `CommunityOrderContext` keyed by that `masterDefendantId`, mirroring
  `CustodialPreprocessor`'s and `YouthRehabilitationPreprocessor`'s master-defendant grouping.
  A `defendantId` with a blank/missing `masterDefendantId` falls back to its own `defendantId`
  as the group key.

---

## Response: `ValidationIssue` (emitted per triggered condition)

Each triggered YAML condition produces one `ValidationIssue` entry in the `errors` list of `DraftValidationResponse`. Fields relevant to this rule:

| Field | Type | Populated by |
|-------|------|-------------|
| `ruleId` | `String` | `"DR-COEW-005"` |
| `severity` | `SeverityEnum` | `ERROR` (ceiling can lower at runtime; DB seed ships `enabled: true`) |
| `message` | `String` | Expanded `messageTemplate` from YAML |
| `affectedOffences` | `List<AffectedOffence>` | `offenceDisplayHelper.buildAffectedOffences(affectedIds, offenceMap)` — scoped to the violation type's offence-id set |
| `affectedDefendants` | `List<AffectedDefendant>` | `[{ defendantId: "<group key>" }]` — the defendant (or `masterDefendantId` group) whose context triggered this condition |

The UI uses `affectedDefendants[].defendantId` to look up the defendant display name and render "This affects: <<name>>" in the error summary box.

---

## Rule YAML — `DR-COEW-005.yaml`

```yaml
rule:
  id: "DR-COEW-005"
  title: "Community Order End Date Validation"
  description: >-
    Validates that a community order's end date is not earlier than the end date of any
    attached requirement (AC2: CUR, CURE, CURA, AAR).
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
      errorMessageTemplate: >-
        The end date of the order must match or be longer than the end date of
        Curfew (community requirement). This affects ${defendantNames}.
      affectedOffenceSet: "curViolationOffenceIds"
      validationLevel: OFFENCE

    - id: "AC2b"
      name: "Curfew with tag requirement exceeds order end date"
      expression: "cureViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of Curfew with electronic monitoring
      errorMessageTemplate: >-
        The end date of the order must match or be longer than the end date of
        Curfew with electronic monitoring. This affects ${defendantNames}.
      affectedOffenceSet: "cureViolationOffenceIds"
      validationLevel: OFFENCE

    - id: "AC2c"
      name: "Further curfew requirement exceeds order end date"
      expression: "curaViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of Further curfew requirement made
      errorMessageTemplate: >-
        The end date of the order must match or be longer than the end date of
        Further curfew requirement made. This affects ${defendantNames}.
      affectedOffenceSet: "curaViolationOffenceIds"
      validationLevel: OFFENCE

    - id: "AC2d"
      name: "Alcohol abstinence requirement exceeds order end date"
      expression: "aarViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of Alcohol abstinence and monitoring
      errorMessageTemplate: >-
        The end date of the order must match or be longer than the end date of
        Alcohol abstinence and monitoring. This affects ${defendantNames}.
      affectedOffenceSet: "aarViolationOffenceIds"
      validationLevel: OFFENCE
```

---

## Migration — `V1.005__insert_dr_coew_005.sql`

```sql
INSERT INTO validation_rule (id, enabled, severity)
VALUES ('DR-COEW-005', true, 'ERROR');
```

This seeds the runtime override row so the rule is live by default (see spec.md A-011 and research.md Decision 8).

---

## Relationships

```
DraftValidationRequest
  ├── resultLines: List<ResultLineDto>
  │     └── prompts: List<Prompt>
  │           ├── promptRef / promptValue ─── parsed by PreprocessorHelper.parsePromptDate()
  └── defendants: List<DefendantDto> ────── display name + masterDefendantId dedupe-key lookup

CommunityOrderEndDatePreprocessor
  ├── reads: DraftValidationRequest (resultLines + defendants)
  ├── config: PreprocessingDefinition (5 short-code list fields, 3 shared with YRO)
  ├── delegates to: PreprocessorHelper (grouping, dedupe keys, matching, date parsing)
  └── produces: Map<groupKey, CommunityOrderContext>

CommunityOrderContext  (one per defendant group with ≥1 community order)
  ├── 4 violation counts  ──→ CEL variable map (toCelContext())
  ├── 4 violation offence-id sets ──→ getOffenceIdSet(name) per YAML condition
  └── allOffenceIds ──→ message template resolver ordering

DR-COEW-005.yaml
  └── 4 conditions → CelValidationRule → ValidationIssue (ERROR) per triggered condition
        ├── affectedOffences  ── scoped to violation offence-id set
        └── affectedDefendants ── [{ defendantId: groupKey }] for the triggering group
```
