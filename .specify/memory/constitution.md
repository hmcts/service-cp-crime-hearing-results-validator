<!--
SYNC IMPACT REPORT
==================
Version change: (uninitialised template) → 1.0.0
Bump rationale: Initial ratification. All principles and sections are new; no
                prior principles to remove or redefine, so MAJOR is the correct
                starting point (1.0.0 per the standard).

Modified principles: N/A (initial ratification).

Added sections:
  - Core Principles
      I.    YAML/CEL Rule-First
      II.   Constructor Injection & Immutable DTOs
      III.  Layered Architecture & Data-Driven Preprocessor Dispatch
      IV.   Spec-Driven Build Loop
      V.    HMCTS Standards Compliance
      VI.   Severity Ceiling, Never Promote
      VII.  No System.out / System.err — SLF4J Only
      VIII. Test-Driven Development
  - Technology Stack & Deployment
  - Development Workflow & Quality Gates
  - Governance

Removed sections: None.

Templates requiring updates:
  - .specify/templates/plan-template.md       ✅ compatible — the "Constitution
      Check" block is filled per-feature by `/speckit-plan`; no structural
      change required. Plan authors MUST gate on Principles I–VIII.
  - .specify/templates/spec-template.md       ✅ compatible — no
      constitution-specific content; spec authors MUST honour Principle I
      (YAML rule contract first) when introducing or changing a rule.
  - .specify/templates/tasks-template.md      ✅ compatible — task ordering
      already encodes "tests before implementation", aligning with Principle
      VIII; no structural change required.
  - .specify/templates/checklist-template.md  ✅ compatible — no changes.
  - README.md / CLAUDE.md / docs/*            ✅ aligned — `.claude/rules/*.md`
      already encode these principles informally; this constitution is now the
      authoritative source.

Follow-up TODOs: None. All placeholders resolved.
-->

# service-cp-crime-hearing-results-validator Constitution

## Core Principles

### I. YAML/CEL Rule-First (NON-NEGOTIABLE)

Validation rules are YAML+CEL files in `src/main/resources/rules/DR-*.yaml`.
The YAML is the contract. Rule IDs, condition expressions, severities, message
templates, and preprocessing configuration MUST live in YAML so non-technical
stakeholders (Business Analysts, policy reviewers) can read and amend them
without reading Java. Adding a new rule MUST be possible without writing Java
code — if it is not, the preprocessor or context model has a gap that MUST be
fixed in the same change.

This service does not own an OpenAPI spec; request/response DTOs are imported
from the external `api-cp-crime-hearing-results-validator` dependency
(`libs.api.hearing.results.validator`). Changes to those DTOs belong in that
upstream repository, not here.

**Rationale**: keeps policy-shaped change (which conditions matter, what
severity, what message text) reviewable by the people accountable for it,
and stops business logic leaking into Java where it becomes opaque to BAs.

### II. Constructor Injection & Immutable DTOs (NON-NEGOTIABLE)

Dependency injection MUST use constructor parameters with `private final`
fields (Lombok `@RequiredArgsConstructor` or an explicit constructor).
Field-level `@Autowired` is forbidden. All in-process value types — the CEL
context records, preprocessing definitions, validation issues, response
bodies — MUST be Java records.

**Rationale**: constructor injection makes dependencies explicit and
test-friendly without reflection. Records give immutability, correct
`equals`/`hashCode`/`toString`, and map cleanly onto the YAML schema and the
external DTOs.

### III. Layered Architecture & Data-Driven Preprocessor Dispatch (NON-NEGOTIABLE)

The service follows four layers:

```
Controller (ValidationController)
    → ValidationService (DefaultValidationService)
        → CelValidationRule (one per YAML file)
            → ValidationPreprocessor → CelExpressionEvaluator
```

- Controllers handle HTTP only; no business logic.
- `DefaultValidationService` orchestrates the rule list and applies feature
  toggles and DB severity overrides.
- Each `CelValidationRule` owns the YAML for one rule and runs its
  preprocessing pipeline followed by CEL condition evaluation.
- Preprocessor selection MUST be data-driven through the YAML
  `preprocessing.type` field, dispatched via a Spring-aware registry that
  resolves a `ValidationPreprocessor` bean by qualifier. Hard-wiring a
  single preprocessor implementation into `CelValidationRule` is a
  transitional state; it MUST be removed before any second preprocessor
  type ships.
- Cross-cutting concerns (`SecurityConfig`, filters under
  `uk.gov.hmcts.cp.filters`, tracing, action-header mapping) live in their
  dedicated config/filters packages and are not invoked from rule code.

**Rationale**: keeps controllers MockMvc-testable in isolation; lets new rule
families plug in by adding a new preprocessor `@Component(qualifier)` plus
a YAML file, without touching `CelValidationRule`.

### IV. Spec-Driven Build Loop (NON-NEGOTIABLE)

Every non-trivial change MUST flow through the cycle:

```
Spec → Write → Code Review → QA → Spec-Validate → Fix → Ship
```

The reviewer agents (`code-reviewer`, `qa`, `spec-validator`) report findings
only; they MUST NOT modify code. The primary agent or a human applies fixes,
then re-runs the loop until all three return PASS / COMPLIANT. The
`spec-validator` checks that YAML rule files conform to the `RuleDefinition`
schema, that `preprocessing.type` resolves to a registered preprocessor
bean, and that every CEL expression compiles. Changes exempt from the loop
are limited to: markdown-only edits, whitespace/import-only edits, and
`.claude/rules/*` or `CLAUDE.md` rule updates.

**Rationale**: keeps a human (or primary agent) as the decision point;
prevents conflicting auto-fixes; preserves auditable, reproducible review
output.

### V. HMCTS Standards Compliance (NON-NEGOTIABLE)

- Build tool: Gradle. Maven is forbidden.
- Stack baseline: Spring Boot 4 on Java 25.
- Root Java package: `uk.gov.hmcts.cp` (NOTE: the base scan package picks up
  `@Component` beans from any HMCTS library JAR under `uk.gov.hmcts.cp` —
  add `excludeFilters` in `Application.java` for libraries that publish
  `@Component` classes you do not want auto-scanned).
- Logging: SLF4J + Logback, with `LogstashEncoder` emitting JSON on the
  `json` Spring profile. Every request MUST carry an MDC `correlationId`
  via the existing `TracingFilter`.
- PII, tokens, passwords, and secrets MUST NOT appear in logs.

**Rationale**: aligns the service with HMCTS security, observability, and
platform conventions for Azure-hosted services, ensuring it is operable and
auditable inside the wider CPP estate.

### VI. Severity Ceiling, Never Promote (NON-NEGOTIABLE)

The `validation_rule` database table acts as a severity ceiling, never a
floor. `SeverityCeiling.resolve()` caps a condition's effective severity
downward (ERROR → WARNING) but MUST NEVER upgrade it. Rule authors MUST set
the *highest* severity a condition should ever produce in the YAML;
runtime overrides only ever lower it.

**Rationale**: ops or product can soften a noisy rule without redeploy
("stop blocking shares for DR-XXX") but cannot accidentally promote an
informational warning into a blocker that breaks downstream callers. This
asymmetry is deliberate and is part of the contract with consumers.

### VII. No `System.out` / `System.err` — SLF4J Only (NON-NEGOTIABLE)

Code MUST NOT use `System.out.println`, `System.err.println`, or
`Throwable#printStackTrace()`. All diagnostic output goes through SLF4J
(`org.slf4j.Logger` via `LoggerFactory.getLogger(...)` or Lombok `@Slf4j`).
This applies to production code AND tests.

**Rationale**: structured JSON logs via `LogstashEncoder` on the `json`
Spring profile depend on every log line going through the SLF4J pipeline.
Direct stdout/stderr writes bypass MDC (correlationId), severity routing,
and log shipping — they vanish from production observability and surface
as noise in test output.

### VIII. Test-Driven Development (NON-NEGOTIABLE)

Red → Green → Refactor for every behaviour change.

1. Write the failing test first. It MUST run and fail for the *correct*
   reason — the assertion, not a missing class or compilation error.
2. Write the minimum production code to make it pass.
3. Refactor with the test still green.

PRs MUST show that the test was authored at or before the production code
(commit history or paired-commit are both acceptable). The `qa` reviewer
agent gates on this — production code without an accompanying
failing-then-passing test is FAIL.

Exempt: pure mechanical refactors (rename, move, extract with no behaviour
change), formatting, and comment-only edits.

**Rationale**: this codebase's regression surface is a YAML/CEL rule engine
where a single wrong character in a CEL expression silently changes
severity routing and which offences are flagged. Only fail-first tests
catch that class of bug before production.

## Technology Stack & Deployment

- **Java**: 25 (verify via `java -version`).
- **Framework**: Spring Boot 4.
- **Build tool**: Gradle (wrapper committed; `gradle build` runs Checkstyle
  Google + PMD + tests with `maxWarnings = 0`).
- **Ports**: local `4550`; Kubernetes `4550`. Override via `SERVER_PORT`.
- **CEL engine**: `org.projectnessie.cel` (cached compiled expressions per
  expression+context-key tuple in `CelExpressionEvaluator`).
- **Database**: PostgreSQL 15.3 via TestContainers for integration tests;
  same in non-prod environments. The `validation_rule` table holds runtime
  rule overrides (enabled flag + severity ceiling).
- **Cache**: Caffeine for `RuleOverrideService` (configurable TTL).
- **Feature toggles**: Azure App Configuration, accessed via
  `AzureFeatureToggleService`. The `RESULTS_VALIDATION` flag short-circuits
  validation when off (returns success with `mode="disabled"`).
- **Logging**: SLF4J + Logback with `LogstashEncoder` on the `json`
  profile; `correlationId` MDC propagation via `TracingFilter`.
- **Auth**: `ActionHeaderFilter` (HIGHEST_PRECEDENCE) maps request paths to
  `CPP-ACTION` header values for downstream Drools authorization. Identity
  flows through `cp-auth-rules-filter` and JWT/identity URL configuration.
- **CI/CD**: GitHub Actions → Azure DevOps Pipeline 460 → ACR.
- **Performance**: Gatling — `gradle gatlingRun-uk.gov.hmcts.cp.simulation.CapacitySimulation`
  has assertions; `StressSimulation` is report-only.

## Development Workflow & Quality Gates

- The YAML rule file (`src/main/resources/rules/DR-*.yaml`) MUST be updated
  **before** any Java change that affects rule behaviour (Principle I).
- The build loop (Principle IV) repeats until `code-reviewer`, `qa`, and
  `spec-validator` each return PASS / COMPLIANT.
- TDD (Principle VIII) MUST be visible in commit history: failing test
  commit precedes (or is paired with) the production code that satisfies it.
- Every feature built via spec-kit lives under `specs/NNN-slug/` containing
  at least `spec.md`, `plan.md`, and `tasks.md`. Flow:
  `/speckit-specify → /speckit-plan → /speckit-tasks → /speckit-implement
  → /speckit-analyze`.
- Required commands run cleanly before merge:
  - `gradle build` — Checkstyle Google (`maxWarnings = 0`), PMD, unit and
    integration tests.
  - `gradle api` — live API tests against the docker-compose stack
    (auto-managed lifecycle).
  - `gradle pmdMain pmdTest` — static analysis with `ignoreFailures = false`.
  - `gradle jacocoTestReport` — coverage report (HTML + XML).
- Commit style: Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`,
  `refactor:`, etc.).
- Pull requests: the description MUST state which principle(s) the change
  touches. Any deviation from a principle requires explicit written
  justification in the PR description and MUST be flagged in the plan's
  "Complexity Tracking" section.

## Governance

This constitution supersedes the informal conventions in `.claude/rules/`
copied from the HMCTS overlay template. Where this document and those files
disagree, this document wins; the rule files are retained as quick-reference
material and MUST be kept in sync.

**Amendment procedure**:

1. Propose the change in a feature spec under `specs/`.
2. Bump `Version` per semantic versioning:
   - **MAJOR** — a breaking principle change, removal, or redefinition that
     invalidates existing practice.
   - **MINOR** — a new principle, new section, or materially expanded
     guidance.
   - **PATCH** — clarifications, wording, typo fixes, or non-semantic
     refinements.
3. Re-run `/speckit-analyze` on every in-flight feature spec to verify it
   still aligns with the amended principles; update or waive as required.

**Compliance expectations**:

- All PRs MUST honour these principles.
- Deviations MUST be explicitly justified in the PR description and, where
  relevant, in the plan's "Complexity Tracking" table.
- Reviewers MUST block merges that silently violate a NON-NEGOTIABLE
  principle without a written waiver.

**Version**: 1.0.0 | **Ratified**: 2026-04-25 | **Last Amended**: 2026-04-25
