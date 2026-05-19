package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-(defendant, offence) context produced by {@link CommunityOrderEndDatePreprocessor} for the
 * community order end-date validation rule (DR-COEW-001). One instance per community order result
 * line (COEW/COS/CONI) found in the request, keyed by {@code defendantId + "_" + offenceId}.
 *
 * <p>Each count variable is 0 or 1 because each context represents exactly one order.
 */
public record CommunityOrderContext(
        String defendantName,
        long curViolationCount,
        long cureViolationCount,
        long curaViolationCount,
        long aarViolationCount,
        long upwrViolationCount,
        List<String> allOffenceIds
) implements RuleEvaluationContext {

    private static final String ALL_OFFENCE_IDS_SET = "allOffenceIds";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "curViolationCount",  curViolationCount,
                "cureViolationCount", cureViolationCount,
                "curaViolationCount", curaViolationCount,
                "aarViolationCount",  aarViolationCount,
                "upwrViolationCount", upwrViolationCount
        );
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (ALL_OFFENCE_IDS_SET.equals(setName)) {
            return allOffenceIds;
        }
        throw new IllegalArgumentException("Unknown offence set: " + setName);
    }
}
