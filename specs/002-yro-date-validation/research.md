# Research: YRO Date Validation (DR-YRO-001)

**Phase 0 — All decisions resolved. No NEEDS CLARIFICATION markers remain.**

---

## Decision 1: Preprocessor reuse vs. new preprocessor

> **⚠️ Superseded during implementation.** This decision (reuse `community-order-end-date`, no new
> Java) was reversed once **AC1 (YRO end date must be in the future)** was added — the community-order
> preprocessor has no future-date check. A dedicated `youth-rehabilitation-order` preprocessor
> (`YouthRehabilitationPreprocessor`) and `YouthRehabilitationContext` were introduced, and the logic
> the two preprocessors would otherwise duplicate was extracted into the shared `PreprocessorHelper`
> (see Decision 7). The original rationale below is retained for history.

**Original decision**: Reuse the existing `community-order-end-date` preprocessor type (`CommunityOrderEndDatePreprocessor`) with YRO short codes declared in the new YAML file. No new Java code is required.

**Rationale**:
`CommunityOrderEndDatePreprocessor` is fully code-list driven — it reads `communityOrderShortCodes`, `curfewShortCodes`, `curfewTagShortCodes`, `furtherCurfewShortCodes`, `alcoholAbstinenceShortCodes`, and `unpaidWorkShortCodes` from `PreprocessingDefinition` (which is populated directly from YAML). The prompt ref keys (`endDate`, `endDateOfTagging`) are stable API-contract values from `api-cp-crime-hearing-results-validator` and match the fields on YRC1, YRC2, and YRC3 requirement result lines. The YRO short codes map cleanly onto the existing config fields:

| YAML field | Community order codes | YRO codes |
|---|---|---|
| `communityOrderShortCodes` | COEW, COS, CONI | YROEW, YRONI, YROFEW, YROISS, YROINI |
| `curfewShortCodes` | CUR | YRC2 |
| `curfewTagShortCodes` | CURE | YRC1 |
| `furtherCurfewShortCodes` | CURA | YRC3 |

The `YouthRehabilitationContext` variables (`pastEndDateCount`, `curViolationCount`, `cureViolationCount`, `curaViolationCount`) carry the violation counts; the YAML CEL conditions reference these variable names directly.

**Alternatives considered**:
- *New `YroEndDatePreprocessor` and `YroContext`* — rejected. Would duplicate ~280 lines of logic with zero behavioral difference; violates Constitution Principle I (adding a new rule must not require Java if an existing preprocessor fits) and Constitution Principle III (pluggable preprocessors must be generic).

---

## Decision 2: Rule ID

**Decision**: `DR-YRO-001`

**Rationale**: Follows the existing `DR-<CATEGORY>-<NNN>` naming convention (DR-COEW-001, DR-CTL-001, DR-DISQ-001, DR-SENT-002). YRO is the natural category acronym for Youth Rehabilitation Order rules. 001 is the first rule in this category. AC1 and AC2 are *conditions within* the rule, not separate rules.

---

## Decision 3: Condition structure (separate per-requirement conditions)

**Decision**: Four conditions — `AC1` (past end date), `AC2a` (YRC2), `AC2b` (YRC1), `AC2c` (YRC3) — mirroring the DR-COEW-001 pattern. AC1 was added after the original plan.

**Rationale**: Each curfew requirement type has a distinct display name that must appear in the error message ("Youth Rehabilitation Requirement: Curfew" vs "…Curfew with electronic monitoring" vs "…Further curfew requirement made"). A single combined CEL condition (`(curViolationCount + cureViolationCount + curaViolationCount) > 0`) cannot produce per-requirement error messages. Separate conditions fire independently, allowing each to scope its `affectedOffenceSet` and message template to the specific breaching requirement.

**Alternatives considered**:
- Single AC2 condition with combined CEL — rejected (see above).

---

## Decision 4: Rule priority

**Decision**: Priority `5000` (community order DR-COEW-001 is `4000`).

**Rationale**: YRO rules are a distinct order category evaluated after community order rules. Priority 5000 places DR-YRO-001 next in the evaluation chain and leaves gap for future YRO rules or interleaved rules.

---

## Decision 5: New Java source files (revised)

> **⚠️ Revised during implementation.** The original plan delivered only `DR-YRO-001.yaml`. Adding AC1
> required Java, so this feature delivers: `YouthRehabilitationPreprocessor`, `YouthRehabilitationContext`,
> and the shared `PreprocessorHelper` (production), plus `YroEndDateValidationIntegrationTest`,
> `YouthRehabilitationPreprocessorTest`, `YouthRehabilitationContextTest`, and `PreprocessorHelperTest`
> (tests). `ValidationRuleAutoConfiguration` still discovers the YAML at startup and the new preprocessor
> registers via `PreprocessorRegistry` on `preprocessing.type: "youth-rehabilitation-order"`.

## Decision 6: Share duplicated preprocessor logic via `PreprocessorHelper`

**Decision**: Extract the helpers duplicated across preprocessors — short-code normalisation (`upperSet`/`upperOrNull`), matching (`hasUpperCode`/`anyShortCodeIn`), result-line grouping (`groupByDefendant`/`groupResultsByOffence`), defendant-name assembly (`buildDefendantNames`/`buildFullName`), and prompt-date handling (`parsePromptDate`/`isRequirementViolated`) — into a stateless static utility `PreprocessorHelper`, and migrate all five preprocessors (COEW, YRO, Custodial, Disqualification, CtlMissing) to use it.

**Rationale**: `YouthRehabilitationPreprocessor` would otherwise be a ~95% copy of `CommunityOrderEndDatePreprocessor`, and the same helpers were independently duplicated in the custodial/disqualification/CTL preprocessors. A static utility (mirroring the `SeverityCeiling` precedent) keeps the preprocessors' no-arg constructors intact (no DI churn in tests) while removing the duplication. Each preprocessor retains only its distinct orchestration and context shape.

---

## Prompt ref keys (stable API-contract values)

The following prompt refs are used by `CommunityOrderEndDatePreprocessor` and apply equally to YRO requirement result lines:

| Prompt ref | Used for | YRO requirement |
|---|---|---|
| `endDate` | Order end date (YRO parent) | All YRO codes |
| `endDate` | Requirement end date | YRC2, YRC3 |
| `endDateOfTagging` | End date of tag | YRC1 |

These are intentionally hardcoded in the preprocessor (not YAML-configurable) because they are stable API-contract values from `api-cp-crime-hearing-results-validator`.
