# Implementation Plan: Electronic Monitoring Status Validation

**Branch**: `dev/CRA-38-electronic-monitoring-status` | **Date**: 2026-11-18 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `specs/018-electronic-monitoring-status/spec.md`

## Summary

Adds a new YAML+CEL validation rule (`DR-EMON-017`) that fires an ERROR when a caseworker enters
a final or non-exempt interim result on an EM-flagged offence without also providing a continuing
or ending electronic-monitoring result. The rule is backed by a new `ElectronicMonitoringPreprocessor`
(`@Component` with qualifier `"electronic-monitoring-status"`) and an `ElectronicMonitoringContext`
record per defendant. A Liquibase migration (`V1.019`) registers the rule in the `validation_rule`
table so runtime severity-ceiling overrides are possible.

---

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Liquibase, Caffeine  
**Storage**: PostgreSQL 15.3 (`validation_rule` table — runtime overrides only)  
**Testing**: JUnit 5 + Mockito + AssertJ (unit), TestContainers + MockMvc (integration), RestTemplate (API)  
**Target Platform**: Linux / Azure (AKS) — also runs locally on macOS via Docker Compose  
**Project Type**: Web service (Spring Boot REST API)  
**Performance Goals**: No new I/O paths — preprocessor is in-memory; p95 latency budget unchanged  
**Constraints**: Zero Checkstyle warnings, PMD clean, no wildcard imports, SLF4J only  
**Scale/Scope**: One new preprocessor + one new YAML rule; extends an existing twelve-rule engine

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. YAML/CEL Rule-First | ✅ PASS | YAML created before any Java code (DR-EMON-017.yaml is the contract) |
| II. Constructor Injection & Immutable DTOs | ✅ PASS | `ElectronicMonitoringContext` will be a Java record; preprocessor uses `@RequiredArgsConstructor` |
| III. Layered Architecture & Preprocessor Dispatch | ✅ PASS | New preprocessor registered via `PreprocessorRegistry`; `CelValidationRule` dispatches by `preprocessing.type` |
| IV. Spec-Driven Build Loop | ✅ PASS | spec → plan → tasks → implement → code-review → qa → spec-validate |
| V. HMCTS Standards Compliance | ✅ PASS | Gradle, Java 25, `uk.gov.hmcts.cp.*`, SLF4J, LogstashEncoder |
| VI. Severity Ceiling, Never Promote | ✅ PASS | YAML sets ERROR as ceiling; DB row can only lower to WARNING |
| VII. No `System.out` / `System.err` | ✅ PASS | All logging via SLF4J `@Slf4j` |
| VIII. TDD | ✅ PASS | Failing tests written first (see task ordering in tasks.md) |

No violations. No Complexity Tracking entry required.

---

## Project Structure

### Documentation (this feature)

```text
specs/018-electronic-monitoring-status/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/           ← Phase 1 output (validation API contract delta)
└── tasks.md             ← Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
src/main/resources/rules/
└── DR-EMON-017.yaml                              ← NEW: validation rule (YAML first)

src/main/resources/db/migration/
└── V1.019__insert_dr_emon_017.sql  ← NEW: inserts DR-EMON-017 into validation_rule

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── ElectronicMonitoringPreprocessor.java         ← NEW: @Component preprocessor
└── ElectronicMonitoringContext.java              ← NEW: Java record (CEL context)

specs/018-electronic-monitoring-status/
└── CRA-38.http                                   ← NEW: local test file (HTTP scratch file)

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
├── ElectronicMonitoringPreprocessorTest.java     ← NEW: unit tests (TDD first)
└── ElectronicMonitoringContextTest.java          ← NEW: unit tests for context record

src/test/java/uk/gov/hmcts/cp/integration/
└── ElectronicMonitoringStatusIT.java             ← NEW: integration test (extends IntegrationTestBase)

src/apiTest/java/uk/gov/hmcts/cp/http/
└── ElectronicMonitoringStatusApiHttpLiveTest.java ← NEW: live API test

README.md                                         ← UPDATED: new rule documented
CHANGELOG.md                                      ← UPDATED: CRA-38 entry
```

**Structure Decision**: Single-project layout. Follows the established pattern where each rule contributes
exactly one YAML file, one preprocessor `@Component`, one context record, and corresponding test classes.

---

## Phase 0: Research

*All NEEDS CLARIFICATION items resolved during research. No items open.*

### Research Finding 1 — EM flag field on `OffenceDto`

**Decision**: Use `OffenceDto.hasActiveElectronicMonitoring` (Boolean, nullable — treat null as false).  
**Rationale**: The OpenAPI spec (`openapi-spec.yml` line 329) defines this field as "Active electronic
monitoring indicator". Existing test scaffolding (`OpenApiObjectsTest.java` line 146) confirms
the field name.  
**Alternatives considered**: `emFlag`, `emStatus` — these do not exist in the generated DTO.

### Research Finding 2 — Continuing result detection (ELMONDET/EMREQ/EMREQV/EMSO)

