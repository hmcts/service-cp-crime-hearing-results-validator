# Phase 0 Research: Sexual Offence Notification Requirement Warning

## R1 — Where `misCode` comes from, and how it's looked up

**Superseded**: the original decision below (call the single-offence `{offenceId}` endpoint,
assuming `OffenceDto.offenceId` **is** the reference-data catalog UUID) has been replaced. This
service's `OffenceDto` carries no reference-data catalog UUID after all, so
`ReferencedataOffenceClient` now calls the *code-based* lookup that was originally considered and
rejected below: `GET .../referencedataoffences-query-api/query/api/rest/
referencedataoffences/offences?cjsoffencecode={offenceCode}`, `Accept: application/vnd.
referencedataoffences.offences-list+json`, passing `OffenceDto.getOffenceCode()`, and reading
`misCode` off `offences[0]` of the response. The cache key (`referencedataOffences`, R4) moves
from `offenceId` to `offenceCode` accordingly. See
`contracts/referencedata-offences-integration.md` for the current contract; the "Original
decision" and "Alternatives considered" below are kept for history, not as the current behaviour.

**Original decision**: Call `cpp-context-referencedata-offences`'s query API directly with
`OffenceDto.getOffenceId()` as the path parameter — `GET .../referencedataoffences-query-api/
query/api/rest/referencedataoffences/offences/{offenceId}`, `Accept: application/vnd.
referencedataoffences.offence+json` — and read `misCode` off the response. No code-based
resolution step (`GET /offences?cjsoffencecode=...`) is used.

**Rationale (original)**: Confirmed directly by the product owner: this service's
`OffenceDto.offenceId` already **is** the reference-data catalog's offence UUID (the hearing's
offence records are sourced from that catalog), so the single-offence `findOffence`/`{offenceId}`
endpoint resolves it in one call. This was cross-checked against a real sample response supplied
by the product owner for `GET /offences/{offenceId}` — it includes `"offenceId":"0000357a-2b27-
3eb5-9377-d7e9d680eb87"` and `"misCode":"SEX"` among ~35 other fields, of which this feature reads
exactly two: `offenceId` (for logging/cache-key correlation) and `misCode`.

**Alternatives considered (original)**:
- *Code-based lookup first* (`GET /offences?cjsoffencecode={offenceCode}`, then read `offenceId`
  off the first result, mirroring the pattern used by `cpp-context-sjp`'s
  `ReferenceOffencesDataService` in the wider CPP estate) — a real, working endpoint, confirmed to
  exist via the module's RAML and multiple production call sites in a sibling repo. Rejected at
  the time for this feature only because it was believed unnecessary given the direct `offenceId`
  mapping above — **this is the option R1 now uses**, once that mapping assumption proved wrong
  (see "Superseded" above).
- *Hardcoded offence-code allow-list in YAML*, the pattern `DR-DISQ-002` uses for
  `relevantOffenceCodes` — this was the original fallback assumption written into `spec.md` before
  the product owner clarified the real data source. Rejected: it would drift from the live
  reference-data catalog and duplicates data this service does not own.

**Open risk (original; resolved by the supersession above)**: if any upstream caller ever sends an
`offenceId` that is a purely local/hearing-scoped identifier rather than the reference-data
catalog UUID, the lookup will 404 or return an unrelated offence. The fail-open contract (R4)
means a 404/error simply produces no warning for that offence — a silent miss, not a crash — so
this risk degrades gracefully but should be watched via the client's log line (see R4) if
`DR-SEX-008` under-fires in production. This is exactly what triggered the supersession above.

## R2 — Rule id / category: `DR-SEX-008`

**Decision**: Category `SEX` (new), id `DR-SEX-008` — next sequential number after the seven
existing rules (`DR-DISQ-002`, `DR-CONV-006`, `DR-CTL-003`, `DR-YRO-004`, `DR-COEW-005`,
`DR-SENT-001`, `DR-AGE-007`).

**Rationale**: This is a genuinely new policy family (sexual-offence notification requirements),
not a variant of any existing category — `CONV` is about conviction status generally, `AGE` is
about custodial eligibility, neither fits. A dedicated `SEX` category keeps this rule
discoverable for policy reviewers scanning by category, consistent with how `DISQ` and `CTL` are
each their own single-rule category today.

## R3 — One rule, two conditions, not two rules

**Decision**: A single YAML file (`DR-SEX-008.yaml`) with two conditions — `AC1` (Adult) and
`AC1A` (Youth) — both `severity: WARNING`, `validationLevel: OFFENCE`, sharing one preprocessing
block, rather than two separate rule files.

**Rationale**: Both conditions test the same preconditions (relevant sexual offence, convicted)
against the same context, differing only in which notification short codes count as satisfying
the requirement and in message wording — exactly the shape `DR-SENT-001` already uses for its
three conditions sharing one `custodial-concurrent-consecutive` preprocessing block. Splitting
into two rule files would duplicate the entire preprocessing definition (mis_code lookup,
convicted check) for no benefit, and would let the two conditions drift independently over time
even though they are the same policy with an age-based branch.

**Alternatives considered**:
- *Two rule files* (`DR-SEX-008` Adult, `DR-SEX-009` Youth) — rejected per above; also would
  double the number of external reference-data lookups per offence (each rule's preprocessor runs
  independently), doubling load against the new external dependency for no reason.

## R4 — HTTP client shape: `RestTemplate`, fail-open, tight timeout, cached

