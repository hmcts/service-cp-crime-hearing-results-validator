# Phase 1 Data Model: Imprisonment Result Age Restriction (DD-42950)

## Upstream entity change (external dependency — not implemented in this repo)

### `DefendantDto.dateOfBirth` (new field)

| Field | Type | Notes |
|---|---|---|
| `dateOfBirth` | `java.time.LocalDate` | New, nullable field on `DefendantDto` in `api-cp-crime-hearing-results-validator`. Nullable because legacy/in-flight callers may not populate it immediately after the contract bump (mirrors how `category` was introduced as nullable on `ResultLineDto` in DD-41656). |

No other upstream entity changes are required — `DraftValidationRequest.hearingDay`
(`java.time.LocalDate`) already exists and supplies the comparison date.

## New entity: `AgeRestrictedResultContext` (this repo)

A `RuleEvaluationContext` implementation, one instance per defendant who has at least one
qualifying imprisonment-type result recorded against an offence.

```java
public record AgeRestrictedResultContext(
        String defendantId,
        String defendantName,
        boolean isUnder21,
        List<String> qualifyingOffenceIds
) implements RuleEvaluationContext
```

| Field | Type | Description |
|---|---|---|
| `defendantId` | `String` | Defendant id, or master-defendant id when the defendant is linked to a master defendant (same grouping convention as `DefendantContext`/`CustodialPreprocessor`). |
| `defendantName` | `String` | `"first last"`, falling back to whichever of first/last name is present, `"Unknown"` if neither (same convention as `CustodialPreprocessor.buildFullName`). |
| `isUnder21` | `boolean` | `true` when the defendant's 21st birthday falls **after** the hearing date (`request.getHearingDay()`); `false` when it falls on or before. `false` (fail-safe, never `true`) when `dateOfBirth` is null/absent. |
| `qualifyingOffenceIds` | `List<String>` | Ids of offences belonging to this defendant that carry at least one result line whose short code is `IMP`, `EXTIVS`, or `SPECC` (case-insensitive). |

**Validation rules encoded here** (not in CEL, per the "no branching in CEL" convention):
- Age comparison: `Period.between(dateOfBirth, hearingDay).getYears() >= 21` → `isUnder21 = false`;
  otherwise `true`. A birthday falling exactly on the hearing date counts as having been reached
  (inclusive), matching `Period.between` semantics where the difference is exactly 21 years, 0
  months, 0 days.
- Fail-safe: if `dateOfBirth` is null, or `hearingDay` is null, `isUnder21` MUST be `false` and no
  context is emitted with `isUnder21 == true` for that defendant — mirrors FR-011 and the
  precedent set by `DisqualificationExtendedTestPreprocessor`'s FR-015 fail-safe handling of a
  missing/invalid `category`.

**Interface method implementations**:

```java
@Override
public Map<String, Long> toCelContext() {
    return Map.of("isUnder21", isUnder21 ? 1L : 0L);
}

@Override
public List<String> getOffenceIdSet(String setName) {
    if ("qualifyingOffenceIds".equals(setName)) return qualifyingOffenceIds;
    throw new IllegalArgumentException("Unknown offence set: " + setName);
}

@Override
public List<String> getDefendantIdSet(String setName) {
    if ("defendantId".equals(setName)) return List.of(defendantId);
    throw new IllegalArgumentException("Unknown defendant set: " + setName);
}

@Override
public List<String> allOffenceIds() {
    return qualifyingOffenceIds;
}
```

(`defendantName()` and `defendantId()` are satisfied directly by the record's accessors, matching
the `RuleEvaluationContext` interface's default-method pattern.)

## Preprocessor: `AgeRestrictedImprisonmentPreprocessor`

- **Qualifier / `type()`**: `age-restricted-imprisonment`
- **Input**: `DraftValidationRequest` (`resultLines`, `defendants`, `hearingDay`),
  `PreprocessingDefinition` (`filterShortCodes: [IMP, EXTIVS, SPECC]`)
- **Algorithm**:
  1. Build the defendant grouping map and defendant-name map exactly as
     `CustodialPreprocessor.buildDefendantGrouping` / `buildDefendantNames` do (master-defendant
     aware).
  2. Group `resultLines` by that grouping key.
  3. For each defendant group, filter lines whose `shortCode` (uppercased) is in
     `{IMP, EXTIVS, SPECC}`. If none, skip this defendant (no context emitted) — same
     "skip if nothing qualifies" shape as `CustodialPreprocessor`.
  4. Collect the distinct `offenceId`s of the qualifying lines → `qualifyingOffenceIds`.
  5. Look up the corresponding `DefendantDto`'s `dateOfBirth`; compute `isUnder21` against
     `request.getHearingDay()` per the fail-safe rule above. **This step MUST be null-safe and
     MUST NOT throw**: if `dateOfBirth` is `null`, or `request.getHearingDay()` is `null`, set
     `isUnder21 = false` and continue — do not call `Period.between(...)` with a null argument.
     Do not let a `NullPointerException` (or any other exception) escape this preprocessor for a
     single defendant's missing data; that would otherwise propagate up through
     `CelValidationRule.evaluate()` into `DefaultValidationService`'s per-rule `catch (Exception
     e)` block, which logs and **skips the entire rule for every defendant in the request** —
     silently losing coverage for defendants whose date of birth *is* present. Guard per
     defendant inside the loop, not with a single try/catch around the whole method.
  6. Emit one `AgeRestrictedResultContext` per qualifying defendant group. Other rules
     (`DR-SENT-002`, `DR-CTL-001`, `DR-DISQ-001`, etc.) are unaffected regardless of what this
     preprocessor does, since each `ValidationRule` is evaluated independently in its own
     try/catch in `DefaultValidationService.evaluateRulesWithMdc()` (existing framework
     behaviour, not new to this feature) — call this out in tests rather than relying on it
     silently.
- **Output**: `Map<String, AgeRestrictedResultContext>` keyed by defendant (or master-defendant)
  id — same map shape `CelValidationRule` already knows how to iterate.

## Rule condition (`DR-AGE-001`, condition `AC2`)

| Property | Value |
|---|---|
| `expression` | `isUnder21 == 1` |
| `severity` | `ERROR` |
| `validationLevel` | `OFFENCE` (mandatory for `ERROR` per `ValidationIssue` javadoc) |
| `affectedOffenceSet` | `qualifyingOffenceIds` |
| `affectedDefendantSet` | `defendantId` |
| `messageTemplate` | `"The defendant is under 21 years of age and cannot receive a sentence of imprisonment."` |
| `errorMessageTemplate` | `"The defendant is under 21 years of age and cannot receive a sentence of imprisonment. This affects: ${defendantNames}."` |

No state transitions apply — this is a stateless, per-request evaluation like every other rule
in this service.
