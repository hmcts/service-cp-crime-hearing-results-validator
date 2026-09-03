# Data Model: YRO Curfew Requirement Duration Validation (DR-YRO-001 extension)

**Branch**: `dev/DD-42850-YRO-Duration`
**Date**: 2026-07-20

---

## Existing Entities (unchanged)

### `DraftValidationRequest` (upstream library `0.2.1`)

| Field | Type | Notes |
|-------|------|-------|
| `hearingId` | `String` | Required |
| `hearingDay` | `LocalDate` | Not used by this feature (no fixed-start-date requirement type on a YRO) |
| `resultLines` | `List<ResultLineDto>` | Flat list of all result lines across all defendants/offences |
| `defendants` | `List<DefendantDto>` | For display name lookup; `masterDefendantId` used for dedupe grouping |
| `offences` | `List<OffenceDto>` | For offence display ordering |

### `ResultLineDto` / `Prompt` (upstream library `0.2.1`)

| Field | Type | Notes |
|-------|------|-------|
| `shortCode` | `String` | e.g. `YROEW`, `YRC2`, `YRC1` |
| `defendantId` | `String` | Groups line to a defendant |
| `offenceId` | `String` | Groups line to an offence |
| `prompts` | `List<Prompt>` | `promptRef` / `promptValue` pairs entered by the caseworker |

**Prompt ref keys used by `DR-YRO-001` (existing + new):**

| Result short code | `promptRef` | Meaning | Status |
|---|---|---|---|
| YROEW/YRONI/YROFEW/YROISS/YROINI | `endDate` | YRO order end date | Existing |
| YRC2 | `endDate` | Curfew requirement end date | Existing |
| YRC1 | `endDateOfTagging` | Curfew with electronic monitoring — end date of tagging | Existing |
| YRC3 | `endDate` | Further curfew end date | Existing |
| **YRC2** | **`startDate`** | **Start date of the curfew requirement** | **New** |
| **YRC2** | **`curfewPeriod`** | **Curfew period (e.g. `"21 Days"`)** | **New** |
| **YRC1** | **`startDateOfTagging`** | **Start date of tagging** | **New** |
| **YRC1** | **`curfewAndElectronicMonitoringPeriod`** | **Curfew and electronic monitoring period** | **New** |

New keys are unverified against the real upstream contract — see research.md Decision 2 and the
Risk Register in plan.md.

---

## `YouthRehabilitationContext` — new fields (extends existing record)

```
YouthRehabilitationContext  (additions)
  ├── curDurationMismatchCount: long              — YRC2 end date != Start date + Curfew period − 1 day
  ├── cureDurationMismatchCount: long             — YRC1 end date of tagging != Start date of tagging + period − 1 day
  ├── curDurationMismatchOffenceIds: List<String>
  ├── cureDurationMismatchOffenceIds: List<String>
  ├── curCalculatedEndDateByOffenceId: Map<String, String>   — offenceId → dd/MM/yyyy correct end date
  └── cureCalculatedEndDateByOffenceId: Map<String, String>
```

(Field names deliberately reuse the `cur`/`cure` prefixes already established by the existing
`curViolationCount`/`cureViolationCount` fields in this same record, for the same requirement types.)

`toCelContext()` gains two new entries: `curDurationMismatchCount`, `cureDurationMismatchCount`
(both `Long`).

`getOffenceIdSet(setName)` gains two new cases: `"curDurationMismatchOffenceIds"`,
`"cureDurationMismatchOffenceIds"`.

**New**: `getCalculatedValue(setName, offenceId)` (overrides the new `RuleEvaluationContext` default
method) — switches on `setName` across the two `*CalculatedEndDateByOffenceId` maps and returns
`map.get(offenceId)` (or `null` if absent — by construction this never happens for an offence
present in the corresponding `*DurationMismatchOffenceIds` list).

---

## `YouthRehabilitationPreprocessor` — algorithm addition

The existing per-offence loop already computes `orderEndDate` and, when non-null, runs the three
AC2a/b/c order-vs-requirement checks. The new duration checks are added as an **unconditional**
second pass per offence (independent of `orderEndDate`):

