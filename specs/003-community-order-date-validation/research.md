# Research: Community Order End Date Validation (DR-COEW-001)

**Branch**: `DD-41653-community-order-date-validation`  
**Date**: 2026-05-20  
**Feature spec**: [spec.md](spec.md)

---

## Decision 1 — Preprocessor Grouping Strategy

**Decision**: Group per-defendant (one `CommunityOrderContext` per `defendantId`), aggregating violations across all offences that belong to that defendant.

**Rationale**: The error summary says "This affects: Defendant X" — the unit of reporting is the defendant. Grouping per-defendant matches `CustodialPreprocessor`'s approach and allows the CEL variable map (violation counts) and affected offence-id sets to accumulate across all of a defendant's offences in a single pass.

**Alternatives considered**:
- *Per-offence grouping* (Rejected — the UI requires a defendant-level error that lists which defendants are affected, not one error per offence. Per-offence would produce up to N errors for N offences with the same violation type on the same defendant.
- *Per-(defendant, offence) grouping*: Rejected — adds complexity without gain; the defendant is still the natural error scope.

---

## Decision 2 — Multiple AC2 Violations: Separate Conditions or Single Combined Condition

**Decision**: Separate YAML `conditions` per requirement type (AC2a=CUR, AC2b=CURE, AC2c=CURA, AC2d=AAR) — each fires its own `ValidationIssue` when the corresponding violation count > 0.

**Rationale**: The YAML+CEL engine produces one `ValidationIssue` per triggered `condition`. Separate conditions are the natural fit for the engine and give maximum rule configurability — each condition can be individually enabled/disabled or severity-capped via the `validation_rule` DB table. CEL cannot dynamically concatenate strings, so a "combined message" would require Java-side post-processing, violating Constitution Principle I. Spec FR-003, FR-004, A-005, and the Edge Cases section have been updated to reflect "one error per violated requirement type" semantics, matching this architectural decision.

**Alternatives considered**:
- *Single condition with a dynamic combined message*: Rejected — CEL expressions cannot dynamically concatenate strings across requirement types; achieving this would require moving business logic into Java, violating Constitution Principle I.
- *Post-processing aggregation layer*: Rejected — adds complexity, breaks the clean separation between rule evaluation and issue emission, and removes per-requirement-type runtime configurability.

---

## Decision 3 — Prompt Ref Names: Hardcoded or Configurable in YAML

**Decision**: Hardcode the `promptRef` lookup keys in `CommunityOrderEndDatePreprocessor` Java code:
- Community orders (COEW/COS/CONI) → `"endDate"`
- CUR → `"endDate"`, CURE → `"endDateOfTag"`, CURA → `"endDate"`, AAR → `"until"`

**Rationale**: The `promptRef` values are part of the upstream API contract (`api-cp-crime-hearing-results-validator`), not a business policy decision. They are stable and tied to the `ResultLineDto.prompts` schema. Adding YAML config fields for them would introduce unnecessary coupling and complexity with no practical benefit — no BA would ever change `"endDateOfTag"` to something else without an API change.

**Alternatives considered**:
- *YAML-configurable `promptRefs` map in `PreprocessingDefinition`*: Rejected — prompt ref names are API-contract values, not policy values. They belong in Java, not YAML.

---

## Decision 4 — `PreprocessingDefinition` Extension

**Decision**: Add five new `List<String>` fields to `PreprocessingDefinition`:
- `communityOrderShortCodes`
- `curfewShortCodes`
- `curfewTagShortCodes`
- `furtherCurfewShortCodes`
- `alcoholAbstinenceShortCodes`

**Rationale**: `PreprocessingDefinition` is the YAML-to-Java bridge. Adding fields here keeps the YAML short-code lists authoritative (Constitution Principle I) and means a BA can change which short codes trigger the rule by editing YAML without touching Java. Each existing preprocessor adds its own fields here; this is the established pattern.

**Alternatives considered**:
- *Hardcode short codes in the preprocessor*: Rejected — violates Principle I; changing COEW to a new code would require a Java change and a redeploy.
- *Add `@JsonIgnoreProperties(ignoreUnknown = true)` to `PreprocessingDefinition`*: Rejected — silently discards unknown YAML fields, masking configuration typos. Explicit fields are safer.

---

## Decision 5 — Date String Format from `promptValue`

**Decision**: Parse `promptValue` as ISO-8601 (`yyyy-MM-dd`) using `LocalDate.parse(promptValue)`. If `promptValue` is null, blank, or unparseable, log a `WARN` and skip the date comparison for that prompt (treat as no violation for that requirement on that offence).

**Rationale**: ISO-8601 is the standard wire format for `LocalDate` in HMCTS services. Defensive handling (skip vs. throw) ensures one malformed prompt does not abort the entire validation run and produce a 500 response.

**Alternatives considered**:
- *Throw on parse failure*: Rejected — a single bad date from the UI would make the entire hearing unvalidatable; log and skip is more resilient.
- *Use a configurable date format*: Rejected — ISO-8601 is the API contract; no need for configurability.

---

## Decision 6 — No New API Contract / No `contracts/` Artefact

**Decision**: No new `contracts/` directory needed for this feature.

**Rationale**: The service exposes one existing endpoint (`POST /api/validation/validate`) whose request/response schema is owned by the upstream `api-cp-crime-hearing-results-validator` library. This feature adds new `ValidationIssue` entries to the existing `errors` list in `DraftValidationResponse` and begins populating `affectedDefendants` — a field that was already declared in the `ValidationIssue` schema but had never been set. No new endpoints, no new consumers, no upstream schema changes required.

---

## Resolved Unknowns Summary

| # | Unknown | Resolution |
|---|---------|------------|
| 1 | Hearing date availability | `hearingDay: LocalDate` on `DraftValidationRequest` — confirmed in 0.1.6 |
| 2 | Prompts field availability | `List<Prompt> prompts` on `ResultLineDto` — confirmed in 0.1.6; `Prompt.getPromptRef()` / `getPromptValue()` |
| 3 | Prompt ref key names | Hardcoded: `endDate`, `endDateOfTag`, `until` |
| 4 | Multiple violations display | Separate condition per requirement type; UI groups them |
| 5 | Share button scope | Hearing-level (hidden if any defendant has errors) |
| 6 | AC1 scope | Out of scope; separate ticket |
| 7 | Grouping unit | Per-defendant |
