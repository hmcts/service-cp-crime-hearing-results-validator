# Quickstart: Imprisonment Result Age Restriction (DD-42950)

## Prerequisite (resolved)

The `libs.api.hearing.results.validator` dependency in `gradle/libs.versions.toml` is pinned to
`0.3.0-DD-42950-local`, a locally-published build of `api-cp-crime-hearing-results-validator`
(branch `DD-42950-defendant-date-of-birth`) whose `DefendantDto` now exposes `dateOfBirth`. See
`contracts/upstream-dependency.md` for the still-open follow-up: getting that change reviewed,
merged, and published as a real upstream version before this ships to a shared environment.

## Try it locally

1. Start the service: `gradle bootRun` (or via the docker-compose stack used by `gradle api`).
2. `POST /api/validation/validate` with a `DraftValidationRequest` body containing:
   - `hearingDay`: e.g. `"2026-07-20"`
   - One `defendant` with `dateOfBirth` less than 21 years before `hearingDay` (e.g.
     `"2006-08-01"` — turns 21 the day *after* the hearing).
   - One `offence` linked to that defendant.
   - One `resultLine` with `shortCode: "IMP"`, linked to that offence and defendant.
3. Expect the response to contain a `validationIssues` entry with `ruleId: "DR-AGE-001"`,
   `severity: "ERROR"`, `validationLevel: "OFFENCE"`, and the offence in `affectedOffences`; and
   `errors.errorMessages` containing: `"The defendant is under 21 years of age and cannot receive
   a sentence of imprisonment. This affects: <defendant name>."`
4. Re-submit with `dateOfBirth` moved to 21+ years before `hearingDay` (e.g. `"2004-07-19"`).
   Expect no `DR-AGE-001` issue in the response.

## Verifying the runtime override still works (do not write a new IT for this)

Per `.claude/rules/design_rules.md`, override/severity-ceiling behaviour is proven once against
`DR-SENT-002` in `ValidationRuleOverrideIntegrationTest`. `DR-AGE-001` inherits that coverage —
do not add a per-rule override IT. If a gap is found, extend that shared test instead.

## Running the tests

```bash
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.AgeRestrictedImprisonmentPreprocessorTest"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.AgeRestrictedResultContextTest"
gradle test --tests "uk.gov.hmcts.cp.integration.AgeRestrictedImprisonmentRuleIT"
gradle build   # full loop: compile, checkstyle, PMD, all tests
```
