# Data Model: Pre-Release Bail Condition Error (DR-BAIL-015)

## New Java Types

### `PreReleaseBailContext` (record)

**Package**: `uk.gov.hmcts.cp.services.rules.cel`
**Implements**: `RuleEvaluationContext`

One context is emitted per **triggered offence** — an offence where:
1. At least one result line has a shortCode in the conditional-bail-parent set, AND
2. At least one result line has a shortCode in the pre-release-bail-condition set.

```
PreReleaseBailContext(
    String  offenceId           // the triggered offence id (context key)
    String  defendantId         // from the result line's defendantId
    String  defendantName       // firstName + lastName from DefendantDto
    long    isRemandBail        // 1 if parent short code is in remandConditionalBailShortCodes
    long    isCcic              // 1 if parent short code is CCIC
    long    isCcsc              // 1 if parent short code is CCSC
    long    isAdult             // 1 if defendant is ≥18 on hearingDay
    long    isYouth             // 1 if defendant is <18 on hearingDay
    long    hasDob              // 1 if defendantDateOfBirth is non-null
    long    hearingHasRemandAdult // hearing-wide: any triggered offence has remandBail parent + adult defendant
    long    hearingHasRemandYouth // hearing-wide: any triggered offence has remandBail parent + youth defendant
)
```

**`toCelContext()`** returns:
```
{
  "isRemandBail":          isRemandBail,
  "isCcic":                isCcic,
  "isCcsc":                isCcsc,
  "isAdult":               isAdult,
  "isYouth":               isYouth,
  "hasDob":                hasDob,
  "hearingHasRemandAdult": hearingHasRemandAdult,
  "hearingHasRemandYouth": hearingHasRemandYouth
}
```

**`getOffenceIdSet("triggeredOffenceId")`** returns `List.of(offenceId)`.

**`defendantId()`** returns `defendantId`.

**`defendantName()`** returns `defendantName`.

**`allOffenceIds()`** returns `List.of(offenceId)`.

---

### `PreReleaseBailConditionPreprocessor` (component)

**Package**: `uk.gov.hmcts.cp.services.rules.cel`
**Implements**: `ValidationPreprocessor`
**Qualifier**: `"pre-release-bail-condition"`

**Algorithm**:

```
PASS 1 — collect triggered offences
  parentCodesByOffence = Map<offenceId, String> (first matching parent code)
  preReleaseByOffence  = Set<offenceId>          (offences with pre-release child)

  for each ResultLineDto rl in request.resultLines:
    upper = rl.shortCode.toUpperCase()
    if upper in (remandCodes ∪ ccicCodes ∪ ccscCodes):
      parentCodesByOffence.putIfAbsent(rl.offenceId, upper)
    if upper in preReleaseCodes:
      preReleaseByOffence.add(rl.offenceId)

  triggeredOffences = parentCodesByOffence.keySet() ∩ preReleaseByOffence
  if triggeredOffences.isEmpty: return Map.of()

  defendantIdByOffence  = Map<offenceId, String>  (from result lines)
  defendantDobMap       = Map<defendantId, LocalDate>
  defendantNameMap      = Map<defendantId, String>
  (build from request.defendants)

PASS 2 — compute hearing-wide flags
  hearingHasRemandAdult = false
  hearingHasRemandYouth = false

  for each offenceId in triggeredOffences:
    parentCode = parentCodesByOffence.get(offenceId)
    if parentCode in remandCodes:
      defendantId = defendantIdByOffence.get(offenceId)
      dob = defendantDobMap.get(defendantId)
      if dob != null:
        if isAdult(dob, hearingDay):  hearingHasRemandAdult = true
        else:                          hearingHasRemandYouth = true

PASS 3 — emit one PreReleaseBailContext per triggered offence
  for each offenceId in triggeredOffences:
    parentCode   = parentCodesByOffence.get(offenceId)
    defendantId  = defendantIdByOffence.get(offenceId)
    dob          = defendantDobMap.get(defendantId)
    hasDob       = dob != null ? 1L : 0L
    isAdult      = hasDob == 1 && isAdult(dob, hearingDay) ? 1L : 0L
    isYouth      = hasDob == 1 && !isAdult(dob, hearingDay) ? 1L : 0L
    isRemandBail = parentCode in remandCodes ? 1L : 0L
    isCcic       = parentCode in ccicCodes ? 1L : 0L
    isCcsc       = parentCode in ccscCodes ? 1L : 0L
    result.put(offenceId, new PreReleaseBailContext(...))
  return result
```

