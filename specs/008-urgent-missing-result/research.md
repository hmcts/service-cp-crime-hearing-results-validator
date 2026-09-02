# Research: URGENT Result Missing Warning (CRA-22 / DR-URG-008)

**Branch**: `CRA-22-urgent-missing-result`
**Date**: 2026-09-02
**Status**: Complete — all unknowns resolved

---

## Decision: CHD-2485 dependency (CRITICAL BLOCKER)

**Decision**: Implementation is designed against `OffenceDto.getRemandStatus()` which does not exist in the current library version (26.25). The field will be delivered by CHD-2485 from the `api-cp-crime-hearing-results-validator` upstream repository.

**Rationale**: The spec is fully specifiable and the preprocessor can be written and tested with mock data now. Production execution requires updating the library version once CHD-2485 ships. The `conditionalBailRemandStatus` value (e.g. `"CONDITIONAL_BAIL"`) is configurable in YAML so no code change is required when the exact string is confirmed.

**How to apply**: When CHD-2485 delivers `OffenceDto.getRemandStatus()`, update `build.gradle` to the new library version. The preprocessor and tests require no logic change — only the library version bump.

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

## Decision: Two new fields added to `PreprocessingDefinition`

**Decision**: Add `List<String> bailEndingShortCodes` and `String conditionalBailRemandStatus` to `PreprocessingDefinition`.

**Rationale**: Existing fields (`remandShortCodes`, `ctlShortCodes`, `filterShortCodes`) are semantically tied to other rule types. Adding purpose-named fields avoids ambiguity and is consistent with the existing per-rule field naming pattern (e.g. `yroOrderShortCodes`, `communityOrderShortCodes`).

**Alternatives considered**: Reusing `remandShortCodes` for bail-ending codes — rejected because DS and WOFN are not "remand" codes and the name would mislead future maintainers. Reusing `filterShortCodes` — rejected because it already carries a different semantic for `CustodialPreprocessor`.

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

## Decision: `conditionalBailRemandStatus` placeholder value

**Decision**: Use `"CONDITIONAL_BAIL"` as the placeholder in YAML. This value is confirmed against the CHD-2485 delivery.

**Rationale**: YAML-configurable, so the actual value can be updated in the rule file without code change once CHD-2485 confirms the exact string.

---

## Known gap: no existing tests for defendant-offence remand status lookup

No existing integration test exercises the `remandStatus` field on `OffenceDto` because CHD-2485 has not shipped. The integration test for this rule will use TestContainers + WireMock stubs (via `IntegrationTestBase`) and mock `OffenceDto` objects with `remandStatus` set in test data builders.
