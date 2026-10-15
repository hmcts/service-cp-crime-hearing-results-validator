# Specification Quality Checklist: Application Results Recorded Against Offences – ERROR Validation

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-16
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

- Validation run 1 (2026-09-16): all items pass. Two points resolved during drafting rather than
  raised as clarifications:
  - **"This affects:" trigger** — AC1/AC2 both condition the line on the hearing having more than
    one defendant, not on more than one defendant *breaching*. Recorded as an assumption and as
    FR-005 / User Story 1 scenarios 3–4.
  - **UI vs. validation boundary** — remaining on Enter Results, hiding the Share button, and
    suppressing errors on Manage Hearings are consuming-UI behaviours. They are kept in the
    acceptance scenarios (they are the user-visible ACs) but the split of responsibility is stated
    explicitly in Assumptions so planning does not mis-scope them into this service.
- The result label (not the code) is what appears in both message templates — see FR-004 and FR-006.
- Per-rule runtime-override / severity-ceiling coverage is intentionally out of scope (proven once
  framework-level); see the last Assumption and `.claude/rules/design_rules.md`.
