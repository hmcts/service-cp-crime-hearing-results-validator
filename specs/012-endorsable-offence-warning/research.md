# Research: Endorsable Offence Warning (CRA-336)

**Feature**: DR-ENDORSEMENT-012 — endorsement/disqualification missing on endorsable offences
**Branch**: `CRA-336-endorsable-offence-warning`

---

## Decision 1: Preprocessor type — new vs. existing

**Decision**: A new preprocessor `EndorsableOffencePreprocessor` (qualifier `"endorsable-offence"`) is required.

**Rationale**: No existing preprocessor checks `endorsable_flag` against a set of satisfying codes. The closest analogues are:
- `DisqualificationExtendedTestPreprocessor` (DR-DISQ-002): checks a fixed list of `relevantOffenceCodes` embedded in the YAML. Not applicable here — we must check every offence regardless of code, using a per-offence flag from reference data.
- `NonImprisonableOffencePreprocessor` (DR-SENT-011): checks every offence via a reference-data lookup (`custodialIndicator`). This is the closest structural match. The endorsable-offence rule follows the same per-offence, fail-open, reference-data-backed pattern.

**Alternatives considered**: Reusing `NonImprisonableOffencePreprocessor` — rejected because it is tightly coupled to the `custodialIndicator` lookup and the single-count `nonImprisonableCount` output shape; the endorsable rule requires two lists of codes (`excludedFinalShortCodes` and `satisfyingShortCodes`) plus a distinct `qualifyingCount` output.

---

## Decision 2: `endorsable_flag` data source

**Decision**: Look up `endorsable_flag` via `ReferencedataOffenceClient`, adding a new `getEndorsableFlag(offenceCode)` method backed by the existing `cpp-context-referencedata-offences` HTTP endpoint.

**Rationale**:
- `OffenceDto` (from upstream `api-cp-crime-hearing-results-validator`) does NOT have an `endorsableFlag` field. Adding it would require an upstream DTO change — an unnecessary scope expansion when the data is already accessible from the reference data service.
- `ReferencedataOffenceResponse` already reads multiple fields from the same endpoint (`misCode`, `custodialIndicator`). Adding `endorsableFlag` (an Integer, value `1` for endorsable) is a minimal additive change with `@JsonIgnoreProperties(ignoreUnknown = true)` protecting backward compatibility.
- The Jira spec states `endorsable_flag` is populated on ~99.7% of rows with no `details_json` fallback needed — a simpler lookup than `custodialIndicator` (which has a two-level fallback).

**Fail-open behaviour**: If the lookup fails (timeout, non-2xx, blank code, absent field), the flag is treated as absent → offence is NOT endorsable → no warning. This matches the "fail-open: no warning" pattern used by `getCustodialIndicator` and `lookupMisCode`.

**Alternatives considered**:
- Adding `endorsableFlag` to `OffenceDto` upstream — rejected; requires an API repo change and a new JAR release before this service can be implemented. The reference-data route is self-contained.
- Hardcoding a list of endorsable offence codes in the YAML — rejected; the offence reference data is the authoritative source and the list is too large/volatile to embed in YAML.

---

## Decision 3: `PreprocessingDefinition` extension — new `satisfyingShortCodes` field

**Decision**: Add a `satisfyingShortCodes` list to `PreprocessingDefinition`.

**Rationale**: `excludedFinalShortCodes` already exists in `PreprocessingDefinition` and covers the excluded-result list. The satisfying-result codes (endorsement, disqualification, special reasons) are a distinct semantic concept — a satisfying code suppresses the warning even when all other conditions are met. No existing field captures this concept. Adding `satisfyingShortCodes` keeps the YAML expressive and keeps the preprocessor logic generic (all code lists come from YAML config, not hardcoded in Java).

**Alternatives considered**: Overloading `filterShortCodes` — rejected; `filterShortCodes` is used by DR-SENT-011 with a different semantic (custodial result codes that trigger the check, not codes that satisfy/suppress). Using `extendedTestShortCodes` — rejected; semantically wrong and already owned by DR-DISQ-002.

---

## Decision 4: New context record `EndorsableOffenceContext`

**Decision**: Create `EndorsableOffenceContext(offenceId, qualifyingCount)` implementing `RuleEvaluationContext`.

**Rationale**: Minimal context matching `NonImprisonableOffenceContext`'s shape. `qualifyingCount` is 1 when the offence qualifies for the warning, 0 otherwise. The CEL condition is simply `qualifyingCount > 0`, making it easy to validate and test.

**Alternatives considered**: Reusing `NonImprisonableOffenceContext` — rejected; that record's field names (`nonImprisonableCount`) would be semantically wrong for this rule's CEL expression and error messages.

---

## Decision 5: YAML rule ID and priority

**Decision**: Rule ID `DR-ENDORSEMENT-012`, priority `12000`.

**Rationale**: Rule ID follows the established convention: `DR-<CATEGORY>-<NNN>` where NNN equals the spec directory number (`012`). Priority `12000` follows the `number × 1000` pattern confirmed by existing rules (DR-SENT-001 → 1000, DR-DISQ-002 → 2000, DR-SENT-011 → 11000).

---

## Decision 6: Integration test WireMock pattern

**Decision**: Follow `NonImprisonableOffenceRuleIT` pattern — stub `REFERENCEDATA_OFFENCE_WIRE_MOCK` with the endorsable-flag JSON, reset in `@BeforeEach`, use distinct `offenceCode` values per test method to avoid Caffeine cache cross-contamination.

**Rationale**: Proven working pattern for reference-data-backed rules. Caffeine cache lifetime is the Spring context lifetime for integration tests; unique offence codes per test are the correct guard.

---

## Unresolved at research: none

All decisions are resolved. No external dependencies block implementation.
