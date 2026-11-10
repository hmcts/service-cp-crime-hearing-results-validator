# Implementation Plan: CRA-362 – Pre-Release Bail Condition Warning (DR-BAIL-016)

**Branch**: `dev/CRA-362-pre-release-bail-condition-warning` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/017-pre-release-bail-condition-warning/spec.md`

## Summary

Implement validation rule `DR-BAIL-016` that emits a `WARNING` (advisory, non-blocking) when:
(a) an offence has a custodial current remand status (`C`, `L`, or `S` in `OffenceDto.bailStatus`);
(b) a conditional bail result code is entered (RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB, CCIC, or CCSC);
(c) at least one pre-release-type bail condition is present (PASS1–PASS8, PORSC, PORS, or PORO1
with a trigger keyword in its free-text prompt).

Five distinct warning messages are shown based on result code and defendant age:
- **Warning A**: adult (18+), conditional bail result → suggests RIB
- **Warning B**: youth (<18), conditional bail result → suggests RILAB
- **Warning C**: no DOB, conditional bail result → suggests RIB for adult or RILAB for youth
- **Warning D**: CCIC (any age) → suggests CCIIB
- **Warning E**: CCSC (any age) → suggests CCSIB

The rule requires:
1. A **new YAML rule** `DR-BAIL-016.yaml` with 5 conditions (AC1, AC2, AC6, AC2A, AC2B)
2. A **new `ValidationPreprocessor`** (`PreReleaseBailPreprocessor`, type `"pre-release-bail-condition"`) — per-offence, no external API call
3. A **new context record** (`PreReleaseBailContext`) with 6 CEL variables
4. **3 new fields** on `PreprocessingDefinition` (`conditionalBailResultCodes`, `crownCourtBailCodes`, `triggerBailConditionCodes`)
5. A **Liquibase migration** `V1.018__insert_dr_bail_016.sql`
6. **Unit + integration + API tests** (TDD — tests first)
7. **HTTP scratch file** `CRA-362.http` covering all AC scenarios
8. **README update** noting the new rule

**No changes to `OffenceDto` or `api-cp-crime-hearing-results-validator`**. All needed data already
exists on the current request DTOs.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), `java.text.Normalizer` (JDK — for Welsh accent-insensitive matching)
**Storage**: PostgreSQL 15.3 — existing `validation_rule` table; one new row added via Liquibase migration
**Testing**: JUnit 5 + Mockito + AssertJ (unit), `IntegrationTestBase` + MockMvc + TestContainers (integration), RestTemplate (API live)
**Target Platform**: Azure-hosted Spring Boot service; local port 4550
**Project Type**: Web service (validation endpoint)
**Performance Goals**: No external API call in this preprocessor — purely in-memory evaluation over `DraftValidationRequest` data. Performance profile is O(offences × result-lines); no Caffeine cache or WireMock needed.
**Constraints**: `gradle build` must pass with zero Checkstyle warnings and zero PMD violations (`maxWarnings = 0`); SLF4J only (no `System.out`)
**Scale/Scope**: ~200 LoC net new Java (preprocessor + context record). `PreprocessingDefinition` gains 3 fields (additive change). One new YAML file with 5 conditions.

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. YAML/CEL Rule-First** | PASS | `DR-BAIL-016.yaml` authored first; all policy (codes, severity, message text) lives in YAML; five conditions cover all five warning variants |
| **II. Constructor Injection & Immutable DTOs** | PASS | `PreReleaseBailContext` is a Java record; `PreReleaseBailPreprocessor` uses `@Component` with no injected dependencies (stateless), so no-arg is fine; no `@Autowired` |
| **III. Layered Architecture & Preprocessor Dispatch** | PASS | New preprocessor registered via `PreprocessorRegistry` by type qualifier `"pre-release-bail-condition"`; no hard-wiring |
| **IV. Spec-Driven Build Loop** | PASS | Full loop: spec → YAML → tests → code → code-reviewer → qa → spec-validator |
| **V. HMCTS Standards Compliance** | PASS | Gradle build, root package `uk.gov.hmcts.cp`, SLF4J logging, Java 25 |
| **VI. Severity Ceiling, Never Promote** | PASS | YAML severity `WARNING`; DB row inserts with `severity = 'WARNING'`; no promotion path possible |
| **VII. No System.out/System.err** | PASS | All diagnostic output via SLF4J (`@Slf4j` / `LoggerFactory`) |
| **VIII. TDD** | PASS | Failing tests for `PreReleaseBailContext` and `PreReleaseBailPreprocessor` authored before production code |

## Project Structure

### Documentation (this feature)

```text
specs/017-pre-release-bail-condition-warning/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── CRA-362.http         # HTTP scratch file (all AC scenarios)
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
src/main/resources/rules/
└── DR-BAIL-016.yaml                                       # NEW — rule definition (5 conditions)

