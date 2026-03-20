package uk.gov.hmcts.cp.services.rules.cel;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MessageTemplateResolver {

    private final OffenceDisplayHelper offenceDisplayHelper;

    public MessageTemplateResolver(OffenceDisplayHelper offenceDisplayHelper) {
        this.offenceDisplayHelper = offenceDisplayHelper;
    }

    public String resolve(String template,
                          String defendantName,
                          List<String> affectedOffenceIds,
                          Map<String, OffenceDto> offenceMap,
                          List<String> allOffenceIds) {
        String formatted = formatOffenceNumbers(affectedOffenceIds, offenceMap, allOffenceIds);
        String result = template.replace("${offenceNumbers}", formatted);
        if (defendantName != null) {
            result = result.replace("${defendantName}", defendantName);
        }
        return result;
    }

    private String formatOffenceNumbers(List<String> offenceIds,
                                        Map<String, OffenceDto> offenceMap,
                                        List<String> allOffenceIds) {
        return offenceIds.stream()
                .map(id -> offenceDisplayHelper.resolveDisplayNumber(id, offenceMap, allOffenceIds))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
