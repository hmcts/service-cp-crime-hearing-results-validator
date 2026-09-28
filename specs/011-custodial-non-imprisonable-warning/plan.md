# Implementation Plan: CRA-256 – Custodial Sentence Recorded Against Non-Imprisonable Offence (Warning)

**Branch**: `dev/CRA-256-custodial-sentence-recorded-against-non-imprisonable-offence` | **Date**: 2026-09-25 (v3 updated 2026-10-06) | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/011-custodial-non-imprisonable-warning/spec.md`

## Summary

Implement validation rule `DR-SENT-011` that emits a `WARNING` when a custodial result code is recorded against an offence whose effective custodial indicator resolves to non-imprisonable (`N`). The warning is advisory and does not block sharing.

The rule requires:
1. A **new cacheable method** `getCustodialIndicator(offenceCode)` on `ReferencedataOffenceClient` (same pattern as `lookupMisCode`), applying the column-over-`details_json` precedence logic client-side
2. **Extended** `ReferencedataOffenceResponse` with `custodialIndicator` (top-level) and nested `details` records for the JSON fallback path
3. A **new `ValidationPreprocessor`** (`NonImprisonableOffencePreprocessor`, type `"non-imprisonable-offence"`) that injects `ReferencedataOffenceClient` and calls `getCustodialIndicator()`
4. A **new context record** (`NonImprisonableOffenceContext`) — already authored
5. A **new YAML rule** (`DR-SENT-011.yaml`) — already authored
6. **Unit + integration tests** (TDD — tests first; ITs use WireMock stubs for the reference data API)

**No changes to `OffenceDto` or `api-cp-crime-hearing-results-validator`**. `libs.versions.toml` reverts to `26.25`.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), `ReferencedataOffenceClient` (existing, extended)
**Storage**: PostgreSQL 15.3 — existing `validation_rule` table covers runtime override; no schema change
**Testing**: JUnit 5 + Mockito + AssertJ (unit), `IntegrationTestBase` + MockMvc + TestContainers + WireMock (integration)
**Target Platform**: Azure-hosted Spring Boot service; local port 4550
**Project Type**: Web service (validation endpoint)
**Performance Goals**: No Gatling assertions introduced for a single rule. The reference data lookup fires at most once per offence per validation request and is Caffeine-cached by `offenceCode` — same performance profile as the existing `lookupMisCode` usage in DR-SEX-008.
**Constraints**: `gradle build` must pass with zero Checkstyle warnings and zero PMD violations; `maxWarnings = 0`
**Scale/Scope**: Extended `ReferencedataOffenceResponse` (+2 new fields / nested records), one new method on existing client, one new preprocessor, one new context record (already shipped), one new YAML rule (already shipped) — ~150 LoC net new Java.

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. YAML/CEL Rule-First** | PASS | `DR-SENT-011.yaml` already authored; all rule policy (codes, severity, message) lives in YAML |
| **II. Constructor Injection & Immutable DTOs** | PASS | `NonImprisonableOffenceContext` is a Java record; `NonImprisonableOffencePreprocessor` will use constructor injection + `private final`, no `@Autowired`; nested records in `ReferencedataOffenceResponse` are also records |
| **III. Layered Architecture & Preprocessor Dispatch** | PASS | New preprocessor registered via `PreprocessorRegistry` by type qualifier; no hard-wiring |
| **IV. Spec-Driven Build Loop** | PASS | Full loop: spec → YAML → tests → code → code-reviewer → qa → spec-validator |
| **V. HMCTS Standards Compliance** | PASS | Gradle build, root package `uk.gov.hmcts.cp`, SLF4J logging, Java 25 |
| **VI. Severity Ceiling, Never Promote** | PASS | YAML severity is `WARNING`; runtime override can only cap to lower — no promotion path |
| **VII. No System.out/System.err** | PASS | All diagnostic output via SLF4J (`@Slf4j` or `LoggerFactory`) |
| **VIII. TDD** | PASS | Failing tests for `getCustodialIndicator()` and `NonImprisonableOffencePreprocessor` authored before production code |

## Project Structure

### Documentation (this feature)

```text
specs/011-custodial-non-imprisonable-warning/
├── plan.md                           # This file
├── research.md                       # Phase 0 output (updated v3)
├── data-model.md                     # Phase 1 output (updated v3)
├── quickstart.md                     # Phase 1 output
├── CRA-256.http                      # IDE-runnable HTTP scratch file (all AC scenarios, US1–US5 + edge cases)
├── contracts/
│   └── upstream-dependency.md        # Phase 1 output (updated v3 — no longer blocking)
└── tasks.md                          # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
src/main/resources/rules/
└── DR-SENT-011.yaml                                    # DONE – rule definition

