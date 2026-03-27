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

    private static final int SINGLE_ELEMENT = 1;
    private static final int TWO_ELEMENTS = 2;

    private final OffenceDisplayHelper offenceDisplayHelper;

    public MessageTemplateResolver(final OffenceDisplayHelper offenceDisplayHelper) {
        this.offenceDisplayHelper = offenceDisplayHelper;
    }

    public String resolve(final String template,
                          final String defendantName,
                          final List<String> affectedOffenceIds,
                          final Map<String, OffenceDto> offenceMap,
                          final List<String> allOffenceIds) {
        final String formatted = formatOffenceNumbers(affectedOffenceIds, offenceMap, allOffenceIds);
        String result = template.replace("${offenceNumbers}", formatted);
        if (defendantName != null) {
            result = result.replace("${defendantName}", defendantName);
        }
        return result;
    }

    private String formatOffenceNumbers(final List<String> offenceIds,
                                        final Map<String, OffenceDto> offenceMap,
                                        final List<String> allOffenceIds) {
        final List<String> formatted = offenceIds.stream()
                .sorted(Comparator.comparingInt(
                        id -> offenceDisplayHelper.resolveOrderIndex(id, offenceMap, allOffenceIds)))
                .map(id -> offenceDisplayHelper.resolveDisplayNumber(id, offenceMap, allOffenceIds))
                .toList();

        final String result;
        if (formatted.isEmpty()) {
            result = "";
        } else if (formatted.size() == SINGLE_ELEMENT) {
            result = formatted.getFirst();
        } else if (formatted.size() == TWO_ELEMENTS) {
            result = formatted.get(0) + " and " + formatted.get(1);
        } else {
            result = String.join(", ", formatted.subList(0, formatted.size() - 1))
                    + " and " + formatted.getLast();
        }
        return result;
    }
}
