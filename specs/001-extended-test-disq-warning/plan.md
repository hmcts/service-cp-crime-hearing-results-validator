# Implementation Plan: Extended Test Disqualification Warning (DD-41656)

**Branch**: `DD-41656-results-validation-warning` | **Date**: 2026-04-25 | **Revised**: 2026-04-28 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-extended-test-disq-warning/spec.md`

## Revision — 2026-04-28

This plan was originally drafted assuming "no upstream contract change is required" (research.md R3, original). That assumption is **superseded** — see [research.md R3-revised](./research.md#r3-revised-refinement-gate-on-categoryf-from-the-result-line-2026-04-28) and the spec's 2026-04-28 Revision section. The rule's "is this a final result?" gate now reads `category = 'F'` directly off the result line, and the change ships as a coordinated four-repo delivery under DD-41656 (no separate Jira).

**What changed in this plan**:

- **Summary** — gate condition rewritten; the four-gate logic (relevance / non-excluded final / no DDOTE / no DDOTEL) now reads from `category` rather than inferring final-result status from short-code-set membership. Adjournments (`category = 'A'`) on relevant offences correctly produce no warning (BA scenarios doc, scenario 5).
- **Technical Context** — `External DTOs` line updated: the upstream contract is being extended to add `category: enum [A, I, F]` to `ResultLineDto`. The upstream lib version is bumped; this service pulls the new version once the lib is published.
- **New section: Cross-Repo Coordination** — the four-repo delivery, branching strategy per repo, dependency ordering between repos, and the API-publish-via-`main` constraint are documented below.
- **Project Structure** — no new files in *this* repo beyond the original plan; one new field on `DisqualificationContext` (`long finalCategoryCount`) and a `category = 'F'` filter step in the preprocessor algorithm.
- **Constitution Check** — Principle I's "DTO changes belong upstream" clause is now actively in play; PASS is preserved because the upstream change is in scope and ships in the same Jira.
- **Complexity Tracking** — no new violations. The TDD waiver for US2/US3 is unchanged.

The companion artifacts in this directory have parallel revision blocks at the top:

- [research.md](./research.md) — R3 marked superseded, R3-revised added with the new gate, branch coordination, and OOS reaffirmed.
- [data-model.md](./data-model.md) — `DisqualificationContext` algorithm switches to `category = 'F'`; CEL variable `finalCategoryCount` (count of F-category lines on the offence) is added as a diagnostic alongside the existing `excludedFinalCount` (semantics tightened to F-category lines only); no PreprocessingDefinition schema change.
- [contracts/rule-DR-DISQ-001.md](./contracts/rule-DR-DISQ-001.md) — upstream `ResultLineDto` extended with `category`; YAML rule definition unchanged; preprocessor contract updated.
- [quickstart.md](./quickstart.md) — `curl` examples updated to include `category` on each result line; new adjournment-`'A'` smoke scenario added.

`tasks.md` is now stale relative to this plan and must be regenerated via `/speckit-tasks` after `/speckit-plan` completes.

## Summary

Add a new YAML+CEL validation rule that raises a non-blocking `WARNING` when a hearing contains an offence with one of five Road Traffic Act 1988 Home Office codes (`RT88046`, `RT88526`, `RT88026`, `RT88530`, `RT88531`) that has at least one **`category = 'F'`** result line whose short code is not in the excluded list (`wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`) and where no result line on that offence has `shortCode` in `{DDOTE, DDOTEL}`. The warning text is fixed and the rule is per-offence (one warning per qualifying offence). The `category` attribute (enum `A` Ancillary / `I` Intermediary / `F` Final) is added to `ResultLineDto` upstream as part of this delivery — see [Cross-Repo Coordination](#cross-repo-coordination-2026-04-28) below.

The rule cannot reuse `CustodialPreprocessor` — its outputs (`noInfoCount`, `hasBothCount`, etc.) are concurrent/consecutive-specific and group by defendant, whereas this rule is per-offence and needs different counts and offence-id sets. A new `ValidationPreprocessor` is therefore required, which under **Constitution Principle III** means the hard-wired `CustodialPreprocessor` field in `CelValidationRule` and `ValidationRuleAutoConfiguration` MUST first be replaced with a registry-based dispatch driven by the YAML `preprocessing.type` field. The plan therefore has two ordered parts:

1. **Refactor**: introduce a `ValidationPreprocessor` interface, a sealed `RuleEvaluationContext` type (`toCelContext()` + `getOffenceIdSet(String)`), and a Spring-aware `PreprocessorRegistry` that resolves preprocessors by qualifier. Wire `CelValidationRule` and `ValidationRuleAutoConfiguration` through the registry. Move `CustodialPreprocessor` and `DefendantContext` behind the interface, qualifier `custodial-concurrent-consecutive`. No behaviour change; existing tests stay green.
2. **Add the new rule**: implement `DisqualificationExtendedTestPreprocessor` (qualifier `disqualification-extended-test`) producing a `DisqualificationContext` with counts and a `qualifyingOffenceIds` set, then add `src/main/resources/rules/DR-DISQ-001.yaml` with one CEL condition firing on `qualifyingCount > 0`. No change to the upstream API contract.

The trigger point in the UI (Save and continue / Manage hearing tab) is wiring outside this service.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Caffeine cache, Logback + LogstashEncoder, SnakeYAML, Lombok, Spring Data JPA + PostgreSQL driver
**External DTOs**: `libs.api.hearing.results.validator` (root package `uk.gov.hmcts.cp.openapi.model`) — `DraftValidationRequest`, `OffenceDto`, `ResultLineDto`, `DefendantDto`, `ValidationIssue`, `AffectedOffence`. Spec source: `/home/sachin/moj/api-cp-crime-hearing-results-validator/src/main/resources/openapi/openapi-spec.yml`. **Upstream contract change is required** (2026-04-28 revision — supersedes the original "no upstream change" line): `ResultLineDto` is extended to add `category: enum [A, I, F]`. The lib version is bumped and published from the API repo's `main` branch (ACR publish pipeline runs only on `main`). This service pulls the new lib version once published; until then this service builds against the previous lib and the new field is unavailable.
**Storage**: PostgreSQL 15.3 (TestContainers in tests, real instance in non-prod). Only the existing `validation_rule` table is touched — a new row keyed by the rule's id (e.g. `DR-DISQ-001`) controls runtime enable/severity.
**Testing**: JUnit 5 + Mockito + AssertJ for unit tests; MockMvc for controller tests; WireMock for external-service stubs; TestContainers for `*IT` integration tests (extend `IntegrationTestBase`); `gradle api` for live API tests via docker-compose; Gatling for performance.
**Target Platform**: Linux server, AKS (Azure Kubernetes Service), behind the CPP STE gateway. Default port 4550.
**Project Type**: Single-module Spring Boot web service.
**Performance Goals**: Validation latency must remain indistinguishable to a user from the existing single-rule pipeline (rule evaluation is in-memory, CEL expressions are cached per (expression, context-keys) tuple).
**Constraints**:

- Spring Boot 4 / Java 25 baseline (Constitution V).
- Constructor injection only; `@Autowired` fields forbidden (Constitution II).
- All in-process value types are Java records (Constitution II).
- SLF4J only — no `System.out` / `System.err` / `printStackTrace` (Constitution VII).
- Severity ceiling caps downward; never promotes (Constitution VI).
- `gradle build` must pass with Checkstyle Google `maxWarnings=0`, PMD `ignoreFailures=false`, all unit + integration tests green (Constitution V + Workflow).
- TDD is mandatory: failing test first, then production code (Constitution VIII).
- No wildcard imports.
- Base scan package is `uk.gov.hmcts.cp` — any new HMCTS library imports must be checked for stray `@Component` beans and excluded if necessary.

**Scale/Scope**: Single new rule + one prerequisite refactor. The refactor touches `CelValidationRule`, `ValidationRuleAutoConfiguration`, `CustodialPreprocessor`, `DefendantContext` (about 5 files). The new rule adds approx. 4 files: 1 YAML, 1 preprocessor, 1 context record, 1 entry in the sealed type. Test scope: ≥6 new unit tests for the preprocessor, ≥1 integration `*IT` test for the rule end-to-end through the controller, plus regression tests for the registry-based dispatch.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Compliance | Notes |
|-----------|------------|-------|
| **I. YAML/CEL Rule-First** | PASS | The new rule lives in `src/main/resources/rules/DR-DISQ-001.yaml`. CEL condition is a single boolean expression. Severity, message text, and the relevant offence codes / excluded codes / disqualification codes are all in the YAML — BAs can amend without touching Java. The new preprocessor publishes a documented set of CEL variables that the YAML references by name. **Phase 7 (2026-04-28) modifies the preprocessor and context record to consume the new `category` field — covered by Principle I's exception clause ("if [adding a new rule without writing Java is] not [possible], the preprocessor or context model has a gap that MUST be fixed in the same change"); the YAML rule itself remains the contract a BA reads.** |
| **II. Constructor Injection & Immutable DTOs** | PASS | `DisqualificationExtendedTestPreprocessor` uses constructor injection (no fields needed beyond Spring's stereotype). `DisqualificationContext` is a Java record with `toCelContext()` and `getOffenceIdSet()`. `RuleEvaluationContext` is a sealed interface (Constitution II's "sealed interfaces for polymorphic types"). |
| **III. Layered Architecture & Data-Driven Preprocessor Dispatch** | PASS *(after the prerequisite refactor)* | The current `CelValidationRule` hard-wires `CustodialPreprocessor` — Principle III explicitly calls this out as a transitional state that "MUST be removed before any second preprocessor type ships". This plan removes that wiring **first**, then adds the new preprocessor. After the refactor, dispatch is by YAML `preprocessing.type` via a Spring registry, which is exactly what Principle III mandates. |
| **IV. Spec-Driven Build Loop** | PASS | This plan is the `/speckit-plan` output following `/speckit-specify`. `/speckit-tasks` will produce `tasks.md` next; `/speckit-implement` runs the build loop with `code-reviewer`, `qa`, `spec-validator` agents. |
| **V. HMCTS Standards Compliance** | PASS | Gradle (no Maven). Spring Boot 4 / Java 25. Root package `uk.gov.hmcts.cp`. SLF4J + Logback. No new external dependencies. The `validation_rule` table row is added by the existing migration mechanism (Liquibase) — not a new migration, just data. |
| **VI. Severity Ceiling, Never Promote** | PASS | The single condition has `severity: WARNING` in the YAML — already at the lower end of the scale. The DB ceiling never promotes WARNING to ERROR; the existing `SeverityCeiling.resolve()` is reused unchanged. |
| **VII. No `System.out` / `System.err` — SLF4J Only** | PASS | New code uses `@Slf4j` (Lombok). Tests use SLF4J too. No print statements. |
| **VIII. Test-Driven Development** | PASS for US1, WAIVED for US2/US3 — see Complexity Tracking | US1's failing tests precede the US1 production code (T021). US2 and US3 ship test coverage *over* the gates already present in T021, so their "failing tests" pass on first run. This is an explicit, narrow waiver — see the Complexity Tracking row below for the engineering rationale. |

**Gate result**: PASS. The Principle III refactor is **part of this plan**, not a deviation requiring a Complexity Tracking entry — Principle III itself states the refactor must happen at the moment a second preprocessor is introduced. Treating it as a complexity violation would be wrong; treating it as a prerequisite phase is right.

## Project Structure

### Documentation (this feature)

```text
specs/001-extended-test-disq-warning/
├── plan.md              # This file
├── spec.md              # Feature spec (already exists)
├── research.md          # Phase 0 output (this command)
├── data-model.md        # Phase 1 output (this command)
├── quickstart.md        # Phase 1 output (this command)
├── contracts/
│   └── rule-DR-DISQ-001.md   # Rule contract (YAML schema + CEL variables)
├── checklists/
│   └── requirements.md  # From /speckit-specify (already exists)
└── tasks.md             # From /speckit-tasks (NOT created by this command)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/uk/gov/hmcts/cp/
│   │   ├── config/
│   │   │   └── ValidationRuleAutoConfiguration.java          # MODIFIED: inject PreprocessorRegistry instead of CustodialPreprocessor
│   │   └── services/rules/
│   │       └── cel/
│   │           ├── ValidationPreprocessor.java               # NEW: interface (preprocess returns Map<String, RuleEvaluationContext>)
│   │           ├── PreprocessorRegistry.java                 # NEW: resolves preprocessor by qualifier from Spring context
│   │           ├── RuleEvaluationContext.java                # NEW: sealed interface (toCelContext + getOffenceIdSet)
│   │           ├── CelValidationRule.java                    # MODIFIED: dispatch via registry, accept polymorphic context
│   │           ├── CustodialPreprocessor.java                # MODIFIED: implements ValidationPreprocessor, qualifier "custodial-concurrent-consecutive"
│   │           ├── DefendantContext.java                     # MODIFIED: implements RuleEvaluationContext (existing record permitted via "permits" clause)
│   │           ├── DisqualificationExtendedTestPreprocessor.java  # NEW: qualifier "disqualification-extended-test"
│   │           └── DisqualificationContext.java              # NEW: record implementing RuleEvaluationContext
│   └── resources/rules/
│       ├── DR-SENT-002.yaml                                  # UNCHANGED
│       └── DR-DISQ-001.yaml                                  # NEW: the rule
└── test/
    └── java/uk/gov/hmcts/cp/services/rules/cel/
        ├── PreprocessorRegistryTest.java                     # NEW
        ├── DisqualificationExtendedTestPreprocessorTest.java # NEW (>=6 scenarios)
        ├── CelValidationRuleTest.java                        # MODIFIED: registry-based fixtures
        └── ../integration/
            └── DisqualificationExtendedTestRuleIT.java       # NEW: end-to-end through MockMvc
