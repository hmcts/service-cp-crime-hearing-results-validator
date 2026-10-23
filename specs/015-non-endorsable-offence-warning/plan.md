# Implementation Plan: Non-Endorsable Offence Warning

**Branch**: `CRA-337-non-endorsable-offence-warning` | **Date**: 2026-10-20 | **Updated**: 2026-10-26 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/015-non-endorsable-offence-warning/spec.md`

## Summary

Warn when an endorsement or endorsable-offence disqualification result is recorded against an offence whose reference data marks it as non-endorsable (`endorsable_flag` = 0 or absent). Two independent offence-level WARNINGs — one per trigger family — are produced by a **single YAML rule (DR-END-014) with two conditions** backed by a **single unified preprocessor** (`NonEndorsableOffencePreprocessor`). Both trigger families share a single reference-data call per offence. The existing `ReferencedataOffenceClient.getEndorsableFlag()` cache layer (introduced in CRA-336) is reused without modification.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Caffeine (cache), Mockito + AssertJ + JUnit 5 (tests), WireMock (integration stubs), TestContainers PostgreSQL 15.3
**Storage**: PostgreSQL 15.3 (`validation_rule` table, one new row for DR-END-014)
**Testing**: `gradle test` (unit + integration), `gradle api` (live HTTP)
**Target Platform**: Azure-hosted Linux container, port 4550
**Project Type**: Web service (Spring Boot REST)
**Performance Goals**: No measurable latency increase — `getEndorsableFlag()` call is Caffeine-cached per offence code; cache hit is O(1). Ref-data call skipped entirely when no trigger result is present.
**Constraints**: Checkstyle Google `maxWarnings = 0`, PMD `ignoreFailures = false`, no `System.out`
**Scale/Scope**: One new YAML rule with two conditions, one new preprocessor `@Component` bean, one new context record, one SQL migration, one unit test class, one IT class, one API test class

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. YAML/CEL Rule-First | ✅ PASS | DR-END-014.yaml defined in data-model.md before any Java; two conditions in one file; no rule logic in Java |
| II. Constructor Injection & Immutable DTOs | ✅ PASS | `NonEndorsableOffenceContext` is a record; `NonEndorsableOffencePreprocessor` uses explicit constructor injection |
| III. Layered Architecture & Preprocessor Dispatch | ✅ PASS | Preprocessor implements `ValidationPreprocessor`, registered by qualifier `"non-endorsable-offence"`; dispatched via `PreprocessorRegistry` |
| IV. Spec-Driven Build Loop | ✅ PASS | spec.md → plan.md → tasks.md → implement loop applied |
| V. HMCTS Standards Compliance | ✅ PASS | Java 25, Spring Boot 4, Gradle, SLF4J, package `uk.gov.hmcts.cp` |
| VI. Severity Ceiling, Never Promote | ✅ PASS | Both YAML conditions declare `severity: WARNING`; SQL inserts `'WARNING'`; never promoted |
| VII. No `System.out` / `System.err` | ✅ PASS | SLF4J only in all new code |
| VIII. TDD | ✅ PASS | Tasks ordered: failing tests first, production code second |

No violations. No complexity-tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/015-non-endorsable-offence-warning/
├── spec.md              ✅ done
├── plan.md              ✅ this file
├── research.md          ✅ done
├── data-model.md        ✅ done
├── quickstart.md        ✅ done
├── CRA-337.http         ✅ done
├── checklists/
│   └── requirements.md  ✅ done
└── tasks.md             ✅ done
```

### Source Code (new files for this feature)

```text
src/main/resources/rules/
└── DR-END-014.yaml                                         # single rule: two conditions (endorsement AC1 + disqualification AC2)

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── NonEndorsableOffenceContext.java                        # record: offenceId + endorsementCount + disqualificationCount
└── NonEndorsableOffencePreprocessor.java                   # @Component qualifier="non-endorsable-offence"

src/main/resources/db/migration/
└── V1.015__insert_dr_end_014.sql                           # INSERT DR-END-014 row

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
└── NonEndorsableOffencePreprocessorTest.java               # unit tests (TDD: written first)

src/test/java/uk/gov/hmcts/cp/integration/
└── NonEndorsableOffenceRuleIT.java                         # integration tests covering all ACs (TDD: written first)

src/apiTest/java/uk/gov/hmcts/cp/http/
└── NonEndorsableOffenceApiHttpLiveTest.java                # live API tests

specs/015-non-endorsable-offence-warning/
└── CRA-337.http                                            # IntelliJ HTTP client smoke tests
```

### Existing files modified

