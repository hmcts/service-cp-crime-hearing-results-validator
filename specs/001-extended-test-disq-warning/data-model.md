# Phase 1 Data Model — DD-41656

This document captures the in-process value types introduced or modified by this feature. Request/response DTOs are owned upstream by `libs.api.hearing.results.validator` — **the contract is being extended in this revision** to add `category: enum [A, I, F]` to `ResultLineDto`. See `contracts/rule-DR-DISQ-001.md` for the full contract change and how this rule consumes it.

## Revision — 2026-04-28

Original line "Request/response DTOs are owned upstream … and are unchanged" is **superseded**. The companion change to `libs.api.hearing.results.validator` adds `category: enum [A, I, F]` to `ResultLineDto`; this rule now reads it directly. Internal type changes:

- `DisqualificationContext` field `excludedFinalCount` keeps its name but its **semantics tighten**: was "any line on offence with shortCode in excluded set"; now "F-category line on offence with shortCode in excluded set". This change is invisible to the YAML — the field's role as a diagnostic counter is unchanged — but rule authors composing future conditions on `excludedFinalCount` should be aware.
- The preprocessor's algorithm step 4 (`hasNonExcludedFinal` derivation) is rewritten to gate on `category = 'F'`. The diagnostic counter `finalCategoryCount` is added alongside `excludedFinalCount` so authors can distinguish "no final result yet" (`finalCategoryCount = 0`) from "final result is excluded" (`finalCategoryCount > 0 && excludedFinalCount > 0`).
- A fail-safe branch is added for missing or malformed `category` values (per FR-015): only lines with `category` matching `'F'` (case-insensitive) count as final. Any other value (`A`, `I`, null, empty, anything unrecognised) is treated as non-final, so a relevant offence with no recognised F line silently produces no warning. Logged at INFO if a non-A/I/F category is observed, to surface upstream contract violations during integration.

---

## New types

### `RuleEvaluationContext` (sealed interface, Java)

Polymorphic context produced by a `ValidationPreprocessor` and consumed by `CelValidationRule`. Replaces the bare `DefendantContext` reference currently held in `CelValidationRule`.

```java
public sealed interface RuleEvaluationContext
        permits DefendantContext, DisqualificationContext {

    /** Returns the variable map consumed by CEL. Keys are `Long`-valued. */
    Map<String, Long> toCelContext();

    /** Returns the offence-id list named by the YAML condition's `affectedOffenceSet`. */
    List<String> getOffenceIdSet(String setName);

    /** Display name for the message template's `${defendantName}` placeholder, or null. */
    String defendantName();

    /** Full set of offence ids represented by this context (used for ordering). */
    List<String> allOffenceIds();
}
```

Notes:
- Sealed interface satisfies Constitution II's "sealed interfaces for polymorphic types".
- Existing `DefendantContext` becomes a `permits` member; its public surface is unchanged.

### `DisqualificationContext` (record, Java)

One instance per **qualifying offence** in the hearing (i.e. one issue per offence as required by FR-009 + R5).

```java
public record DisqualificationContext(
        String offenceId,
        long qualifyingCount,
        long relevantCount,
        long finalCategoryCount,    // NEW (2026-04-28): count of F-category lines on offence
        long excludedFinalCount,    // semantics tightened to F-category lines only
        long disqExtTestCount,
        List<String> qualifyingOffenceIds,
        List<String> allOffenceIds
) implements RuleEvaluationContext {

    @Override public String defendantName() { return null; }

    @Override public Map<String, Long> toCelContext() {
        return Map.of(
            "qualifyingCount",     qualifyingCount,
            "relevantCount",       relevantCount,
            "finalCategoryCount",  finalCategoryCount,
            "excludedFinalCount",  excludedFinalCount,
            "disqExtTestCount",    disqExtTestCount
        );
    }

    @Override public List<String> getOffenceIdSet(String setName) {
        return switch (setName) {
            case "qualifyingOffenceIds" -> qualifyingOffenceIds;
            case "allOffenceIds"        -> allOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }
}
```

Field semantics (per offence — see R6, refined by R3-revised 2026-04-28):

| Field | Value |
|-------|-------|
| `offenceId` | The offence id this context represents (also the singleton in `qualifyingOffenceIds` when qualifying). |
| `qualifyingCount` | `1` if relevant **AND** at least one F-category line on the offence has a non-excluded short code **AND** no line on the offence has shortCode `DDOTE`/`DDOTEL`, else `0`. |
| `relevantCount` | `1` if `OffenceDto.offenceCode` ∈ relevant set, else `0`. |
| `finalCategoryCount` | Count of result lines on this offence whose `category` equals `'F'` (case-insensitive). `0` means no final result has been recorded yet — the rule cannot fire. |
| `excludedFinalCount` | Count of **F-category** result lines on this offence whose shortCode is in the excluded list. (Semantics tightened 2026-04-28 — previously counted any line on the offence regardless of category.) |
| `disqExtTestCount` | `1` if any result line on this offence has shortCode `DDOTE` or `DDOTEL` (regardless of category), else `0`. |
| `qualifyingOffenceIds` | Singleton `[offenceId]` when qualifying, empty otherwise. |
| `allOffenceIds` | Singleton `[offenceId]` (used for ordering by `OffenceDisplayHelper`). |

