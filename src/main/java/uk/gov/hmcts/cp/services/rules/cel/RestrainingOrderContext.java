package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link RestrainingOrderMultiplePersonsPreprocessor} for the
 * DR-RESTRAO-010 multiple protected persons warning rule. One instance per offence that has at
 * least one RESTRAO result line; {@code multiplePersonsCount} is 0 or 1.
 */
public record RestrainingOrderContext(
        String offenceId,
        long multiplePersonsCount,
        List<String> breachingOffenceIds,
        List<String> allOffenceIds
) implements RuleEvaluationContext {

    @Override
    public String defendantName() {
        return null;
    }

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("multiplePersonsCount", multiplePersonsCount);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        return switch (setName) {
            case "breachingOffenceIds" -> breachingOffenceIds;
            case "allOffenceIds" -> allOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }
}