```text
src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java
    → endorsementShortCodes and disqualificationTriggerShortCodes fields added

src/apiTest/java/uk/gov/hmcts/cp/http/ValidationRulesApiHttpLiveTest.java
    → DR-END-014 added to rule-list assertions (count = 10)

src/apiTest/java/uk/gov/hmcts/cp/http/NonEndorsableOffenceApiHttpLiveTest.java
    ✅ Fixed (T016): RULE_ID_015 removed; all assertions use RULE_ID_014

wiremock/mappings/referencedataoffences-non-endorsable-stub.json
    ✅ RT88026 added as endorsableFlag:true (prevents DR-DISQ-002 live test collision)

src/apiTest/java/uk/gov/hmcts/cp/http/ValidationRulesApiHttpLiveTest.java
    ✅ DR-END-014 in rule-list assertion (count = 12 rules total)

specs/015-non-endorsable-offence-warning/CRA-337.http
    ⚠️ OUTSTANDING: stale DR-END-015 comments; RT88026 coded as non-endorsable but is now
       mapped endorsable in WireMock stub; AC4/AC5/AC6 sections missing
```

**No other existing files change.**

## Implementation Phases

### Phase A — YAML Rule (Principle I gate) ✅ DONE

Write the YAML file first; implementation cannot begin until this is committed.

**A1** ✅: Create `src/main/resources/rules/DR-END-014.yaml`
- `preprocessing.type: "non-endorsable-offence"`
- `endorsementShortCodes: [LEP, LEN, LEA]`
- `disqualificationTriggerShortCodes: [DDO, DDOL, DDOR, DDOTE, DDOTEL, DDD, DDDL, DDDT, DDDTL, DDDTO, DDP, DDPL, DDPR, DDPTE, DDPTEL, DDRCOT]`
- Condition AC1 `expression: "endorsementCount > 0"`, severity: WARNING, message: "This offence is not endorsable. Remove the endorsement result.", `affectedOffenceSet: "nonEndorsableEndorsementOffenceIds"`, `validationLevel: OFFENCE`
- Condition AC2 `expression: "disqualificationCount > 0"`, severity: WARNING, message: "This offence is not endorsable. Remove the disqualification result, or record a disqualification for non-endorsable offence result instead.", `affectedOffenceSet: "nonEndorsableDisqualificationOffenceIds"`, `validationLevel: OFFENCE`
- Full YAML body in `data-model.md`

### Phase B — SQL Migration ✅ DONE

**B1** ✅: `V1.015__insert_dr_end_014.sql` — `INSERT INTO validation_rule (id, enabled, severity) VALUES ('DR-END-014', true, 'WARNING');`

### Phase C — Failing Tests (TDD gate — Principle VIII) ✅ DONE

Write tests before production Java. All tests must compile and fail at the assertion.

**C1** ✅: `NonEndorsableOffencePreprocessorTest` (unit) — covers:
- Endorsement family: LEP/LEN/LEA triggers on non-endorsable (flag=false), absent-flag fail-open, no-trigger skips ref-data
- Disqualification family: DDO, DDRCOT trigger; DDRN does not; DDO on endorsable does not
- Both families on same offence: both counts populated, single ref-data call
- Multi-offence independence
- Context record contracts: `toCelContext()`, `getOffenceIdSet()`, `defendantName()`, `allOffenceIds()`

**C2** ✅: `NonEndorsableOffenceRuleIT` (integration) — extends `IntegrationTestBase`, covers all 6 ACs:
- AC1a (LEP/LEN/LEA on non-endorsable → WARNING DR-END-014 endorsement condition)
- AC1b (DDO/DDRCOT on non-endorsable → WARNING DR-END-014 disqualification condition)
- AC1c (LEP + DDO on same non-endorsable → two WARNINGs from DR-END-014)
- AC2 (trigger on endorsable → no warning)
- AC3 (DDRN/DDRVL on non-endorsable → no warning)
- AC4 (no trigger result → no warning, stateless)
- AC5 (multi-offence independence for both families)
- AC6 (coexistence with DR-SENT-001)

### Phase D — Production Code ✅ DONE

**D1** ✅: `NonEndorsableOffenceContext` record
- Fields: `String offenceId`, `long endorsementCount`, `long disqualificationCount`
- `toCelContext()` → `Map.of("endorsementCount", endorsementCount, "disqualificationCount", disqualificationCount)`
- `getOffenceIdSet("nonEndorsableEndorsementOffenceIds")` → `List.of(offenceId)` when endorsementCount > 0
- `getOffenceIdSet("nonEndorsableDisqualificationOffenceIds")` → `List.of(offenceId)` when disqualificationCount > 0
- `defendantName()` → null; `allOffenceIds()` → `List.of(offenceId)`

**D2** ✅: `NonEndorsableOffencePreprocessor`
- `@Component`, qualifier `"non-endorsable-offence"` via `type()` return value
- Injects `ReferencedataOffenceClient` via constructor
- Per offence: check both `endorsementShortCodes` and `disqualificationTriggerShortCodes` presence; skip ref-data if neither trigger is present; call `getEndorsableFlag()` once per offence when any trigger is present; if flag absent or false → set applicable counts to 1; if flag true → no context produced

### Phase E — API Test & HTTP Client File ✅ DONE

