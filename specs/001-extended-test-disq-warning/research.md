# Phase 0 Research — DD-41656 Extended Test Disqualification Warning

This document records the design decisions taken during planning, why each one was made, and what alternatives were rejected. There were no `[NEEDS CLARIFICATION]` markers in the spec; the questions below surfaced from reading the existing rule engine against the feature requirements.

---

## R1. Should we reuse `CustodialPreprocessor` or add a new preprocessor?

**Decision**: Add a new `DisqualificationExtendedTestPreprocessor`.

**Rationale**:
- `CustodialPreprocessor` groups by defendant (or master defendant) and computes counts about concurrent/consecutive sentencing (`noInfoCount`, `hasBothCount`, `hasPrimaryCount`). None of those are relevant to "is this offence missing DDOTE/DDOTEL?"
- The new rule is fundamentally per-offence, not per-defendant: each qualifying offence produces its own warning.
- The new rule needs a different filter set (excluded final-result codes, DDOTE/DDOTEL codes) and a different relevance gate (Home Office offence codes, not result short codes).
- Forcing the existing preprocessor to do both jobs would balloon `DefendantContext` with unrelated fields and turn the YAML's `preprocessing.type` into dead documentation.

**Alternatives considered**:
- *Extend `CustodialPreprocessor` with a mode flag.* Rejected — violates Single Responsibility, fails the Principle I "BAs can read the YAML" smell test (the YAML would carry both filter sets and a hidden behaviour flag), and makes the unit-test surface ambiguous.
- *Make the rule programmatic (no YAML).* Rejected — violates Constitution Principle I (YAML/CEL Rule-First, NON-NEGOTIABLE).

---

## R2. Adding a second preprocessor triggers Constitution Principle III's "remove the hard-wiring" rule

**Decision**: The plan has two ordered phases. Phase A refactors `CelValidationRule` and `ValidationRuleAutoConfiguration` to dispatch preprocessors through a Spring-aware `PreprocessorRegistry` keyed by the YAML's `preprocessing.type`. Phase B adds the new preprocessor + rule. The DR-SENT-002 rule must continue to pass all existing tests after Phase A with no behaviour change.

**Rationale**: Principle III is unambiguous: *"Hard-wiring a single preprocessor implementation into `CelValidationRule` is a transitional state; it MUST be removed before any second preprocessor type ships."* Shipping the new rule first and the refactor second would knowingly violate the constitution.

**Alternatives considered**:
- *Squash both phases into a single commit series.* Rejected — separating them keeps the no-behaviour-change refactor reviewable on its own and makes a clean revert possible if either step regresses.
- *Skip the refactor and inject both preprocessors directly into `CelValidationRule`.* Rejected — same hard-wiring problem, scaled by N rules. The principle exists to stop exactly that.

---

## R3. How is "the offence's final result" identified, given the upstream DTO has no explicit flag?

**Context**: `ResultLineDto` (from `libs.api.hearing.results.validator`) has `id`, `shortCode`, `label`, `defendantId`, `offenceId`, `isConcurrent`, `consecutiveToOffence`. There is no `isFinalResult` boolean and no `makesOffenceInactive` flag. AC1 asks us to detect an offence whose *final result* is not in the excluded list and that has no DDOTE/DDOTEL.

**Decision**: Treat the question on a per-offence basis as a property of the *set of result lines* on that offence, without needing to single one out as "the" final result:

- An offence is **excluded** if **any** result line on it has a short code in the excluded set (`wdrn`, `WDRNOFF`, `dism`, `dine`, `dini`, `disch`, `disc`, `ctrof`, `iremfile`, case-insensitive). These short codes are themselves the "did not proceed" outcomes.
- An offence has **DDOTE/DDOTEL** if **any** result line on it has shortCode `DDOTE` or `DDOTEL` (case-insensitive).
- An offence has a **non-excluded final result** if it has at least one result line whose short code is **not** in the excluded set and **not** `DDOTE` / `DDOTEL`. (i.e. there is at least one substantive non-suppressing result on the offence.)

The rule fires when:

```
offence.offenceCode ∈ { RT88046, RT88526, RT88026, RT88530, RT88531 }
  AND offence has ≥1 non-excluded, non-DDOTE/DDOTEL result line
  AND offence has 0 result lines with a short code in the excluded set
  AND offence has 0 result lines with short code DDOTE or DDOTEL
```