**Helper**: `isAdult(dob, hearingDay)` — true when `dob != null && hearingDay != null && Period.between(dob, hearingDay).getYears() >= 18`.

---

### `PreprocessingDefinition` — new fields

Four fields appended to the existing record (following the established extension pattern):

```java
// PreReleaseBailConditionPreprocessor: remand-type conditional bail parent short codes
List<String> remandConditionalBailShortCodes,

// PreReleaseBailConditionPreprocessor: CCIC parent short code(s)
List<String> ccicShortCodes,

// PreReleaseBailConditionPreprocessor: CCSC parent short code(s)
List<String> ccscShortCodes,

// PreReleaseBailConditionPreprocessor: pre-release bail condition child short codes
List<String> preReleaseBailConditionShortCodes
```

---

## YAML Rule: `DR-BAIL-015.yaml`

**Location**: `src/main/resources/rules/DR-BAIL-015.yaml`

```yaml
rule:
  id: "DR-BAIL-015"
  title: "Pre-release bail conditions on a conditional bail result"
  description: >-
    Blocks sharing when a conditional bail parent result has one or more pre-release
    bail condition results as child results on the same offence. Pre-release conditions
    are only valid under a remand in custody with a bail direction; attaching them to a
    conditional bail result is contradictory.
  priority: 15000
  enabled: true

  preprocessing:
    type: "pre-release-bail-condition"
    remandConditionalBailShortCodes:
      - RC
      - RCBV
      - BAILREVC
      - REMCB
      - REMCBY
      - RCCCB
    ccicShortCodes:
      - CCIC
    ccscShortCodes:
      - CCSC
    preReleaseBailConditionShortCodes:
      - PREBC
      - PRBCO
      - PRBCP1
      - PRBCP2
      - PRBCP3
      - PRBCP4
      - PRBCP5
      - PRBCP6
      - PRBCP7
      - PRBCP8
      - PRBCS1
      - PRBCS2
      - PRBCS3
      - PRBCSE1
      - PRBCSE2
      - PRBCT

  conditions:
    - id: "AC_ADULT"
      name: "Adult defendant — remand on conditional bail with pre-release conditions (adult-only hearing)"
      expression: "isRemandBail == 1 && isAdult == 1 && hasDob == 1 && hearingHasRemandYouth == 0"
      severity: ERROR
      messageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Remanded in custody with bail direction (RIB)'
        instead. If the conditions apply after release, remove the pre-release conditions
        and enter them as bail conditions.
      errorMessageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Remanded in custody with bail direction (RIB)'
        instead. If the conditions apply after release, remove the pre-release conditions
        and enter them as bail conditions.
      affectedOffenceSet: "triggeredOffenceId"
      validationLevel: OFFENCE

    - id: "AC_YOUTH"
      name: "Youth defendant — remand on conditional bail with pre-release conditions (youth-only hearing)"
      expression: "isRemandBail == 1 && isYouth == 1 && hasDob == 1 && hearingHasRemandAdult == 0"
      severity: ERROR
      messageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Remand in care of Local Authority with bail
        direction (RILAB)' instead. If the conditions apply after release, remove the
        pre-release conditions and enter them as bail conditions.
      errorMessageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Remand in care of Local Authority with bail
        direction (RILAB)' instead. If the conditions apply after release, remove the
        pre-release conditions and enter them as bail conditions.
      affectedOffenceSet: "triggeredOffenceId"
      validationLevel: OFFENCE

    - id: "AC_MIXED_ADULT"
      name: "Adult defendant — remand on conditional bail with pre-release conditions (mixed adult+youth hearing, inline)"
      expression: "isRemandBail == 1 && isAdult == 1 && hasDob == 1 && hearingHasRemandYouth == 1"
      severity: ERROR
      messageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Remanded in custody with bail direction (RIB)'
        instead. If the conditions apply after release, remove the pre-release conditions
        and enter them as bail conditions.
      errorMessageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Remanded in custody with bail direction (RIB)'
        for an adult or 'Remand in care of Local Authority with bail direction (RILAB)' for
        a youth instead. If the conditions apply after release, remove the pre-release
        conditions and enter them as bail conditions.
      affectedOffenceSet: "triggeredOffenceId"
      validationLevel: OFFENCE

    - id: "AC_MIXED_YOUTH"
      name: "Youth defendant — remand on conditional bail with pre-release conditions (mixed adult+youth hearing, inline)"
      expression: "isRemandBail == 1 && isYouth == 1 && hasDob == 1 && hearingHasRemandAdult == 1"
      severity: ERROR
      messageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Remand in care of Local Authority with bail
        direction (RILAB)' instead. If the conditions apply after release, remove the
        pre-release conditions and enter them as bail conditions.
      errorMessageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Remanded in custody with bail direction (RIB)'
        for an adult or 'Remand in care of Local Authority with bail direction (RILAB)' for
        a youth instead. If the conditions apply after release, remove the pre-release
        conditions and enter them as bail conditions.
      affectedOffenceSet: "triggeredOffenceId"
      validationLevel: OFFENCE

    - id: "AC_NODOB"
      name: "Unknown-age defendant — remand on conditional bail with pre-release conditions (no date of birth)"
      expression: "isRemandBail == 1 && hasDob == 0"
      severity: ERROR
      messageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Remanded in custody with bail direction (RIB)'
        for an adult or 'Remand in care of Local Authority with bail direction (RILAB)' for
        a youth instead. If the conditions apply after release, remove the pre-release
        conditions and enter them as bail conditions.
      errorMessageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Remanded in custody with bail direction (RIB)'
        for an adult or 'Remand in care of Local Authority with bail direction (RILAB)' for
        a youth instead. If the conditions apply after release, remove the pre-release
        conditions and enter them as bail conditions.
      affectedOffenceSet: "triggeredOffenceId"
      validationLevel: OFFENCE

    - id: "AC_CCIC"
      name: "Sent to Crown Court for trial on conditional bail (CCIC) with pre-release conditions"
      expression: "isCcic == 1"
      severity: ERROR
      messageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Sent to Crown Court in custody for trial with
        bail direction (CCIIB)' instead. If the conditions apply after release, remove the
        pre-release conditions and enter them as bail conditions.
      errorMessageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Sent to Crown Court in custody for trial with
        bail direction (CCIIB)' instead. If the conditions apply after release, remove the
        pre-release conditions and enter them as bail conditions.
      affectedOffenceSet: "triggeredOffenceId"
      validationLevel: OFFENCE

    - id: "AC_CCSC"
      name: "Committed to Crown Court for sentence on conditional bail (CCSC) with pre-release conditions"
      expression: "isCcsc == 1"
      severity: ERROR
      messageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Committed to Crown Court for sentence - in
        custody with bail direction (CCSIB)' instead. If the conditions apply after release,
        remove the pre-release conditions and enter them as bail conditions.
      errorMessageTemplate: >-
        A conditional bail result cannot have pre-release bail conditions. If the court
        ordered pre-release conditions, use 'Committed to Crown Court for sentence - in
        custody with bail direction (CCSIB)' instead. If the conditions apply after release,
        remove the pre-release conditions and enter them as bail conditions.
      affectedOffenceSet: "triggeredOffenceId"
      validationLevel: OFFENCE
```

