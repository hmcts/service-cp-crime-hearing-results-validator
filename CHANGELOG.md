# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added

- **DR-AGE-007** — Imprisonment result age restriction: blocks sharing when a defendant
  under 21 years of age on the hearing date has an imprisonment-type result (`IMP`,
  `EXTIVS`, `SPECC`, `SUSPS`, `SUSPSNR`) recorded against one or more of their offences
  (DD-42950).
- **DR-CONV-006** — No conviction on sentenced offence warning: warns when an offence has
  a final, non-excluded result but is not convicted (no guilty plea, finding of guilt, or
  recorded date of conviction), clearing once conviction is recorded via plea or verdict.
  Reuses DR-DISQ-002's excluded-short-code gate and DR-CTL-003's conviction check via a new
  `NoConvictionPreprocessor` (DD-43039). Seeds `enabled=true` by default in fresh databases.
- **DR-COEW-005** — Community Order End Date Validation: validates that a community
    order's end date is not earlier than the end date of any attached requirement (`CUR`,
    `CURE`, `CURA`, `AAR`), plus new `DUR-CUR`/`DUR-CURE`/`DUR-AAR` conditions checking that a
    `CUR`/`CURE`/`AAR` requirement's own recorded end date matches its calculated duration
    (start date + period − 1 day, or hearing date + days − 1 day for `AAR`) (DD-41653, DD-41655,
    DD-42678).
- `DR-CTL-003` gains a `CTLDATE` prompt-reference bypass as a fifth suppression condition
  for the CTL-missing warning.

### Changed

- Renumbered validation rule IDs into a single global sequence ordered by priority:
  `DR-SENT-002` → `DR-SENT-001` (1000), `DR-DISQ-001` → `DR-DISQ-002` (2000),
  `DR-CTL-001` → `DR-CTL-003` (3000), `DR-YRO-001` → `DR-YRO-004` (4000), and the new
  `DR-COEW-005` (5000). Previously each rule category numbered independently from 001.
- `DR-CTL-003`'s `ctlShortCodes` narrowed to just `CTL`, dropping `CCII`, `CCIIB`, `CCIILA`,
  `CCIITDH`, `CCIIYDA`, `CCQB`.
- `DR-SENT-001`, `DR-DISQ-002`, `DR-CTL-003`, `DR-YRO-004`, and `DR-COEW-005` now seed
  `enabled=true` by default in fresh databases, matching the manually-enabled steady state
  already in place on SIT/PRD.

### Removed

- Dead per-rule enable/disable plumbing in the `apiTest` and integration-test suites
  (`enableRule`/`setRuleEnabled`/`awaitRuleState` helpers and their JDBC/cache-eviction
  support) for `DR-CTL-003`, `DR-YRO-004`, `DR-DISQ-002`, and `DR-COEW-005` — now
  unnecessary since every shipped rule seeds enabled by default. Per-rule override/severity-
  ceiling coverage remains centralised in `ValidationRuleOverrideIntegrationTest` and the
  single PATCH round-trip test in `ValidationRulesApiHttpLiveTest`, per
  `.claude/rules/design_rules.md`.

### Fixed

- Existing integration/API test fixtures updated for the `DR-CONV-006` rollout: result
  lines with no `isConvicted` flag now set it explicitly where the fixture's intent was
  unrelated to conviction status, avoiding unexpected `DR-CONV-006` firings.
- V1.006's rule-id renumbering (`UPDATE validation_rule SET id = '<new>' WHERE id = '<old>'`)
  had no guard against the target id already being occupied (e.g. an STE rebuild reseeding
  rows under the new ids ahead of Flyway, or a PATCH-created `validation_rule` row), which
  hit a primary-key violation and crashlooped the service at startup. Added
  `V1.009__guard_renumbered_rule_ids.sql`, a new, collision-safe migration that re-applies
  the same four renumbering pairs defensively rather than editing the already-applied V1.006
  in place, which would break Flyway checksum validation (DD-43134).
- Raw `promptValue` free text was logged verbatim in two `Unparseable date/integer` warn lines
  (`PreprocessorHelper`, `CommunityOrderEndDatePreprocessor`) — a CodeQL log-injection finding.
  Both now pass the value through `Encode.forJava`, matching the existing `TracingFilter`
  precedent; three further open alerts of the same class (`ValidationRulesController`,
  `DefaultValidationRulesService`, `RuleOverrideService` all logging the request-path `ruleId`)
  are also closed, either by dropping the raw value from a pre-validation log line or by
  encoding it (DD-43134).
