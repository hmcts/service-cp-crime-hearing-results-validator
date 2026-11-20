package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-defendant summary of unresolved EM-flagged offences, produced by
 * {@link ElectronicMonitoringPreprocessor} and consumed by DR-EMON-017 CEL conditions.
 *
 * <p>The record component {@code defendantId} satisfies
 * {@link RuleEvaluationContext#defendantId()} via the implicit Java record accessor —
 * renaming this component would silently break the interface contract.
 */
public record ElectronicMonitoringContext(
        String defendantId,
        String defendantName,
        long unresolvedEmCount,
        List<String> unresolvedOffenceIds,
        List<String> allEmOffenceIds
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

    /**
     * Returns the full set of EM-flagged offence IDs for this defendant (resolved + unresolved),
     * used by {@link MessageTemplateResolver} for stable offence-number ordering when
     * {@code orderIndex} is absent from the offence payload.
     */
    @Override
    public List<String> allOffenceIds() {
        return allEmOffenceIds;
    }
}