**Decision**: ELMONDET, EMREQ, EMREQV, and EMSO are `promptRef` values on a parent result line.
A continuing result is detected when any result line on the EM-flagged offence carries a prompt
with `promptRef` exactly matching one of those codes (case-sensitive, matching the convention in
`PreprocessorHelper.hasPromptRef`) AND `promptValue` equals `"Yes"` (case-insensitive).  
**Rationale**: The Prompt model (`promptRef` + `promptValue`) is used in the codebase for
capturing structured Yes/No and date values alongside a result line (e.g., CTLDATE, protectedPersonsName,
endDate). The spec describes these as "child results with 'Yes' selected" — the Yes/No selection is the
prompt value. The promptRef IS the child result code.  
**Alternatives considered**: Treating ELMONDET/EMREQ/etc. as top-level result line shortCodes — rejected
because the spec says the parent result "includes" them, implying they are prompts on the parent line
rather than peer result lines.

### Research Finding 3 — Ending result (EMONE)

**Decision**: EMONE is a top-level result line shortCode on the offence. Detect by checking if any
result line for the offence has `shortCode.equalsIgnoreCase("EMONE")`.  
**Rationale**: The scenario test `CelValidationRuleScenarioTest.java` line 372 shows `resultLine("rl3", "EMONE", "d1", "off3")` as a standard result line. PreprocessorHelper's `anyShortCodeIn` method handles this pattern.

### Research Finding 4 — Exempt main result detection

**Decision**: An EM-flagged offence has an exempt main result if it has zero category-F result lines AND every category-I result line has a shortCode in `{NHMC, NHCC, A, ADJNN, ADJAPP}` (at least one must exist).  
**Rationale**: `ResultLineDto.CategoryEnum` values: `A` = Ancillary (adjournment, listing), `I` = Intermediary/interim, `F` = Final. NHMC/NHCC/ADJNN/ADJAPP/A are adjournment-type listing results that keep the case live. When one of these is the only non-ancillary result, the offence is exempt. If a non-exempt interim result (RC, REMUB) is also present, the NHMC etc. are "children" of that main result (AC3).  
**Alternatives considered**: Explicit parent-child ID linking — no such field exists in `ResultLineDto`.

### Research Finding 5 — Ancillary-only detection

**Decision**: An offence is ancillary-only if all its result lines have category A (or if there are no result lines). Check: none of the result lines have category F or I.  
**Rationale**: CategoryEnum.A = "Ancillary (e.g. adjournment, listing)". ODIR/ORDA/SMP are typical category A short codes.

### Research Finding 6 — Defendant-name suffix (`${defendantNames}`)

**Decision**: Use `${defendantNames}` placeholder in the page-level `errorMessageTemplate`. The existing
`MessageTemplateResolver.resolveDefendantNames()` handles single-defendant suppression and "and"/"," formatting automatically.  
**Rationale**: `MessageTemplateResolver` already implements AC4 logic (single defendant → omit, two defendants → "and", three+ → comma-separated). No new code required in the resolver.

### Research Finding 7 — Next DB migration number

**Decision**: `V1.019__insert_dr_emon_017.sql`.  
**Rationale**: User confirmed "next sql script would be V1.019__***" and "ignore missing sql script numbers, as they are in different branches". The local tree reaches V1.014 but V1.015–V1.018 are on other branches.

### Research Finding 8 — PreprocessorHelper utility methods available

The following `PreprocessorHelper` static methods are relevant and reusable:
- `groupResultsByOffence(request)` — `Map<String, List<ResultLineDto>>` (offenceId → lines)
- `groupLinesByDedupedDefendant(request)` — groups by deduplicated defendant keys
- `buildDefendantNames(request)` — `Map<String, String>` (defendantId → displayName)
- `anyShortCodeIn(lines, codeSet)` — true if any line has a shortCode in the set
- `hasPromptRef(line, promptRef)` — true if line has a prompt with matching promptRef

---

## Phase 1: Design & Contracts

### Data Model

See [data-model.md](data-model.md).

**New entities (this feature only):**

| Entity | Type | Location | Description |
|--------|------|----------|-------------|
| `ElectronicMonitoringContext` | Java record | `...cel/ElectronicMonitoringContext.java` | Per-defendant CEL context: unresolved EM offence count + offence ID sets |
| `ElectronicMonitoringPreprocessor` | `@Component` | `...cel/ElectronicMonitoringPreprocessor.java` | Preprocessor with type `"electronic-monitoring-status"` |
| `DR-EMON-017.yaml` | YAML rule file | `src/main/resources/rules/` | Rule definition — the YAML contract |
| `V1.019__insert_dr_emon_017.sql` | Liquibase migration | `src/main/resources/db/migration/` | Inserts rule row into `validation_rule` |

**`ElectronicMonitoringContext` record fields:**

```java
public record ElectronicMonitoringContext(
    String defendantId,
    String defendantName,
    long unresolvedEmCount,          // count of unresolved EM-flagged offences for this defendant
    List<String> unresolvedOffenceIds // IDs of unresolved offences → affectedOffenceSet in YAML
) implements RuleEvaluationContext
```

