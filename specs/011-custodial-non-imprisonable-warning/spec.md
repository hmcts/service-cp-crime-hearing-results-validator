# Feature Specification: Custodial Sentence Recorded Against Non-Imprisonable Offence – Warning

**Feature Branch**: `dev/CRA-256-custodial-sentence-recorded-against-non-imprisonable-offence`
**Jira Ticket**: CRA-256
**Created**: 2026-09-25
**Status**: Draft (v3 — updated 2026-10-06)
**Priority**: 3-Medium
**Parent**: CRA-2 Results Validation

## User Scenarios & Testing *(mandatory)*

### User Story 1 – Warning when custodial result on non-imprisonable offence (Priority: P1)

A caseworker records a custodial result against an offence that the reference data marks as non-imprisonable (effective custodial indicator = N). After selecting "Save and continue" they land on the Manage Hearings screen where an offence-level warning is displayed below the offence and above the result. The caseworker can read the advisory, decide to keep the result as-is, and proceed to share.

**Why this priority**: Core business requirement — the warning exists to prompt the caseworker to double-check before sharing a potentially incorrect custodial sentence. Without this, incorrect sentences may be shared without any advisory notice.

**Independent Test**: Record any custodial short code (e.g. IMP) against an offence with `custodial_indicator = N`; confirm the warning text appears on Manage Hearings and the Share button is still enabled.

**Acceptance Scenarios**:

1. **Given** an offence where `custodial_indicator` column = `N`, **When** a result with a custodial short code is recorded and the user saves and continues, **Then** the warning "A custodial sentence may not be available for this offence. Check the sentence is correct before continuing." is displayed as an offence-level warning below the offence and above the result.
2. **Given** the warning is displayed, **When** the user proceeds to share without changing the result, **Then** sharing succeeds — the warning does not block the action.
3. **Given** an offence where `custodial_indicator` column = `Y`, **When** a custodial result is recorded and saved, **Then** the warning is NOT displayed.

---

### User Story 2 – Fallback to details_json when column has no value (Priority: P2)

When the `custodial_indicator` column holds no value, the system falls back to `document.libra.custodialindicator.code` from the offence's `details_json`. A caseworker who results such an offence gets the same behaviour as if the column held the value directly — warning shown for N, no warning for Y.

**Why this priority**: Required for completeness of the indicator-resolution logic; some offences in the reference data may rely solely on the JSON field.

**Independent Test**: Set column blank and `details_json` custodialIndicator code = N (i.e. `{"custodialindicator": {"code": "N", "description": "NO"}}`); record a custodial result and confirm the warning appears. Repeat with code = Y and confirm no warning.

**Acceptance Scenarios**:

1. **Given** `custodial_indicator` column is blank and `details_json` contains `{"custodialindicator": {"code": "N", "description": "NO"}}`, **When** a custodial result is recorded, **Then** the warning IS displayed.
2. **Given** `custodial_indicator` column is blank and `details_json` contains `{"custodialindicator": {"code": "Y", "description": "YES"}}`, **When** a custodial result is recorded, **Then** the warning is NOT displayed.
3. **Given** neither the column nor `details_json` holds any value, **When** a custodial result is recorded, **Then** no warning is displayed (treated as imprisonable).

---

### User Story 3 – Column takes precedence over details_json (Priority: P2)

When the `custodial_indicator` column has a value, it overrides whatever `details_json` contains. A caseworker benefits from consistent, predictable behaviour even when the two sources disagree.

**Why this priority**: Data-integrity rule; without precedence the system would behave unpredictably for offences where the two sources disagree.

**Independent Test**: Set column = Y and `details_json` code = N; record a custodial result and confirm no warning — the column wins.

**Acceptance Scenarios**:

1. **Given** `custodial_indicator` column = `Y` and `details_json` code = `N`, **When** a custodial result is recorded, **Then** no warning is displayed.

---

### User Story 4 – No warning for non-custodial results (Priority: P3)

When the recorded result code is not in the custodial result codes list, the custodial indicator check is not run and no warning appears regardless of the offence's imprisonability indicator.

