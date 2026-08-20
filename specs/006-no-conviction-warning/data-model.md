# Data Model: No Conviction Warning (DR-CONV-006)

**Branch**: `DD-43039-no-conviction-warning` | **Date**: 2026-07-31

---

## 1. No upstream DTO changes required

Both fields this rule needs already exist on the current version of `api-cp-crime-hearing-results-validator`:

| Field | Owner type | Nullable | Description |
|-------|-----------|----------|-------------|
| `ResultLineDto.category` | `ResultLineDto` | yes | `A` / `I` / `F` — closed enum; `F` marks a final disposition |
| `OffenceDto.isConvicted` | `OffenceDto` | yes | `true` if the offence has a guilty plea, finding of guilt, or recorded date of conviction (added for `DR-CTL-001`) |

The preprocessor treats `null` as `false` for `isConvicted` (safe default — when unknown, the warning is surfaced rather than suppressed), matching `CtlMissingPreprocessor`'s existing convention.

---

## 2. `NoConvictionContext` record (new)

**Package**: `uk.gov.hmcts.cp.services.rules.cel`
**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/NoConvictionContext.java`

```
NoConvictionContext
├── offenceId                 : String         — the offence this context represents
├── unconvictedSentenceCount  : long           — 1 if the warning applies, 0 otherwise
├── finalCategoryCount        : long           — count of category='F' result lines on this offence
├── excludedFinalCount        : long           — count of those final lines matching an excluded short code
├── convictedCount            : long           — 1 if offence.isConvicted is true, 0 otherwise
├── warningOffenceIds         : List<String>   — singleton [offenceId] when unconvictedSentenceCount == 1, else empty
└── allOffenceIds             : List<String>   — always [offenceId]
```

**Implements**: `RuleEvaluationContext`

`toCelContext()` exposes:
```java
Map.of(
    "unconvictedSentenceCount", unconvictedSentenceCount,
    "finalCategoryCount", finalCategoryCount,
    "excludedFinalCount", excludedFinalCount,
    "convictedCount", convictedCount
)
```

`getOffenceIdSet(setName)` switches on:
- `"warningOffenceIds"` → `warningOffenceIds`
- `"allOffenceIds"` → `allOffenceIds`
- default → `IllegalArgumentException`

**State transitions**: all counts are 0 or 1 per context, computed once by the preprocessor and never mutated.

---

## 3. `NoConvictionPreprocessor` (new)

**Package**: `uk.gov.hmcts.cp.services.rules.cel`
**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/NoConvictionPreprocessor.java`

**Qualifier**: `"no-conviction-check"` (YAML `preprocessing.type`)

**Algorithm** (per offence):

```
excludedShortCodes = PreprocessorHelper.upperSet(config.excludedFinalShortCodes)
resultsByOffence    = resultLines grouped by offenceId

for each offence:
  lines            = resultsByOffence.getOrDefault(offenceId, [])
  finalLines        = lines.filter(rl -> rl.category == F)
  finalCategoryCount = finalLines.size()
  excludedFinalCount = finalLines.count(rl -> PreprocessorHelper.hasUpperCode(rl, excludedShortCodes))
  finalNonExcluded   = finalLines.any(rl -> !PreprocessorHelper.hasUpperCode(rl, excludedShortCodes))
  isConvicted        = Boolean.TRUE.equals(offence.isConvicted)

  unconvictedSentence = finalNonExcluded && !isConvicted

  yield NoConvictionContext(
      offenceId,
      unconvictedSentence ? 1 : 0,
      finalCategoryCount,
      excludedFinalCount,
      isConvicted ? 1 : 0,
      unconvictedSentence ? [offenceId] : [],
      [offenceId])
```

**Short-code comparison**: case-insensitive, via `PreprocessorHelper` (rather than re-implementing private static helpers, unlike the two existing preprocessors — see `research.md` Decision 6).

**No offence-code gate**: unlike `DisqualificationExtendedTestPreprocessor`, there is no `relevantCodes` check — every offence is evaluated.

**Reads from `PreprocessingDefinition`**:
- `excludedFinalShortCodes` (existing field, reused as-is from `DR-DISQ-001`)

---

## 4. `PreprocessingDefinition` — no changes

No new fields required. `excludedFinalShortCodes` already exists and is populated per-rule from each YAML file independently.

---

## 5. `DR-CONV-006.yaml` (new rule file)

**File**: `src/main/resources/rules/DR-CONV-006.yaml`

```yaml
rule:
  id: "DR-CONV-006"
  title: "No conviction on sentenced offence check"
  description: >-
    Warns when a final result is recorded against an offence that is not one
    of the excluded non-substantive disposals, and the offence is not
    convicted (no guilty plea, finding of guilt, or recorded date of
    conviction).
  priority: 4000
  enabled: true

  preprocessing:
    type: "no-conviction-check"
    excludedFinalShortCodes:
      - wdrn
      - WDRNOFF
      - dism
      - dine
      - dini
      - disch
      - disc
      - ctrof
      - iremfile
      - err
      - errf
      - dead

  conditions:
    - id: "AC1"
      name: "Sentenced offence not convicted"
      expression: "unconvictedSentenceCount > 0"
      severity: WARNING
      messageTemplate: >-
        No conviction has been added against the offence. Check whether you
        need to add a guilty plea or verdict
      affectedOffenceSet: "warningOffenceIds"
      validationLevel: OFFENCE
```

---

## 6. Entity relationships

```
DraftValidationRequest
├── offences: List<OffenceDto>
│   ├── offenceId       (existing)
│   └── isConvicted     (existing — reused from DR-CTL-001)
└── resultLines: List<ResultLineDto>
    ├── shortCode        (existing — checked against excludedFinalShortCodes)
    ├── category         (existing — F marks a final disposition, reused from DR-DISQ-001)
    └── offenceId        (existing — groups lines to offences)

NoConvictionPreprocessor
└── produces Map<offenceId, NoConvictionContext>

NoConvictionContext
└── exposes unconvictedSentenceCount → evaluated by CelExpressionEvaluator against
    "unconvictedSentenceCount > 0"
```