```

**Structure Decision**: This is a single-module Spring Boot service. All Java production code lives under `src/main/java/uk/gov/hmcts/cp/`. YAML rule files live under `src/main/resources/rules/`. Tests live under `src/test/java/uk/gov/hmcts/cp/` mirroring the production package layout, with integration tests in a `.integration.` sub-package. No new top-level modules or directories are introduced.

## Cross-Repo Coordination (2026-04-28)

The `category = 'F'` refinement requires a coordinated change across **four repositories**, all under DD-41656 (no separate Jira). The dependency ordering is one-way: the published lib version from `api-cp-crime-hearing-results-validator` must land first; the other three repos consume it.

### Repos, branches, and per-repo scope

| Order | Repo | Branch (off) | Why this branch | Scope of change |
|-------|------|--------------|-----------------|-----------------|
| 1 | `api-cp-crime-hearing-results-validator` | `main` (no feature branch) | The ACR-publish pipeline runs **only on `main`**. To make a new lib version available to consumers, the OpenAPI edit + version bump must land directly on `main` (or via a short-lived PR that merges to `main`). | Add `category: enum [A, I, F]` to `ResultLineDto` in `src/main/resources/openapi/openapi-spec.yml`. Bump `version` in `build.gradle` / `pom.xml`. Trigger the publish-to-ACR job. |
| 2 | `cpp-context-hearing` | `DD-41656-results-validation-warning` (off `team/DD-41715-results-validator`) | The DD-41715 branch already wires `ShareResultsCommandHandler` to call the validator HTTP endpoint with the four new pre-share ops (only one of which is the validator call — see project memory `project_hearing_validator_integration.md`). DD-41656 builds on that wiring; rebasing onto a fresh branch off integration would lose the HTTP wiring and force re-implementation. | Add `private String category` + `withCategory(...)` builder to the locally hand-written `ResultLineDto` at `hearing-command/.../service/validation/ResultLineDto.java` (parallel mirror — **not** regenerated from the OpenAPI contract; must be maintained alongside it). Populate `.category(line.getCategory())` in `ValidationRequestMapper.toValidationRequest`. Add unit-test coverage on the mapper. |
| 3 | `cpp-ui-hearing` | `DD-41656-results-validation-warning` (off `team/result-validation`) | The team integration branch for the validator-call work in the UI; branching off it preserves the in-flight pre-share validation wiring. | Extend `buildResultLines` (`src/app/results/core/helpers/results-validation.ts`) to map `line.category` from the resolved draft line onto the validation request body. Add unit-test coverage on `buildResultLines`. |
| 4 | `service-cp-crime-hearing-results-validator` (this repo) | `DD-41656-results-validation-warning` (already open, off main) | Continues from the original 001 implementation; staying here preserves the existing Phase A registry refactor commits and the in-flight DR-DISQ-001 implementation. | Pull the new lib version once published. Tighten `DisqualificationExtendedTestPreprocessor` to gate on `category = 'F'`. Add positive IT for BA scenario 5 (adjournment `'A'` → no warning). Adjust the existing edge-case unit/IT tests that asserted the inference behaviour. No YAML schema change. |

### Dependency ordering

```
[api repo: publish v_new lib]  →  [validator service: pull v_new + tighten preprocessor]
                                ↘
                                  [cpp-context-hearing: pull v_new + mirror DTO + mapper update]
                                ↘
                                  [cpp-ui-hearing: extend buildResultLines (no lib dep — TypeScript-side)]
