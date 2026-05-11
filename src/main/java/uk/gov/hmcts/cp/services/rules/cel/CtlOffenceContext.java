package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link CtlMissingPreprocessor} for the CTL missing check rule
 * (DR-CTL-001). One instance per offence in the request; {@code ctlWarningCount} is 0 or 1
 * because each context represents exactly one offence.
 */
public record CtlOffenceContext(
        String offenceId,
        long ctlWarningCount,
        List<String> warningOffenceIds,
        List<String> allOffenceIds
) implements RuleEvaluationContext {

    @Override
    public String defendantName() {
        return null;
    }

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("ctlWarningCount", ctlWarningCount);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        return switch (setName) {
            case "warningOffenceIds" -> warningOffenceIds;
            case "allOffenceIds" -> allOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }
}
