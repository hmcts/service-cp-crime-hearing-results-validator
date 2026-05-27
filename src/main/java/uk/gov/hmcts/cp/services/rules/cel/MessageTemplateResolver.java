package uk.gov.hmcts.cp.services.rules.cel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;

/**
 * Expands rule message templates with defendant names and formatted offence references.
 */
@Component
public class MessageTemplateResolver {

    private static final int SINGLE_ELEMENT = 1;
    private static final int TWO_ELEMENTS = 2;

    private final OffenceDisplayHelper offenceDisplayHelper;

    /** Constructs the resolver with the given offence display helper. */
    public MessageTemplateResolver(final OffenceDisplayHelper offenceDisplayHelper) {
        this.offenceDisplayHelper = offenceDisplayHelper;
    }

    /**
     * Resolves placeholders in the message template with defendant name, offence references,
     * and arbitrary string variables supplied by the context.
     *
     * <p>Substitution order:
     * <ol>
     *   <li>{@code ${offenceNumber}} → formatted offence label(s)</li>
     *   <li>{@code ${defendantName}} → defendant display name (when non-null)</li>
     *   <li>{@code ${key}} → value from {@code stringVariables} for each key present</li>
     * </ol>
     *
     * <p>Unknown {@code ${key}} tokens not present in {@code stringVariables} are left unchanged.
     */
    public String resolve(final String template,
                          final String defendantName,
                          final List<String> affectedOffenceIds,
                          final Map<String, OffenceDto> offenceMap,
                          final List<String> allOffenceIds,
                          final Map<String, String> stringVariables) {
        String result = resolve(template, defendantName, affectedOffenceIds, offenceMap, allOffenceIds);
        for (final Map.Entry<String, String> entry : stringVariables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /** Resolves placeholders in the message template with defendant and offence details. */
    public String resolve(final String template,
                          final String defendantName,
                          final List<String> affectedOffenceIds,
                          final Map<String, OffenceDto> offenceMap,
                          final List<String> allOffenceIds) {
        final String formatted = formatOffenceNumbers(affectedOffenceIds, offenceMap, allOffenceIds);
        String result = template.replace("${offenceNumber}", formatted);
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
        } else if (SINGLE_ELEMENT == formatted.size()) {
            result = formatted.getFirst();
        } else if (TWO_ELEMENTS == formatted.size()) {
            result = formatted.get(0) + " and " + formatted.get(1);
        } else {
            result = String.join(", ", formatted.subList(0, formatted.size() - 1))
                    + " and " + formatted.getLast();
        }
        return result;
    }
}
