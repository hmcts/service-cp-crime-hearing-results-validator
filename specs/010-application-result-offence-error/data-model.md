# Phase 1 Data Model: Application Results Recorded Against Offences – ERROR Validation

## Engine generalisation (this repo, shared code — see research.md R5)

### `ConditionDefinition` (modified)

Add one new, optional field:

| Field | Type | Notes |
|---|---|---|
| `calculatedValuePlaceholderName` | `String` (nullable) | Names the `${...}` token that the calculated-value mechanism expands. When absent, defaults to `"calculatedEndDate"` — the current hardcoded behaviour, so every existing rule YAML (`DR-COEW-005`, `DR-YRO-004`) needs **no change** and keeps expanding `${calculatedEndDate}` exactly as today. |

### `CelValidationRule.evaluate()` (modified)

Two changes to the existing per-offence loop and the `errorMessage` build step:

1. `calculatedValuePlaceholder(calculatedValue)` becomes
   `calculatedValuePlaceholder(placeholderName, calculatedValue)`, using
   `condition.calculatedValuePlaceholderName()` (defaulted to `"calculatedEndDate"` when null)
   instead of the hardcoded literal. Existing call site (building the per-offence
   `messageTemplate`) passes the resolved name through; behaviour for existing rules is
   byte-for-byte unchanged since they never set the new field.
2. The `errorMessage` build step gains the same calculated-value resolution the `messageTemplate`
   loop already has: when `condition.calculatedValueSet() != null` **and**
   `offenceIdsForTemplate` is non-empty, resolve
   `context.getCalculatedValue(calculatedValueSet, offenceIdsForTemplate.get(0))` and pass it as
   an extra placeholder into the `errorMessageTemplate` resolve call, using the same
   `calculatedValuePlaceholder(...)` helper. (Every existing rule with an `errorMessageTemplate`
   — `DR-SENT-001` AC2, `DR-AGE-007` AC2 — has `calculatedValueSet == null`, so this is a no-op
   for them: the new branch is simply never entered.) When a context spans more than one offence
   id, only the first is used for this purpose — true for every current caller of
   `calculatedValueSet`, since `CommunityOrderContext`/`YouthRehabilitationContext` use it for a
   per-offence inline message only, and this feature's own context (below) always covers exactly
   one offence.

No other method signatures change. `RuleEvaluationContext.getCalculatedValue(String, String)` is
reused unchanged.

## Engine fix: dedup + blank-name guard in `DefaultValidationService.appendDefendantName` (research.md R8)

`DefaultValidationService.appendDefendantName` currently:

```java
private static void appendDefendantName(final Map<String, List<String>> errorNamesByRule,
                                         final String ruleId,
                                         final String name) {
    errorNamesByRule.computeIfAbsent(ruleId, k -> new ArrayList<>()).add(name);
}
```

Changes to:

```java
private static void appendDefendantName(final Map<String, List<String>> errorNamesByRule,
                                         final String ruleId,
                                         final String name) {
    if (name == null || name.isBlank()) {
        return;
    }
    final List<String> names = errorNamesByRule.computeIfAbsent(ruleId, k -> new ArrayList<>());
    if (!names.contains(name)) {
        names.add(name);
    }
}
```

**Required companion change, same method's caller** — without this second change, a
`ruleId::errorMessage` group whose *every* contributing name is blank (e.g. the sole breaching
defendant for that group has an unresolvable name) never gets an entry in `errorNamesByRule` at
all (the guard above returns before `computeIfAbsent` runs), so the aggregation loop's lookup
returns `null` rather than an empty list, and `MessageTemplateResolver.resolveDefendantNames` /
`formatDefendantNames` throws a `NullPointerException` on `names.isEmpty()` when the hearing has
more than one defendant. **Identified during a second `/speckit.analyze` pass** — this failure
mode did not exist before the guard above, since the previous unconditional `.add()` guaranteed
a non-null (if occasionally blank-containing) list for any group that existed at all. In the same
method that builds `errorMessages`, change:

