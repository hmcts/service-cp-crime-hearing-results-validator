# Research: Curfew and AAR Requirement End-Date Period Validation (DR-COEW-002)

**Branch**: `DD-41655-curfew-aar-end-date-validation`
**Date**: 2026-05-27
**Phase**: 0 — Unknowns resolved before design

---

## Decision 1 — Rule ID

**Decision**: `DR-COEW-002`, priority `4500`

**Rationale**: The new rule is a sibling to `DR-COEW-001` (Community Order End Date Validation, priority 4000) and covers the same result short-code family (curfew and AAR requirements on CO/YRO orders). Assigning the next COEW number keeps related rules grouped. Priority 4500 ensures DR-COEW-001 runs first (its violations are a superset concern; the period-mismatch check runs after the ordering check).

**Alternatives considered**: A new category (e.g., `DR-REQ-001`) was considered since YRC1/YRC2 are YRO-specific. Rejected — the validation logic, prompt keys, and context of use are all community/rehabilitation order requirements; the COEW prefix is the best fit.

---

## Decision 2 — Preprocessor qualifier and context keying strategy

**Decision**: Preprocessor qualifier `"curfew-period-check"`. The preprocessor produces **one `CurfewPeriodContext` per violation** (per combination of violation-type × defendant × offence), keyed in the map by the compound string `"<TYPE>:<defendantId>:<offenceId>"` (e.g., `"CUR:d1:o3"`).

**Rationale**: Each violation carries a distinct computed expected end date. If the context were keyed per defendant (as in `CommunityOrderEndDatePreprocessor`), a defendant with two violated offences of the same type (two different CUR requirements with different periods) could not have a specific expected date per offence in the message. The compound key keeps each context to exactly one violation with one date. Since `CelValidationRule.evaluate()` iterates `contexts.values()` and never reads the map key, no change to `CelValidationRule`'s iteration is required.

**Alternatives considered**:
- Per-defendant context with separate `curExpectedEndDate`, `cureExpectedEndDate`, … string fields per type. Rejected — breaks down when the same defendant has two violated offences of the same type.
- Per-defendant context with a list of `(offenceId, expectedDate)` pairs. Rejected — message templates cannot reference individual list items in CEL; requires bespoke resolver logic.

---

## Decision 3 — YAML conditions: one condition or five

**Decision**: **One condition** (`violationCount > 0`) covering all five violation types (CUR, CURE, YRC2, YRC1, AAR).

**Rationale**: Because each context already represents exactly one violation type, the CEL expression `violationCount > 0` always evaluates true for all contexts emitted by the preprocessor (contexts are only created for violations, never for passing offences). A single condition with a single message template `"The end date does not match the period entered. End date based on entered period: ${expectedEndDate}"` is sufficient — the expected date is specific to each context. Five separate conditions would add noise without adding logic.

**Alternatives considered**: Five separate conditions (one per short-code group: AC1a CUR, AC1b CURE, AC1c YRC2, AC1d YRC1, AC2 AAR). Rejected — the message text is identical across all five; any distinction exists only in which expected date is shown, already handled by `${expectedEndDate}` from the context.

---

## Decision 4 — Template variable extension: `${expectedEndDate}`

**Decision**: Add a `default Map<String, String> stringVariables()` method to `RuleEvaluationContext` (returning `Map.of()`) and extend `MessageTemplateResolver.resolve()` to also substitute `${key}` tokens from `stringVariables()` after the existing `${offenceNumber}` / `${defendantName}` substitutions.

**Rationale**: The message template must embed a computed expected date string (e.g., `"30/07/2026"`). The CEL context is `Map<String, Long>` — dates cannot be represented there. The existing resolver only handles `${offenceNumber}` and `${defendantName}`. Adding a generic `stringVariables()` method on the context interface (with a safe default) lets any future context expose arbitrary string placeholders without requiring a new resolver method signature per placeholder. Backward compatible: existing contexts (`CommunityOrderContext`, `DefendantContext`, etc.) inherit the default empty map and existing behaviour is unchanged.

