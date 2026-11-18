package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link PreReleaseBailPreprocessor} for rule DR-BAIL-016.
 * One instance per offence that qualifies (current remand status C/L/S + conditional bail
 * result + trigger bail condition present).
 */
public record PreReleaseBailContext(
        String offenceId,
        long triggerCount,
        long adultCount,
        long youthCount,
        long noDobCount,
        long ccicCount,
        long ccscCount
) implements RuleEvaluationContext {

    private static final String OFFENCE_ID_SET = "offenceId";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "triggerCount", triggerCount,
                "adultCount",   adultCount,
                "youthCount",   youthCount,
                "noDobCount",   noDobCount,
                "ccicCount",    ccicCount,
                "ccscCount",    ccscCount);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (OFFENCE_ID_SET.equals(setName)) {
            return List.of(offenceId);
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
