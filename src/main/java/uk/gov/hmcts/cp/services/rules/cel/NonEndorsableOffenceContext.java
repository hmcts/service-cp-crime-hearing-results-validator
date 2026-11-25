package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link NonEndorsableOffencePreprocessor} for DR-END-014.
 * One instance is produced for each non-endorsable offence that carries at least one trigger
 * result from either the endorsement family (LEP, LEN, LEA) or the disqualification family
 * (DDO/DDD/DDP families plus DDRCOT). Both counts are independent — an offence can have one
 * or both non-zero.
 *
 * @param offenceId              the offence this context represents
 * @param endorsementCount       1 if the offence is non-endorsable and has an endorsement trigger
 * @param disqualificationCount  1 if the offence is non-endorsable and has a disqualification trigger
 */
public record NonEndorsableOffenceContext(
        String offenceId,
        long endorsementCount,
        long disqualificationCount
) implements RuleEvaluationContext {

    private static final String ENDORSEMENT_SET = "nonEndorsableEndorsementOffenceIds";
    private static final String DISQUALIFICATION_SET = "nonEndorsableDisqualificationOffenceIds";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("endorsementCount", endorsementCount, "disqualificationCount", disqualificationCount);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        final List<String> result;
        if (ENDORSEMENT_SET.equals(setName)) {
            result = endorsementCount > 0 ? List.of(offenceId) : List.of();
        } else if (DISQUALIFICATION_SET.equals(setName)) {
            result = disqualificationCount > 0 ? List.of(offenceId) : List.of();
        } else {
            throw new IllegalArgumentException("Unknown offence set: " + setName);
        }
        return result;
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
