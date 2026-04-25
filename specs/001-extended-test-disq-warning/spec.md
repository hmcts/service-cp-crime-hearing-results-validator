# Feature Specification: Extended Test Disqualification Warning (DD-41656)

**Feature Branch**: `DD-41656-results-validation-warning`
**Created**: 2026-04-25
**Status**: Draft
**Input**: User description: "DD-41656 Results validation - Warning if extended test disqualification missing from relevant offence(s)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Warn when a relevant offence has a final result but no extended test disqualification (Priority: P1)

A user with access to "Enter results" is recording the outcome of a hearing that contains a dangerous-driving offence (one of the five Road Traffic Act 1988 offence codes that legally require an extended test on disqualification). The user records a final result that makes the offence inactive (for example COEW, FO, SSO) but does not add a DDOTE or DDOTEL disqualification line against that offence. When they navigate away from the results screen via "Save and continue" or by selecting the "Manage hearing" tab, the system surfaces a non-blocking warning above the affected offence reminding them to consider adding an extended test disqualification. The user can still progress and share the results.

**Why this priority**: This is the entire feature. It directly addresses the business problem (legal/operational errors caused by missing extended test disqualification), and it is the only behaviour that produces user-visible output. Without it, no other story has anything to suppress or interact with.

**Independent Test**: Submit a hearing payload containing one defendant, one offence with Home Office code `RT88026` and a recorded final result with short code `COEW`, and no `DDOTE` / `DDOTEL` result line linked to that offence. The validation response must contain exactly one warning issue, linked to that offence id, with the exact message text below, and the response must not be a blocking error.

**Acceptance Scenarios**:

1. **Given** a hearing containing one offence with Home Office code `RT88026` (dangerous driving), and a recorded final result `COEW` against that offence, and no `DDOTE` or `DDOTEL` result against that offence, **When** the validation request is submitted, **Then** the system returns one warning issue linked to that offence id with severity `WARNING`.
2. **Given** the warning is raised, **When** the warning message is read, **Then** it reads exactly: `Check whether you need to add extended test disqualification with DDOTE (disqualification and extended test) or DDOTEL (disqualification for life and extended test)`.
3. **Given** a warning is raised on a relevant offence, **When** the user proceeds to share the results, **Then** the warning does not block the share action.
4. **Given** a hearing containing **two** relevant offences (e.g. `RT88026` and `RT88046`) each with a qualifying final result and neither carrying `DDOTE`/`DDOTEL`, **When** validation runs, **Then** the system returns two warnings, each linked to its respective offence id.
5. **Given** a hearing where all offences carry Home Office codes other than the five relevant ones, **When** validation runs, **Then** no warning is produced by this rule.

---

### User Story 2 - Suppress the warning when the final result is an excluded outcome (Priority: P2)

When a relevant offence's final result is one of the excluded "did not proceed" outcomes (Withdrawn, Withdrawn-in-favour-of-another, Dismissed, Discharged, Discontinued, Count to remain on file, Indictment to remain on file), no extended test disqualification is legally required, so the warning must not be raised. This story protects the user from noisy false-positive warnings that would otherwise erode trust in the rule.

**Why this priority**: Without this suppression the warning would fire on every withdrawn or dismissed dangerous-driving offence — a large volume of cases — and users would learn to ignore it. P2 because story 1 still delivers value alone, but this story is required before the rule is safe to ship to production.

**Independent Test**: Submit a hearing identical to story 1 but with the final result short code replaced by `wdrn` (or any single excluded code). The validation response must contain no warnings linked to that offence from this rule.

**Acceptance Scenarios**:

1. **Given** a relevant offence whose final result short code is `wdrn`, **When** validation runs, **Then** no warning is raised against that offence.
2. **Given** a relevant offence whose final result short code is `WDRNOFF`, **When** validation runs, **Then** no warning is raised against that offence.
3. **Given** a relevant offence whose final result short code is one of `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`, **When** validation runs, **Then** no warning is raised against that offence.
4. **Given** a relevant offence whose final result short code matches an excluded value but in a different letter case (e.g. `WDRN`, `Wdrn`), **When** validation runs, **Then** no warning is raised (matching is case-insensitive).

---

### User Story 3 - Suppress the warning when DDOTE or DDOTEL is already recorded (Priority: P2)

When the user has already added a `DDOTE` or `DDOTEL` result line against the relevant offence, the rule's purpose is satisfied and the warning must not fire. This is the positive-path completion of story 1 — once the user fixes the data, the warning disappears.

