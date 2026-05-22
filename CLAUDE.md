# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
gradle build                  # Full build (compile + checkstyle + PMD + unit/integration tests)
gradle test                   # Unit and integration tests only
gradle api                    # API tests (auto-manages docker-compose lifecycle)
gradle checkstyleMain         # Checkstyle (Google style, zero warnings allowed)
gradle pmdMain                # PMD static analysis
gradle jacocoTestReport       # Code coverage report (HTML + XML)

# Single test
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CelValidationRuleTest"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CelValidationRuleTest.methodName"

# Gatling performance tests
gradle gatlingRun-uk.gov.hmcts.cp.simulation.CapacitySimulation
gradle gatlingRun-uk.gov.hmcts.cp.simulation.StressSimulation
gradle gatlingRun -Dgatling.baseUrl=http://target-host:port  # Custom target
```

## Architecture

### Validation Rules Engine (YAML + CEL)

The core of this service is a rule engine that evaluates hearing data using CEL (Common Expression Language) expressions defined in YAML files.

**Rule execution flow:**
1. `ValidationRuleAutoConfiguration` discovers `rules/DR-*.yaml` files from classpath at startup
2. Each YAML file becomes a `CelValidationRule` bean (sorted by priority)
3. `DefaultValidationService.validate()` iterates all rules, calling `rule.evaluate(request)`
4. `CelValidationRule.evaluate()` runs the preprocessing pipeline, then evaluates CEL conditions:
   - `CustodialPreprocessor.preprocess()` transforms hearing result lines into `DefendantContext` records (counts like `noInfoCount`, `hasBothCount`, etc.)
   - `DefendantContext.toCelContext()` converts to `Map<String, Long>` for CEL evaluation
   - `CelExpressionEvaluator` compiles and caches CEL expressions
   - `MessageTemplateResolver` expands `${placeholder}` tokens in message templates
5. `RuleOverrideService` checks the `validation_rule` DB table for runtime overrides (enable/disable, severity ceiling), cached via Caffeine with configurable TTL

**Adding a new rule:** Create a `DR-<category>-<number>.yaml` in `src/main/resources/rules/` -- no Java code needed. The auto-configuration discovers it.

### Severity Ceiling Model

Database severity overrides act as a ceiling, not a replacement. `SeverityCeiling.resolve()` caps condition severities downward (ERROR -> WARNING) but never upgrades them.

### Feature Toggle

Validation is gated by a `RESULTS_VALIDATION` feature flag in Azure App Configuration, checked via `AzureFeatureToggleService`. When disabled, the service returns a success response with `mode="disabled"`.

### Request Filters

- `ActionHeaderFilter` (HIGHEST_PRECEDENCE): Maps request paths to `CPP-ACTION` header values for downstream Drools authorization
- `TracingFilter`: Propagates tracing/identity headers to MDC and echoes trace headers on responses

### Component Scanning Caveat

The base package `uk.gov.hmcts.cp` means Spring auto-scans beans from HMCTS library JARs under that package. Libraries with `@Component` classes need exclude filters in `Application.java` (see the `@ComponentScan` excludeFilters).

## Test Structure

| Type | Location | Runner | Notes |
|------|----------|--------|-------|
| Unit | `src/test/java` | `gradle test` | Mockito + AssertJ, `@ExtendWith(MockitoExtension.class)` |
| Integration | `src/test/java/.../integration/` | `gradle test` | Extend `IntegrationTestBase` (TestContainers PostgreSQL + WireMock) |
| API | `src/apiTest/java` | `gradle api` | Live HTTP against docker-compose stack (RestTemplate) |
| Gatling | `src/gatling/java` | `gradle gatlingRun-*` | CapacitySimulation has assertions; StressSimulation is report-only |

**Integration test infrastructure:** `IntegrationTestBase` provides `MockMvc`, a static `WireMockServer` with identity stubs, and `TestContainersInitialise` boots a reusable PostgreSQL 15.3 container.

## Key Conventions

- **Java 25** required
- **Checkstyle:** Google checks (`config/checkstyle/google_checks.xml`), `maxWarnings = 0` -- always run after code changes
- **PMD:** Custom ruleset (`.github/pmd-ruleset.xml`), `ignoreFailures = false`
- **No wildcard imports** -- use explicit imports for each class
- Test naming: `{action}_{scenario}_should_{expectation}` for methods; `@Nested` classes with `@DisplayName` for grouped scenarios
- Docker health checks use `bash /dev/tcp` (JRE image lacks curl)
- Default server port: `4550` (override via `SERVER_PORT` env var)
- **Spec-driven workflow**: features flow through `/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`. The constitution at `.specify/memory/constitution.md` is authoritative for principles (YAML/CEL Rule-First, TDD, SLF4J-only / no `System.out`, severity-ceiling never-promote, etc.). Branches use Jira-id prefix (e.g. `DD-41656-...`).
- **Local speckit patches**: `SPECKIT-LOCAL-PATCH:` markers identify files diverged from upstream — `.specify/scripts/bash/{common,setup-plan,check-prerequisites}.sh`, `.specify/extensions/git/scripts/bash/{create-new-feature,auto-commit}.sh`, and `.claude/skills/speckit-git-feature/SKILL.md`. Re-apply after `specify extension update`.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/002-ctl-missing-warning/plan.md](specs/002-ctl-missing-warning/plan.md)
<!-- SPECKIT END -->
