# Data Model: CTL Missing Warning (DR-CTL-001)

**Branch**: `DD-41663-ctl-missing-warning` | **Date**: 2026-05-06

---

## 1. Upstream DTO changes (blocker — `api-cp-crime-hearing-results-validator`)

Two Boolean fields must be added to `OffenceDto` in the upstream repository before the preprocessor can be implemented:

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `hasExistingCtlRecord` | `Boolean` | yes | `true` if a Custody Time Limit was recorded against this offence at a prior hearing |
| `isConvicted` | `Boolean` | yes | `true` if the offence has a guilty plea, finding of guilt, or a recorded date of conviction |

The preprocessor treats `null` as `false` for both fields (safe default — when unknown, the warning is surfaced rather than suppressed).

---

## 2. `CtlOffenceContext` record (new)

**Package**: `uk.gov.hmcts.cp.services.rules.cel`
**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CtlOffenceContext.java`

```
CtlOffenceContext
├── offenceId         : String         — the offence this context represents
├── ctlWarningCount   : long           — 1 if the CTL warning applies, 0 otherwise
├── warningOffenceIds : List<String>   — singleton [offenceId] when ctlWarningCount == 1, else empty
└── allOffenceIds     : List<String>   — always [offenceId]
```

**Implements**: `RuleEvaluationContext`

`toCelContext()` exposes: `Map.of("ctlWarningCount", ctlWarningCount)`

`getOffenceIdSet(setName)` switches on:
- `"warningOffenceIds"` → `warningOffenceIds`
- `"allOffenceIds"` → `allOffenceIds`
- default → `IllegalArgumentException`

**State transitions**: `ctlWarningCount` is 0 or 1 per context; it is computed once by the preprocessor and never mutated.

---

## 3. `CtlMissingPreprocessor` (new)

**Package**: `uk.gov.hmcts.cp.services.rules.cel`
**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CtlMissingPreprocessor.java`

**Qualifier**: `"ctl-missing"` (YAML `preprocessing.type`)

**Algorithm** (per offence):

```
remandCodes  = upper(config.remandShortCodes)
ctlCodes     = upper(config.ctlShortCodes)
lines        = resultLines grouped by offenceId

for each offence:
  hasRemandResult  = any line for offence has shortCode in remandCodes
  hasExistingCtl   = offence.hasExistingCtlRecord (null → false)
  hasCtlResult     = any line for offence has shortCode in ctlCodes
  isConvicted      = offence.isConvicted (null → false)

  ctlWarning = hasRemandResult && !hasExistingCtl && !hasCtlResult && !isConvicted

  yield CtlOffenceContext(offenceId, ctlWarning ? 1 : 0, ...)
```

**Short-code comparison**: case-insensitive (normalised to `Locale.ROOT` upper case), matching the existing preprocessor style.

**Reads from `PreprocessingDefinition`**:
- `remandShortCodes` (List<String>)
- `ctlShortCodes` (List<String>)

---

## 4. `PreprocessingDefinition` changes (existing class modified)

**File**: `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`

Two new fields added:

| Field | Type | Purpose |
|-------|------|---------|
| `remandShortCodes` | `List<String>` | Trigger result short codes (RI, RIYDA, …) |
| `ctlShortCodes` | `List<String>` | CTL result short codes in current hearing (CTL) |

No existing fields removed or renamed.

---

## 5. `DR-CTL-001.yaml` (new rule file)

**File**: `src/main/resources/rules/DR-CTL-001.yaml`

```yaml
rule:
  id: "DR-CTL-001"
  title: "CTL missing check"
  description: >-
    Warns when a remand-type result is recorded against an offence that has no
    existing CTL record, no CTL result in the current hearing, and is not convicted.
  priority: 3000
  enabled: true

  preprocessing:
    type: "ctl-missing"
    remandShortCodes:
      - RI
      - RIYDA
      - RIH
      - RIB
      - RILA
      - RILAB
      - REMYD
    ctlShortCodes:
      - CTL

  conditions:
    - id: "AC1"
      name: "Remand result with no CTL"
      expression: "ctlWarningCount > 0"
      severity: WARNING
      messageTemplate: >-
        This offence does not have a CTL. If the trial has started a CTL is not
        needed. It is your responsibility to check and confirm.
      affectedOffenceSet: "warningOffenceIds"
```

---

## 6. Entity relationships

```
DraftValidationRequest
├── offences: List<OffenceDto>
│   ├── id                       (existing)
│   ├── offenceCode              (existing)
│   ├── hasExistingCtlRecord     (NEW — upstream)
│   └── isConvicted              (NEW — upstream)
└── resultLines: List<ResultLineDto>
    ├── shortCode                (existing — checked against remandShortCodes / ctlShortCodes)
    └── offenceId                (existing — groups lines to offences)

CtlMissingPreprocessor
└── produces Map<offenceId, CtlOffenceContext>

CtlOffenceContext
└── exposes ctlWarningCount → evaluated by CelExpressionEvaluator against "ctlWarningCount > 0"
```
