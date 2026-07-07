package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-defendant context produced by {@link CommunityOrderEndDatePreprocessor} for the
 * DR-COEW-001 rule. Carries violation counts (exposed to CEL conditions) and the sets of
 * offence ids that triggered each violation (used by the message template resolver to
 * scope inline errors).
 *
 * <p>Also carries the DD-41655 requirement duration-mismatch counts/offence-id sets (CUR, CURE,
 * AAR — independent of the AC2 order-end-date checks above) and, per offence, the correctly
 * calculated end date for use in the {@code ${calculatedEndDate}} message-template placeholder.
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
        List<String> allOffenceIds,
        long curDurationMismatchCount,
        long cureDurationMismatchCount,
        long aarDurationMismatchCount,
        List<String> curDurationMismatchOffenceIds,
        List<String> cureDurationMismatchOffenceIds,
        List<String> aarDurationMismatchOffenceIds,
        Map<String, String> curCalculatedEndDateByOffenceId,
        Map<String, String> cureCalculatedEndDateByOffenceId,
        Map<String, String> aarCalculatedEndDateByOffenceId
) implements RuleEvaluationContext {

    /**
     * Returns the four AC2 violation counts plus the three DD-41655 duration-mismatch counts as
     * the CEL variable map.
     *
     * @return CEL variable map keyed by expression variable name
     */
    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "curViolationCount", curViolationCount,
                "cureViolationCount", cureViolationCount,
                "curaViolationCount", curaViolationCount,
                "aarViolationCount", aarViolationCount,
                "curDurationMismatchCount", curDurationMismatchCount,
                "cureDurationMismatchCount", cureDurationMismatchCount,
                "aarDurationMismatchCount", aarDurationMismatchCount
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
            case "curDurationMismatchOffenceIds" -> curDurationMismatchOffenceIds;
            case "cureDurationMismatchOffenceIds" -> cureDurationMismatchOffenceIds;
            case "aarDurationMismatchOffenceIds" -> aarDurationMismatchOffenceIds;
            case "allOffenceIds" -> allOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }

    /**
     * Returns the calculated correct end date for the given offence from the named
     * {@code calculatedValueSet} map, or {@code null} if that offence has no entry.
     *
     * @param setName configured calculated-value set name
     * @param offenceId offence id to look up
     * @return the calculated date string, or {@code null} if absent
     * @throws IllegalArgumentException if the set name is not known to this context
     */
    @Override
    public String getCalculatedValue(final String setName, final String offenceId) {
        final Map<String, String> map = switch (setName) {
            case "curCalculatedEndDateByOffenceId" -> curCalculatedEndDateByOffenceId;
            case "cureCalculatedEndDateByOffenceId" -> cureCalculatedEndDateByOffenceId;
            case "aarCalculatedEndDateByOffenceId" -> aarCalculatedEndDateByOffenceId;
            default -> throw new IllegalArgumentException("Unknown calculated-value set: " + setName);
        };
        return map.get(offenceId);
    }
}
