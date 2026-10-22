# Data Model: Restraining Order – Multiple Protected Persons Warning (CRA-260)

**Branch**: `CRA-260-restrao-multiple-protected-persons`
**Date**: 2026-09-20

## New Entities

### RestrainingOrderContext (Java record)

```
Package: uk.gov.hmcts.cp.services.rules.cel

record RestrainingOrderContext(
    String       offenceId,            // map key; the offence this context covers
    long         multiplePersonsCount, // 1 = at least one RESTRAO on offence has multiple persons; 0 = none
    List<String> breachingOffenceIds,  // singleton [offenceId] when count == 1; empty list otherwise
    List<String> allOffenceIds         // singleton [offenceId] always
) implements RuleEvaluationContext
```

CEL context exposed via `toCelContext()`:
```
{ "multiplePersonsCount": Long }
```

Named offence sets:
```
"breachingOffenceIds" → breachingOffenceIds
"allOffenceIds"       → allOffenceIds
```

`defendantName()` returns `null` — offence-level context, no defendant association.

---

### RestrainingOrderMultiplePersonsPreprocessor (Java class)

```
Package   : uk.gov.hmcts.cp.services.rules.cel
Stereotype: @Component
Interface : ValidationPreprocessor
Qualifier : "restrao-multiple-protected-persons"

Constants:
  static final String QUALIFIER                    = "restrao-multiple-protected-persons"
  static final String PROMPT_PROTECTED_PERSON_NAME = "protectedPersonsName"
  // Matches "and" only when flanked by word groups on both sides (e.g. "John and Jane").
  // Leading/trailing "and" (e.g. "and Smith", "John and") does NOT match.
  static final Pattern PATTERN_AND                 = Pattern.compile("(?i)\\w+\\s+and\\s+\\w+")

Method: type() → "restrao-multiple-protected-persons"

Method: preprocess(DraftValidationRequest request, PreprocessingDefinition config)
        → Map<String, RestrainingOrderContext>

  Input:
    request  — DraftValidationRequest with offences and resultLines
    config   — PreprocessingDefinition; config.filterShortCodes() == ["RESTRAO"]

  Algorithm:
    upperSet        = PreprocessorHelper.upperSet(config.filterShortCodes())  // {"RESTRAO"}
    resultsByOffence = PreprocessorHelper.groupResultsByOffence(request)

    result = new LinkedHashMap<>()

    for each (offenceId, lines) in resultsByOffence
      where any line has shortCode in upperSet:
        restraoLines  = filter lines where shortCode in upperSet
        breach        = restraoLines.stream().anyMatch(line → isMultiplePersons(line))
        count         = breach ? 1L : 0L
        breachingList = breach ? List.of(offenceId) : List.of()
        allList       = List.of(offenceId)
        result.put(offenceId, new RestrainingOrderContext(offenceId, count, breachingList, allList))

    return result

  Helper: isMultiplePersons(ResultLineDto line):
    name = findPromptValue(line, PROMPT_PROTECTED_PERSON_NAME)
    if (name == null || name.isBlank()) return false
    // Separator characters: any occurrence triggers.
    // "and": only triggers when a word group appears on both sides (PATTERN_AND).
    //   "John Smith and Jane Smith" → triggers
    //   "and Smith" / "John and"   → does NOT trigger (no flanking word group)
    return name.contains("&")
        || name.contains(",")
        || name.contains("/")
        || PATTERN_AND.matcher(name).find()

  Helper: findPromptValue(ResultLineDto line, String promptRef):
    if (line.getPrompts() == null) return null
    return line.getPrompts().stream()
        .filter(p → promptRef.equals(p.getPromptRef()))
        .map(Prompt::getPromptValue)
        .filter(v → v != null && !v.isBlank())
        .findFirst()
        .orElse(null)
```

---

## New YAML Rule: DR-RESTRAO-010.yaml

```
Location: src/main/resources/rules/DR-RESTRAO-010.yaml

rule:
  id: "DR-RESTRAO-010"
  title: "Restraining Order – Multiple Protected Persons Warning"
  description: >-
    Warns when the "Protected person's name" field on a RESTRAO result
    appears to contain more than one person's details, detected by
    separator characters (&, comma, /) or the word "and" appearing between two word groups.
  priority: 10000
  enabled: true
  preprocessing:
    type: "restrao-multiple-protected-persons"
    filterShortCodes:
      - RESTRAO
  conditions:
    - id: "AC1"
      name: "Multiple protected persons in restraining order"
      expression: "multiplePersonsCount > 0"
      severity: WARNING
      messageTemplate: >-
        A restraining order result can only include one protected person's
        details. Add a separate restraining order result for each protected
        person.
      affectedOffenceSet: "breachingOffenceIds"
```

---

## Existing Entities (unchanged)

| Entity | Change |
|--------|--------|
| `ResultLineDto` | No change — `protectedPersonName` is in `prompts` list |
| `Prompt` | No change — used read-only by new preprocessor |
| `PreprocessingDefinition` | No change — `filterShortCodes` field already exists |
| `PreprocessorRegistry` | No change — autowires new `@Component` automatically |
| `CelValidationRule` | No change |
| `validation_rule` table | No migration — DR-RESTRAO-010 gets a row only if a runtime override is needed |

---

## Entity Relationships

```
DraftValidationRequest
  └── resultLines: List<ResultLineDto>
        └── ResultLineDto [shortCode="RESTRAO"]
              └── prompts: List<Prompt>
                    └── Prompt [promptRef="protectedPersonsName"]
                          └── promptValue: "John Smith & Jane Smith"  // separator character → triggers
                          or: "John Smith and Jane Smith" // "and" between two words → triggers
                          or: "John and" / "and Smith"   // leading/trailing "and" → no trigger
                                              ↓
                              RestrainingOrderMultiplePersonsPreprocessor
                                              ↓
                              RestrainingOrderContext [multiplePersonsCount=1]
                                              ↓
                              CEL: multiplePersonsCount > 0 → true
                                              ↓
                              ValidationIssue [severity=WARNING, level=OFFENCE]
                              "A restraining order result can only include one
                               protected person's details. Add a separate
                               restraining order result for each protected person."
```

---

## Open Dependencies

| # | Item | Owner | Blocker? |
|---|------|-------|----------|
| 1 | ~~Confirm `promptRef` key for RESTRAO protected person's name~~ | Resolved — `"protectedPersonsName"` confirmed from `cpp-apitests` fixture `hearing.save-draft-for-RESTRAO.json` | No longer a blocker |
