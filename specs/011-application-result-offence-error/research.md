# Phase 0 Research: Application Results Recorded Against Offences – ERROR Validation

## R1 — No upstream DTO change needed (unlike DD-42950)

**Decision**: Build this rule entirely against the existing `ResultLineDto` fields —
`offenceId`, `shortCode`, `label`, `defendantId` — with no change to the external
`api-cp-crime-hearing-results-validator` contract.

**Rationale**: Direct inspection of the resolved `api-cp-crime-hearing-results-validator:26.25`
jar shows `ResultLineDto` has exactly: `resultLineId`, `shortCode`, `label`, `defendantId`,
`offenceId`, `isConcurrent`, `consecutiveToOffence`, `category`, `prompts`. There is no
`applicationId` field, and no `ApplicationDto` exists anywhere in the model package (confirmed
by listing every class under `uk/gov/hmcts/cp/openapi/model/` in the jar). The request schema
this service receives has exactly one attachment point for a result line: `offenceId`. This
means "recorded against an offence" is not an ambiguous state to detect — every `ResultLineDto`
this service ever sees is, by construction of the current contract, offence-linked. This rule's
job is therefore purely: is this result line's `shortCode` one that should never have reached an
offence at all? Whatever upstream UI/domain concept of "application" exists is out of band for
this service's current contract — this service does not need to see or validate applications
directly to enforce "this code must not appear on an offence."

**Alternatives considered**:
- *Wait for an upstream `applicationId` / `ApplicationDto` addition, mirroring the DD-42950
  precedent* — rejected. That precedent applied because the *positive* fact needed
  (`dateOfBirth`) genuinely did not exist anywhere in the contract. Here, the fact needed
  (whether a result line is attached to an offence) is already fully and unambiguously
  determined by the existing `offenceId` field — there is nothing missing to add.
- *Treat a null/blank `offenceId` as "attached to an application, skip"* — kept as a defensive
  guard in the preprocessor (see data-model.md) even though today's schema gives every result
  line a non-null `offenceId`; this future-proofs the rule for the day an application concept
  *is* added to the contract, without requiring a design change now.

## R2 — Rule id / category: `DR-APP-009`

**Decision**: Category `APP` (application-result misuse), id `DR-APP-009` — the next number in
the flat, cross-category rule-id sequence (`DR-SENT-001` … `DR-SEX-008`).

**Rationale**: This is a distinct policy question — "was an application-only disposal recorded
in the wrong place" — from every existing category (`SENT` structural sentencing checks, `DISQ`
disqualification, `CTL`, `YRO`/`COEW` requirement durations, `CONV` conviction presence, `AGE`
eligibility, `SEX` notification requirements). `specs/007-imprisonment-age-restriction/research.md`
(R2) and `specs/002-extended-test-disq-warning/research.md` (R3) both established the same
"new policy owner → new category prefix" reasoning; it transfers directly here.

**Alternatives considered**: Folding this into an existing category (e.g. `SENT`) — rejected;
none of the existing categories' preprocessors or reviewers own "is this disposal type even
legal to attach to an offence at all," which is a distinct concern from sentence structure,
disqualification, or notification requirements.

## R3 — Preprocessing approach: one context per breaching result-line occurrence

**Decision**: A new `ValidationPreprocessor` (`ApplicationResultOffencePreprocessor`, qualifier
`application-result-offence`) emits one `ApplicationResultBreachContext` **per breaching result
line**, keyed by that line's `resultLineId` — not one context per offence, and not one context
per defendant.

**Rationale**: AC2 requires that "more than one application-only result against the same
offence" produce a *separate* inline error for each breaching result. Every existing
`RuleEvaluationContext` implementation in this service (`CommunityOrderContext`,
`YouthRehabilitationContext`) models a per-offence *computed value* as `Map<String, String>`
keyed by offence id — which holds exactly one value per offence, so it cannot represent two
distinct breaching labels landing on the same offence. `CelValidationRule.evaluate()` also only
ever produces one triggered `ValidationIssueResult` per `(context, condition)` pair — it does not
iterate multiple values within a single context for one condition. The only way to get N separate
issues for N breaches on one offence, using the existing engine unchanged, is N separate contexts.
Keying by `resultLineId` (rather than, say, `offenceId + shortCode`) also means two identical
breaching codes recorded twice against the same offence (an edge case not explicitly in scope,
but not excluded either) still produce two issues rather than silently colliding on a map key.

