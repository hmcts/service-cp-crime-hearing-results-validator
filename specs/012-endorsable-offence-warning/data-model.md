# Data Model: Endorsable Offence Warning (CRA-336)

## Entities and Fields

### `ReferencedataOffenceResponse` (modified)

Existing record in `uk.gov.hmcts.cp.services.referencedata`. Add one field:

| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `endorsableFlag` | `Integer` (nullable) | `endorsableFlag` column in referencedata response | `1` = endorsable, `0` = not endorsable, `null` = absent |

All existing fields (`offenceId`, `misCode`, `custodialIndicator`, `details`) unchanged. `@JsonIgnoreProperties(ignoreUnknown = true)` ensures backward compatibility if the upstream service does not yet return the field.

---

### `EndorsableOffenceContext` (new record)

Package: `uk.gov.hmcts.cp.services.rules.cel`

| Field | Type | CEL key | Description |
|-------|------|---------|-------------|
| `offenceId` | `String` | — | Key used to group context per offence |
| `qualifyingCount` | `long` | `qualifyingCount` | `1` when the warning should fire; `0` otherwise |

**`toCelContext()`** returns `Map.of("qualifyingCount", qualifyingCount)`.

**`getOffenceIdSet("endorsableOffenceIds")`** returns `List.of(offenceId)` when `qualifyingCount == 1`, `List.of()` otherwise.

**`defendantName()`** returns `null` (offence-level rule; no defendant name needed).

**`allOffenceIds()`** returns `List.of(offenceId)`.

---

### `PreprocessingDefinition` (modified record)

Add one field to the existing record in `uk.gov.hmcts.cp.services.rules.cel`:

| Field | Type | YAML key | Used by |
|-------|------|----------|---------|
| `satisfyingShortCodes` | `List<String>` | `satisfyingShortCodes` | `EndorsableOffencePreprocessor` |

All existing fields unchanged. New field is `null`-safe (treated as empty list when absent in YAML).

---

## State Transitions

The endorsable-offence warning has no persistent state. It is computed on every validation request call from:
1. `endorsable_flag` resolved from reference-data service (Caffeine-cached by offence code)
2. Result lines on the current request payload

---

## Validation Rules (from spec FR-001 to FR-009)

| Rule | Logic |
|------|-------|
| FR-001 | Check `endorsable_flag` for each offence when ≥1 Category F result is present |
| FR-002 | `qualifyingCount = 1` when: endorsable_flag=1 AND ≥1 non-excluded Category F result AND no satisfying result |
| FR-003 | `qualifyingCount = 0` when endorsable_flag=0 or absent |
| FR-004 | Excluded codes (24): WDRN, WDRNOFF, DISM, DINE, DINI, DISCH, DISC, CTROF, IREMFILE, ERR, ERRF, DHD, ONI, DCS, DCCFSA, DCCFSTA, CQUASH, IQUASH, RESTRAO, STAYP, RBBH, SOCOR, PDW, RBBO |
| FR-005 | Satisfying codes (21): LEP, LEN, LEA, DDO, DDOL, DDOR, DDOTE, DDOTEL, DDD, DDDL, DDDT, DDDTL, DDDTO, DDP, DDPL, DDPR, DDPTE, DDPTEL, DDRCOT, NESR, NDSR |
| FR-006 | Severity = WARNING (advisory; does not block save/share) |
| FR-007 | Each offence assessed independently |
| FR-008 | Coexists with other rules; does not suppress other issues |
| FR-009 | Message: "This offence is endorsable. Add an endorsement, disqualification or special reasons result." |

> Note: 21 unique satisfying codes: 3 endorsement + 5 obligatory disq + 5 discretionary disq + 5 totting disq + 1 DDRCOT + 2 special reasons. The YAML `satisfyingShortCodes` list must include all 21 codes.
