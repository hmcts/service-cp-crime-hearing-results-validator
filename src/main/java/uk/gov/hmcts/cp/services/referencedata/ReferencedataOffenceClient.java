package uk.gov.hmcts.cp.services.referencedata;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Fail-open client for the {@code cpp-context-referencedata-offences} offence classification
 * lookup. Any failure (timeout, non-2xx, malformed body, or missing {@code misCode}) returns
 * {@link Optional#empty()} rather than propagating — {@code DR-SEX-008} can only ever produce a
 * {@code WARNING}, so a downstream outage degrading to "no warning" is preferable to blocking the
 * whole {@code /validate} response. Mirrors the fail-open convention already used by this
 * codebase's identity lookup ({@code IdentityClient}, in the {@code cp-auth-rules-filter}
 * library) and its own {@code AzureAppConfigFetcher}. See
 * {@code specs/009-sexual-offence-norr-warning/contracts/referencedata-offences-integration.md}.
 */
@Slf4j
@Component
public class ReferencedataOffenceClient {

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
            final RequestEntity<Void> request = RequestEntity.get(uri)
                    .header(HttpHeaders.ACCEPT, properties.acceptHeader())
                    .build();
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
