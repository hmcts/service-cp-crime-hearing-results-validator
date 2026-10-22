# Quickstart: Application Results Recorded Against Offences – ERROR Validation

## Try it locally

1. Start the service: `gradle bootRun` (or via the docker-compose stack used by `gradle api`).
2. `POST /api/validation/validate` with a `DraftValidationRequest` body containing:
   - Two `defendant`s (so the "This affects:" clause is exercised — see step 4 for the
     single-defendant variant).
   - One `offence` linked to the first defendant.
   - One `resultLine` with `shortCode: "LAWD"`, `label: "Legal Aid Withdrawn"`, linked to that
     offence and the first defendant.
3. Expect the response to contain:
   - A `validationIssues` entry with `ruleId: "DR-APP-009"`, `severity: "ERROR"`,
     `validationLevel: "OFFENCE"`, and the offence in `affectedOffences` carrying the message
     `"Remove Legal Aid Withdrawn from this offence. It is an application result, so it can
     only be added to an application."`
   - `errors.errorMessages` containing: `"Legal Aid Withdrawn is an application result. It
     cannot be added to an offence. Remove it from the offence and add an application to the
     hearing. This affects: <first defendant name>."`
4. Resubmit with only one `defendant` on the hearing. Expect the same `errorMessages` entry with
   the trailing `"This affects: ..."` sentence removed entirely.
5. Add a second `resultLine` with `shortCode: "RFSD"`, `label: "Application refused"`, linked to
   the **same** offence and defendant as step 2. Expect two separate `affectedOffences` message
   entries against that offence (one per breaching result) and two separate `errorMessages`
   entries (one naming "Legal Aid Withdrawn", one naming "Application refused").
6. Remove both breaching result lines and resubmit. Expect no `DR-APP-009` issue in the response.

## Verifying the runtime override still works (do not write a new IT for this)

Per `.claude/rules/design_rules.md`, override/severity-ceiling behaviour is proven once against
`DR-SENT-001` in `ValidationRuleOverrideIntegrationTest`. `DR-APP-009` inherits that coverage —
do not add a per-rule override IT. If a gap is found, extend that shared test instead.

## Verifying the engine generalisation did not regress existing rules

`DR-COEW-005` and `DR-YRO-004` are the only rules using `calculatedValueSet` today (for
`${calculatedEndDate}`, via `messageTemplate`). Some of their conditions (`DR-COEW-005`'s
`DUR-CUR`/`DUR-CURE`/`DUR-AAR`) also carry an `errorMessageTemplate`, but that template's text
does not itself contain `${calculatedEndDate}`, so the new errorMessage-side resolution branch
is exercised as a harmless no-op for them (the token substitution runs, finds nothing to
replace, and the text is unchanged). Their existing unit and integration tests must stay green
unchanged, since neither rule sets the new `calculatedValuePlaceholderName` field:

```bash
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CommunityOrderEndDatePreprocessorTest"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.YouthRehabilitationPreprocessorTest"
```

## Running the new tests

```bash
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.ApplicationResultOffencePreprocessorTest"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.ApplicationResultBreachContextTest"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.CelValidationRuleTest"
gradle test --tests "uk.gov.hmcts.cp.integration.ApplicationResultOffenceRuleIT"
gradle build   # full loop: compile, checkstyle, PMD, all tests
```
