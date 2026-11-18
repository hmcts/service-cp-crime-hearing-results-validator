package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-defendant summary of unresolved EM-flagged offences, produced by
 * {@link ElectronicMonitoringPreprocessor} and consumed by DR-EMON-017 CEL conditions.
 */
public record ElectronicMonitoringContext(
        String defendantId,
        String defendantName,
        long unresolvedEmCount,
        List<String> unresolvedOffenceIds
) implements RuleEvaluationContext {

    private static final String UNRESOLVED_OFFENCE_IDS_SET = "unresolvedOffenceIds";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("unresolvedEmCount", unresolvedEmCount);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (UNRESOLVED_OFFENCE_IDS_SET.equals(setName)) {
            return unresolvedOffenceIds;
        }
        throw new IllegalArgumentException("Unknown set: " + setName);
    }

    @Override
    public List<String> allOffenceIds() {
        return unresolvedOffenceIds;
    }
}