**Rationale**:
- Matches the literal acceptance-criteria reading: `wdrn` etc. are the very codes that mean "did not proceed", so if a result line carries one, the case did not proceed and the rule is correctly suppressed.
- Avoids the upstream DTO change that would otherwise be required to add an `isFinalResult` flag — Constitution Principle I steers DTO changes to the upstream repo, which would block this feature on cross-repo coordination.
- Conservative on false positives: an offence with no result lines at all does not fire (the "≥1 non-excluded result" gate filters it out), matching the spec's "no final result yet" edge case.
- Conservative on false negatives: an offence that has a non-excluded result *and* is also marked DDOTE/DDOTEL correctly suppresses (DDOTE/DDOTEL takes precedence over the relevance gate).

**Alternatives considered**:
- *Add `ResultLineDto.isFinalResult` upstream.* Rejected for v1 — extra cross-repo work, blocks this feature, and the existing data is enough.
- *Treat the most recently added result line as final.* Rejected — `ResultLineDto` has no creation timestamp and the API spec gives no ordering guarantee.

**Open follow-up** (not blocking): if a future rule legitimately needs the canonical "final result" for an offence, that should be a single, explicit upstream DTO change rather than rule-by-rule heuristics.

---

## R4. What is the rule's identifier — `DR-SENT-003` or a new `DR-DISQ-NNN`?

**Decision**: `DR-DISQ-001`.

**Rationale**:
- The existing rule (`DR-SENT-002`) is about *sentencing arithmetic* (concurrent/consecutive). The new rule is about *disqualification*. Putting them in the same `SENT` family because both relate to sentencing would conflate two distinct policy areas and obscure intent for BAs reading the YAML.
- `DISQ` reads cleanly in dashboards, in the `validation_rule` DB table, and in the `RuleDetailResponse` JSON.
- The `id` is a free-form `VARCHAR(20)` in the DB and a free string in the YAML — there is no schema constraint on the prefix.
- Picking `001` for the new family leaves room for further disqualification rules without renumbering.

**Alternatives considered**:
- *`DR-SENT-003`* — rejected for the conflation reason above.
- *`DR-RT-001`* (Road Traffic) — rejected as too narrow; future disqualification rules might cover offences outside the Road Traffic Act.

---

## R5. How does the warning get linked to a specific offence id for UI placement?

**Context**: `ValidationIssue` has `affectedOffences: List<AffectedOffence>` where each `AffectedOffence` has at minimum an `offenceId`. AC1A says the warning is displayed *above the offence with the missing or incorrect result*.

**Decision**: The new preprocessor groups the request by offence id and produces **one `DisqualificationContext` per qualifying offence** (rather than one per defendant). The CEL condition fires once per context, producing one `ValidationIssue` whose `affectedOffences` list contains that single offence id.

The condition's `affectedOffenceSet` field references a list-name that the context exposes through `getOffenceIdSet`, returning the singleton list `[offenceId]`. The existing `OffenceDisplayHelper` resolves it to the human-readable order index.

**Rationale**:
- Producing one issue per qualifying offence matches the spec's "exactly one warning per qualifying offence" requirement (FR-009) and gives the UI an offence-specific anchor for placement.
- It avoids cramming N offences into a single issue's `affectedOffences` list, which the UI would then have to demultiplex by hand.
- It reuses the existing message-template + offence-display machinery without modification.

**Alternatives considered**:
- *One issue per defendant with multiple offences in `affectedOffences`.* Rejected — wrong granularity for the spec; the UI design wants one warning above each affected offence.
- *Pack all qualifying offences in the hearing into a single issue.* Rejected — same reason.

---

## R6. CEL variables exposed by the new preprocessor

**Decision**: `DisqualificationContext.toCelContext()` exposes:

| Variable | Type | Meaning |
|----------|------|---------|
| `qualifyingCount` | Long | `1` if this offence qualifies (relevant + non-excluded final result + no DDOTE/DDOTEL), else `0`. With one context per offence, the only useful value is 0 or 1. |
| `relevantCount` | Long | `1` if this offence has a relevant Home Office code, else `0`. Useful for diagnostics / future conditions. |
| `excludedFinalCount` | Long | `1` if any result on this offence has an excluded short code, else `0`. Useful for diagnostics. |
| `disqExtTestCount` | Long | `1` if any result on this offence has shortCode DDOTE or DDOTEL, else `0`. Useful for diagnostics. |