```java
final List<String> names = errorNamesByTemplate.get(entry.getKey());
```

to:

```java
final List<String> names = errorNamesByTemplate.getOrDefault(entry.getKey(), List.of());
```

This is the **only** other change to `DefaultValidationService`. No other method, and no other
call site, is touched. Behaviour for every existing rule (`DR-SENT-001`, `DR-AGE-007`,
`DR-SEX-008`) is unchanged: each already only ever calls `appendDefendantName` with a
non-blank name, so `errorNamesByTemplate` already always had a populated entry for every key in
`errorBaseByTemplate` for them, and `getOrDefault` behaves identically to `get` whenever the key
is present.

For this feature, this is what makes two `ApplicationResultBreachContext`s sharing the same
defendant and the same breaching label (spec.md's "same code, two offences, same defendant" edge
case) collapse to one name in the aggregated "This affects" list instead of two, and what makes
an unresolvable defendant name (see below) — whether it's the only name in its group or one of
several — disappear from that list entirely rather than rendering as an empty or placeholder
entry, or crashing the response.

## New entity: `ApplicationResultBreachContext` (this repo)

A `RuleEvaluationContext` implementation, one instance per breaching result line — i.e. per
`ResultLineDto` whose `shortCode` is in the application-only set **and** whose `offenceId` is
non-null/non-blank.

```java
public record ApplicationResultBreachContext(
        String defendantId,
        String defendantName,
        String offenceId,
        String resultLabel
) implements RuleEvaluationContext
```