**Alternatives considered**:
- Add `expectedEndDate` as a new first-class resolver method. Rejected — ties the resolver to a specific field, not extensible.
- Pre-compute the full message in the context and skip the template mechanism. Rejected — violates Constitution Principle I (message text must live in YAML, not Java).

---

## Decision 5 — `affectedDefendants` on OFFENCE-level ERRORs

**Decision**: Extend `CelValidationRule.evaluate()` to set `affectedDefendants` on the `ValidationIssue` for OFFENCE-level ERROR conditions **when `context.defendantId()` returns a non-null value**. For existing contexts where `defendantId()` returns `null` (the default), behaviour is unchanged.

**Rationale**: The spec (FR-008) requires `affectedDefendants: [{ defendantId }]` on each ERROR issue. The spec 003 data-model.md noted this as a planned "framework change to `CelValidationRule`" that was not yet implemented. The new `CurfewPeriodContext` will override `defendantId()` to return the actual defendant ID, allowing the framework to populate `affectedDefendants` precisely for this rule. Existing contexts (`CommunityOrderContext`, `CommunityOrderContext` etc.) leave `defendantId()` as null and are unaffected.

**Alternatives considered**: Set `affectedDefendants` unconditionally using the map key. Rejected — the map key for the new preprocessor is a compound string (not a bare defendantId), so this would populate the field with garbage for new contexts and potentially break existing rules if they ever adopt non-defendant keying.

---

## Decision 6 — Upstream API library: DURATION prompt support (BLOCKING)

**Decision**: The upstream `api-cp-crime-hearing-results-validator` library must be bumped to version `0.1.8` (or higher) to add `type: String` and `childPrompts: List<Prompt>` fields to the `Prompt` class. This is a **blocking prerequisite** — `CurfewPeriodPreprocessor` cannot read DURATION nested prompts from the current `Prompt` model (`0.1.7`).

**Rationale**: Confirmed by inspection — the current `Prompt` class in `0.1.7` only has `promptRef` and `promptValue` (both `String`). The DURATION prompt for curfew period is a JSON object with a nested `value` array of child prompt objects; this cannot be deserialized into the current model. The INT prompt for AAR can already be read via `promptValue` (the integer as a string), so no model change is needed for AAR. Only DURATION (curfew period) requires the new fields.

**Library change required** (in the `api-cp-crime-hearing-results-validator` source repo):
```java
// Prompt.java additions
private String type;                    // e.g. "DATE", "INT", "DURATION"
private List<Prompt> childPrompts;      // non-null for DURATION type; empty otherwise
```

The `CurfewPeriodPreprocessor` reads the period for CUR/YRC2 and CURE/YRC1 by:
1. Finding the prompt where `promptRef` equals `"curfewPeriod"` (or `"curfewAndElectronicMonitoringPeriod"`)
2. Reading `childPrompts.get(0).getPromptRef()` → unit (`"Days"`, `"Weeks"`, `"Months"`)
3. Reading `childPrompts.get(0).getPromptValue()` → quantity as integer string

For AAR: reads `promptValue` directly from the `"numberOfDaysToAbstainFromConsumingAnyAlcohol"` prompt (no nested structure; always days).

**Alternatives considered**: Parse the raw JSON `value` as a String and extract the child data manually. Rejected — circumvents the generated OpenAPI model contract; brittle and untyped.

---

## Decision 7 — `PreprocessingDefinition`: new `yroShortCodes` field

**Decision**: Add `yroShortCodes: List<String>` to `PreprocessingDefinition`. The YAML configures it as `[YROEW, YRONI, YROFEW, YROISS, YROINI]`.

**Rationale**: The preprocessor needs to distinguish CO parents from YRO parents because AAR validation applies to CO parents only. Existing fields (`communityOrderShortCodes`) already cover CO parents. A new `yroShortCodes` field covers YRO parents. Curfew types (CUR/YRC2, CURE/YRC1) apply when EITHER parent type is present.

