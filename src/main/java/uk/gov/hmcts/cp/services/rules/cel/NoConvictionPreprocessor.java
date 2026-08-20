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
 * Per-offence preprocessor for the DR-CONV-006 no-conviction rule. Produces one
 * {@link NoConvictionContext} per offence in the request, with counts that drive the YAML CEL
 * condition {@code unconvictedSentenceCount > 0}.
 *
 * <p>An offence warrants the warning when both of the following hold:
 * <ol>
 *   <li>At least one {@code category='F'} result line on the offence has a short code that is
 *       <em>not</em> in the excluded set (the same {@code excludedFinalShortCodes} used by
 *       DR-DISQ-001, but with no offence-code restriction — this rule applies to every offence).</li>
 *   <li>The offence is not convicted ({@code OffenceDto.isConvicted}, the same field used by
 *       DR-CTL-001; {@code null} is treated as not convicted).</li>
 * </ol>
 *
 * <p>Short-code comparisons are case-insensitive via {@link PreprocessorHelper}.
 */
@Component
public class NoConvictionPreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "no-conviction-check";

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    public Map<String, NoConvictionContext> preprocess(final DraftValidationRequest request,
                                                        final PreprocessingDefinition config) {
        final Set<String> excludedShortCodes = PreprocessorHelper.upperSet(config.getExcludedFinalShortCodes());

        final Map<String, List<ResultLineDto>> resultsByOffence = PreprocessorHelper.groupResultsByOffence(request);
        final Map<String, NoConvictionContext> result = new LinkedHashMap<>();

        if (request.getOffences() != null) {
            for (final OffenceDto offence : request.getOffences()) {
                result.put(offence.getOffenceId(),
                        buildContext(offence, resultsByOffence, excludedShortCodes));
            }
        }

        return result;
    }

    private NoConvictionContext buildContext(final OffenceDto offence,
                                              final Map<String, List<ResultLineDto>> resultsByOffence,
                                              final Set<String> excludedShortCodes) {
        final String offenceId = offence.getOffenceId();
        final List<ResultLineDto> lines = resultsByOffence.getOrDefault(offenceId, List.of());

        final List<ResultLineDto> finalLines = lines.stream()
                .filter(rl -> rl.getCategory() == ResultLineDto.CategoryEnum.F)
                .toList();

        final long finalCategoryCount = finalLines.size();
        final long excludedFinalCount = finalLines.stream()
                .filter(rl -> PreprocessorHelper.hasUpperCode(rl, excludedShortCodes))
                .count();
        final boolean finalNonExcluded = finalLines.stream()
                .anyMatch(rl -> !PreprocessorHelper.hasUpperCode(rl, excludedShortCodes));
        final boolean isConvicted = Boolean.TRUE.equals(offence.getIsConvicted());

        final boolean unconvictedSentence = finalNonExcluded && !isConvicted;

        return new NoConvictionContext(
                offenceId,
                unconvictedSentence ? 1L : 0L,
                finalCategoryCount,
                excludedFinalCount,
                isConvicted ? 1L : 0L,
                unconvictedSentence ? List.of(offenceId) : List.of(),
                List.of(offenceId));
    }
}