src/main/resources/db/migration/
└── V1.018__insert_dr_bail_016.sql                        # NEW — Liquibase migration

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── PreReleaseBailContext.java                             # NEW — context record (per offence)
├── PreReleaseBailPreprocessor.java                        # NEW — preprocessor @Component
└── PreprocessingDefinition.java                           # EXTEND — +3 new list fields

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
├── PreReleaseBailContextTest.java                         # NEW — unit tests for context record
└── PreReleaseBailPreprocessorTest.java                    # NEW — unit tests for preprocessor

src/test/java/uk/gov/hmcts/cp/integration/
└── PreReleaseBailConditionRuleIT.java                     # NEW — integration test (all ACs)

src/apiTest/java/uk/gov/hmcts/cp/http/
└── PreReleaseBailConditionApiHttpLiveTest.java            # NEW — live API test

specs/017-pre-release-bail-condition-warning/
└── CRA-362.http                                           # NEW — HTTP scratch file

README.md                                                  # UPDATE — document DR-BAIL-016
```

**Structure Decision**: Single-project layout (existing). No new directories. No new Spring config. All artefacts slot into existing source trees.

## Phase 0: Research Findings

Full findings in [research.md](research.md). Key decisions:

| Decision | Outcome |
|----------|---------|
| Rule ID | `DR-BAIL-016` |
| YAML priority | `17000` |
| Preprocessor type qualifier | `"pre-release-bail-condition"` |
| Context record | `PreReleaseBailContext` (per offence, 6 CEL vars) |
| External API calls | **None** — all data in `DraftValidationRequest` |
| PORO1 keyword matching | NFD normalise → strip `\p{M}` → lowercase → stem substring search |
| Trigger stems | `passport`, `suret`, `securit`, `asbort`, `echniw`, `sicrwydd` |
| OffenceDto / upstream JAR | **No change** |
| Blocked? | **No** |

## Phase 1: Design

### YAML Rule (`DR-BAIL-016.yaml`)

Five conditions, each `validationLevel: OFFENCE`, `severity: WARNING`:

| Condition ID | CEL expression | Fires when |
|-------------|---------------|-----------|
| AC1 | `triggerCount > 0 && ccicCount == 0 && ccscCount == 0 && adultCount > 0` | Adult, conditional bail result |
| AC2 | `triggerCount > 0 && ccicCount == 0 && ccscCount == 0 && youthCount > 0` | Youth, conditional bail result |
| AC6 | `triggerCount > 0 && ccicCount == 0 && ccscCount == 0 && noDobCount > 0` | No DOB, conditional bail result |
| AC2A | `triggerCount > 0 && ccicCount > 0` | CCIC result (any age) |
| AC2B | `triggerCount > 0 && ccscCount > 0` | CCSC result (any age) |

Exactly one condition fires per offence context (see mutual-exclusion analysis in `data-model.md`).

Full YAML content, including all code lists and message templates: see [data-model.md](data-model.md).

### `PreReleaseBailContext` record

Six `long` CEL variables. Minimal record — no defendant name needed (warning is at offence level
and the message template references the offence, not the defendant). `toCelContext()` returns a
6-entry `Map<String, Long>`. `getOffenceIdSet("offenceId")` returns `List.of(offenceId)`. See
[data-model.md](data-model.md) for full source.

### `PreReleaseBailPreprocessor` logic

Per-offence, no external I/O:

```
1. Build upper-cased code sets from config (conditionalBailResultCodes, crownCourtBailCodes, triggerBailConditionCodes)
2. Index offences by id (offenceById), defendants by id (defendantById)
3. Group all result lines by offence id (groupResultsByOffence)
4. For each offence with result lines:
   a. Skip if offence.bailStatus ∉ {C, L, S}
   b. Skip if no result line shortCode ∈ (conditionalBailCodes ∪ crownCourtBailCodes)
   c. Skip if no trigger bail condition (named codes OR PORO1 with keyword)
   d. Resolve defendant age → adultCount / youthCount / noDobCount
   e. Classify result code → ccicCount / ccscCount
   f. Emit PreReleaseBailContext(offenceId, 1L, adultCount, youthCount, noDobCount, ccicCount, ccscCount)
