# Service Identity

- **Service:** service-cp-crime-hearing-results-validator
- **Description:** Validates draft hearing results before they are shared. Applies configurable YAML+CEL validation rules to detect issues such as missing concurrent/consecutive sentencing information, missing extended-test disqualification, etc., and returns structured error and warning responses.
- **Programme:** Crime Common Platform (CPP)
- **Team:** Disposals / Results
- **Organisation:** HMCTS / Ministry of Justice

## Technology Stack

| Component       | Value                                                         |
|-----------------|---------------------------------------------------------------|
| Framework       | Spring Boot 4                                                 |
| Language        | Java 25+                                                      |
| Build tool      | Gradle (NEVER Maven)                                          |
| Root package    | `uk.gov.hmcts.cp`                                             |
| Local port      | 4550 (override via `SERVER_PORT`)                             |
| K8s port        | 4550                                                          |
| Database        | PostgreSQL 15.3 (TestContainers / non-prod)                   |
| Rule engine     | YAML + CEL (`org.projectnessie.cel`)                          |
| Cache           | Caffeine (configurable TTL on `RuleOverrideService`)          |
| Feature flags   | Azure App Configuration (`RESULTS_VALIDATION` toggle)         |
| Static analysis | Checkstyle (Google) + PMD                                     |
| CI/CD           | GitHub Actions → ADO Pipeline 460                             |

## Constraints

- NEVER use Maven or Spring Initializr
- NEVER scaffold from scratch — work from the existing codebase
- All code in package `uk.gov.hmcts.cp.*` (the base scan package picks up `@Component` beans from any HMCTS library JAR under `uk.gov.hmcts.cp` — add `excludeFilters` in `Application.java` for libraries that publish unwanted beans)
- DTOs come from external dependency `libs.api.hearing.results.validator` (source repo: `~/moj/api-cp-crime-hearing-results-validator`); changes to those records belong upstream
- Validation rules live in `src/main/resources/rules/DR-*.yaml` — adding a new rule MUST NOT require Java changes if an existing preprocessor type fits (Constitution Principle I)
- Severity overrides in the `validation_rule` DB table act as a *ceiling* only; never as a floor (Constitution Principle VI)
- Logging via SLF4J only — no `System.out` / `System.err` (Constitution Principle VII)
- Test-Driven Development is mandatory (Constitution Principle VIII)

## Build & Test Commands

```bash
gradle build                  # Full build (compile + Checkstyle + PMD + tests)
gradle test                   # Unit and integration tests only
gradle api                    # API tests (auto-manages docker-compose lifecycle)
gradle checkstyleMain         # Checkstyle (Google style, maxWarnings = 0)
gradle pmdMain                # PMD static analysis
gradle jacocoTestReport       # Code coverage report (HTML + XML)

# Single test
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CelValidationRuleTest"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CelValidationRuleTest.methodName"

# Gatling performance
gradle gatlingRun-uk.gov.hmcts.cp.simulation.CapacitySimulation
gradle gatlingRun-uk.gov.hmcts.cp.simulation.StressSimulation
```
