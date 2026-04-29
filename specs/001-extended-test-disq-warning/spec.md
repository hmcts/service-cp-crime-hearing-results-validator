# Feature Specification: Extended Test Disqualification Warning (DD-41656)

**Feature Branch**: `DD-41656-results-validation-warning`
**Created**: 2026-04-25
**Status**: Refined 2026-04-28
**Input**: User description: "DD-41656 Results validation - Warning if extended test disqualification missing from relevant offence(s)"

## Revision — 2026-04-28

This revision refines the rule's "is this a final result?" gate, **superseding** the original line-65 edge case ("unknown short code → warns") and `research.md` decision **R3** ("treat any non-excluded result line as final, no upstream DTO change"). The BA test-scenarios document (`Disq with ext test scenarios.docx`, scenario 5: adjournment `'A'` on a relevant offence → no warning) is now the authoritative behaviour.

The rule now reads the **`category`** attribute that already exists on each result line in the upstream domain models (cpp-ui-hearing's `ResolvedDraftResultLine`, cpp-context-hearing's `SharedResultsCommandResultLineV2`). Both call sites currently drop `category` before posting to the validator; the fix is to thread it through the contract and consume it in the rule.

`category` is a closed enum:

| Value | Meaning |
|-------|---------|
| `A` | Ancillary (e.g. adjournment, listing, administrative) |
| `I` | Intermediary (e.g. plea recorded, hearing-internal) |
| `F` | Final (the outcome that makes the offence inactive) |

The qualifying gate becomes: relevant offence **AND** at least one result line on the offence has `category = 'F'` whose `shortCode` is not in the excluded-final list **AND** no result line on the offence has `shortCode` in `{DDOTE, DDOTEL}`. The previous "anything-not-excluded counts as final" inference is retired — it produced a false positive on every adjournment of a relevant offence (BA scenario 5).

This revision spans **four repositories** under DD-41656 (no separate Jira), all behind a single behaviour change:

1. **`api-cp-crime-hearing-results-validator`** — add `category: enum [A, I, F]` to `ResultLineDto` in the OpenAPI spec; bump the published library version.
2. **`cpp-ui-hearing`** — extend `buildResultLines` (`src/app/results/core/helpers/results-validation.ts`) to map `line.category` onto the validation request body. The data is already on the resolved draft line.
3. **`cpp-context-hearing`** — add a `category` field and `withCategory(...)` builder to the locally hand-written `ResultLineDto` (parallel mirror of the OpenAPI DTO; not regenerated). Populate it in `ValidationRequestMapper.toValidationRequest`.
4. **`service-cp-crime-hearing-results-validator`** — pull the new lib version. Update `DR-DISQ-001.yaml` and `DisqualificationExtendedTestPreprocessor` so the gate reads `category`.

The existing excluded-final short-code list (`wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`) is unchanged and continues to suppress as before — but only when present on the `'F'` line itself, not on any line.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Warn when a relevant offence has a final result but no extended test disqualification (Priority: P1)

A user with access to "Enter results" is recording the outcome of a hearing that contains a dangerous-driving offence (one of the five Road Traffic Act 1988 offence codes that legally require an extended test on disqualification). The user records a final result that makes the offence inactive (for example COEW, FO, SSO) but does not add a DDOTE or DDOTEL disqualification line against that offence. When they navigate away from the results screen via "Save and continue" or by selecting the "Manage hearing" tab, the system surfaces a non-blocking warning above the affected offence reminding them to consider adding an extended test disqualification. The user can still progress and share the results.

**Why this priority**: This is the entire feature. It directly addresses the business problem (legal/operational errors caused by missing extended test disqualification), and it is the only behaviour that produces user-visible output. Without it, no other story has anything to suppress or interact with.

**Independent Test**: Submit a hearing payload containing one defendant, one offence with Home Office code `RT88026`, and a recorded result line with short code `COEW` and `category = 'F'`, and no `DDOTE` / `DDOTEL` result line linked to that offence. The validation response must contain exactly one warning issue, linked to that offence id, with the exact message text below, and the response must not be a blocking error.

**Acceptance Scenarios**:

