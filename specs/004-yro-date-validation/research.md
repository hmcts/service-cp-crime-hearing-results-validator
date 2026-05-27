# Research: YRO Date Validation (DR-YRO-001)

**Phase 0 — All decisions resolved. No NEEDS CLARIFICATION markers remain.**

---

## Decision 1: Preprocessor reuse vs. new preprocessor

**Decision**: Reuse the existing `community-order-end-date` preprocessor type (`CommunityOrderEndDatePreprocessor`) with YRO short codes declared in the new YAML file. No new Java code is required.

**Rationale**:
`CommunityOrderEndDatePreprocessor` is fully code-list driven — it reads `communityOrderShortCodes`, `curfewShortCodes`, `curfewTagShortCodes`, `furtherCurfewShortCodes`, `alcoholAbstinenceShortCodes`, and `unpaidWorkShortCodes` from `PreprocessingDefinition` (which is populated directly from YAML). The prompt ref keys (`endDate`, `endDateOfTagging`) are stable API-contract values from `api-cp-crime-hearing-results-validator` and match the fields on YRC1, YRC2, and YRC3 requirement result lines. The YRO short codes map cleanly onto the existing config fields:

| YAML field | Community order codes | YRO codes |
|---|---|---|
| `communityOrderShortCodes` | COEW, COS, CONI | YROEW, YRONI, YROFEW, YROISS, YROINI |
| `curfewShortCodes` | CUR | YRC2 |
| `curfewTagShortCodes` | CURE | YRC1 |
| `furtherCurfewShortCodes` | CURA | YRC3 |
| `alcoholAbstinenceShortCodes` | AAR | *(none — not applicable to YRO)* |
| `unpaidWorkShortCodes` | UPWR | YRUP1 |

The `CommunityOrderContext` variables (`curViolationCount`, `cureViolationCount`, `curaViolationCount`, `aarViolationCount`, `upwrViolationCount`) carry the violation counts; the YAML CEL conditions reference these variable names directly. `aarViolationCount` will always be 0 for the YRO rule (no `alcoholAbstinenceShortCodes` configured), which is correct — YRO has no Alcohol Abstinence and Monitoring Requirement equivalent.

**Alternatives considered**:
- *New `YroEndDatePreprocessor` and `YroContext`* — rejected. Would duplicate ~280 lines of logic with zero behavioral difference; violates Constitution Principle I (adding a new rule must not require Java if an existing preprocessor fits) and Constitution Principle III (pluggable preprocessors must be generic).

---

## Decision 2: Rule ID

**Decision**: `DR-YRO-001`

**Rationale**: Follows the existing `DR-<CATEGORY>-<NNN>` naming convention (DR-COEW-001, DR-CTL-001, DR-DISQ-001, DR-SENT-002). YRO is the natural category acronym for Youth Rehabilitation Order rules. 001 is the first rule in this category. AC2 and AC3 are *conditions within* the rule, not separate rules.

**Alternatives considered**:
- Separate `DR-YRO-002` for AC3 — rejected. Both ACs share the same preprocessor context and concern the same parent order (YRO). Splitting into two YAML files would duplicate the preprocessing config and produce disjoint rule IDs for the same order type.

---

## Decision 3: Condition structure (3 separate AC2 conditions)

**Decision**: Four conditions — `AC2a` (YRC2), `AC2b` (YRC1), `AC2c` (YRC3), `AC3` (YRUP1) — mirroring the DR-COEW-001 pattern.

**Rationale**: Each curfew requirement type has a distinct display name that must appear in the error message ("Youth Rehabilitation Requirement: Curfew" vs "…Curfew with electronic monitoring" vs "…Further curfew requirement made"). A single combined CEL condition (`(curViolationCount + cureViolationCount + curaViolationCount) > 0`) cannot produce per-requirement error messages. Separate conditions fire independently, allowing each to scope its `affectedOffenceSet` and message template to the specific breaching requirement.

**Alternatives considered**:
- Single AC2 condition with combined CEL — rejected (see above).

---

## Decision 4: AC3 date boundary

**Decision**: Reuse the existing `hearingDay.plusMonths(12).minusDays(1)` boundary already implemented in `CommunityOrderEndDatePreprocessor`. The error fires when `orderEndDate.isBefore(minEndDate)`.

**Rationale**: The user's acceptance criterion example is consistent with this logic:
- Hearing date: 20/05/2026
- `minEndDate` = 20/05/2027 − 1 day = 19/05/2027
- End date 18/05/2027: `isBefore(19/05/2027)` → **ERROR** ✓

The minimum valid end date is therefore `hearingDay + 12 months − 1 day` (not `hearingDay + 12 months`). This is a deliberate business rule nuance shared with the community order rule.

**Alternatives considered**: No alternative calculation considered — the preprocessor already implements this and the user's example validates it.

---

## Decision 5: Rule priority

**Decision**: Priority `5000` (community order DR-COEW-001 is `4000`).

**Rationale**: YRO rules are a distinct order category evaluated after community order rules. Priority 5000 places DR-YRO-001 next in the evaluation chain and leaves gap for future YRO rules or interleaved rules.

---

## Decision 6: No new Java source files

**Decision**: This feature delivers one new file only: `src/main/resources/rules/DR-YRO-001.yaml`. The integration test (`YroDateValidationRuleIntegrationTest.java`) is also new but is a test class, not a production class.

**Rationale**: Full compliance with Constitution Principle I. The preprocessor, context record, rule runner, registry, and auto-configuration all handle DR-YRO-001 transparently. `ValidationRuleAutoConfiguration` discovers the new YAML file at startup via `classpath*:rules/DR-*.yaml` — no registration step needed.

---

## Prompt ref keys (stable API-contract values)

The following prompt refs are used by `CommunityOrderEndDatePreprocessor` and apply equally to YRO requirement result lines:

| Prompt ref | Used for | YRO requirement |
|---|---|---|
| `endDate` | Order end date (YRO parent) | All YRO codes |
| `endDate` | Requirement end date | YRC2, YRC3 |
| `endDateOfTagging` | End date of tag | YRC1 |

These are intentionally hardcoded in the preprocessor (not YAML-configurable) because they are stable API-contract values from `api-cp-crime-hearing-results-validator`.
