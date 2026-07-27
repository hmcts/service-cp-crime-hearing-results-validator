package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-defendant context produced by {@link YouthRehabilitationPreprocessor} for the
 * DR-YRO-001 rule. Carries violation counts (exposed to CEL conditions) and the sets of
 * offence ids that triggered each violation (used by the message template resolver to
 * scope inline errors).
 *
 * <p>Covers:
 * <ul>
 *   <li>AC2a — YRC2 (Curfew) end date exceeds YRO end date</li>
 *   <li>AC2b — YRC1 (Curfew with electronic monitoring) end-of-tag exceeds YRO end date</li>
 *   <li>AC2c — YRC3 (Further curfew requirement made) end date exceeds YRO end date</li>
 *   <li>DUR-YRC2 — YRC2 end date does not match Start date + Curfew period − 1 day</li>
 *   <li>DUR-YRC1 — YRC1 end date of tagging does not match Start date of tagging + period − 1 day</li>
 * </ul>
 */
public record YouthRehabilitationContext(
        String defendantName,
        long curViolationCount,
        long cureViolationCount,
        long curaViolationCount,
        List<String> curViolationOffenceIds,
        List<String> cureViolationOffenceIds,
        List<String> curaViolationOffenceIds,
        List<String> allOffenceIds,
        long curDurationMismatchCount,
        long cureDurationMismatchCount,
        List<String> curDurationMismatchOffenceIds,
        List<String> cureDurationMismatchOffenceIds,
        Map<String, String> curCalculatedEndDateByOffenceId,
        Map<String, String> cureCalculatedEndDateByOffenceId
) implements RuleEvaluationContext {

    /**
     * Returns the AC2 violation counts and duration-mismatch counts as the CEL variable map.
     *
     * @return CEL variable map keyed by expression variable name
     */
    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "curViolationCount", curViolationCount,
                "cureViolationCount", cureViolationCount,
                "curaViolationCount", curaViolationCount,
                "curDurationMismatchCount", curDurationMismatchCount,
                "cureDurationMismatchCount", cureDurationMismatchCount
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
            case "allOffenceIds" -> allOffenceIds;
            case "curDurationMismatchOffenceIds" -> curDurationMismatchOffenceIds;
            case "cureDurationMismatchOffenceIds" -> cureDurationMismatchOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }

    /**
     * Returns the calculated correct end date for an offence, named by a condition's
     * {@code calculatedValueSet}.
     *
     * @param setName configured calculated-value set name
     * @param offenceId offence id to look up
     * @return the calculated {@code dd/MM/yyyy} end date, or {@code null} if absent
     * @throws IllegalArgumentException if the set name is not known to this context
     */
    @Override
    public String getCalculatedValue(final String setName, final String offenceId) {
        return switch (setName) {
            case "curCalculatedEndDateByOffenceId" -> curCalculatedEndDateByOffenceId.get(offenceId);
            case "cureCalculatedEndDateByOffenceId" -> cureCalculatedEndDateByOffenceId.get(offenceId);
            default -> throw new IllegalArgumentException("Unknown calculated-value set: " + setName);
        };
    }
}
