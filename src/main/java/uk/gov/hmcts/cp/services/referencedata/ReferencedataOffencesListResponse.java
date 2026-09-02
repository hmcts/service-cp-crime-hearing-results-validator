package uk.gov.hmcts.cp.services.referencedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Top-level body of the {@code cpp-context-referencedata-offences} {@code GET /offences}
 * search-by-{@code cjsoffencecode} response ({@code
 * application/vnd.referencedataoffences.offences-list+json}). {@link ReferencedataOffenceClient}
 * queries by a single {@code cjsOffenceCode} per lookup, so it reads only the first element of
 * {@link #offences()} when present.
 *
 * @param offences the matched offences, or {@code null}/empty when the {@code cjsOffenceCode} is
 *         not recognised
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReferencedataOffencesListResponse(List<ReferencedataOffenceResponse> offences) {
}