**Alternatives considered**:
- *One context per offence, with a `List<String>` of breaching labels* — rejected: would require
  either widening `RuleEvaluationContext`'s calculated-value contract from
  `Map<offenceId, value>` to `Map<offenceId, List<value>>` (a breaking-ish change touching every
  existing implementer) or changing `CelValidationRule.evaluate()` to loop over a list per
  context (a larger, riskier engine change than the one this plan already takes on in R5).
  Per-occurrence contexts get the same outcome with a one-line preprocessor loop and zero change
  to how contexts are consumed.
- *One context per defendant, aggregating breach counts like `CustodialPreprocessor`* — rejected:
  that shape suits *counting* violations across a defendant's offences to decide a single
  boolean/count outcome (e.g. "no primary sentence"). Here every single breach is independently
  reportable with its own distinct message text (the breaching label), which a count-based
  context cannot carry.

## R4 — Result label source: `ResultLineDto.getLabel()`, not a YAML code→label table

**Decision**: The rule's message text embeds `resultLine.getLabel()` as supplied by the caller
on the breaching result line itself. The YAML `preprocessing` config lists only the thirteen
**codes** (`applicationOnlyShortCodes`) used for membership testing; it does not duplicate their
display labels.

**Rationale**: `ResultLineDto` already carries a `label` field alongside `shortCode` — the
caller (Enter Results) already knows and sends the human-readable label for every result line it
submits, since that is what it renders on screen. Hardcoding a second copy of the thirteen labels
in YAML would create two sources of truth that can drift (e.g. if reference data is reworded)
and gives this service no way to detect that drift. Using the label already on the request
guarantees the error text always matches what the user is looking at, with no extra data feed.

**Alternatives considered**:
- *Hardcode a code→label map in the YAML `preprocessing` block* — rejected per the drift risk
  above; also more YAML to keep in sync for no behavioural benefit, since the caller already
  supplies the label on every result line.
- *Fall back to the raw `shortCode` if `label` is blank/null* — kept as a defensive default in
  the preprocessor (data-model.md) so the message never renders a literal `null` or empty string,
  without treating a missing label as a reason to suppress the ERROR itself.

## R5 — Small, non-blocking engine extension: generalise calculated-value placeholder resolution

**Decision**: Extend `CelValidationRule`'s existing calculated-value mechanism (today: resolves
one hardcoded token, `${calculatedEndDate}`, and only inside the per-offence inline
`messageTemplate`) in two ways:

1. Also apply it when building the page-level `errorMessage` (from `errorMessageTemplate`) —
   currently that resolve call passes no extra placeholders at all.
2. Parameterise the placeholder's token name instead of hardcoding `"calculatedEndDate"`, so this
   rule can use `${resultLabel}` without adding a second hardcoded constant that only this rule
   uses. Add an optional `calculatedValuePlaceholderName` field to `ConditionDefinition`
   (and the YAML schema); when absent, default to `"calculatedEndDate"` — preserving every
   existing rule's (`DR-COEW-005`, `DR-YRO-004`) behaviour byte-for-byte with no YAML change on
   their part.

For this rule, `calculatedValueSet: "resultLabelByOffenceId"` and
`calculatedValuePlaceholderName: "resultLabel"`; because each
`ApplicationResultBreachContext` covers exactly one offence, the existing
per-offence-in-a-loop resolution (already used for `messageTemplate`) needs no change in shape
to also feed the (single) affected offence id's value into the `errorMessageTemplate` call.