**Why this priority**: Without this, the warning would persist even after the user has done the right thing, defeating the rule's purpose. P2 alongside story 2 because both must be in place before the rule is shippable, but story 1 is the headline behaviour.

**Independent Test**: Submit a hearing identical to story 1 but with an additional result line `DDOTE` (or `DDOTEL`) recorded against the same offence id. The validation response must contain no warnings from this rule against that offence.

**Acceptance Scenarios**:

1. **Given** a relevant offence with a qualifying final result and an additional `DDOTE` result recorded against the same offence, **When** validation runs, **Then** no warning is raised against that offence.
2. **Given** a relevant offence with a qualifying final result and an additional `DDOTEL` result recorded against the same offence, **When** validation runs, **Then** no warning is raised against that offence.
3. **Given** a relevant offence with a qualifying final result and a `DDOTE` recorded in mixed case (e.g. `ddote`, `DdOtE`), **When** validation runs, **Then** no warning is raised against that offence (matching is case-insensitive).
4. **Given** a hearing containing two relevant offences where one has `DDOTE` recorded against it and the other does not, **When** validation runs, **Then** exactly one warning is raised, linked to the offence missing the disqualification.

---

### Edge Cases

- **No final result yet** — the offence is still active (no result that would make it inactive). The rule does not fire because precondition AC1 ("the relevant offence has a final result recorded") is not met.
- **Final result short code is unknown / not in any list** — treated as a non-excluded final result, so the warning fires (matches the literal reading of AC1: "the final result is not any of the following [excluded list]").
- **DDOTE / DDOTEL recorded against a different offence in the same hearing** — does not suppress the warning; the linkage must be to the same relevant offence id.
- **Multiple defendants on the same offence** — each defendant's offence instance is evaluated independently; one warning per qualifying defendant–offence pair.
- **Hearing contains no offence with a relevant Home Office code** — the rule produces no output.
- **Same hearing already failed an unrelated rule (e.g. DR-SENT-002 ERROR)** — this rule's `WARNING` does not become an error and does not affect other rules' decisions; rules evaluate independently.
- **Operator has set the database `validation_rule` row for this rule to `enabled = false`** — the rule produces no output.
- **Operator has set the database `severity` ceiling to `WARNING`** — has no observable effect, since this rule's only condition is already `WARNING` (the ceiling never promotes upward).
- **Mixed-case result short codes** — comparisons against the excluded list and against `DDOTE` / `DDOTEL` are case-insensitive.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST identify a relevant offence as any offence whose Home Office offence code matches one of: `RT88046`, `RT88526`, `RT88026`, `RT88530`, `RT88531`.
- **FR-002**: For each relevant offence, the system MUST inspect the final result short code recorded against that offence (the result that has rendered the offence inactive — for example `COEW`, `FO`, `SSO`, etc.).
- **FR-003**: The system MUST treat the following final result short codes as **excluded** (no warning): `wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`. Matching MUST be case-insensitive.
- **FR-004**: For each relevant offence, the system MUST inspect the result short codes linked to that offence and detect the presence of `DDOTE` or `DDOTEL`. Matching MUST be case-insensitive.
- **FR-005**: The system MUST raise exactly one warning per relevant offence when **all** of the following hold:
  - The offence's Home Office code is in the relevant list (FR-001), AND
  - A final result is recorded against the offence (FR-002), AND
  - That final result short code is **not** in the excluded list (FR-003), AND
  - No `DDOTE` and no `DDOTEL` result is recorded against the offence (FR-004).
- **FR-006**: The warning message text MUST be exactly: `Check whether you need to add extended test disqualification with DDOTE (disqualification and extended test) or DDOTEL (disqualification for life and extended test)`.
- **FR-007**: Each warning MUST be linked to the specific offence id it concerns, so that the consumer (UI) can position the warning above the correct offence.
- **FR-008**: The warning's default severity MUST be `WARNING` (non-blocking — the user remains able to proceed and share).
- **FR-009**: The rule MUST be evaluated independently per relevant offence; multiple qualifying offences in the same hearing MUST each produce their own warning.
- **FR-010**: The rule MUST be subject to the existing runtime severity-ceiling model: an operator MUST be able to disable the rule entirely or cap its severity downward via the `validation_rule` database row, without redeployment. Severity MUST NOT be promoted upward by the ceiling.
- **FR-011**: The rule MUST NOT mutate the validation request and MUST NOT depend on the result of any other rule (rules evaluate independently per project architecture).

### Key Entities

