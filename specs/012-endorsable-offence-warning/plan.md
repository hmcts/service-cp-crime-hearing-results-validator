# Implementation Plan: Endorsable Offence Warning (CRA-336)

**Branch**: `CRA-336-endorsable-offence-warning` | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/012-endorsable-offence-warning/spec.md`

## Summary

Add validation rule DR-END-012: when a final result (Category F) is recorded against an endorsable offence (endorsable_flag is TRUE) and no satisfying endorsement, disqualification, or special reasons result is present, return a WARNING-severity issue at offence level. The check is advisory only and does not block saving or sharing. Follows the per-offence, reference-data-backed, YAML/CEL pattern established by DR-SENT-011.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: Spring Boot 4, org.projectnessie.cel, Caffeine, WireMock (test), TestContainers PostgreSQL 15.3 (test)  
**Storage**: PostgreSQL 15.3 (validation_rule override table only; rule logic is stateless)  
**Testing**: JUnit 5 + Mockito + AssertJ + MockMvc + WireMock + TestContainers  
**Target Platform**: Azure (Linux container, Kubernetes 4550)  
**Project Type**: Spring Boot web service (validation microservice)  
**Performance Goals**: Fail-fast reference-data lookup (configurable connect/read timeout, Caffeine-cached); same per-validation-call latency budget as DR-SENT-011  
**Constraints**: Fail-open on reference-data lookup failure (no warning rather than error); severity is WARNING only — never ERROR; no `System.out` / `System.err`; no wildcard imports; Google Checkstyle zero warnings  
**Scale/Scope**: One context per offence per validation request; number of offences per hearing typically 1–5

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. YAML/CEL Rule-First | PASS | YAML file `DR-END-012.yaml` is written before any Java; rule config (code lists, CEL expression, severity, message) lives entirely in YAML |
| II. Constructor Injection & Immutable DTOs | PASS | `EndorsableOffencePreprocessor` uses `@RequiredArgsConstructor`; `EndorsableOffenceContext` is a record |
| III. Layered Architecture & Registry Dispatch | PASS | Preprocessor selected via `PreprocessorRegistry.require("endorsable-offence")`; no hard-wiring |
| IV. Spec-Driven Build Loop | PASS | This plan is produced before any Java is written; code-reviewer → qa → spec-validator loop applies |
| V. HMCTS Standards | PASS | Gradle, Spring Boot 4, Java 25, `uk.gov.hmcts.cp.*`, SLF4J |
| VI. Severity Ceiling, Never Promote | PASS | YAML sets WARNING; DB ceiling can only lower it; no promotion path |
| VII. No System.out / SLF4J Only | PASS | All logging via `@Slf4j` / SLF4J |
| VIII. TDD | PASS | Failing tests authored before production code (enforced by task order) |

**Re-check post-design**: All principles confirmed PASS. No violations or deviations.

## Project Structure

### Documentation (this feature)

```text
specs/012-endorsable-offence-warning/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code changes (repository root)

```text
src/main/resources/rules/
└── DR-END-012.yaml                        # NEW — validation rule (YAML/CEL)

src/main/java/uk/gov/hmcts/cp/
├── services/referencedata/
│   ├── ReferencedataOffenceResponse.java          # MODIFY — add endorsableFlag field
│   └── ReferencedataOffenceClient.java            # MODIFY — add getEndorsableFlag() method
└── services/rules/cel/
    ├── PreprocessingDefinition.java               # MODIFY — add satisfyingShortCodes field
    ├── EndorsableOffenceContext.java              # NEW — context record
    └── EndorsableOffencePreprocessor.java         # NEW — preprocessor @Component

src/test/java/uk/gov/hmcts/cp/
├── services/rules/cel/
│   └── EndorsableOffencePreprocessorTest.java    # NEW — unit tests
└── integration/
    └── EndorsableOffenceRuleIT.java              # NEW — integration tests
```

## Complexity Tracking

No constitution violations. No complexity justification required.

---

## IT Test Coverage Matrix

Every Jira AC must have a dedicated `@Nested` class in `EndorsableOffenceRuleIT`.
Two gaps were identified during the v2-PDF review (2026-10-12) and are recorded here as required additions.

| AC | IT test class | Status | Notes |
|----|--------------|--------|-------|
| AC1 | `Ac1Warning` | ✅ present | Triggers warning; verifies severity=WARNING, validationLevel=OFFENCE, message text, affectedOffences |
| AC2 | `Ac2SatisfyingResultPresent` | ✅ present | LEP + IMP → no warning |
| AC3 | `Ac3ExcludedFinalOnly` | ✅ present | WDRN → no warning |
| AC4 | `Ac4NonEndorsable` | ✅ present | Two tests: `flag=false` (CD98075) + `flag absent` (see gap below) |
| AC5 | `Ac5SatisfyingResultClears` | ✅ present | IMP + DDO → no warning (added Phase 9 T029) |
| AC6 | `Ac6MultipleOffences` | ✅ present | One endorsable + one non-endorsable → only endorsable warned |
| AC7 | `Ac7Coexistence` | ✅ present | DR-END-012 + DR-SENT-001 both fire in one response |

