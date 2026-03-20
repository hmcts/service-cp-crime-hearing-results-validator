package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

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
        List<String> allOffenceIds
) {

    public Map<String, Long> toCelContext() {
        return Map.of(
                "noInfoCount", noInfoCount,
                "hasInfoCount", hasInfoCount,
                "hasBothCount", hasBothCount,
                "hasPrimaryCount", hasPrimaryCount,
                "totalOffences", totalOffences
        );
    }

    public List<String> getOffenceIdSet(String setName) {
        return switch (setName) {
            case "noInfoOffenceIds" -> noInfoOffenceIds;
            case "hasInfoOffenceIds" -> hasInfoOffenceIds;
            case "hasBothOffenceIds" -> hasBothOffenceIds;
            case "allOffenceIds" -> allOffenceIds;
            default -> throw new IllegalArgumentException("Unknown offence set: " + setName);
        };
    }
}