**Why this priority**: Scoping rule — prevents false-positive warnings on non-custodial results.

**Independent Test**: Record a non-custodial result against an offence with indicator = N; confirm no warning appears.

**Acceptance Scenarios**:

1. **Given** any offence (any indicator value), **When** a result with a non-custodial short code is recorded (e.g. `COEW`, `YROEW`, `FO`), **Then** no custodial indicator check is performed and no warning is shown.

---

### User Story 5 – Warning coexists with other warnings (Priority: P3)

When the same hearing has triggered additional offence-level or defendant-level warnings from other validation rules, the custodial-indicator warning is displayed alongside them without suppressing or being suppressed by them.

**Why this priority**: Display-composition requirement; caseworkers must see all advisory messages simultaneously.

**Independent Test**: Trigger both this warning and another offence-level warning (e.g. restraining order warning) in the same hearing; confirm both appear together on Manage Hearings with Share still enabled.

**Acceptance Scenarios**:

1. **Given** the custodial indicator warning is triggered and other offence-level warnings are also triggered, **When** the user views Manage Hearings, **Then** all offence-level warnings appear (each below its offence, above its result) and Share remains available.
2. **Given** the custodial indicator warning is triggered and defendant-level warnings from other rules are also triggered, **When** the user views Manage Hearings, **Then** all warnings display together — defendant-level warnings above the first offence for that defendant, offence-level warnings below their offence and above their result — and Share remains available.

---

### Edge Cases

- What happens when the same offence has multiple custodial result lines? Each result line is evaluated independently; if the offence is non-imprisonable the warning appears once per offence (not repeated per result line).
- What happens when `details_json` is malformed, or `document.libra.custodialindicator` does not exist, or the object's `code` field is missing? Treat as no value — no warning displayed.
- What happens when `custodial_indicator` column value is neither `Y` nor `N` (unexpected value)? Treat as no value, fall back to `details_json`; if `details_json` also has no usable value, treat as imprisonable (no warning).
- What happens when a hearing contains multiple offences, some imprisonable and some not, all with custodial results? Warning appears only beneath each non-imprisonable offence; imprisonable offences display no warning.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST emit an offence-level WARNING when a custodial result code is recorded against an offence whose effective custodial indicator resolves to `N`.
- **FR-002**: The effective custodial indicator MUST be resolved using this precedence: (1) `custodial_indicator` column if it holds `Y` or `N`; (2) `document.libra.custodialindicator.code` from `details_json` if the column has no usable value; (3) treated as imprisonable (no warning) if neither source has a value.
- **FR-003**: A custodial result MUST be defined as any result whose short code is one of: `IMP`, `YOI`, `DTO`, `EXTDVS`, `EXTDVSU`, `EXTIVS`, `STSDY`, `SPECC`, `SPECCC`, `SPECCD`, `SUSPS`, `SUSPSS`, `SUSPSNI`, `SUSPSNR`, `SUSPSD`, `SUSPSDS`, `SUSPSDNI`, `SUSPSDNR`.
- **FR-004**: When the effective custodial indicator is `Y`, the system MUST NOT display the warning, even if `details_json` indicates `N`.
- **FR-005**: When the result short code is not in the custodial list, the system MUST NOT check the custodial indicator and MUST NOT display a warning.
- **FR-006**: When neither indicator source has a usable value, the system MUST NOT display the warning (treat as imprisonable by default).
- **FR-007**: The warning text MUST be exactly: "A custodial sentence may not be available for this offence. Check the sentence is correct before continuing."
- **FR-008**: The warning MUST be advisory only — it MUST NOT prevent the caseworker from saving or sharing the result.
- **FR-009**: The warning MUST be displayed at offence level (below the relevant offence, above the relevant result) on the Manage Hearings screen.
- **FR-010**: The warning MUST coexist with all other offence-level and defendant-level warnings without suppressing or being suppressed by them.
- **FR-011**: The check MUST be performed per offence for which a custodial result has been recorded; a hearing with multiple offences produces independent checks per offence.

### Key Entities

