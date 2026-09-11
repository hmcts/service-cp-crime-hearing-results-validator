# Quickstart: Sexual Offence Notification Requirement Warning

## Prerequisite

No upstream contract change is required — unlike `specs/007-imprisonment-age-restriction`, every
field this feature reads (`OffenceDto.offenceCode`/`.isConvicted`, `DefendantDto.dateOfBirth`,
`ResultLineDto.shortCode`) already exists on the current `libs.api.hearing.results.validator`
pin. What's required instead is a **new external dependency wired up in this repo** — the
`cpp-context-referencedata-offences` client, config, and cache described in `data-model.md` and
`contracts/referencedata-offences-integration.md` — before `DR-SEX-008` can ever produce a
warning (every offence lookup fails open with no client in place, so the rule stays permanently
dormant, same fail-safe posture as any other missing dependency in this codebase).

## Try it locally

1. Start the service with the referencedata-offences stub running (via WireMock in
   `IntegrationTestBase`, or the `gradle api` docker-compose stack once that stub is added — see
   `contracts/referencedata-offences-integration.md`'s open item on live-test wiring).
2. Stub `GET /referencedataoffences-query-api/query/api/rest/referencedataoffences/offences?cjsoffencecode={code}`
   to return `{"offences": [{"offenceId": "<id>", "misCode": "SEX"}]}` for the offence code you'll
   use below.
3. `POST /api/validation/validate` with a `DraftValidationRequest` body containing:
   - `hearingDay`: e.g. `"2026-08-25"`
   - One `defendant` with `dateOfBirth: "2000-01-01"` (Adult — 18+ at hearing date)
   - One `offence` with `offenceCode` matching the stubbed reference-data code, `isConvicted: true`
   - **No** `resultLine` with `shortCode: "NORRR"` for that offence
4. Expect the response's `warnings` list to contain a `ValidationIssue` with `ruleId:
   "DR-SEX-008"`, `severity: "WARNING"`, `validationLevel: "OFFENCE"`, and an `affectedOffences`
   entry whose `message` reads: `"This offence does not have a sexual offences notification
   requirement (NORRR). Check if this is required before sharing"`.
5. Re-submit with a `resultLine` added: `shortCode: "NORRR"`, linked to the same offence and
   defendant. Expect no `DR-SEX-008` issue in the response.
6. Repeat steps 3–5 with `dateOfBirth: "2012-01-01"` (Youth — under 18) and no `NORRR`/`NORPGP`
   result line. Expect the youth-specific message: `"This offence does not have a sexual offences
   notification requirement (NORRR - defendant or NORPGP - parent and defendant). Check if this
   is required before sharing"`. Adding **either** `NORRR` or `NORPGP` should clear the warning.
7. Re-stub the reference-data endpoint to return 404 (or stop the stub server). Re-submit the
   Adult scenario from step 3. Expect **no** `DR-SEX-008` issue — confirms the fail-open contract
   (`research.md` R4).

## Verifying the runtime override still works (do not write a new IT for this)

Per `.claude/rules/design_rules.md`, override/severity-ceiling behaviour is proven once against
`DR-SENT-001`/`DR-SENT-002` in `ValidationRuleOverrideIntegrationTest`. `DR-SEX-008` inherits that
coverage — do not add a per-rule override IT. If a gap is found, extend that shared test instead.

## Verifying AC2 (combined offence + defendant level display) — regression only

Per spec.md User Story 3, this is a verification story against the existing multi-issue response
structure, not new code. Construct a hearing that triggers `DR-SEX-008` (an offence-level warning)
alongside an existing DEFENDANT-level warning condition (e.g. `DR-SENT-001`'s `AC4`), and assert
both appear in the same `warnings` list — no new aggregation logic should be needed for this to
pass.

## Running the tests

```bash
gradle test --tests "uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClientTest"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.SexualOffenceNotificationPreprocessorTest"
gradle test --tests "uk.gov.hmcts.cp.services.rules.cel.SexualOffenceNotificationContextTest"
gradle test --tests "uk.gov.hmcts.cp.integration.SexualOffenceNotificationRuleIT"
gradle build   # full loop: compile, checkstyle, PMD, all tests
gradle api     # live API tests, once the docker-compose stack stubs the new dependency
```
