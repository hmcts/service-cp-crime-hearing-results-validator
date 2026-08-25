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
    private static final int SOLE_DEFENDANT_IN_HEARING = 1;
    private static final String DEFENDANT_NAMES_TOKEN = "${defendantNames}";

    private final OffenceDisplayHelper offenceDisplayHelper;

    /** Constructs the resolver with the given offence display helper. */
    public MessageTemplateResolver(final OffenceDisplayHelper offenceDisplayHelper) {
        this.offenceDisplayHelper = offenceDisplayHelper;
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

    /**
     * Resolves placeholders as {@link #resolve(String, String, List, Map, List)} does, then
     * additionally replaces {@code ${key}} for each entry in {@code extraPlaceholders}. Used for
     * per-offence, condition-specific computed values (e.g. {@code ${calculatedEndDate}}) that
     * have no fixed token name known to this resolver.
     */
    public String resolve(final String template,
                          final String defendantName,
                          final List<String> affectedOffenceIds,
                          final Map<String, OffenceDto> offenceMap,
                          final List<String> allOffenceIds,
                          final Map<String, String> extraPlaceholders) {
        String result = resolve(template, defendantName, affectedOffenceIds, offenceMap, allOffenceIds);
        for (final Map.Entry<String, String> entry : extraPlaceholders.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * Expands {@code ${defendantNames}} in the template with the formatted list of defendant
     * names, unless the hearing itself has only one defendant (or none recorded) — in that case
     * the "This affects &lt;name&gt;" clause is redundant, since the user is already looking at
     * that sole defendant's own results, and is removed entirely rather than having their own
     * name substituted back in. This is an aggregate token resolved at the service level after all
     * per-context results are collected, distinct from the per-context {@code ${defendantName}}
     * token handled by {@link #resolve}.
     *
     * @param hearingDefendantCount the total number of defendants on the current hearing (not the
     *                              size of {@code names}, which is only the subset this particular
     *                              message affects)
     */
    public String resolveDefendantNames(final String template, final List<String> names,
                                        final int hearingDefendantCount) {
        return hearingDefendantCount <= SOLE_DEFENDANT_IN_HEARING
                ? stripAffectedDefendantsClause(template)
                : template.replace(DEFENDANT_NAMES_TOKEN, formatDefendantNames(names));
    }

    /**
     * Removes the sentence carrying the {@code ${defendantNames}} token in full -- e.g. "... This
     * affects ${defendantNames}." becomes "..." -- rather than leaving an awkward "This affects ."
     * fragment behind. Assumes the token's sentence ends at the next {@code '.'} after it (true of
     * every current rule template); falls back to trimming to the end of the template if no
     * closing '.' is found. Returns the template unchanged if the token is not present.
     */
    private static String stripAffectedDefendantsClause(final String template) {
        final int tokenIndex = template.indexOf(DEFENDANT_NAMES_TOKEN);
        final String result;
        if (tokenIndex < 0) {
            result = template;
        } else {
            final int sentenceStart = template.lastIndexOf('.', tokenIndex) + 1;
            final int periodAfterToken = template.indexOf('.', tokenIndex);
            final String before = template.substring(0, sentenceStart);
            final String after = periodAfterToken >= 0 ? template.substring(periodAfterToken + 1) : "";
            result = (before.stripTrailing() + after).stripLeading();
        }
        return result;
    }

    private static String formatDefendantNames(final List<String> names) {
        final String result;
        if (names.isEmpty()) {
            result = "";
        } else if (names.size() == SINGLE_ELEMENT) {
            result = names.get(0);
        } else {
            result = String.join(", ", names.subList(0, names.size() - 1))
                    + " and " + names.get(names.size() - 1);
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
