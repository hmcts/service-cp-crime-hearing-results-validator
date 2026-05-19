# Data Model: Community Order End Date Validation

**Branch**: `DD-41653-co-end-date-validation` | **Date**: 2026-05-12  
**Feature**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## New Entities

### CommunityOrderContext (Java record)

**Package**: `uk.gov.hmcts.cp.services.rules.cel`  
**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderContext.java`

One context instance is created per `(defendantId, offenceId)` pair that contains a community order result line (COEW/COS/CONI) with a non-null `endDate`.

```
CommunityOrderContext
├── defendantName: String          — "FirstName LastName" for ${defendantName} placeholder
├── curViolationCount: long        — 1 if any CUR.endDate > orderEndDate, else 0
├── cureViolationCount: long       — 1 if any CURE.endDate > orderEndDate, else 0
├── curaViolationCount: long       — 1 if any CURA.endDate > orderEndDate, else 0
├── aarViolationCount: long        — 1 if any AAR.endDate > orderEndDate, else 0
├── upwrViolationCount: long       — 1 if UPWR present AND orderEndDate.isBefore(hearingDate.plusMonths(12)), else 0
└── allOffenceIds: List<String>    — singleton list containing the one offenceId
```

**toCelContext()** returns:
```
{
  "curViolationCount":  <0 or 1>,
  "cureViolationCount": <0 or 1>,
  "curaViolationCount": <0 or 1>,
  "aarViolationCount":  <0 or 1>,
  "upwrViolationCount": <0 or 1>
}
```

**getOffenceIdSet(name)**:
- `"allOffenceIds"` → returns `allOffenceIds` (the singleton list)
- Any other name → throws `IllegalArgumentException`

---

## Modified Entities

### PreprocessingDefinition (existing class — additive change only)

**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`

Add six new nullable `List<String>` fields. No existing fields are changed; existing rules continue to work with null values for the new fields.

| New field | Used by | Purpose |
|-----------|---------|---------|
| `communityOrderShortCodes` | `CommunityOrderEndDatePreprocessor` | Identifies parent order result lines |
| `curfewShortCodes` | `CommunityOrderEndDatePreprocessor` | Identifies CUR requirements |
| `curfewTagShortCodes` | `CommunityOrderEndDatePreprocessor` | Identifies CURE requirements |
| `furtherCurfewShortCodes` | `CommunityOrderEndDatePreprocessor` | Identifies CURA requirements |
| `alcoholAbstinenceShortCodes` | `CommunityOrderEndDatePreprocessor` | Identifies AAR requirements |
| `unpaidWorkShortCodes` | `CommunityOrderEndDatePreprocessor` | Identifies UPWR requirements |

---

## New Components

### CommunityOrderEndDatePreprocessor

**Package**: `uk.gov.hmcts.cp.services.rules.cel`  
**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CommunityOrderEndDatePreprocessor.java`  
**Qualifier / type()**: `"community-order-end-date"`

**Algorithm**:

```
Input: DraftValidationRequest request, PreprocessingDefinition config

1. Build defendantMap: Map<String, DefendantDto> from request.getDefendants()

2. Build resultLinesByOffenceAndDefendant:
   Map<String, List<ResultLineDto>> where key = defendantId + "_" + offenceId

3. For each entry in resultLinesByOffenceAndDefendant:
   a. Find the community order result line:
      orderLine = lines where shortCode ∈ config.communityOrderShortCodes
                  AND endDate != null
   b. If no orderLine found → skip (no community order on this offence/defendant)
   c. hearingDate = request.getHearingDay()
   d. Compute counts:
      curViolationCount  = lines(CUR) with endDate != null AND endDate > orderLine.endDate → 1 : 0
      cureViolationCount = lines(CURE) with endDate != null AND endDate > orderLine.endDate → 1 : 0
      curaViolationCount = lines(CURA) with endDate != null AND endDate > orderLine.endDate → 1 : 0
      aarViolationCount  = lines(AAR) with endDate != null AND endDate > orderLine.endDate → 1 : 0
      upwrViolationCount = lines(UPWR) exists ? (orderLine.endDate.isBefore(hearingDate.plusMonths(12)) ? 1 : 0) : 0
   e. Resolve defendantName from defendantMap (firstName + " " + lastName)
   f. Create CommunityOrderContext(defendantName, curViolation…, allOffenceIds=[offenceId])
   g. Add to result map with key = defendantId + "_" + offenceId

