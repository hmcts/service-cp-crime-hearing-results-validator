package uk.gov.hmcts.cp.services.referencedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One element of the {@code offences} array in the {@code cpp-context-referencedata-offences}
 * {@code application/vnd.referencedataoffences.offences-list+json} response. The real element
 * carries around 35 fields (e.g. {@code cjsOffenceCode}, {@code modeOfTrial}, {@code details},
 * {@code offenceWording}); this service reads only {@code offenceId} (log correlation),
 * {@code misCode} (the sexual-offence classification test), and {@code endorsableFlag} (the
 * endorsable-offence classification), so every other field is ignored rather than mirrored.
 *
 * @param offenceId the reference-data catalog offence id, echoed back from the matched offence
 * @param misCode the offence's classification code — {@code "SEX"} identifies a relevant sexual
 *         offence; any other value, or a missing field, means "not a relevant sexual offence"
 * @param endorsableFlag boolean — {@code true} when endorsable, {@code false} or absent when not
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReferencedataOffenceResponse(String offenceId, String misCode,
        Boolean endorsableFlag) {
}