### `ValidationPreprocessor` (interface, Java)

Replaces the hard-wired `CustodialPreprocessor` parameter on `CelValidationRule`.

```java
public interface ValidationPreprocessor {
    /** Returns the YAML `preprocessing.type` qualifier this preprocessor handles. */
    String type();

    /** Produces zero or more contexts from the request. Map key is opaque (offence id, defendant id, …). */
    Map<String, ? extends RuleEvaluationContext> preprocess(
            DraftValidationRequest request,
            PreprocessingDefinition config);
}
```

### `PreprocessorRegistry` (component, Java)

Resolves a `ValidationPreprocessor` by qualifier at rule-evaluation time.

```java
@Component
public class PreprocessorRegistry {
    private final Map<String, ValidationPreprocessor> byType;

    public PreprocessorRegistry(List<ValidationPreprocessor> preprocessors) {
        this.byType = preprocessors.stream()
            .collect(Collectors.toUnmodifiableMap(ValidationPreprocessor::type, p -> p));
    }

    public ValidationPreprocessor require(String type) {
        ValidationPreprocessor p = byType.get(type);
        if (p == null) {
            throw new IllegalStateException("No preprocessor registered for type: " + type);
        }
        return p;
    }
}
```

### `DisqualificationExtendedTestPreprocessor` (component, Java)

Implements `ValidationPreprocessor` with `type() = "disqualification-extended-test"`.

Algorithm (revised 2026-04-28 — gate on `category = 'F'`):

1. Build `Map<String, OffenceDto> offenceById` from `request.getOffences()`.
2. Build `Map<String, List<ResultLineDto>> resultsByOffence` by grouping `request.getResultLines()` on `offenceId`.
3. Read the YAML config:
   - `relevantOffenceCodes: List<String>` (the five Home Office codes; matched case-insensitively against `OffenceDto.offenceCode`).
   - `excludedFinalShortCodes: List<String>` (the nine excluded codes).
   - `extendedTestShortCodes: List<String>` (`DDOTE`, `DDOTEL`).
4. For each offence (iterating `offenceById.entrySet()`), iterate the result lines on that offence and compute:
   - `relevant = offenceCode ∈ relevantOffenceCodes` (case-insensitive).
   - `finalLines = result lines on this offence where category equals 'F'` (case-insensitive). Lines with `category` missing, null, or any value other than `'A'`, `'I'`, `'F'` are **not** treated as final (FR-015 fail-safe). Non-A/I/F values are logged at INFO once per request to surface upstream contract violations during integration.
   - `finalCategoryCount = |finalLines|`.
   - `finalNonExcluded = ∃ line ∈ finalLines : line.shortCode ∉ excludedFinalShortCodes` (case-insensitive). This is the rule's **positive gate**.
   - `excludedFinalCount = count of lines in finalLines whose shortCode ∈ excludedFinalShortCodes`. (Diagnostic only — does **not** drive `qualifying` directly; if every F line is excluded then `finalNonExcluded` is false anyway.)
   - `disqExtTest = ∃ result on this offence (regardless of category) with shortCode ∈ extendedTestShortCodes` (case-insensitive). DDOTE/DDOTEL on an `A`/`I` line still suppresses — the rule's purpose is satisfied wherever the disqualification line sits.
   - `qualifying = relevant && finalNonExcluded && !disqExtTest`.
5. Emit one `DisqualificationContext` per offence (keyed on offenceId in the returned map). Non-relevant offences produce a context with all counts `0` and an empty `qualifyingOffenceIds` — the CEL condition `qualifyingCount > 0` then fails for them, producing no issue.