**Rationale**:
- The CEL condition `qualifyingCount > 0` is single, boolean, and BA-readable in the YAML.
- The auxiliary counts give future rule authors something to compose without adding a new preprocessor — for example, a future "warn that DDOTE was added but the offence is withdrawn" rule could read `disqExtTestCount > 0 && excludedFinalCount > 0`.
- Keeping the CEL variables `Long`-valued matches the existing `DefendantContext.toCelContext()` convention; `CelExpressionEvaluator` is already configured for that shape.

**Alternatives considered**:
- *Expose a single `qualifies` boolean.* Rejected — diverges from the existing `Long`-valued convention with no benefit (CEL coerces `> 0` cleanly).
- *Expose only `qualifyingCount`.* Rejected — auxiliary counts are nearly free, and the project memory note `project_ac2_update.md` shows that conditions get tweaked over time without changing preprocessors. Better to have the diagnostics already in the context map.

---

## R7. Affected-offence-set name in the YAML condition

**Decision**: `affectedOffenceSet: "qualifyingOffenceIds"` returning the singleton list `[offenceId]` for that context. `DisqualificationContext.getOffenceIdSet("qualifyingOffenceIds")` returns the list; any other set name throws `IllegalArgumentException`, matching the `DefendantContext` pattern.

**Rationale**: parity with `DefendantContext.getOffenceIdSet` keeps `MessageTemplateResolver` and `OffenceDisplayHelper` working unchanged.

---

## R8. Message template

**Decision**: The message template in the YAML is the literal text from AC1A, with no placeholders:

```yaml
messageTemplate: >-
  Check whether you need to add extended test disqualification with DDOTE
  (disqualification and extended test) or DDOTEL (disqualification for life
  and extended test)
```

**Rationale**:
- AC1A specifies the exact wording — no offence number is interpolated into the message itself; the offence linkage is via `affectedOffences` (R5).
- This is simpler for BAs to review and lets the UI position the warning above the correct offence using the structured `affectedOffences` field.

**Alternatives considered**:
- *Include `${offenceNumbers}` so the message reads "Check offence 2 whether you need to…".* Rejected — diverges from AC1A's literal wording and duplicates the offence-id information that `affectedOffences` already carries.

---

## R9. Severity in the YAML

**Decision**: `severity: WARNING` for the single condition. No DB row is required at release time — the absence of an override means the YAML severity stands. If ops wants to silence the rule entirely, they insert a `validation_rule` row with `enabled = false`.

**Rationale**: Constitution Principle VI (severity ceiling caps downward only) means the YAML must hold the highest severity the condition should ever produce. AC1 says the user can still progress and share — therefore non-blocking — therefore `WARNING`, not `ERROR`.

---

## R10. Test fixture data: how does the rule react to the existing DR-SENT-002 test corpus?

**Decision**: Add a regression test that runs both rules against a hearing crafted to trigger DR-SENT-002 *and* DR-DISQ-001 simultaneously (e.g. one defendant, two offences, one with `RT88026 + COEW + IMP`, the other with `IMP` plus missing concurrent/consecutive info). The expected output is one `DR-SENT-002` ERROR plus one `DR-DISQ-001` WARNING — proving the rules evaluate independently per Constitution Principle III and FR-011.

**Rationale**: Catches the regression where the registry refactor accidentally short-circuits one rule when the other matches.

---

## Summary of design decisions

| # | Question | Decision |
|---|----------|----------|
| R1 | Reuse or new preprocessor? | New `DisqualificationExtendedTestPreprocessor` |
| R2 | Refactor preprocessor dispatch? | Yes, before the new preprocessor ships (Constitution III) |
| R3 | "Final result" detection | Per-offence rule over the result-line set; no upstream DTO change |
| R4 | Rule id | `DR-DISQ-001` |
| R5 | Offence-level linkage | One context (and one `ValidationIssue`) per qualifying offence |
| R6 | CEL variables | `qualifyingCount`, `relevantCount`, `excludedFinalCount`, `disqExtTestCount` |
| R7 | Affected set name | `qualifyingOffenceIds` |
| R8 | Message template | Literal AC1A text, no placeholders |
| R9 | Severity | `WARNING` (non-blocking) |
| R10 | Cross-rule regression | Add a test that runs both rules together |
