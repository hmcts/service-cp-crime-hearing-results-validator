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
 *   <li>AC1 — order end date is on or before the hearing date (not in the future)</li>
 *   <li>AC2a — YRC2 (Curfew) end date exceeds YRO end date</li>
 *   <li>AC2b — YRC1 (Curfew with electronic monitoring) end-of-tag exceeds YRO end date</li>
 *   <li>AC2c — YRC3 (Further curfew requirement made) end date exceeds YRO end date</li>
 * </ul>
 */
public record YouthRehabilitationContext(
        String defendantName,
        long pastEndDateCount,
        long curViolationCount,
        long cureViolationCount,
        long curaViolationCount,
        List<String> pastEndDateOffenceIds,
        List<String> curViolationOffenceIds,
        List<String> cureViolationOffenceIds,
        List<String> curaViolationOffenceIds,
        List<String> allOffenceIds
) implements RuleEvaluationContext {

    /**
     * Returns the AC1/AC2 violation counts as the CEL variable map.
     *
     * @return CEL variable map keyed by expression variable name
     */
    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "pastEndDateCount", pastEndDateCount,
                "curViolationCount", curViolationCount,
                "cureViolationCount", cureViolationCount,
                "curaViolationCount", curaViolationCount
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
            case "pastEndDateOffenceIds" -> pastEndDateOffenceIds;
            case "curViolationOffenceIds" -> curViolationOffenceIds;
            case "cureViolationOffenceIds" -> cureViolationOffenceIds;
            case "curaViolationOffenceIds" -> curaViolationOffenceIds;
            case "allOffenceIds" -> allOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }
}
