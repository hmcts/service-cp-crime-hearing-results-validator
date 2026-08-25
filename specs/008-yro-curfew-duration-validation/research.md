# Research: YRO Curfew Requirement Duration Validation

**Branch**: `dev/DD-42850-YRO-Duration`
**Date**: 2026-07-20

---

## Decision 1: Extend the existing `DR-YRO-001` rule rather than create a new rule

**Decision**: Add two new conditions (`DUR-YRC2`, `DUR-YRC1`) to the existing `DR-YRO-001.yaml` /
`YouthRehabilitationPreprocessor` / `YouthRehabilitationContext`, rather than authoring a second
YAML rule file.

**Rationale**: `DR-YRO-001` already matches on the exact same short-code families (`yroOrderShortCodes`,
`curfewShortCodes` = YRC2, `curfewTagShortCodes` = YRC1) needed here. The duration-mismatch check is
a different comparison over the *same* result-line groupings the preprocessor already builds, not a
new domain concept. This exactly mirrors the reference implementation: Community Order's `DD-41655`
extended `DR-COEW-001` in place rather than adding a second rule.

**Alternatives considered**: A standalone `DR-YRO-002.yaml` — rejected because it would duplicate the
defendant/offence grouping logic already in `YouthRehabilitationPreprocessor` and would double the
number of `RuleOverrideService` lookups per request for no behavioural benefit.

---

## Decision 2: Duration-mismatch prompt ref keys (hardcoded, **unverified**)

**Decision**: Reuse the existing `endDate` / `endDateOfTagging` prompt refs (already hardcoded in
`YouthRehabilitationPreprocessor`) as the *end*-date side of the comparison, and add these new
prompt ref keys for the *start*-date/period side:

| Requirement | New `promptRef` | Meaning |
|---|---|---|
| YRC2 (Curfew) | `startDate` | Start date of the curfew requirement |
| YRC2 (Curfew) | `curfewPeriod` | Curfew period |
| YRC1 (Curfew with electronic monitoring) | `startDateOfTagging` | Start date of tagging |
| YRC1 (Curfew with electronic monitoring) | `curfewAndElectronicMonitoringPeriod` | Curfew and electronic monitoring period |