4. Return: Map<String, CommunityOrderContext>
```

**Edge cases**:
- Multiple COEW result lines on the same offence/defendant: use the first one found (rare edge case; first encountered in stream order).
- `endDate` null on order line: skip (no context generated).
- `endDate` null on requirement line: exclude from comparison (treated as "no date set").
- No requirements on the offence: all violation counts remain 0; AC2/AC3 conditions do not fire.

---

### DR-COEW-001.yaml (new YAML rule)

**File**: `src/main/resources/rules/DR-COEW-001.yaml`

```yaml
rule:
  id: "DR-COEW-001"
  title: "Community Order End Date Validation"
  description: >-
    Validates that the end date of a community order (COEW, COS, CONI) is in the
    future, is not earlier than any attached requirement's end date (CUR, CURE, CURA,
    AAR), and is at least 12 months from the hearing date when an Unpaid Work
    requirement (UPWR) is present.
  priority: 4000
  enabled: true
  preprocessing:
    type: "community-order-end-date"
    communityOrderShortCodes:
      - COEW
      - COS
      - CONI
    curfewShortCodes:
      - CUR
    curfewTagShortCodes:
      - CURE
    furtherCurfewShortCodes:
      - CURA
    alcoholAbstinenceShortCodes:
      - AAR
    unpaidWorkShortCodes:
      - UPWR
  conditions:
    - id: "AC2a"
      name: "Order end date before CUR end date"
      expression: "curViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of
        Curfew (community requirement) - CUR
      affectedOffenceSet: "allOffenceIds"
    - id: "AC2b"
      name: "Order end date before CURE end date of tag"
      expression: "cureViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of
        Curfew with electronic monitoring - CURE
      affectedOffenceSet: "allOffenceIds"
    - id: "AC2c"
      name: "Order end date before CURA end date"
      expression: "curaViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of
        Further curfew requirement made - CURA
      affectedOffenceSet: "allOffenceIds"
    - id: "AC2d"
      name: "Order end date before AAR until date"
      expression: "aarViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must match or be longer than the end date of
        Alcohol abstinence and monitoring - AAR
      affectedOffenceSet: "allOffenceIds"
    - id: "AC3"
      name: "UPWR order end date less than 12 months from hearing"
      expression: "upwrViolationCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date of the order must be at least 12 months as it includes an
        unpaid work requirement
      affectedOffenceSet: "allOffenceIds"
```

---

## No External Contract Changes

This feature adds a new YAML rule and preprocessor. It does NOT change:
- The `ValidationController` API (no new endpoints)
- The `DraftValidationResponse` or `ValidationIssue` structure
- The request DTO (`DraftValidationRequest`, `ResultLineDto`) — all required fields already exist

The upstream `api-cp-crime-hearing-results-validator` dependency requires **no changes**.

---

## State Transitions

| Input State | Trigger | Resulting ValidationIssue |
|-------------|---------|--------------------------|
| COEW with endDate < CUR.endDate on same offence | AC2a | ERROR: "…end date of Curfew (community requirement) - CUR" |
| COEW with endDate < CURE.endDate on same offence | AC2b | ERROR: "…end date of Curfew with electronic monitoring - CURE" |
| COEW with endDate < CURA.endDate on same offence | AC2c | ERROR: "…end date of Further curfew requirement made - CURA" |
| COEW with endDate < AAR.endDate on same offence | AC2d | ERROR: "…end date of Alcohol abstinence and monitoring - AAR" |
| COEW + UPWR on same offence AND endDate < hearingDate.plusMonths(12) | AC3 | ERROR: "…at least 12 months…unpaid work requirement" |
| All constraints satisfied | — | No ValidationIssue from DR-COEW-001 |
