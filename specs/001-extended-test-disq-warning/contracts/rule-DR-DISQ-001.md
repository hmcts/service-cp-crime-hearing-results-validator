# Contract — `DR-DISQ-001` Extended Test Disqualification Warning

This is the contract for the new validation rule. The HTTP API contract (`POST /api/validation/validate`) and the request/response DTOs (`DraftValidationRequest`, `OffenceDto`, `ResultLineDto`, `DefendantDto`, `ValidationIssue`, `AffectedOffence`) are owned upstream by `libs.api.hearing.results.validator` and are unchanged by this feature.

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

Each `DisqualificationContext` exposes the CEL variables documented in `data-model.md` §`DisqualificationContext`. The condition `qualifyingCount > 0` fires for any offence that meets all four gates (relevant, has non-excluded result, no excluded result, no DDOTE/DDOTEL).

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

- The HTTP API contract is unchanged. Consumers get one extra `ValidationIssue` element under qualifying conditions; otherwise no change.
- Existing rule `DR-SENT-002` is unchanged in behaviour. Its preprocessor wiring moves from "constructor parameter" to "registry lookup" but its YAML, its `CustodialPreprocessor` algorithm, and its emitted issues are byte-identical.
- The `GET /api/validation/rules` endpoint will return both rules. The `GET /api/validation/rules/{ruleId}` endpoint will work for `DR-DISQ-001`.
