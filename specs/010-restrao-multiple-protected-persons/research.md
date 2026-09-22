# Research: Restraining Order – Multiple Protected Persons Warning (CRA-260)

**Branch**: `CRA-260-restrao-multiple-protected-persons`
**Date**: 2026-09-20

## R-01: ResultLineDto — no dedicated `protectedPersonName` field

**Finding**: `ResultLineDto` (from `uk.gov.hmcts.cp:api-cp-crime-hearing-results-validator:0.2.10-cra-22`) has
nine fields: `resultLineId`, `shortCode`, `label`, `defendantId`, `offenceId`, `isConcurrent`,
`consecutiveToOffence`, `category`, and `prompts: List<Prompt>`.

There is **no** `protectedPersonName` field. The protected person's name is carried as a `Prompt` entry inside
the `prompts` list: `{ promptRef: "<key>", promptValue: "<name text>" }`.

**Resolved**: The exact `promptRef` key is `"protectedPersonsName"` — confirmed from
`cpp-apitests/api-integration-test/src/test/resources/draftresults/hearing/hearing.save-draft-for-RESTRAO.json`,
which contains `{"promptRef": "protectedPersonsName", "label": "Protected person's name", ...}`.
No CPA team consultation required.

**Implementation approach**: Add a `static final String PROMPT_PROTECTED_PERSON_NAME = "protectedPersonsName"` constant to
the preprocessor. To extract the value:
```
findPromptValue(line, PROMPT_PROTECTED_PERSON_NAME)
  → iterate line.getPrompts(); find first where promptRef.equals(key); return promptValue
  → return null if no matching prompt or value is blank
```

This follows the same pattern as `CtlMissingPreprocessor.PROMPT_CTL_DATE = "CTLDATE"`.

---

## R-02: Trigger logic — pattern matching in Java, not CEL

**Finding**: CEL (via `org.projectnessie.cel`) does not support word-boundary regex (`\b`). The whole-word
"and" match therefore cannot be implemented in a CEL expression. It must be done in the Java preprocessor,
which then exposes a boolean/count to CEL.

**Decision**: All four trigger signals are checked in Java:

| Signal | Implementation |
|--------|----------------|
| `&` | `value.contains("&")` |
| `,` | `value.contains(",")` |
| `/` | `value.contains("/")` |
| `and` (between two words, case-insensitive) | `Pattern.compile("(?i)\\w+\\s+and\\s+\\w+").matcher(value).find()` |

The `Pattern` is compiled once as a `static final` constant to avoid per-call recompilation.

**Validation of AC6**: The regex `(?i)\w+\s+and\s+\w+` correctly handles:
- `"John Smith and Jane Smith"` → match (triggers warning) ✅
- `"John Smith AND Jane Smith"` → match via `(?i)` flag ✅
- `"Alexandra Sanderson"` → no match ("and" is within "Sander**son**") ✅
- `"Amanda Anderson"` → no match ("and" is within "Am**and**a" and "And**erson**") ✅
- `"and Smith"` → no match (no word before "and") ✅
- `"John and"` → no match (no word after "and") ✅
- `"Alexandra Sanderson And"` → no match (trailing "And" has no word after it) ✅

**Why not `\band\b`**: `\band\b` matches "and" at the start or end of the field (e.g. `"and Smith"`, `"John and"`), which are not meaningful multi-person patterns. The `\w+\s+and\s+\w+` pattern is more precise — it only fires when "and" sits between two word groups, matching genuine "Name1 and Name2" constructions.

---

## R-03: Context granularity — per offence

**Finding**: `CelValidationRule.evaluate()` iterates the preprocessor's returned map and produces either
offence-level or defendant-level `ValidationIssue` objects. Per-result-line granularity is not supported by
the engine.

**Decision**: One `RestrainingOrderContext` per offence (keyed by `offenceId`). Only offences that have at
least one RESTRAO result line are included in the map; all others are excluded (no wasted CEL evaluations).

If multiple RESTRAO lines exist on one offence:
- If **any** line has a breaching name → `multiplePersonsCount = 1`
- If **none** breach → `multiplePersonsCount = 0` (context included but CEL evaluates to false, no warning)

This is correct for AC7: warnings appear only against offences with at least one breaching RESTRAO result.

---

## R-04: YAML preprocessing config

**Finding**: `PreprocessingDefinition` already has a `filterShortCodes: List<String>` field. The preprocessor
can use `PreprocessorHelper.upperSet(config.filterShortCodes())` to obtain the set `{"RESTRAO"}`.

**Decision**: Set `preprocessing.filterShortCodes: [RESTRAO]` in the YAML rule. The preprocessor uses this
to filter result lines — consistent with the pattern in `DisqualificationExtendedTestPreprocessor` which reads
`config.extendedTestShortCodes()`.

No new fields needed in `PreprocessingDefinition`.

---

## R-05: Engine code — no changes required

**Finding**: All required extension points already exist:
- `ValidationRuleAutoConfiguration` discovers YAML files at startup by classpath scan — adding a new YAML
  requires no Java change
- `PreprocessorRegistry` autowires all `ValidationPreprocessor` beans — adding a new `@Component` requires no
  registry change
- `CelValidationRule.evaluate()` handles offence-level warnings via `affectedOffenceSet` — no change needed
- `RuleOverrideService` and `SeverityCeiling` work for any rule ID — no change needed

---

## Summary

| Item | Decision | Confidence |
|------|----------|------------|
| Protected person's name location | `prompts` list, `promptRef = "protectedPersonsName"` (confirmed) | High |
| Trigger logic location | Java preprocessor (not CEL) | High |
| "and" between two words | `(?i)\w+\s+and\s+\w+` regex, compiled once | High |
| Context granularity | Per offence | High |
| YAML config for short code | `filterShortCodes: [RESTRAO]` | High |
| Engine changes | None | High |
