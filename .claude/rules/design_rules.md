# Architecture & Domain Rules

## Layer Architecture

```
Controller (ValidationController)
    → ValidationService (DefaultValidationService)
        → CelValidationRule (one per YAML file under src/main/resources/rules/)
            → ValidationPreprocessor → CelExpressionEvaluator → MessageTemplateResolver
```

- **Controllers:** HTTP handling ONLY. No business logic. Delegate to `ValidationService`.
- **ValidationService:** Orchestrates the rule list. Applies feature toggle (`RESULTS_VALIDATION`), iterates `CelValidationRule` beans (sorted by priority), aggregates issues into errors/warnings.
- **CelValidationRule:** Owns the YAML for a single rule. Runs preprocessing, then evaluates each CEL condition against the resulting context map.
- **ValidationPreprocessor:** Transforms the request into a domain-specific context record (counts + offence-id sets) ready for CEL evaluation. Selected by `preprocessing.type` via the preprocessor registry.
- **CelExpressionEvaluator:** Compiles and caches CEL expressions per (expression, context-keys) tuple.

NEVER put business logic in controllers.
NEVER access repositories directly from controllers.
NEVER hard-wire a single preprocessor implementation into `CelValidationRule` — dispatch by qualifier.

## Domain Concepts

| Concept              | Description                                                                                                  |
|----------------------|--------------------------------------------------------------------------------------------------------------|
| `DraftValidationRequest` | Request body — hearing identifier plus offences, defendants, and result lines drawn from the draft state. |
| Offence              | A charge in the hearing. Has an `id`, an `offenceCode` (Home Office code, e.g. `RT88026`), and a display order. |
| Defendant            | A person charged. Has an `id`, optional `masterDefendantId`, and a name.                                     |
| Result line          | A recorded outcome on an offence. Carries a `shortCode` (e.g. `IMP`, `COEW`, `DDOTE`, `wdrn`) and offence linkage. |
| Validation rule      | A YAML file `DR-*.yaml` containing rule metadata, a `preprocessing` block, and one or more `conditions` (each a CEL expression with a severity and a message template). |
| Validation issue     | An `ERROR` or `WARNING` produced by a triggered condition. Errors block sharing; warnings advise.            |
| Severity ceiling     | DB row in `validation_rule` (id, enabled, severity) that caps a rule's runtime severity downward. Never promotes. |

## Rule Engine Flow

1. `ValidationRuleAutoConfiguration` discovers `rules/DR-*.yaml` from classpath at startup.
2. Each YAML becomes a `CelValidationRule` bean (sorted by `priority`).
3. `DefaultValidationService.validate()` iterates rules and calls `rule.evaluate(request)`.
4. `CelValidationRule.evaluate()`:
   - Looks up the preprocessor via the registry using `preprocessing.type`.
   - Calls `preprocessor.preprocess(request, preprocessingDefinition)` → `Map<String, ContextRecord>`.
   - For each context, evaluates every `conditions[].expression` via `CelExpressionEvaluator`.
   - For triggered conditions, expands `messageTemplate` placeholders via `MessageTemplateResolver` and resolves the named `affectedOffenceSet`.
5. `RuleOverrideService` checks the `validation_rule` DB table (Caffeine-cached) and applies `SeverityCeiling.resolve()` — capping severity downward only.

## YAML Rule Schema (summary)

```yaml
rule:
  id: "DR-<CATEGORY>-<NNN>"
  title: "Human-readable title"
  description: "Optional longer description"
  priority: 1000
  enabled: true
  preprocessing:
    type: "<preprocessor-qualifier>"
    # ... preprocessor-specific config
  conditions:
    - id: "AC<n>"
      name: "Short condition name"
      expression: "<CEL boolean expression over context variables>"
      severity: ERROR | WARNING
      messageTemplate: >-
        Message text using ${placeholder} tokens
      affectedOffenceSet: "<context-list-name>"
```

## Adding a New Rule

1. **YAML first.** Create `src/main/resources/rules/DR-<CATEGORY>-<NNN>.yaml`. The rule auto-loads at startup — no Java code change is required if an existing preprocessor type fits.
2. **If a new preprocessor type is needed:**
   - Add a `ValidationPreprocessor` `@Component` whose qualifier matches the YAML `preprocessing.type` string
   - Add a corresponding context record (extending or sibling to `DefendantContext`) with a `toCelContext()` returning `Map<String, Long>`
   - Add unit tests for the preprocessor and the rule (TDD: write tests first — Constitution Principle VIII)
3. **Test the YAML:** the `spec-validator` agent compiles every CEL expression, validates the schema, and confirms the `preprocessing.type` resolves to a registered bean.

## Test the framework once, not the rule again (severity ceiling / runtime overrides)

The runtime-override mechanism (`validation_rule` table → `RuleOverrideService` → Caffeine cache → `SeverityCeiling.resolve()` → `CelValidationRule`) is **rule-agnostic**. The same code path executes for every rule. Therefore: **per-rule integration tests of override / severity-ceiling behaviour are duplicative and should be rejected by reviewers.** The mechanism is proven once, against `DR-SENT-002`, in `ValidationRuleOverrideIntegrationTest.java`. New rules (current or future) inherit that coverage without their own override IT.

What a new rule's integration test SHOULD cover:

- Rule-specific input → expected output (the rule's actual logic)
- Edge cases unique to that rule's preprocessor

What it SHOULD NOT cover (already proven framework-level):

- Inserting a `validation_rule` row with `enabled=false` and asserting suppression.
- DB severity ceiling capping the YAML severity downward.
- Severity ceiling refusing to promote.
- Cache invalidation across runtime row changes.

If the framework-level IT is missing a scenario you need, **extend `ValidationRuleOverrideIntegrationTest.java`** rather than copying it into a new per-rule IT.

## Out-of-Scope (do not add)

- Business logic in CEL expressions beyond simple count comparisons. If an expression needs branching, add the branching to the preprocessor and expose a single boolean / count to CEL.
- Mutating the request from inside a preprocessor.
- Cross-rule dependencies. Each `CelValidationRule` evaluates independently.
- Promoting severity at runtime — the DB ceiling is one-way (Constitution Principle VI).
