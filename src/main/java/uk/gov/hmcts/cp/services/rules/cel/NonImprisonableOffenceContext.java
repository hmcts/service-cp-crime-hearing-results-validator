package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link NonImprisonableOffencePreprocessor} for the
 * DR-SENT-011 custodial-sentence-against-non-imprisonable-offence warning rule. One instance per
 * offence that has at least one custodial result line; {@code nonImprisonableCount} is 1 when the
 * effective custodial indicator resolves to {@code "N"}, and 0 when it resolves to {@code "Y"} or
 * is absent (fail-safe: treated as imprisonable).
 */
public record NonImprisonableOffenceContext(
        String offenceId,
        long nonImprisonableCount
) implements RuleEvaluationContext {

    /* default */ static final String NON_IMPRISONABLE_OFFENCE_IDS = "nonImprisonableOffenceIds";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("nonImprisonableCount", nonImprisonableCount);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (NON_IMPRISONABLE_OFFENCE_IDS.equals(setName)) {
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