**`toCelContext()` returns:**

```java
Map.of("unresolvedEmCount", unresolvedEmCount)
```

**`getOffenceIdSet("unresolvedOffenceIds")` returns** `unresolvedOffenceIds`.

### YAML Rule Contract

```yaml
rule:
  id: "DR-EMON-017"
  title: "Electronic monitoring status not updated on EM-flagged offence"
  description: >-
    Errors when a final or non-exempt interim result is recorded against an offence that carries
    the active electronic monitoring flag, but neither a continuing EM result nor an EMONE ending
    result is present. Prevents sharing until the EM contractor is informed whether monitoring
    continues or ends.
  priority: 18000
  enabled: true

  preprocessing:
    type: "electronic-monitoring-status"
    continuingEmPromptRefs:
      - ELMONDET
      - EMREQ
      - EMREQV
      - EMSO
    endingEmShortCodes:
      - EMONE
    exemptMainResultShortCodes:
      - NHMC
      - NHCC
      - A
      - ADJNN
      - ADJAPP

  conditions:
    - id: "AC2"
      name: "EM-flagged offence not resolved"
      expression: "unresolvedEmCount > 0"
      severity: ERROR
      messageTemplate: >-
        Electronic monitoring is in place on this offence. Add a result that continues electronic
        monitoring, or end it with 'Electronic Monitoring End - notify contractor (EMONE)'.
      errorMessageTemplate: >-
        Electronic monitoring is in place on offences. Add a result that continues electronic
        monitoring, or end it with 'Electronic Monitoring End - notify contractor (EMONE)'.
        This affects ${defendantNames}.
      affectedOffenceSet: "unresolvedOffenceIds"
      validationLevel: OFFENCE
```

### Preprocessor Algorithm

```
ElectronicMonitoringPreprocessor.preprocess(request, config):

  emFlaggedOffenceIds ← offences with hasActiveElectronicMonitoring == true
  if emFlaggedOffenceIds is empty → return empty map (AC9: no EM offences → no processing)

  resultsByOffence ← groupResultsByOffence(request)
  defendantNames   ← buildDefendantNames(request)

  for each defendant in request:
    emOffencesForDefendant ← emFlaggedOffenceIds ∩ offences owned by this defendant
    unresolvedIds ← []

    for each offenceId in emOffencesForDefendant:
      lines ← resultsByOffence.getOrDefault(offenceId, [])

      resolved ← isResolved(lines, config)
      if !resolved → unresolvedIds.add(offenceId)

    if unresolvedIds is not empty:
      emit context(defendantId, defendantName, unresolvedIds.size(), unresolvedIds)

  return contexts keyed by defendantId

isResolved(lines, config):
  if lines is empty → true                                       (AC1: blank → resolved)
  if anyShortCodeIn(lines, endingEmShortCodes) → true            (AC1: EMONE → resolved)
  if hasContinuingEmResult(lines, continuingEmPromptRefs) → true (AC1: continuing → resolved)
  if isExemptMainResult(lines, exemptMainResultShortCodes) → true (AC1: exempt → resolved)
  if isAncillaryOnly(lines) → true                               (AC1: ancillary-only → resolved)
  return false                                                   (AC2: unresolved)

hasContinuingEmResult(lines, promptRefs):
  return any line has a prompt where promptRef ∈ promptRefs AND promptValue = "Yes" (case-insensitive)

isExemptMainResult(lines, exemptCodes):
  return lines has ≥1 result
     AND no line has category F
     AND every line with category I has shortCode ∈ exemptCodes
     AND ≥1 line has category I with shortCode ∈ exemptCodes    (must have at least one exempt main)

isAncillaryOnly(lines):
  return no line has category F or I
```

### Database Contract

The `validation_rule` table row:

```sql
INSERT INTO validation_rule (id, enabled, severity)
VALUES ('DR-EMON-017', true, 'ERROR');
```

### Validation API Contract Delta

No new endpoints. The existing `POST /validate` endpoint returns a `DraftValidationResponse` unchanged.
When DR-EMON-017 fires, the response will include a new `ValidationIssue` with:
- `ruleId = "DR-EMON-017"`
- `severity = "ERROR"`
- `conditionId = "AC2"`
- `offenceIds` = list of unresolved EM-flagged offence IDs

See [contracts/validation-api-delta.md](contracts/validation-api-delta.md) for the JSON example.

---

## Post-Design Constitution Re-check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. YAML/CEL Rule-First | ✅ PASS | DR-EMON-017.yaml created before Java code |
| II. Constructor Injection & Immutable DTOs | ✅ PASS | `ElectronicMonitoringContext` is a record; constructor injection for preprocessor |
| III. Layered Architecture | ✅ PASS | No bypassing of PreprocessorRegistry; `preprocessing.type = "electronic-monitoring-status"` |
| VI. Severity Ceiling | ✅ PASS | YAML severity = ERROR; DB row can only lower to WARNING, never raise |
| VIII. TDD | ✅ PASS | Test files listed in task order before production classes |