**Rationale**: Matches the naming convention already established in this codebase
(`endDateOfTagging`, not CO's `endDateOfTag`) and mirrors the equivalent CO prompt refs
(`startDate`, `curfewPeriod`, `startDateOfTagging`, `curfewAndElectronicMonitoringPeriod`) used for
the identical requirement shape in the sibling Community Order service.

**Risk**: These are **unverified** against the real upstream `api-cp-crime-hearing-results-validator`
payload — same caveat CO's `DD-41655` extension carried. See Risk Register in plan.md. Unit tests
will fail fast (wrong key → prompt never matches → no violation ever raised, a false-negative that
must be caught in `qa` review / integration testing against a real/representative payload before ship).

---

## Decision 3: Period values may carry a unit suffix, not a bare integer

**Decision**: Parse the period prompt value with the pattern `^(\d+)\s*(Days?|Weeks?|Months?)$`
(case-insensitive), defaulting to `DAYS` when the value is a bare integer with no suffix. Use
`LocalDate.plus(amount, ChronoUnit)` (calendar-aware) rather than a fixed day-count conversion, so
month arithmetic (e.g. 31 Jan + 1 month) lands correctly on the calendar, not 30/31 days later.

**Rationale**: The Community Order duration-validation feature (`DD-41655`) shipped with a bare-integer
assumption and had to be corrected after discovering real payloads send `"90 Days"`, `"1 Months"`,
`"1 weeks"`. Since this YRO feature uses the identical upstream requirement/period field shape, we
adopt the corrected pattern from day one instead of re-discovering it.

**Alternatives considered**: Bare-integer-only parsing — rejected as a known-wrong starting point
given the sibling service's discovered payload shape.

---

## Decision 4: Calculated end date message format is `dd/MM/yyyy`

**Decision**: Format the calculated end date substituted into `${calculatedEndDate}` as `dd/MM/yyyy`
(e.g. `21/09/2026`), using `DateTimeFormatter.ofPattern("dd/MM/yyyy")`.

**Rationale**: Matches the exact format in the Jira acceptance criteria worked example
("...the end date should be 21/09/2026") and the corrected CO implementation (CO's `data-model.md`
originally said ISO-8601 but the shipped code and its API test assertions use `dd/MM/yyyy`).

---

## Decision 5: Duration checks run independently of the existing order-end-date (AC2) check

**Decision**: The duration-mismatch checks for YRC2/YRC1 do not depend on a parseable YRO order end
date. They must run even when the order's own `endDate` prompt is missing/unparseable — unlike the
existing AC2a/AC2b/AC2c checks, which are gated behind `if (orderEndDate == null) continue;`.

**Rationale**: The duration check compares a requirement's own start date + period against its own
end date — the parent order's end date is irrelevant to that comparison. Per spec Assumption, this
check is independent of and additive to the existing order-end-date check; a requirement may fail
either, both, or neither.

---

## Decision 6: Framework additions ported verbatim from the Community Order implementation

**Decision**: Port four framework-level changes, identical in shape to the already-shipped CO
`DD-41655` extension:

1. `ConditionDefinition` — add `private String calculatedValueSet;`
2. `RuleEvaluationContext` — add `default String getCalculatedValue(String setName, String offenceId) { throw new IllegalArgumentException(...); }`
3. `MessageTemplateResolver` — add a 6-arg `resolve(..., Map<String, String> extraPlaceholders)` overload that delegates to the existing 5-arg overload then substitutes any `${key}` tokens from `extraPlaceholders`
4. `CelValidationRule` — in the OFFENCE-level branch, when `condition.getCalculatedValueSet() != null`, pass `Map.of("calculatedEndDate", context.getCalculatedValue(calculatedValueSet, id))` to the resolver

**Rationale**: This is a generalisation of the rule-evaluation framework (any future duration-style
rule can reuse the same `calculatedValueSet` mechanism), not a YRO-specific hack — it is applied
identically across every `CelValidationRule` invocation regardless of which rule/context is active.
It does not violate Constitution Principle III (layered architecture / data-driven dispatch): no new
preprocessor registration is needed, and existing rules (`DR-SENT-002`, `DR-DISQ-001`) are unaffected
because `calculatedValueSet` defaults to `null` for their conditions.

**Alternatives considered**: Building the calculated-date string directly into `messageTemplate` via
string concatenation in the preprocessor — rejected because it would require per-condition Java
branching in `YouthRehabilitationContext.toCelContext()`/`getOffenceIdSet()` and would leak
presentation formatting into the CEL context layer, and it could not be reused by a future duration
rule without copy-pasting the pattern again.

---

## Decision 7: `parsePromptPeriod` lives in the shared `PreprocessorHelper`, not duplicated per-preprocessor

**Decision**: Add a new `parsePromptPeriod` (and a `ParsedPeriod(long amount, ChronoUnit unit)`
record) to `PreprocessorHelper`, alongside the existing `parsePromptDate`/`isRequirementViolated`
helpers, rather than as private methods inside `YouthRehabilitationPreprocessor`.

**Rationale**: Unlike the CO repo (where all preprocessor logic is private methods on
`CommunityOrderEndDatePreprocessor` itself), this repo has already extracted shared preprocessor
logic into `PreprocessorHelper` (used by both `CustodialPreprocessor` and
`YouthRehabilitationPreprocessor`). Continuing that pattern keeps the de-duplication intact and
means any future preprocessor needing period arithmetic does not have to re-implement it.

---

## Decision 8: `YRC3` and an AAR-equivalent are out of scope

**Decision**: Only YRC2 (Curfew) and YRC1 (Curfew with electronic monitoring) get duration-mismatch
conditions. YRC3 (Further curfew requirement made) is not touched, and there is no AAR-equivalent
requirement type on a YRO.

**Rationale**: The Jira acceptance criteria supplied for `DD-42850` (AC1, AC1A) name only YRC2 and
YRC1. YRC3 already has an AC2-style (order-end-date) condition in `DR-YRO-001`, but no duration ticket
covers it here. A Youth Rehabilitation Order has no alcohol-abstinence-monitoring requirement
equivalent to Community Order's AAR, so there is no third condition to add.

---

## Decision 9: Comparison semantics — strict inequality (mismatch), not `isAfter`

**Decision**: A duration mismatch is `!endDate.isEqual(expectedEndDate)` — any deviation (early or
late) is a violation. This differs from the existing AC2 checks, which use `isAfter` (only "too late"
is a violation; "too early" and "exactly equal" are both valid).

**Rationale**: Directly stated in the Jira acceptance criteria: "the 'End date' recorded... does not
equal the start date + curfew period − 1 day." There is only one correct value; both under- and
over-shooting it are data-entry errors.

---

## Decision 10: No new Flyway migration required

**Decision**: No new migration is added. `validation_rule` holds one row per rule id
(`DR-YRO-001`, already present via `V1.003__insert_dr_yro_001.sql`, `enabled: false`), not one row
per condition. Adding conditions to an existing rule's YAML does not require a new DB row.

**Rationale**: Confirmed by the identical precedent in the CO repo — `DD-41655` added three new
conditions to `DR-COEW-001` with zero new Flyway migrations.

---

## Decision 11: No `PreprocessingDefinition` changes required

**Decision**: No new fields are added to `PreprocessingDefinition`. The existing `curfewShortCodes`
(YRC2) and `curfewTagShortCodes` (YRC1) lists, already configured in `DR-YRO-001.yaml`, are reused
as-is for the duration checks.

**Rationale**: Unlike CO's original (pre-`DD-41655`) ticket, which introduced the short-code lists
for the first time, `DR-YRO-001` already has these lists from the existing AC2 checks — there is
nothing new to configure.

---

## Open items resolved from the spec

- `List<Prompt> prompts` confirmed present on `ResultLineDto` (upstream library `0.2.1`, currently
  in use in this repo — a later version than CO's `0.1.6`, but the `Prompt`/`ResultLineDto` shapes
  relevant here are unchanged).
- Multiple violations across defendants/offences: each produces its own `ValidationIssue`, consistent
  with the existing per-condition YAML+CEL evaluation model (no combined-message Java logic).
- Share button suppression / error display: entirely UI-owned, out of scope for this service (see
  spec.md "Service scope vs. UI scope").
