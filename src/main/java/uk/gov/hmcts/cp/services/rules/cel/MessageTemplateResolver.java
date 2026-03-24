package uk.gov.hmcts.cp.services.rules.cel;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Expands rule message templates with defendant names and formatted offence references.
 */
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
        List<String> formatted = offenceIds.stream()
                .sorted(Comparator.comparingInt(
                        id -> offenceDisplayHelper.resolveOrderIndex(id, offenceMap, allOffenceIds)))
                .map(id -> offenceDisplayHelper.resolveDisplayNumber(id, offenceMap, allOffenceIds))
                .toList();

        if (formatted.size() == 1) {
            return formatted.getFirst();
        }
        if (formatted.size() == 2) {
            return formatted.get(0) + " and " + formatted.get(1);
        }
        return String.join(", ", formatted.subList(0, formatted.size() - 1))
                + " and " + formatted.getLast();
    }
}
