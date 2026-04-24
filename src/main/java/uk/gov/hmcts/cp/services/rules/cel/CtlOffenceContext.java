package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context for the CTL validation rule, holding binary flags for CEL evaluation.
 */
@SuppressWarnings("PMD.TooFewBranchesForSwitch")
public record CtlOffenceContext(
        String offenceId,
        long hasRelevantResult,
        long hasCtlResult,
        long isConvicted
) implements RuleContext {

    /**
     * Returns the three CTL flags as Long values for CEL evaluation.
     *
     * @return CEL variable map
     */
    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "hasRelevantResult", hasRelevantResult,
                "hasCtlResult", hasCtlResult,
                "isConvicted", isConvicted
        );
    }

    /**
     * Returns the single offence ID in the named set. Only "offenceIds" is supported.
     *
     * @param setName configured offence-id set name
     * @return list containing the single offence id
     */
    @Override
    public List<String> getOffenceIdSet(final String setName) {
        return switch (setName) {
            case "offenceIds" -> List.of(offenceId);
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }

    /**
     * Returns empty string — the CTL message template contains no defendant name placeholder.
     *
     * @return empty string
     */
    @Override
    public String displayName() {
        return "";
    }

    /**
     * Returns a list containing this offence's ID.
     *
     * @return single-element list with the offence id
     */
    @Override
    public List<String> allOffenceIds() {
        return List.of(offenceId);
    }
}
