# Implementation Plan: YRO Curfew Requirement Duration Validation

**Branch**: `dev/DD-42850-YRO-Duration` | **Date**: 2026-07-20
**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md) | **Data model**: [data-model.md](data-model.md)

## Summary

Extend the existing `DR-YRO-001` rule with two new conditions that detect when a Youth
Rehabilitation Order curfew requirement's own recorded end date does not match its calculated
duration:

- **`DUR-YRC2`** — Curfew (YRC2): "End date" ≠ "Start date" + "Curfew period" − 1 day
- **`DUR-YRC1`** — Curfew with electronic monitoring (YRC1): "End date of tagging" ≠ "Start date of
  tagging" + "Curfew and electronic monitoring period" − 1 day

This is independent of and additive to the existing AC2 order-end-date-vs-requirement-end-date
checks already shipped in `DR-YRO-001` (`AC2a`/`AC2b`/`AC2c`) — a requirement may fail either, both,
or neither. The implementation directly mirrors the equivalent, already-shipped Community Order
requirement-duration validation (Jira `DD-41655`, `DR-COEW-001` conditions `DUR-CUR`/`DUR-CURE`) in
the sibling `service-cp-crime-hearing-results-validator` (Community Order) codebase — see
research.md for the specific decisions carried over and adapted.

No new rule, preprocessor, or `PreprocessingDefinition` fields are needed: `YouthRehabilitationPreprocessor`,
`YouthRehabilitationContext`, and the existing `curfewShortCodes`/`curfewTagShortCodes` config are
extended in place. Four small framework-level additions (ported verbatim from the CO precedent) are
required so any rule can express a calculated-value message placeholder: `ConditionDefinition`,
`RuleEvaluationContext`, `MessageTemplateResolver`, `CelValidationRule`.

---

## Technical Context

**Language/Version**: Java 25
**Primary Dependencies**: Spring Boot 4, `org.projectnessie.cel` (CEL engine), `api-cp-crime-hearing-results-validator:0.2.1` (provides `DraftValidationRequest`, `ResultLineDto`, `Prompt`), Lombok, Jackson (YAML deserialisation)
**Storage**: PostgreSQL — existing `validation_rule` table (rule-level severity/enabled override, one row for `DR-YRO-001`, already present); no new tables or migrations
**Testing**: JUnit 5 + Mockito + AssertJ (unit), MockMvc + TestContainers + WireMock (integration), RestTemplate live-HTTP (API test, `gradle api`)
**Target Platform**: Azure-hosted Spring Boot service, local port 4550
**Project Type**: Web service — stateless validation API
**Performance Goals**: No change to existing Gatling assertion thresholds; rule evaluation remains per-request and stateless
**Constraints**: Checkstyle Google (`maxWarnings = 0`), PMD (`ignoreFailures = false`), no wildcard imports
**Scale/Scope**: Zero new rules/preprocessors/DTOs. Two new YAML conditions, ~6 new fields on one existing record, 4 small framework-level additions shared across all rules.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| **I — YAML/CEL Rule-First** | ✅ PASS | `DUR-YRC2`/`DUR-YRC1` are authored in `DR-YRO-001.yaml` before any Java change; CEL only ever evaluates `*DurationMismatchCount > 0` booleans — all date/period arithmetic stays in the preprocessor |
| **II — Constructor Injection & Immutable DTOs** | ✅ PASS | `YouthRehabilitationContext` remains a record; no field injection introduced |
| **III — Layered Architecture & Data-Driven Preprocessor Dispatch** | ✅ PASS | Reuses the existing `PreprocessorRegistry` dispatch (qualifier `youth-rehabilitation-order`); no new preprocessor registration needed. **Note**: `.claude/rules/design_rules.md`'s "Known limitations" section describing a single hard-wired `CustodialPreprocessor` is stale — the registry (`PreprocessorRegistry`, three registered preprocessors, three shipped rules) is already live on this branch. Flagged for a doc fix; not a gate blocker for this feature. |
| **IV — Spec-Driven Build Loop** | ✅ PASS | This plan; tasks.md and implement follow; `code-reviewer`, `qa`, `spec-validator` gate before ship |
| **V — HMCTS Standards** | ✅ PASS | Java 25, Spring Boot 4, Gradle, SLF4J logging only |
| **VI — Severity Ceiling** | ✅ PASS | Both new conditions set `severity: ERROR`; the existing rule-level DB ceiling can lower to `WARNING` but never promotes |
| **VII — No System.out** | ✅ PASS | New `parsePromptPeriod` helper follows the existing SLF4J `log.warn` pattern in `PreprocessorHelper` |
| **VIII — TDD** | ✅ PASS | Implementation Sequence below mandates failing tests authored before/with each production change |