```

PORO1 keyword matching: `java.text.Normalizer.normalize(text, NFD)` → strip `\p{M}` → `toLowerCase(ROOT)` → `contains(stem)` for each of 6 stems. Pure static utility method, no state.

### `PreprocessingDefinition` extension

Add three fields at the end of the record (Lombok `@Builder` handles optional fields; callers
that don't use these new fields pass `null`/empty naturally via `@Builder.Default` or by
omitting the field in the `@Builder` call):

```java
List<String> conditionalBailResultCodes,
List<String> crownCourtBailCodes,
List<String> triggerBailConditionCodes
```

The YAML deserialiser (Jackson, via `CelValidationRule`'s `ObjectMapper`) maps the new YAML keys
to these fields automatically — no additional wiring needed.

### Liquibase migration

```sql
-- V1.018__insert_dr_bail_016.sql
INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by)
VALUES ('DR-BAIL-016', true, 'WARNING', now(), 'system');
```

### Integration test strategy

`PreReleaseBailConditionRuleIT` extends `IntegrationTestBase` (TestContainers PostgreSQL, WireMock
identity stubs, MockMvc). No WireMock stubs for reference data needed — this rule is purely
in-memory. Each test scenario posts a JSON request and asserts the response body.

**What the IT covers** (per-AC):
- AC1: adult (DOB sets age ≥ 18), offence bailStatus=C, result=RC, PASS5 → Warning A, `isValid=true`
- AC2: youth (DOB sets age < 18), bailStatus=S, result=REMCB, PORS → Warning B, `isValid=true`
- AC2A: any age, bailStatus=L, result=CCIC, PASS1 → Warning D, `isValid=true`
- AC2B: any age, bailStatus=C, result=CCSC, PASS8 → Warning E, `isValid=true`
- AC3 (PORO1 English): result=RC, bailStatus=C, PORO1 prompt="needs a surety" → Warning A
- AC3 (PORO1 Welsh): PORO1 prompt="mechnïwr" → Warning A
- AC3 (PORO1 no keyword): PORO1 prompt="report to probation" → NO warning
- AC4: warning does not block sharing — `isValid=true` confirmed
- AC4A: same offence also triggers DR-SENT-001 or another rule → both warnings present independently
- AC5 (no custodial remand): bailStatus=B, result=RC, PASS5 → NO DR-BAIL-016 warning
- AC5 (non-conditional result): bailStatus=C, result=RIB, PASS5 → NO warning
- AC5 (no trigger condition): bailStatus=C, result=RC, no PASS/PORSC/PORS/PORO1 → NO warning
- AC6: no DOB on defendant, result=RCBV, PORSC → Warning C
- Edge: multiple offences for same defendant, only one qualifies → warning on qualifying offence only

**What it SHOULD NOT cover** (framework-level, proven in `ValidationRuleOverrideIntegrationTest`):
- DB severity ceiling capping WARNING
- `enabled=false` suppression
- Caffeine cache TTL

### Implementation Order (TDD — Constitution §VIII)

1. **Write `DR-BAIL-016.yaml`** — the YAML is the contract (Principle I)
2. **Extend `PreprocessingDefinition`** — add the 3 new fields
3. **Write failing `PreReleaseBailContextTest`** — `toCelContext()`, `getOffenceIdSet()`, `allOffenceIds()`, `defendantName()` — run `gradle test --tests "*PreReleaseBailContextTest"` → FAIL (class does not exist)
4. **Implement `PreReleaseBailContext`** — context test goes GREEN
5. **Write failing `PreReleaseBailPreprocessorTest`** — all scenarios (AC1–AC7, PORO1 variants, edge cases); run `gradle test --tests "*PreReleaseBailPreprocessorTest"` → FAIL (class does not exist)
6. **Implement `PreReleaseBailPreprocessor`** — preprocessor test goes GREEN
7. **Write failing `PreReleaseBailConditionRuleIT`** — full end-to-end via MockMvc; run `gradle test --tests "*PreReleaseBailConditionRuleIT"` → FAIL initially (rule not wired or DB row missing)
8. **Write `V1.018__insert_dr_bail_016.sql`** — IT goes GREEN
9. **Write `PreReleaseBailConditionApiHttpLiveTest`** — covers AC1, AC2, AC2A, AC2B, AC6 live
10. **Write `CRA-362.http`** — covers all AC scenarios for IDE-based manual testing
11. **Update README.md** — add DR-BAIL-016 to the rules table
12. **Run full build loop**: `gradle build` → code-reviewer agent → qa agent → spec-validator agent → fix any findings → repeat until PASS/COMPLIANT

### Dependency Tracking

| Item | Owner | Blocking? |
|------|-------|-----------|
| `OffenceDto` upstream DTO change | None — not needed | **NOT BLOCKING** |
| `api-cp-crime-hearing-results-validator` lib update | None — not needed | **NOT BLOCKING** |
| `validation_rule` DB row for DR-BAIL-016 | Liquibase migration (V1.018) | Required for IT and production |

## Complexity Tracking

No constitution violations. All principles satisfied without exception.

The only complexity worth noting: 5 conditions in a single YAML rule file is higher than typical
(most rules have 1–2 conditions). This is justified by the fact that all 5 conditions share the
same trigger predicate (`triggerCount > 0`) and the same preprocessor — separating them into
5 rules would require 5 preprocessor invocations, 5 DB rows, and 5 sets of tests for the same
underlying check. A single rule with 5 conditions is the simpler, more maintainable design.
