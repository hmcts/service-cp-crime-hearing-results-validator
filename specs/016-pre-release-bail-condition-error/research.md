# Research: Pre-Release Bail Condition Error (DR-BAIL-015)

## R1 — Parent/child concept in ResultLineDto

**Decision**: There is no explicit parent/child field in `ResultLineDto`. The rule fires when:
- ANY result line on the offence has a shortCode in the conditional-bail-parent set, AND
- ANY result line on the SAME offence has a shortCode in the pre-release-bail-condition set.

**Rationale**: Confirmed by reading all existing preprocessors. None use a parent/child selector. The "parent" in the Jira spec is a UI concept only; at the data level all result lines on an offence are peers in `request.getResultLines()`.

**Alternatives considered**: Checking `ResultLineDto.getCategory()` — rejected; category distinguishes final/non-final disposal types but not the parent/child relationship described in the spec.

---

## R2 — Mixed-hearing summary consolidation (Error C when adult AND youth both affected)

**Decision**: No new framework mechanism needed. The existing `DefaultValidationService.evaluateRulesWithMdc()` already buckets page-level error messages by `ruleId::errorMessage` (line 106–108). Two separate conditions that both emit the same `errorMessageTemplate` text (Error C) will automatically merge into one summary entry, with defendant names accumulated across all triggered contexts.

**How to implement**:
- Condition AC_MIXED_ADULT (adult offence in mixed hearing): `messageTemplate` = Error A, `errorMessageTemplate` = Error C.
- Condition AC_MIXED_YOUTH (youth offence in mixed hearing): `messageTemplate` = Error B, `errorMessageTemplate` = Error C.
- Both produce identical Error C text as `errorMessageTemplate` → one `DR-BAIL-015::Error C` key in the bucket → one summary entry with all affected defendant names appended.

**Rationale**: The same mechanism already drives the DR-APP-009 consolidation pattern (`consolidatePageLevelError`); our case is simpler because we want identical Error C text verbatim without a calculated-value placeholder.

---

## R3 — Existing ConditionalBailPreprocessor and ConditionalBailContext

**Decision**: Do NOT reuse. `ConditionalBailPreprocessor` (qualifier `conditional-bail-urgent-check`) is for DR-URG-008 (urgent-result missing on a bail-ended offence in Crown Court only). Its context shape, filtering logic, and CROWN-only guard are all wrong for DR-BAIL-015.

**New components required**: `PreReleaseBailConditionPreprocessor` and `PreReleaseBailContext`.

---

## R4 — Age determination

**Decision**: Use `DefendantDto.getDateOfBirth()` vs `DraftValidationRequest.getHearingDay()`, following `AgeRestrictedImprisonmentPreprocessor.isUnder21()` (line 100–103). Adult = ≥18. Youth = <18. No DOB = `hasDob == 0`.

**No-DOB rule**: When `dateOfBirth` is null, both `isAdult` and `isYouth` are 0, `hasDob` is 0. This causes AC_NODOB_REMAND to fire → Error C inline and page-level.

---

## R5 — Hearing-wide flags (hearingHasRemandAdult / hearingHasRemandYouth)

**Decision**: Two-pass algorithm in the preprocessor:
1. First pass — scan all result lines to find triggered offences (conditional bail parent + pre-release child on same offence). For each triggered offence, record parent type and defendant age.
2. Compute hearing-wide flags: `hearingHasRemandAdult` = any triggered offence has remand-bail parent AND isAdult==1. `hearingHasRemandYouth` = any triggered offence has remand-bail parent AND isYouth==1.
3. Second pass — emit one `PreReleaseBailContext` per triggered offence with all flags baked in.

**Rationale**: CEL expressions are evaluated per-context and cannot read across contexts. Hearing-wide aggregates must be pre-computed in the preprocessor.

---

## R6 — PreprocessingDefinition extension