No violations. One framework-level generalisation is introduced deliberately
(`calculatedValueSet` / `getCalculatedValue` / `MessageTemplateResolver` overload) — justified in
research.md Decision 6 as a reusable mechanism (already proven by the CO precedent), not a
YRO-specific hack, so it does not count as a Principle III violation. Complexity Tracking table not
required.

---

## Project Structure

### Documentation (this feature)

```text
specs/008-yro-curfew-duration-validation/
├── spec.md                          ✅ complete
├── research.md                      ✅ complete (this phase)
├── data-model.md                    ✅ complete (this phase)
├── contracts/
│   └── validation-issue-additions.md ✅ complete (this phase)
├── quickstart.md                    ✅ complete (this phase)
├── plan.md                          ✅ this file
├── checklists/requirements.md       ✅ complete (specify phase)
└── tasks.md                         🔲 next — /speckit-tasks
```

### Source Code Changes

```text
src/main/resources/rules/
└── DR-YRO-001.yaml                                             [MODIFY — +2 conditions: DUR-YRC2, DUR-YRC1]

src/main/java/uk/gov/hmcts/cp/services/rules/cel/
├── YouthRehabilitationPreprocessor.java                        [MODIFY — duration-mismatch pass]
├── YouthRehabilitationContext.java                              [MODIFY — +6 fields, +getCalculatedValue override]
├── PreprocessorHelper.java                                      [MODIFY — +parsePromptPeriod / ParsedPeriod]
├── ConditionDefinition.java                                     [MODIFY — +calculatedValueSet field]
├── RuleEvaluationContext.java                                   [MODIFY — +getCalculatedValue default method]
├── MessageTemplateResolver.java                                 [MODIFY — +6-arg resolve overload]
└── CelValidationRule.java                                       [MODIFY — OFFENCE-branch wiring + calculatedValuePlaceholder helper]

src/test/java/uk/gov/hmcts/cp/
├── services/rules/cel/
│   ├── YouthRehabilitationPreprocessorTest.java                [MODIFY — +DurYrc2/DurYrc1 nested classes]
│   ├── YouthRehabilitationContextTest.java                     [MODIFY — +new fields/getCalculatedValue]
│   └── MessageTemplateResolverTest.java                        [MODIFY — +extraPlaceholders overload]
└── integration/
    └── YroEndDateValidationIntegrationTest.java                [MODIFY — +duration-mismatch scenarios]

src/apiTest/java/uk/gov/hmcts/cp/http/
└── YroCurfewDurationApiHttpLiveTest.java                       [NEW — live-HTTP test, mirrors RequirementDurationApiHttpLiveTest]
```

