package uk.gov.hmcts.cp.services.referencedata;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.slf4j.MDC;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.cp.filters.tracing.TracingFilter;

/**
 * Fail-open client for the {@code cpp-context-referencedata-offences} offence lookup, used by
 * {@code DR-SEX-008} ({@link #lookupMisCode(String)}) and {@code DR-END-014} ({@link
 * #lookupEndorsability(String)}). Any failure (timeout, non-2xx, malformed body) degrades to "no
 * answer" — {@link Optional#empty()} for {@code misCode}, {@link Endorsability#UNAVAILABLE} for
 * endorsability — rather than propagating, and is never cached. Both rules only ever produce a
 * {@code WARNING}, so a downstream outage degrading to "no warning" is preferable to blocking the
 * whole {@code /validate} response or raising false warnings. Mirrors the fail-open convention already used by this
 * codebase's identity lookup ({@code IdentityClient}, in the {@code cp-auth-rules-filter}
 * library) and its own {@code AzureAppConfigFetcher}. See
 * {@code specs/009-sexual-offence-norr-warning/contracts/referencedata-offences-integration.md}.
 *
 * <p>Looks up by {@code cjsOffenceCode} (via the {@code GET /offences?cjsoffencecode=...}
 * search-list endpoint, {@code Accept: application/vnd.referencedataoffences.offences-list+json})
 * rather than the single-offence {@code GET /offences/{offenceId}} endpoint — the reference-data
 * catalog's offence UUID is not otherwise available to this service's {@code OffenceDto}.
 *
 * <p>The downstream service's Drools access-control rules require the caller's {@code CJSCPPUID}
 * on every request. This client forwards the same value {@link TracingFilter} captured from the
 * inbound {@code /validate} request's {@code CJSCPPUID} header into MDC ({@link
 * TracingFilter#USER_ID}) — it does not re-derive or accept the identity independently. When MDC
 * holds no value (e.g. a call outside a request context), the header is simply omitted and the
 * downstream ACL rejection is absorbed by the same fail-open handling as any other failure.
 */
@Slf4j
@Component
public class ReferencedataOffenceClient {

    /** Header the {@code cpp-context-referencedata-offences} Drools ACL requires per request. */
    private static final String CJSCPPUID_HEADER = "CJSCPPUID";

    private final ReferencedataOffenceProperties properties;
    private final RestTemplate restTemplate;

    /**
     * Builds the client with a dedicated, tight timeout configuration — this call can fire once
     * per offence per validation request (not once per request, like the identity check), so a
     * slow reference-data service must fail fast rather than compounding across offences.
     *
     * @param properties bound {@code referencedata.offences.http.*} configuration
     */
    public ReferencedataOffenceClient(final ReferencedataOffenceProperties properties) {
        this.properties = properties;
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Looks up the {@code misCode} classification for an offence, by its {@code cjsOffenceCode}.
     *
     * <p>Cached by {@code offenceCode} only on success. Spring's caching layer unwraps an {@code
     * Optional} return value before evaluating {@code unless} — an {@code Optional.empty()}
     * result is presented to the SpEL expression as a plain {@code null}, not an empty {@code
     * Optional}, so the guard is {@code unless = "#result == null"} rather than {@code
     * "#result.isEmpty()"} (which throws {@code SpelEvaluationException} against a null context).
     * This deliberately excludes fail-open results from the cache, so a transient reference-data
     * outage does not suppress {@code DR-SEX-008} for the full cache TTL after the dependency
     * recovers; the next validation request for the same offence simply retries the call.
     *
     * @param offenceCode the offence's CJS offence code ({@code OffenceDto.getOffenceCode()})
     * @return the offence's {@code misCode}, or {@link Optional#empty()} if unavailable for any
     *         reason (lookup disabled, blank code, non-2xx, timeout, malformed body, an empty
     *         {@code offences} list, or missing {@code misCode})
     */
    @Cacheable(value = "referencedataOffences", key = "#offenceCode", unless = "#result == null")
    public Optional<String> lookupMisCode(final String offenceCode) {
        Optional<String> misCode = Optional.empty();
        if (properties.enabled() && offenceCode != null && !offenceCode.isBlank()) {
            misCode = fetchMisCode(offenceCode);
        }
        return misCode;
    }

