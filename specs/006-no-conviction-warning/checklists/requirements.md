# Specification Quality Checklist: No Conviction Warning

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-07-31
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

- No upstream API blocker: unlike the CTL-missing-warning feature (which required a new `convicted`/`existingCtlRecord` field to be added upstream), the offence-level conviction indicator already exists on the offence object as of the version consumed by this service — it was added to support the CTL-missing rule and is reused here as-is.
- The excluded final short-code list (`wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`) was initially carried over unchanged from the extended-test disqualification rule's equivalent list, per the user's explicit instruction to reuse existing logic where possible. It was later extended with `err`, `errf`, `dead` on DR-CONV-006 only — the two rules' YAML lists are configured independently and are not required to stay identical.
- AC1A and AC1B are modelled as two acceptance scenarios under one User Story 2 rather than two separate stories, because from this service's perspective both reduce to the same observable signal (the offence-level conviction indicator becoming true) — the distinction between "guilty plea" and "guilty verdict" as the trigger is a front-end/domain concern upstream of this validator, not something the CEL rule itself needs to distinguish.
