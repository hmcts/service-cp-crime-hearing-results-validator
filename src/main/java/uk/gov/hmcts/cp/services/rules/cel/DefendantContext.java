package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Derived custodial-sentencing summary for a single defendant or defendant group.
 */
public record DefendantContext(
        String defendantName,
        long noInfoCount,
        long hasInfoCount,
        long hasBothCount,
        long hasPrimaryCount,
        long totalOffences,
        List<String> noInfoOffenceIds,
        List<String> hasInfoOffenceIds,
        List<String> hasBothOffenceIds,
        List<String> allOffenceIds,
        List<String> allNoInfoOffenceIds
) implements RuleContext {

    /**
     * Converts the summary counts into the numeric context consumed by CEL expressions.
     *
     * @return CEL variable map for this defendant context
     */
    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "noInfoCount", noInfoCount,
                "hasInfoCount", hasInfoCount,
                "hasBothCount", hasBothCount,
                "hasPrimaryCount", hasPrimaryCount,
                "totalOffences", totalOffences
        );
    }

    /**
     * Returns the named offence-id set referenced by a condition's {@code affectedOffenceSet}.
     *
     * @param setName configured offence-id set name
     * @return matching offence-id list
     */
    @Override
    public List<String> getOffenceIdSet(final String setName) {
        return switch (setName) {
            case "noInfoOffenceIds" -> noInfoOffenceIds;
            case "hasInfoOffenceIds" -> hasInfoOffenceIds;
            case "hasBothOffenceIds" -> hasBothOffenceIds;
            case "allOffenceIds" -> allOffenceIds;
            case "allNoInfoOffenceIds" -> allNoInfoOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }

    /**
     * Returns the defendant display name for message template substitution.
     *
     * @return defendant display name
     */
    @Override
    public String displayName() {
        return defendantName;
    }
}
