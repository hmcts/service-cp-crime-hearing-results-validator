# Implementation Plan: Community Order End Date Validation

**Branch**: `DD-41653-community-order-date-validation` | **Date**: 2026-05-20  
**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md) | **Data model**: [data-model.md](data-model.md)

**Extended**: 2026-07-06 (Jira `DD-41655`, branch `DD-41655-requirement-duration-validation`,
based on `team/DD-41653`) — see [Extension (DD-41655)](#extension-2026-07-06-jira-dd-41655) below
for the plan covering User Stories 4–7 (requirement duration end date validation). The content
above this marker describes the already-shipped User Story 1–3 work and is left unchanged for
traceability.

---

## Summary

Implement the `DR-COEW-001` validation rule that detects community order date errors at "Save and continue":

- **AC2** — A community order's end date is earlier than the end date of any attached requirement (CUR, CURE, CURA, or AAR).

The implementation follows the established YAML+CEL rule engine pattern: a new `DR-COEW-001.yaml` rule file, a new `CommunityOrderEndDatePreprocessor` Spring component, a new `CommunityOrderContext` record, and minor extensions to `PreprocessingDefinition`. AC1 ("end date must be in the future") is **out of scope** — handled by a separate ticket.

---

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), `api-cp-crime-hearing-results-validator:0.1.6` (provides `DraftValidationRequest`, `ResultLineDto`, `Prompt`), Lombok, Jackson (YAML deserialisation)  
**Storage**: PostgreSQL — existing `validation_rule` table for runtime severity overrides; no new tables or migrations  
**Testing**: JUnit 5 + Mockito + AssertJ (unit), MockMvc + TestContainers + WireMock (integration)  
**Target Platform**: Azure-hosted Spring Boot service, local port 4550  
**Project Type**: Web service — stateless validation API  
**Performance Goals**: No change to existing Gatling assertion thresholds; rule evaluation is per-request and stateless  
**Constraints**: Checkstyle Google (`maxWarnings = 0`), PMD (`ignoreFailures = false`), no wildcard imports  
**Scale/Scope**: One new YAML rule, one new preprocessor, one new context record, five new fields on an existing class

---

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| **I — YAML/CEL Rule-First** | ✅ PASS | `DR-COEW-001.yaml` is authored before any Java change; all conditions and short-code lists live in YAML |
| **II — Constructor Injection & Immutable DTOs** | ✅ PASS | `CommunityOrderContext` is a Java record; `CommunityOrderEndDatePreprocessor` uses `@Component` with no field injection |
| **III — Layered Architecture & Preprocessor Dispatch** | ✅ PASS | New preprocessor plugs in via `PreprocessorRegistry` using the `type()` qualifier; zero changes to `CelValidationRule` |
| **IV — Spec-Driven Build Loop** | ✅ PASS | This plan document; tasks.md and implement follow; `code-reviewer`, `qa`, `spec-validator` gate before ship |
| **V — HMCTS Standards** | ✅ PASS | Java 25, Spring Boot 4, Gradle, SLF4J logging only |
| **VI — Severity Ceiling** | ✅ PASS | All four conditions set `severity: ERROR`; ceiling can lower to WARNING at runtime but never promotes |
| **VII — No System.out** | ✅ PASS | All diagnostic output via SLF4J (`@Slf4j`) |
| **VIII — TDD** | ✅ PASS | Plan mandates failing tests authored before production code for every behaviour |

No violations. Complexity Tracking section not required.

---

## Project Structure

### Documentation (this feature)

```text
specs/003-community-order-date-validation/
├── spec.md          ✅ complete
├── research.md      ✅ complete (this phase)
├── data-model.md    ✅ complete (this phase)
├── plan.md          ✅ this file
└── tasks.md         🔲 next — /speckit-tasks
```

### Source Code Changes