src/main/java/uk/gov/hmcts/cp/services/referencedata/
├── ReferencedataOffenceResponse.java                   # EXTEND – add custodialIndicator + nested details records
└── ReferencedataOffenceClient.java                     # EXTEND – add getCustodialIndicator(offenceCode) method

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── NonImprisonableOffenceContext.java                  # DONE – RuleEvaluationContext record
└── NonImprisonableOffencePreprocessor.java             # REWRITE – inject client, call getCustodialIndicator

gradle/libs.versions.toml                               # REVERT – back to 26.25

src/test/java/uk/gov/hmcts/cp/services/referencedata/
└── ReferencedataOffenceClientTest.java                 # EXTEND – add getCustodialIndicator scenarios

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
└── NonImprisonableOffencePreprocessorTest.java         # REWRITE – mock client, no DTO fields

src/test/java/uk/gov/hmcts/cp/integration/
└── NonImprisonableOffenceRuleIT.java                   # REWRITE – WireMock stubs, no DTO fields in JSON

src/test/java/uk/gov/hmcts/cp/services/rules/
└── ValidationRuleTestHelper.java                       # REMOVE offenceWithCustodialIndicator helper

wiremock/mappings/
└── referencedataoffences-stub.json                     # EXTEND – add custodial indicator stubs
```

**Structure Decision**: Single-project layout (existing). No new directories. All artefacts slot into existing source trees.

## Phase 0: Research Findings

Full findings in [research.md](research.md). Key decisions (v3):

| Decision | Outcome |
|----------|---------|
| Rule ID | `DR-SENT-011` |
| YAML priority | `11000` (suffix × 1000 convention; slots after DR-SEX-008 @ 8000) |
| Preprocessor type qualifier | `"non-imprisonable-offence"` |
| Preprocessor class | `NonImprisonableOffencePreprocessor` |
| Context record | `NonImprisonableOffenceContext` |
| CEL expression | `nonImprisonableCount > 0` |
| CEL variable | `nonImprisonableCount` (Long: 1 = non-imprisonable + custodial result; 0 = safe) |
| Custodial indicator source | `ReferencedataOffenceClient.getCustodialIndicator(offenceCode)` — reference data API lookup (same endpoint as `lookupMisCode`); precedence applied client-side |
| OffenceDto / upstream JAR | **No change** — `libs.versions.toml` reverted to `26.25` |
| Blocked? | **No** — implementation can proceed immediately |

## Phase 1: Design

### YAML Rule (`DR-SENT-011.yaml`) — already authored

No changes required. The YAML references `preprocessing.type: "non-imprisonable-offence"` and exposes `nonImprisonableCount` via the CEL condition. The `filterShortCodes` list contains all 18 custodial short codes (IMP, YOI, DTO, EXTDVS, EXTDVSU, EXTIVS, STSDY, SPECC, SPECCC, SPECCD, SUSPS, SUSPSS, SUSPSNI, SUSPSNR, SUSPSD, SUSPSDS, SUSPSDNI, SUSPSDNR).

### `ReferencedataOffenceResponse` extension

Add `custodialIndicator` (top-level field from `custodial_indicator` DB column) and nested `details` records for the JSON fallback. All records `@JsonIgnoreProperties(ignoreUnknown = true)`.

```java
// Updated top-level record
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReferencedataOffenceResponse(
    String offenceId,
    String misCode,
    String custodialIndicator,       // NEW — from custodial_indicator column
    OffenceDetails details            // NEW — nested JSON document object
) {}