1. **Given** a hearing containing one offence with Home Office code `RT88026` (dangerous driving), and a result line with short code `COEW` and `category = 'F'` against that offence, and no `DDOTE` or `DDOTEL` result against that offence, **When** the validation request is submitted, **Then** the system returns one warning issue linked to that offence id with severity `WARNING`.
2. **Given** the warning is raised, **When** the warning message is read, **Then** it reads exactly: `Check whether you need to add extended test disqualification with DDOTE (disqualification and extended test) or DDOTEL (disqualification for life and extended test)`.
3. **Given** a warning is raised on a relevant offence, **When** the user proceeds to share the results, **Then** the warning does not block the share action.
4. **Given** a hearing containing **two** relevant offences (e.g. `RT88026` and `RT88046`) each with a qualifying final result and neither carrying `DDOTE`/`DDOTEL`, **When** validation runs, **Then** the system returns two warnings, each linked to its respective offence id.
5. **Given** a hearing where all offences carry Home Office codes other than the five relevant ones, **When** validation runs, **Then** no warning is produced by this rule.

---

### User Story 2 - Suppress the warning when the final result is an excluded outcome (Priority: P2)

When a relevant offence's final result is one of the excluded "did not proceed" outcomes (Withdrawn, Withdrawn-in-favour-of-another, Dismissed, Discharged, Discontinued, Count to remain on file, Indictment to remain on file), no extended test disqualification is legally required, so the warning must not be raised. This story protects the user from noisy false-positive warnings that would otherwise erode trust in the rule.

**Why this priority**: Without this suppression the warning would fire on every withdrawn or dismissed dangerous-driving offence — a large volume of cases — and users would learn to ignore it. P2 because story 1 still delivers value alone, but this story is required before the rule is safe to ship to production.

**Independent Test**: Submit a hearing identical to story 1 but with the `category = 'F'` line's short code replaced by `wdrn` (or any single excluded code). The validation response must contain no warnings linked to that offence from this rule.

**Acceptance Scenarios**:

1. **Given** a relevant offence whose `category = 'F'` line carries short code `wdrn`, **When** validation runs, **Then** no warning is raised against that offence.
2. **Given** a relevant offence whose `category = 'F'` line carries short code `WDRNOFF`, **When** validation runs, **Then** no warning is raised against that offence.
3. **Given** a relevant offence whose `category = 'F'` line carries one of `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`, **When** validation runs, **Then** no warning is raised against that offence.
4. **Given** a relevant offence whose `category = 'F'` line carries a short code matching an excluded value but in a different letter case (e.g. `WDRN`, `Wdrn`), **When** validation runs, **Then** no warning is raised (matching is case-insensitive).

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

### User Story 4 - Suppress the warning when no final result is recorded yet (Priority: P1)

When a relevant offence has only ancillary or intermediary lines recorded against it (e.g. an **adjournment** with `category = 'A'`, or an intermediary procedural result with `category = 'I'`), the offence has not yet reached a final outcome. The warning must **not** fire — there is no final result to evaluate against, and asking the user to add a disqualification before the offence is finalised would be premature and noisy. This is the headline behaviour change of the 2026-04-28 revision; under the previous heuristic the warning would fire on every adjournment of a relevant offence, producing a high-volume false positive.

**Why this priority**: This story is the entire reason for the revision. Without it, the rule continues to fire on adjournments — exactly the noise pattern that the original spec promised to avoid (see SC-002). It is co-equal in priority with US1 because the two together define the rule's full positive-and-negative behaviour on the new gate.

**Independent Test**: Submit a hearing payload (BA scenarios doc, scenario 5) containing one defendant, one offence with Home Office code `RT88026`, and a single result line with `category = 'A'` (adjournment) and no other lines on that offence. The validation response must contain zero warnings from this rule.

**Acceptance Scenarios**:

1. **Given** a relevant offence whose only result line has `category = 'A'` (adjournment), **When** validation runs, **Then** no warning is raised against that offence.
2. **Given** a relevant offence whose only result line has `category = 'I'` (intermediary), **When** validation runs, **Then** no warning is raised against that offence.
3. **Given** a relevant offence with two recorded lines, both with `category` in `{A, I}` (no `'F'` line yet), **When** validation runs, **Then** no warning is raised against that offence.
4. **Given** a relevant offence whose result line has `category` missing (legacy or malformed payload), **When** validation runs, **Then** no warning is raised against that offence (the rule fails safe — no F line, no warning).

