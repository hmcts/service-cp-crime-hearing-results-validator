# Research: CTL Missing Warning (DR-CTL-001)

**Branch**: `DD-41663-ctl-missing-warning` | **Date**: 2026-05-06

## Decision 1: Preprocessor pattern — per-offence (matching DR-DISQ-001)

**Decision**: Use a per-offence preprocessor, one `CtlOffenceContext` per offence in the request, mirroring `DisqualificationExtendedTestPreprocessor`.

**Rationale**: The CTL check is independent for each offence — whether a given offence has a remand result, an existing CTL record, a CTL result in the current hearing, and a conviction status are all offence-scoped properties. The per-defendant pattern (DR-SENT-002) is inappropriate because there is no cross-defendant aggregation.

**Alternatives considered**:
- A single aggregate context covering all offences (like CustodialPreprocessor) — rejected because the warning is issued per-offence and a flat aggregate would lose the per-offence scoping that `affectedOffenceSet` needs.

---

## Decision 2: Context record — `CtlOffenceContext` with a single `ctlWarningCount` variable

**Decision**: New record `CtlOffenceContext` exposes one CEL variable: `ctlWarningCount` (0 or 1 per offence context). All four-condition logic (has remand result, no existing CTL, no CTL result, not convicted) is computed in the preprocessor; CEL only tests `ctlWarningCount > 0`.

**Rationale**: Constitution Principle I states business logic must not leak into CEL expressions beyond simple count comparisons. The four-condition conjunction is branching logic that belongs in the preprocessor. Exposing a single computed flag as a count keeps the YAML readable and policy-reviewable.

**Alternatives considered**:
- Exposing four separate boolean counts (`hasRemandCount`, `hasExistingCtlCount`, etc.) and writing `hasRemandCount > 0 && hasExistingCtlCount == 0 && ...` in CEL — rejected because CEL condition complexity is a Constitution violation (Principle I anti-pattern). The preprocessor is the right place for conjunctive logic.

---

## Decision 3: New `remandShortCodes` and `ctlShortCodes` fields in `PreprocessingDefinition`

**Decision**: Add two new fields to `PreprocessingDefinition`:
- `remandShortCodes: List<String>` — configures the set of trigger short codes (`RI`, `RIYDA`, `RIH`, `RIB`, `RILA`, `RILAB`, `REMYD`)
- `ctlShortCodes: List<String>` — configures the short codes that count as a CTL result in the current hearing (initially `CTL`)

**Rationale**: Keeping these code lists in YAML (not hard-wired in Java) preserves the YAML/CEL Rule-First principle. If the policy changes (e.g., a new remand code is introduced), a BA can update the YAML without a Java change. Making it a list rather than a single string for `ctlShortCodes` future-proofs against multiple CTL codes.

**Alternatives considered**:
- Hard-coding the short code lists in the preprocessor — rejected; violates Constitution Principle I (YAML is the contract, non-technical stakeholders must be able to amend it).

---

## Decision 4: Rule ID `DR-CTL-001`, priority `3000`

**Decision**: `DR-CTL-001` (new category `CTL`, first rule in category). Priority `3000` (evaluated after DR-SENT-002 at 1000 and DR-DISQ-001 at 2000; lower urgency than sentencing structure checks).

**Rationale**: The CTL check is advisory (WARNING only) and is conceptually a reminder rather than a structural sentencing error. Running it last is fine; all rules are evaluated independently.

**Alternatives considered**: Interleaving with existing rules — no benefit, rules are independent.

---

## Decision 5: Upstream API change required — two new `OffenceDto` fields

**Decision**: Two Boolean fields must be added to `OffenceDto` in `api-cp-crime-hearing-results-validator` before the Java preprocessor can be implemented:
- `hasExistingCtlRecord: Boolean` — true if a CTL record exists from a prior hearing
- `isConvicted: Boolean` — true if the offence has a guilty plea, finding of guilt, or recorded date of conviction

**Rationale**: Inspection of the v0.1.1 JAR (`javap`) confirmed neither field exists today. The preprocessor cannot implement FR-003 and FR-005 without them. Implementation of the rule in this repo is blocked on a new JAR version.

**Impact on task sequencing**: Task T001 (upstream API change) must complete and the new JAR version must be published and referenced in `gradle.properties` before Java implementation tasks can begin.

**Alternatives considered**:
- Defaulting missing fields to `false` (most conservative: always warns unless the result short code check clears it) — rejected; this would produce false positives for convicted offences and offences with an existing CTL record, breaching SC-002.
- Implementing stub fields in this repo and overriding at the DTO boundary — rejected; DTOs are owned by the upstream repo (Constitution Principle I notes: "changes to those records belong upstream").

---

## Decision 6: No new OpenAPI contract change in this repo

**Decision**: This service exposes no new endpoints and the response schema is unchanged. The new rule adds `WARNING` issues to the existing `DraftValidationResponse` warnings list. No contract documentation update is needed in this repo.

**Rationale**: The existing response structure already accommodates arbitrary warnings. The upstream `DraftValidationResponse` contract (owned by `api-cp-crime-hearing-results-validator`) has no structural change.

---

## Resolved unknowns

| Unknown | Resolution |
|---------|------------|
| CTL result short code | `CTL` (confirmed by user) |
| `existingCtlRecord` field presence | Absent from `OffenceDto` v0.1.1 — upstream change required |
| `convicted` / conviction-status field presence | Absent from `OffenceDto` v0.1.1 — upstream change required |
| Preprocessor pattern (per-offence vs per-defendant) | Per-offence, matching `DisqualificationExtendedTestPreprocessor` |
| CEL expression complexity | Single `ctlWarningCount > 0` check; all logic in preprocessor |