**Rationale**: This is exactly the kind of gap Constitution Principle I anticipates —
*"if [adding a rule without Java changes] is not [possible], the preprocessor or context model
has a gap that MUST be fixed in the same change"* — a message-resolution gap, not new business
logic. The alternative (a rule-specific special case inside `CelValidationRule`) would violate
Principle III's data-driven dispatch by hardwiring one rule's shape into shared code. Widening
the existing, already-generic mechanism instead keeps `CelValidationRule` rule-agnostic and
makes the capability available to any future rule that needs a dynamic page-level message.

**Alternatives considered**:
- *Duplicate the breaching label into the `errorMessageTemplate` text at YAML-authoring time* —
  impossible; the label is only known per-request (it varies per breach), not at rule-authoring
  time.
- *Have the preprocessor pre-format the entire page-level message string and expose it via a new,
  bespoke `RuleEvaluationContext` method* — rejected: `errorMessage` grouping in
  `DefaultValidationService` keys on the literal `errorMessage` string via
  `ruleId::errorMessage` (see R6); pre-formatting per-context would still need the same
  token-substitution machinery this decision already builds, just relocated, without gaining
  anything.
- *Leave `errorMessageTemplate` static and put the label only in the inline `messageTemplate`,
  contradicting AC1's literal page-level text* — rejected; the spec is explicit that the
  page-level message itself must name the breaching result label, not just the inline one.

## R6 — Aggregate "This affects: <names>" grouping: reuse the existing `ruleId::errorMessage` mechanism

**Decision**: Model the condition the same way `DR-SENT-002`'s `AC2` and `DR-AGE-007`'s `AC2` do:
`severity: ERROR`, `validationLevel: OFFENCE`, `affectedOffenceSet` pointing at the breach's
single offence id, `affectedDefendantSet: "defendantId"`, and an `errorMessageTemplate`
containing both `${resultLabel}` (R5) and `${defendantNames}`.

