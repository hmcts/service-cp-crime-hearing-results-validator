package uk.gov.hmcts.cp.services.referencedata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code referencedata.offences.http.*} configuration block — the outbound HTTP
 * contract for the {@code cpp-context-referencedata-offences} lookup used by
 * {@link ReferencedataOffenceClient}. Reuses this codebase's {@code @Value}-with-inline-default
 * convention (see {@code CacheConfig}'s {@code @Bean} method parameters) rather than a separate
 * {@code @ConfigurationProperties} mechanism, which is not otherwise used in this repo — this is
 * the first place that convention is applied to a {@code @Component} record's canonical
 * constructor rather than a {@code @Bean} method.
 *
 * @param enabled whether the reference-data lookup is attempted at all; when {@code false},
 *         {@link ReferencedataOffenceClient} short-circuits to {@code Optional.empty()}
 * @param offenceUrlTemplate the {@code GET} URL template, with a literal {@code {offenceCode}}
 *         placeholder substituted per lookup with the offence's {@code cjsOffenceCode} (
 *         {@code OffenceDto.getOffenceCode()})
 * @param acceptHeader the vendor media type sent as the {@code Accept} header
 * @param connectTimeoutMs connect timeout in milliseconds
 * @param readTimeoutMs read timeout in milliseconds
 */
@Component
public record ReferencedataOffenceProperties(
        @Value("${referencedata.offences.http.enabled:true}") boolean enabled,
        @Value("${referencedata.offences.http.offence-url-template:"
                + "http://localhost:8080/referencedataoffences-query-api/query/api/rest/"
                + "referencedataoffences/offences?cjsoffencecode={offenceCode}}") String offenceUrlTemplate,
        @Value("${referencedata.offences.http.accept-header:"
                + "application/vnd.referencedataoffences.offences-list+json}") String acceptHeader,
        @Value("${referencedata.offences.http.connect-timeout-ms:2000}") int connectTimeoutMs,
        @Value("${referencedata.offences.http.read-timeout-ms:3000}") int readTimeoutMs) {
}
