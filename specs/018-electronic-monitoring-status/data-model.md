# Data Model: Electronic Monitoring Status Validation (CRA-38)

**Branch**: `dev/CRA-38-electronic-monitoring-status`  
**Date**: 2026-11-18

## Existing entities consumed (read-only, no changes)

| Entity | Source | Relevant fields |
|--------|--------|-----------------|
| `OffenceDto` | `uk.gov.hmcts.cp.openapi.model` | `offenceId`, `hasActiveElectronicMonitoring`, `defendantId` |
| `ResultLineDto` | `uk.gov.hmcts.cp.openapi.model` | `resultLineId`, `shortCode`, `category` (F/I/A), `offenceId`, `defendantId`, `prompts` |
| `Prompt` | `uk.gov.hmcts.cp.openapi.model` | `promptRef`, `promptValue` |
| `DefendantDto` | `uk.gov.hmcts.cp.openapi.model` | `defendantId`, `firstName`, `lastName` |
| `DraftValidationRequest` | `uk.gov.hmcts.cp.openapi.model` | `hearingId`, `offences`, `resultLines`, `defendants` |

## New entity: `ElectronicMonitoringContext`

**Package**: `uk.gov.hmcts.cp.services.rules.cel`  
**Type**: Java record implementing `RuleEvaluationContext`

```java
public record ElectronicMonitoringContext(
    String defendantId,
    String defendantName,
    long unresolvedEmCount,
    List<String> unresolvedOffenceIds
) implements RuleEvaluationContext {

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("unresolvedEmCount", unresolvedEmCount);
    }

    @Override
    public List<String> getOffenceIdSet(String setName) {
        return switch (setName) {
            case "unresolvedOffenceIds" -> unresolvedOffenceIds;
            default -> throw new IllegalArgumentException("Unknown set: " + setName);
        };
    }
}
```

**Invariants**:
- `unresolvedEmCount == unresolvedOffenceIds.size()` always
- `unresolvedEmCount > 0` only — a context with count 0 is not emitted (preprocessor skips resolved defendants)
- `defendantName` may be null if the defendant has no name data

## Modified entity: `PreprocessingDefinition`

Three new optional `List<String>` fields added to allow YAML-driven configuration:

| Field | YAML key | Description |
|-------|----------|-------------|
| `continuingEmPromptRefs` | `continuingEmPromptRefs` | promptRef values indicating EM continuation (ELMONDET, EMREQ, EMREQV, EMSO) |
| `endingEmShortCodes` | `endingEmShortCodes` | Result short codes that end EM (EMONE) |
| `exemptMainResultShortCodes` | `exemptMainResultShortCodes` | Short codes treated as exempt main results (NHMC, NHCC, A, ADJNN, ADJAPP) |

These are `@Nullable List<String>` fields with `null` meaning "not configured" (preprocessor falls back to no-op or empty set).

## New entity: `ElectronicMonitoringPreprocessor`

**Package**: `uk.gov.hmcts.cp.services.rules.cel`  
**Type**: Spring `@Component` implementing `ValidationPreprocessor`  
**Qualifier**: `"electronic-monitoring-status"` (returned by `type()`)

**Key method**:
```
Map<String, ElectronicMonitoringContext> preprocess(DraftValidationRequest, PreprocessingDefinition)
```

Returns one entry per defendant who has at least one unresolved EM-flagged offence.

## Database

No schema changes. The `validation_rule` table (existing DDL from V1.001) receives one new row:

| id | enabled | severity |
|----|---------|---------|
| `DR-EMON-017` | `true` | `ERROR` |

Inserted by `V1.019__insert_dr_emon_017.sql`.

## State transitions for an EM-flagged offence

```
EM-flagged offence
       │
       ├─ No result lines ──────────────────────────────► RESOLVED (blank)
       │
       ├─ Has EMONE result line ─────────────────────────► RESOLVED (ending)
       │
       ├─ Has prompt with promptRef ∈ {ELMONDET, EMREQ,
       │   EMREQV, EMSO} AND promptValue = "Yes" ─────────► RESOLVED (continuing)
       │
       ├─ No category-F line AND
       │   every category-I line has shortCode ∈
       │   {NHMC, NHCC, A, ADJNN, ADJAPP}
       │   AND ≥1 category-I exempt result ─────────────── RESOLVED (exempt main result)
       │
       ├─ No category-F or category-I lines
       │   (all ancillary or none) ─────────────────────── RESOLVED (ancillary-only)
       │
       └─ Otherwise ─────────────────────────────────────► UNRESOLVED → ERROR
```
