package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link DrugDrivingCourseReductionPreprocessor} for the
 * drug driving course-reduction rule (DR-DISQ-013). One instance per offence in the request;
 * {@code qualifyingCount} is 0 or 1 because each context represents exactly one offence.
 */
public record DrugDrivingContext(
        String offenceId,
        long qualifyingCount,
        List<String> qualifyingOffenceIds
) implements RuleEvaluationContext {

    private static final String QUALIFYING_OFFENCE_IDS = "qualifyingOffenceIds";

    @Override
    public String defendantName() {
        // Per-offence context — not anchored to a single defendant; null per RuleEvaluationContext contract.
        return null;
    }

    @Override
    public List<String> allOffenceIds() {
        return List.of(offenceId);
    }

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("qualifyingCount", qualifyingCount);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (QUALIFYING_OFFENCE_IDS.equals(setName)) {
            return qualifyingOffenceIds;
        }
        throw new IllegalArgumentException("Unknown offence set: " + setName);
    }
}