**Alternatives considered**: Hardcode YRO short codes in the preprocessor Java. Rejected — violates Constitution Principle I (all configurable values must live in YAML).

---

## Decision 8 — Date format in message template

**Decision**: The `${expectedEndDate}` placeholder is formatted as `DD/MM/YYYY` (e.g., `"30/07/2026"`) using `DateTimeFormatter.ofPattern("dd/MM/yyyy")`.

**Rationale**: This matches the existing UI date display convention on the CPP platform and is consistent with the spec's message template examples (`"<<start date + period - 1 day>>"`).

**Alternatives considered**: ISO-8601 (`YYYY-MM-DD`). Rejected — inconsistent with the UI date display format.

---

## Decision 9 — Graceful handling of missing/invalid prompt data

**Decision**: When any required prompt (start date, period quantity, period unit, end date) is missing, blank, or unparseable, the check for that specific requirement is **skipped silently** (no `ValidationIssue` emitted, no exception thrown). A `WARN`-level SLF4J log is emitted identifying the prompt ref, short code, offence ID, and the reason for skipping.

**Rationale**: Missing prompts indicate incomplete data entry, which is handled upstream (mandatory field validation in the UI). Emitting a spurious error for an incomplete entry would create noise and confuse the clerk. This matches the existing behaviour in `CommunityOrderEndDatePreprocessor.parseDateValue()`.

**Alternatives considered**: Emit a validation error for missing dates. Rejected — the clerk hasn't finished entering data; the form's own required-field validation handles this.

---

## Decision 10 — No per-rule override integration test

**Decision**: The new rule does NOT need its own override / severity-ceiling integration test. The framework-level override mechanism is already proven by `ValidationRuleOverrideIntegrationTest.java` (covers DR-SENT-002). The new rule inherits that coverage.

**Rationale**: Constitution Principle and `design_rules.md` both state explicitly that per-rule override ITs are duplicative. The override mechanism is rule-agnostic.

---

## Prompt ref keys — full confirmed list

| Short code | Field | `promptRef` | Type | Notes |
|------------|-------|-------------|------|-------|
| CUR, YRC2 | Start date | `startDate` | DATE | ISO-8601 string |
| CUR, YRC2 | Period | `curfewPeriod` | DURATION | nested child: unit=`promptRef`, qty=`promptValue` |
| CUR, YRC2 | End date | `endDate` | DATE | ISO-8601 string |
| CURE, YRC1 | Start date | `startDateOfTagging` | DATE | ISO-8601 string |
| CURE, YRC1 | Period | `curfewAndElectronicMonitoringPeriod` | DURATION | same nested structure |
| CURE, YRC1 | End date | `endDateOfTagging` | DATE | existing promptRef |
| AAR | Period | `numberOfDaysToAbstainFromConsumingAnyAlcohol` | INT | flat; value = integer string; always days |
| AAR | Until date | `until` | DATE | existing promptRef |
| Request | Hearing date | `hearingDay` | `LocalDate` | `DraftValidationRequest.getHearingDay()` |

DURATION child `promptRef` unit values: `"Days"` → `ChronoUnit.DAYS`, `"Weeks"` → `ChronoUnit.WEEKS`, `"Months"` → `ChronoUnit.MONTHS`.

---

## Implementation prerequisite sequence

1. **Upstream library PR** — add `type` + `childPrompts` to `Prompt` and release `0.1.8`
2. **Library version bump** — update `libs.versions.toml` to consume `0.1.8`
3. **Framework changes** — `RuleEvaluationContext.stringVariables()`, `MessageTemplateResolver`, `CelValidationRule` (OFFENCE-level `affectedDefendants`), `PreprocessingDefinition.yroShortCodes`
4. **Rule implementation** — `CurfewPeriodContext`, `CurfewPeriodPreprocessor`, `DR-COEW-002.yaml`
5. **Tests** — unit tests (TDD), integration test
