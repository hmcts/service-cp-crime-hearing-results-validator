# Specification Quality Checklist: Custodial Sentence Against Non-Imprisonable Offence – Warning

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-25 (v2 validated 2026-09-28; v3 revised 2026-10-06)
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

All items pass. v3 revision 2026-10-06: approach pivoted from OffenceDto fields to reference-data API lookup (same pattern as DR-SEX-008). `OffenceDto` and `api-cp-crime-hearing-results-validator` are now unchanged. Custodial indicator is resolved via `ReferencedataOffenceClient.getCustodialIndicator(offenceCode)` with client-side precedence logic (column > details_json). spec.md, data-model.md, and contracts/upstream-dependency.md updated accordingly. plan.md and tasks.md require regeneration before implementation.
