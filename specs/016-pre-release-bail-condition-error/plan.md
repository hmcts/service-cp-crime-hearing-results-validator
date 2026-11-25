# Implementation Plan: Pre-Release Bail Condition Error

**Branch**: `CRA-363-pre-release-bail-error` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/016-pre-release-bail-condition-error/spec.md`

## Summary

Add validation rule **DR-BAIL-015** (priority 15000, severity ERROR) that blocks sharing when any
conditional bail parent result (RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB, CCIC, or CCSC) is paired
with one or more pre-release bail condition results (PREBC, PRBCO, PRBCP1–PRBCP8, PRBCS1–PRBCS3,
PRBCSE1, PRBCSE2, PRBCT) on the **same offence**. Five error message variants (A–E) cover adult,
youth, mixed, no-DOB, CCIC, and CCSC cases. Implemented as a new `PreReleaseBailConditionPreprocessor`
+ `PreReleaseBailContext` + `DR-BAIL-015.yaml`. No changes to `CelValidationRule`, `DefaultValidationService`,
or the message-consolidation framework are needed.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Lombok, Flyway
**Storage**: PostgreSQL 15.3 (`validation_rule` table, migration `V1.017__pre_release_bail_condition_error.sql`)
**Testing**: JUnit 5 + Mockito + AssertJ (unit), TestContainers + MockMvc (IT), RestTemplate (API)
**Target Platform**: Spring Boot service on JVM, deployed on Azure / Kubernetes
**Project Type**: Spring Boot REST service
**Performance Goals**: rule evaluation adds negligible overhead (sub-millisecond per request)
**Constraints**: Google Checkstyle (`maxWarnings = 0`), PMD with `ignoreFailures = false`, 100% AC test coverage
**Scale/Scope**: One new preprocessor, one new context record, four new `PreprocessingDefinition` fields, one YAML, one SQL migration

## Constitution Check

| Principle | Status | Notes |
|---|---|---|
| I. YAML/CEL Rule-First | ✅ PASS | `DR-BAIL-015.yaml` is written before any Java. All short-code lists in YAML. |
| II. Constructor Injection & Immutable DTOs | ✅ PASS | `PreReleaseBailContext` is a record; `PreReleaseBailConditionPreprocessor` is `@Component` with no state. |
| III. Layered Architecture & Preprocessor Dispatch | ✅ PASS | New preprocessor registered via `@Component` + `type()` qualifier; resolved through `PreprocessorRegistry`. |
| IV. Spec-Driven Build Loop | ✅ PASS | Plan precedes implementation; code-reviewer + qa + spec-validator loop enforced. |
| V. HMCTS Standards Compliance | ✅ PASS | Gradle, Spring Boot 4, Java 25, `uk.gov.hmcts.cp.*`, SLF4J logging. |
| VI. Severity Ceiling, Never Promote | ✅ PASS | YAML severity is `ERROR`; DB row inserts `ERROR`; ceiling can only lower it. |
| VII. No System.out / System.err | ✅ PASS | All diagnostic output via SLF4J `@Slf4j`. |
| VIII. TDD — Red → Green → Refactor | ✅ PASS | Tests written first per task ordering in tasks.md. |

## Project Structure

### Documentation (this feature)

```text
specs/016-pre-release-bail-condition-error/
├── spec.md          ✅
├── plan.md          ✅ (this file)
├── research.md      ✅
├── data-model.md    ✅
├── quickstart.md    ✅
├── checklists/
│   └── requirements.md  ✅
└── tasks.md         (Phase 2 — /speckit-tasks)
```

### Source Code (repository root)

```text
src/main/resources/rules/
└── DR-BAIL-015.yaml                           (NEW)

src/main/resources/db/migration/
└── V1.017__pre_release_bail_condition_error.sql  (NEW)

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── PreReleaseBailConditionPreprocessor.java   (NEW)
├── PreReleaseBailContext.java                 (NEW)
└── PreprocessingDefinition.java               (MODIFIED — 4 new fields)

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
├── PreReleaseBailConditionPreprocessorTest.java  (NEW)
└── PreReleaseBailContextTest.java                (NEW)

src/test/java/uk/gov/hmcts/cp/integration/
└── PreReleaseBailConditionRuleIT.java            (NEW)

src/apiTest/java/uk/gov/hmcts/cp/http/
└── PreReleaseBailConditionApiHttpLiveTest.java   (NEW)

