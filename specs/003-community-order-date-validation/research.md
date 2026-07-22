# Research: Community Order End Date Validation (DR-COEW-001)

**Branch**: `DD-41653-community-order-date-validation`  
**Date**: 2026-05-20  
**Feature spec**: [spec.md](spec.md)

---

## Decision 1 — Preprocessor Grouping Strategy

**Decision**: Group per-defendant (one `CommunityOrderContext` per `defendantId`), aggregating violations across all offences that belong to that defendant.

**Rationale**: The error summary says "This affects: Defendant X" — the unit of reporting is the defendant. Grouping per-defendant matches `CustodialPreprocessor`'s approach and allows the CEL variable map (violation counts) and affected offence-id sets to accumulate across all of a defendant's offences in a single pass.

**Alternatives considered**:
- *Per-offence grouping* (Rejected — the UI requires a defendant-level error that lists which defendants are affected, not one error per offence. Per-offence would produce up to N errors for N offences with the same violation type on the same defendant.
- *Per-(defendant, offence) grouping*: Rejected — adds complexity without gain; the defendant is still the natural error scope.

---

## Decision 2 — Multiple AC2 Violations: Separate Conditions or Single Combined Condition

**Decision**: Separate YAML `conditions` per requirement type (AC2a=CUR, AC2b=CURE, AC2c=CURA, AC2d=AAR) — each fires its own `ValidationIssue` when the corresponding violation count > 0.

**Rationale**: The YAML+CEL engine produces one `ValidationIssue` per triggered `condition`. Separate conditions are the natural fit for the engine and give maximum rule configurability — each condition can be individually enabled/disabled or severity-capped via the `validation_rule` DB table. CEL cannot dynamically concatenate strings, so a "combined message" would require Java-side post-processing, violating Constitution Principle I. Spec FR-003, FR-004, A-005, and the Edge Cases section have been updated to reflect "one error per violated requirement type" semantics, matching this architectural decision.

**Alternatives considered**:
- *Single condition with a dynamic combined message*: Rejected — CEL expressions cannot dynamically concatenate strings across requirement types; achieving this would require moving business logic into Java, violating Constitution Principle I.
- *Post-processing aggregation layer*: Rejected — adds complexity, breaks the clean separation between rule evaluation and issue emission, and removes per-requirement-type runtime configurability.

---

## Decision 3 — Prompt Ref Names: Hardcoded or Configurable in YAML

**Decision**: Hardcode the `promptRef` lookup keys in `CommunityOrderEndDatePreprocessor` Java code:
- Community orders (COEW/COS/CONI) → `"endDate"`
- CUR → `"endDate"`, CURE → `"endDateOfTag"`, CURA → `"endDate"`, AAR → `"until"`

**Rationale**: The `promptRef` values are part of the upstream API contract (`api-cp-crime-hearing-results-validator`), not a business policy decision. They are stable and tied to the `ResultLineDto.prompts` schema. Adding YAML config fields for them would introduce unnecessary coupling and complexity with no practical benefit — no BA would ever change `"endDateOfTag"` to something else without an API change.

**Alternatives considered**:
- *YAML-configurable `promptRefs` map in `PreprocessingDefinition`*: Rejected — prompt ref names are API-contract values, not policy values. They belong in Java, not YAML.

---

## Decision 5 — `PreprocessingDefinition` Extension

**Decision**: Add five new `List<String>` fields to `PreprocessingDefinition`:
- `communityOrderShortCodes`
- `curfewShortCodes`
- `curfewTagShortCodes`
- `furtherCurfewShortCodes`
- `alcoholAbstinenceShortCodes`

**Rationale**: `PreprocessingDefinition` is the YAML-to-Java bridge. Adding fields here keeps the YAML short-code lists authoritative (Constitution Principle I) and means a BA can change which short codes trigger the rule by editing YAML without touching Java. Each existing preprocessor adds its own fields here; this is the established pattern.

**Alternatives considered**:
- *Hardcode short codes in the preprocessor*: Rejected — violates Principle I; changing COEW to a new code would require a Java change and a redeploy.
- *Add `@JsonIgnoreProperties(ignoreUnknown = true)` to `PreprocessingDefinition`*: Rejected — silently discards unknown YAML fields, masking configuration typos. Explicit fields are safer.

---

## Decision 6 — Date String Format from `promptValue`

**Decision**: Parse `promptValue` as ISO-8601 (`yyyy-MM-dd`) using `LocalDate.parse(promptValue)`. If `promptValue` is null, blank, or unparseable, log a `WARN` and skip the date comparison for that prompt (treat as no violation for that requirement on that offence).

**Rationale**: ISO-8601 is the standard wire format for `LocalDate` in HMCTS services. Defensive handling (skip vs. throw) ensures one malformed prompt does not abort the entire validation run and produce a 500 response.

**Alternatives considered**:
- *Throw on parse failure*: Rejected — a single bad date from the UI would make the entire hearing unvalidatable; log and skip is more resilient.
- *Use a configurable date format*: Rejected — ISO-8601 is the API contract; no need for configurability.

---

## Decision 7 — No New API Contract / No `contracts/` Artefact

**Decision**: No new `contracts/` directory needed for this feature.

**Rationale**: The service exposes one existing endpoint (`POST /api/validation/validate`) whose request/response schema is owned by the upstream `api-cp-crime-hearing-results-validator` library. This feature adds new `ValidationIssue` entries to the existing `errors` list in `DraftValidationResponse` and begins populating `affectedDefendants` — a field that was already declared in the `ValidationIssue` schema but had never been set. No new endpoints, no new consumers, no upstream schema changes required.

---

## Resolved Unknowns Summary

| # | Unknown | Resolution |
|---|---------|------------|
| 1 | Prompts field availability | `List<Prompt> prompts` on `ResultLineDto` — confirmed in 0.1.6; `Prompt.getPromptRef()` / `getPromptValue()` |
| 2 | Prompt ref key names | Hardcoded: `endDate`, `endDateOfTag`, `until` |
| 3 | Multiple violations display | Separate condition per requirement type; UI groups them |
| 4 | Share button scope | Hearing-level (hidden if any defendant has errors) |
| 5 | AC1 scope | Out of scope; separate ticket |
| 6 | Grouping unit | Per-defendant |

---

## Extension (2026-07-06, Jira `DD-41655`) — Requirement Duration End Date Validation

Adds User Stories 4–7 to the spec: a CUR/CURE/AAR requirement's own recorded end date must
match its calculated duration (`Start date + period − 1 day`, or `hearing date + days − 1 day`
for AAR). Independent of and additive to AC2 (Decisions 1–7 above), which compares a
requirement's date against its *parent order's* end date.

### Decision 8 — Extend `DR-COEW-001` Rather Than Create a New Rule

**Decision**: Add three new conditions and extend `CommunityOrderEndDatePreprocessor` /
`CommunityOrderContext` in place, rather than authoring a new `DR-COEW-002.yaml` with its own
preprocessor and context type. `CURA` is excluded — the new acceptance criteria (AC1, AC1A, AC2 of
`DD-41655`) only name CUR, CURE and AAR.

**Rationale**: The duration-mismatch check reads from the same result lines, grouped by the same
defendant/offence keys, already scanned by this preprocessor for AC2. Extending it avoids a second
full pass over `resultLines` and mirrors the existing "one rule, N independent per-requirement-type
conditions" pattern (AC2a–d).

**Alternatives considered**: A new rule/preprocessor pair — rejected as needless duplication of the
grouping logic for no isolation benefit; the two checks are independently enable/disable-able as
separate CEL conditions either way.

### Decision 9 — Duration-Mismatch Comparison Semantics

**Decision**: A duration mismatch is **any inequality** between the recorded end date and the
calculated end date (`startDate.plusDays(period - 1)`), not just "later than". Both an early and a
late recorded date are violations. For AAR, the reference start point is the hearing date
(`request.getHearingDay()`), not a requirement-specific prompt — CUR and CURE each use their own
"Start date" / "Start date of tagging" prompt.

**Rationale**: AC1/AC1A/AC2 all say the error triggers when the recorded date "does not equal" the
calculated value — a strict equality check, deliberately different from AC2's `isAfter`-only
semantics (Decision 6), because this check validates the requirement's own internal consistency,
not a boundary against a parent order.

**Alternatives considered**: Reusing `isAfter`-only semantics — rejected; it would silently accept
an end date entered too early, which the acceptance criteria explicitly treat as equally wrong.

### Decision 10 — New Prompt Ref Keys (AAR key confirmed against real payload)

**Decision**: Hardcode four new `promptRef` keys, following the naming convention already used for
`endDateOfTagging` (Decision 3):

| Requirement | Start-point prompt | Period prompt |
|---|---|---|
| CUR | `"startDate"` | `"curfewPeriod"` (integer days) |
| CURE | `"startDateOfTagging"` | `"curfewAndElectronicMonitoringPeriod"` (integer days) |
| AAR | *(uses `request.getHearingDay()`, no prompt)* | `"numberOfDaysToAbstainFromConsumingAnyAlcohol"` (integer days) |

**Rationale**: Consistent with Decision 3 — these are stable upstream API-contract values, not YAML
policy, so they stay hardcoded in Java rather than becoming YAML config.

**Risk (CUR/CURE)**: `"startDate"`, `"curfewPeriod"`, and `"curfewAndElectronicMonitoringPeriod"` are
still **assumed** from the acceptance-criteria field labels and the existing naming convention — not
yet confirmed against a real payload. Carried into the Risk Register; must be verified early during
implementation (unit tests will fail fast if wrong).

**AAR — confirmed wrong, then fixed**: the original assumption `"numberOfDaysToAbstain"` was
disproved by a real payload, which sends `"numberOfDaysToAbstainFromConsumingAnyAlcohol"`. Because
the key didn't match, `CommunityOrderEndDatePreprocessor` silently found no period prompt and
`recordDurationMismatchIfAny` returned early — the `DUR-AAR` condition never fired even when the
`until` date was genuinely wrong. Fixed in `CommunityOrderEndDatePreprocessor.PROMPT_DAYS_TO_ABSTAIN`
plus the corresponding fixtures in `CommunityOrderEndDatePreprocessorTest`,
`CommunityOrderEndDateRuleIntegrationTest`, and `RequirementDurationApiHttpLiveTest` (DD-41655
follow-up).

### Decision 11 — Injecting a Computed Value (Calculated Date) into a Message Template

**Decision**: Extend `MessageTemplateResolver.resolve(...)` with a backward-compatible overload
taking an additional `Map<String, String> extraPlaceholders` parameter — for each entry, replaces
`${key}` in the template with its value. `RuleEvaluationContext` gains a new default method
`getCalculatedValue(String setName, String offenceId)` (throws `IllegalArgumentException` by
default, mirroring `getOffenceIdSet`); `CommunityOrderContext` overrides it against three new
per-offence `Map<String, String>` fields. `ConditionDefinition` gains an optional YAML field
`calculatedValueSet`; a new `${calculatedEndDate}` token is introduced. `CelValidationRule`'s
existing per-offence message-resolution lambda (OFFENCE-level branch only) looks up and passes this
value when `calculatedValueSet` is configured on the condition. The aggregate `errorMessageTemplate`
(one message per defendant/condition, used for the top-of-screen summary) does **not** receive it —
a single summary line cannot show a distinct calculated date per offence.

**Rationale**: No existing placeholder covers a per-offence, rule-specific computed value — the
framework only resolves `${offenceNumber}`, `${defendantName}`, and (separately) `${defendantNames}`.
The acceptance criteria require the *actual* calculated date inline (e.g. "...should be
30/09/2026"), so this capability is required, not incidental scope creep. Generalising via a named
`calculatedValueSet` (rather than a one-off "curfew date" field) keeps the mechanism reusable for
any future rule needing a per-offence computed value, matching how `affectedOffenceSet` already
generalises per-condition offence scoping.

**Alternatives considered**:
- *Bake the fully-formatted sentence into the preprocessor and treat it as a stand-in offence-id* —
  rejected; conflates data with YAML-owned message wording (Constitution Principle I).
- *One-off `${curfewCalculatedEndDate}`-style field wired directly into `CelValidationRule`* —
  rejected in favour of the generic, named-set design for reusability.

### Decision 12 — CURA Excluded

**Decision**: The three new conditions cover CUR, CURE, and AAR only. `CURA` ("Further curfew
requirement made") keeps its existing AC2c order-end-date check (User Story 1) but has no
duration-mismatch counterpart.

**Rationale**: `DD-41655`'s acceptance criteria (AC1, AC1A, AC2) name only Curfew (non-EM), Curfew
with EM, and AAM. Adding a symmetrical CURA check was not requested (YAGNI).

### Extension — Resolved Unknowns

| # | Unknown | Resolution |
|---|---------|------------|
| 7 | New prompt ref keys for start dates / periods | Hardcoded, **unverified** — see Risk Register in plan.md |
| 8 | Duration-mismatch comparison semantics | Strict equality (not `isAfter`); an off-by-one (forgetting `− 1 day`) is a violation |
| 9 | Calculated-date placeholder mechanism | New generic `extraPlaceholders` param on `MessageTemplateResolver`; `${calculatedEndDate}` token, wired via `calculatedValueSet` |
| 10 | Rule/preprocessor reuse vs. new rule | Extend `DR-COEW-001` / `CommunityOrderEndDatePreprocessor` / `CommunityOrderContext`; no new rule ID |