---

### Edge Cases

- **No final result yet (no `'F'` line)** — the offence is still active or adjourned. The rule does not fire because the qualifying gate requires at least one `category = 'F'` line on the offence. Replaces the original "no final result" edge case and **supersedes** the original "unknown short code" edge case — short-code-set membership no longer drives final-result detection.
- **Final `'F'` line carries an unknown short code that is not in the excluded list and is not `DDOTE`/`DDOTEL`** — the warning fires, because the line is unambiguously final by `category` and not suppressed by the excluded set. This is conservative-by-design: novel final outcomes get flagged, not silently dropped.
- **`'F'` line carries a short code in the excluded list** (e.g. `wdrn` on the final line) — no warning, the suppression behaviour is preserved.
- **Excluded short code on a non-`'F'` line** (e.g. an early `wdrn` recorded against the offence but later replaced by a `category = 'F'` line with a substantive outcome) — the warning **fires** if the F line itself is non-excluded. This is a subtle behaviour change from the original gate, where any excluded code anywhere on the offence would suppress.
- **Multiple `'F'` lines on the same offence** — the rule fires if **at least one** F line has a non-excluded short code. (An offence with two F lines is unusual; the gate is permissive towards firing in that edge case.)
- **`category` missing or any value other than `A`, `I`, `F`** — the line cannot be the F line; the rule falls back to "no F line, no warning" rather than producing a false positive (FR-015 fail-safe). Affects callers that have not yet upgraded to populate `category`.
- **DDOTE / DDOTEL recorded against a different offence in the same hearing** — does not suppress the warning; the linkage must be to the same relevant offence id.
- **DDOTE / DDOTEL recorded on a non-`'F'` line of the relevant offence** — suppresses the warning regardless of category, by design. Whichever line carries the disqualification short code, the rule's purpose is satisfied.
- **Multiple defendants on the same offence** — the rule produces exactly **one** warning for the offence regardless of how many defendants are charged with it. The warning is offence-anchored (linked to the offence id, not to a defendant–offence pair). See the **Per-offence evaluation** assumption below for the rationale and how this contrasts with `DR-SENT-002`.
- **Hearing contains no offence with a relevant Home Office code** — the rule produces no output.
- **Same hearing already failed an unrelated rule (e.g. DR-SENT-002 ERROR)** — this rule's `WARNING` does not become an error and does not affect other rules' decisions; rules evaluate independently.
- **Operator has set the database `validation_rule` row for this rule to `enabled = false`** — the rule produces no output.
- **Operator has set the database `severity` ceiling to `WARNING`** — has no observable effect, since this rule's only condition is already `WARNING` (the ceiling never promotes upward).
- **Mixed-case result short codes** — comparisons against the excluded list and against `DDOTE` / `DDOTEL` are case-insensitive.
- **Mixed-case `category` values** — strict enum match on uppercase `A` / `I` / `F` (case-insensitive equality). Any other value fails the FR-015 fail-safe path.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST identify a relevant offence as any offence whose Home Office offence code matches one of: `RT88046`, `RT88526`, `RT88026`, `RT88530`, `RT88531`.
- **FR-002**: For each relevant offence, the system MUST identify a result line as the offence's **final result** by reading its `category` attribute and confirming it equals `'F'` (case-insensitive). The system MUST NOT infer final-result status from short-code-set membership. (Supersedes the original FR-002 wording.)
- **FR-003**: The system MUST treat the following final result short codes as **excluded** (no warning, when they appear on a `'F'` line): `wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`. Matching MUST be case-insensitive.
- **FR-004**: For each relevant offence, the system MUST inspect the result short codes linked to that offence (regardless of `category`) and detect the presence of `DDOTE` or `DDOTEL`. Matching MUST be case-insensitive.
- **FR-005**: The system MUST raise exactly one warning per relevant offence when **all** of the following hold:
  - The offence's Home Office code is in the relevant list (FR-001), AND
  - There is **at least one** result line on the offence with `category = 'F'` whose short code is **not** in the excluded list (combining FR-002 and FR-003), AND
  - **No** result line on the offence has short code `DDOTE` or `DDOTEL` (FR-004).