README.md                                         (MODIFIED — add DR-BAIL-015 row)
CRA-363.http                                      (NEW — local HTTP test file)
```

**Structure Decision**: Standard single-project layout. No new packages required.

## Key Design Decisions

### 1. Per-offence context (not per-defendant)

Each `PreReleaseBailContext` represents one **triggered offence** (keyed by `offenceId`). This is
consistent with `ApplicationResultOffencePreprocessor` (DR-APP-009), which is also per-offence.

**Why**: The trigger is per-offence (parent and child result must be on the SAME offence). Multiple
offences on the same defendant can independently trigger the rule.

### 2. Hearing-wide flags baked into context

`hearingHasRemandAdult` and `hearingHasRemandYouth` are computed in a two-pass algorithm and baked
into every `PreReleaseBailContext`. CEL expressions can then compare these flags to determine which
error variant applies.

**Why**: CEL expressions evaluate one context at a time and have no cross-context visibility. Hearing-
wide aggregates must be computed in the preprocessor (Java) and surfaced as CEL variables.

### 3. Error C summary consolidation via identical errorMessageTemplate text

AC_MIXED_ADULT and AC_MIXED_YOUTH both set `errorMessageTemplate` to the **identical** Error C
text. `DefaultValidationService` groups page-level messages by `ruleId::errorMessage` string key.
Both conditions therefore contribute to the same key `DR-BAIL-015::{Error C text}`, and defendant
names are accumulated across all contributing contexts into one summary entry.

**Why**: No new framework mechanism is needed. The existing `ruleId::errorMessage` bucketing already
handles this. Adding a new field or hook would violate the "no over-engineering" principle.

### 4. No changes to CelValidationRule or DefaultValidationService

This feature fits entirely within the existing extension points. The only Java changes are:
- Adding a new `@Component` preprocessor
- Adding a new context record
- Adding 4 fields to `PreprocessingDefinition`

### 5. Short codes are YAML-configurable (Constitution Principle I)

All 16 pre-release bail condition codes and all 8 conditional bail parent codes live in
`DR-BAIL-015.yaml` under `preprocessing`. No short codes are hardcoded in Java.

## CEL Condition Design (7 conditions)

| Condition ID | CEL expression | Inline (messageTemplate) | Page-level (errorMessageTemplate) |
|---|---|---|---|
| AC_ADULT | `isRemandBail==1 && isAdult==1 && hasDob==1 && hearingHasRemandYouth==0` | Error A | Error A |
| AC_YOUTH | `isRemandBail==1 && isYouth==1 && hasDob==1 && hearingHasRemandAdult==0` | Error B | Error B |
| AC_MIXED_ADULT | `isRemandBail==1 && isAdult==1 && hasDob==1 && hearingHasRemandYouth==1` | Error A | Error C |
| AC_MIXED_YOUTH | `isRemandBail==1 && isYouth==1 && hasDob==1 && hearingHasRemandAdult==1` | Error B | Error C |
| AC_NODOB | `isRemandBail==1 && hasDob==0` | Error C | Error C |
| AC_CCIC | `isCcic==1` | Error D | Error D |
| AC_CCSC | `isCcsc==1` | Error E | Error E |

AC_MIXED_ADULT + AC_MIXED_YOUTH sharing identical `errorMessageTemplate` (Error C) is the mechanism
that produces ONE combined summary entry in the mixed-hearing case (AC4). Mutual exclusivity with
AC_ADULT / AC_YOUTH is guaranteed by the `hearingHasRemandYouth / hearingHasRemandAdult` guards.

## PreReleaseBailContext — CEL variable map

| Variable | Type | Meaning |
|---|---|---|
| `isRemandBail` | Long (0/1) | Parent is RC/RCBV/BAILREVC/REMCB/REMCBY/RCCCB |
| `isCcic` | Long (0/1) | Parent is CCIC |
| `isCcsc` | Long (0/1) | Parent is CCSC |
| `isAdult` | Long (0/1) | Defendant is ≥18 on hearingDay |
| `isYouth` | Long (0/1) | Defendant is <18 on hearingDay |
| `hasDob` | Long (0/1) | Defendant's dateOfBirth is non-null |
| `hearingHasRemandAdult` | Long (0/1) | Hearing-wide: any remand-bail + pre-release + adult offence |
| `hearingHasRemandYouth` | Long (0/1) | Hearing-wide: any remand-bail + pre-release + youth offence |

`affectedOffenceSet: "triggeredOffenceId"` → `List.of(offenceId)`.

## Preprocessor Algorithm (three-pass)

```
PASS 1 — find triggered offences
  remandCodes = upperSet(config.remandConditionalBailShortCodes())
  ccicCodes   = upperSet(config.ccicShortCodes())
  ccscCodes   = upperSet(config.ccscShortCodes())
  preRelCodes = upperSet(config.preReleaseBailConditionShortCodes())
  allParentCodes = remandCodes ∪ ccicCodes ∪ ccscCodes

  parentCodeByOffence = LinkedHashMap<offenceId, String>   (first matching parent code)
  preReleaseOffences  = LinkedHashSet<String>              (offences with ≥1 pre-release code)
  defendantByOffence  = LinkedHashMap<offenceId, String>   (defendantId from result line)

  for each ResultLineDto rl in request.resultLines (skip null offenceId or shortCode):
    upper = rl.shortCode.toUpperCase(ROOT)
    if upper in allParentCodes: parentCodeByOffence.putIfAbsent(rl.offenceId, upper)
    if upper in preRelCodes:    preReleaseOffences.add(rl.offenceId)
    defendantByOffence.putIfAbsent(rl.offenceId, rl.defendantId)

  triggeredOffences = parentCodeByOffence.keySet() ∩ preReleaseOffences
  if triggeredOffences.isEmpty: return Map.of()

  dobByDefendant  = Map<defendantId, LocalDate>  (from request.defendants)
  nameByDefendant = Map<defendantId, String>      (firstName + " " + lastName)

