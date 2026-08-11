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

## Released

Rules released: `DR-SENT-001`, `DR-DISQ-002`, `DR-CTL-003`, `DR-YRO-004`, `DR-COEW-005`