```text
src/main/resources/rules/
└── DR-COEW-001.yaml                                           [NEW]

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── CelValidationRule.java                                     [MODIFY — populate affectedDefendants]
├── PreprocessingDefinition.java                               [MODIFY — 6 new fields]
├── CommunityOrderEndDatePreprocessor.java                     [NEW]
└── CommunityOrderContext.java                                 [NEW]

src/test/java/uk/gov/hmcts/cp/
├── services/rules/cel/
│   ├── CommunityOrderEndDatePreprocessorTest.java             [NEW — unit]
│   └── CommunityOrderContextTest.java                        [NEW — unit]
├── integration/
│   ├── CommunityOrderEndDateRuleIntegrationTest.java          [NEW — integration]
│   ├── ValidationControllerIntegrationTest.java              [MODIFY — rule count 3→4]
│   ├── ValidationRulesControllerIntegrationTest.java         [MODIFY — rule count 3→4]
│   └── CrossRuleRegressionIntegrationTest.java               [MODIFY — rule count 3→4]
└── config/
    └── ValidationRuleAutoConfigurationTest.java               [MODIFY — +1 preprocessor, rule count 3→4]
```

**No changes to**: `ValidationPreprocessor` interface, `PreprocessorRegistry`, `RuleEvaluationContext` interface, `MessageTemplateResolver`, `DefaultValidationService`, `ValidationController`, Liquibase migrations.

> **`CelValidationRule` framework change** (applies to all rules): The iteration over preprocessor contexts was changed from `contexts.values()` to `contexts.entrySet()` so that `defendantId` is captured alongside each context. Each emitted `ValidationIssue` now includes `affectedDefendants: [{ defendantId }]` — a list containing the single defendant whose context triggered the condition. The UI uses this to look up the defendant's display name and render the "This affects: ..." line. This change is backward-compatible: `affectedDefendants` was always a defined field on `ValidationIssue` (from the upstream API library) — it was previously unpopulated (null/absent) and is now consistently set for every rule.

---

## Phase 0: Research (complete)

All unknowns resolved. See [research.md](research.md) for full decisions.

Key resolved items:
- `List<Prompt> prompts` confirmed on `ResultLineDto` in v0.1.6; `Prompt` has `getPromptRef()` / `getPromptValue()`
- Prompt ref keys hardcoded: `endDate`, `endDateOfTag`, `until`
- Multiple violations: separate condition per requirement type (one `ValidationIssue` each)
- Share button: hearing-level (hidden if any defendant has errors)
- AC1: out of scope

---

## Phase 1: Design & Contracts

### 1a. Data model

See [data-model.md](data-model.md) for full entity definitions, CEL variable map, and named offence-id sets.

**Summary of new/changed types:**

| Type | Change | Location |
|------|--------|----------|
| `PreprocessingDefinition` | +5 `List<String>` fields | `cel/PreprocessingDefinition.java` |
| `CommunityOrderContext` | NEW record, implements `RuleEvaluationContext` | `cel/CommunityOrderContext.java` |
| `CommunityOrderEndDatePreprocessor` | NEW `@Component`, type `"community-order-end-date"` | `cel/CommunityOrderEndDatePreprocessor.java` |
| `DR-COEW-001.yaml` | NEW rule, priority 4000, 4 conditions | `resources/rules/DR-COEW-001.yaml` |

### 1b. Interface contracts

No new API surface. The existing `POST /api/validation/validate` endpoint is unchanged. New `ValidationIssue` entries of type ERROR will appear in the `errors` list of `DraftValidationResponse` when AC2 violations are detected — this is an additive, backward-compatible change.

Each emitted `ValidationIssue` now carries:
- `affectedOffences` — offences scoped to the specific violation (existing field, set by all rules)
- `affectedDefendants` — `[{ defendantId: "<id>" }]` — the defendant whose context triggered this issue (field was already defined in the API schema; this feature begins populating it via a framework change to `CelValidationRule`)

The UI resolves the defendant display name from `affectedDefendants[].defendantId` and renders "This affects: <<name>>" in the error summary.

### 1c. Agent context

CLAUDE.md updated to point to this plan (see below).

---

## Implementation Sequence (for /speckit-tasks)

The tasks must be ordered to enforce TDD (Constitution Principle VIII): test authorship must precede or be paired with production code for every behaviour.

### Step 1 — YAML Rule First (Constitution Principle I)

Author `DR-COEW-001.yaml` with all 5 conditions before writing any Java. This is the contract the Java must satisfy.

