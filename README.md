# Crime Hearing Results Validator

A Spring Boot service that validates draft hearing results before they are shared. The service applies configurable validation rules to detect issues such as missing concurrent/consecutive sentencing information, and returns structured error and warning responses.

## Validation Rules Engine

Rules are defined in YAML files using [CEL (Common Expression Language)](https://cel.dev/) for condition expressions. This makes rules readable and reviewable by non-technical stakeholders such as Business Analysts, without requiring Java knowledge.

### Example Rule (YAML)

```yaml
rule:
  id: "DR-SENT-002"
  title: "Custodial sentence concurrent/consecutive check"
  conditions:
    - id: "AC2"
      name: "Multiple offences missing info"
      expression: "noInfoCount > 1"
      severity: ERROR
      messageTemplate: >-
        Offence/counts ${offenceNumbers} do not include details of whether
        they are concurrent or consecutive.
```

Rule files are located in `src/main/resources/rules/`.

### Current Rules

| Rule ID | Description |
|---------|-------------|
| DR-SENT-002 | Custodial sentence concurrent/consecutive check |

### Adding a New Rule

1. Create a YAML rule file named `DR-<category>-<number>.yaml` in `src/main/resources/rules/`
2. Define the preprocessing configuration, CEL conditions, and message templates
3. The rule is auto-discovered at startup by `ValidationRuleAutoConfiguration` (no Java code needed)

### Runtime Rule Overrides

Rules can be toggled or have their severity adjusted at runtime via the `validation_rule` database table, without redeployment.

| Column | Type | Description |
|--------|------|-------------|
| `id` | VARCHAR(20) | Rule ID, must match the YAML file (e.g. `DR-SENT-002`) |
| `enabled` | BOOLEAN | `false` disables the rule entirely |
| `severity` | VARCHAR(20) | Maximum severity ceiling (see below) |
| `updated_at` | TIMESTAMP | Last modification time |
| `updated_by` | VARCHAR(100) | User who made the change |

#### Severity Ceiling Model

A single rule can contain multiple conditions with different severities. For example, DR-SENT-002 has:

- **AC2** (missing concurrent/consecutive info) — `ERROR` (blocks sharing)
- **AC3** (both concurrent and consecutive) — `WARNING` (advisory)
- **AC4** (no primary sentence) — `WARNING` (advisory)

The database `severity` column acts as a **ceiling**, not a blanket replacement. It caps condition severities downward but never upgrades them:

| Condition | YAML severity | DB override = `WARNING` | DB override = `ERROR` | No override |
|-----------|--------------|------------------------|----------------------|-------------|
| AC2 | ERROR | WARNING (capped) | ERROR | ERROR |
| AC3 | WARNING | WARNING | WARNING | WARNING |
| AC4 | WARNING | WARNING | WARNING | WARNING |

This means setting a rule's severity to `WARNING` in the database effectively says "stop this rule from blocking shares" — all conditions become non-blocking while still surfacing as warnings. Setting it to `ERROR` (or having no override) leaves each condition at its YAML-defined severity.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/validation/validate` | Validate draft hearing results |
| GET | `/api/validation/rules` | List all validation rules |
| GET | `/api/validation/rules/{ruleId}` | Get rule detail by ID |

## Prerequisites

- **Java 25 or later**
- **Gradle** (the project defines which version to use via `gradle/wrapper/gradle-wrapper.properties`)

Verify installation:
```bash
java -version
gradle -v
```

## Build

```bash
gradle build
```

## Adding HMCTS Library Dependencies

This service's base package is `uk.gov.hmcts.cp`, which means Spring Boot component scanning picks up `@Component` classes from any library JAR under that same package. For example, `cp-audit-filter-springboot` has beans in `uk.gov.hmcts.cp.filter.audit` that get scanned directly, bypassing the library's autoconfiguration conditionals.

When adding a new `uk.gov.hmcts.cp.*` library, check whether it has `@Component`-annotated classes. If so, add an exclude filter in `Application.java`:

```java
@ComponentScan(
    basePackages = "uk.gov.hmcts.cp",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "uk\\.gov\\.hmcts\\.cp\\.filter\\.audit\\..*"
    )
)
```

Libraries under different base packages (e.g. `uk.gov.moj.cpp.authz` for `cp-auth-rules-filter`) are not affected.

## Tests

```bash
gradle test       # unit and integration tests
gradle api        # API tests
```

## Environment Setup

This project uses `.env` and `.envrc` files for environment variable management.

**Quick Setup:**
1. Install `direnv`: `brew install direnv`
2. Add to shell: `echo 'eval "$(direnv hook zsh)"' >> ~/.zshrc`
3. Allow direnv: `direnv allow`
4. Create `.env` file with your local configuration

**Server Port:** Default `8082`. Override with `export SERVER_PORT=8080`.

See the [Environment Variables Guide](docs/EnvironmentVariables.md) for full details.

## Documentation

- [Environment Variables Guide](docs/EnvironmentVariables.md)
- [JWT Filter Documentation](docs/JWTFilter.md)
- [Logging Documentation](docs/Logging.md)
- [Pipeline Documentation](docs/PIPELINE.md)
- [Spring Boot v4 Upgrade Guide](docs/SpringUpgradev4.md)

## Static Code Analysis

```bash
gradle pmdTest
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
