package uk.gov.hmcts.cp.services.rules.cel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClient;

/**
 * Per-offence preprocessor for the DR-ENDORSEMENT-012 endorsable-offence warning rule.
 * Produces one {@link EndorsableOffenceContext} per offence in the request.
 *
 * <p>An offence qualifies (qualifyingCount=1) when:
 * <ol>
 *   <li>The reference-data service returns {@code endorsableFlag = 1} for its offence code, AND</li>
 *   <li>At least one Category-F result line is present whose short code is NOT in
 *       {@code excludedFinalShortCodes}, AND</li>
 *   <li>No result line (any category) has a short code in {@code satisfyingShortCodes}.</li>
 * </ol>
 *
 * <p>A context entry is emitted for EVERY offence (even non-qualifying ones) so the CEL engine
 * always has a context to evaluate. Fail-open: a missing or non-1 endorsable flag → qualifyingCount=0
 * (no warning), matching the precedent set by {@link ReferencedataOffenceClient#getCustodialIndicator}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndorsableOffencePreprocessor implements ValidationPreprocessor {

    public static final String QUALIFIER = "endorsable-offence";

    private final ReferencedataOffenceClient referencedataOffenceClient;

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public Map<String, EndorsableOffenceContext> preprocess(final DraftValidationRequest request,
                                                             final PreprocessingDefinition config) {
        final Set<String> excludedFinalCodes = PreprocessorHelper.upperSet(config.excludedFinalShortCodes());
        final Set<String> satisfyingCodes = PreprocessorHelper.upperSet(config.satisfyingShortCodes());
        final Map<String, List<ResultLineDto>> resultsByOffence =
                PreprocessorHelper.groupResultsByOffence(request);

        final Map<String, EndorsableOffenceContext> result = new LinkedHashMap<>();
        if (request.getOffences() != null) {
            for (final OffenceDto offence : request.getOffences()) {
                final String offenceId = offence.getOffenceId();
                final long qualifyingCount = computeQualifyingCount(
                        offence, resultsByOffence.getOrDefault(offenceId, List.of()),
                        excludedFinalCodes, satisfyingCodes);
                result.put(offenceId, new EndorsableOffenceContext(offenceId, qualifyingCount));
            }
        }
        return result;
    }

    private long computeQualifyingCount(
            final OffenceDto offence,
            final List<ResultLineDto> lines,
            final Set<String> excludedFinalCodes,
            final Set<String> satisfyingCodes) {

        final List<ResultLineDto> finalLines = lines.stream()
                .filter(rl -> rl.getCategory() == ResultLineDto.CategoryEnum.F)
                .toList();
        final boolean hasNonExcludedFinal = finalLines.stream()
                .anyMatch(rl -> !PreprocessorHelper.hasUpperCode(rl, excludedFinalCodes));

        long qualifyingCount = 0L;
        if (hasNonExcludedFinal) {
            final Optional<Integer> endorsableFlag =
                    referencedataOffenceClient.getEndorsableFlag(offence.getOffenceCode());
            if (Optional.of(1).equals(endorsableFlag)
                    && !PreprocessorHelper.anyShortCodeIn(lines, satisfyingCodes)) {
                qualifyingCount = 1L;
            }
        }
        return qualifyingCount;
    }
}