- **Hearing (validation request)** — the unit of work submitted by the calling system. Contains a hearing identifier, one or more offences, one or more defendants, and the result lines recorded so far.
- **Offence** — a charge in the hearing. Identified by an offence id and a Home Office offence code (e.g. `RT88026`). Five specific Home Office codes mark an offence as **relevant** for this rule.
- **Result line** — a recorded outcome linked to a specific offence id. Carries a short code (e.g. `COEW`, `wdrn`, `DDOTE`, `DDOTEL`). One of the result lines on an offence is the offence's final result; others (such as a disqualification line) sit alongside it.
- **Final result** — the result that makes the offence inactive. Used by FR-002, FR-003, FR-005.
- **Excluded final result short code** — the nine codes listed in FR-003 that suppress the warning.
- **Disqualification with extended test** — a result line whose short code is `DDOTE` (obligatory disqualification with extended test) or `DDOTEL` (obligatory disqualification for life with extended test). Presence on the relevant offence suppresses the warning.
- **Validation issue (warning)** — the output the rule produces. Severity `WARNING`, with the exact message text from FR-006 and a reference to the affected offence id.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every hearing containing at least one relevant offence with a non-excluded final result and no `DDOTE`/`DDOTEL`, the validation response includes exactly one warning per such offence (true-positive rate = 100% on the qualifying offences in any test corpus).
- **SC-002**: For hearings where every relevant offence either has an excluded final result, or already carries `DDOTE`/`DDOTEL`, or is still active (no final result), the validation response from this rule contains zero warnings (false-positive rate = 0%).
- **SC-003**: A user who triggers the warning by saving and continuing reads the message and either adds the missing disqualification or proceeds to share — they are never blocked from sharing by this rule (0% block rate).
- **SC-004**: Within three months of release, the rate of post-share amendments and reshares attributed to a missing extended test disqualification on the five relevant offences is reduced relative to the equivalent three-month period before release. The exact target percentage will be set by the product owner once a baseline measurement is captured, but the metric MUST be reported.
- **SC-005**: Validation response time for a hearing containing relevant offences is indistinguishable to the user from validation of a hearing without them (the rule adds no perceptible latency to the existing validate call).
- **SC-006**: Operators can disable the rule or downgrade its severity via the existing runtime override mechanism within one working day, without a software release.

## Assumptions

- **Validation entry point** — the rule is consumed via the existing `POST /api/validation/validate` endpoint and follows the existing rule-engine contract. No new API endpoint is introduced.
- **Request payload shape** — the validation request already provides offence Home Office codes and result-line short codes against the offence, with offence ids that the consuming UI can use to position the warning. (Source-of-truth: the existing `libs.api.hearing.results.validator` request schema.) No upstream contract change is required.
- **Final result identification** — the request payload either explicitly marks which result line is the offence's final result, or the existing rule-engine preprocessing layer can identify it deterministically. This rule treats the marked final result as authoritative.
- **Trigger point in the UI** — AC1 names "Save and continue" and the "Manage hearing" tab as the user actions that invoke validation. Wiring those UI events to the validate endpoint is the responsibility of the consuming front-end and is out of scope for this service.
- **Rule packaging** — the new rule will be added as a YAML file under `src/main/resources/rules/`, following the existing naming convention (e.g. `DR-SENT-003.yaml` if assigned to the sentencing category, or a new category code such as `DR-DISQ-001`). No Java change is required if an existing preprocessor type fits; a new preprocessor and context record will be added if needed.
- **Severity model** — the rule's only condition has `severity: WARNING` in the YAML. The runtime severity-ceiling row in the `validation_rule` table behaves per project Constitution Principle VI: it caps downward only and never promotes.
- **Case-insensitive matching** — the input description uses mixed case (`ddote`, `WDRNOFF`, `COEW`). The rule treats all short-code comparisons as case-insensitive, consistent with how the existing DR-SENT-002 rule handles its filter list.
- **Per-offence evaluation** — the rule produces at most one warning per qualifying offence, regardless of how many defendants are charged with it. The warning is offence-anchored (it points at a specific offence id for UI placement), so two defendants linked to the same relevant offence still yield exactly one warning. This deliberately contrasts with `DR-SENT-002`, which groups by defendant; the data-model and preprocessor for this rule group by offence id, matching how the warning is presented to the user.
- **Out of scope** — no UI design work, no upstream changes to the offence-result data model, no expansion of the relevant-offence list beyond the five Home Office codes given, no changes to which short codes count as excluded.
