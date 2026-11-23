# Quickstart: Electronic Monitoring Status Validation (CRA-38)

## Prerequisites

- Java 25, Docker Desktop running
- Local checkout on `dev/CRA-38-electronic-monitoring-status`

## Run tests

```bash
# Unit + integration tests
gradle test

# Single test class
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.ElectronicMonitoringPreprocessorTest"
gradle test --tests "uk.gov.hmcts.cp.integration.ElectronicMonitoringStatusIT"

# Full build (Checkstyle + PMD + tests)
gradle build

# API tests (starts docker-compose automatically)
gradle api
```

## Test the rule locally via HTTP

Start the service:

```bash
docker-compose up -d
# or
gradle bootRun
```

Then use the HTTP scratch file:

```bash
# IntelliJ: open src/main/java/uk/gov/hmcts/cp/services/rules/http/CRA-38.http and click Run
# or curl:
curl -s -X POST http://localhost:4550/validate \
  -H "Content-Type: application/json" \
  -d @specs/018-electronic-monitoring-status/contracts/validation-api-delta.md
```

## Key files

| File | Purpose |
|------|---------|
| `src/main/resources/rules/DR-EMON-017.yaml` | YAML rule — edit to tune conditions/messages |
| `src/main/java/.../ElectronicMonitoringPreprocessor.java` | Preprocessor logic |
| `src/main/java/.../ElectronicMonitoringContext.java` | CEL context record |
| `src/main/resources/db/migration/V1.019__*.sql` | DB migration for the validation_rule row |
| `src/test/java/.../ElectronicMonitoringPreprocessorTest.java` | Unit tests |
| `src/test/java/.../integration/ElectronicMonitoringStatusIT.java` | Integration tests |
| `src/apiTest/java/.../ElectronicMonitoringStatusApiHttpLiveTest.java` | Live API tests |

## Verify the rule is registered at startup

```bash
grep "DR-EMON-017" logs/app.log
# or
curl http://localhost:4550/validate/rules | jq '.[] | select(.id == "DR-EMON-017")'
```