**Decision**: A `RestTemplate`-based client (`ReferencedataOffenceClient`), instantiated with a
dedicated `SimpleClientHttpRequestFactory` (connect timeout 2s, read timeout 3s), wrapped in a
try/catch that logs at WARN via SLF4J and returns `Optional.empty()` on any exception, non-2xx
response, or missing `misCode` field. Results are cached via a new named Caffeine cache
(`referencedataOffences`, keyed by `offenceCode` — see R1's supersession, TTL configurable via
`referencedata.offences.cache.ttl-seconds`, default proposed 3600s — offence classification data
changes far less often than rule-override data, so a much longer TTL than the existing 30s
`ruleOverrides`/600s `featureFlags` caches is appropriate).

**Rationale**: This mirrors the only two existing external-call patterns in this codebase
(`IdentityClient` in the `cp-auth-rules-filter` library, and this repo's own
`AzureAppConfigFetcher`) — both use blocking HTTP (`RestTemplate` / raw `HttpClient`) with a
fail-open catch-and-default. No reactive stack (`spring-boot-starter-webflux`/`WebClient`), Feign,
or resilience4j exists in `build.gradle`; introducing any of them for a single new client would be
a disproportionate new dependency for one integration. The tighter timeout (2s/3s vs. the
library's 20s/21s) reflects that this call can fire once per offence per validation request (not
once per request like the identity check), so a slow reference-data service must fail fast rather
than compounding across offences.

**Alternatives considered**:
- *`WebClient` (reactive)* — rejected; no reactive stack exists anywhere else in this Spring MVC
  service, and introducing one for a single blocking-context call point (`preprocess()` runs
  synchronously inside a controller thread) adds complexity without benefit — `.block()`ing a
  reactive call here would defeat the point of using it.
- *No caching* — rejected; the same offence code recurs across many hearings (e.g. a common
  Road Traffic Act or sexual-offence charge), and re-querying reference data for every validation
  request would multiply the new dependency's load unnecessarily for data that changes rarely.
- *Batch/multi-offence lookup* — no batch-by-UUID endpoint was found in the reference-data query
  API; deferred as a future optimisation if per-offence latency proves material under load
  (Gatling `CapacitySimulation`/`StressSimulation` should be watched after this ships).
- *Fail-closed (block sharing on lookup failure)* — rejected; this rule can only ever produce a
  `WARNING` (FR-011/FR-008 in spec.md — never blocks sharing), so failing the whole validation
  request because one downstream classification lookup is unavailable would be a worse outcome
  than simply not raising this one warning. Matches this codebase's established fail-open
  convention for every other external/optional lookup.

## R5 — Preprocessing approach: new preprocessor, offence-keyed context

**Decision**: A new `ValidationPreprocessor` (`SexualOffenceNotificationPreprocessor`, qualifier
`sexual-offence-notification-requirement`) producing one `SexualOffenceNotificationContext` per
offence that is convicted AND classified `misCode == "SEX"` (offences that fail either check are
simply absent from the map — no context entry, mirroring `NoConvictionPreprocessor`'s
offence-keyed shape rather than `AgeRestrictedImprisonmentPreprocessor`'s defendant-keyed shape,
since this rule's precondition and message are inherently per-offence, not per-defendant-across-
offences).

**Rationale**: None of the seven existing preprocessors combine "classify via external lookup" +
"convicted check" + "age-branch the required short-code set" — each existing context shape is
purpose-built for its one rule's counts, and none expose a misCode/reference-data hook. This
follows the same "new preprocessor when no existing one fits" decision already made for
`AgeRestrictedImprisonmentPreprocessor` (age) and `NoConvictionPreprocessor` (convicted-status),
which this feature's context composes ideas from (convicted check) and extends (external
classification, age branch, dual short-code set).

**Alternatives considered**:
- *Extend `NoConvictionPreprocessor`/`NoConvictionContext`* — rejected; that context has no
  concept of misCode, age, or notification codes, and bolting all three onto an unrelated rule's
  context would violate the "one preprocessor, one policy concern" shape every other preprocessor
  follows.
- *Extend `AgeRestrictedResultContext`* — rejected; that context is defendant-keyed and grouped by
  master-defendant-id for a different rule's purpose (imprisonment eligibility), not offence-keyed;
  forcing this rule's per-offence semantics through it would be a worse fit than a new context.

## R6 — Fail-safe default for missing/unknown defendant age

**Decision**: Reuse the same fail-safe direction already chosen in `spec.md`'s Assumptions and
FR-010: when `dateOfBirth` is null, treat the defendant as Adult (apply the `NORRR`-only
requirement), consistent with `Period.between`-based age comparisons elsewhere in this codebase
(`AgeRestrictedImprisonmentPreprocessor`). Unlike `AgeRestrictedImprisonmentPreprocessor` (which
fails safe toward **not triggering** an `ERROR`), this rule's fail-safe direction is toward the
**stricter** of the two conditions (Adult, requiring only `NORRR`) because both outcomes here are
advisory `WARNING`s, not blocking `ERROR`s — there is no "safe toward no warning" direction to
prefer, so the fail-safe choice is simply the one that matches the more common case (Adult
defendants) and avoids assuming a youth-specific requirement (`NORPGP`) the system cannot confirm
applies.

**Rationale**: No new decision beyond what's already documented in the spec; recorded here to
confirm it survives the technical design unchanged. `Period.between(dateOfBirth, hearingDay).
getYears() &gt;= 18` is a direct reuse of the existing `AgeRestrictedImprisonmentPreprocessor`
pattern for a different threshold (18 vs. 21) and an inverted boolean (`isYouth` vs. `isUnder21`,
same underlying comparison shape).