// New nested records (package-private inner records or same package)
@JsonIgnoreProperties(ignoreUnknown = true)
record OffenceDetails(OffenceDocument document) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record OffenceDocument(LibraSection libra) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record LibraSection(CustodialIndicatorCode custodialindicator) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record CustodialIndicatorCode(String code) {}
```

**Jackson behaviour**: `@JsonIgnoreProperties(ignoreUnknown = true)` means null-safe deserialization. If `details` is absent or `custodialindicator` is absent, the nested record fields will be `null` — no NPE from Jackson. Null-checking in `getCustodialIndicator()` guards the rest.

### `ReferencedataOffenceClient.getCustodialIndicator()`

```java
@Cacheable(value = "referencedataOffences", key = "'ci:' + #offenceCode", unless = "#result == null")
public Optional<String> getCustodialIndicator(final String offenceCode) {
    Optional<String> indicator = Optional.empty();
    if (properties.enabled() && offenceCode != null && !offenceCode.isBlank()) {
        indicator = fetchCustodialIndicator(offenceCode);
    }
    return indicator;
}

private Optional<String> fetchCustodialIndicator(final String offenceCode) {
    Optional<String> indicator = Optional.empty();
    try {
        final URI uri = UriComponentsBuilder.fromUriString(properties.offenceUrlTemplate())
                .build(Map.of("offenceCode", offenceCode));
        final RequestEntity.HeadersBuilder<?> requestBuilder = RequestEntity.get(uri)
                .header(HttpHeaders.ACCEPT, properties.acceptHeader());
        final String userId = MDC.get(TracingFilter.USER_ID);
        if (userId != null && !userId.isBlank()) {
            requestBuilder.header(CJSCPPUID_HEADER, userId);
        }
        final ResponseEntity<ReferencedataOffencesListResponse> response =
                restTemplate.exchange(requestBuilder.build(), ReferencedataOffencesListResponse.class);
        final ReferencedataOffenceResponse offence = firstOffenceOf(response.getBody());
        if (offence != null) {
            indicator = resolveIndicator(offence);
        } else {
            log.debug("No offence returned for offenceCode={}", Encode.forJava(offenceCode));
        }
    } catch (RestClientException | IllegalArgumentException e) {
        log.warn("Reference-data custodialIndicator lookup failed for offenceCode={} ({}): {}",
                Encode.forJava(offenceCode), e.getClass().getSimpleName(), e.getMessage());
    }
    return indicator;
}

private static Optional<String> resolveIndicator(final ReferencedataOffenceResponse offence) {
    final String col = offence.custodialIndicator();
    if ("Y".equals(col) || "N".equals(col)) {
        return Optional.of(col);
    }
    if (offence.details() != null
            && offence.details().document() != null
            && offence.details().document().libra() != null
            && offence.details().document().libra().custodialindicator() != null) {
        final String code = offence.details().document().libra().custodialindicator().code();
        if ("Y".equals(code) || "N".equals(code)) {
            return Optional.of(code);
        }
    }
    return Optional.empty();
}
```

**Cache note**: `getCustodialIndicator` uses the `referencedataOffences` Caffeine cache with key `'ci:' + offenceCode` — the `"ci:"` prefix keeps custodial-indicator entries separate from `lookupMisCode` entries (which use the bare `offenceCode`). Both methods share the same `referencedataOffences` cache region but can never cross-contaminate because their key spaces are disjoint.

### `NonImprisonableOffencePreprocessor` rewrite

```
@Component, @RequiredArgsConstructor
type() = "non-imprisonable-offence"

private final ReferencedataOffenceClient referencedataOffenceClient;

preprocess(request, config):
  custodialCodes = upperSet(config.filterShortCodes())
  offenceById = index(request.getOffences(), OffenceDto::getOffenceId)
  resultsByOffence = groupResultsByOffence(request)           // PreprocessorHelper

  result = LinkedHashMap<String, NonImprisonableOffenceContext>
  for offenceId, lines in resultsByOffence:
    hasCustodialResult = anyShortCodeIn(lines, custodialCodes)    // cheaper local check first
    if not hasCustodialResult: continue
    offence = offenceById.get(offenceId)
    if offence == null: continue
    indicator = referencedataOffenceClient.getCustodialIndicator(offence.getOffenceCode())
    nonImprisonableCount = indicator.map(v -> "N".equals(v) ? 1L : 0L).orElse(0L)
    result.put(offenceId, new NonImprisonableOffenceContext(offenceId, nonImprisonableCount))

  return result
