# Specification Quality Checklist: Non-Endorsable Offence Warning

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-20
**Updated**: 2026-10-26
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
- [x] All acceptance scenarios are defined (AC1a, AC1b, AC1c, AC2, AC3, AC4, AC5, AC6)
- [x] Edge cases are identified (fail-open, absent flag, result removal, cache safety)
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## AC Coverage Matrix (from Jira CRA-337)

| AC | Description | Unit | Integration | API Live | CRA-337.http |
|----|-------------|------|-------------|----------|--------------|
| AC1a | Endorsement (LEP/LEN/LEA) on non-endorsable → WARNING | ✅ | ✅ | ✅ | ✅ |
| AC1b | Disqualification trigger on non-endorsable → WARNING | ✅ (DDO, DDRCOT) | ✅ (DDO, DDRCOT) | ✅ (DDO, fixed T016) | ⚠️ stale DR-END-015 comment |
| AC1c | Both families on same offence → two DR-END-014 warnings | ✅ | ✅ | ✅ (fixed T016) | ⚠️ stale DR-END-014+015 comment |
| AC2 | Trigger on endorsable offence → no warning | ✅ | ✅ (both families) | ✅ (endorsement) | ✅ |
| AC3 | Non-endorsable disq (DDRN family) → no warning | ✅ (DDRN) | ✅ (DDRN, DDRVL) | ✅ (DDRN, T025) | ✅ (DDRN) |
| AC4 | Warning clears when trigger removed (stateless) | ✅ implicit | ✅ implicit | ✅ (IMP note, T024) | ✅ (T024) |
| AC5 | Multiple offences assessed independently | ✅ | ✅ (both families) | ✅ (endorsement, T026) | ✅ (T024) |
| AC6 | Coexistence with other rules' warnings | N/A | ✅ (DR-SENT-001) | ✅ (IT reference note, T024) | ✅ (T024) |

## Outstanding Actions

- [x] **T016 DONE**: Fixed `NonEndorsableOffenceApiHttpLiveTest.java` — removed `RULE_ID_015`, fixed
  disqualification and both-families assertions to use `RULE_ID_014`; verified `gradle api` green
  (83/83 tests pass). Added RT88026 → endorsableFlag:true to WireMock stub to fix collision with
  DisqualificationExtendedTestApiHttpLiveTest.

- [x] **Fix CRA-337.http stale DR-END-015 references**: Removed; file now references DR-END-014 only (T024).

- [x] **Fix CRA-337.http offence codes**: RT88026 replaced with `live-ne-end-trigger`, `live-ne-disq-trigger`,
  `live-ne-both-off`, `live-ne-endorsable` throughout (T024).

- [x] **Add AC3 to API live test**: `NonEndorsableDiqFamilyClear` nested class added with
  `ddrnOnNonEndorsableOffence_shouldNotRaiseAnyWarning()` — PASSED in `gradle api` 85/85 (T025).

- [x] **Add AC5 to API live test**: `MultiOffenceIndependence` nested class added with
  `lepOnNonEndorsableAndEndorsableOffence_shouldWarnOnlyForNonEndorsable()` — PASSED in `gradle api` 85/85 (T026).

- [x] **Add AC4, AC5, AC6 sections to CRA-337.http**: All sections added (T024).

## Notes

- Disqualification trigger codes: unit and integration tests cover DDO and DDRCOT only; remaining 14
  codes (DDOL, DDOR, DDOTE, DDOTEL, DDDL, DDDT, DDDTL, DDDTO, DDPL, DDPR, DDPTE, DDPTEL) are
  covered by config-list correctness rather than per-code test cases. Acceptable — the YAML
  configures the trigger list and the unit test verifies the config is read and applied correctly.
- Non-endorsable disqualification codes: DDRN and DDRVL tested; DDRNL, DDRA, DDRAL, DDRV rely on
  config-list coverage. Same reasoning applies.
- RT88026 (Dangerous Driving) is a genuinely endorsable RTA offence. It was added to the WireMock
  endorsable stub to prevent DR-DISQ-002 live tests from falsely triggering DR-END-014.
