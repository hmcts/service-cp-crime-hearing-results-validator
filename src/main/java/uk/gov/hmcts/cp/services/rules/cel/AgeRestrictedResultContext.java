package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-defendant summary of imprisonment-type results and age eligibility, consumed by the
 * {@code DR-AGE-001} rule.
 */
public record AgeRestrictedResultContext(
        String defendantId,
        String defendantName,
        boolean isUnder21,
        List<String> qualifyingOffenceIds
) implements RuleEvaluationContext {

    private static final String QUALIFYING_OFFENCE_IDS_SET = "qualifyingOffenceIds";
    private static final String DEFENDANT_ID_SET = "defendantId";

    /**
     * Converts the age-eligibility flag into the numeric context consumed by CEL expressions.
     *
     * @return CEL variable map for this defendant context
     */
    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("isUnder21", isUnder21 ? 1L : 0L);
    }

    /**
     * Returns the named offence-id set referenced by a condition's {@code affectedOffenceSet}.
     *
     * @param setName configured offence-id set name
     * @return matching offence-id list
     */
    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (QUALIFYING_OFFENCE_IDS_SET.equals(setName)) {
            return qualifyingOffenceIds;
        }
        throw new IllegalArgumentException("Unknown offence set: " + setName);
    }

    @Override
    public List<String> getDefendantIdSet(final String setName) {
        if (DEFENDANT_ID_SET.equals(setName)) {
            return List.of(defendantId);
        }
        throw new IllegalArgumentException("Unknown defendant set: " + setName);
    }

    @Override
    public List<String> allOffenceIds() {
        return qualifyingOffenceIds;
    }
}
