package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link DisqualificationExtendedTestPreprocessor} for the
 * extended-test disqualification rule (DR-DISQ-001). One instance per offence in the request;
 * counts are 0 or 1 because each context represents exactly one offence.
 */
public record DisqualificationContext(
        String offenceId,
        long qualifyingCount,
        long relevantCount,
        long excludedFinalCount,
        long disqExtTestCount,
        List<String> qualifyingOffenceIds,
        List<String> allOffenceIds
) implements RuleEvaluationContext {

    @Override
    public String defendantName() {
        return null;
    }

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "qualifyingCount", qualifyingCount,
                "relevantCount", relevantCount,
                "excludedFinalCount", excludedFinalCount,
                "disqExtTestCount", disqExtTestCount
        );
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        return switch (setName) {
            case "qualifyingOffenceIds" -> qualifyingOffenceIds;
            case "allOffenceIds" -> allOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }
}
