# Specification Analysis Metrics — DR-BAIL-015 / CRA-363

**Generated**: 2026-10-29  
**Last updated**: 2026-11-05 (PR review gaps resolved — all 16 pre-release codes now exhaustively covered against every triggering parent type)  
**Spec folder**: `specs/016-pre-release-bail-condition-error`  
**Branch**: `CRA-363-pre-release-bail-error`

## Metrics

| Metric | Value |
|--------|-------|
| Total Functional Requirements | 20 |
| Total Success Criteria (buildable) | 8 |
| Total Tasks | 43 (T001–T040 + T041–T043 from PR review) |
| Completed Tasks | 43 [X] |
| Pending Tasks | 0 (T040 — Welsh translations confirmed not required, closed) |
| Requirements with ≥1 task | 20/20 (100%) |
| AC coverage — all 16 pre-release codes × all parent types | 100% ✅ |
| API test AC coverage | 11/11 backend-testable ACs (100%) ✅ |
| Ambiguity findings | 0 (resolved) |
| Duplication findings | 0 |
| Inconsistency findings | 0 (resolved) |
| Coverage gap findings | 3 (resolved — PR review 2026-11-05) |
| Critical Issues | 0 |
| High Issues | 0 (was 2 — resolved) |
| Medium Issues | 0 (was 5 — resolved) |
| Low Issues | 0 |

## Resolved Issues

| ID | Severity | Summary | Resolution |
|----|----------|---------|------------|
| E1 | HIGH | `PreReleaseBailConditionApiHttpLiveTest.java` missing AC3, AC4, AC4A, AC5A, AC7 | Added 5 `@Nested` test classes: `Ac3MultiDefendantNames`, `Ac4MixedAdultYouth`, `Ac4aMultipleParentTypes`, `Ac5aAlongsideOtherErrors`, `Ac7ErrorCleared` |
| E2 | HIGH | `CRA-363.http` missing AC5A section | Added `### AC5A` section with RC+PREBC (d1) + concurrent/consecutive IMP (d2) payload |
| F1 | MEDIUM | T034 and T036 marked [X] but implementations incomplete | T034 description updated to enumerate all 11 ACs; implementation now matches description |
| A1 | MEDIUM | T034 scope description omitted AC3, AC4A, AC5A, AC7 | T034 description rewritten to list all 11 backend-testable ACs explicitly |
| G1 | MEDIUM | AC2 — all 16 pre-release codes tested with adult+RC only; youth path covered only for PRBCS1+REMCBY | Added `allPreReleaseCodes_withRemcbyParentAndYouth_shouldRaiseErrorB` (`@ParameterizedTest`, all 16 codes × REMCBY+youth) in `Ac2YouthRemandBail` |
| G2 | MEDIUM | AC2A — only 3 of 16 pre-release codes (PRBCS1, PRBCS2, PRBCSE2) tested with CCIC parent | Added `allPreReleaseCodes_withCcicParent_shouldRaiseErrorD` (`@ParameterizedTest`, all 16 codes × CCIC) in `Ac2aCcic` |
| G3 | MEDIUM | AC2B — only 2 of 16 pre-release codes (PRBCT, PRBCS3) tested with CCSC parent | Added `allPreReleaseCodes_withCcscParent_shouldRaiseErrorE` (`@ParameterizedTest`, all 16 codes × CCSC) in `Ac2bCcsc` |

## AC Coverage Summary

| AC | IT Test | All 16 codes × all parents | API Live Test | .http file |
|----|---------|---------------------------|---------------|------------|
| AC1 | ✅ | ✅ 16 codes × RC (adult) | ✅ | ✅ |
| AC2 | ✅ | ✅ 16 codes × REMCBY (youth) | ✅ | ✅ |
| AC2A | ✅ | ✅ 16 codes × CCIC | ✅ | ✅ |
| AC2B | ✅ | ✅ 16 codes × CCSC | ✅ | ✅ |
| AC3 | ✅ | n/a | ✅ | ✅ |
| AC4 | ✅ | n/a | ✅ | ✅ |
| AC4A | ✅ | n/a | ✅ | ✅ |
| AC5A | ✅ | n/a | ✅ | ✅ |
| AC7 | ✅ | n/a | ✅ | ✅ |
| AC8 | ✅ | n/a | ✅ | ✅ |
| AC9 | ✅ | n/a | ✅ | ✅ |
