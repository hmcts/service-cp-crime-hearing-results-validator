package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link EndorsableOffencePreprocessor} for the
 * DR-ENDORSEMENT-012 endorsable-offence warning rule. One instance per offence in the request;
 * {@code qualifyingCount} is 1 when the warning should fire, 0 otherwise.
 */
public record EndorsableOffenceContext(
        String offenceId,
        long qualifyingCount
) implements RuleEvaluationContext {

    /* default */ static final String ENDORSABLE_OFFENCE_IDS = "endorsableOffenceIds";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("qualifyingCount", qualifyingCount);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (ENDORSABLE_OFFENCE_IDS.equals(setName)) {
            return qualifyingCount == 1 ? List.of(offenceId) : List.of();
        }
        throw new IllegalArgumentException("Unknown offence set: " + setName);
    }

    @Override
    public String defendantName() {
        return null;
    }

    @Override
    public List<String> allOffenceIds() {
        return List.of(offenceId);
    }
}
