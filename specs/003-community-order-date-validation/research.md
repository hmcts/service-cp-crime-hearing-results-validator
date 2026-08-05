# Research: Community Order End Date Validation (DR-COEW-005)

**Branch**: `dev/DD-42678-co-end-date-rule`
**Date**: 2026-05-20 · **Updated**: 2026-08-05
**Feature spec**: [spec.md](spec.md)

---

## Decision 1 — Preprocessor Grouping Strategy

**Decision**: Group per-defendant (one `CommunityOrderContext` per defendant group), aggregating violations across all offences that belong to that defendant.

**Rationale**: The error summary says "This affects: Defendant X" — the unit of reporting is the defendant. Grouping per-defendant matches `CustodialPreprocessor`'s approach and allows the CEL variable map (violation counts) and affected offence-id sets to accumulate across all of a defendant's offences in a single pass.

**Alternatives considered**:
- *Per-offence grouping* — Rejected: the UI requires a defendant-level error that lists which defendants are affected, not one error per offence. Per-offence would produce up to N errors for N offences with the same violation type on the same defendant.
- *Per-(defendant, offence) grouping* — Rejected: adds complexity without gain; the defendant is still the natural error scope.

---

## Decision 2 — Multiple AC2 Violations: Separate Conditions or Single Combined Condition

**Decision**: Separate YAML `conditions` per requirement type (AC2a=CUR, AC2b=CURE, AC2c=CURA, AC2d=AAR) — each fires its own `ValidationIssue` when the corresponding violation count > 0.

**Rationale**: The YAML+CEL engine produces one `ValidationIssue` per triggered `condition`. Separate conditions are the natural fit for the engine and give maximum rule configurability — each condition can be individually enabled/disabled or severity-capped via the `validation_rule` DB table. CEL cannot dynamically concatenate strings, so a "combined message" would require Java-side post-processing, violating Constitution Principle I.

**Alternatives considered**:
- *Single condition with a dynamic combined message* — Rejected: CEL expressions cannot dynamically concatenate strings across requirement types; achieving this would require moving business logic into Java, violating Constitution Principle I.
- *Post-processing aggregation layer* — Rejected: adds complexity, breaks the clean separation between rule evaluation and issue emission, and removes per-requirement-type runtime configurability.

---

## Decision 3 — Prompt Ref Names: Hardcoded or Configurable in YAML

**Decision**: Hardcode the `promptRef` lookup keys in the preprocessing layer:
- Community orders (COEW/COS/CONI) → `"endDate"`
- CUR → `"endDate"`, CURE → `"endDateOfTag"`, CURA → `"endDate"`, AAR → `"until"`

**Rationale**: The `promptRef` values are part of the upstream API contract (`api-cp-crime-hearing-results-validator`), not a business policy decision. They are stable and tied to the `ResultLineDto.prompts` schema. Adding YAML config fields for them would introduce unnecessary coupling and complexity with no practical benefit — no BA would ever change `"endDateOfTag"` to something else without an API change.

**Alternatives considered**:
- *YAML-configurable `promptRefs` map in `PreprocessingDefinition`* — Rejected: prompt ref names are API-contract values, not policy values. They belong in code, not YAML.

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
- *Hardcode short codes in the preprocessor* — Rejected: violates Principle I; changing COEW to a new code would require a Java change and a redeploy.
- *Add `@JsonIgnoreProperties(ignoreUnknown = true)` to `PreprocessingDefinition`* — Rejected: silently discards unknown YAML fields, masking configuration typos. Explicit fields are safer.

**Retrofit note (2026-08-05)**: On `dev/DD-42678-co-end-date-rule`, `curfewShortCodes`,
`curfewTagShortCodes`, and `furtherCurfewShortCodes` ended up shared with
`YouthRehabilitationPreprocessor` (both YRO and community-order requirements use CUR/CURE/CURA
short codes) rather than being exclusive to this rule. `communityOrderShortCodes` and
`alcoholAbstinenceShortCodes` remain specific to `DR-COEW-005`.

---

## Decision 6 — Date String Format from `promptValue`

**Decision**: Parse `promptValue` as ISO-8601 (`yyyy-MM-dd`) using `LocalDate.parse(promptValue)`. If `promptValue` is null, blank, or unparseable, log a `WARN` and skip the date comparison for that prompt (treat as no violation for that requirement on that offence).

