# Feature Specification: No Conviction Warning

**Feature Branch**: `DD-43039-no-conviction-warning`
**Created**: 2026-07-31
**Status**: Draft
**Input**: User description: "As a user with access to enter results and manage hearings, when I record a sentence against an offence I want to be reminded if no conviction has been recorded (guilty plea/guilty verdict) so that the Court record is correct and the sentence lawful. AC1 — offence(s) resulted with a sentence and no conviction on sentenced offences (WARNING); AC1A — warning cleared once a guilty plea is recorded; AC1B — warning cleared once a guilty verdict is recorded."

## User Scenarios & Testing *(mandatory)*

### User Story 1 – Warning displayed for sentenced offence with no conviction (Priority: P1)

A Legal Adviser or Court Clerk records a final result against an offence (e.g. `COEW`, `FO`, `SSO`) that is not one of the recognised non-substantive disposals (withdrawn, dismissed, discharged, discontinued, or remain-on-file outcomes). The offence has no guilty plea, no finding of guilt, and no recorded date of conviction. When the user navigates to Manage Hearings (via "Save and continue"), they see an offence-level warning above the breaching offence(s) telling them a sentence cannot lawfully stand against an unconvicted offence.

**Why this priority**: This is the core AC — without it the rule does not exist. The warning protects against a sentence being recorded and shared against an offence that was never actually proven or admitted, which would make the Court record legally incorrect.

**Independent Test**: Can be fully tested by constructing a hearing with a single offence carrying a final, non-excluded result and no conviction indicator, then calling the validation endpoint and asserting the warning is returned.

**Acceptance Scenarios**:

1. **Given** an offence has a final result recorded against it (a result that makes the offence inactive, e.g. `COEW`, `FO`, `SSO`), **And** that final result is not one of `wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`, **And** the offence is not convicted (no guilty plea, no finding of guilt, no recorded date of conviction), **When** the user selects "Save and continue" and validation is triggered, **Then** a WARNING is returned for that offence with the message "No conviction has been added against the offence. Check whether you need to add a guilty plea or verdict", displayed above the offence in Manage Hearings.
2. **Given** a hearing with multiple offences where only one meets all warning conditions, **When** validation is triggered, **Then** the warning appears only on the breaching offence and not on the compliant offences.
3. **Given** the warning is displayed, **When** the user navigates back to "Enter results", "Enter pleas", or "Enter verdicts", **Then** they can make changes, including recording a guilty plea and/or verdict against the affected offence(s).

---

### User Story 2 – Warning cleared once the offence is convicted (Priority: P1)

Having seen the warning from User Story 1, the user records a guilty plea, or a not-guilty plea followed by a guilty verdict, against every offence that presented the warning. The offence(s) are now shown as convicted. When the user selects "Save and continue" and returns to Manage Hearings, the warning is no longer shown for those offences.

**Why this priority**: Avoiding false positives once the underlying condition is fixed is as critical as raising the warning in the first place — a warning that persists after remediation would erode trust in the rule.

**Independent Test**: Can be fully tested by re-running validation for the same offence after its conviction indicator flips to true (via a recorded guilty plea, per AC1A, or a recorded guilty verdict following a not-guilty plea, per AC1B) and asserting no warning is returned.

**Acceptance Scenarios**:

1. **Given** an offence previously presented the warning from User Story 1, **And** a guilty plea (e.g. Indicated Guilty, Guilty) has since been recorded and saved against it, **And** the offence is now shown as convicted, **When** the user selects "Save and continue" and navigates to Manage Hearings, **Then** the warning is no longer shown for that offence. *(AC1A)*
2. **Given** an offence previously presented the warning from User Story 1, **And** a not-guilty plea was recorded followed by a guilty verdict (e.g. Found Guilty, Proved) recorded and saved against it, **And** the offence is now shown as convicted, **When** the user selects "Save and continue" and navigates to Manage Hearings, **Then** the warning is no longer shown for that offence. *(AC1B)*

---

### User Story 3 – Warning is advisory only; sharing is not blocked (Priority: P2)

When the warning is raised, the user can still progress and share the result without making any changes to the result, pleas, or verdicts. The warning is informational, not a blocking error.

**Why this priority**: The business explicitly requires that the warning does not prevent sharing — it is the user's responsibility to decide whether to act on it before sharing.

**Independent Test**: Can be tested by asserting the validation response carries the issue at severity WARNING (not ERROR) and that no blocking outcome is set.

