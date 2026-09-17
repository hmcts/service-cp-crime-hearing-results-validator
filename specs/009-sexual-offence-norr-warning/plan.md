# Implementation Plan: Sexual Offence Notification Requirement Warning

**Branch**: `009-sexual-offence-norr-warning` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/009-sexual-offence-norr-warning/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Add a new validation rule, `DR-SEX-008`, that raises an offence-level `WARNING` when a convicted
relevant sexual offence (`misCode` "SEX" in offence reference data) has no sexual-offences
notification-requirement result recorded — `NORRR` for an Adult defendant (18+ at hearing date),
or neither `NORRR` nor `NORPGP` for a Youth defendant (under 18). This is the first rule in this
service to depend on data that isn't already present on the request: `misCode` is not on
`OffenceDto` and must be fetched per offence from the external `cpp-context-referencedata-offences`
service via a new HTTP client (`GET .../referencedataoffences-query-api/query/api/rest/
referencedataoffences/offences/{offenceId}`, keyed directly by `OffenceDto.getOffenceId()` per
explicit product direction — no code-based resolution step is needed). The rule follows the
existing YAML+CEL, preprocessor-registry pattern (Principle I / III): a new
`ValidationPreprocessor` (`SexualOffenceNotificationPreprocessor`, qualifier
`sexual-offence-notification-requirement`) resolves each offence's `misCode`, convicted status,
defendant age, and notification-result presence into a per-offence context; two CEL conditions in
one YAML file branch on a single `isYouth` boolean, keeping CEL a trivial comparison per the
"no branching in CEL" convention. AC2 (combined offence + defendant level warning display) is a
verification-only story — the existing `DraftValidationResponse` already returns a full list of
triggered issues across rules (proven in `DR-SENT-001`'s mixed OFFENCE+DEFENDANT-level rule); no
new aggregation code is needed, only a regression test that this rule's warnings coexist with
others.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4 (`spring-boot-starter-web` for `RestTemplate` — no
Feign/WebClient/resilience4j exist in this codebase and none are being introduced), `org.
projectnessie.cel` (existing CEL evaluator), Caffeine (existing cache infra, new named cache),
Lombok, `libs.api.hearing.results.validator` (external DTO jar — no contract change needed;
`OffenceDto.offenceId` and `.offenceCode`, `DefendantDto.dateOfBirth`, and `ResultLineDto.
shortCode`/`.category` already cover every field this feature reads)
**Storage**: PostgreSQL 15.3 (existing `validation_rule` table only — the new rule is registered
there for the runtime severity-ceiling mechanism; no new tables or migrations). New: an in-memory
Caffeine cache (`referencedataOffences`, keyed by `offenceId`) for `misCode` lookups, mirroring the
existing `ruleOverrides`/`featureFlags` caches in `CacheConfig`.
**Testing**: JUnit 5 + Mockito + AssertJ (unit, `gradle test`), MockMvc + TestContainers +
WireMock (integration, extends `IntegrationTestBase` — needs a **second** static `WireMockServer`
for the new referencedata-offences stub, alongside the existing identity stub), live API tests
(`gradle api`)
**Target Platform**: Linux container on Kubernetes (HMCTS CPP estate), local port 4550
**Project Type**: Single Spring Boot web service (existing project; no new services/repos)
**Performance Goals**: The new per-offence external lookup must not make `/validate` p95 latency
user-perceptibly worse. Mitigate with: (a) Caffeine caching so repeat lookups of the same offence
across hearings/requests are free after the first, (b) a tight client timeout (proposed: 2s
connect / 3s read — tighter than the existing `IdentityClient`'s 20s/21s, since this call sits in
the hot path of every offence in every validation request, not a once-per-request auth check),
and (c) fail-open so a slow/erroring reference-data call degrades to "no warning" rather than
blocking the whole validation response.
**Constraints**: Severity ceiling never promotes (Principle VI) — this rule is authored at its
maximum severity, `WARNING`, so there is no promotion risk regardless; SLF4J-only logging
(Principle VII) for the new client's fail-open path; no wildcard imports; TDD red-green-refactor
(Principle VIII); CEL expressions limited to simple comparisons, all age/convicted/misCode/
notification-code branching lives in the preprocessor (existing "Out-of-Scope" convention).
**Fail-open contract (new)**: if the reference-data lookup errors, times out, or returns no
`misCode`, the offence is treated as **not** a relevant sexual offence for this rule (no warning
produced for it) — consistent with this repo's existing fail-open precedent (`IdentityClient`,
`AzureAppConfigFetcher`) and with the "never block sharing on a warning-only rule" principle
already in the spec (FR-011). This is a deliberate availability-over-completeness trade-off:
flagged in Complexity Tracking below since it is new to this repo's preprocessors (existing
preprocessors fail safe on **missing request data**, not on an **external call failing**).
**Scale/Scope**: One new rule YAML (`DR-SEX-008`), one new preprocessor + context record, one new
external HTTP client + config properties + Caffeine cache registration + WireMock stub, unit +
integration + live-API tests. No changes to `ValidationRuleAutoConfiguration`, `PreprocessorRegistry`,
or `DefaultValidationService` — confirmed data-driven/rule-agnostic by direct code inspection.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. YAML/CEL Rule-First | **PASS** | Rule conditions, severity, and message templates live entirely in `DR-SEX-008.yaml`. A new preprocessor is required (no existing `preprocessing.type` covers a live external lookup) — explicitly permitted by `design_rules.md` §"Adding a New Rule" when no existing preprocessor fits. |
| II. Constructor Injection & Immutable DTOs | **PASS** | New context record (`SexualOffenceNotificationContext`) is a Java record. New preprocessor takes the new `ReferencedataOffenceClient` via constructor injection (`private final`, no field `@Autowired`). New HTTP response DTO is a record. |
| III. Layered Architecture & Data-Driven Preprocessor Dispatch | **PASS** | Dispatches via the existing `PreprocessorRegistry` by `preprocessing.type`; zero changes to `CelValidationRule` or the registry. This is the 8th preprocessor — confirms the registry remains genuinely data-driven. |
| IV. Spec-Driven Build Loop | **PASS (procedural)** | Implementation MUST go through code-reviewer → qa → spec-validator before merge, per `workflow.md`. |
| V. HMCTS Standards Compliance | **PASS** | Gradle, Java 25, `uk.gov.hmcts.cp` package (new client lives under the already-scaffolded, currently-empty `uk.gov.hmcts.cp.services.referencedata` package), SLF4J — no deviation. |
| VI. Severity Ceiling, Never Promote | **PASS** | Both conditions authored at `WARNING`, their maximum intended severity; DB ceiling can only stay at `WARNING` or (in principle) be configured lower — never promoted to `ERROR`. |
| VII. No `System.out`/`System.err` | **PASS** | New client logs via SLF4J on its fail-open path, matching `IdentityClient`/`AzureAppConfigFetcher`. |
| VIII. Test-Driven Development | **PASS (procedural)** | Tasks phase MUST order failing tests before production code for the new client, preprocessor, and rule. |