**Decision**: Add four new fields to `PreprocessingDefinition` record (following the established pattern from every rule added after DR-COEW-005):
- `List<String> remandConditionalBailShortCodes` — the 6 remand-type parent codes
- `List<String> ccicShortCodes` — single-entry list [CCIC]
- `List<String> ccscShortCodes` — single-entry list [CCSC]
- `List<String> preReleaseBailConditionShortCodes` — the 16 pre-release condition codes

All four are YAML-configurable in `DR-BAIL-015.yaml`, satisfying Constitution Principle I (YAML/CEL Rule-First).

---

## R7 — SQL migration numbering

**Decision**: `V1.017__pre_release_bail_condition_error.sql` — next available after `V1.012__insert_dr_restrao_010.sql`. Inserts `('DR-BAIL-015', true, 'ERROR', now(), 'system')`.

---

## R8 — CEL expression design (7 conditions)

| Condition ID | CEL expression | messageTemplate (inline) | errorMessageTemplate (page-level) |
|---|---|---|---|
| AC_ADULT      | `isRemandBail == 1 && isAdult == 1 && hasDob == 1 && hearingHasRemandYouth == 0` | Error A | Error A |
| AC_YOUTH      | `isRemandBail == 1 && isYouth == 1 && hasDob == 1 && hearingHasRemandAdult == 0` | Error B | Error B |
| AC_MIXED_ADULT | `isRemandBail == 1 && isAdult == 1 && hasDob == 1 && hearingHasRemandYouth == 1` | Error A | Error C |
| AC_MIXED_YOUTH | `isRemandBail == 1 && isYouth == 1 && hasDob == 1 && hearingHasRemandAdult == 1` | Error B | Error C |
| AC_NODOB      | `isRemandBail == 1 && hasDob == 0` | Error C | Error C |
| AC_CCIC       | `isCcic == 1` | Error D | Error D |
| AC_CCSC       | `isCcsc == 1` | Error E | Error E |

**AC_MIXED_ADULT and AC_MIXED_YOUTH** both produce identical Error C `errorMessageTemplate` text → they bucket under the same `DR-BAIL-015::Error C` key in DefaultValidationService → ONE summary entry with all defendant names accumulated across both adult and youth contexts. The inline messages remain age-specific (Error A for adult, Error B for youth). ✓

**Mutual exclusivity**:
- AC_ADULT and AC_MIXED_ADULT are mutually exclusive via `hearingHasRemandYouth == 0` vs `== 1`.
- AC_YOUTH and AC_MIXED_YOUTH are mutually exclusive via `hearingHasRemandAdult == 0` vs `== 1`.
- AC_NODOB fires only when `hasDob == 0`, so AC_ADULT/AC_YOUTH/AC_MIXED_ADULT/AC_MIXED_YOUTH cannot fire for the same context.
- AC_CCIC/AC_CCSC fire on `isCcic == 1` / `isCcsc == 1`; a context with `isRemandBail == 1` will have `isCcic == 0` and `isCcsc == 0`. ✓

---

## R9 — RICD and extradition exclusions

**Decision**: RICD is excluded by not including it in `remandConditionalBailShortCodes`. Extradition results (E1CB, E1CONCB, E2CB, etc.) are excluded because they set post-hearing custody status A, not B — they will not appear as "conditional bail parent" short codes in the reference data and need no explicit exclusion logic.

---

## R10 — Test structure (100% AC coverage requirement)

| Test class | Location | ACs covered |
|---|---|---|
| `PreReleaseBailConditionPreprocessorTest` | `src/test/java/.../cel/` | All preprocessor logic, including hearing-wide flag computation, no-DOB handling, exclusion of non-trigger codes |
| `PreReleaseBailContextTest` | `src/test/java/.../cel/` | `toCelContext()`, `getOffenceIdSet()` |
| `PreReleaseBailConditionRuleIT` | `src/test/java/.../integration/` | AC1, AC2, AC2A, AC2B, AC3, AC4, AC4A, AC5A, AC7, AC8, AC9 |
| `PreReleaseBailConditionApiHttpLiveTest` | `src/apiTest/java/.../http/` | Representative ACs via live HTTP |
