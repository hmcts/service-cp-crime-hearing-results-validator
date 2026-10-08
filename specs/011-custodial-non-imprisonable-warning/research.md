# Phase 0 Research: CRA-256 – Custodial Sentence Against Non-Imprisonable Offence

*v3 updated 2026-10-06 — approach pivoted from OffenceDto fields to reference-data API lookup.*

---

## 1. Custodial Indicator Source (REVISED)

**Decision (v3)**: Resolve the effective custodial indicator via `ReferencedataOffenceClient.getCustodialIndicator(offenceCode)` — the same outbound `cpp-context-referencedata-offences` API already used by `DR-SEX-008` via `lookupMisCode`. No changes to `OffenceDto` or `api-cp-crime-hearing-results-validator` are needed.

**Finding (v3)**: `CUSTODIAL_INDICATOR_NOTES.md` in `cpp-context-referencedata-offences` documents:
- The API response carries a top-level `custodialIndicator` field (camelCase, from `custodial_indicator` DB column).
- The API response also carries a `details` JSON object (the full offence document). The Libra custodial indicator lives at `details.document.libra.custodialindicator.code`.
- **The upstream service does NOT apply the precedence logic** — it passes the column value straight through without fallback. Therefore the precedence must be applied client-side in `getCustodialIndicator()`.

**Rationale**: The reference-data client already exists, is tested, is cached, and is wired into Spring as a `@Component`. Adding a second method mirrors the exact pattern already proven by `lookupMisCode`. This avoids any upstream DTO dependency, unblocks implementation immediately, and keeps the indicator resolution consistent with how `misCode` is resolved.

**Previous approach (v2 — abandoned)**: Read `custodialIndicator` and `libraCustodialIndicatorCode` directly from `OffenceDto` (flat String fields). Required bumping `libs.versions.toml` to `26.26` (which added these fields to `OffenceDto`). Abandoned because the reference-data lookup is a better architectural fit — the indicator is reference data, not hearing data.

**Alternatives considered**:
- Pass raw `detailsJson: String` from the request and parse in the preprocessor — rejected: JSON parsing inside a `ValidationPreprocessor` violates single-responsibility; `details_json` is large.
- Have the upstream service implement precedence server-side — valid future improvement (documented in `CUSTODIAL_INDICATOR_NOTES.md`), but not needed now; client-side precedence is correct and testable.

---

## 2. Rule ID

**Decision**: `DR-SENT-011`

**Rationale**: Rule number suffix matches the spec folder sequence number (global across all categories). Spec folder `011-custodial-non-imprisonable-warning` → rule suffix `011`. This aligns with the established pattern: `DR-SENT-001` = spec `001`, `DR-DISQ-002` = spec `002`, `DR-AGE-007` = spec `007`, `DR-RESTRAO-010` = spec `010`.

---

## 3. Preprocessor Type and Class

**Decision**: New `ValidationPreprocessor` with type qualifier `"non-imprisonable-offence"`, implemented as `NonImprisonableOffencePreprocessor`.

**Rationale**: No existing preprocessor handles custodial indicator resolution via the reference-data client. The preprocessor is per-offence (groups by `offenceId`), injects `ReferencedataOffenceClient`, and applies the custodial short-code filter before making any network call.

**Pattern followed**: `SexualOffenceNotificationPreprocessor` — cheap local check first (short-code filter), then network call (`getCustodialIndicator` vs `lookupMisCode`), fail-open on empty result.

---

## 4. Context Record

**Decision**: `NonImprisonableOffenceContext` record implementing `RuleEvaluationContext` — already authored.

**CEL variable map**:
- `nonImprisonableCount` → `1L` when the offence has ≥1 custodial result line and the effective indicator is `N`; `0L` otherwise

**Named offence-id sets**:
- `"nonImprisonableOffenceIds"` → `List.of(offenceId)` when triggered

---

## 5. Effective Indicator Resolution (Precedence Logic)

**Decision**: Implement inside `ReferencedataOffenceClient.getCustodialIndicator()`, not in the preprocessor or CEL.