- `AzureAppConfigFetcher.parseFeatures` defaulted a feature flag to `false` when its Azure App
  Configuration value JSON was missing the `enabled` field (schema drift or a hand-edited flag),
  silently flipping the documented fail-open contract to fail-closed for that flag. The key is
  now omitted from the map instead, so `AzureFeatureToggleService`'s own
  `getOrDefault(featureName, true)` fail-open default applies.
- `AzureAppConfigFetcher`'s HMAC `x-ms-date` header used `ZonedDateTime.now()` (the JVM's
  default time zone) formatted as ISO-local rather than RFC1123 GMT. Azure requires RFC1123 GMT;
  this only worked because containers run UTC — on any other host every signed request would
  401 and the toggle would be stuck fail-open. Now pinned to `ZoneOffset.UTC` with
  `DateTimeFormatter.RFC_1123_DATE_TIME`.
- `DisqualificationExtendedTestPreprocessor` treated a final result line with a null `shortCode`
  as excluded (not counting toward `qualifyingCount`), diverging from `DR-CONV-006`'s identical
  gate and from the spec's `∉ excludedFinalShortCodes` set-membership definition (a null value
  is never a member of the excluded set). Fixed by routing both preprocessors through the same
  `PreprocessorHelper.hasUpperCode`-based check, so the semantics can't drift apart again.
- `DefaultValidationRulesService.updateRule` merged a PATCH onto an entity read through the
  30s-TTL, per-pod `ruleOverrides` cache and then wrote the full merged row back — a
  non-atomic read-modify-write that let two concurrent or cross-pod PATCHes touching different
  fields silently revert each other's change. Replaced with a single atomic upsert
  (`ValidationRuleRepository.upsertPartial`, a native `INSERT ... ON CONFLICT DO UPDATE` with
  `COALESCE`) that merges against the database's current row rather than an application-level
  read.

### Changed

- `PreprocessingDefinition`, `RuleDefinition`, and `ConditionDefinition` — the three YAML-bound
  rule-config types — converted from mutable Lombok `@Data` classes to immutable records
  (Constitution Principle II). Jackson's native record support (already on the classpath via
  `jackson-dataformat-yaml`) deserializes them unchanged; `@Builder` continues to work on
  records, so no call site needed to change construction style, only accessor names
  (`getXxx()`/`isXxx()` → `xxx()`).
- Deduplicated ~180 lines of `PreprocessorHelper`-equivalent logic that `DisqualificationExtendedTestPreprocessor`
  and `CtlMissingPreprocessor` had privately reimplemented, and extracted the defendant-dedupe/
  offence-grouping/order-end-date boilerplate shared by `YouthRehabilitationPreprocessor` and
  `CommunityOrderEndDatePreprocessor` into new `PreprocessorHelper` methods
  (`groupLinesByDedupedDefendant`, `groupByOffence`, `findOrderEndDate`).
- `design_rules.md`, `CLAUDE.md`, `.specify/memory/constitution.md`, and `README.md` updated to
  describe the preprocessor registry as shipped (it fully dispatches on `preprocessing.type`
  across seven registered preprocessors) rather than as a "not yet on main" transitional state —
  the docs had drifted behind the code. `README.md`'s rule table extended to all seven shipped
  rules.
- Static analysis (Checkstyle, PMD) now runs on `src/test` as well as `src/main`/`apiTest`/
  gatling, closing the last unlinted sourceset (~65 test classes). A small number of real
  findings were fixed (unused imports, missing `Charset`, lambda-to-method-reference, field
  ordering, a stray class/method naming mismatch); a few rules that are either inapplicable to
  test code or false-positive against this project's own test-naming convention are excluded for
  `src/test` only, via `config/checkstyle/checkstyle-suppressions.xml` and
  `.github/pmd-test-ruleset.xml` (both with inline rationale).

## Released

Rules released: `DR-SENT-001`, `DR-DISQ-002`, `DR-CTL-003`, `DR-YRO-004`, `DR-COEW-005`
