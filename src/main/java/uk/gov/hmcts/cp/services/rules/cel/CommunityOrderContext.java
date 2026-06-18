package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-defendant context produced by {@link CommunityOrderEndDatePreprocessor} for the
 * DR-COEW-001 rule. Carries violation counts (exposed to CEL conditions) and the sets of
 * offence ids that triggered each violation (used by the message template resolver to
 * scope inline errors).
 */
public record CommunityOrderContext(
        String defendantName,
        long curViolationCount,
        long cureViolationCount,
        long curaViolationCount,
        long aarViolationCount,
        List<String> curViolationOffenceIds,
        List<String> cureViolationOffenceIds,
        List<String> curaViolationOffenceIds,
        List<String> aarViolationOffenceIds,
        List<String> allOffenceIds
) implements RuleEvaluationContext {

    /**
     * Returns the four AC2 violation counts as the CEL variable map.
     *
     * @return CEL variable map keyed by expression variable name
     */
    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "curViolationCount", curViolationCount,
                "cureViolationCount", cureViolationCount,
                "curaViolationCount", curaViolationCount,
                "aarViolationCount", aarViolationCount
        );
    }

    /**
     * Returns the offence-id set named by a condition's {@code affectedOffenceSet}.
     *
     * @param setName configured offence-id set name
     * @return matching offence-id list
     * @throws IllegalArgumentException if the set name is not known to this context
     */
    @Override
    public List<String> getOffenceIdSet(final String setName) {
        return switch (setName) {
            case "curViolationOffenceIds" -> curViolationOffenceIds;
            case "cureViolationOffenceIds" -> cureViolationOffenceIds;
            case "curaViolationOffenceIds" -> curaViolationOffenceIds;
            case "aarViolationOffenceIds" -> aarViolationOffenceIds;
            case "allOffenceIds" -> allOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }
}
