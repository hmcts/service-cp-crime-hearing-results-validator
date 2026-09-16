package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-defendant summary of conditional-bail offences and their outcome states,
 * produced by {@link ConditionalBailPreprocessor} and consumed by DR-URG-008 CEL conditions.
 */
public record ConditionalBailContext(
        String defendantId,
        String defendantName,
        long conditionalBailOffenceCount,
        long bailEndedCount,
        long hasUrgentCount,
        List<String> allOffenceIds
) implements RuleEvaluationContext {

    private static final String DEFENDANT_ID_SET = "defendantId";
    private static final String ALL_OFFENCE_IDS_SET = "allOffenceIds";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "conditionalBailOffenceCount", conditionalBailOffenceCount,
                "bailEndedCount", bailEndedCount,
                "hasUrgentCount", hasUrgentCount
        );
    }

    @Override
    public List<String> getDefendantIdSet(final String setName) {
        if (DEFENDANT_ID_SET.equals(setName)) {
            return List.of(defendantId);
        }
        throw new IllegalArgumentException("Unknown defendant set: " + setName);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (ALL_OFFENCE_IDS_SET.equals(setName)) {
            return allOffenceIds;
        }
        throw new IllegalArgumentException("Unknown offence set: " + setName);
    }
}
