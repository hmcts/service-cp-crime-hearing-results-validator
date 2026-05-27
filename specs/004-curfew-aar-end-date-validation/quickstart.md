# Developer Quickstart: DD-41655 Curfew / AAR Period End-Date Validation

## Branch
```
DD-41655-curfew-aar-end-date-validation
```

## Prerequisite — upstream library bump (BLOCKING)

Before writing `CurfewPeriodPreprocessor`, the `api-cp-crime-hearing-results-validator` library
must be at `0.1.8` with `type` and `childPrompts` on `Prompt`.

Check current version:
```bash
grep api-hearing-results-validator gradle/libs.versions.toml
```

If still `0.1.7`, raise the upstream PR first and update `libs.versions.toml` once `0.1.8` is published.

## Run tests
```bash
gradle test                      # unit + integration
gradle test --tests "*.CurfewPeriodPreprocessorTest"
gradle test --tests "*.CurfewPeriodRuleIntegrationTest"
```

## Full build (required before PR)
```bash
gradle build
gradle checkstyleMain pmdMain
gradle jacocoTestReport
```

## Implementation order (TDD — tests before production code)

1. **Author `DR-COEW-002.yaml`** — YAML first (Constitution Principle I)
2. **`RuleEvaluationContext`** — add `stringVariables()` default
3. **`MessageTemplateResolverTest`** — add failing tests for `${expectedEndDate}` resolution, then fix `MessageTemplateResolver`
4. **`CurfewPeriodContextTest`** — failing tests for record, then `CurfewPeriodContext`
5. **`PreprocessingDefinition`** — add `yroShortCodes`
6. **`CurfewPeriodPreprocessorTest`** — failing unit tests (all scenarios from plan.md R-3), then `CurfewPeriodPreprocessor`
7. **`CelValidationRule`** — wire `stringVariables()` and `affectedDefendants`
8. **`CurfewPeriodRuleIntegrationTest`** — failing IT scenarios, then verify green

## Key files

| File | Purpose |
|------|---------|
| `src/main/resources/rules/DR-COEW-002.yaml` | Rule definition — edit here for message text / severity |
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodPreprocessor.java` | All date arithmetic lives here |
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CurfewPeriodContext.java` | Context record — exposes `violationCount` (CEL) and `expectedEndDate` (template) |
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/RuleEvaluationContext.java` | Interface — add `stringVariables()` default |
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/MessageTemplateResolver.java` | Extend to resolve `${key}` from `stringVariables` |
| `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CelValidationRule.java` | Framework wiring — two small changes |
| `gradle/libs.versions.toml` | Bump library version here |

## Date arithmetic reference

| Violation type | Base date | Period source | Calculation |
|----------------|-----------|---------------|-------------|
| CUR, YRC2 | `startDate` prompt | `curfewPeriod` DURATION | `base.plus(qty, unit).minusDays(1)` |
| CURE, YRC1 | `startDateOfTagging` prompt | `curfewAndElectronicMonitoringPeriod` DURATION | `base.plus(qty, unit).minusDays(1)` |
| AAR (CO only) | `request.getHearingDay()` | `numberOfDaysToAbstainFromConsumingAnyAlcohol` INT | `base.plusDays(qty).minusDays(1)` |

DURATION child unit mapping:
```java
"Days"   → ChronoUnit.DAYS
"Weeks"  → ChronoUnit.WEEKS
"Months" → ChronoUnit.MONTHS
// any other value → log WARN, return null (skip this line)
```

## Prompt reading reference

```java
// DATE prompt (startDate, startDateOfTagging, endDate, endDateOfTagging, until)
LocalDate date = LocalDate.parse(prompt.getPromptValue());

// DURATION prompt (curfewPeriod, curfewAndElectronicMonitoringPeriod)
Prompt child = prompt.getChildPrompts().get(0);
String unit  = child.getPromptRef();   // "Days" | "Weeks" | "Months"
long   qty   = Long.parseLong(child.getPromptValue());

// INT prompt (numberOfDaysToAbstainFromConsumingAnyAlcohol)
int days = Integer.parseInt(prompt.getPromptValue());
```

## Context map key convention

```
"CUR:<defendantId>:<offenceId>"
"CURE:<defendantId>:<offenceId>"
"AAR:<defendantId>:<offenceId>"
```

One entry per violation. Map is empty when no violations detected.

## Spec-validator checklist (before merge)

- [ ] `DR-COEW-002.yaml` passes CEL compile (`violationCount > 0` is valid)
- [ ] `preprocessing.type = "curfew-period-check"` resolves to `CurfewPeriodPreprocessor` in registry
- [ ] `affectedOffenceSet = "violatedOffenceIds"` handled by `CurfewPeriodContext.getOffenceIdSet()`
- [ ] `${expectedEndDate}` placeholder resolved by `MessageTemplateResolver.stringVariables()`
- [ ] No `System.out` in any new file
- [ ] No wildcard imports