- **FR-006**: The warning message text MUST be exactly: `Check whether you need to add extended test disqualification with DDOTE (disqualification and extended test) or DDOTEL (disqualification for life and extended test)`.
- **FR-007**: Each warning MUST be linked to the specific offence id it concerns, so that the consumer (UI) can position the warning above the correct offence.
- **FR-008**: The warning's default severity MUST be `WARNING` (non-blocking — the user remains able to proceed and share).
- **FR-009**: The rule MUST be evaluated independently per relevant offence; multiple qualifying offences in the same hearing MUST each produce their own warning.
- **FR-010**: The rule MUST be subject to the existing runtime severity-ceiling model: an operator MUST be able to disable the rule entirely or cap its severity downward via the `validation_rule` database row, without redeployment. Severity MUST NOT be promoted upward by the ceiling.
- **FR-011**: The rule MUST NOT mutate the validation request and MUST NOT depend on the result of any other rule (rules evaluate independently per project architecture).
- **FR-012**: The validator's published result-line contract MUST carry a `category` attribute, restricted to the closed enum `{A, I, F}` (Ancillary, Intermediary, Final). The contract is the published API specification consumed by all callers of the validation endpoint.
- **FR-013**: The pre-share validation request issued by the user-facing results-entry application MUST populate each result line's `category` from the corresponding resolved-draft-line value already present in that application's domain model. Callers MUST NOT drop the field on the way out.
- **FR-014**: The share-time validation request issued by the share-results command handler MUST populate each result line's `category` from the corresponding shared-results-command line value already present in that application's domain model. Callers MUST NOT drop the field on the way out.
- **FR-015**: When a result line's `category` is missing, null, or any value other than `A`, `I`, `F` (case-insensitive), the line MUST NOT be treated as the offence's final result for the purposes of this rule. The rule MUST fail safe: if no qualifying `'F'` line is identifiable, no warning is produced. False positives on malformed/legacy data are unacceptable; missing the warning entirely on such data is acceptable for a transitional period until all callers are upgraded.

### Key Entities

- **Hearing (validation request)** — the unit of work submitted by the calling system. Contains a hearing identifier, one or more offences, one or more defendants, and the result lines recorded so far.
- **Offence** — a charge in the hearing. Identified by an offence id and a Home Office offence code (e.g. `RT88026`). Five specific Home Office codes mark an offence as **relevant** for this rule.
- **Result line** — a recorded outcome linked to a specific offence id. Carries a short code (e.g. `COEW`, `wdrn`, `DDOTE`, `DDOTEL`) and a `category` attribute (`A`, `I`, or `F`). Several lines may sit on the same offence — the one with `category = 'F'` is the final result; lines carrying disqualifications or procedural status sit alongside it.
- **Result line category** — closed enum on each result line: `A` (Ancillary, e.g. adjournment, listing), `I` (Intermediary, e.g. plea or hearing-internal), `F` (Final, the line that makes the offence inactive). Authoritative source of "is this the final result?" — supersedes the previous short-code-set heuristic.
- **Final result** — the result line whose `category = 'F'`. Used by FR-002, FR-003, FR-005.
- **Excluded final result short code** — the nine codes listed in FR-003 that suppress the warning.
- **Disqualification with extended test** — a result line whose short code is `DDOTE` (obligatory disqualification with extended test) or `DDOTEL` (obligatory disqualification for life with extended test). Presence on the relevant offence suppresses the warning.
- **Validation issue (warning)** — the output the rule produces. Severity `WARNING`, with the exact message text from FR-006 and a reference to the affected offence id.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every hearing containing at least one relevant offence with a non-excluded `category = 'F'` line and no `DDOTE`/`DDOTEL`, the validation response includes exactly one warning per such offence (true-positive rate = 100% on the qualifying offences in any test corpus).
- **SC-002**: For hearings where every relevant offence either has its `'F'` line carrying an excluded short code, or already carries `DDOTE`/`DDOTEL`, or has no `'F'` line at all (only ancillary/intermediary, e.g. an adjournment), the validation response from this rule contains zero warnings (false-positive rate = 0%). **BA scenario 5 (adjournment `'A'` on a relevant offence → no warning) is the canonical regression test for this criterion.**
- **SC-003**: A user who triggers the warning by saving and continuing reads the message and either adds the missing disqualification or proceeds to share — they are never blocked from sharing by this rule (0% block rate).
- **SC-004**: Within three months of release, the rate of post-share amendments and reshares attributed to a missing extended test disqualification on the five relevant offences is reduced relative to the equivalent three-month period before release. The exact target percentage will be set by the product owner once a baseline measurement is captured, but the metric MUST be reported.
- **SC-005**: Validation response time for a hearing containing relevant offences is indistinguishable to the user from validation of a hearing without them (the rule adds no perceptible latency to the existing validate call).
- **SC-006**: Operators can disable the rule or downgrade its severity via the existing runtime override mechanism within one working day, without a software release.

