package uk.gov.hmcts.cp.services.rules.cel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Per-offence preprocessor for the DR-DISQ-001 extended-test disqualification rule. Produces one
 * {@link DisqualificationContext} per offence in the request, with counts that drive the YAML CEL
 * condition {@code qualifyingCount > 0}.
 *
 * <p>An offence qualifies (warning fires) when all of the following hold:
 * <ol>
 *   <li>The offence's Home Office code is in the YAML's {@code relevantOffenceCodes} list.</li>
 *   <li>At least one {@code category='F'} result line on the offence has a short code that is
 *       <em>not</em> in the excluded set (FR-015: lines with null or non-F category are
 *       treated as non-final).</li>
 *   <li>No result line on the offence (regardless of category) has a short code in the
 *       extended-test set ({@code DDOTE} / {@code DDOTEL}).</li>
 * </ol>
 *
 * <p>All short-code and offence-code comparisons are case-insensitive (normalised to upper case
 * once at the top of the algorithm, matching {@link CustodialPreprocessor}'s style).
 */
@Slf4j
@Component
public class DisqualificationExtendedTestPreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "disqualification-extended-test";

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    public Map<String, DisqualificationContext> preprocess(final DraftValidationRequest request,
                                                            final PreprocessingDefinition config) {
        final Set<String> relevantCodes = upperSet(config.getRelevantOffenceCodes());
        final Set<String> excludedShortCodes = upperSet(config.getExcludedFinalShortCodes());
        final Set<String> extendedTestShortCodes = upperSet(config.getExtendedTestShortCodes());

        final Map<String, List<ResultLineDto>> resultsByOffence = groupResultsByOffence(request);
        final Map<String, DisqualificationContext> result = new LinkedHashMap<>();

        if (request.getOffences() != null) {
            for (final OffenceDto offence : request.getOffences()) {
                result.put(offence.getId(),
                        buildContext(offence, resultsByOffence, relevantCodes,
                                excludedShortCodes, extendedTestShortCodes));
            }
        }

        return result;
    }

    private DisqualificationContext buildContext(final OffenceDto offence,
                                                  final Map<String, List<ResultLineDto>> resultsByOffence,
                                                  final Set<String> relevantCodes,
                                                  final Set<String> excludedShortCodes,
                                                  final Set<String> extendedTestShortCodes) {
        final String offenceId = offence.getId();
        final boolean relevant = offence.getOffenceCode() != null
                && relevantCodes.contains(offence.getOffenceCode().toUpperCase(Locale.ROOT));

        final List<ResultLineDto> lines = resultsByOffence.getOrDefault(offenceId, List.of());

        lines.forEach(rl -> {
            final ResultLineDto.CategoryEnum cat = rl.getCategory();
            if (cat != null
                    && cat != ResultLineDto.CategoryEnum.A
                    && cat != ResultLineDto.CategoryEnum.I
                    && cat != ResultLineDto.CategoryEnum.F) {
                log.info("Unrecognised result line category '{}' on offenceId='{}' — treating as non-final",
                        cat.name(), offenceId);
            }
        });

        final List<ResultLineDto> finalLines = lines.stream()
                .filter(rl -> rl.getCategory() == ResultLineDto.CategoryEnum.F)
                .toList();

        final long finalCategoryCount = finalLines.size();
        final long excludedFinalCount = finalLines.stream()
                .filter(rl -> {
                    final String upper = upperOrNull(rl.getShortCode());
                    return upper != null && excludedShortCodes.contains(upper);
                }).count();
        final boolean finalNonExcluded = finalLines.stream().anyMatch(rl -> {
            final String upper = upperOrNull(rl.getShortCode());
            return upper != null && !excludedShortCodes.contains(upper);
        });
        final boolean disqExtTest = anyShortCodeIn(lines, extendedTestShortCodes);

        final boolean qualifying = relevant && finalNonExcluded && !disqExtTest;

        return new DisqualificationContext(
                offenceId,
                qualifying ? 1L : 0L,
                relevant ? 1L : 0L,
                finalCategoryCount,
                excludedFinalCount,
                disqExtTest ? 1L : 0L,
                qualifying ? List.of(offenceId) : List.of(),
                List.of(offenceId));
    }

    private static Set<String> upperSet(final List<String> values) {
        final List<String> source = values == null ? List.of() : values;
        return source.stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static Map<String, List<ResultLineDto>> groupResultsByOffence(
            final DraftValidationRequest request) {
        final Map<String, List<ResultLineDto>> grouped = new LinkedHashMap<>();
        if (request.getResultLines() != null) {
            for (final ResultLineDto rl : request.getResultLines()) {
                if (rl.getOffenceId() != null) {
                    grouped.computeIfAbsent(rl.getOffenceId(), k -> new ArrayList<>()).add(rl);
                }
            }
        }
        return grouped;
    }

    private static boolean anyShortCodeIn(final List<ResultLineDto> lines,
                                           final Set<String> upperCodes) {
        return lines.stream().anyMatch(rl -> {
            final String upper = upperOrNull(rl.getShortCode());
            return upper != null && upperCodes.contains(upper);
        });
    }

    private static String upperOrNull(final String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