```

The UI repo's task can run in parallel with the API publish, since the UI does not depend on the OpenAPI lib version. The validator service's task technically can also begin in parallel — the `category` field can be added to the preprocessor and tests against synthetic payloads without waiting for the lib bump — but the integration test that exercises the full deserialised request shape needs the new lib to be on the classpath.

### Branch coordination — open question (carried from spec.md Assumptions)

`cpp-context-hearing` carries an active `team/DD-41715-results-validator` branch that adds four new pre-share operations (only one of which is the validator HTTP call). The DD-41656 branching question — piggyback on DD-41715 vs. open a fresh branch off integration — is resolved by the user input on this `/speckit-plan` invocation: **branch off `team/DD-41715-results-validator`**. Rationale: DD-41715's wiring is the carrier for DD-41656's data plumbing; rebasing onto integration would orphan the HTTP-call work that DD-41656 needs.

If DD-41715 merges to `team/result-validation` (or to integration) before DD-41656 lands, DD-41656's branch will need a rebase. That is acceptable churn — strictly preferable to losing the wiring.

### CI/CD-shaped constraints

- **API repo lib publish only on `main`**: this is the reason the API change has no feature branch. If review needs a non-`main` workflow (e.g. PR with checks), a short-lived PR to `main` that auto-merges on green is acceptable, but the lib will not appear in ACR until something lands on `main`.
- **Validator service Lib version**: pulled via `libs.api.hearing.results.validator` in `build.gradle`; bumping the version is a one-line dependency change committed alongside the preprocessor tightening.
- **cpp-context-hearing parallel-mirror DTO**: the locally hand-written `ResultLineDto` is **not** regenerated from the OpenAPI contract. It is a deliberate, manually-maintained mirror, kept in lockstep with the upstream contract by hand. Future authors editing this DTO must remember the mirror exists; a comment at the top of the local DTO referencing the OpenAPI source-of-truth is recommended (and is part of the cpp-context-hearing scope).

### Out-of-scope

Carried forward from the spec's Out-of-Scope section:
- `isFinalResult: boolean` on the contract — `category` already encodes the signal.
- Runtime call from the validator to the result-definitions reference-data service — `category` already crosses the wire via the share command, no extra service-to-service hop is needed.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| **Principle VIII (TDD), narrow waiver for US2/US3** — the four-gate logic (relevance, final-result presence, excluded codes, DDOTE/DDOTEL absence) ships in a single preprocessor in T021 (US1). US2 (T024–T028) and US3 (T030–T033) add *test coverage* of the excluded-codes and DDOTE gates respectively; those tests pass on first run rather than failing red, because the gates already exist in production. | Shipping the gates incrementally would mean shipping a knowingly noisy MVP — US1 alone would warn on every relevant offence regardless of result code, until US2 lands suppression for excluded codes (wdrn, dism, etc.), then US3 lands DDOTE/DDOTEL suppression. That's a worse user experience than holding the MVP back until all four gates are live. | (b) Splitting T021 across user-story phases so each gate adds production code in its own phase would restore strict TDD ordering for US2/US3 but ship a knowingly-noisy MVP for hours/days between phases. The product cost outweighs the process purity. (a) Picked. |

The Principle III refactor of preprocessor dispatch is mandated by the principle itself, not a deviation, so it is not a complexity violation and is not listed here.
