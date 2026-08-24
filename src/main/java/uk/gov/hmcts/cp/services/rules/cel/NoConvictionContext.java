package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link NoConvictionPreprocessor} for the no-conviction check
 * rule (DR-CONV-006). One instance per offence in the request; {@code unconvictedSentenceCount}
 * and {@code convictedCount} are 0 or 1, while {@code finalCategoryCount} and
 * {@code excludedFinalCount} count every matching result line on the offence.
 */
public record NoConvictionContext(
        String offenceId,
        long unconvictedSentenceCount,
        long finalCategoryCount,
        long excludedFinalCount,
        long convictedCount,
        List<String> warningOffenceIds,
        List<String> allOffenceIds
) implements RuleEvaluationContext {

    @Override
    public String defendantName() {
        return null;
    }

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "unconvictedSentenceCount", unconvictedSentenceCount,
                "finalCategoryCount", finalCategoryCount,
                "excludedFinalCount", excludedFinalCount,
                "convictedCount", convictedCount
        );
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        return switch (setName) {
            case "warningOffenceIds" -> warningOffenceIds;
            case "allOffenceIds" -> allOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }
}
