package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.anyShortCodeIn;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupResultsByOffence;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClient;

/**
 * Per-offence preprocessor for DR-SENT-011. For each offence with at least one custodial result
 * line, resolves the effective custodial indicator via
 * {@link ReferencedataOffenceClient#getCustodialIndicator(String)} — which applies column-over-JSON
 * precedence client-side. When the indicator is {@code "N"} the offence is non-imprisonable and
 * {@code nonImprisonableCount = 1}; any other outcome (indicator is {@code "Y"}, absent, or the
 * lookup fails) is treated as imprisonable (fail-open: no warning).
 */
@Component
@RequiredArgsConstructor
public class NonImprisonableOffencePreprocessor implements ValidationPreprocessor {

    public static final String QUALIFIER = "non-imprisonable-offence";

    private final ReferencedataOffenceClient referencedataOffenceClient;

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    public Map<String, NonImprisonableOffenceContext> preprocess(
            final DraftValidationRequest request,
            final PreprocessingDefinition config) {
        final Set<String> custodialCodes = upperSet(config.filterShortCodes());
        final Map<String, OffenceDto> offenceById = indexOffences(request);
        final Map<String, List<ResultLineDto>> resultsByOffence = groupResultsByOffence(request);
        return resultsByOffence.entrySet().stream()
                .filter(entry -> anyShortCodeIn(entry.getValue(), custodialCodes))
                .filter(entry -> offenceById.containsKey(entry.getKey()))
                .map(entry -> buildContext(entry.getKey(), offenceById.get(entry.getKey())))
                .collect(Collectors.toMap(
                        NonImprisonableOffenceContext::offenceId,
                        ctx -> ctx,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private NonImprisonableOffenceContext buildContext(
            final String offenceId, final OffenceDto offence) {
        final boolean nonImprisonable = "N".equals(
                referencedataOffenceClient.getCustodialIndicator(offence.getOffenceCode())
                        .orElse(null));
        return new NonImprisonableOffenceContext(offenceId, nonImprisonable ? 1L : 0L);
    }

    private static Map<String, OffenceDto> indexOffences(final DraftValidationRequest request) {
        final List<OffenceDto> offences = request.getOffences();
        return offences == null ? Map.of()
                : offences.stream()
                        .filter(o -> o.getOffenceId() != null)
                        .collect(Collectors.toMap(OffenceDto::getOffenceId, o -> o, (a, b) -> a,
                                LinkedHashMap::new));
    }
}
