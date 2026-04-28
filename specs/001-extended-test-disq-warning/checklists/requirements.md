# Specification Quality Checklist: Extended Test Disqualification Warning (DD-41656)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-04-25
**Revised**: 2026-04-28 — refinement to gate on `category = 'F'` (supersedes R3 / line-65 edge case)
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

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`.
- Two areas use existing project terminology (`validation_rule` table, severity-ceiling model, `src/main/resources/rules/` location, `POST /api/validation/validate` endpoint, `libs.api.hearing.results.validator` schema). These are *project context* references rather than implementation prescription — the spec describes WHAT the rule does, not HOW it is coded — but reviewers may want to scrub them out if a stricter "no project-internal terms" reading is preferred.
- SC-004's exact reduction percentage is left to the product owner once a baseline is captured. The metric itself (reshare rate attributable to missing extended test disqualification on the five relevant Home Office codes) is concrete; only the target threshold is deferred. This is intentional and documented as an assumption.
- No `[NEEDS CLARIFICATION]` markers were inserted: AC1, AC1A, and the "Logic" section in the source ticket are unambiguous on the five offence codes, the nine excluded final-result codes, the two suppressing disqualification codes, the exact warning text, and the non-blocking severity. Multi-defendant behaviour, case sensitivity, and rule packaging are covered in **Assumptions** with reasonable defaults consistent with the existing DR-SENT-002 rule.

### 2026-04-28 revision — re-validation

- [x] Revision banner clearly identifies what is superseded (line-65 edge case, R3) and links to the BA scenarios doc.
- [x] New User Story 4 (adjournment → no warning) is independently testable and cites the canonical BA scenario.
- [x] FR-002, FR-005 rewritten to gate on `category = 'F'`; FR-012 to FR-015 added for the four-repo contract & plumbing.
- [x] Edge Cases section updated: line-65 edge case explicitly superseded; new edge cases cover missing/malformed `category` (fail-safe), adjournment-only offence, multiple `'F'` lines.
- [x] Key Entities updated to introduce `category` enum and refine "Final result".
- [x] Assumptions updated: contract change is now in scope (was previously out-of-scope); cross-repo branch coordination noted; BA scenarios doc named as authoritative.
- [x] Out of Scope rewritten: `isFinalResult` boolean and runtime reference-data lookup explicitly excluded; the original "no upstream contract change" exclusion explicitly superseded.
- [x] Open question carried into the revision: `cpp-context-hearing` branching strategy (piggyback on DD-41715 vs. open fresh DD-41656 branch) — flagged as "to be confirmed before implementation".
- [x] No new `[NEEDS CLARIFICATION]` markers introduced; all behaviour decisions resolved against the BA scenarios doc.
