# Research: Community Order End Date Validation

**Branch**: `DD-41653-co-end-date-validation` | **Date**: 2026-05-12  
**Feature**: [spec.md](spec.md)

## Decision Log

### D-001: Parent-Child Result Line Relationship

**Question**: How do we identify which requirement result lines (CUR, CURE, CURA, AAR, UPWR) belong to a specific community order result line (COEW/COS/CONI)?

**Decision**: Group by `(defendantId, offenceId)`. All result lines sharing the same `offenceId` and `defendantId` within a `DraftValidationRequest` are implicitly associated. A community order and all its child requirements are recorded on the same offence for the same defendant.

**Rationale**: `ResultLineDto` has no `parentResultLineId` field (confirmed by inspecting the generated class at `api-cp-crime-hearing-results-validator/build/generated/src/main/java/uk/gov/hmcts/cp/openapi/model/ResultLineDto.java`). The domain model only groups by `(defendantId, offenceId)`. This matches how the existing `CustodialPreprocessor` groups custodial lines.

**Alternatives considered**: Adding a `parentResultLineId` to `ResultLineDto` (upstream DTO change required; introduces coupling and breaks backward compatibility; rejected).

---

### D-002: Requirement-Specific Date Field Names

**Question**: The spec references "End date of tag" (CURE) and "Until" (AAR). Do distinct date fields exist on `ResultLineDto` for these?

**Decision**: All result lines — parent community orders and child requirements — use the single `endDate: LocalDate` field. The UI labels "End date", "End date of tag", and "Until" are display names for the same data field mapped by result type. The preprocessor reads `resultLine.getEndDate()` for all types.

**Rationale**: `ResultLineDto` has only one date field (`endDate`). `endDateOfTag` and `until` do NOT exist in the generated DTO (confirmed by source inspection). No upstream DTO change is required.

**Alternatives considered**: Requesting upstream DTO change to add `endDateOfTag` / `until` fields (unnecessary, adds complexity, rejected).

---

### D-003: Single Rule vs Two Separate Rules

**Question**: Should AC2 and AC3 be one YAML rule file or two separate files?

**Decision**: **One YAML rule file** (`DR-COEW-001.yaml`) with five conditions — four for AC2 (one per requirement type: CUR, CURE, CURA, AAR), and one for AC3.

**Rationale**: All five conditions share one preprocessor computation. A single rule allows atomic enable/disable via the `validation_rule` DB table. Splitting into two rules would cause the same preprocessor to run twice per request with the same result. The existing `DR-SENT-002.yaml` also bundles multiple conditions (3) in one file; this is precedent.

**Alternatives considered**: Two separate rules (DR-COEW-001/002) for independent DB override control — rejected because the ability to disable requirement-date checking separately from the UPWR 12-month check is not a stated requirement, and premature splitting adds management overhead.

---

### D-004: Context Grouping Strategy (Per-Offence vs Per-Defendant)

**Question**: Should `CommunityOrderContext` group by offence or by defendant?

**Decision**: **Per-offence-per-defendant** — one context entry per `(defendantId, offenceId)` pair that has a community order result line. The map key is `defendantId + "_" + offenceId`.

**Rationale**: A community order is a sentence on a specific offence. Date comparisons are between the order's end date and its requirements' end dates — all on the same offence. Grouping per-defendant and folding multiple orders together would make date comparisons ambiguous (which order's end date vs which requirement's end date?). Per-offence keeps each comparison self-contained.

**Alternatives considered**: Per-defendant (like `CustodialPreprocessor`) — rejected because community order end-date rules compare dates within a single offence, not across all offences for a defendant.

---

### D-005: Handling Missing End Dates

**Question**: What should the preprocessor do if a community order result line has a null `endDate`?

**Decision**: **Skip silently**. If `endDate` is null on a COEW/COS/CONI result line, no `CommunityOrderContext` is generated for that result line. If `endDate` is null on a requirement line, that requirement is excluded from AC2 date comparison.

**Rationale**: The spec states "these three validation rules apply only when an end date is provided; absence of an end date is handled by mandatory-field validation (out of scope for this feature)." Null end dates are a separate validation concern; this rule should not fire on them.

---

### D-006: "12 Months" Boundary Interpretation

**Question**: Is an end date of exactly `hearingDate.plusMonths(12)` acceptable for AC3, or must it be strictly after?

**Decision**: **Inclusive** — `orderEndDate >= hearingDate.plusMonths(12)` passes AC3. The violation condition is `orderEndDate.isBefore(hearingDate.plusMonths(12))` which evaluates to false for equality.

**Rationale**: The spec states "end date before 12 months - 1 day from the hearing date" is a violation, and "end date of exactly hearing-date + 12 months is acceptable". This means strict `<`, not `<=`.

---

### D-007: Rule Priority

**Question**: What priority number should DR-COEW-001 use?

**Decision**: **Priority 4000** — runs after the three existing rules (SENT=1000, DISQ=2000, CTL=3000).

**Rationale**: Lower numbers run first in the rule chain. Community order end-date validation is independent of the other rules; no ordering dependency exists. Using 4000 places it last, consistent with "new rules go to the back" convention.

---

## Confirmed Existing Infrastructure

| Component | Status | Notes |
|-----------|--------|-------|
| `DraftValidationRequest.hearingDay` | ✓ Available | `LocalDate`, required field |
| `ResultLineDto.endDate` | ✓ Available | `LocalDate`, optional (null for non-order lines) |
| `ResultLineDto.defendantId` | ✓ Available | Required, used for grouping |
| `ResultLineDto.offenceId` | ✓ Available | Required, used for grouping |
| `ResultLineDto.shortCode` | ✓ Available | Required, used for filtering |
| Preprocessor registry dispatch | ✓ Works | Add `@Component` with qualifier via `type()` |
| CEL expression evaluator | ✓ Works | Supports Long variable comparisons |
| Message template resolver | ✓ Works | Supports `${defendantName}`, `${offenceNumbers}` |
| Severity ceiling (DB override) | ✓ Works | Framework-level, not rule-specific |
| Parent-child linking | N/A | Inferred from shared (defendantId, offenceId) |

## Files Referenced

- `api-cp-crime-hearing-results-validator/build/generated/src/main/java/uk/gov/hmcts/cp/openapi/model/ResultLineDto.java`
- `src/main/java/uk/gov/hmcts/cp/services/rules/cel/CustodialPreprocessor.java`
- `src/main/java/uk/gov/hmcts/cp/services/rules/cel/PreprocessingDefinition.java`
- `src/main/java/uk/gov/hmcts/cp/services/rules/cel/RuleEvaluationContext.java`
- `src/main/resources/rules/DR-SENT-002.yaml`
- `src/main/resources/rules/DR-CTL-001.yaml`
- `src/main/resources/rules/DR-DISQ-001.yaml`
