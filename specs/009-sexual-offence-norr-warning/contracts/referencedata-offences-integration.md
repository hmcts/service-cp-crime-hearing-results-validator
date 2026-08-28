# Outbound Integration Contract: `cpp-context-referencedata-offences`

This is a **new** external dependency for `service-cp-crime-hearing-results-validator` — nothing
in this repo calls this service today. This document is the interface contract between this
service (consumer) and the reference-data service (provider), analogous in role to the OpenAPI
spec this service doesn't own for its own inbound contract (Constitution Principle I).

## Endpoint

```
GET {CP_BASE_URL}/referencedataoffences-query-api/query/api/rest/referencedataoffences/offences/{offenceId}
Accept: application/vnd.referencedataoffences.offence+json
CJSCPPUID: {userId}
```

- `{offenceId}` — the reference-data catalog's offence UUID, passed as
  `OffenceDto.getOffenceId()` directly (per product-owner direction, R1 in `research.md` — this
  service's `OffenceDto.offenceId` already **is** the catalog UUID; no code-based resolution step
  is used).
- `CJSCPPUID` — the real service's Drools ACL rejects the request without it (`403`,
  `Access Control failed ... Reason: Rules failed to match`). `ReferencedataOffenceClient` forwards
  the value `TracingFilter` already captured into MDC (`TracingFilter.USER_ID`) from the inbound
  `/validate` request's own `CJSCPPUID` header — it is not re-derived independently. Omitted when
  MDC holds no value (e.g. a call outside a request context), in which case the downstream ACL
  rejection is absorbed by the same fail-open handling as any other failure.
- Method: `ReferencedataOffenceQueryApi.findOffence`, action name
  `referencedataoffences.query.offence` (server-side handler; irrelevant to this consumer beyond
  confirming which HTTP resource fronts it).

## Response (fields this repo reads)

```json
{
  "offenceId": "0000357a-2b27-3eb5-9377-d7e9d680eb87",
  "misCode": "SEX"
}
```

Real responses carry ~35 additional fields (`cjsOffenceCode`, `modeOfTrial`, `details`,
`offenceWording`, `pocCode`, etc.) — this service deserializes only `offenceId` and `misCode`
(see `data-model.md`'s `ReferencedataOffenceResponse`); all other fields are ignored, not
validated, and not persisted.

| Field | Type | Used for |
|---|---|---|
| `misCode` | `String` | `"SEX"` identifies a relevant sexual offence (FR-001 in `spec.md`). Any other value, or field absence, means "not a relevant sexual offence" for this rule. |
| `offenceId` | `String` | Log correlation only. |

## Failure modes and this service's response to each

| Upstream behaviour | This service's handling |
|---|---|
| 200 with `misCode` present | Cache and use the value. |
| 200 with `misCode` null/absent | Treated as "not a relevant sexual offence" — no warning for that offence. Logged at DEBUG (expected for the majority of offences, which are not sexual offences). |
| 404 (unknown `offenceId`) | Fail-open — no warning for that offence. Logged at WARN (unexpected; likely signals the `offenceId`-as-catalog-UUID assumption in R1 doesn't hold for some caller — see "Open risk" in `research.md`). |
| Timeout (>2s connect / >3s read) | Fail-open — no warning for that offence. Logged at WARN. |
| Any other error (5xx, malformed JSON, connection refused) | Fail-open — no warning for that offence. Logged at WARN. |

**This is a one-way availability trade-off, made deliberately**: `DR-SEX-008` can only ever
produce a `WARNING` (never `ERROR` — see spec.md FR-011), so a reference-data outage degrading to
"this warning doesn't fire" is preferable to blocking the entire `/validate` response over one
downstream classification lookup. This mirrors the existing fail-open convention already used by
`IdentityClient` and `AzureAppConfigFetcher` in this codebase.

## Caching

Every successful (non-null `misCode`) lookup is cached in the `referencedataOffences` Caffeine
cache, keyed by `offenceId`, TTL `${referencedata.offences.cache.ttl-seconds:3600}`. Failed
lookups (fail-open results) are **not** cached — a transient outage should not suppress this
warning for a full TTL window once the reference-data service recovers; the next validation
request for the same offence retries the call.

## Test doubles

- **Unit**: `ReferencedataOffenceClient` tested with a mocked `RestTemplate` (or
  `MockRestServiceServer`), covering the failure-mode table above.
- **Integration**: a second static `WireMockServer` in `IntegrationTestBase` (alongside the
  existing identity stub), stubbing `GET /referencedataoffences-query-api/query/api/rest/
  referencedataoffences/offences/{offenceId}` for both a `misCode: "SEX"` offence and a
  non-matching one, following the exact shape of `wiremock/mappings/identity-stub.json`.
- **Live API** (`gradle api`): resolved during implementation — `docker-compose.yml`'s shared
  `wiremock` service already mounts the whole `./wiremock` directory, so
  `wiremock/mappings/referencedataoffences-stub.json` (added alongside `identity-stub.json`) is
  picked up automatically. No compose changes were needed; `SexualOffenceNotificationApiHttpLiveTest`
  exercises it via the existing `CP_BASE_URL` wiring.
