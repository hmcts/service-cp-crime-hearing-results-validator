# Research: No Conviction Warning (DR-CONV-006)

**Branch**: `DD-43039-no-conviction-warning` | **Date**: 2026-07-31

## Decision 1: Preprocessor pattern — per-offence (matching DR-DISQ-001 and DR-CTL-001)

**Decision**: Use a per-offence preprocessor, one `NoConvictionContext` per offence in the request.

**Rationale**: Whether an offence has a final, non-excluded result and whether it is convicted are both offence-scoped properties, with no cross-offence or cross-defendant aggregation. This matches the established pattern for both prior warning-style rules.

**Alternatives considered**: A single aggregate context (like `CustodialPreprocessor`) — rejected for the same reason it was rejected for DR-CTL-001: it would lose the per-offence scoping `affectedOffenceSet` needs.

---

## Decision 2: Reuse `DR-DISQ-001`'s "final, non-excluded result" gate verbatim, minus the offence-code restriction

**Decision**: Copy the final-result gate logic from `DisqualificationExtendedTestPreprocessor` — filter result lines to `category == F`, then check whether any of those final lines has a short code outside `excludedFinalShortCodes` — but drop the `relevantOffenceCodes` check entirely, since AC1 applies to any offence, not just Road Traffic Act offences.

**Rationale**: The user stories explicitly reuse the same excluded short-code set (`wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`) as DR-DISQ-001, word for word. Re-deriving this logic independently would duplicate a proven implementation for no benefit and risks the two rules silently drifting apart on what counts as a "final, substantive" result.

**Update**: DR-CONV-006's `excludedFinalShortCodes` was later extended with `err`, `errf`, `dhd` — non-substantive disposals not on DR-DISQ-002's list. The two rules' lists are configured independently per YAML file (Decision 4) and are not required to stay identical; this addition only widens DR-CONV-006's excluded set.

**Alternatives considered**: Introducing a shared `FinalResultClassifier` helper used by both `DisqualificationExtendedTestPreprocessor` and the new preprocessor — considered but rejected for this change. It would touch an already-shipped, tested class (`DisqualificationExtendedTestPreprocessor`) for a refactor that isn't required by this feature, and the constitution's Spec-Driven Build Loop discourages incidental refactors riding on a feature change. Flagged as a follow-up opportunity, not done here.

---

## Decision 3: Reuse `DR-CTL-001`'s conviction check verbatim — `OffenceDto.isConvicted`

**Decision**: Read `offence.getIsConvicted()` directly, treating `null` as `false` (`Boolean.TRUE.equals(...)`), exactly as `CtlMissingPreprocessor` does.

**Rationale**: This is the only conviction signal this service has. There is no `ConvictionStatus` type, no separate guilty-plea or guilty-verdict field, and no upstream API change is needed — `isConvicted` was added to `OffenceDto` for `DR-CTL-001` and already reflects "guilty plea, finding of guilt, or a recorded date of conviction" per its existing field description. AC1A (guilty plea) and AC1B (guilty verdict) are therefore functionally identical from this service's point of view: both are upstream events that flip the same boolean before the next validation call.

**Alternatives considered**: Distinguishing plea-driven vs verdict-driven conviction with two separate fields — rejected; no such fields exist upstream, nothing in the acceptance criteria requires the *reason* for conviction to affect the warning (only whether the offence is convicted at all), and inventing new upstream fields for a distinction the rule doesn't need would violate Constitution Principle I's "don't leak business logic beyond what's needed" spirit and add an unnecessary upstream dependency.

---

## Decision 4: No new `PreprocessingDefinition` fields

**Decision**: `excludedFinalShortCodes` already exists as a generic `List<String>` field on `PreprocessingDefinition` (added for `DR-DISQ-001`). The new rule's YAML populates the same field; no Java change to `PreprocessingDefinition` is needed.

**Rationale**: `PreprocessingDefinition` fields are already rule-agnostic (each `CelValidationRule` parses its own YAML into its own `PreprocessingDefinition` instance) — reusing the field name across two unrelated rule YAMLs is exactly how the class is designed to be used, not a coupling risk.

**Alternatives considered**: Adding a differently-named field (e.g. `nonConvictionExcludedShortCodes`) to avoid any appearance of coupling between the two rules' YAML — rejected as unnecessary indirection; the values started out identical per the user's own instruction to reuse existing logic (later diverging by design when `err`, `errf`, `dhd` were added to DR-CONV-006 only — see the Decision 2 update), and a second field name would only invite the two lists to drift apart by accident rather than by an explicit, documented decision.

---

## Decision 5: Rule ID `DR-CONV-006`, priority `4000`

**Decision**: `DR-CONV-006` (new category `CONV`, first rule in category). Priority `4000` — after `DR-SENT-002` (1000), `DR-DISQ-001` (2000), `DR-CTL-001` (3000), and before `DR-YRO-001` (5000).

**Rationale**: Purely advisory (WARNING), evaluated independently of every other rule; ordering has no functional effect, only picks a free slot in the existing sequence.

**Alternatives considered**: None material — rules do not interact.

---

## Decision 6: Preprocessor calls `PreprocessorHelper` directly instead of re-implementing private helpers

**Decision**: `NoConvictionPreprocessor` calls `PreprocessorHelper.upperSet`, `PreprocessorHelper.hasUpperCode`, and `PreprocessorHelper.anyShortCodeIn` rather than declaring its own private static copies.

**Rationale**: Both `CtlMissingPreprocessor` and `DisqualificationExtendedTestPreprocessor` independently re-implement these exact same three helpers as private statics instead of delegating to the already-existing `PreprocessorHelper` (only `CustodialPreprocessor` uses it today). Since this is a brand-new class with no existing behaviour to preserve, there's no reason to copy the duplication forward — this is not a refactor of shipped code, just not repeating an existing wart in new code.

**Alternatives considered**: Matching the exact style of the two existing preprocessors (private static copies) for maximum copy-paste consistency — rejected; `PreprocessorHelper` exists precisely to be reused, and consistency-with-a-known-wart is not a reason to keep duplicating it.

---

## Decision 7: No upstream API change, no OpenAPI contract change in this repo

**Decision**: No new fields, no new endpoints, no response schema change. The new rule adds `WARNING` issues to the existing `DraftValidationResponse` warnings list using fields that already exist.

**Rationale**: Both `OffenceDto.isConvicted` and `ResultLineDto.category` already exist in the currently-consumed version of `api-cp-crime-hearing-results-validator` (confirmed by inspecting the resolved dependency JAR). Unlike `DR-CTL-001`, this feature is not blocked on any upstream work.

---

## Resolved unknowns

| Unknown | Resolution |
|---------|------------|
| Excluded final short-code list | Started identical to `DR-DISQ-002`'s `excludedFinalShortCodes` (confirmed by user instruction to reuse); later extended with `err`, `errf`, `dhd` on DR-CONV-006 only |
| Conviction signal | `OffenceDto.isConvicted`, already present upstream (added for `DR-CTL-001`); no distinction needed between plea- and verdict-driven conviction |
| Offence-code scope | Applies to all offences — no `relevantOffenceCodes` restriction, unlike `DR-DISQ-001` |
| Preprocessor pattern | Per-offence, matching `DisqualificationExtendedTestPreprocessor` / `CtlMissingPreprocessor` |
| CEL expression complexity | Single `unconvictedSentenceCount > 0` check; all conjunctive logic in the preprocessor |
| Upstream blocker | None — both required fields already exist |
