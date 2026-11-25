# Research: CRA-362 – Pre-Release Bail Condition Warning (DR-BAIL-016)

**Branch**: `dev/CRA-362-pre-release-bail-condition-warning` | **Date**: 2026-11-09

## Decisions

| Decision | Outcome |
|----------|---------|
| Rule ID | `DR-BAIL-016` |
| Rule category short code | `BAIL` (4 chars, under 5) |
| YAML priority | `17000` (suffix × 1000 convention) |
| Preprocessor type qualifier | `"pre-release-bail-condition"` |
| Preprocessor class | `PreReleaseBailPreprocessor` |
| Context record | `PreReleaseBailContext` (per offence) |
| CEL variables | `triggerCount`, `adultCount`, `youthCount`, `noDobCount`, `ccicCount`, `ccscCount` |
| Number of YAML conditions | 5 (AC1=Warning A, AC2=Warning B, AC6=Warning C, AC2A=Warning D, AC2B=Warning E) |
| DB migration | `V1.018__insert_dr_bail_016.sql` |
| OffenceDto / upstream JAR | No change needed — `bailStatus` (C/L/S) already exists on `OffenceDto` |
| External API dependency | None — all data available on the existing `DraftValidationRequest` |
| PORO1 keyword matching | Unicode NFD normalisation + strip combining marks + lowercase + substring stem search |
| Blocked? | **No** — can proceed immediately |

## Remand Status Field Mapping

`OffenceDto.bailStatus` (BailStatusEnum) already carries the current remand status:
- `C` = Custody ✓
- `L` = Remanded into care of Local Authority ✓
- `S` = Remanded to youth detention accommodation ✓

This is the offence-level "current (from)" remand status. No new DTO fields needed.

## Bail Condition Result Lines

Bail condition codes (PASS1–PASS8, PORSC, PORS, PORO1) are separate `ResultLineDto` entries
on the same offence as the main remand result (RC, RCBV, BAILREVC, etc.). Both appear in
`DraftValidationRequest.resultLines` linked by `offenceId`.

## Age Determination

`DefendantDto.dateOfBirth` (nullable `LocalDate`) + `DraftValidationRequest.hearingDay` (required
`LocalDate`) → age in years via `Period.between(dob, hearingDay).getYears()`.

Three mutually exclusive states per defendant:
1. DOB present and 18+ → adult
2. DOB present and <18 → youth
3. DOB absent → no-DOB

For the per-offence context, defendant age is resolved once per defendant and carried into the
context. The preprocessor resolves the defendant owning each offence via `OffenceDto.getDefendantId()`
(mandatory field).

## PORO1 Keyword Matching

The PDF specifies stem-based, case-insensitive, accent-insensitive matching anywhere in the
prompt text. After Unicode NFD normalisation and stripping of combining diacritical marks, the
six stems to search:

| Stem (normalised) | Matches |
|-------------------|---------|
| `passport` | passport, passports |
| `suret` | surety, sureties |
| `securit` | security, securities |
| `asbort` | pasbort, pasbortau, basbort (Welsh) |
| `echniw` | mechnïwr, mechnïwyr, fechnïwr, fechnïwyr (echnïw after NFD strip) |
| `sicrwydd` | sicrwydd (Welsh) |

Implementation: `java.text.Normalizer.normalize(text, NFD)` → strip `\p{M}` via regex →
`toLowerCase(Locale.ROOT)` → `String.contains(stem)` for each stem.

This is a pure Java utility method with no external dependency. PORO1 result lines may carry
one or more prompts; we check the concatenated or individually-tested `promptValue`s of all prompts
on the PORO1 line.

## PreprocessingDefinition New Fields

Three new list fields added to the existing `PreprocessingDefinition` record (Lombok `@Builder`):

```java
// PreReleaseBailPreprocessor: remand bail change detection
List<String> conditionalBailResultCodes,  // RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB
List<String> crownCourtBailCodes,         // CCIC, CCSC
List<String> triggerBailConditionCodes    // PASS1–PASS8, PORSC, PORS
```

PORO1 (`"PORO1"`) is hardcoded in the preprocessor — it is a single, immutable code from the
Results Reference data and does not benefit from YAML configurability.

The custodial remand statuses (C, L, S) are `OffenceDto.BailStatusEnum` values, not String codes,
so they are hardcoded in the preprocessor as a `Set<BailStatusEnum>`.

## Existing Patterns Used

- **Per-offence preprocessor** pattern: `NonImprisonableOffencePreprocessor` / `NonImprisonableOffenceContext` (DR-SENT-011)
- **DOB age lookup** pattern: `AgeRestrictedImprisonmentPreprocessor` (DR-AGE-007)
- **groupResultsByOffence**: `PreprocessorHelper.groupResultsByOffence(request)` — groups all result lines by offence id
- **upperSet / anyShortCodeIn**: `PreprocessorHelper.upperSet(list)` and `anyShortCodeIn(lines, set)` — case-insensitive short-code set membership
- **Defendant index by id**: build a `Map<String, DefendantDto>` from `request.getDefendants()`

## Alternatives Considered

| Alternative | Rejected Because |
|-------------|-----------------|
| Reuse `ConditionalBailPreprocessor` | That preprocessor's context (per-defendant, bail-status B offences) is structurally different — this rule operates per-offence against bail-status C/L/S offences |
| 5 separate YAML files | Unnecessary proliferation; a single YAML with 5 conditions is simpler to maintain and keeps the related rule logic together |
| Regex for keyword matching | Simple stem substring search is sufficient and faster; stems were chosen by the BA to cover all forms, eliminating the need for regex alternation |
| Adding `bailStatus` to result lines | No — `OffenceDto.bailStatus` already carries the current remand status at the offence level; result lines carry the new result code |
