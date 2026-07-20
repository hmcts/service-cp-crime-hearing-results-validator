# Phase 0 Research: Imprisonment Result Age Restriction (DD-42950)

## R1 — Defendant date of birth is not in the current contract (blocking prerequisite)

**Decision**: Design and build this rule now, against an assumed `dateOfBirth` field on
`DefendantDto`, but treat the upstream contract change as a separate, blocking prerequisite
tracked outside this repository. The preprocessor fails safe (produces no context, no error) for
any defendant whose date of birth cannot be read — which, until the upstream field ships, is
every defendant, so the rule is merged but dormant.

**Rationale**: Direct inspection of the resolved `api-cp-crime-hearing-results-validator:0.2.4`
jar confirms `DefendantDto` has exactly four fields: `defendantId`, `firstName`, `lastName`,
`masterDefendantId`. There is no date of birth anywhere in the request model. Constitution
Principle I is explicit: *"This service does not own an OpenAPI spec; request/response DTOs are
imported from the external `api-cp-crime-hearing-results-validator` dependency... Changes to
those DTOs belong in that upstream repository, not here."* Building the field into this repo's
copy of the DTO is not an option.

**Precedent**: `specs/001-extended-test-disq-warning` hit the identical situation for the
`category` field on `ResultLineDto` (missing from the contract, needed for a rule gate). That
spec's resolution — add the field upstream, bump the dependency version, then wire the
preprocessor to read it — is the template this plan follows.

**Alternatives considered**:
- *Compute age from some other existing field* — rejected, no birth-date-adjacent data exists
  anywhere in `DefendantDto`, `OffenceDto`, or `ResultLineDto`.
- *Defer the entire feature until the dependency ships* — rejected; writing the rule, preprocessor,
  and tests now (against a not-yet-existing getter) still requires the dependency to compile, so
  this doesn't actually save time, and this repo's code review / spec-validator loop can still
  run over the CEL/YAML shape and the non-age parts of the preprocessor (grouping, short-code
  filtering) independently of the getter's existence. **Practical consequence**: the Java code in
  this feature cannot compile until the dependency version bump lands (see
  `contracts/upstream-dependency.md`) — implementation tasks for this feature are blocked on that
  external change landing first, not merely "nice to have before shipping."

## R2 — Rule id / category: `DR-AGE-001`, not `DR-SENT-003`

**Decision**: Category `AGE`, id `DR-AGE-001`.

**Rationale**: `DR-SENT-002` (concurrent/consecutive) is about the *structural correctness* of a
custodial sentence across a defendant's offences. This new rule is about *defendant eligibility*
for any imprisonment-type result at all, gated on age — a different policy question with a
different owner/reviewer (safeguarding/legal minimum-age policy vs. sentencing-structure policy).
`specs/001-extended-test-disq-warning/research.md` (R3 in that document) established the same
reasoning when it chose `DR-DISQ-001` over reusing the `SENT` category for a differently-shaped
policy concern, and that reasoning transfers directly here.

**Alternatives considered**:
- `DR-SENT-003` — rejected per the above; also would misleadingly group an eligibility gate
  alongside sentence-structure checks for anyone scanning the `SENT` category.

## R3 — Preprocessing approach: new preprocessor, not an extension of `CustodialPreprocessor`

**Decision**: A new `ValidationPreprocessor` (`AgeRestrictedImprisonmentPreprocessor`, qualifier
`age-restricted-imprisonment`) and a new context record (`AgeRestrictedResultContext`), rather
than adding fields to `DefendantContext` / reusing `custodial-concurrent-consecutive`.

**Rationale**: `DefendantContext` and `CustodialPreprocessor` are shaped around counting
concurrent/consecutive/no-info *offence combinations*; none of that logic or those counts are
relevant here. This rule needs exactly two things per defendant: (a) does at least one offence
carry a qualifying short code (`IMP`/`EXTIVS`/`SPECC`), and (b) is the defendant under 21 on the
hearing date. Bolting an unrelated boolean onto `DefendantContext` would couple two independent
policy domains in one preprocessor, contradicting the precedent already set by
`CtlMissingPreprocessor` and `DisqualificationExtendedTestPreprocessor` each owning their own
context shape. The registry (`PreprocessorRegistry`) already supports N preprocessors with zero
marginal wiring cost (Spring auto-collects `List<ValidationPreprocessor>`), so there is no
structural cost to a fourth.

**Alternatives considered**:
- *Extend `DefendantContext` with `isUnder21`/`hasImprisonmentResult` fields* — rejected: couples
  an unrelated concern into a context whose existing fields are specific to the concurrent/
  consecutive domain, and would force `DR-SENT-002` and `DR-AGE-001` to share
  `skipWhenGroupCount`/`groupBy` semantics that don't apply to the age check.

## R4 — Aggregate "This affects: <names>" mechanism: reuse `errorMessageTemplate` + `affectedDefendantSet`

**Decision**: Model the condition on `DR-SENT-002`'s `AC2` exactly: `severity: ERROR`,
`validationLevel: OFFENCE` (required — `ValidationIssue`'s javadoc states *"Issues with severity
ERROR must always have validationLevel OFFENCE"*), `affectedOffenceSet` pointing at the
defendant's qualifying offence ids, `affectedDefendantSet: "defendantId"`, and an
`errorMessageTemplate` containing `${defendantNames}` for the aggregate, service-level list.

**Rationale**: `DefaultValidationService.evaluateRulesWithMdc()` already groups every triggered
`ValidationIssueResult` with the same `ruleId + errorMessage` key and appends each context's
`affectedDefendantName()` into one list, resolved once via
`MessageTemplateResolver.resolveDefendantNames()` — producing exactly the "This affects: A, B and
C" shape the spec asks for, with zero new service-layer code. Because the preprocessor groups
result lines **per defendant** (not per offence), a defendant with two qualifying offences yields
exactly one context and therefore appears exactly once in the aggregate list — satisfying the
spec's "reported once, not once per offence" edge case without extra dedup logic.

**Alternatives considered**:
- *Emit one `ValidationIssueResult` per qualifying offence* — rejected: would duplicate a
  defendant's name in the aggregate list for every extra qualifying offence, since
  `appendDefendantName` does not dedup. Per-defendant grouping avoids this by construction.

## R5 — "There is a problem" heading

**Decision**: Treat "There is a problem" as the existing GOV.UK Design System error-summary panel
heading, rendered by the consuming UI for any blocking validation error — not literal text this
rule needs to emit. This rule's `messageTemplate`/`errorMessageTemplate` supplies only "The
defendant is under 21 years of age and cannot receive a sentence of imprisonment" (+ the
aggregate "This affects" sentence); the panel heading is out of scope for this backend service.

**Rationale**: No other rule in this service (`DR-SENT-002`, `DR-CTL-001`, `DR-DISQ-001`) emits a
"There is a problem"-style heading in its message templates; it is a standard UI-layer
convention, consistent with the AC's phrasing that separates the heading from the message body.

**Alternatives considered**: Embed "There is a problem" into the `errorMessageTemplate` itself —
rejected, would duplicate a UI-owned heading in backend-owned text and break if the UI changes its
generic error-summary wording.