**Rationale**: ISO-8601 is the standard wire format for `LocalDate` in HMCTS services. Defensive handling (skip vs. throw) ensures one malformed prompt does not abort the entire validation run and produce a 500 response.

**Alternatives considered**:
- *Throw on parse failure* — Rejected: a single bad date from the UI would make the entire hearing unvalidatable; log and skip is more resilient.
- *Use a configurable date format* — Rejected: ISO-8601 is the API contract; no need for configurability.

---

## Decision 7 — No New API Contract / No `contracts/` Artefact

**Decision**: No new `contracts/` directory needed for this feature.

**Rationale**: The service exposes one existing endpoint (`POST /api/validation/validate`) whose request/response schema is owned by the upstream `api-cp-crime-hearing-results-validator` library. This feature adds new `ValidationIssue` entries to the existing `errors` list in `DraftValidationResponse` and relies on the already-populated `affectedDefendants` field. No new endpoints, no new consumers, no upstream schema changes required.

---

## Decision 8 — Rename to `DR-COEW-005` and `PreprocessorHelper` Extraction *(retrofit, 2026-08-05)*

**Decision**: When this feature was ported from `team/DD-41653` onto `dev/DD-42678-co-end-date-rule`:

1. The rule id was renamed from `DR-COEW-001` to `DR-COEW-005`, and its Flyway migration moved from `V1.004` to `V1.005`, because migration slots `V1.002`–`V1.004` were already claimed on this branch by `DR-DISQ-001`, `DR-CTL-001`, and `DR-YRO-001`.
2. The preprocessor's shared logic (short-code case-insensitive matching, grouping result lines by defendant, `masterDefendantId` dedupe-key resolution, defendant full-name assembly, and prompt-date parsing) was extracted out of `CommunityOrderEndDatePreprocessor` and into a new stateless utility class, `PreprocessorHelper`, so `YouthRehabilitationPreprocessor` could reuse the same logic instead of duplicating it.
3. The DB seed row's `enabled` flag was flipped from `false` to `true` — the rule ships live by default on this branch rather than requiring a manual enable via the `validation_rule` table.

**Rationale**: Both (1) and keeping the DB migration numbering append-only are mechanical consequences of merge order across parallel feature branches, not business-rule changes. (2) followed naturally once a second preprocessor (`YouthRehabilitationPreprocessor`, for `DR-YRO-001`) needed the identical grouping/matching/date-parsing plumbing — extracting it avoided duplicating ~150 lines of near-identical code and centralizes the `masterDefendantId` dedupe logic so both rules share one bug surface instead of two. (3) reflects a product decision made when this branch integrated the rule that it should be active immediately rather than dormant pending a manual toggle.

**Alternatives considered**:
- *Keep `DR-COEW-001` and force-renumber the conflicting rules instead* — Rejected: `DR-DISQ-001`/`DR-CTL-001`/`DR-YRO-001` were already released to other environments off this branch; renumbering them would be the more disruptive change.
- *Duplicate the shared logic in `YouthRehabilitationPreprocessor` instead of extracting `PreprocessorHelper`* — Rejected: violates DRY, and the `masterDefendantId` dedupe-key logic in particular is subtle enough that two independent implementations risk drifting apart.
- *Leave the DB seed row `enabled: false` as originally specified* — Rejected: superseded by a product decision on this branch (not separately re-derived here; see `V1.005__insert_dr_coew_005.sql` and the commit `refactor: rename rule to DR-COEW-005 and enable it by default`).

---

## Resolved Unknowns Summary

| # | Unknown | Resolution |
|---|---------|------------|
| 1 | Prompts field availability | `List<Prompt> prompts` on `ResultLineDto` — confirmed in 0.1.6; `Prompt.getPromptRef()` / `getPromptValue()` |
| 2 | Prompt ref key names | Hardcoded: `endDate`, `endDateOfTag`, `until` |
| 3 | Multiple violations display | Separate condition per requirement type; UI groups them |
| 4 | Share button scope | Hearing-level (hidden if any defendant has errors) |
| 5 | AC1 scope | Out of scope; separate ticket |
| 6 | Grouping unit | Per-defendant, folded by `masterDefendantId` where shared |
| 7 | Rule id / migration slot on this branch | `DR-COEW-005` / `V1.005` (slots 002–004 already taken) |
| 8 | Shared preprocessor plumbing | Extracted into `PreprocessorHelper`, reused by `YouthRehabilitationPreprocessor` |
