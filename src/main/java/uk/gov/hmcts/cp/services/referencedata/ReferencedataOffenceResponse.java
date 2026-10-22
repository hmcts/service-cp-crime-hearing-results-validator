package uk.gov.hmcts.cp.services.referencedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One element of the {@code offences} array in the {@code cpp-context-referencedata-offences}
 * {@code application/vnd.referencedataoffences.offences-list+json} response. The real element
 * carries around 35 fields; this service reads only the fields listed below — every other field
 * is ignored rather than mirrored.
 *
 * @param offenceId the reference-data catalog offence id, echoed back from the matched offence
 * @param misCode the offence's classification code — {@code "SEX"} identifies a relevant sexual
 *         offence; any other value, or a missing field, means "not a relevant sexual offence"
 * @param custodialIndicator top-level custodial-indicator column value ({@code "Y"} or
 *         {@code "N"}); {@code null} for pre-migration-022 records — fall through to
 *         {@code details.document.libra.custodialindicator.code} in that case
 * @param details the full offence document from {@code details_json}; may be {@code null};
 *         the relevant nested path is {@code document.libra.custodialindicator.code}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReferencedataOffenceResponse(String offenceId, String misCode,
        String custodialIndicator, OffenceDetails details) {

    /** Root of the {@code details_json} offence document. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OffenceDetails(OffenceDocument document) {}

    /** Wrapper for the Libra-specific section of the offence document. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OffenceDocument(LibraSection libra) {}

    /** Libra section carrying the custodial-indicator code object. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LibraSection(CustodialIndicatorCode custodialindicator) {}

    /** Custodial indicator code — {@code "Y"} (imprisonable) or {@code "N"} (non-imprisonable). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustodialIndicatorCode(String code) {}
}
