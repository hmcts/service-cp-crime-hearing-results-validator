# Implementation Plan: Restraining Order – Multiple Protected Persons Warning

**Branch**: `CRA-260-restrao-multiple-protected-persons` | **Date**: 2026-09-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/010-restrao-multiple-protected-persons/spec.md`

## Summary

Add validation rule **DR-RESTRAO-010** that inspects every RESTRAO (Restraining Order) result line in the
`DraftValidationRequest`. For each RESTRAO line, the rule reads the "Protected person's name" value from the
result's `prompts` list (via a designated `promptRef` key) and checks whether it contains a trigger signal —
the characters `&`, `,`, or `/`, or the word `and` as a whole word (case-insensitive). If any RESTRAO on an
offence breaches this rule, an offence-level WARNING is raised against that offence. The warning is advisory
and does not block sharing.

The implementation follows the established YAML+CEL rule engine pattern: a new YAML file defines the rule
contract; a new `ValidationPreprocessor` component (`RestrainingOrderMultiplePersonsPreprocessor`) reads the
prompts, applies the trigger logic, and exposes a `multiplePersonsCount` variable to CEL; the existing
`CelValidationRule` / `PreprocessorRegistry` / `CelExpressionEvaluator` infrastructure routes everything
automatically with no changes to shared engine code.

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), Lombok, external DTO library `uk.gov.hmcts.cp:api-cp-crime-hearing-results-validator:0.2.10-cra-22`
**Storage**: PostgreSQL (TestContainers for IT) — no schema changes; the `validation_rule` table already supports runtime overrides for any rule ID
**Testing**: JUnit 5 + Mockito + AssertJ (unit); IntegrationTestBase / WireMock / TestContainers (IT); `gradle api` live API tests
**Target Platform**: Azure Kubernetes (Spring Boot, port 4550)
**Project Type**: Web service
**Performance Goals**: Sub-50 ms p95 for a single hearing; the preprocessor is O(N) over result lines; regex compilation is once-per-JVM via a `static final Pattern`
**Constraints**: Checkstyle Google zero-warnings; PMD `ignoreFailures = false`; no wildcard imports; SLF4J only
**Scale/Scope**: One new YAML rule file, one new `ValidationPreprocessor` bean, one new context record, one new unit test class, one new integration test class

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I — YAML/CEL Rule-First | ✅ PASS | DR-RESTRAO-010.yaml is the contract. Java code is needed only for the new preprocessor (no existing preprocessor handles RESTRAO name inspection); this gap is filled in the same change per Principle I's footnote: "Adding a new rule MUST be possible without writing Java code — if it is not, the preprocessor or context model has a gap that MUST be fixed in the same change." |
| II — Constructor Injection & Immutable DTOs | ✅ PASS | New preprocessor uses no DI fields; context is a Java record |
| III — Layered Architecture & Preprocessor Dispatch | ✅ PASS | New `@Component` preprocessor registers via `PreprocessorRegistry`; `CelValidationRule` dispatches via `preprocessing.type = "restrao-multiple-protected-persons"` |
| IV — Spec-Driven Build Loop | ✅ PASS | spec → plan → tasks → implement → code-reviewer → qa → spec-validator |
| V — HMCTS Standards Compliance | ✅ PASS | Java 25, Spring Boot 4, Gradle, SLF4J |
| VI — Severity Ceiling, Never Promote | ✅ PASS | YAML severity = WARNING; runtime override can only lower it |
| VII — No System.out | ✅ PASS | No `System.out`/`System.err` anywhere |
| VIII — TDD | ✅ PASS | Failing tests written first per plan task ordering |

**Complexity Tracking**: None — no violations.

## Project Structure

### Documentation (this feature)

```text
specs/010-restrao-multiple-protected-persons/
├── plan.md              # This file
├── research.md          # Phase 0 output (below)
├── data-model.md        # Phase 1 output (below)
├── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
└── checklists/
    └── requirements.md  # Quality checklist (already created by /speckit-specify)
```

### Source Code (repository root)

```text
src/main/resources/rules/
└── DR-RESTRAO-010.yaml              # NEW — rule contract

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── RestrainingOrderMultiplePersonsPreprocessor.java  # NEW
└── RestrainingOrderContext.java                      # NEW

src/test/java/uk/gov/hmcts/cp/services/rules/cel/
└── RestrainingOrderMultiplePersonsPreprocessorTest.java  # NEW (unit)

src/test/java/uk/gov/hmcts/cp/integration/
└── RestrainingOrderMultiplePersonsIntegrationTest.java   # NEW (IT)