**E1** ✅: `NonEndorsableOffenceApiHttpLiveTest` — live HTTP tests
- ✅ AC1a: endorsement trigger → WARNING DR-END-014 endorsement condition
- ✅ AC1b: disqualification trigger → WARNING DR-END-014 disqualification condition (fixed T016)
- ✅ AC2: endorsable offence → no warning
- ✅ AC1c (both families): DR-END-014 appears exactly twice (fixed T016)

**E2** ✅: `ValidationRulesApiHttpLiveTest` — DR-END-014 in rule-list assertion (count = 12)

**E3** ⚠️: `specs/015-non-endorsable-offence-warning/CRA-337.http` — needs fixes (see Phase H)

### Phase F — API Live Test Fix ✅ DONE

**F1** ✅: Fixed `NonEndorsableOffenceApiHttpLiveTest.java` (T016)
- Removed `RULE_ID_015 = "DR-END-015"` constant
- Fixed `DisqualificationTrigger` → assert `RULE_ID_014`
- Fixed `BothFamilies` → `filter(RULE_ID_014::equals).count() == 2`

**F2** ✅: Fixed WireMock collision — added RT88026 as endorsableFlag:true so existing
  `DisqualificationExtendedTestApiHttpLiveTest` does not falsely trigger DR-END-014

### Phase G — Build & Quality Loop ✅ DONE

```bash
gradle checkstyleMain      # zero warnings ✅
gradle pmdMain             # zero violations ✅
gradle test                # all unit + integration green ✅
gradle api                 # 83/83 tests green ✅
gradle jacocoTestReport    # NonEndorsableOffencePreprocessor 100% instruction/line/method ✅
                           # NonEndorsableOffenceContext 100% all metrics ✅
```

### Phase H — AC Coverage Gaps ✅ DONE

Re-audit against Jira CRA-337 v2 PDF revealed three gaps at the API live test level and
one gap in the HTTP scratch file. All gaps resolved 2026-10-26.

**H1** ✅: Added `NonEndorsableDiqFamilyClear` nested class to `NonEndorsableOffenceApiHttpLiveTest`
- `ddrnOnNonEndorsableOffence_shouldNotRaiseAnyWarning()` — DDRN on `live-ne-disq-trigger` →
  no DR-END-014 (DDRN not in trigger set, no ref-data call)

**H2** ✅: Added `MultiOffenceIndependence` nested class to `NonEndorsableOffenceApiHttpLiveTest`
- `lepOnNonEndorsableAndEndorsableOffence_shouldWarnOnlyForNonEndorsable()` — two offences
  → exactly one DR-END-014 warning

**H3** ✅: Fixed `specs/015-non-endorsable-offence-warning/CRA-337.http`
- Removed DR-END-015 stale comments; header now references DR-END-014 only
- Replaced RT88026 with `live-ne-*` WireMock-compatible codes throughout
- Added AC4 (IMP on non-endorsable → no warning), AC5 (multi-offence → one warning),
  AC6 (coexistence note referencing IT test)

**Checkpoint**: `gradle api` 85/85 green (2 new tests added). All ACs covered at unit, IT, and API live test levels. CRA-337.http usable for manual smoke testing against local docker stack.

## Key Design Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Preprocessor strategy | One unified preprocessor | Both families need identical endorsable_flag lookup per offence; one ref-data call per offence (not two); single responsibility for the endorsability check |
| YAML files | One file (DR-END-014), two conditions | Both conditions share the same preprocessor context; single rule means single DB row; conditions are still independently represented with distinct IDs (AC1, AC2) and distinct affectedOffenceSets |
| Context record | `NonEndorsableOffenceContext` with `endorsementCount` + `disqualificationCount` | Avoids duplicate ref-data call; both counts produced in one preprocessor pass |
| CEL expressions | `endorsementCount > 0` and `disqualificationCount > 0` | Simple integer comparisons; no branching needed in CEL |
| Fail-open | Absent `endorsable_flag` → warn | Conservative; cannot silently miss a legally relevant warning |
| Reference-data call | Reuse `getEndorsableFlag()` | Already on main; Caffeine-cached; no new network path |
| Ref-data skip | Skip when no trigger result on offence | Avoids any ref-data call for offences with only non-trigger results (e.g. IMP) |
| DDRN-family exclusion | Not in trigger set | DDRN codes are not listed under `disqualificationTriggerShortCodes`; AC3 passes naturally without explicit exclusion logic |
| Priority | DR-END-014 → 14000 | Continues from existing DR-END-012 spacing |
| SQL migration | V1.015 only | One rule, one row |

## Out of Scope

- Changes to `EndorsableOffencePreprocessor` or DR-END-012 — not touched
- Changes to request/response DTO contracts — DTOs are from upstream `api-cp-crime-hearing-results-validator`
- Per-rule override / severity-ceiling integration tests — covered once by `ValidationRuleOverrideIntegrationTest` (per `design_rules.md`)
- UI rendering of GDS warning text
- DR-END-015 — the disqualification warning is condition AC2 inside DR-END-014; no separate rule file is needed