### Step 2 — Extend `PreprocessingDefinition`

Add the 5 new `List<String>` fields. This is a pure data change; existing tests must still pass.

### Step 3 — `CommunityOrderContext` record (test-first)

1. Write `CommunityOrderContextTest` — assert `toCelContext()` returns correct map and `getOffenceIdSet()` dispatches correctly.
2. Write `CommunityOrderContext.java` to pass.

### Step 4 — `CommunityOrderEndDatePreprocessor` (test-first)

1. Write `CommunityOrderEndDatePreprocessorTest` covering:
   - AC2a: CUR end date after order end date → `curViolationCount = 1`
   - AC2b: CURE end date of tag after order → `cureViolationCount = 1`
   - AC2c: CURA end date after order → `curaViolationCount = 1`
   - AC2d: AAR until date after order → `aarViolationCount = 1`
   - Boundary: requirement date equal to order date → no violation (AC2)
   - Multiple defendants: only affected defendant has counts > 0
   - Multiple violations on same order: each count incremented independently
   - Missing/null `promptValue`: skip gracefully, no violation
2. Write `CommunityOrderEndDatePreprocessor.java` to pass all tests.

### Step 5 — Integration test (test-first, end-to-end)

1. Write `CommunityOrderEndDateRuleIntegrationTest` extending `IntegrationTestBase`:
   - Scenarios 6–13 from the spec (AC2)
   - Verify rule ID `DR-COEW-001` appears in `rulesEvaluated`
   - Verify error messages contain exact requirement display names
   - Verify "This affects" defendant list correctness
   - Verify no errors for valid community orders
   - Verify share button suppression (errors in response)
2. Run integration tests to confirm all pass.

### Step 6 — Update `ValidationRuleAutoConfigurationTest`

- Add `CommunityOrderEndDatePreprocessor` to the `PreprocessorRegistry` in the test
- Update `should_create_one_rule_per_yaml_file` assertion: `hasSize(2)`, `containsExactlyInAnyOrder("DR-SENT-002", "DR-COEW-001")`

### Step 7 — Quality gates

Run in order:
```bash
gradle test                    # all unit + integration tests green
gradle checkstyleMain          # zero warnings
gradle pmdMain                 # no violations
gradle build                   # full build clean
```

---

## Complexity Tracking

*(No constitution violations — section left empty per template instructions)*

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `promptRef` key names differ from assumed values (`endDate`, `endDateOfTag`, `until`) | Low | High | Verify against a real request payload in dev/test environment early; unit tests will fail fast if keys don't match |
| `promptValue` not present for all result lines in test data | Medium | Low | Preprocessor skips null/blank values and logs WARN; integration tests use explicit test data |
| Community order and requirement result lines not co-located on same offence | Low | High | Confirmed by architecture: all result lines share `offenceId`; integration tests with real grouped data will verify |

---

## Extension (2026-07-06, Jira `DD-41655`)

### Summary

Add three new CEL conditions (`DUR-CUR`, `DUR-CURE`, `DUR-AAR`) to the existing `DR-COEW-001` rule,
detecting when a CUR/CURE/AAR requirement's own recorded end date does not equal its calculated
duration (`Start date + period − 1 day`, or `hearing date + days − 1 day` for AAR). This is
independent of and additive to the AC2 order-end-date check already shipped above — a requirement
may fail either, both, or neither check, and both surface through the same already-built
error-summary/inline-error UI (User Stories 2/3, reused verbatim per spec FR-020).

