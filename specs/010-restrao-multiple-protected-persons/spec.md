# Feature Specification: Restraining Order – Multiple Protected Persons Warning

**Feature Branch**: `CRA-260-restrao-multiple-protected-persons`
**Created**: 2026-09-20
**Status**: Draft
**Jira**: CRA-260 (parent: CRA-2 Results Validation)

## User Scenarios & Testing *(mandatory)*

### User Story 1 – Single RESTRAO Triggers Warning (Priority: P1)

A court clerk records a restraining order result and inadvertently enters more than one protected person in the "Protected person's name" field using a separator character (`&`, `,`, or `/`). When they save and continue to Manage Hearings, the system displays an advisory warning telling them to add a separate restraining order result for each protected person. The warning is offence-level and does not block sharing.

**Why this priority**: This is the core deliverable — catching the most common data-entry mistake (concatenating two names with a separator character).

**Independent Test**: Can be fully tested by submitting a RESTRAO result with `"John Smith & Jane Smith"` in the name field and verifying the warning appears on Manage Hearings.

**Acceptance Scenarios**:

1. **Given** a RESTRAO result whose "Protected person's name" contains `"John Smith & Jane Smith"`, **When** the user selects "Save and continue" and is navigated to Manage Hearings, **Then** the advisory warning is displayed below the offence and above the RESTRAO result, and the Share button remains available.
2. **Given** a RESTRAO result whose "Protected person's name" contains `"John Smith, Jane Smith"`, **When** the user selects "Save and continue", **Then** the same advisory warning is displayed.
3. **Given** a RESTRAO result whose "Protected person's name" contains `"John Smith/Jane Smith"`, **When** the user selects "Save and continue", **Then** the same advisory warning is displayed.

---

### User Story 2 – Whole-Word "and" Triggers Warning, Substring Does Not (Priority: P1)

A clerk enters "John Smith and Jane Smith" in the name field, or conversely enters a single name like "Alexandra Sanderson" or "Amanda Anderson" where "and" appears as part of the name. The system must trigger the warning only when "and" is a standalone word (any capitalisation), not when it is embedded within a name.

**Why this priority**: The whole-word matching logic is a critical correctness requirement — false positives on legitimate single names (e.g. "Alexandra") would undermine user trust.

**Independent Test**: Can be fully tested by submitting two requests — one with `"John Smith and Jane Smith"` (must warn) and one with `"Alexandra Sanderson"` (must not warn) — and verifying only the first triggers the warning.

**Acceptance Scenarios**:

1. **Given** a RESTRAO result with `"John Smith and Jane Smith"` in the name field, **When** the user saves and continues, **Then** the advisory warning is displayed.
2. **Given** a RESTRAO result with `"John Smith AND Jane Smith"` in the name field, **When** the user saves and continues, **Then** the advisory warning is displayed (case-insensitive match).
3. **Given** a RESTRAO result with `"Alexandra Sanderson"` in the name field, **When** the user saves and continues, **Then** no warning from this rule is displayed.
4. **Given** a RESTRAO result with `"Amanda Anderson"` in the name field, **When** the user saves and continues, **Then** no warning from this rule is displayed.

---

### User Story 3 – Multiple RESTRAO Results Evaluated Independently (Priority: P2)

A hearing contains more than one RESTRAO result — some with a single protected person's name and some with trigger characters. Each result is evaluated independently: the warning appears only against the breaching result(s), not against clean ones.

**Why this priority**: Correct per-result scoping prevents false warnings and ensures the user receives targeted, actionable feedback.

**Independent Test**: Can be fully tested by submitting a hearing with two RESTRAO results — one clean and one with `&` — and confirming the warning appears against only the breaching result.

**Acceptance Scenarios**:

1. **Given** two RESTRAO results in the same hearing, one with `"Jane Smith"` and one with `"John Smith & Jane Smith"`, **When** the user saves and continues, **Then** the warning is shown against the second result only, and no warning is shown against the first.

---

### User Story 4 – Warning Is Advisory; Sharing Remains Possible (Priority: P2)

A clerk who has triggered the warning can still proceed to share the result without making any changes. The warning is informational only.

**Why this priority**: The advisory-only semantics directly affect the user's workflow — incorrectly blocking sharing would cause a significant usability problem.

**Independent Test**: Can be fully tested by triggering the warning and then confirming the Share action completes successfully.

**Acceptance Scenarios**:

1. **Given** the warning has been triggered, **When** the user is on Manage Hearings, **Then** the Share button remains enabled and the user can share without modification.
2. **Given** other offence-level or defendant-level warnings from other rules are also active, **When** the user is on Manage Hearings, **Then** all warnings are displayed together in the standard location as per the design.

---

### User Story 5 – Resolving the Warning by Splitting Results (Priority: P3)

A clerk who sees the warning corrects the data by creating separate RESTRAO results — one per protected person. On the next "Save and continue" the warning is no longer displayed.

**Why this priority**: This confirms the round-trip: triggering then resolving the warning, and verifies the validation fires on each save cycle rather than being sticky.

**Independent Test**: Can be fully tested by triggering the warning, splitting the result, saving again, and confirming no warning appears.