```
Additional per-offence steps (independent of orderEndDate):

YRC2 line present:
  startDate = parsePromptDate(line, "startDate", offenceId)
  period    = parsePromptPeriod(line, "curfewPeriod", offenceId)      # amount + ChronoUnit
  endDate   = parsePromptDate(line, "endDate", offenceId)             # already parsed for AC2a
  if startDate, period, endDate all present:
      expected = startDate.plus(period.amount(), period.unit()).minusDays(1)
      if !endDate.isEqual(expected):
          add offenceId to curDurationMismatchOffenceIds
          curCalculatedEndDateByOffenceId[offenceId] = expected.format(dd/MM/yyyy)

YRC1 line present: same shape, using "startDateOfTagging" / "curfewAndElectronicMonitoringPeriod" /
  "endDateOfTagging"
```

An offence contributes at most one mismatch per condition (mirrors `isRequirementViolated`'s
`anyMatch` semantics — stop checking further lines for that offence once a mismatch is recorded, to
avoid duplicate `AffectedOffence` entries if an offence ever carries more than one matching
requirement line).

**Date comparison semantics**: violation when `!endDate.isEqual(expected)` — both early and late
mismatches trigger; exact equality is the only valid state (research.md Decision 9). This differs
from the `isAfter`-only semantics of the existing AC2a/b/c checks.

**Period parsing**: `^(\d+)\s*(Days?|Weeks?|Months?)$` (case-insensitive), default unit `DAYS` for a
bare integer with no suffix; calendar-aware `LocalDate.plus(amount, unit)` (research.md Decision 3).
Missing/blank/unparseable start date, period, or end date → skip gracefully (no violation), `WARN`
logged, consistent with the existing `parsePromptDate` defensive pattern.

---

## `PreprocessorHelper` — new shared helper

```java
record ParsedPeriod(long amount, ChronoUnit unit) {}

static ParsedPeriod parsePromptPeriod(ResultLineDto line, String promptRef, String offenceId)
```

Added alongside the existing `parsePromptDate`/`isRequirementViolated` static helpers, following the
same null-safe / first-matching-prompt / WARN-and-return-null pattern.

---

## `ConditionDefinition` — new field (framework, shared by all rules)

| New Field | YAML Key | Purpose |
|-----------|----------|---------|
| `calculatedValueSet` | `calculatedValueSet` | Names which `*CalculatedEndDateByOffenceId` map to consult when resolving `${calculatedEndDate}` (`null` for conditions that don't use it — every existing condition in every rule is unaffected) |

## `RuleEvaluationContext` — new default method (framework)

```java
default String getCalculatedValue(String setName, String offenceId) {
    throw new IllegalArgumentException("Unknown calculated-value set: " + setName);
}
```

Mirrors `getOffenceIdSet` — contexts that don't support computed values (e.g. `DefendantContext`,
`DisqualificationExtendedTestContext`) inherit the throwing default unchanged;
`YouthRehabilitationContext` overrides it.

## `MessageTemplateResolver` — new overload (framework)

```java
public String resolve(String template, String defendantName, List<String> affectedOffenceIds,
                       Map<String, OffenceDto> offenceMap, List<String> allOffenceIds,
                       Map<String, String> extraPlaceholders) {
    String result = resolve(template, defendantName, affectedOffenceIds, offenceMap, allOffenceIds);
    for (Map.Entry<String, String> entry : extraPlaceholders.entrySet()) {
        result = result.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    return result;
}
```

The existing 5-arg `resolve(...)` is unchanged; every current call site (all three existing rules)
is unaffected.

## `CelValidationRule` — wiring (framework)

In the OFFENCE-level (`isDefendantLevel == false`) branch, the per-offence message lambda passes an
extra-placeholder map only when `condition.getCalculatedValueSet() != null`:

```java
final String calculatedValueSet = condition.getCalculatedValueSet();
issueBuilder.affectedOffences(
        offenceDisplayHelper.buildAffectedOffences(
                offenceIdsForTemplate,
                offenceMap,
                id -> calculatedValueSet == null
                        ? messageResolver.resolve(
                                condition.getMessageTemplate(), context.defendantName(),
                                List.of(id), offenceMap, context.allOffenceIds())
                        : messageResolver.resolve(
                                condition.getMessageTemplate(), context.defendantName(),
                                List.of(id), offenceMap, context.allOffenceIds(),
                                calculatedValuePlaceholder(
                                        context.getCalculatedValue(calculatedValueSet, id)))));

private static Map<String, String> calculatedValuePlaceholder(final String calculatedValue) {
    return calculatedValue == null ? Map.of() : Map.of("calculatedEndDate", calculatedValue);
}
```