**No changes to**: `PreprocessorRegistry`, `PreprocessingDefinition` (reuses existing
`curfewShortCodes`/`curfewTagShortCodes`), `DefaultValidationService`, `ValidationController`,
Flyway migrations, rule count in `ValidationRuleAutoConfigurationTest` (still 3 rules —
`DR-SENT-002`, `DR-DISQ-001`, `DR-YRO-001` — only `DR-YRO-001`'s condition count changes, 3 → 5).

**Structure Decision**: Single project (existing Spring Boot service layout under `src/main` /
`src/test` / `src/apiTest`, per `.claude/rules/design_rules.md`'s layer architecture). No new module,
package, or project boundary is introduced.

---

## Phase 0: Research (complete)

All decisions resolved. See [research.md](research.md) for full rationale. Key resolved items:

- Extend `DR-YRO-001` in place rather than author a new rule (Decision 1)
- New prompt ref keys are hardcoded and **unverified** against the real upstream contract (Decision 2 — flagged as a risk below)
- Period values may carry a unit suffix (`"21 Days"`, `"3 Weeks"`, `"1 Months"`) — reuse the corrected regex/`ChronoUnit` approach discovered by the CO precedent, not a bare-integer assumption (Decision 3)
- Calculated end date is formatted `dd/MM/yyyy` in messages (Decision 4)
- Duration checks run independently of the existing order-end-date gate (Decision 5)
- Four framework additions ported verbatim from the shipped CO implementation (Decision 6)
- `parsePromptPeriod` added to the shared `PreprocessorHelper`, continuing this repo's existing de-duplication pattern (Decision 7)
- YRC3 and an AAR-equivalent are explicitly out of scope (Decision 8)
- Mismatch semantics are strict inequality, not `isAfter` (Decision 9)
- No Flyway migration or `PreprocessingDefinition` changes needed (Decisions 10–11)

---

## Phase 1: Design & Contracts

### 1a. Data model

See [data-model.md](data-model.md) for full entity definitions, CEL variable map, named offence-id
sets, and the calculated-value mechanism.

**Summary of new/changed types:**

| Type | Change | Location |
|------|--------|----------|
| `YouthRehabilitationContext` | +6 fields (2 counts, 2 offence-id lists, 2 calculated-date maps), +`getCalculatedValue` override | `cel/YouthRehabilitationContext.java` |
| `YouthRehabilitationPreprocessor` | +duration-mismatch pass (unconditional per offence) | `cel/YouthRehabilitationPreprocessor.java` |
| `PreprocessorHelper` | +`parsePromptPeriod`, +`ParsedPeriod` record | `cel/PreprocessorHelper.java` |
| `ConditionDefinition` | +`calculatedValueSet` field (framework) | `cel/ConditionDefinition.java` |
| `RuleEvaluationContext` | +`getCalculatedValue` default method (framework) | `cel/RuleEvaluationContext.java` |
| `MessageTemplateResolver` | +6-arg `resolve` overload (framework) | `cel/MessageTemplateResolver.java` |
| `CelValidationRule` | OFFENCE-branch wiring (framework) | `cel/CelValidationRule.java` |
| `DR-YRO-001.yaml` | +2 conditions (`DUR-YRC2`, `DUR-YRC1`) | `resources/rules/DR-YRO-001.yaml` |

### 1b. Interface contracts

See [contracts/validation-issue-additions.md](contracts/validation-issue-additions.md). No new API
surface — the existing `POST /api/validation/validate` endpoint is unchanged. Two new possible
`ValidationIssue` entries (condition ids `DUR-YRC2`, `DUR-YRC1`) can appear under the already-existing
`ruleId: "DR-YRO-001"` in `errors.validationIssues` — additive and backward-compatible.

### 1c. Agent context

`CLAUDE.md` updated to point to this plan (see below).

---

## Implementation Sequence (for /speckit-tasks)

TDD ordering (Constitution Principle VIII): test authorship precedes or pairs with production code
for every behaviour.

### Step 1 — YAML first (Constitution Principle I)

Append `DUR-YRC2` and `DUR-YRC1` conditions to `DR-YRO-001.yaml` (see data-model.md for exact
blocks). This is the contract the Java must satisfy.

### Step 2 — Framework primitives (test-first)

1. `MessageTemplateResolverTest` — new 6-arg overload: substitutes `${calculatedEndDate}` from
   `extraPlaceholders`, leaves other placeholders behaving exactly as the existing 5-arg overload
   untouched.
2. `ConditionDefinition` — no dedicated test file exists for this simple Lombok `@Data` class in
   this repo; covered transitively via `YouthRehabilitationPreprocessorTest`/YAML-loader coverage of
   `calculatedValueSet`.
3. `RuleEvaluationContext`'s new default method — covered transitively via
   `YouthRehabilitationContextTest`'s override test (Step 3) and any other context's
   inherited-throw behaviour, if such coverage already exists for `getOffenceIdSet`'s default.

### Step 3 — `YouthRehabilitationContext` extension (test-first)

1. Extend `YouthRehabilitationContextTest`: assert the 2 new CEL variables
   (`curDurationMismatchCount`, `cureDurationMismatchCount`), the 2 new offence-id set names, and
   `getCalculatedValue` for both new set names plus an `IllegalArgumentException` for an unknown set
   name.
2. Extend `YouthRehabilitationContext.java` to pass.

### Step 4 — `PreprocessorHelper.parsePromptPeriod` (test-first)

1. Add unit coverage (in `YouthRehabilitationPreprocessorTest` or a dedicated
   `PreprocessorHelperTest` if one exists) for: bare integer, `"21 Days"`, `"3 Weeks"`,
   `"1 Months"`, unrecognised unit fallback to `DAYS`, blank/missing value, non-numeric value.
2. Implement `parsePromptPeriod` + `ParsedPeriod` in `PreprocessorHelper.java`.

### Step 5 — `YouthRehabilitationPreprocessor` extension (test-first)

1. Extend `YouthRehabilitationPreprocessorTest` covering:
   - YRC2: end date matches Start date + period − 1 → no violation; any mismatch (early or late) →
     violation with correct `curCalculatedEndDateByOffenceId` entry (`dd/MM/yyyy`)
   - YRC1: same shape, `startDateOfTagging` / `curfewAndElectronicMonitoringPeriod` / `endDateOfTagging`
   - Duration check still runs when the YRO's own order end date prompt is missing/unparseable (must
     not be gated by the existing `orderEndDate == null` early-continue)
   - Missing/unparseable start date, period, or end date → skip gracefully, no violation, `WARN` logged
   - A requirement that fails both the existing AC2 check and the new duration check on the same
     offence → both offence-id lists contain the offence
   - Month-arithmetic period (e.g. `"1 Months"` from a 31-day start date) lands on the correct
     calendar date, not a naive 30/31-day add
   - Multiple defendants / master-defendant-id grouping isolation (mirrors existing AC2 test coverage)
2. Implement the duration-mismatch pass in `YouthRehabilitationPreprocessor.java` to pass all tests.

### Step 6 — `CelValidationRule` wiring

Verify (via existing scenario/override tests, or new targeted tests) that the OFFENCE-level branch
passes `${calculatedEndDate}` only when `calculatedValueSet` is set, and that `errorMessageTemplate`
resolution (top-of-screen summary) is unaffected.

### Step 7 — Integration test (test-first, end-to-end)

1. Extend `YroEndDateValidationIntegrationTest` with the acceptance scenarios from spec.md User
   Stories 1–3: navigation-blocking scenarios, exact top-summary and inline message text (including
   the calculated date), and the combined AC2 + duration-mismatch case.
2. Run integration tests to confirm all pass.

### Step 8 — API test (live HTTP)

1. Add `YroCurfewDurationApiHttpLiveTest.java` under `src/apiTest/java/uk/gov/hmcts/cp/http/`,
   mirroring `YroEndDateApiHttpLiveTest.java`'s pattern (and the CO precedent
   `RequirementDurationApiHttpLiveTest.java`): JDBC toggle of the `validation_rule` row with a
   2-second sleep either side (Caffeine cache TTL), `RestTemplate` POSTs against the docker-compose
   stack, assertions on `isValid`, `errors.validationIssues[].affectedOffences[].message`, and
   `errors.errorMessages[]`.

