package uk.gov.hmcts.cp.services.rules.cel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.OffenceConviction;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.openapi.model.ValidationRequestWithConvictions;

/**
 * Preprocesses result lines into per-offence CTL contexts consumed by DR-CTL-001 conditions.
 */
@Component
public class CtlPreprocessor implements RulePreprocessor {

    private static final String CTL_SHORT_CODE = "CTL";

    /**
     * For each offence in the request, determines whether a relevant remand result is present,
     * whether a CTL result is present, and whether the offence is convicted. Only offences with
     * at least one relevant result are included in the output.
     *
     * @param requestWithConvictions full validation request including conviction flags
     * @param config preprocessing configuration loaded from YAML
     * @return map of offence id to derived CTL context
     */
    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public Map<String, RuleContext> preprocess(final ValidationRequestWithConvictions requestWithConvictions,
                                                final PreprocessingDefinition config) {
        final Set<String> relevantCodes = config.getFilterShortCodes().stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        final Map<String, String> requiredLabels = buildUpperCaseRequiredLabels(config.getRequiredLabel());

        final List<ResultLineDto> allLines = requestWithConvictions.getValidationRequest().getResultLines();
        final Set<String> convictedOffenceIds = buildConvictedOffenceIds(
                requestWithConvictions.getOffenceConvictions());

        final Map<String, RuleContext> result = new LinkedHashMap<>();

        for (final OffenceDto offence : requestWithConvictions.getValidationRequest().getOffences()) {
            final String offenceId = offence.getId();
            final List<ResultLineDto> offenceLines = allLines.stream()
                    .filter(rl -> offenceId.equals(rl.getOffenceId()))
                    .toList();

            final boolean hasRelevant = offenceLines.stream()
                    .anyMatch(rl -> isRelevantResult(rl, relevantCodes, requiredLabels));

            if (hasRelevant) {
                final boolean hasCTL = offenceLines.stream()
                        .anyMatch(rl -> rl.getShortCode() != null
                                && CTL_SHORT_CODE.equalsIgnoreCase(rl.getShortCode()));

                final boolean convicted = convictedOffenceIds.contains(offenceId);

                result.put(offenceId, new CtlOffenceContext(
                        offenceId,
                        1L,
                        hasCTL ? 1L : 0L,
                        convicted ? 1L : 0L));
            }
        }

        return result;
    }

    private boolean isRelevantResult(final ResultLineDto rl,
                                      final Set<String> relevantCodes,
                                      final Map<String, String> requiredLabels) {
        final boolean relevant;
        if (rl.getShortCode() == null
                || !relevantCodes.contains(rl.getShortCode().toUpperCase(Locale.ROOT))) {
            relevant = false;
        } else {
            final String upperCode = rl.getShortCode().toUpperCase(Locale.ROOT);
            final String requiredLabel = requiredLabels.get(upperCode);
            relevant = requiredLabel == null || requiredLabel.equals(rl.getLabel());
        }
        return relevant;
    }

    private Map<String, String> buildUpperCaseRequiredLabels(final Map<String, String> requiredLabel) {
        final Map<String, String> upper = new LinkedHashMap<>();
        if (requiredLabel != null) {
            for (final Map.Entry<String, String> entry : requiredLabel.entrySet()) {
                upper.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
            }
        }
        return upper;
    }

    private Set<String> buildConvictedOffenceIds(final Set<OffenceConviction> convictions) {
        final Set<String> convicted;
        if (convictions == null) {
            convicted = Set.of();
        } else {
            convicted = convictions.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getConvicted()))
                    .map(c -> c.getOffenceId().toString())
                    .collect(Collectors.toUnmodifiableSet());
        }
        return convicted;
    }
}
