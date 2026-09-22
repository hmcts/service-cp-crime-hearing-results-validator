package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupResultsByOffence;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Per-offence preprocessor for DR-RESTRAO-010. For each offence with at least one RESTRAO result
 * line, inspects the {@value #PROMPT_PROTECTED_PERSON_NAME} prompt value for indicators that more
 * than one protected person's name has been entered (separator characters or whole-word "and"),
 * and exposes {@code multiplePersonsCount} (0 or 1) to the CEL expression.
 */
@Component
public class RestrainingOrderMultiplePersonsPreprocessor implements ValidationPreprocessor {

    public static final String QUALIFIER = "restrao-multiple-protected-persons";

    private static final String PROMPT_PROTECTED_PERSON_NAME = "protectedPersonsName";

    private static final Pattern PATTERN_AND = Pattern.compile("(?i)\\w+\\s+and\\s+\\w+");

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    public Map<String, RestrainingOrderContext> preprocess(final DraftValidationRequest request,
                                                            final PreprocessingDefinition config) {
        final Set<String> restraoCodes = upperSet(config.filterShortCodes());
        final Map<String, List<ResultLineDto>> resultsByOffence = groupResultsByOffence(request);
        final Map<String, RestrainingOrderContext> result = new LinkedHashMap<>();

        for (final Map.Entry<String, List<ResultLineDto>> entry : resultsByOffence.entrySet()) {
            final String offenceId = entry.getKey();
            final List<ResultLineDto> lines = entry.getValue();
            final List<ResultLineDto> restraoLines = lines.stream()
                    .filter(l -> restraoCodes.contains(l.getShortCode().toUpperCase(Locale.ROOT)))
                    .toList();

            if (!restraoLines.isEmpty()) {
                result.put(offenceId, buildContext(offenceId, restraoLines));
            }
        }

        return result;
    }

    private RestrainingOrderContext buildContext(final String offenceId,
                                                  final List<ResultLineDto> restraoLines) {
        final boolean breach = restraoLines.stream().anyMatch(this::isMultiplePersons);
        final long count = breach ? 1L : 0L;
        final List<String> breachingList = breach ? List.of(offenceId) : List.of();
        return new RestrainingOrderContext(offenceId, count, breachingList, List.of(offenceId));
    }

    private boolean isMultiplePersons(final ResultLineDto line) {
        final String name = findPromptValue(line, PROMPT_PROTECTED_PERSON_NAME);
        return name != null && (name.contains("&")
                || name.contains(",")
                || name.contains("/")
                || PATTERN_AND.matcher(name).find());
    }

    private String findPromptValue(final ResultLineDto line, final String promptRef) {
        return line.getPrompts() == null ? null : line.getPrompts().stream()
                .filter(p -> promptRef.equals(p.getPromptRef()))
                .map(Prompt::getPromptValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }
}