PASS 2 — hearing-wide flags
  hearingHasRemandAdult = false
  hearingHasRemandYouth = false
  for each offenceId in triggeredOffences:
    if parentCodeByOffence.get(offenceId) in remandCodes:
      dob = dobByDefendant.get(defendantByOffence.get(offenceId))
      if dob != null:
        if isAdult(dob, hearingDay): hearingHasRemandAdult = true
        else:                         hearingHasRemandYouth = true

PASS 3 — emit contexts
  for each offenceId in triggeredOffences:
    parentCode = parentCodeByOffence.get(offenceId)
    defendantId = defendantByOffence.get(offenceId)
    dob = dobByDefendant.get(defendantId)
    hasDob = dob != null ? 1L : 0L
    emit PreReleaseBailContext(
      offenceId, defendantId, nameByDefendant.getOrDefault(defendantId, ""),
      parentCode in remandCodes ? 1L : 0L,
      parentCode in ccicCodes ? 1L : 0L,
      parentCode in ccscCodes ? 1L : 0L,
      hasDob == 1 && isAdult(dob, hearingDay) ? 1L : 0L,
      hasDob == 1 && !isAdult(dob, hearingDay) ? 1L : 0L,
      hasDob,
      hearingHasRemandAdult ? 1L : 0L,
      hearingHasRemandYouth ? 1L : 0L
    )
```

`isAdult(dob, hearingDay)` → `Period.between(dob, hearingDay).getYears() >= 18`
(returns `false` when either argument is null — fail-safe, same pattern as `AgeRestrictedImprisonmentPreprocessor:100`).

## SQL Migration

```sql
-- V1.017__pre_release_bail_condition_error.sql
INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by)
VALUES ('DR-BAIL-015', true, 'ERROR', now(), 'system');
```

## Test Coverage Map

| AC | Test location | Test method pattern |
|---|---|---|
| AC1 (adult, remand bail) | PreReleaseBailConditionRuleIT | `adultDefendant_remandBailWithPreRelease_shouldRaiseErrorA` |
| AC2 (youth, remand bail) | PreReleaseBailConditionRuleIT | `youthDefendant_remandBailWithPreRelease_shouldRaiseErrorB` |
| AC2A (CCIC) | PreReleaseBailConditionRuleIT | `ccicParent_withPreRelease_shouldRaiseErrorD` |
| AC2B (CCSC) | PreReleaseBailConditionRuleIT | `ccscParent_withPreRelease_shouldRaiseErrorE` |
| AC3 (multi-defendant names) | PreReleaseBailConditionRuleIT | `multipleDefendants_affectedNamesAppended_*` |
| AC4 (mixed adult+youth → Error C summary) | PreReleaseBailConditionRuleIT | `mixedAdultYouth_remandBail_errorCSummaryWithAgeSpecificInline` |
| AC4A (multiple parent types) | PreReleaseBailConditionRuleIT | `multipleBailParentTypes_separateSummaryEntries` |
| AC5A (alongside other rules) | PreReleaseBailConditionRuleIT | `alongsideOtherRuleErrors_neitherSuppressed` |
| AC7 (cleared when corrected) | PreReleaseBailConditionRuleIT | `correctedToRib_shouldClearError` |
| AC8 (not triggered) | PreReleaseBailConditionRuleIT | `nonConditionalBailParent_shouldNotTrigger` + `preReleaseOnDifferentOffence_shouldNotTrigger` |
| AC9 (no DOB → Error C) | PreReleaseBailConditionRuleIT | `noDob_remandBail_shouldRaiseErrorC` |
| All 16 pre-release codes | PreReleaseBailConditionPreprocessorTest | `@ParameterizedTest @ValueSource` over all 16 codes |
| All 6 remand parent codes | PreReleaseBailConditionPreprocessorTest | `@ParameterizedTest @ValueSource` over RC/RCBV/BAILREVC/REMCB/REMCBY/RCCCB |
| RICD excluded | PreReleaseBailConditionPreprocessorTest | `ricdParent_shouldNotTrigger` |

## Complexity Tracking

No constitution violations. No complexity justification required.

**FR-018 withdrawn**: Welsh translations for Error A–E are not required for this rule. Confirmed out of scope; English-only `messageTemplate` / `errorMessageTemplate` values in `DR-BAIL-015.yaml` are the agreed launch scope. T040 in `tasks.md` is closed as not required.
