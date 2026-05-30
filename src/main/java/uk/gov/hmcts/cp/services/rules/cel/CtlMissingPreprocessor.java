package uk.gov.hmcts.cp.services.rules.cel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Per-offence preprocessor for the DR-CTL-001 CTL missing check rule. Produces one
 * {@link CtlOffenceContext} per offence in the request, with {@code ctlWarningCount} set to 1
 * when all four warning conditions are met:
 *
 * <ol>
 *   <li>At least one result line on the offence carries a trigger short code from the YAML
 *       {@code remandShortCodes} list (e.g. RI, RIYDA, RIH, …).</li>
 *   <li>The offence has no existing CTL record from a previous hearing
 *       ({@code hasExistingCtlRecord} is null or false).</li>
 *   <li>No result line on the offence carries a CTL short code from the YAML
 *       {@code ctlShortCodes} list (e.g. CTL).</li>
 *   <li>The offence is not convicted ({@code isConvicted} is null or false).</li>
 * </ol>
 *
 * <p>All short-code comparisons are case-insensitive (normalised to upper case via
 * {@link PreprocessorHelper}).
 */
@Component
public class CtlMissingPreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "ctl-missing";

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    public Map<String, CtlOffenceContext> preprocess(final DraftValidationRequest request,
                                                      final PreprocessingDefinition config) {
        final Set<String> remandCodes = PreprocessorHelper.upperSet(config.getRemandShortCodes());
        final Set<String> ctlCodes = PreprocessorHelper.upperSet(config.getCtlShortCodes());

        final Map<String, List<ResultLineDto>> resultsByOffence =
                PreprocessorHelper.groupResultsByOffence(request);
        final Map<String, CtlOffenceContext> result = new LinkedHashMap<>();

        if (request.getOffences() != null) {
            for (final OffenceDto offence : request.getOffences()) {
                result.put(offence.getId(),
                        buildContext(offence, resultsByOffence, remandCodes, ctlCodes));
            }
        }

        return result;
    }

    private CtlOffenceContext buildContext(final OffenceDto offence,
                                            final Map<String, List<ResultLineDto>> resultsByOffence,
                                            final Set<String> remandCodes,
                                            final Set<String> ctlCodes) {
        final String offenceId = offence.getId();
        final List<ResultLineDto> lines = resultsByOffence.getOrDefault(offenceId, List.of());

        final boolean hasRemandResult = PreprocessorHelper.anyShortCodeIn(lines, remandCodes);
        final boolean hasExistingCtl = Boolean.TRUE.equals(offence.getHasExistingCtlRecord());
        final boolean hasCtlResult = PreprocessorHelper.anyShortCodeIn(lines, ctlCodes);
        final boolean isConvicted = Boolean.TRUE.equals(offence.getIsConvicted());

        final boolean ctlWarning = hasRemandResult && !hasExistingCtl && !hasCtlResult && !isConvicted;

        return new CtlOffenceContext(
                offenceId,
                ctlWarning ? 1L : 0L,
                ctlWarning ? List.of(offenceId) : List.of(),
                List.of(offenceId));
    }
}
