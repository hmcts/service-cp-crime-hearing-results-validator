package uk.gov.hmcts.cp.services.rules.cel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.services.referencedata.Endorsability;
import uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClient;

/**
 * Per-offence preprocessor for DR-END-014 (endorsement or disqualification result on non-endorsable
 * offence). Produces one {@link NonEndorsableOffenceContext} per non-endorsable offence that carries
 * at least one trigger result from the endorsement family (LEP, LEN, LEA) or the disqualification
 * family (DDO/DDD/DDP families plus DDRCOT). The reference-data lookup is made at most once per
 * offence regardless of how many trigger codes are present.
 *
 * <p>Only {@link Endorsability#NON_ENDORSABLE} produces a context — that includes a successful
 * reference-data response with no {@code endorsableFlag} value. {@link Endorsability#UNAVAILABLE}
 * (lookup failed or disabled) is fail-open: no context, no warning, matching DR-SEX-008.
 */
@Component
public class NonEndorsableOffencePreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "non-endorsable-offence";

    private final ReferencedataOffenceClient referencedataOffenceClient;

    /**
     * Constructs the preprocessor with its reference-data collaborator.
     *
     * @param referencedataOffenceClient client for endorsability lookups
     */
    public NonEndorsableOffencePreprocessor(final ReferencedataOffenceClient referencedataOffenceClient) {
        this.referencedataOffenceClient = referencedataOffenceClient;
    }

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // context record is per-offence; no reusable pool applies
    public Map<String, NonEndorsableOffenceContext> preprocess(final DraftValidationRequest request,
                                                               final PreprocessingDefinition config) {
        final Set<String> endorsementCodes = PreprocessorHelper.upperSet(config.endorsementShortCodes());
        final Set<String> disqualificationCodes = PreprocessorHelper.upperSet(config.disqualificationTriggerShortCodes());
        final Map<String, List<ResultLineDto>> resultsByOffence = PreprocessorHelper.groupResultsByOffence(request);
        final Map<String, NonEndorsableOffenceContext> result = new LinkedHashMap<>();
        if (request.getOffences() != null) {
            for (final OffenceDto offence : request.getOffences()) {
                final String offenceId = offence.getOffenceId();
                final List<ResultLineDto> lines = resultsByOffence.getOrDefault(offenceId, List.of());
                final boolean hasEndorsementTrigger = PreprocessorHelper.anyShortCodeIn(lines, endorsementCodes);
                final boolean hasDisqualificationTrigger = PreprocessorHelper.anyShortCodeIn(lines, disqualificationCodes);
                if (hasEndorsementTrigger || hasDisqualificationTrigger) {
                    final Endorsability endorsability =
                            referencedataOffenceClient.lookupEndorsability(offence.getOffenceCode());
                    if (endorsability == Endorsability.NON_ENDORSABLE) {
                        result.put(offenceId, new NonEndorsableOffenceContext(
                                offenceId,
                                hasEndorsementTrigger ? 1L : 0L,
                                hasDisqualificationTrigger ? 1L : 0L));
                    }
                }
            }
        }
        return result;
    }
}