The `errorMessageTemplate` resolve call (top-of-screen summary) is **not** changed — it has no
per-offence `id` in scope, only the aggregate `offenceIdsForTemplate` list, matching FR-003's message
text which does not include the calculated date (only the inline/per-offence message does, per FR-004).

---

## Rule YAML — conditions appended to `DR-YRO-001.yaml`

```yaml
    - id: "DUR-YRC2"
      name: "Curfew requirement end date does not match calculated duration"
      expression: "curDurationMismatchCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date for the Curfew Requirement does not match the period of the requirement.
        The current recorded period would mean the end date should be ${calculatedEndDate}.
      errorMessageTemplate: >-
        The end date for the Curfew Requirement does not match the period of the requirement.
        This affects ${defendantNames}.
      affectedOffenceSet: "curDurationMismatchOffenceIds"
      calculatedValueSet: "curCalculatedEndDateByOffenceId"
      validationLevel: OFFENCE

    - id: "DUR-YRC1"
      name: "Curfew with electronic monitoring end date of tagging does not match calculated duration"
      expression: "cureDurationMismatchCount > 0"
      severity: ERROR
      messageTemplate: >-
        The end date for the Curfew Requirement does not match the period of the requirement.
        The current recorded period would mean the end date should be ${calculatedEndDate}.
      errorMessageTemplate: >-
        The end date for the Curfew Requirement does not match the period of the requirement.
        This affects ${defendantNames}.
      affectedOffenceSet: "cureDurationMismatchOffenceIds"
      calculatedValueSet: "cureCalculatedEndDateByOffenceId"
      validationLevel: OFFENCE
```

Condition ids follow the `DUR-<shortCode>` convention (mirrors CO's `DUR-CUR`/`DUR-CURE`, substituting
this rule's own YRC2/YRC1 short codes rather than CUR/CURE). No changes to the `preprocessing:` block —
`curfewShortCodes` (YRC2) and `curfewTagShortCodes` (YRC1) are reused as-is; `yroOrderShortCodes` and
`furtherCurfewShortCodes` (YRC3) are untouched.

---

## Response: `ValidationIssue` (emitted per triggered condition — unchanged shape)

Each triggered condition produces one `ValidationIssue` entry in `errors.validationIssues` of
`DraftValidationResponse`, identical field shape to the existing AC2a/b/c issues:

| Field | Populated by |
|-------|-------------|
| `ruleId` | `"DR-YRO-001"` |
| `severity` | `ERROR` (ceiling can lower to `WARNING` at runtime, never promotes) |
| `affectedOffences[].message` | Expanded `messageTemplate`, including `${calculatedEndDate}` for `DUR-YRC2`/`DUR-YRC1` |
| `errorMessages[]` | Expanded `errorMessageTemplate` — "This affects ${defendantNames}" |

No new fields on `ValidationIssue`; this is purely additive within the existing rule/condition model.

---

## Relationships

```
DraftValidationRequest
  ├── resultLines: List<ResultLineDto>
  │     └── prompts: List<Prompt>
  │           ├── endDate / endDateOfTagging ──────────── existing AC2 comparison
  │           └── startDate / curfewPeriod /
  │               startDateOfTagging / curfewAndElectronicMonitoringPeriod ── new duration comparison
  └── defendants: List<DefendantDto> ────── display name + masterDefendantId dedupe

YouthRehabilitationPreprocessor
  ├── reads: DraftValidationRequest (resultLines + defendants)
  ├── config: PreprocessingDefinition (unchanged — reuses curfewShortCodes / curfewTagShortCodes)
  └── produces: Map<groupKey, YouthRehabilitationContext>

YouthRehabilitationContext  (one per defendant-group with ≥1 YRO)
  ├── 3 existing violation counts + 2 new duration-mismatch counts  ──→ CEL variable map
  ├── 3 existing + 2 new offence-id sets ──→ getOffenceIdSet(name) per YAML condition
  ├── 2 new calculated-end-date maps ──→ getCalculatedValue(setName, offenceId)
  └── allOffenceIds ──→ message template resolver ordering

DR-YRO-001.yaml
  └── 3 existing + 2 new conditions → CelValidationRule → ValidationIssue (ERROR) per triggered condition
```