### Step 9 — Quality gates

Run in order:
```bash
gradle test                    # all unit + integration tests green
gradle checkstyleMain          # zero warnings
gradle pmdMain                 # no violations
gradle build                   # full build clean
gradle api                     # live API tests (docker-compose)
```

---

## Complexity Tracking

*(No constitution violations — section left empty per template instructions)*

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| New prompt ref keys (`startDate`, `curfewPeriod`, `startDateOfTagging`, `curfewAndElectronicMonitoringPeriod`) differ from the real upstream contract | **Medium** | High | Unverified against a real payload — verify against the upstream `api-cp-crime-hearing-results-validator` schema/Swagger or a real dev payload during implementation; unit tests fail fast if wrong |
| Period value format assumptions (unit-suffix regex) don't cover every real-world value | Low | Medium | Pattern ported from the CO precedent, which was corrected against real payloads; unrecognised units fall back to `DAYS` with a `WARN` log rather than throwing |
| Moving/duplicating the per-offence loop body could accidentally change existing AC2a/b/c behaviour | Low | High | `YroEndDateValidationIntegrationTest` is a regression suite for the existing checks — re-run in full after the change; spec SC-005 explicitly requires no change to any existing scenario's pass/fail outcome |
| `${calculatedEndDate}` left unexpanded if `getCalculatedValue` returns `null` for an offence in the mismatch list | Low | Medium | By construction the preprocessor always populates the map entry in the same branch that adds the offence id to the mismatch list — covered by a unit test asserting the two collections stay in lock-step |
| Stale `.claude/rules/design_rules.md` "Known limitations" section could mislead a future contributor into re-implementing already-shipped registry dispatch | Low | Low | Flagged in Constitution Check above; recommend a follow-up docs-only fix (exempt from the build loop per workflow.md) |