```

**Key design points:**
- Custodial short-code filter runs first (local, cheap) — no network call for offences without custodial results (same principle as `SexualOffenceNotificationPreprocessor`'s `isConvicted` early-exit)
- `getCustodialIndicator` is fail-open: `Optional.empty()` → `nonImprisonableCount = 0` → no warning
- No `resolveIndicator` logic in the preprocessor — all indicator resolution is inside the client

### Integration test strategy

Integration tests (`NonImprisonableOffenceRuleIT`) use `REFERENCEDATA_OFFENCE_WIRE_MOCK` from `IntegrationTestBase`. The request JSON sends offences with an `offenceCode` (e.g. `"RT88026"`) and the WireMock stub maps that code to a response containing the desired `custodialIndicator` / `details` combination. This mirrors `SexualOffenceNotificationRuleIT`'s pattern exactly.

**Per-test stubs** rather than per-class stubs: each test scenario registers its own WireMock mapping using `REFERENCEDATA_OFFENCE_WIRE_MOCK.stubFor(...)` in a `@BeforeEach` or inline, and resets via `REFERENCEDATA_OFFENCE_WIRE_MOCK.resetAll()` in `@AfterEach`.

**What the IT covers** (unchanged ACs, new mechanics):
- AC1: `custodialIndicator: "Y"` in stub → no warning
- AC2: `custodialIndicator: "N"` in stub → warning with correct text and offence ID
- AC3: `custodialIndicator: null`, `details.document.libra.custodialindicator.code: "N"` in stub → warning
- AC4: `custodialIndicator: null`, `details.document.libra.custodialindicator.code: "Y"` in stub → no warning
- AC5: `custodialIndicator: "Y"`, `details.document.libra.custodialindicator.code: "N"` in stub → no warning (column wins)
- AC6: `custodialIndicator: null`, no `details.document.libra.custodialindicator` in stub → no warning
- AC7: non-custodial short code, `custodialIndicator: "N"` in stub → no warning (no lookup call at all)
- Coexistence with DR-SENT-001 and DR-RESTRAO-010 warnings
- `isValid = true` and Share button not blocked

### Implementation Order (TDD — Constitution §VIII)

1. **Revert** `libs.versions.toml` to `26.25` — removes DTO fields, unblocks compilation
2. **Extend** `ReferencedataOffenceResponse` — add `custodialIndicator` and nested `OffenceDetails` records (Java records, `@JsonIgnoreProperties(ignoreUnknown = true)`)
3. **Write failing** `ReferencedataOffenceClientTest` getCustodialIndicator scenarios — column-only, JSON-only, column-wins, empty-response, timeout; run `gradle test --tests "...ReferencedataOffenceClientTest"` — FAIL at assertion level
4. **Implement** `getCustodialIndicator()` + `resolveIndicator()` in `ReferencedataOffenceClient` — client test goes GREEN
5. **Rewrite failing** `NonImprisonableOffencePreprocessorTest` — mock `ReferencedataOffenceClient.getCustodialIndicator()`; run test — FAIL at assertion level (preprocessor not yet updated)
6. **Rewrite** `NonImprisonableOffencePreprocessor` — inject client, call `getCustodialIndicator()` — unit test goes GREEN
7. **Rewrite failing** `NonImprisonableOffenceRuleIT` — WireMock stubs replacing DTO fields in JSON; run `gradle test --tests "...NonImprisonableOffenceRuleIT"` — confirms end-to-end wiring
8. **Update** `ValidationRuleTestHelper` — remove `offenceWithCustodialIndicator()` or change it to a plain offence builder without those fields
9. **Update** `wiremock/mappings/referencedataoffences-stub.json` — add custodial indicator variants for live API tests
10. **Run** full build loop: `gradle build` → code-reviewer agent → qa agent → spec-validator agent

### Dependency Tracking

| Item | Owner | Blocking? |
|------|-------|-----------|
| `OffenceDto` upstream DTO change | None — no longer needed | **NOT BLOCKING** |
| `libs.versions.toml` revert to 26.25 | This repo | YES — first task |
| `validation_rule` DB row for DR-SENT-011 | Ops / Liquibase migration (already in `V1.011__insert_dr_sent_011.sql`) | Optional at dev time; required for production |

## Complexity Tracking

No constitution violations. All principles satisfied without exception.

The only complexity worth noting: both `lookupMisCode` and `getCustodialIndicator` share the `referencedataOffences` Caffeine cache key space. This is intentional and safe — the cache stores `Optional<String>` results keyed by offence code, and the two methods never write different types to the same key. The `unless = "#result == null"` guard applies identically to both.