**Rationale**: `DefaultValidationService.evaluateRulesWithMdc()` already groups every triggered
`ValidationIssueResult` by the literal key `ruleId + "::" + result.errorMessage()`, appending
each context's `affectedDefendantName()` into that group's name list, resolved once via
`MessageTemplateResolver.resolveDefendantNames()`. Because `${resultLabel}` is resolved into the
literal `errorMessage` text *before* grouping happens (R5 resolves it inside `CelValidationRule`,
upstream of the service-level grouping step), two breaches sharing the same label collapse into
one page-level entry with a combined defendant list (satisfying AC2's "identifies every breaching
result" without duplicate page-level lines for the same label), while two different labels
naturally produce two distinct page-level entries — exactly AC1/AC2's required shape, with zero
new service-layer code beyond R5.

**Alternatives considered**:
- *Emit a single combined page-level message naming all breaching labels in one sentence* —
  rejected; contradicts the literal error text in the spec ("`<<Application result>>` displays
  the result label of the result breaching the rule" — singular, one label per message), and
  would require new aggregation code where the existing mechanism already produces the right
  shape for free.

## R7 — "This affects" line and single-defendant suppression

**Decision**: No new logic needed. `MessageTemplateResolver.resolveDefendantNames()` already
strips the "This affects ..." clause entirely when the hearing has one defendant or none, and
formats multiple names as "A, B and C" otherwise — matching the spec's "if there is more than 1
defendant in the hearing" condition and the "comma-separated where there is more than one"
formatting exactly as already proven for `DR-SENT-002` and `DR-AGE-007`.

**Alternatives considered**: None — this is a direct reuse of existing, already-tested behaviour.

## R8 — Small, non-blocking engine fix: dedup and blank-name guard in the "This affects" aggregation

**Decision**: Extend `DefaultValidationService.appendDefendantName` (currently an unconditional
`list.add(name)`) to (a) skip a name already present in that `ruleId::errorMessage` group's
list, and (b) skip a `null`/blank name entirely, rather than adding either. `ApplicationResultBreachContext.defendantName()`
falls back to `""` (empty string) — never `null`, never a placeholder like `"Unknown"` — when the
breaching result line's defendant cannot be resolved to a real name, relying on guard (b) to drop
it from the rendered list rather than rendering an empty or placeholder entry.

**Companion fix, same method's caller (identified during a second `/speckit.analyze` pass)**:
guard (b) means a `ruleId::errorMessage` group whose *every* contributing name is blank never
gets an entry created in `errorNamesByRule` at all (the guard returns before `computeIfAbsent`
runs). The aggregation loop that later reads this map (to build each `errorMessages` entry) must
therefore use `errorNamesByTemplate.getOrDefault(entry.getKey(), List.of())` rather than
`.get(entry.getKey())` — otherwise it retrieves `null` for that group, and
`MessageTemplateResolver.resolveDefendantNames`/`formatDefendantNames` throws a
`NullPointerException` on `names.isEmpty()` for any multi-defendant hearing where the sole
breaching defendant for a given label has an unresolvable name (spec.md's "no name recorded" edge
case, in a hearing of 2+ defendants). This risk did not exist before guard (b): the previous
unconditional `.add()` guaranteed a non-null list for any group that existed at all. Both changes
belong to the same fix and are described together in data-model.md.

**Rationale (identified during `/speckit.analyze`, not in the original R1–R7 pass)**: R3's
per-breach-occurrence context design (needed for AC2's separate-inline-error-per-breach
requirement) means the *same* defendant can produce two separate `ValidationIssueResult`s that
share an identical `errorMessage` text whenever two breaches carry the same breaching label —
e.g. the same application-only code recorded on two different offences for one defendant, which
is spec.md's own edge case ("the same application-only result code appears on two different
offences for the same defendant ... the defendant is named once in the This affects: list").
Both entries call `appendDefendantName` with the identical name under the same templateKey.
Unconditional `.add()` would render that name twice — e.g. "This affects: John Smith and John
Smith" — directly contradicting FR-005's "de-duplicated" requirement. Separately, spec.md's edge
case for a defendant with no name recorded requires that defendant to be *omitted* from the list,
not rendered with an empty or placeholder name; `defendantName() == ""` combined with guard (b)
achieves exactly that, without ever inserting an empty string into the rendered "This affects"
text.

Both fixes share one call site and are dormant (no behavioural change) for every existing rule:
`DR-SENT-001`, `DR-AGE-007`, and `DR-SEX-008` all group contexts per-defendant, so they never
call `appendDefendantName` twice with the same name under one templateKey, and every defendant
they resolve a name for is non-blank. This is the same class of change as R5 — a gap in shared,
rule-agnostic aggregation code that a new rule's shape exposes, fixed once at the shared layer
per Constitution Principle I, rather than worked around per-rule.

**Why not fix this in the preprocessor instead**: Deduping by having the preprocessor skip
emitting a second context for a repeat defendant+label combination was considered and rejected —
that would also suppress the second offence's *inline* error, since one context drives both the
inline (per-offence) message and the page-level entry. AC2 requires the inline error on **every**
breaching offence; only the page-level "This affects" aggregation should collapse the duplicate,
which is a service-level (aggregation-time) concern, not a preprocessor (context-construction)
concern.

**Why not fix this in `MessageTemplateResolver.formatDefendantNames`/`resolveDefendantNames`
instead**: Equivalent outcome, but those methods are already covered by proven tests for other
rules' aggregate-name formatting; fixing at the collection point (`appendDefendantName`, the
single call site that builds the list) keeps that already-tested formatting code untouched and
confines the change to the one place actually responsible for constructing the list.

**Alternatives considered**:
- *Fall back to `"Unknown"` for an unresolvable defendant name (the original data-model.md
  decision, before this was caught)* — rejected: `"Unknown"` is a non-empty, non-null string, so
  it would render as `"This affects: Unknown."` rather than omitting the entry, contradicting the
  spec.md edge case's explicit requirement to omit it. It was originally chosen only to avoid a
  `null` `defendantName()` pushing the issue down `DefaultValidationService`'s "standalone
  message" branch (used when `affectedDefendantName() == null`) — a branch that does **not**
  resolve the `${defendantNames}` token at all, which would leak the literal, unresolved token
  into the response. Using `""` instead of `null` keeps the issue on the correct, already-tested
  "grouped" branch (so `${defendantNames}` is still properly resolved), while guard (b) strips the
  blank name from the list that branch builds.
