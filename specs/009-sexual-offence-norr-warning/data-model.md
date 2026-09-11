# Phase 1 Data Model: Sexual Offence Notification Requirement Warning

## Existing entities read by this feature (no changes)

### `DraftValidationRequest` (external, `libs.api.hearing.results.validator`)

| Field | Type | Used for |
|---|---|---|
| `hearingDay` | `java.time.LocalDate` | Adult/Youth age boundary comparison |
| `resultLines` | `List<ResultLineDto>` | Locating `NORRR`/`NORPGP` result lines per offence |
| `defendants` | `List<DefendantDto>` | `dateOfBirth` for age classification |
| `offences` | `List<OffenceDto>` | `offenceCode` (reference-data lookup key), `isConvicted` |

### `OffenceDto` (external)

| Field | Type | Used for |
|---|---|---|
| `offenceCode` | `String` | Passed directly as the reference-data `cjsoffencecode` query parameter (R1) |
| `isConvicted` | `Boolean` | Convicted precondition (FR-002) |

`offenceId` and `orderIndex` are **not** used by this feature's reference-data lookup —
classification is resolved by `offenceCode` against the reference-data search-list endpoint, not
by `offenceId` (this service's `OffenceDto` carries no reference-data catalog UUID) and not from a
local code allow-list.

### `DefendantDto` (external)

| Field | Type | Used for |
|---|---|---|
| `defendantId` | `String` | Linking result lines to their offence's defendant |
| `dateOfBirth` | `java.time.LocalDate` (nullable) | Age classification; null → Adult (R6) |

### `ResultLineDto` (external)

| Field | Type | Used for |
|---|---|---|
| `shortCode` | `String` | Matched case-insensitively against `NORRR`/`NORPGP` |
| `offenceId` | `String` | Grouping result lines to their offence |
| `defendantId` | `String` | Grouping result lines to their defendant |

## New entities: `ReferencedataOffencesListResponse` / `ReferencedataOffenceResponse` (this repo)

Minimal local records capturing only the fields this feature reads from the reference-data
service's `GET .../offences?cjsoffencecode={offenceCode}` search-list response — **not** a full
mirror of that service's ~35 field `ReferencedataOffence` domain object (Constitution Principle
II: local records stay purpose-built, not speculative mirrors of an upstream shape).

```java
package uk.gov.hmcts.cp.services.referencedata;

public record ReferencedataOffencesListResponse(
        List<ReferencedataOffenceResponse> offences
) {}

public record ReferencedataOffenceResponse(
        String offenceId,
        String misCode
) {}
```

| Field | Type | Description |
|---|---|---|
| `offences` | `List<ReferencedataOffenceResponse>` | The matched offence(s) for the requested `cjsOffenceCode`; a single-code lookup is expected to match at most one currently-valid offence, so the client reads only `offences.get(0)`. Empty or absent is treated as "no such offence code". |
| `offences[].offenceId` | `String` | Echoed back from the reference-data catalog; used only for log correlation, not business logic (the request already knows which offence it asked about). |
| `offences[].misCode` | `String` | The classification code this feature exists to read; `"SEX"` identifies a relevant sexual offence. Deserialized case-sensitively; comparison in the client/preprocessor is exact-match `"SEX".equals(misCode)` per the confirmed sample payload (`misCode` values are short, uppercase, stable codes like `"SEX"`, not free text needing normalization). |

Deserialization ignores all other fields on each element (Jackson `@JsonIgnoreProperties(ignoreUnknown = true)` on both records, or an equivalent partial-DTO Jackson config already used elsewhere in this codebase for external JSON) — each upstream element carries ~35 fields (`cjsOffenceCode`, `modeOfTrial`, `details`, `offenceWording`, etc.), none of which this feature needs.

## New entity: `SexualOffenceNotificationContext` (this repo)

A `RuleEvaluationContext` implementation, one instance per offence that is both convicted and
classified as a relevant sexual offence. Offences that are not convicted, or whose reference-data
lookup does not return `misCode == "SEX"` (including lookup failures — R4 fail-open), produce
**no** context entry at all — mirrors `NoConvictionContext`'s offence-keyed, presence-implies-
qualifying shape rather than emitting a context with an explicit "not applicable" flag.

```java
package uk.gov.hmcts.cp.services.rules.cel;

public record SexualOffenceNotificationContext(
        String offenceId,
        String defendantName,
        boolean isYouth,
        boolean hasQualifyingNotification
) implements RuleEvaluationContext {
    // ...
}
```

| Field | Type | Description |
|---|---|---|
| `offenceId` | `String` | The single offence this context represents. Used as the context map key and as the sole entry in `getOffenceIdSet("offenceId")`. |
| `defendantName` | `String` | `"first last"` display name of the offence's charged defendant, via `PreprocessorHelper.buildFullName` (same convention as every other context). |
| `isYouth` | `boolean` | `true` when the charged defendant is under 18 at `hearingDay` (`Period.between(dateOfBirth, hearingDay).getYears() < 18`); `false` (Adult) when 18+ or when `dateOfBirth` is null (fail-safe, R6). |
| `hasQualifyingNotification` | `boolean` | `true` when at least one result line on this offence has short code `NORRR` (Adult and Youth) or `NORPGP` (Youth only — computed by the preprocessor using the already-resolved `isYouth` flag, so this single boolean already encodes the age-appropriate check; CEL only ever needs to test `hasQualifyingNotification == 0`). |