**Algorithm**:
```
resolveIndicator(ReferencedataOffenceResponse offence) → Optional<String>:
  col = offence.custodialIndicator()
  if "Y".equals(col) or "N".equals(col) → return Optional.of(col)
  details = offence.details()
  if details != null and details.document() != null ...
    code = details.document().libra().custodialindicator().code()
    if "Y".equals(code) or "N".equals(code) → return Optional.of(code)
  return Optional.empty()    // fail-open: no warning
```

**Edge cases handled inside the client**:
- Column value other than "Y"/"N": ignored, fall through to JSON field
- JSON field value other than "Y"/"N": ignored, treat as no value
- `details` absent / `custodialindicator` node absent: null-safe traversal, return empty
- Network failure, timeout, non-2xx, empty offence list: `Optional.empty()` (fail-open)

---

## 6. CEL Expression

**Decision**: `nonImprisonableCount > 0`

**Rationale**: single-boolean-as-count follows existing rule patterns. All complexity lives in the preprocessor / client, keeping CEL trivial and reviewable by BAs.

---

## 7. YAML Rule Structure

**Rule file**: `src/main/resources/rules/DR-SENT-011.yaml` — already authored.
**Priority**: `2000` (after DR-SENT-001 at 1000, before DR-AGE-007 at 7000)
**Severity**: `WARNING` (advisory per spec; never to be upgraded per Constitution §VI)
**validationLevel**: `OFFENCE`
**Custodial short codes** (18 total):
```
IMP, YOI, DTO, EXTDVS, EXTDVSU, EXTIVS, STSDY, SPECC, SPECCC, SPECCD,
SUSPS, SUSPSS, SUSPSNI, SUSPSNR, SUSPSD, SUSPSDS, SUSPSDNI, SUSPSDNR
```

---

## 8. Integration Test Strategy

**Decision**: Rewrite `NonImprisonableOffenceRuleIT` to use WireMock stubs (via `REFERENCEDATA_OFFENCE_WIRE_MOCK` from `IntegrationTestBase`) instead of populating `custodialIndicator` / `libraCustodialIndicatorCode` on `OffenceDto` via the request JSON.

**Per-test WireMock stubs**: each AC registers a stub mapping the test offence's `cjsoffencecode` query parameter to a response body containing the desired `custodialIndicator` and/or `details` structure.

**Not duplicated** (per `design_rules.md`): runtime override / severity ceiling tests are NOT included — those are already proven once in `ValidationRuleOverrideIntegrationTest`.

**What the IT covers**:
- AC1: `custodialIndicator: "Y"` in stub → no warning
- AC2: `custodialIndicator: "N"` in stub → warning (text, offence ID, severity, isValid = true)
- AC3: column null, `details...code: "N"` → warning
- AC4: column null, `details...code: "Y"` → no warning
- AC5: column `"Y"`, `details...code: "N"` → no warning (column wins)
- AC6: column null, no `custodialindicator` node → no warning
- AC7: non-custodial short code, column `"N"` — no stub registered (no network call); no warning
- Coexistence with DR-SENT-001 and DR-RESTRAO-010 warnings
- All 18 custodial short codes via parameterised test (extend AC2)

---

## 9. TDD Order (Constitution §VIII)

1. **Revert** `libs.versions.toml` to `26.25`
2. **Extend** `ReferencedataOffenceResponse` (records + `@JsonIgnoreProperties`)
3. **Write failing** `ReferencedataOffenceClientTest` `getCustodialIndicator` scenarios → FAIL (method missing)
4. **Implement** `getCustodialIndicator()` + `resolveIndicator()` → client test GREEN
5. **Rewrite failing** `NonImprisonableOffencePreprocessorTest` (mock client) → FAIL (preprocessor still has old implementation)
6. **Rewrite** `NonImprisonableOffencePreprocessor` (inject client, call `getCustodialIndicator`) → preprocessor test GREEN
7. **Rewrite** `NonImprisonableOffenceRuleIT` (WireMock stubs) → IT GREEN
8. **Update** `ValidationRuleTestHelper` — remove `offenceWithCustodialIndicator` (fields no longer on DTO)
9. **Update** `wiremock/mappings/referencedataoffences-stub.json` — add custodial indicator stubs for live API tests
10. Run `gradle build` → code-reviewer → qa → spec-validator