| Field | Type | Description |
|---|---|---|
| `defendantId` | `String` | The breaching result line's own `defendantId`, taken as-is (no master-defendant collapsing — each breach is reported individually, so there is no cross-offence count to collapse, unlike `CustodialPreprocessor`). |
| `defendantName` | `String` | `"first last"`, falling back to whichever of first/last name is present on the matching `DefendantDto`. When the defendant cannot be resolved at all (no matching `DefendantDto`, or one with neither first nor last name), resolves to `""` (empty string) — **never** `null` and **never** a placeholder like `"Unknown"`. `""` is deliberate: combined with the `appendDefendantName` guard above, it makes this defendant silently disappear from the aggregated "This affects" list (per spec.md's edge case: "the entry is omitted ... rather than rendering an empty name") without ever inserting a blank entry into the rendered text, and without pushing the issue down `DefaultValidationService`'s null-triggered "standalone message" branch (which would otherwise leave the `${defendantNames}` token unresolved — see research.md R8). |
| `offenceId` | `String` | The breaching result line's `offenceId`. |
| `resultLabel` | `String` | `resultLine.getLabel()` if present and non-blank; otherwise falls back to `resultLine.getShortCode()` so the message never renders an empty or `null` value (research.md R4). |

**Interface method implementations**:

```java
@Override
public Map<String, Long> toCelContext() {
    return Map.of("hasBreach", 1L);
}

@Override
public List<String> getOffenceIdSet(String setName) {
    if ("breachOffenceId".equals(setName)) return List.of(offenceId);
    throw new IllegalArgumentException("Unknown offence set: " + setName);
}

@Override
public List<String> allOffenceIds() {
    return List.of(offenceId);
}

@Override
public List<String> getDefendantIdSet(String setName) {
    if ("defendantId".equals(setName)) return List.of(defendantId);
    throw new IllegalArgumentException("Unknown defendant set: " + setName);
}

@Override
public String getCalculatedValue(String setName, String offenceId) {
    if (!"resultLabelByOffenceId".equals(setName)) {
        throw new IllegalArgumentException("Unknown calculated-value set: " + setName);
    }
    return this.offenceId.equals(offenceId) ? resultLabel : null;
}
```

(`defendantName()` and `defendantId()` are satisfied directly by the record's accessors, matching
the `RuleEvaluationContext` interface's default-method pattern.)

**CEL context is deliberately trivial**: `hasBreach` is always `1` because a context is only ever
constructed for an actual breach — the preprocessor does the branching (short-code membership,
null-offence guard), matching the "no branching in CEL" convention. The condition expression is
simply `hasBreach == 1`.

## Preprocessor: `ApplicationResultOffencePreprocessor`

- **Qualifier / `type()`**: `application-result-offence`
- **Input**: `DraftValidationRequest` (`resultLines`, `defendants`),
  `PreprocessingDefinition` (`applicationOnlyShortCodes: [ARBSFG, ARBSPG, ARBSR, VT, G, LAREP,
  LAREPCC, ORDC, LATG, LATR, LAWD, RFSD, SMAR]`)
- **Algorithm**:
  1. Build a `defendantId -> DefendantDto` lookup map from `request.getDefendants()` (direct id
     match; no master-defendant collapsing needed — see field notes above).
  2. For each `resultLine` in `request.getResultLines()`:
     - Skip if `resultLine.getOffenceId()` is null or blank (defensive guard for a future
       application-linked result — see research.md R1; never true against the current contract,
       but keeps this preprocessor correct if that changes upstream without a code change here).
     - Skip if `resultLine.getShortCode()` (case-insensitive) is not in
       `applicationOnlyShortCodes`.
     - Otherwise, this is a breach: resolve `resultLabel` per the fallback rule above, resolve
       `defendantName` from the lookup map (or `""` — see the field notes above; never
       `"Unknown"`), and emit one `ApplicationResultBreachContext` keyed by
       `resultLine.getResultLineId()`.
  3. This preprocessor **must not throw** for a single malformed result line (missing
     `resultLineId`, unresolvable defendant, etc.) — guard per line inside the loop, matching the
     existing convention (`DefaultValidationService.evaluateRulesWithMdc()`'s per-rule
     `catch (Exception e)` would otherwise skip this rule for the *entire* request on one bad
     line, silently losing coverage for every other breach in that request).
  4. This preprocessor performs **no** deduplication of its own: an offence carrying both an
     application-only result and a non-breaching, valid result only ever produces a breach
     context for the application-only line (the valid line simply never matches step 2's filter,
     so it's silently skipped — no special-casing needed). Two breaching result lines carrying
     the *same* application-only code against *different* offences for the *same* defendant each
     still get their own context (one per offence, per `resultLineId`) — deliberately, since AC2
     requires an inline error on each offence. Collapsing the defendant's name to appear once in
     the aggregated page-level "This affects" list, despite two contexts existing, is the
     aggregation layer's job (research.md R8 / the `appendDefendantName` fix above), not this
     preprocessor's.
- **Output**: `Map<String, ApplicationResultBreachContext>` keyed by `resultLineId` — one entry
  per breach, so N breaches (whether on one offence, several offences for one defendant, or
  across several defendants) always produce N map entries and therefore N separately-evaluated
  contexts.

## Rule condition (`DR-APP-009`, condition `AC1`)

| Property | Value |
|---|---|
| `expression` | `hasBreach == 1` |
| `severity` | `ERROR` |
| `validationLevel` | `OFFENCE` (mandatory for `ERROR` per `ValidationIssue`'s javadoc) |
| `affectedOffenceSet` | `breachOffenceId` |
| `affectedDefendantSet` | `defendantId` |
| `calculatedValueSet` | `resultLabelByOffenceId` |
| `calculatedValuePlaceholderName` | `resultLabel` |
| `messageTemplate` | `"Remove ${resultLabel} from this offence. It is an application result, so it can only be added to an application."` |
| `errorMessageTemplate` | `"${resultLabel} is an application result. It cannot be added to an offence. Remove it from the offence and add an application to the hearing. This affects: ${defendantNames}."` |

One condition covers both AC1 and AC2: AC1 is the single-breach case of this condition firing
once; AC2 is the same condition firing once per breach, with the service-level `ruleId::errorMessage`
grouping (research.md R6) and per-offence inline-error positioning (existing
`buildAffectedOffences`) doing the "all errors displayed" work with no further YAML or Java
branching.

No state transitions apply — this is a stateless, per-request evaluation like every other rule
in this service.