**Validation rules encoded here** (not in CEL, per the "no branching in CEL" convention):

- **Relevant sexual offence**: `misCode` returned by `ReferencedataOffenceClient.lookupMisCode(offenceCode)` equals `"SEX"`. Any other value, or an empty `Optional` (fail-open), excludes the offence from this rule entirely — no context entry.
- **Convicted**: `OffenceDto.getIsConvicted() == Boolean.TRUE`. `null`/`false` excludes the offence — no context entry (same convention as `NoConvictionPreprocessor`, which requires the opposite: it warns when *not* convicted; this rule requires the offence *to be* convicted before it's even in scope).
- **Age classification**: `Period.between(dateOfBirth, hearingDay).getYears() < 18` → `isYouth = true`. Exactly 18 on the hearing date → `isYouth = false` (Adult), matching `>=` semantics. `dateOfBirth == null` or `hearingDay == null` → `isYouth = false` (Adult, fail-safe per R6 — the inverse fail-safe direction from `AgeRestrictedImprisonmentPreprocessor`'s `isUnder21 = false` default, but the same underlying principle: default to the outcome that doesn't assume information the system doesn't have).
- **Qualifying notification**: for Adult contexts, at least one result line on the offence has `shortCode` case-insensitively equal to `NORRR`. For Youth contexts, at least one result line has `shortCode` case-insensitively equal to `NORRR` **or** `NORPGP`.

**Interface method implementations**:

```java
@Override
public Map<String, Long> toCelContext() {
    return Map.of("hasQualifyingNotification", hasQualifyingNotification ? 1L : 0L,
                   "isYouth", isYouth ? 1L : 0L);
}

@Override
public List<String> getOffenceIdSet(final String setName) {
    if ("offenceId".equals(setName)) {
        return List.of(offenceId);
    }
    throw new IllegalArgumentException("Unknown offence set: " + setName);
}

@Override
public List<String> allOffenceIds() {
    return List.of(offenceId);
}
```

No `getDefendantIdSet`/`getCalculatedValue` overrides are needed — both conditions are
`validationLevel: OFFENCE` with no calculated-value placeholder, so the `RuleEvaluationContext`
default (throwing) implementations are never invoked, matching `NoConvictionContext`'s shape.

## Modified entity: `PreprocessingDefinition` (this repo)

Three new optional fields, additive only — no existing preprocessor's YAML changes shape or
behaviour, consistent with every prior preprocessor-specific field already on this shared record
(e.g. `curfewShortCodes`, `communityOrderShortCodes`).

| New field | Type | Populated from YAML |
|---|---|---|
| `qualifyingMisCode` | `String` | `preprocessing.qualifyingMisCode: "SEX"` — kept configurable rather than hardcoded in Java, per Principle I (policy value belongs in YAML). |
| `adultNotificationShortCodes` | `List<String>` | `preprocessing.adultNotificationShortCodes: [NORRR]` |
| `youthNotificationShortCodes` | `List<String>` | `preprocessing.youthNotificationShortCodes: [NORRR, NORPGP]` |

## Modified entity: `CacheConfig` (this repo)

One new registered Caffeine cache, additive alongside the existing two:

| Cache name | Key | TTL source | Rationale |
|---|---|---|---|
| `referencedataOffences` | `offenceCode` (`String`) | `${referencedata.offences.cache.ttl-seconds:3600}` | Offence classification data changes far less often than rule overrides (30s TTL) or feature flags (600s TTL); a 1-hour default balances staleness risk against load on the new external dependency. |

## New entity: `ReferencedataOffenceProperties` (this repo)

`@ConfigurationProperties`-style binding for the new `referencedata.offences.http.*` YAML block
(mirrors `HttpAuthzProperties`'s binding of `authz.http.*`, though that class lives in an external
library — this one is local since there's no shared library for this integration).

| Property | YAML key | Default |
|---|---|---|
| `enabled` | `referencedata.offences.http.enabled` | `true` |
| `offenceUrlTemplate` | `referencedata.offences.http.offence-url-template` | `${CP_BASE_URL:http://localhost:8080}/referencedataoffences-query-api/query/api/rest/referencedataoffences/offences?cjsoffencecode={offenceCode}` |
| `acceptHeader` | `referencedata.offences.http.accept-header` | `application/vnd.referencedataoffences.offences-list+json` |
| `connectTimeoutMs` | `referencedata.offences.http.connect-timeout-ms` | `2000` |
| `readTimeoutMs` | `referencedata.offences.http.read-timeout-ms` | `3000` |

## Unchanged: `ValidationIssue`, `AffectedOffence`, `DraftValidationResponse` (external)

Both `DR-SEX-008` conditions are `validationLevel: OFFENCE`, `severity: WARNING`, producing a
`ValidationIssue` with `affectedOffences` populated via `OffenceDisplayHelper.buildAffectedOffences`
— the same existing code path every other offence-level warning rule already uses. No wire-contract
change; this section exists only to confirm no changes are needed (AC2 / User Story 3 in spec.md
is a verification story against this existing, unmodified shape).