---

## Summary Message Consolidation (Error C)

AC_MIXED_ADULT and AC_MIXED_YOUTH both have **identical `errorMessageTemplate` text** (Error C). `DefaultValidationService` buckets page-level errors by `ruleId::errorMessage`. Both conditions' `errorMessage` resolves to the same string → they share one bucket key `DR-BAIL-015::Error C` → one summary entry with all adult AND youth defendant names accumulated → "This affects [names]" suffix appended once.

The inline `messageTemplate` is still age-specific: Error A for AC_MIXED_ADULT, Error B for AC_MIXED_YOUTH. ✓

---

## Database Migration

**File**: `src/main/resources/db/migration/V1.017__pre_release_bail_condition_error.sql`

```sql
INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by)
VALUES ('DR-BAIL-015', true, 'ERROR', now(), 'system');
```

---

## Source Code Layout (this feature)

```text
src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── PreReleaseBailConditionPreprocessor.java   (new)
├── PreReleaseBailContext.java                 (new)
└── PreprocessingDefinition.java               (modified — 4 new fields)

src/main/resources/rules/
└── DR-BAIL-015.yaml                           (new)

src/main/resources/db/migration/
└── V1.017__pre_release_bail_condition_error.sql  (new)

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
├── PreReleaseBailConditionPreprocessorTest.java  (new)
└── PreReleaseBailContextTest.java                (new)

src/test/java/uk/gov/hmcts/cp/integration/
└── PreReleaseBailConditionRuleIT.java            (new)

src/apiTest/java/uk/gov/hmcts/cp/http/
└── PreReleaseBailConditionApiHttpLiveTest.java   (new)

README.md                                         (modified — add DR-BAIL-015 row)
CRA-363.http                                      (new — at project root)
```
