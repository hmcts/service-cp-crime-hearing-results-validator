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
 * Fail-open client for the {@code cpp-context-referencedata-offences} offence classification
 * lookup. Any failure (timeout, non-2xx, malformed body, or missing {@code misCode}) returns
 * {@link Optional#empty()} rather than propagating — {@code DR-SEX-008} can only ever produce a
 * {@code WARNING}, so a downstream outage degrading to "no warning" is preferable to blocking the
 * whole {@code /validate} response. Mirrors the fail-open convention already used by this
 * codebase's identity lookup ({@code IdentityClient}, in the {@code cp-auth-rules-filter}
 * library) and its own {@code AzureAppConfigFetcher}. See
 * {@code specs/009-sexual-offence-norr-warning/contracts/referencedata-offences-integration.md}.
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
     * Looks up the {@code misCode} classification for an offence.
     *
     * <p>Cached by {@code offenceId} only on success. Spring's caching layer unwraps an {@code
     * Optional} return value before evaluating {@code unless} — an {@code Optional.empty()}
     * result is presented to the SpEL expression as a plain {@code null}, not an empty {@code
     * Optional}, so the guard is {@code unless = "#result == null"} rather than {@code
     * "#result.isEmpty()"} (which throws {@code SpelEvaluationException} against a null context).
     * This deliberately excludes fail-open results from the cache, so a transient reference-data
     * outage does not suppress {@code DR-SEX-008} for the full cache TTL after the dependency
     * recovers; the next validation request for the same offence simply retries the call.
     *
     * @param offenceId the reference-data catalog offence id ({@code OffenceDto.offenceId})
     * @return the offence's {@code misCode}, or {@link Optional#empty()} if unavailable for any
     *         reason (lookup disabled, blank id, non-2xx, timeout, malformed body, or missing
     *         {@code misCode})
     */
    @Cacheable(value = "referencedataOffences", key = "#offenceId", unless = "#result == null")
    public Optional<String> lookupMisCode(final String offenceId) {
        Optional<String> misCode = Optional.empty();
        if (properties.enabled() && offenceId != null && !offenceId.isBlank()) {
            misCode = fetchMisCode(offenceId);
        }
        return misCode;
    }

    private Optional<String> fetchMisCode(final String offenceId) {
        Optional<String> misCode = Optional.empty();
        try {
            // UriComponentsBuilder percent-encodes the expanded {offenceId} path variable,
            // unlike a raw String.replace, so a value containing reserved URI characters
            // (/, ?, #, ...) is treated as an opaque path segment rather than altering the
            // resulting path/query.
            final URI uri = UriComponentsBuilder.fromUriString(properties.offenceUrlTemplate())
                    .build(Map.of("offenceId", offenceId));
            final RequestEntity.HeadersBuilder<?> requestBuilder = RequestEntity.get(uri)
                    .header(HttpHeaders.ACCEPT, properties.acceptHeader());
            final String userId = MDC.get(TracingFilter.USER_ID);
            if (userId != null && !userId.isBlank()) {
                requestBuilder.header(CJSCPPUID_HEADER, userId);
            }
            final RequestEntity<Void> request = requestBuilder.build();
            final ResponseEntity<ReferencedataOffenceResponse> response =
                    restTemplate.exchange(request, ReferencedataOffenceResponse.class);
            final ReferencedataOffenceResponse body = response.getBody();
            if (body != null && body.misCode() != null && !body.misCode().isBlank()) {
                misCode = Optional.of(body.misCode());
            } else {
                log.debug("No misCode returned for offenceId={}", Encode.forJava(offenceId));
            }
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Reference-data offence lookup failed for offenceId={} ({}): {}",
                    Encode.forJava(offenceId), e.getClass().getSimpleName(), e.getMessage());
        }
        return misCode;
    }
}