**Acceptance Scenarios**:

1. **Given** the warning was triggered by `"John Smith & Jane Smith"` in one RESTRAO result, **When** the clerk amends to two separate RESTRAO results (`"John Smith"` and `"Jane Smith"`) and saves again, **Then** no warning from this rule is displayed against either result.

---

### User Story 6 – Amend and Reshare After Initial Share (Priority: P3)

After results have been shared, a clerk makes an amendment that introduces trigger characters into a RESTRAO name field. The validation rule fires identically on the amended submission.

**Why this priority**: Validates that the check is stateless and is not bypassed on reshare flows.

**Acceptance Scenarios**:

1. **Given** results have previously been shared, **When** the clerk amends a RESTRAO "Protected person's name" to `"John Smith & Jane Smith"` and saves again, **Then** the warning appears as per the standard behaviour, and the clerk can still reshare without changes.

---

### Edge Cases

- What happens when the "Protected person's name" field is empty or absent on a RESTRAO result? (No warning expected — the trigger condition cannot be satisfied.)
- What happens when "and" appears at the very start or end of the field, e.g. `"and Smith"` or `"John and"`? (No warning — "and" must be flanked by a word group on both sides to be treated as a separator.)
- What happens when `&` or `,` or `/` appears more than once? (Any single occurrence is sufficient to trigger the warning.)
- What happens when the name field contains both a separator character and a whole-word "and"? (Warning fires once — a single WARNING issue per breaching RESTRAO line.)
- What happens when the user navigates to Manage Hearings via the tab rather than "Save and continue"? (No validation check is performed; no warning is shown.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST inspect the "Protected person's name" field on every RESTRAO result line in the hearing when the user selects "Save and continue" and is navigated to Manage Hearings.
- **FR-002**: The system MUST trigger an advisory WARNING when that field contains any of the following: the character `&`, the character `,` (comma), the character `/`, or the word `and` matched as a whole word (case-insensitive).
- **FR-003**: The system MUST NOT trigger the warning when `and` appears only as a substring within a longer word (e.g. "Alexandra", "Sanderson", "Amanda", "Anderson").
- **FR-004**: Each RESTRAO result line MUST be evaluated independently; a warning MUST be associated with the specific breaching result only, not with other RESTRAO results in the same hearing.
- **FR-005**: The warning MUST be displayed as an offence-level warning, positioned below the relevant offence and above the RESTRAO result on the Manage Hearings screen.
- **FR-006**: The warning MUST use the following text: *"A restraining order result can only include one protected person's details. Add a separate restraining order result for each protected person."*
- **FR-007**: The warning MUST NOT prevent the user from saving or sharing the result — it is advisory only.
- **FR-008**: The validation check MUST run on the initial share flow and identically on all subsequent amend-and-reshare flows.
- **FR-009**: The validation check MUST NOT run when the user navigates to Manage Hearings via the tab (only on "Save and continue").

### Key Entities

- **RESTRAO result line**: A result line on an offence with `shortCode = "RESTRAO"` (Restraining Order). Contains a `protectedPersonName` field holding the free-text name entered by the clerk.
- **Trigger pattern**: Any of the characters `&`, `,`, `/` present anywhere in the field, or the word `and` appearing as a whole word (not as a substring of another word), case-insensitively.
- **Offence-level warning**: A validation issue of severity WARNING associated with a specific offence (and the specific RESTRAO result line on that offence), displayed in the standard warning location per the GDS design.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When a RESTRAO "Protected person's name" field contains any trigger character or whole-word "and", 100% of such cases produce the advisory warning on Manage Hearings.
- **SC-002**: When a RESTRAO "Protected person's name" field contains "and" only within a longer word (e.g. "Alexandra", "Sanderson"), 0% of such cases produce a false-positive warning.
- **SC-003**: In hearings with multiple RESTRAO results, warnings are scoped to breaching results only — clean results produce no false warnings in 100% of cases.
- **SC-004**: The warning does not prevent sharing in any case — 100% of users can share results unchanged after the warning is displayed.
- **SC-005**: The check runs identically on first share and all subsequent amend-and-reshare submissions with no behavioural difference.

## Assumptions

- The `protectedPersonName` field is already present on RESTRAO result lines in the `DraftValidationRequest` payload; no upstream API change is needed to surface it to this service.
- The trigger-character check (for `&`, `,`, `/`) and the whole-word "and" match are performed server-side during result pre-processing before the rule expression is evaluated; the pre-processing step exposes a boolean or count to the rule engine so the rule expression remains simple.
- A single WARNING issue is raised per breaching RESTRAO result line, even if the field contains multiple trigger signals simultaneously (e.g. `&` and `,` both present).
- The warning is rendered by the consuming UI using the GDS warning text component with a visually hidden "Warning" prefix for screen-reader accessibility; this service only returns the warning issue with the message text.
- The rule applies to all court types (Magistrates' Court and Crown Court) where RESTRAO results can be recorded — no court-type scoping is needed.
- The `protectedPersonName` field is treated as a raw string; no normalisation (trimming, Unicode normalisation) beyond what the client sends is required.