src/apiTest/java/uk/gov/hmcts/cp/http/
└── (no new API test class — framework-level PATCH path covered by ValidationRulesApiHttpLiveTest)
```

**Structure Decision**: Single-project standard layout (Option 1). No new packages needed; all new classes join the existing `uk.gov.hmcts.cp.services.rules.cel` preprocessor/context package.

---

## Phase 0: Research

*All NEEDS CLARIFICATION items resolved below.*

### research.md

---

#### R-01: ResultLineDto field for protected person's name

**Decision**: The `protectedPersonName` field is **not** a direct field on `ResultLineDto`. It is stored in the `prompts` list as a `Prompt` with a `promptRef` key and a free-text `promptValue`. The preprocessor must call `line.getPrompts()` and find the prompt whose `promptRef` matches the designated key.

**Prompt ref key (OPEN DEPENDENCY)**: The exact `promptRef` value used by the CPA UI for the RESTRAO "Protected person's name" field is **not defined in this codebase** in any existing test fixture, YAML rule, or preprocessor. It must be confirmed with the CPA/upstream API team before implementation.

**Resolved**: the key is `"protectedPersonsName"`. Confirmed from the `cpp-apitests` fixture `hearing.save-draft-for-RESTRAO.json` — the `resultPrompts` array on a RESTRAO result line includes `{"promptRef": "protectedPersonsName", "label": "Protected person's name", ...}`. The constant in the preprocessor is:
```java
static final String PROMPT_PROTECTED_PERSON_NAME = "protectedPersonsName";
```

**Rationale**: All prompt refs in existing preprocessors are hardcoded constants (`"CTLDATE"`, `"endDate"`, `"endDateOfTagging"`). The same approach applies here. This is no longer an open dependency.

---

#### R-02: Trigger logic — characters and whole-word "and"

**Decision**: Trigger logic is implemented in the Java preprocessor (not in CEL). The preprocessor exposes a `multiplePersonsCount` (0 or 1 per offence) to CEL.

**Trigger conditions** (any one is sufficient):
| Signal | Check | Implementation |
|--------|-------|----------------|
| `&` | `value.contains("&")` | String `contains` — O(N) |
| `,` (comma) | `value.contains(",")` | String `contains` — O(N) |
| `/` | `value.contains("/")` | String `contains` — O(N) |
| `and` between two words | `PATTERN_AND.matcher(value).find()` | `static final Pattern PATTERN_AND = Pattern.compile("(?i)\\w+\\s+and\\s+\\w+")` |

**Rationale**: CEL string operations cannot do word-boundary or contextual regex matching. Keeping the logic in Java makes it unit-testable in isolation. The compiled `Pattern` is a `static final` constant, avoiding repeated compilation. `\w+\s+and\s+\w+` is preferred over `\band\b` because it only fires when "and" sits between two word groups — trailing or leading "and" (e.g. `"and Smith"`, `"John and"`) is not a meaningful multi-person signal.

**Alternatives considered**:
- Delegating all checks to CEL — rejected; CEL does not support contextual regex
- Using `\band\b` — rejected; fires on leading/trailing "and" which are not genuine separator patterns
- Configuring trigger chars/words in YAML (new `PreprocessingDefinition` fields) — rejected; these are spec-defined fixed signals not expected to change; hardcoding keeps the change self-contained

---

#### R-03: Context granularity — per-offence

**Decision**: One `RestrainingOrderContext` is produced per offence that has at least one RESTRAO result line. Offences with no RESTRAO result are not included in the context map (so the CEL never evaluates against them).

**Rationale**: Offence-level granularity matches how the `CelValidationRule.evaluate()` loop exposes `affectedOffences` in the response — the warning is associated with the offence, not the individual result line. This matches AC2–AC5 ("below the offence and above the RESTRAO result") and AC7 (per-offence independence). If multiple RESTRAO lines exist on one offence and any one breaches, `multiplePersonsCount = 1`; if none breach, `multiplePersonsCount = 0` and no warning fires.

**Alternatives considered**:
- Per-result-line contexts — not supported by the engine architecture; `CelValidationRule` produces offence-level or defendant-level issues only
- Per-defendant contexts — not applicable; the RESTRAO name check is per-result, not per-defendant

---

#### R-04: YAML preprocessing config — filterShortCodes

**Decision**: Use `preprocessing.filterShortCodes: ["RESTRAO"]` in the YAML to declare which short codes the preprocessor handles. The preprocessor reads this via `PreprocessorHelper.upperSet(config.filterShortCodes())` for filtering.

**Rationale**: Follows the existing convention (`DisqualificationExtendedTestPreprocessor` uses `config.extendedTestShortCodes()`; `CtlMissingPreprocessor` uses `config.ctlShortCodes()`, etc.). Keeps the short code declarative in YAML rather than hardcoded in Java.

Note: `filterShortCodes` is already a field in `PreprocessingDefinition` — no schema change needed.

---

#### R-05: No changes to shared engine code

**Decision**: `CelValidationRule`, `PreprocessorRegistry`, `CelExpressionEvaluator`, `MessageTemplateResolver`, `OffenceDisplayHelper`, and `PreprocessingDefinition` are **unchanged**. The new preprocessor and context plug in via the existing extension points.

**Rationale**: The engine is designed for extension without modification (Constitution Principle III). All seven existing preprocessors follow this pattern.

---

## Phase 1: Design & Contracts

### data-model.md

---

#### New YAML Rule: DR-RESTRAO-010.yaml

```yaml
rule:
  id: "DR-RESTRAO-010"
  title: "Restraining Order – Multiple Protected Persons Warning"
  description: >-
    Warns when the "Protected person's name" field on a RESTRAO result line
    appears to contain more than one person's details, detected by separator
    characters (&, comma, /) or the word "and" as a whole word
    (case-insensitive).
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
        A restraining order result can only include one protected person's details.
        Add a separate restraining order result for each protected person.
      affectedOffenceSet: "breachingOffenceIds"