### AC5 — IT test (completed Phase 9 T029)

```java
@Nested
@DisplayName("AC5 — warning clears when a satisfying result is added")
class Ac5SatisfyingResultClears {

    @Test
    void givenEndorsableOffenceWithFinalAndSatisfyingResult_should_returnNoWarning()
            throws Exception {
        stubEndorsable("end-ac5-clr", true);

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest("end-ac5-clr", "IMP", "DDO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DR_ENDORSEMENT_012_WARNINGS, empty()));
    }
}
```

Uses a satisfying code from the **driving disqualification** family (`DDO`) to complement `LEP` already used in AC2,
covering two distinct satisfying families at the integration level as recommended in the Jira implementation notes.

### AC4 "flag absent" gap

`givenEndorsableFlagAbsent_should_returnNoWarning` previously stubbed `endorsableFlag: false` which duplicated the
`flag=false` path. A true "absent" integration test stubs a response that omits `endorsableFlag` entirely (resolved Phase 9 T028):

```java
// In stubEndorsable helper — add a second helper:
private static void stubEndorsableAbsent(final String offenceCode) {
    stubReferencedataOffenceRawBody(offenceCode,
            "{\"offences\":[{\"offenceId\":\"ref-id\"}]}");  // endorsableFlag field omitted
}
```

Update `givenEndorsableFlagAbsent_should_returnNoWarning` to call `stubEndorsableAbsent("end-ac4-absent")`.

---

## Phase 0: Research (complete)

See [research.md](research.md) for all decisions. Summary:

1. **New preprocessor required**: `EndorsableOffencePreprocessor` qualifier `"endorsable-offence"` — no existing preprocessor covers per-offence endorsable_flag + satisfying codes logic.
2. **Reference data source**: `ReferencedataOffenceClient.getEndorsableFlag(offenceCode)` — new method backed by the existing `cpp-context-referencedata-offences` endpoint; `endorsableFlag` (Boolean) added to `ReferencedataOffenceResponse`.
3. **`PreprocessingDefinition` extension**: Add `satisfyingShortCodes` list — distinct from `excludedFinalShortCodes` (which already exists).
4. **New context record**: `EndorsableOffenceContext(offenceId, qualifyingCount)`.
5. **Rule ID**: `DR-END-012`, priority `12000`.
6. **Integration test pattern**: WireMock stub per unique offenceCode, reset in `@BeforeEach`, following `NonImprisonableOffenceRuleIT`.

---

## Phase 1: Design & Contracts

### YAML Rule Design

**File**: `src/main/resources/rules/DR-END-012.yaml`

```yaml
rule:
  id: "DR-END-012"
  title: "Endorsement/disqualification missing on endorsable offence"
  description: >-
    Warns when a final result (Category F) is recorded against an endorsable
    offence (endorsable_flag is TRUE) and no endorsement, driving disqualification,
    or special reasons result is present. Advisory only — does not block sharing.
  priority: 12000
  enabled: true

  preprocessing:
    type: "endorsable-offence"
    excludedFinalShortCodes:
      - WDRN
      - WDRNOFF
      - DISM
      - DINE
      - DINI
      - DISCH
      - DISC
      - CTROF
      - IREMFILE
      - ERR
      - ERRF
      - DHD
      - ONI
      - DCS
      - DCCFSA
      - DCCFSTA
      - CQUASH
      - IQUASH
      - RESTRAO
      - STAYP
      - RBBH
      - SOCOR
      - PDW
      - RBBO
    satisfyingShortCodes:
      # Endorsement
      - LEP
      - LEN
      - LEA
      # Obligatory disqualification
      - DDO
      - DDOL
      - DDOR
      - DDOTE
      - DDOTEL
      # Discretionary disqualification
      - DDD
      - DDDL
      - DDDT
      - DDDTL
      - DDDTO
      # Points (totting) disqualification
      - DDP
      - DDPL
      - DDPR
      - DDPTE
      - DDPTEL
      # Obligatory disqualification until extended test — reduction for course
      - DDRCOT
      # Special reasons
      - NESR
      - NDSR

  conditions:
    - id: "AC1"
      name: "Endorsable offence missing endorsement, disqualification or special reasons"
      expression: "qualifyingCount > 0"
      severity: WARNING
      messageTemplate: >-
        This offence is endorsable. Add an endorsement, disqualification or special reasons result.
      affectedOffenceSet: "endorsableOffenceIds"
      validationLevel: OFFENCE
```

