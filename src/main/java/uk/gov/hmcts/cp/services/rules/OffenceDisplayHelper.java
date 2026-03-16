package uk.gov.hmcts.cp.services.rules;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.AffectedOffence;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;

import java.util.List;
import java.util.Map;

@Component
public class OffenceDisplayHelper {

    public String resolveDisplayNumber(String id,
                                       Map<String, OffenceDto> offenceMap,
                                       List<String> allOffenceIds) {
        OffenceDto offence = offenceMap.get(id);
        if (offence != null && offence.getOrderIndex() != null) {
            return String.valueOf(offence.getOrderIndex());
        }
        int index = allOffenceIds.indexOf(id);
        if (index >= 0) {
            return String.valueOf(index + 1);
        }
        return id;
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