Notes:
- Returning a context for every offence (including non-relevant ones) is simpler than filtering and matches the existing `CustodialPreprocessor` pattern of "let CEL decide".
- All short-code and `category` comparisons are upper-cased once at the start (matching `CustodialPreprocessor`'s style). For `category`, valid values after normalisation are `A`, `I`, `F`; any other value falls into the FR-015 fail-safe path and is treated as non-final.
- The previous "anything-not-excluded counts as final" inference is **retired**. Result lines with `category` ∈ `{A, I}` are no longer evidence of finality; an offence with only such lines correctly produces no warning (BA scenarios doc, scenario 5).

### `PreprocessingDefinition` (record, Java) — modified

The existing `PreprocessingDefinition` carries `filterShortCodes`, `groupBy`, `skipWhenGroupCount`. The new rule needs different fields, so the YAML schema gets two additions and stays back-compatible:

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PreprocessingDefinition {
    private String type;
    private List<String> filterShortCodes;        // existing — used by custodial-concurrent-consecutive
    private String groupBy;                        // existing
    private int skipWhenGroupCount;                // existing
    private List<String> relevantOffenceCodes;     // NEW — used by disqualification-extended-test
    private List<String> excludedFinalShortCodes;  // NEW — used by disqualification-extended-test
    private List<String> extendedTestShortCodes;   // NEW — used by disqualification-extended-test
}
```

`@Data` keeps existing behaviour; new fields default to null and are read only by the new preprocessor. (Alternative considered: nested polymorphic config records per type. Rejected for v1 — adds a YAML deserialisation bump for no current consumer benefit.)

---

## Modified types

### `CelValidationRule` (class, Java)

Constructor signature changes from:

```java
public CelValidationRule(String rulePath,
                         CustodialPreprocessor preprocessor,    // <-- removed
                         CelExpressionEvaluator evaluator,
                         MessageTemplateResolver messageResolver,
                         OffenceDisplayHelper offenceDisplayHelper,
                         RuleOverrideService ruleOverrideService)
```

to:

```java
public CelValidationRule(String rulePath,
                         PreprocessorRegistry registry,         // <-- new
                         CelExpressionEvaluator evaluator,
                         MessageTemplateResolver messageResolver,
                         OffenceDisplayHelper offenceDisplayHelper,
                         RuleOverrideService ruleOverrideService)
```

Inside `evaluate(...)`:

```java
ValidationPreprocessor preprocessor = registry.require(ruleDefinition.getPreprocessing().getType());
Map<String, ? extends RuleEvaluationContext> contexts =
        preprocessor.preprocess(request, ruleDefinition.getPreprocessing());
for (RuleEvaluationContext context : contexts.values()) {
    // unchanged: build celContext, evaluate conditions, build ValidationIssue
}
```

The `MessageTemplateResolver.resolve(...)` call passes `context.defendantName()` (which is `null` for `DisqualificationContext`); the existing resolver already null-checks `defendantName`, so no change there. `OffenceDisplayHelper` is unchanged.

### `ValidationRuleAutoConfiguration` (class, Java)

Replace the `CustodialPreprocessor` parameter with `PreprocessorRegistry`:

```java
@Bean("validationRules")
public List<ValidationRule> validationRules(
        PreprocessorRegistry registry,
        CelExpressionEvaluator evaluator,
        MessageTemplateResolver messageResolver,
        OffenceDisplayHelper offenceDisplayHelper,
        RuleOverrideService ruleOverrideService) throws IOException { ... }
```

The body is unchanged except the constructor argument passed to `new CelValidationRule(...)`.

### `CustodialPreprocessor` (class, Java)

- Implements `ValidationPreprocessor` with `type() = "custodial-concurrent-consecutive"`.
- Return type widens from `Map<String, DefendantContext>` to `Map<String, ? extends RuleEvaluationContext>` (still concretely returns `DefendantContext` instances).
- No behaviour change.

### `DefendantContext` (record, Java)

- Add `implements RuleEvaluationContext`.
- Existing `toCelContext()` and `getOffenceIdSet()` already match the interface — no rename or signature change.
- Existing `defendantName()` is the record accessor — already matches the interface.
- Add `allOffenceIds()` (already a record component, already an accessor — interface implementation is automatic).

---

## YAML schema delta (`PreprocessingDefinition`)

```yaml
preprocessing:
  type: <string>                          # existing — required
  # ---- used by `custodial-concurrent-consecutive` ----
  filterShortCodes: [<string>, ...]       # existing — optional per type
  groupBy: <string>                       # existing — optional per type
  skipWhenGroupCount: <int>               # existing — optional per type
  # ---- used by `disqualification-extended-test` ----
  relevantOffenceCodes: [<string>, ...]   # NEW — required for this type
  excludedFinalShortCodes: [<string>, ...] # NEW — required for this type
  extendedTestShortCodes: [<string>, ...] # NEW — required for this type
```

The `spec-validator` agent (Workflow + Constitution IV) gates on this schema and on every CEL expression compiling.

---

## Validation flow (after refactor)

```
ValidationController
  └─ DefaultValidationService.validate(request)
       └─ for each CelValidationRule (priority order):
            ├─ override = RuleOverrideService.findOverride(ruleId)
            ├─ if !override.enabled → skip
            ├─ preprocessor = registry.require(yaml.preprocessing.type)
            ├─ contexts = preprocessor.preprocess(request, yaml.preprocessing)
            └─ for each context, for each condition in yaml.conditions:
                 ├─ if CelExpressionEvaluator.evaluate(condition.expression, context.toCelContext()):
                 │    ├─ affectedIds = context.getOffenceIdSet(condition.affectedOffenceSet)
                 │    ├─ message = MessageTemplateResolver.resolve(...)
                 │    ├─ severity = SeverityCeiling.resolve(condition.severity, override.dbSeverity)
                 │    └─ emit ValidationIssue
```

Nothing in the controller, response shape, or `ValidationIssue` schema changes.