### Data Model

See [data-model.md](data-model.md).

### Preprocessor Algorithm

`EndorsableOffencePreprocessor.preprocess(request, config)` iterates every offence in the request:

```
For each offence O in request.offences:
  lines = resultsByOffence.getOrDefault(O.offenceId, [])
  finalLines = lines.filter(category == F)

  hasNonExcludedFinal = finalLines.anyMatch(code NOT in excludedFinalShortCodes)
  // Short-circuit: skip the reference-data call when no qualifying final line exists.
  // This avoids unnecessary network calls for offences with only non-F or excluded results,
  // and preserves the "no reference-data call for non-custodial results" invariant tested
  // by NonImprisonableOffenceRuleIT AC7.
  if NOT hasNonExcludedFinal:
    emit EndorsableOffenceContext(O.offenceId, qualifyingCount=0)
    continue

  endorsableFlag = referencedataOffenceClient.getEndorsableFlag(O.offenceCode)
  if NOT Boolean.TRUE.equals(endorsableFlag.orElse(null)):
    emit EndorsableOffenceContext(O.offenceId, qualifyingCount=0)
    continue

  hasSatisfying = lines.anyMatch(code in satisfyingShortCodes)  // any category, not just F
  qualifying = !hasSatisfying

  emit EndorsableOffenceContext(O.offenceId, qualifyingCount = qualifying ? 1L : 0L)
```

**Key invariants**:
- All short-code comparisons are case-insensitive (normalised to upper case via `upperSet`)
- `endorsable_flag is TRUE` triggers the check; FALSE, null, or absent → treat as non-endorsable (fail-open, no warning)
- Satisfying codes checked across ALL result lines (not only Category F), matching Jira AC2 semantics
- Excluded codes checked only on Category F lines, matching Jira AC3 semantics
- Context is emitted for EVERY offence (keyed by `offenceId`), even non-qualifying ones, so the CEL engine always has a context to evaluate

### `ReferencedataOffenceClient` Extension

Add to `ReferencedataOffenceResponse`:
```java
Boolean endorsableFlag  // true = endorsable, false = non-endorsable, null = absent
```

Add to `ReferencedataOffenceClient`:
```java
@Cacheable(value = "referencedataOffences", key = "'ef:' + #offenceCode", unless = "#result == null")
public Optional<Boolean> getEndorsableFlag(String offenceCode)
```

Cache key prefix `"ef:"` avoids cross-contamination with `"ci:"` (custodial indicator) and bare-key (misCode) entries in the shared `referencedataOffences` cache.

### `PreprocessingDefinition` Extension

Add to the record:
```java
// Endorsable-offence-specific short-code lists
List<String> satisfyingShortCodes
```

### Integration Test WireMock Stubs

Two stub response shapes:
```java
// endorsableFlag = true (endorsable)
"{\"offences\":[{\"offenceId\":\"ref-id\",\"endorsableFlag\":true}]}"

// endorsableFlag = false (non-endorsable)
"{\"offences\":[{\"offenceId\":\"ref-id\",\"endorsableFlag\":false}]}"

// Absent / lookup failure (WireMock not stubbed → RestClientException → fail-open)
```

### Agent Context Update

The plan reference in `CLAUDE.md` will be updated to point to `specs/012-endorsable-offence-warning/plan.md`.

---

## Phase 2: Task List

See `tasks.md` (generated by `/speckit-tasks`).

### IT-test gaps resolved (Phase 9, 2026-10-12)

Both gaps from the v2-PDF AC review have been resolved in Phase 9:

1. **`Ac5SatisfyingResultClears`** — added (T029).
2. **`Ac4NonEndorsable.givenEndorsableFlagAbsent_should_returnNoWarning`** — `stubEndorsableAbsent` helper added and test updated (T028).

---

## Appendix: Complete Code Lists

### Excluded final short codes (24 codes)
WDRN, WDRNOFF, DISM, DINE, DINI, DISCH, DISC, CTROF, IREMFILE, ERR, ERRF, DHD, ONI, DCS, DCCFSA, DCCFSTA, CQUASH, IQUASH, RESTRAO, STAYP, RBBH, SOCOR, PDW, RBBO

### Satisfying short codes (21 codes)
**Endorsement** (3): LEP, LEN, LEA  
**Obligatory disqualification** (5): DDO, DDOL, DDOR, DDOTE, DDOTEL  
**Discretionary disqualification** (5): DDD, DDDL, DDDT, DDDTL, DDDTO  
**Points (totting) disqualification** (5): DDP, DDPL, DDPR, DDPTE, DDPTEL  
**Course reduction** (1): DDRCOT  
**Special reasons** (2): NESR, NDSR  