    /**
     * Looks up whether an offence is endorsable, by its {@code cjsOffenceCode}.
     *
     * <p>A successful response with {@code endorsableFlag} null or missing is {@link
     * Endorsability#NON_ENDORSABLE} — CRA-337 treats "has no value" as {@code 0}. Any failure to
     * get an answer is {@link Endorsability#UNAVAILABLE}, which {@code DR-END-014} treats as "no
     * warning" — the same fail-open posture as {@link #lookupMisCode(String)}, so an outage does
     * not raise a warning against every genuinely endorsable offence. {@code UNAVAILABLE} is never
     * cached, so the next validation request retries once the dependency recovers.
     *
     * @param offenceCode the offence's CJS offence code ({@code OffenceDto.getOffenceCode()})
     * @return {@code ENDORSABLE}, {@code NON_ENDORSABLE}, or {@code UNAVAILABLE} when the lookup
     *         is disabled, the code is blank, or the call fails (non-2xx, timeout, malformed body,
     *         empty {@code offences} list)
     */
    @Cacheable(value = "referencedataOffences", key = "'ef:' + #offenceCode",
            unless = "#result == T(uk.gov.hmcts.cp.services.referencedata.Endorsability).UNAVAILABLE")
    public Endorsability lookupEndorsability(final String offenceCode) {
        Endorsability endorsability = Endorsability.UNAVAILABLE;
        if (properties.enabled() && offenceCode != null && !offenceCode.isBlank()) {
            endorsability = fetchEndorsability(offenceCode);
        }
        return endorsability;
    }

    private Endorsability fetchEndorsability(final String offenceCode) {
        Endorsability endorsability = Endorsability.UNAVAILABLE;
        final ReferencedataOffenceResponse offence = fetchOffenceResponse(offenceCode);
        if (offence != null) {
            if (Boolean.TRUE.equals(offence.endorsableFlag())) {
                endorsability = Endorsability.ENDORSABLE;
            } else {
                if (offence.endorsableFlag() == null) {
                    log.debug("No endorsableFlag returned for offenceCode={}; treating as non-endorsable",
                            Encode.forJava(offenceCode));
                }
                endorsability = Endorsability.NON_ENDORSABLE;
            }
        }
        return endorsability;
    }

    private Optional<String> fetchMisCode(final String offenceCode) {
        Optional<String> misCode = Optional.empty();
        final ReferencedataOffenceResponse offence = fetchOffenceResponse(offenceCode);
        if (offence != null && offence.misCode() != null && !offence.misCode().isBlank()) {
            misCode = Optional.of(offence.misCode());
        } else if (offence != null) {
            log.debug("No misCode returned for offenceCode={}", Encode.forJava(offenceCode));
        }
        return misCode;
    }

    /**
     * Executes the HTTP lookup for a given offence code and returns the first matching
     * {@link ReferencedataOffenceResponse}, or {@code null} on any failure (timeout, non-2xx,
     * malformed body, or empty offences list). Callers map the response field they need.
     *
     * <p>UriComponentsBuilder percent-encodes the expanded {@code {offenceCode}} template
     * variable, so a value containing reserved URI characters ({@code /}, {@code ?}, {@code #},
     * …) is treated as an opaque query value rather than altering the resulting path/query.
     */
    private ReferencedataOffenceResponse fetchOffenceResponse(final String offenceCode) {
        ReferencedataOffenceResponse offence = null;
        try {
            final URI uri = UriComponentsBuilder.fromUriString(properties.offenceUrlTemplate())
                    .build(Map.of("offenceCode", offenceCode));
            final RequestEntity.HeadersBuilder<?> requestBuilder = RequestEntity.get(uri)
                    .header(HttpHeaders.ACCEPT, properties.acceptHeader());
            final String userId = MDC.get(TracingFilter.USER_ID);
            if (userId != null && !userId.isBlank()) {
                requestBuilder.header(CJSCPPUID_HEADER, userId);
            }
            final RequestEntity<Void> request = requestBuilder.build();
            final ResponseEntity<ReferencedataOffencesListResponse> response =
                    restTemplate.exchange(request, ReferencedataOffencesListResponse.class);
            offence = firstOffenceOf(response.getBody());
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Reference-data offence lookup failed for offenceCode={} ({}): {}",
                    Encode.forJava(offenceCode), e.getClass().getSimpleName(), e.getMessage());
        }
        return offence;
    }

    /**
     * Returns the first element of the response's {@code offences} list, or {@code null} when
     * the body, its list, or the list itself is absent/empty. A single {@code cjsOffenceCode}
     * lookup is expected to match at most one currently-valid offence.
     */
    private ReferencedataOffenceResponse firstOffenceOf(final ReferencedataOffencesListResponse body) {
        ReferencedataOffenceResponse offence = null;
        if (body != null && body.offences() != null && !body.offences().isEmpty()) {
            offence = body.offences().getFirst();
        }
        return offence;
    }
}