**Acceptance Scenarios**:

1. **Given** the warning condition is met, **When** validation runs, **Then** the issue is returned at severity WARNING, not ERROR.
2. **Given** the warning is present, **When** the user chooses to share without remediation, **Then** the share action is not blocked by this validation rule.

---

### Edge Cases

- What happens when an offence has a final result that is one of the excluded non-substantive disposals (e.g. `wdrn`, `dism`)? → No warning, regardless of conviction status.
- What happens when an offence has not yet received any final result (still active)? → No warning; the rule only evaluates offences with a final, non-excluded result.
- What happens when an offence is already convicted before any final result is recorded? → No warning, since the not-convicted condition is not met.
- What happens when every offence in the hearing meets the warning condition? → A warning is produced for each offence independently.
- What happens when the same offence has multiple result lines, only one of which is a final, non-excluded result? → The warning condition is satisfied if any result line on the offence is a final, non-excluded result.
- What happens with short-code casing (e.g. `WDRN` vs `wdrn`)? → Matching against the excluded list is case-insensitive.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST treat a result line as a "final result" using the same result-line category indicator already used to identify final dispositions (category `F`).
- **FR-002**: The system MUST recognise the following short codes as non-substantive disposals that are excluded from this rule regardless of conviction status: `wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`. Short-code matching MUST be case-insensitive.
- **FR-003**: For each offence, the system MUST check whether at least one result line is a final result (FR-001) whose short code is not in the excluded list (FR-002). If none, no further evaluation is performed for that offence.
- **FR-004**: For each offence with a qualifying final, non-excluded result, the system MUST check whether the offence is convicted (guilty plea, finding of guilt, or a recorded date of conviction). If convicted, no warning is produced for that offence.
- **FR-005**: When an offence has a qualifying final, non-excluded result (FR-003) and is not convicted (FR-004), the system MUST produce a WARNING issue for that offence.
- **FR-006**: The warning message text MUST be exactly: "No conviction has been added against the offence. Check whether you need to add a guilty plea or verdict".
- **FR-007**: The warning MUST be scoped to the individual offence(s) that breach the rule; other offences in the same hearing MUST NOT be affected.
- **FR-008**: The warning issue MUST carry severity WARNING, not ERROR, so that it does not block result sharing.
- **FR-009**: The rule MUST apply to any offence, regardless of offence code or offence type — no offence-code allow-list or restriction is used (unlike the extended-test disqualification rule, which is scoped to specific Road Traffic Act offences).

### Key Entities

- **Offence**: A charge in the hearing. Has an identifier, a list of result lines for the current hearing, and a conviction status (convicted / not convicted).
- **Result line**: A recorded outcome on an offence. Carries a short code (e.g. `COEW`, `wdrn`) and a category indicating whether it is a final disposition.
- **Conviction status**: Whether the offence has a guilty plea, a finding of guilt, or a recorded date of conviction. Set to convicted once either a guilty plea (AC1A) or a guilty verdict following a not-guilty plea (AC1B) is recorded.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every offence with a final, non-excluded result and no conviction receives the exact prescribed warning message — zero omissions across all test scenarios.
- **SC-002**: No false positives: offences with an excluded final result, no final result yet, or a recorded conviction produce zero warnings — validated across at least three distinct bypass scenarios.
- **SC-003**: The warning is returned at WARNING severity in 100% of triggered cases, never at ERROR severity.
- **SC-004**: Validation results for a multi-offence hearing correctly isolate the warning to breaching offences only, with no spillover to non-breaching offences.
- **SC-005**: Once an offence's conviction status flips to convicted (via guilty plea or guilty verdict), re-validation no longer returns the warning for that offence, in 100% of cases.
- **SC-006**: Result sharing is not blocked when the only issues present are warnings from this rule.

## Assumptions

- The conviction status used by this rule is the same offence-level "convicted" indicator already published on the offence object and already consumed by the existing CTL-missing rule — no new upstream field is required, and this feature is not blocked on an API change.
- The excluded final short-code list is identical to the one already used by the existing extended-test disqualification rule, on the basis that both rules treat the same set of outcomes as "not a substantive final disposal requiring a conviction check".
- The "final result" concept (a result that makes the offence inactive) is the same result-line category already used to identify final dispositions elsewhere in this service.
- This rule applies at the offence level, independent of which defendant the offence is charged against, and independent of court type.
- Multiple result lines per offence are not a concern beyond checking whether any one of them is a qualifying final, non-excluded result.
