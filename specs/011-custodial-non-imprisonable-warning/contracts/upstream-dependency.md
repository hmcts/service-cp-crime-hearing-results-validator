# Upstream Dependencies

## `api-cp-crime-hearing-results-validator` — NO CHANGE REQUIRED

The earlier draft of this spec (v2) proposed adding `custodialIndicator` and
`libraCustodialIndicatorCode` fields to `OffenceDto`. That approach has been **abandoned**.

The custodial indicator is now resolved by calling the **`cpp-context-referencedata-offences`
API** directly — the same outbound endpoint already used by `DR-SEX-008` via
`ReferencedataOffenceClient`. No changes to `OffenceDto` or the upstream JAR are needed.
`libs.versions.toml` should remain at the version on `main` (no version bump required for this
feature).

---

## `cpp-context-referencedata-offences` — no code change required, but a known gap exists

The reference data API currently passes the `custodial_indicator` column value straight through
without applying the `details_json` fallback. The precedence logic is implemented **client-side**
in `ReferencedataOffenceClient.getCustodialIndicator()` in this repo instead:

1. Reads the top-level `custodialIndicator` field (column value) from the API response.
2. If null / not `"Y"` or `"N"`, reads `details.document.libra.custodialindicator.code` from the
   nested `details` object in the same response.

This is correct for current data: newer offence records have a populated column; older records
(pre-migration `022`) have `null` in the column and the value lives only in `details_json`.

**Future alignment**: the `CUSTODIAL_INDICATOR_NOTES.md` in `cpp-context-referencedata-offences`
describes changes that would move the precedence logic server-side. When those changes ship, this
client-side fallback becomes a no-op (column always wins anyway); it can be simplified then.
No action is required for this feature.

---

## Outbound API contract (unchanged from DR-SEX-008)

```
GET {CP_BASE_URL}/referencedataoffences-query-api/query/api/rest/referencedataoffences/offences?cjsoffencecode={offenceCode}
Accept: application/vnd.referencedataoffences.offences-list+json
CJSCPPUID: {userId}
```

Fields read by `getCustodialIndicator()` (in addition to the existing `misCode` / `offenceId`):

| Field | Type | Description |
|-------|------|-------------|
| `offences[0].custodialIndicator` | `String` | From `custodial_indicator` DB column. `"Y"`, `"N"`, or `null`. |
| `offences[0].details.document.libra.custodialindicator.code` | `String` | From `details_json` JSONB. `"Y"`, `"N"`, or absent. |

**Fail-open**: any failure (timeout, non-2xx, malformed response, absent fields) returns
`Optional.empty()` → treated as imprisonable → no warning. Consistent with `lookupMisCode`.