```

Notes:
- `priority: 10000` — higher number = lower priority; placed after all current rules (highest existing is DR-COEW-005 at ~8000)
- No `affectedDefendantSet` — offence-level warning only
- No `calculatedValueSet` — message has no computed placeholders
- No `errorMessageTemplate` — WARNING only, never ERROR

---

#### New Preprocessor: RestrainingOrderMultiplePersonsPreprocessor

```
Package : uk.gov.hmcts.cp.services.rules.cel
Type    : @Component implementing ValidationPreprocessor
Qualifier (type()): "restrao-multiple-protected-persons"

preprocess(DraftValidationRequest, PreprocessingDefinition)
  → Map<String, RestrainingOrderContext>   (keyed by offenceId)

Algorithm:
  1. upperSet = PreprocessorHelper.upperSet(config.filterShortCodes())
     → {"RESTRAO"}
  2. resultsByOffence = PreprocessorHelper.groupResultsByOffence(request)
  3. For each offenceId → resultLines entry where any line has shortCode in upperSet:
     a. Filter lines for RESTRAO short code
     b. For each RESTRAO line, extract name =
          findPromptValue(line, PROMPT_PROTECTED_PERSON_NAME)
          (null/blank → skip; not a trigger)
     c. isBreach = (name != null) && hasMultiplePersonsSignal(name)
     d. If any RESTRAO line on this offence is a breach → multiplePersonsCount = 1
     e. Build RestrainingOrderContext(offenceId, count, breachingList, allList)
  4. Return populated map (only offences with ≥1 RESTRAO result)

hasMultiplePersonsSignal(String name):
  return name.contains("&")
      || name.contains(",")
      || name.contains("/")
      || PATTERN_AND.matcher(name).find()

PATTERN_AND = Pattern.compile("(?i)\\w+\\s+and\\s+\\w+")  // static final, compiled once; requires word on both sides

PROMPT_PROTECTED_PERSON_NAME = "protectedPersonsName"  // static final — confirmed from cpp-apitests fixture
```

---

#### New Context Record: RestrainingOrderContext

```
Package : uk.gov.hmcts.cp.services.rules.cel
Type    : Java record implementing RuleEvaluationContext

Fields:
  String       offenceId                — the offence this context covers
  long         multiplePersonsCount     — 1 if any RESTRAO on the offence breaches; 0 otherwise
  List<String> breachingOffenceIds      — [offenceId] if count == 1, else []
  List<String> allOffenceIds            — [offenceId] always

toCelContext() → Map<String, Long>:
  {"multiplePersonsCount": multiplePersonsCount}

getOffenceIdSet(String setName):
  "breachingOffenceIds" → breachingOffenceIds
  "allOffenceIds"       → allOffenceIds
  default               → throw IllegalArgumentException

defendantName() → null   (offence-level context; no defendant association)
```

---

#### Integration Points (unchanged engine components)

| Component | Role | Change |
|-----------|------|--------|
| `ValidationRuleAutoConfiguration` | Discovers `DR-RESTRAO-010.yaml` at startup | None |
| `PreprocessorRegistry` | Registers `RestrainingOrderMultiplePersonsPreprocessor` by qualifier | None |
| `CelValidationRule` | Routes to new preprocessor via `preprocessing.type`; evaluates `multiplePersonsCount > 0` | None |
| `CelExpressionEvaluator` | Compiles and caches the CEL expression | None |
| `MessageTemplateResolver` | Resolves message (no placeholders — static text) | None |
| `OffenceDisplayHelper` | Builds `affectedOffences` from `breachingOffenceIds` | None |
| `RuleOverrideService` | Applies DB severity ceiling to DR-RESTRAO-010 | None |
| `validation_rule` table | Accepts a row for DR-RESTRAO-010 if runtime override needed | No migration needed (optional row) |

---

### contracts/

This service does not own an OpenAPI spec (DTOs come from the upstream `api-cp-crime-hearing-results-validator` dependency). No contract files to generate.

The existing `POST /validate` endpoint signature is unchanged. The new rule simply contributes additional `ValidationIssue` entries (severity=WARNING, validationLevel=OFFENCE) to the existing response shape.

---

### Agent context update

Update the plan reference in `CLAUDE.md` to point to this plan.

---

## Post-Phase-1 Constitution Re-check

All eight principles remain ✅ PASS. No new violations introduced. No Complexity Tracking entries required.
