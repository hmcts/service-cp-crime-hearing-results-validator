package uk.gov.hmcts.cp.services.rules;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.AffectedOffence;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;

import java.util.List;
import java.util.Map;

/**
 * Builds user-facing offence references for validation messages and affected offence payloads.
 */
@Component
public class OffenceDisplayHelper {

    public String resolveDisplayNumber(String id,
                                       Map<String, OffenceDto> offenceMap,
                                       List<String> allOffenceIds) {
        OffenceDto offence = offenceMap.get(id);
        String countNumber;
        if (offence != null && offence.getOrderIndex() != null) {
            countNumber = String.valueOf(offence.getOrderIndex());
        } else {
            int index = allOffenceIds.indexOf(id);
            countNumber = index >= 0 ? String.valueOf(index + 1) : id;
        }

        if (offence != null && offence.getCaseUrn() != null && !offence.getCaseUrn().isBlank()) {
            return "Offence " + countNumber + " (URN:" + offence.getCaseUrn() + ")";
        }
        return "Offence " + countNumber;
    }

    public int resolveOrderIndex(String id,
                                  Map<String, OffenceDto> offenceMap,
                                  List<String> allOffenceIds) {
        OffenceDto offence = offenceMap.get(id);
        if (offence != null && offence.getOrderIndex() != null) {
            return offence.getOrderIndex();
        }
        int index = allOffenceIds.indexOf(id);
        return index >= 0 ? index + 1 : Integer.MAX_VALUE;
    }

    public List<AffectedOffence> buildAffectedOffences(List<String> offenceIds,
                                                        Map<String, OffenceDto> offenceMap) {
        return offenceIds.stream()
                .map(id -> {
                    OffenceDto offence = offenceMap.get(id);
                    return AffectedOffence.builder()
                            .offenceId(id)
                            .offenceTitle(offence != null ? offence.getOffenceTitle() : null)
                            .build();
                })
                .toList();
    }
}