## Assumptions

- **Validation entry point** — the rule is consumed via the existing `POST /api/validation/validate` endpoint and follows the existing rule-engine contract. No new API endpoint is introduced.
- **Request payload shape** — the validation request provides offence Home Office codes, result-line short codes against the offence, and (per the 2026-04-28 revision) a `category` attribute on each result line. Offence ids let the consuming UI position the warning. The published contract (`api-cp-crime-hearing-results-validator`) is being extended to carry `category`; the library version is bumped so downstream services pull the new schema. The original assumption that "no upstream contract change is required" is **superseded**.
- **Final result identification** — the request payload marks the final result line explicitly via `category = 'F'`. This rule treats that line as authoritative and does **not** infer final-result status from short-code-set membership (supersedes the original heuristic).
- **Cross-repo coordination under DD-41656** — the contract change spans four repositories (validator API, validator service, share-time command handler, results-entry UI) but ships under a single Jira (no separate ticket per repo). Each repo carries its own `DD-41656`-prefixed branch. `cpp-context-hearing`'s DD-41656 branch is taken off `team/DD-41715-results-validator` (resolved during `/speckit-plan`): DD-41715's `ShareResultsCommandHandler` HTTP-call wiring is the carrier for DD-41656's data plumbing, and rebasing onto integration would orphan that work. If DD-41715 merges before DD-41656 lands, the DD-41656 branch will need a rebase — accepted churn.
- **BA test scenarios as authoritative behaviour** — `Disq with ext test scenarios.docx` (held with the BA) is the source of truth for positive and negative cases. Scenario 5 (adjournment `'A'` on a relevant offence → no warning) is the defining positive integration test for User Story 4 of this revision.
- **Trigger point in the UI** — AC1 names "Save and continue" and the "Manage hearing" tab as the user actions that invoke validation. Wiring those UI events to the validate endpoint is the responsibility of the consuming front-end and is out of scope for this service.
- **Rule packaging** — the new rule will be added as a YAML file under `src/main/resources/rules/`, following the existing naming convention (e.g. `DR-SENT-003.yaml` if assigned to the sentencing category, or a new category code such as `DR-DISQ-001`). No Java change is required if an existing preprocessor type fits; a new preprocessor and context record will be added if needed.
- **Severity model** — the rule's only condition has `severity: WARNING` in the YAML. The runtime severity-ceiling row in the `validation_rule` table behaves per project Constitution Principle VI: it caps downward only and never promotes.
- **Case-insensitive matching** — the input description uses mixed case (`ddote`, `WDRNOFF`, `COEW`). The rule treats all short-code comparisons as case-insensitive, consistent with how the existing DR-SENT-002 rule handles its filter list.
- **Per-offence evaluation** — the rule produces at most one warning per qualifying offence, regardless of how many defendants are charged with it. The warning is offence-anchored (it points at a specific offence id for UI placement), so two defendants linked to the same relevant offence still yield exactly one warning. This deliberately contrasts with `DR-SENT-002`, which groups by defendant; the data-model and preprocessor for this rule group by offence id, matching how the warning is presented to the user.
- **Out of scope** — no UI design work; no expansion of the relevant-offence list beyond the five Home Office codes given; no changes to which short codes count as excluded; no changes to the warning text or the rule id; no introduction of an `isFinalResult` boolean alongside `category` (the enum already carries the signal); no runtime call from the validator to the result-definitions reference-data service (`category` already crosses the wire via the share command, no additional service-to-service hop is needed). The original "no upstream changes to the offence-result data model" exclusion is **superseded** — the `category` field is a deliberate, in-scope upstream contract change for this revision.
