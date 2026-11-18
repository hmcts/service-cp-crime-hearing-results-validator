# Research: Electronic Monitoring Status Validation (CRA-38)

**Branch**: `dev/CRA-38-electronic-monitoring-status`  
**Date**: 2026-11-18

## Finding 1 — EM flag field on `OffenceDto`

**Decision**: `OffenceDto.hasActiveElectronicMonitoring` (Boolean, nullable)  
**Rationale**: Confirmed in `api-cp-crime-hearing-results-validator/build/generated/.../OffenceDto.java` and in `openapi-spec.yml` ("Active electronic monitoring indicator"). Treat null as false (no EM monitoring active).  
**Alternatives considered**: `emFlag`, `emStatus` — do not exist.

## Finding 2 — Continuing result detection

**Decision**: ELMONDET, EMREQ, EMREQV, EMSO are `promptRef` values on a parent result line. A continuing result exists when `line.getPrompts()` contains a `Prompt` with `promptRef` matching one of those codes (case-sensitive) AND `promptValue.equalsIgnoreCase("Yes")`.  
**Rationale**: The Prompt model is used for Yes/No and date values alongside a result line. The spec says the parent result "includes" these child results — consistent with prompts being structured data on the parent.  
**Code reference**: `PreprocessorHelper.hasPromptRef(line, promptRef)` for the promptRef check; extend with promptValue check.

## Finding 3 — Ending result (EMONE)

**Decision**: EMONE is a top-level `ResultLineDto.shortCode`. Use `anyShortCodeIn(lines, Set.of("EMONE"))`.  
**Code reference**: `CelValidationRuleScenarioTest.java:372` — `resultLine("rl3", "EMONE", "d1", "off3")`.

## Finding 4 — Exempt main result (NHMC, NHCC, A, ADJNN, ADJAPP)

**Decision**: An offence has an exempt main result if: (a) no category-F result line exists, AND (b) every category-I result line has a shortCode in the exempt set, AND (c) at least one category-I result with an exempt shortCode exists.  
**Rationale**: `ResultLineDto.CategoryEnum`: F=Final, I=Intermediary, A=Ancillary. These adjournment codes are category I. AC3 fires when RC (category I, NOT in exempt set) is present alongside NHMC — the RC disqualifies the "exempt" status.

## Finding 5 — Ancillary-only

**Decision**: Ancillary-only = no category-F or category-I result lines on the offence (all are category A, or no results).

## Finding 6 — Defendant-name suffix

**Decision**: Use `${defendantNames}` placeholder in `errorMessageTemplate`. `MessageTemplateResolver.resolveDefendantNames()` handles single/double/triple+ formatting.  
**Code reference**: `MessageTemplateResolver.java` — `resolveDefendantNames(template, names, hearingDefendantCount)`.  
**Bug found and fixed (2026-11-18)**: The original `formatDefendantNames` implementation produced "A, B and C" for 3+ names (Oxford-comma style). The spec (AC4) requires pure comma-separated output for 3+ defendants: "A, B, C". The method was corrected to use `String.join(", ", names)` for lists of three or more, reserving the " and " join exclusively for the two-name case. All affected tests across the codebase were updated to match the corrected behaviour.

## Finding 7 — Next DB migration file

**Decision**: `V1.019__insert_dr_emon_017.sql`.  
**Rationale**: Confirmed with user; V1.015–V1.018 are on other feature branches.

## Finding 8 — `PreprocessorHelper` utilities available

Reusable methods:
- `groupResultsByOffence(request)` → `Map<String, List<ResultLineDto>>`
- `buildDefendantNames(request)` → `Map<String, String>` (defendantId → displayName)
- `anyShortCodeIn(lines, codeSet)` → boolean
- `hasPromptRef(line, promptRef)` → boolean

## Finding 9 — Preprocessor context pattern

Closest model: `ConditionalBailContext` (per-defendant, with count + offence-id list). The new `ElectronicMonitoringContext` follows the same pattern with `unresolvedEmCount` and `unresolvedOffenceIds`.

## Finding 10 — PreprocessingDefinition YAML fields

The `PreprocessingDefinition` record does NOT yet have `continuingEmPromptRefs`, `endingEmShortCodes`, or `exemptMainResultShortCodes` fields. These will need to be added to `PreprocessingDefinition.java`. Alternatively, these can be hardcoded in the preprocessor and driven by YAML lists mapped to new fields — the YAML approach (Principle I) is preferred so a BA can update them without Java code changes. New optional list fields will be added to `PreprocessingDefinition`.

## Open Questions Resolved

All questions resolved. No NEEDS CLARIFICATION items remain.