Full design rationale: [research.md § Extension](research.md#extension-2026-07-06-jira-dd-41655--requirement-duration-end-date-validation).
Full data/YAML/algorithm design: [data-model.md § Extension](data-model.md#extension-2026-07-06-jira-dd-41655--requirement-duration-end-date-validation).

### Technical Context (delta)

**Scale/Scope**: No new rule, preprocessor, or context type. Extends one existing YAML rule
(+3 conditions), one existing preprocessor, one existing context record (+9 fields), one existing
condition-definition class (+1 field), one existing interface (+1 default method), one existing
resolver (+1 overload), and one existing rule-evaluation class (wiring only). Four new hardcoded
`promptRef` keys (unverified — see Risk Register below).

### Constitution Check (delta)

| Principle | Status | Notes |
|-----------|--------|-------|
| **I — YAML/CEL Rule-First** | ✅ PASS | New conditions authored in `DR-COEW-001.yaml` first; CEL only ever sees `*DurationMismatchCount > 0` booleans — all date arithmetic stays in the preprocessor |
| **II — Constructor Injection & Immutable DTOs** | ✅ PASS | `CommunityOrderContext` remains a record; no field injection introduced |
| **III — Layered Architecture & Preprocessor Dispatch** | ✅ PASS | Reuses the existing `PreprocessorRegistry` dispatch; no new registry entries needed |
| **VI — Severity Ceiling** | ✅ PASS | All three new conditions set `severity: ERROR`; ceiling can only lower, never promote |
| **VII — No System.out** | ✅ PASS | New `parsePromptInt` helper follows the existing SLF4J `log.warn` pattern |
| **VIII — TDD** | ✅ PASS | Implementation Sequence below mandates failing tests before each production change |

No violations. One framework extension is introduced deliberately (`MessageTemplateResolver`
overload + `getCalculatedValue` default method) — justified in research.md Decision 11 as a
generalisation, not a rule-specific hack, so it does not count as a Principle III violation.

### Project Structure (delta)

```text
specs/003-community-order-date-validation/
├── spec.md          ✅ extended (User Stories 4–7)
├── research.md      ✅ extended (Decisions 8–12)
├── data-model.md    ✅ extended
├── plan.md          ✅ this section
└── tasks.md         🔲 next — /speckit-tasks

src/main/resources/rules/
└── DR-COEW-001.yaml                                           [MODIFY — +3 conditions]

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── CommunityOrderEndDatePreprocessor.java                     [MODIFY — duration-mismatch pass]
├── CommunityOrderContext.java                                 [MODIFY — +9 fields, +getCalculatedValue override]
├── ConditionDefinition.java                                   [MODIFY — +calculatedValueSet field]
├── RuleEvaluationContext.java                                 [MODIFY — +getCalculatedValue default method]
├── MessageTemplateResolver.java                                [MODIFY — +6-arg resolve overload]
└── CelValidationRule.java                                     [MODIFY — pass extra placeholder in OFFENCE branch]

src/test/java/uk/gov/hmcts/cp/
├── services/rules/cel/
│   ├── CommunityOrderEndDatePreprocessorTest.java             [MODIFY — +duration-mismatch scenarios]
│   ├── CommunityOrderContextTest.java                         [MODIFY — +new fields/getCalculatedValue]
│   └── MessageTemplateResolverTest.java                        [MODIFY — +extraPlaceholders overload]
└── integration/
    └── CommunityOrderEndDateRuleIntegrationTest.java          [MODIFY — +User Story 4–7 scenarios]
```

**No changes to**: `PreprocessorRegistry`, `DefaultValidationService`, `ValidationController`,
`PreprocessingDefinition` (reuses existing short-code lists), Liquibase migrations, rule count in
`ValidationRuleAutoConfigurationTest`/`ValidationControllerIntegrationTest` (still one rule,
`DR-COEW-001` — only its condition count changes, 4 → 7).

### Phase 0: Research

See [research.md § Extension](research.md#extension-2026-07-06-jira-dd-41655--requirement-duration-end-date-validation)
for full decisions. Key items: extend rather than duplicate the rule (Decision 8); strict-equality
comparison semantics, not `isAfter` (Decision 9); four new hardcoded, **unverified** prompt ref keys
(Decision 10 — flagged as a risk below); generic `extraPlaceholders`/`calculatedValueSet` mechanism
for the calculated-date message token (Decision 11); `CURA` explicitly excluded (Decision 12).

### Phase 1: Design & Contracts

Full entity/algorithm/YAML design: [data-model.md § Extension](data-model.md#extension-2026-07-06-jira-dd-41655--requirement-duration-end-date-validation).

No new API surface (same as Decision 7 for the original scope) — this remains additive to the
existing `errors` list in `DraftValidationResponse`.

### Implementation Sequence (for /speckit-tasks)

TDD ordering (Constitution Principle VIII) as before: test authorship precedes or pairs with
production code for every behaviour.

1. **YAML first** — append `DUR-CUR`, `DUR-CURE`, `DUR-AAR` conditions to `DR-COEW-001.yaml`.
2. **Framework primitives (test-first)**:
   - `RuleEvaluationContextTest`-style coverage (or inline in `CommunityOrderContextTest`) for the
     new `getCalculatedValue` default-throw behaviour and the `CommunityOrderContext` override.
   - `MessageTemplateResolverTest` — new 6-arg overload: substitutes `${calculatedEndDate}` from
     `extraPlaceholders`, leaves other placeholders behaving exactly as the existing 5-arg overload.
   - `ConditionDefinitionTest` (if one exists) / YAML-loader coverage for `calculatedValueSet`.
3. **`CommunityOrderContext` extension (test-first)** — `CommunityOrderContextTest`: assert the 3
   new CEL variables and 3 new offence-id sets, plus `getCalculatedValue` for all three set names
   and an `IllegalArgumentException` for an unknown set name.
4. **`CommunityOrderEndDatePreprocessor` extension (test-first)** — `CommunityOrderEndDatePreprocessorTest`:
   - CUR: end date matches Start date + period − 1 → no violation; any mismatch (early or late) → violation with correct `curCalculatedEndDateByOffenceId` entry
   - CURE: same shape, `startDateOfTagging` / `curfewAndElectronicMonitoringPeriod` / `endDateOfTagging`
   - AAR: same shape, using `request.getHearingDay()` / `numberOfDaysToAbstain` / `until`
   - Boundary: mismatch check still runs when the community order's own end date prompt is missing/unparseable (must not be gated by the AC2 `orderEndDate` early-continue)
   - Missing/unparseable `startDate`, period, or `endDate` → skip gracefully, no violation, `WARN` logged
   - A requirement that fails both AC2 (order-end-date) and the new duration check on the same offence → both offence-id lists contain the offence
5. **`CelValidationRule` wiring** — verify (via existing scenario/override tests) that the OFFENCE-level
   branch passes `${calculatedEndDate}` only when `calculatedValueSet` is set, and that
   `errorMessageTemplate` resolution is unaffected.
6. **Integration test (test-first, end-to-end)** — extend `CommunityOrderEndDateRuleIntegrationTest`
   with User Story 4–7 scenarios (1–3 each), confirming: navigation blocked, exact top-summary and
   inline message text (including the calculated date), Share button suppression, and the
   AC2 + duration-mismatch combined case (User Story 7 Scenario 2).
7. **Quality gates** — `gradle test`, `gradle checkstyleMain`, `gradle pmdMain`, `gradle build`.

### Risk Register (delta)

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| New prompt ref keys (`startDate`, `curfewPeriod`, `startDateOfTagging`, `curfewAndElectronicMonitoringPeriod`, `numberOfDaysToAbstain`) differ from the real upstream contract | **Medium** | High | Unlike the original ticket's keys, these are unverified against a real payload — verify against the upstream `api-cp-crime-hearing-results-validator` schema/Swagger or a real dev payload before/during implementation; unit tests fail fast if wrong |
| Period value is not a clean integer string (e.g. `"30 days"`, decimals) | Medium | Low | `parsePromptInt` follows Decision 6's defensive parse-and-skip-with-WARN pattern; integration tests cover a malformed period |
| Moving the `orderEndDate == null` early-continue could accidentally also skip AC2 checks that should still run, or vice versa | Low | High | Existing `CommunityOrderEndDateRuleIntegrationTest` (User Story 1) is a regression suite — re-run in full after the refactor; spec SC-009 explicitly requires no change to any existing scenario's pass/fail outcome |
| `${calculatedEndDate}` left unexpanded if `getCalculatedValue` returns `null` for an offence that is in the mismatch list | Low | Medium | By construction the preprocessor always populates the map entry in the same branch that adds the offence id to the mismatch list — covered by a unit test asserting the two collections stay in lock-step |