**One flagged deviation from established precedent (not a violation, but new territory — see
Complexity Tracking)**: every existing preprocessor is a pure function over the request body; this
is the first preprocessor that performs I/O (an external HTTP call) during `preprocess()`. This is
judged necessary (mis_code classification does not exist anywhere in this service's own data) and
consistent with Principle III's "preprocessor transforms the request into a context" contract —
the call is read-only, request-scoped, and does not mutate the request — but is called out
explicitly since it changes the preprocessor's operational profile (latency, failure modes) versus
its seven siblings.

## Project Structure

### Documentation (this feature)

```text
specs/009-sexual-offence-norr-warning/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md         # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   ├── DR-SEX-008.yaml                       # draft rule YAML (the CEL/business contract)
│   └── referencedata-offences-integration.md # the new outbound HTTP contract
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# Option 1: Single project (this repo's existing shape — no alternative considered, this is a
# Spring Boot monolith and stays one)

src/main/java/uk/gov/hmcts/cp/
├── services/rules/cel/
│   ├── SexualOffenceNotificationPreprocessor.java   # NEW — ValidationPreprocessor impl
│   ├── SexualOffenceNotificationContext.java        # NEW — RuleEvaluationContext record
│   └── PreprocessingDefinition.java                 # MODIFIED — add qualifyingMisCode,
│                                                     #   adultNotificationShortCodes,
│                                                     #   youthNotificationShortCodes fields
├── services/referencedata/                          # EXISTING EMPTY SCAFFOLD, now populated
│   ├── ReferencedataOffenceClient.java               # NEW — RestTemplate-based client, fail-open
│   ├── ReferencedataOffenceProperties.java            # NEW — @ConfigurationProperties for
│   │                                                  #   referencedata.offences.http.*
│   └── ReferencedataOffenceResponse.java              # NEW — record, only fields this repo reads
│                                                      #   (offenceId, misCode)
└── config/
    └── CacheConfig.java                              # MODIFIED — register "referencedataOffences"
                                                        #   named cache + TTL @Value

src/main/resources/
├── rules/DR-SEX-008.yaml                              # NEW
└── application.yaml                                   # MODIFIED — add referencedata.offences.http.*
                                                        #   block, mirroring authz.http

wiremock/mappings/
└── referencedataoffences-stub.json                    # NEW

src/test/java/uk/gov/hmcts/cp/
├── services/rules/cel/
│   ├── SexualOffenceNotificationPreprocessorTest.java  # NEW
│   └── SexualOffenceNotificationContextTest.java       # NEW
├── services/referencedata/
│   └── ReferencedataOffenceClientTest.java             # NEW
└── integration/
    ├── IntegrationTestBase.java                        # MODIFIED — add second WireMockServer +
    │                                                    #   stub helper for referencedata-offences
    └── SexualOffenceNotificationRuleIT.java             # NEW

src/apiTest/java/uk/gov/hmcts/cp/http/
└── SexualOffenceNotificationApiHttpLiveTest.java        # NEW
```

**Structure Decision**: Single Spring Boot service (existing structure, Option 1) — this feature
adds files inside the established `services/rules/cel/` (rule engine) and `services/referencedata/`
(the pre-existing but empty scaffold package, now given real content) packages, plus one new
config/cache registration and one new YAML rule. No new modules, no new services.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| New I/O-performing preprocessor (external HTTP call inside `preprocess()`), a first for this codebase | `misCode` classification (the "relevant sexual offence" test) does not exist anywhere in this service's own request data or configuration — it is only available from the CPP-wide offence reference-data catalog, and the product owner has directed that the live catalog (not a hardcoded snapshot) be the source of truth | A YAML-configured hardcoded list of sexual-offence codes (the pattern used by `DR-DISQ-002`'s `relevantOffenceCodes`) was the original fallback assumption in `spec.md`, but was explicitly superseded by the product owner in favour of the live reference-data lookup — rejected because it would drift out of sync with the reference-data catalog (new sexual offences added there would silently not trigger this warning) and duplicates data this service does not own |
| New external dependency (`cpp-context-referencedata-offences`) with its own client, config block, and cache — nothing like it exists in this repo today | Same as above — no simpler path to `misCode` exists | Waiting for an upstream contract addition (adding `misCode` directly to `OffenceDto`, the pattern used for `DefendantDto.dateOfBirth` in `specs/007-imprisonment-age-restriction`) was considered, but `misCode` is offence-reference data, not hearing/case data — it does not belong on the hearing-results-validator's own request contract, so this is a genuinely different, correct integration shape, not just an easier stopgap |