- **Offence**: A charge in the hearing (`OffenceDto`). Carries `offenceCode` (CJS offence code used to look up reference data). No custodial-indicator fields are read from `OffenceDto` directly.
- **Reference data offence**: The record returned by `cpp-context-referencedata-offences` for a given `cjsOffenceCode`. Carries `custodialIndicator` (top-level, from the `custodial_indicator` DB column) and `details` (a nested JSON object containing the full offence document, including `document.libra.custodialindicator.code`).
- **Effective custodial indicator**: `"Y"` or `"N"` resolved by `ReferencedataOffenceClient.getCustodialIndicator()` using column-takes-precedence logic; `Optional.empty()` when neither source has a usable value (fail-open: treated as imprisonable, no warning).
- **Result line**: A recorded outcome on an offence. Carries a `shortCode` that determines whether the custodial check applies.
- **Validation warning**: An advisory `WARNING`-severity issue produced when the rule fires. Does not block sharing.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Caseworkers are warned before sharing whenever a custodial sentence is recorded against a non-imprisonable offence — 100% of such cases trigger the advisory warning.
- **SC-002**: No false-positive warnings appear for imprisonable offences, non-custodial results, or offences with no indicator data — 0 false positives in any of those scenarios.
- **SC-003**: The column-over-`details_json` precedence rule is correctly applied in all cases — when the column holds a value it always wins, regardless of `details_json`.
- **SC-004**: The warning does not interrupt the sharing workflow — the Share button remains enabled and caseworkers can complete sharing without additional steps.
- **SC-005**: The warning coexists correctly with all other active warnings on the Manage Hearings screen — no warnings are hidden or duplicated due to this rule firing.

## Assumptions

- The effective custodial indicator is resolved by calling the `cpp-context-referencedata-offences` API using the offence's `cjsOffenceCode` — the same outbound endpoint already used by `DR-SEX-008` via `ReferencedataOffenceClient.lookupMisCode()`. No changes to `OffenceDto` or the `api-cp-crime-hearing-results-validator` library are required.
- The reference data API response carries a top-level `custodialIndicator` field (from the `custodial_indicator` DB column) and a nested `details` JSON object that contains the full offence document, including `document.libra.custodialindicator` at path `$.document.libra.custodialindicator.code`.
- The precedence rule — column wins, `details_json` as fallback — is implemented entirely inside `ReferencedataOffenceClient.getCustodialIndicator()`. The upstream `cpp-context-referencedata-offences` service currently does NOT apply this precedence itself (it passes the column value through without fallback); therefore this service must apply it client-side.
- The `custodialindicator` node under `details.document.libra` is an **extra field present in real data but not in the formal schema** of the reference data service — it is accessed the same way as `misCode`, `libracategory`, etc.
- Any `code` value other than `"Y"` or `"N"` (case-sensitive) is treated as no value. Absent / null column, absent `details` field, absent or malformed `custodialindicator` node — all treated the same as "no value" (fail-open: no warning).
- The rule is offence-scoped: one warning per non-imprisonable offence that carries a custodial result, not one warning per result line if multiple custodial results are on the same offence.
- The coexistence scenarios in US5 (US5 AC1, US5 AC2) and the shareability scenario in US1 AC2 describe UI rendering behaviour on the Manage Hearings screen; this service is responsible only for emitting the warning in the validation response — the UI rendering is out of scope for this service.
- The existing `AgeRestrictedImprisonmentPreprocessor` (DR-AGE-007) works on similar custodial short codes but uses different reference data fields; a new dedicated preprocessor is required for this rule.
- A new YAML rule `DR-SENT-011` and a new `NonImprisonableOffencePreprocessor` are required. The preprocessor follows the same pattern as `SexualOffenceNotificationPreprocessor`: it calls `ReferencedataOffenceClient.getCustodialIndicator(offence.getOffenceCode())` to resolve the effective indicator, applying the custodial short-code filter first (cheaper local check before any network call).
- The `getCustodialIndicator()` method on `ReferencedataOffenceClient` is cached by `offenceCode` on success (same Caffeine cache as `lookupMisCode`, same `unless = "#result == null"` guard to exclude fail-open results from the cache). Failed lookups return `Optional.empty()` and are not cached.
