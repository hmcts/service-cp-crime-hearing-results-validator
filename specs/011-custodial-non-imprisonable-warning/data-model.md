# Data Model: CRA-256 – Custodial Sentence Against Non-Imprisonable Offence

## Entities

### OffenceDto (upstream – `api-cp-crime-hearing-results-validator`) — UNCHANGED

No changes to `OffenceDto`. The preprocessor reads only `offenceCode` (existing field) to key the
reference-data lookup; it does not read any custodial-indicator field from the DTO.

| Field | Type | Notes |
|-------|------|-------|
| `offenceId` | `String` | Primary key for this offence in the hearing |
| `offenceCode` | `String` | CJS offence code — key for `ReferencedataOffenceClient.getCustodialIndicator()` |

### ReferencedataOffenceResponse (extended — `uk.gov.hmcts.cp.services.referencedata`)

`ReferencedataOffenceResponse` is extended with two new fields consumed by `getCustodialIndicator()`.
All fields remain `@JsonIgnoreProperties(ignoreUnknown = true)`.

| Field | Type | Source in API response |
|-------|------|------------------------|
| `offenceId` | `String` | existing — log correlation |
| `misCode` | `String` | existing — used by DR-SEX-008 |
| `custodialIndicator` | `String` | NEW — top-level field from `custodial_indicator` DB column |
| `details` | `OffenceDetails` | NEW — nested JSON object (the full offence document) |

Nested records (package-private, inside `ReferencedataOffenceResponse` or same package):

```
OffenceDetails          { OffenceDocument document }
OffenceDocument         { LibraFields libra }
LibraFields             { CustodialIndicatorCode custodialindicator }
CustodialIndicatorCode  { String code }
```

All nested records annotated `@JsonIgnoreProperties(ignoreUnknown = true)`.

### ReferencedataOffenceClient — new method `getCustodialIndicator`

```
getCustodialIndicator(offenceCode: String) → Optional<String>
```

- Same HTTP call as `lookupMisCode` (same URL template, same `Accept` header, same `CJSCPPUID` forwarding).
- Reads `offence.custodialIndicator()` first (column value); if null/blank, reads
  `offence.details().document().libra().custodialindicator().code()` (JSON fallback).
- Any value other than `"Y"` or `"N"` (case-sensitive) from either source is treated as absent.
- Returns `Optional.empty()` when: lookup disabled, blank `offenceCode`, non-2xx, timeout,
  empty offence list, or neither source has `"Y"`/`"N"` — same fail-open contract as `lookupMisCode`.
- `@Cacheable(value = "referencedataOffences", key = "#offenceCode", unless = "#result == null")` —
  reuses the same Caffeine cache as `lookupMisCode`; successful lookups cached by code, failures not.
- Logs at WARN on network/HTTP failure, DEBUG when neither source has a usable value.

---

## Effective Custodial Indicator Resolution (inside `getCustodialIndicator`)

```
resolveCustodialIndicator(offence):
  col = offence.custodialIndicator()
  if "Y".equals(col) or "N".equals(col): return Optional.of(col)
  code = offence.details()?.document()?.libra()?.custodialindicator()?.code()
  if "Y".equals(code) or "N".equals(code): return Optional.of(code)
  return Optional.empty()   // treated as imprisonable — no warning
```

---

### NonImprisonableOffenceContext (unchanged — `uk.gov.hmcts.cp.services.rules.cel`)

Java record implementing `RuleEvaluationContext`. One instance per offence that has ≥1 custodial result line.

| Field | Type | Description |
|-------|------|-------------|
| `offenceId` | `String` | The offence this context is anchored to |
| `nonImprisonableCount` | `long` | `1L` if effective indicator = N; `0L` if Y or absent |

**`toCelContext()`** returns `Map.of("nonImprisonableCount", nonImprisonableCount)`.

**`getOffenceIdSet("nonImprisonableOffenceIds")`** returns `List.of(offenceId)`.

**`defendantName()`** returns `null` — this rule is offence-scoped, not defendant-scoped.

**`allOffenceIds()`** returns `List.of(offenceId)`.

---

### NonImprisonableOffencePreprocessor — updated

Injects `ReferencedataOffenceClient` (constructor injection, `private final`). Follows the same
pattern as `SexualOffenceNotificationPreprocessor`:

```
preprocess(request, config):
  custodialCodes = upperSet(config.filterShortCodes())
  offenceById = index(request.getOffences(), OffenceDto::getOffenceId)
  resultsByOffence = groupResultsByOffence(request)

  result = LinkedHashMap<String, NonImprisonableOffenceContext>
  for offenceId, lines in resultsByOffence:
    hasCustodialResult = anyShortCodeIn(lines, custodialCodes)   // cheaper check first
    if not hasCustodialResult: continue
    offence = offenceById.get(offenceId)
    if offence == null: continue
    indicator = referencedataOffenceClient.getCustodialIndicator(offence.getOffenceCode())
    nonImprisonableCount = indicator.map(v -> "N".equals(v) ? 1L : 0L).orElse(0L)
    result.put(offenceId, new NonImprisonableOffenceContext(offenceId, nonImprisonableCount))

  return result
```

---

## Custodial Short-Code Set (from DR-SENT-011 YAML `filterShortCodes`)

```
IMP, YOI, DTO,
EXTDVS, EXTDVSU, EXTIVS, STSDY,
SPECC, SPECCC, SPECCD,
SUSPS, SUSPSS, SUSPSNI, SUSPSNR, SUSPSD, SUSPSDS, SUSPSDNI, SUSPSDNR
```

(18 codes total; stored upper-cased in `PreprocessorHelper.upperSet`.)

---

## State Transitions

This rule is stateless — no DB writes. The `DR-SENT-011.yaml` rule may have a row in the `validation_rule` table (id, enabled, severity) for runtime override, but the table schema is unchanged.

---

## Source Code Locations

| Artefact | Path |
|----------|------|
| YAML rule | `src/main/resources/rules/DR-SENT-011.yaml` |
| Context record | `src/main/java/uk/gov/hmcts/cp/services/rules/cel/NonImprisonableOffenceContext.java` |
| Preprocessor | `src/main/java/uk/gov/hmcts/cp/services/rules/cel/NonImprisonableOffencePreprocessor.java` |
| Reference data client | `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceClient.java` |
| Reference data response | `src/main/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceResponse.java` |
| Preprocessor unit test | `src/test/java/uk/gov/hmcts/cp/services/rules/cel/NonImprisonableOffencePreprocessorTest.java` |
| Client unit test | `src/test/java/uk/gov/hmcts/cp/services/referencedata/ReferencedataOffenceClientTest.java` |
| Integration test | `src/test/java/uk/gov/hmcts/cp/integration/NonImprisonableOffenceRuleIT.java` |
