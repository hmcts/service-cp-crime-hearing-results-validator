# Contract — `DR-DISQ-001` Extended Test Disqualification Warning

This is the contract for the new validation rule. The HTTP API contract (`POST /api/validation/validate`) is owned upstream by `libs.api.hearing.results.validator`. **As of the 2026-04-28 revision, `ResultLineDto` is being extended** to add `category: enum [A, I, F]` (Ancillary / Intermediary / Final). All other DTOs (`DraftValidationRequest`, `OffenceDto`, `DefendantDto`, `ValidationIssue`, `AffectedOffence`) are unchanged.

The original "unchanged by this feature" assertion is **superseded** — see the [Upstream contract delta](#upstream-contract-delta-2026-04-28) section below.

---

## Upstream contract delta (2026-04-28)

The OpenAPI source-of-truth at `/home/sachin/moj/api-cp-crime-hearing-results-validator/src/main/resources/openapi/openapi-spec.yml` is extended. Added field on `ResultLineDto`:

```yaml
ResultLineDto:
  type: object
  required:
    - id
    - shortCode
    # ... existing required fields
  properties:
    # ... existing properties (id, shortCode, label, defendantId, offenceId, isConcurrent, consecutiveToOffence)
    category:
      type: string
      enum: [A, I, F]
      description: >-
        Closed enum identifying the role of this result line on the offence:
        A = Ancillary (e.g. adjournment, listing); I = Intermediary
        (e.g. plea, hearing-internal); F = Final (the line that makes
        the offence inactive).
```

Notes:

- `category` is **optional** at the schema level for transitional compatibility — older callers that have not yet been upgraded will still produce valid payloads. The validator's `DisqualificationExtendedTestPreprocessor` falls back to FR-015 fail-safe behaviour (treat as non-final → no warning) when `category` is absent.
- Library version is bumped per semantic versioning. The lib publishes to ACR on merge to `main` of the API repo (CI/CD-shaped constraint — see plan.md "Cross-Repo Coordination").
- Consumers that need to *write* `category` upstream (i.e. `cpp-ui-hearing` and `cpp-context-hearing`) need to: (a) pull the new lib version once published; (b) populate the field from their local domain models (`ResolvedDraftResultLine.category` / `SharedResultsCommandResultLineV2.category`); (c) confirm there is no client-side code path that strips unknown fields.
- `cpp-context-hearing` carries a hand-written parallel-mirror `ResultLineDto` (not regenerated from this contract). That file is updated in lockstep — see plan.md "Cross-Repo Coordination" row 2.

### Rule-engine consumption

`DisqualificationExtendedTestPreprocessor` reads `category` once per result line during preprocessing. Comparison is case-insensitive against the literal `'F'` (and against the validation set `{A, I, F}` for the FR-015 logging branch). No CEL expression references `category` directly — the preprocessor distils it into the existing `qualifyingCount > 0` boolean and the new `finalCategoryCount` diagnostic counter (see data-model.md).

---

## YAML rule definition

File: `src/main/resources/rules/DR-DISQ-001.yaml`

```yaml
rule:
  id: "DR-DISQ-001"
  title: "Extended test disqualification check"
  description: >-
    Warns when a relevant Road Traffic Act 1988 offence has a non-excluded
    final result and no DDOTE / DDOTEL extended-test disqualification
    recorded against it.
  priority: 2000
  enabled: true

  preprocessing:
    type: "disqualification-extended-test"
    relevantOffenceCodes:
      - RT88046   # s.1  — causing death by dangerous driving
      - RT88526   # s.1A — causing serious injury by dangerous driving
      - RT88026   # s.2  — dangerous driving
      - RT88530   # s.3ZC — causing death by driving: disqualified drivers
      - RT88531   # s.3ZD — causing serious injury by driving: disqualified drivers
    excludedFinalShortCodes:
      - wdrn      # withdrawn
      - WDRNOFF   # withdrawn in favour of another offence
      - dism      # dismissed
      - dine      # dismissed (no evidence)
      - dini      # dismissed (insufficient)
      - disch     # discharged
      - disc      # discontinued
      - ctrof     # count to remain on file
      - iremfile  # indictment to remain on file
    extendedTestShortCodes:
      - DDOTE     # obligatory disqualification with extended test
      - DDOTEL    # obligatory disqualification for life with extended test

  conditions:
    - id: "AC1"
      name: "Relevant offence missing extended test disqualification"
      expression: "qualifyingCount > 0"
      severity: WARNING
      messageTemplate: >-
        Check whether you need to add extended test disqualification with
        DDOTE (disqualification and extended test) or DDOTEL (disqualification
        for life and extended test)
      affectedOffenceSet: "qualifyingOffenceIds"
```

Notes:
- `priority: 2000` runs after `DR-SENT-002` (priority 1000). Order is not behaviourally significant — rules evaluate independently — but a stable ordering keeps integration-test assertions deterministic.
- All short-code matching is case-insensitive; the YAML uses the casing from the source ticket for readability.
- `messageTemplate` carries the literal AC1A text. No `${placeholder}` tokens — the offence linkage rides on `affectedOffences`, see below.

---

## Preprocessor contract

| Field | Value |
|-------|-------|
| Qualifier (`type()`) | `"disqualification-extended-test"` |
| Implements | `ValidationPreprocessor` |
| Output | `Map<String, DisqualificationContext>` keyed on `OffenceDto.id` |
| One context per | offence in the request |
| Context size | exactly `request.getOffences().size()` |

Each `DisqualificationContext` exposes the CEL variables documented in `data-model.md` §`DisqualificationContext`. The condition `qualifyingCount > 0` fires for any offence that meets all three gates (revised 2026-04-28): **relevant offence code** AND **at least one F-category line whose shortCode is non-excluded** AND **no DDOTE/DDOTEL line on the offence**. The previous "no excluded result on the offence" gate is folded into the F-line check (an offence whose only F line carries an excluded shortCode has no non-excluded F line, so the rule does not fire).

---

## Issue emission contract

For each context where the condition fires, exactly one `ValidationIssue` is emitted with:

| Field | Value |
|-------|-------|
| `ruleId` | `"DR-DISQ-001"` |
| `severity` | `WARNING` (capped downward only by DB ceiling — see Constitution VI) |
| `message` | `"Check whether you need to add extended test disqualification with DDOTE (disqualification and extended test) or DDOTEL (disqualification for life and extended test)"` (exact text, no leading or trailing whitespace) |
| `affectedOffences[]` | Singleton list. The single `AffectedOffence` has `offenceId` set to that context's offence id. `OffenceDisplayHelper` resolves `orderIndex` for stable display ordering. |
| `affectedResultCodes[]` | Empty (the rule does not call out specific result codes). |

A hearing with N qualifying offences produces N issues with this rule id, each with one offence in `affectedOffences`.

---

## Runtime override contract

The rule honours the existing `validation_rule` table:

| Column | Behaviour |
|--------|-----------|
| `id = 'DR-DISQ-001'` | identifies the rule |
| `enabled = false` | rule produces no issues |
| `severity = 'WARNING'` | no observable change (already WARNING) |
| `severity = 'ERROR'` | no observable change (ceiling never promotes — Constitution VI) |
| no row | YAML defaults stand (`enabled: true`, condition severity `WARNING`) |

---

## Feature toggle contract

When the Azure App Configuration `RESULTS_VALIDATION` feature flag is OFF, `DefaultValidationService` short-circuits the entire validation pipeline and returns `mode="disabled"` — this rule is *not* evaluated and produces no issues, matching the existing behaviour for `DR-SENT-002`.

---

## Backward compatibility

- **HTTP API contract** (revised 2026-04-28) — `ResultLineDto.category` is added as an **optional** field. Existing callers that omit it produce valid payloads; the validator treats them under FR-015 fail-safe (no recognised F line → no warning). This ensures the lib bump can be rolled out before all callers populate `category`.
- **Older payloads transitional behaviour** — for the period between the lib publish and all callers being upgraded, the rule will be silent on relevant offences (because no F line is identifiable). This is preferable to a noisy false-positive period; ops can monitor `finalCategoryCount = 0` rates to track caller-upgrade progress.
- **Existing rule `DR-SENT-002` is unchanged in behaviour.** Its preprocessor wiring moves from "constructor parameter" to "registry lookup" but its YAML, its `CustodialPreprocessor` algorithm, and its emitted issues are byte-identical. `DR-SENT-002` does not consume `category`.
- **Validator `/api/validation/rules` endpoint** — returns both rules. The `GET /api/validation/rules/{ruleId}` endpoint will work for `DR-DISQ-001`.
