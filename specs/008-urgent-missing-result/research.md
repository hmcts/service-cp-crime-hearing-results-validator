# Research: URGENT Result Missing Warning (CRA-22 / DR-URG-008)

**Branch**: `CRA-22-urgent-missing-result`
**Date**: 2026-09-02
**Status**: Complete — all unknowns resolved

---

## Decision: CHD-2485 dependency (RESOLVED)

**Decision**: CHD-2485 has been delivered in `api-cp-crime-hearing-results-validator` version `0.2.8-cra-22`. `OffenceDto` now has `BailStatusEnum bailStatus` (accessed via `offence.getBailStatus()`). Conditional bail is identified by `OffenceDto.BailStatusEnum.B` (code `"B"`, description `"Conditional bail"`).

**Rationale**: The enum constant `B` is confirmed from the generated source and the CRA-22 commit in the upstream repo (`1c70b3c`). `build.gradle` must reference version `0.2.8-cra-22` (or the release version once it is promoted from the branch build).

**How to apply**: Use `OffenceDto.BailStatusEnum.B.equals(offence.getBailStatus())` in `ConditionalBailPreprocessor`. Null `bailStatus` is treated as "not conditional bail" (safe no-op).

---

## Decision: Preprocessor pattern — defendant-grouped, offence-level remand status

**Decision**: Use the defendant-deduplication grouping pattern from `PreprocessorHelper.groupLinesByDedupedDefendant()` plus per-offence `OffenceDto` lookups. Produce one `ConditionalBailContext` per deduplicated defendant.

**Rationale**: The rule fires at defendant level. Multiple `defendantId`s sharing a `masterDefendantId` (linked cases) must be folded into one group, exactly as `CustodialPreprocessor` and `YouthRehabilitationPreprocessor` do. The `OffenceDto` remandStatus field drives which offences are in scope, while result lines drive whether those offences have bail-ending or URGENT results.

**Alternatives considered**: Per-offence context (like `NoConvictionPreprocessor`) — rejected because the firing condition is defendant-scoped ("all conditional-bail offences for a defendant have ended") and the output is a defendant-level warning.

---

## Decision: Bail-ending detection — Category F OR explicit short codes

**Decision**: A result line is "bail-ending" if either:
1. Its `category` field is `ResultLineDto.CategoryEnum.F` (any final result), OR
2. Its short code (case-insensitive) is in the configured `bailEndingShortCodes` list: `DS`, `RI`, `RIYDA`, `RIH`, `RIB`, `RILA`, `RILAB`, `REMYD`, `WOFN`.

**Rationale**: AC1 defines bail-ending as Category F (sentences + end offences). AC2 (DS), AC3 (RI family), and AC4 (WOFN) are non-F result codes that also end conditional bail. The two-pronged check mirrors the product's definition of "bail-ending" across all four AC types without enumerating every Category F short code.

**Alternatives considered**: Short-code-only list (enumerate all Category F codes too) — rejected because Category F codes are managed upstream and enumerating them would create drift risk.

---

## Decision: URGENT detection — hardcoded constant, not YAML-configurable

**Decision**: The URGENT short code is hardcoded as the constant `"URGENT"` in `ConditionalBailPreprocessor`. It is NOT added to `PreprocessingDefinition` as a configurable field.

**Rationale**: The spec states "The exact URGENT short code is `URGENT` (all uppercase, no variation)". This is a stable business constant with no reasonable variation. Making it YAML-configurable would add complexity without benefit. Unlike bail-ending codes (which are a meaningful list), URGENT is a single known value.

---

## Decision: One new field added to `PreprocessingDefinition`

**Decision**: Add only `List<String> bailEndingShortCodes` to `PreprocessingDefinition`. The `conditionalBailRemandStatus` String field originally planned is **not needed** — conditional bail is detected by comparing `offence.getBailStatus() == OffenceDto.BailStatusEnum.B` directly in the preprocessor (a hardcoded enum constant, not a YAML-configurable string).

**Rationale**: Now that CHD-2485 has delivered a typed enum (`BailStatusEnum`), hardcoding `BailStatusEnum.B` in the preprocessor is safer and clearer than a YAML string that could be mistyped. The enum value is a stable CPP platform constant with no runtime variation. This also keeps `PreprocessingDefinition` smaller.

**Alternatives considered**: YAML-configurable `conditionalBailRemandStatus` string — rejected because the enum value `B` is fixed by the upstream data model; making it configurable would give a false sense of flexibility while adding a misconfiguration failure mode.

---

## Decision: Rule ID — `DR-URG-008`

**Decision**: Name the YAML rule `DR-URG-008.yaml` with category abbreviation `URG` (for URGENT result).

**Rationale**: Follows the `DR-<CATEGORY>-<NNN>` pattern. `URG` clearly signals the missing-URGENT-result concern. Number 008 is the next sequential slot.

---

## Decision: CEL expression

**Decision**:
```
conditionalBailOffenceCount > 0 && bailEndedCount == conditionalBailOffenceCount && hasUrgentCount == 0
```

**Rationale**: Three guards map directly to the three business conditions: (1) at least one conditional-bail offence exists, (2) all of them have bail-ending results, (3) none has URGENT. The equality `bailEndedCount == conditionalBailOffenceCount` captures the "all" requirement without counting — if any conditional-bail offence has no bail-ending result, `bailEndedCount < conditionalBailOffenceCount` and the expression is false.

---

## Decision: Context fields exposed to CEL

| CEL variable                 | Type | Meaning                                                         |
|------------------------------|------|-----------------------------------------------------------------|
| `conditionalBailOffenceCount` | Long | Total offences for this defendant with conditional bail remand status |
| `bailEndedCount`              | Long | How many of those have at least one bail-ending result line      |
| `hasUrgentCount`              | Long | 1 if any conditional-bail offence carries URGENT; 0 otherwise   |

**Rationale**: Minimum set needed for the CEL expression. No over-provisioning of context variables.

---

## Decision: Conditional bail detection — hardcoded `BailStatusEnum.B`

**Decision**: `ConditionalBailPreprocessor` detects conditional bail via `OffenceDto.BailStatusEnum.B.equals(offence.getBailStatus())`. This is hardcoded as a private constant (`private static final BailStatusEnum CONDITIONAL_BAIL = BailStatusEnum.B`), not YAML-configurable.

**Rationale**: `BailStatusEnum.B` is a typed, compiler-checked constant from the upstream API contract. It cannot be mistyped in YAML, cannot drift silently, and mirrors the treatment of `"URGENT"` (also hardcoded). The DR-URG-008.yaml preprocessing block therefore does **not** include a `conditionalBailRemandStatus` key.

---

## Known gap resolved: BailStatusEnum.B confirmed

CHD-2485 delivered. Integration tests can now use `OffenceDto.builder().bailStatus(OffenceDto.BailStatusEnum.B).build()` directly. No stubs or workarounds needed.
