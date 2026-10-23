# Data Model: Non-Endorsable Offence Warning (CRA-337)

## New Java Types

### NonEndorsableOffenceContext

```java
package uk.gov.hmcts.cp.services.rules.cel;

public record NonEndorsableOffenceContext(
    String offenceId,
    long endorsementCount,       // 1 if offence is non-endorsable AND has an endorsement trigger result; 0 otherwise
    long disqualificationCount   // 1 if offence is non-endorsable AND has a disqualification trigger result; 0 otherwise
) implements RuleEvaluationContext {

    private static final String ENDORSEMENT_SET = "nonEndorsableEndorsementOffenceIds";
    private static final String DISQUALIFICATION_SET = "nonEndorsableDisqualificationOffenceIds";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("endorsementCount", endorsementCount, "disqualificationCount", disqualificationCount);
    }

    @Override
    public List<String> getOffenceIdSet(String setName) {
        boolean endorsementMatch = ENDORSEMENT_SET.equals(setName) && endorsementCount > 0;
        boolean disqualificationMatch = DISQUALIFICATION_SET.equals(setName) && disqualificationCount > 0;
        return endorsementMatch || disqualificationMatch ? List.of(offenceId) : List.of();
    }

    @Override
    public String defendantName() { return null; }

    @Override
    public List<String> allOffenceIds() { return List.of(offenceId); }
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `offenceId` | `String` | The offence UUID from the hearing request |
| `endorsementCount` | `long` | 1 = endorsement trigger on non-endorsable offence; 0 = no warning condition |
| `disqualificationCount` | `long` | 1 = disqualification trigger on non-endorsable offence; 0 = no warning condition |

---

## Preprocessor Logic

### NonEndorsableOffencePreprocessor

**Qualifier**: `"non-endorsable-offence"`

**Algorithm per offence**:
1. Collect all result line `shortCode` values for this offence.
2. Determine `hasEndorsementTrigger`: any code in `endorsementShortCodes` (LEP, LEN, LEA).
3. Determine `hasDisqualificationTrigger`: any code in `disqualificationTriggerShortCodes`.
4. If neither trigger is present → skip ref-data call entirely; do not emit a context entry for this offence.
5. If at least one trigger is present → call `referencedataOffenceClient.getEndorsableFlag(offence.getOffenceCode())` **once**.
6. If flag is `Optional.empty()` or `Boolean.FALSE` (non-endorsable) → emit `NonEndorsableOffenceContext(offenceId, hasEndorsementTrigger ? 1L : 0L, hasDisqualificationTrigger ? 1L : 0L)`.
7. If flag is `Boolean.TRUE` (endorsable) → skip; do not emit a context entry.

**YAML configuration fields** (under `preprocessing:`):
| Key | Value |
|-----|-------|
| `type` | `"non-endorsable-offence"` |
| `endorsementShortCodes` | `[LEP, LEN, LEA]` |
| `disqualificationTriggerShortCodes` | `[DDO, DDOL, DDOR, DDOTE, DDOTEL, DDD, DDDL, DDDT, DDDTL, DDDTO, DDP, DDPL, DDPR, DDPTE, DDPTEL, DDRCOT]` |

---

## YAML Rule File

### DR-END-014.yaml (actual file content)

```yaml
rule:
  id: "DR-END-014"
  title: "Endorsement or disqualification result on non-endorsable offence"
  description: >-
    Warns when an endorsement result (LEP, LEN, LEA) or a disqualification result
    intended for endorsable offences (DDO/DDD/DDP families plus DDRCOT) is recorded
    against an offence whose reference data records endorsable_flag as false or absent.
    Advisory only — does not block saving or sharing.
  priority: 14000
  enabled: true

  preprocessing:
    type: "non-endorsable-offence"
    endorsementShortCodes:
      - LEP
      - LEN
      - LEA
    disqualificationTriggerShortCodes:
      - DDO
      - DDOL
      - DDOR
      - DDOTE
      - DDOTEL
      - DDD
      - DDDL
      - DDDT
      - DDDTL
      - DDDTO
      - DDP
      - DDPL
      - DDPR
      - DDPTE
      - DDPTEL
      - DDRCOT

  conditions:
    - id: "AC1"
      name: "Endorsement on non-endorsable offence"
      expression: "endorsementCount > 0"
      severity: WARNING
      messageTemplate: >-
        This offence is not endorsable. Remove the endorsement result.
      affectedOffenceSet: "nonEndorsableEndorsementOffenceIds"
      validationLevel: OFFENCE

    - id: "AC2"
      name: "Disqualification on non-endorsable offence"
      expression: "disqualificationCount > 0"
      severity: WARNING
      messageTemplate: >-
        This offence is not endorsable. Remove the disqualification result, or record a disqualification for non-endorsable offence result instead.
      affectedOffenceSet: "nonEndorsableDisqualificationOffenceIds"
      validationLevel: OFFENCE
```

---

## Database Migration

### V1.015__insert_dr_end_014.sql

```sql
INSERT INTO validation_rule (id, enabled, severity)
VALUES ('DR-END-014', true, 'WARNING');
```

---

## Existing Types Consumed (no changes)

| Type | Location | Role |
|------|----------|------|
| `ReferencedataOffenceClient.getEndorsableFlag()` | `uk.gov.hmcts.cp.services.referencedata` | Fetches `endorsable_flag` per offence code; Caffeine-cached |
| `ValidationPreprocessor` | `uk.gov.hmcts.cp.services.rules.cel` | Interface the new preprocessor implements |
| `PreprocessorRegistry` | `uk.gov.hmcts.cp.services.rules.cel` | Auto-discovers new preprocessor by qualifier |
| `PreprocessingDefinition` | `uk.gov.hmcts.cp.services.rules.cel` | Carries YAML `preprocessing:` block; `endorsementShortCodes` and `disqualificationTriggerShortCodes` fields added |
| `DraftValidationRequest` | external DTO | Input to the preprocessor |
| `RuleEvaluationContext` | `uk.gov.hmcts.cp.services.rules.cel` | Interface the context record implements |
| `PreprocessorHelper` | `uk.gov.hmcts.cp.services.rules.cel` | Utility: `upperSet()`, `groupResultsByOffence()`, `anyShortCodeIn()` |

---

## Relationships

```
DraftValidationRequest
  └─ offences[]
       └─ resultLines[].shortCode
            └─ NonEndorsableOffencePreprocessor  (qualifier: "non-endorsable-offence")
                 ├─ endorsementShortCodes ∈ {LEP, LEN, LEA}
                 ├─ disqualificationTriggerShortCodes ∈ {DDO...DDRCOT}
                 └─ ReferencedataOffenceClient.getEndorsableFlag(offenceCode) = false/absent
                      └─ NonEndorsableOffenceContext{offenceId, endorsementCount, disqualificationCount}
                           ├─ DR-END-014 condition AC1: endorsementCount > 0 → WARNING (endorsement message)
                           └─ DR-END-014 condition AC2: disqualificationCount > 0 → WARNING (disqualification message)
```