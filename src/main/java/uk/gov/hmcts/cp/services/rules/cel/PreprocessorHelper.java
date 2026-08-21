package uk.gov.hmcts.cp.services.rules.cel;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Stateless helpers shared by the {@link ValidationPreprocessor} implementations: short-code
 * normalisation and matching, result-line grouping, defendant-name assembly, and prompt-date
 * parsing. Mirrors the static-utility shape of {@link uk.gov.hmcts.cp.services.rules.SeverityCeiling}.
 */
@Slf4j
public final class PreprocessorHelper {

    private PreprocessorHelper() {
    }

    /** Upper-cases a short-code list into an immutable set; null-safe (null becomes empty). */
    public static Set<String> upperSet(final List<String> values) {
        final List<String> source = values == null ? List.of() : values;
        return source.stream()
            .map(s -> s.toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    /** Upper-cases a single value, or returns {@code null} if the input is {@code null}. */
    public static String upperOrNull(final String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    /** True if the line's short code (case-insensitive) is in the given upper-cased set. */
    public static boolean hasUpperCode(final ResultLineDto line, final Set<String> upperCodes) {
        final String upper = upperOrNull(line.getShortCode());
        return upper != null && upperCodes.contains(upper);
    }

    /** True if any line's short code (case-insensitive) is in the given upper-cased set. */
    public static boolean anyShortCodeIn(final List<ResultLineDto> lines,
                                         final Set<String> upperCodes) {
        return lines.stream().anyMatch(rl -> hasUpperCode(rl, upperCodes));
    }

    /**
     * True if the line carries a prompt whose {@code promptRef} matches exactly. Comparison is
     * case-sensitive, matching the convention used by {@link #parsePromptDate}: {@code promptRef}
     * values are Java constants defined by the caller, not user input, so case normalisation is
     * unnecessary (unlike short codes, which are YAML-configurable and compared case-insensitively).
     */
    public static boolean hasPromptRef(final ResultLineDto line, final String promptRef) {
        return line.getPrompts() != null && line.getPrompts().stream()
            .anyMatch((Prompt prompt) -> promptRef.equals(prompt.getPromptRef()));
    }

    /** True if any line carries a prompt whose {@code promptRef} matches exactly (case-sensitive). */
    public static boolean anyPromptRefIn(final List<ResultLineDto> lines, final String promptRef) {
        return lines.stream().anyMatch(rl -> hasPromptRef(rl, promptRef));
    }

    /** Groups result lines by defendant id, preserving order; skips lines with a null id. */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public static Map<String, List<ResultLineDto>> groupByDefendant(
        final DraftValidationRequest request) {
        final Map<String, List<ResultLineDto>> grouped = new LinkedHashMap<>();
        if (request.getResultLines() != null) {
            for (final ResultLineDto rl : request.getResultLines()) {
                if (rl.getDefendantId() != null) {
                    grouped.computeIfAbsent(rl.getDefendantId(), k -> new ArrayList<>()).add(rl);
                }
            }
        }
        return grouped;
    }

    /**
     * Maps each defendantId to its dedupe key: the {@code masterDefendantId} when present and
     * non-blank, otherwise the defendantId itself. Lets callers fold multiple defendantIds that
     * represent the same person (linked cases) into a single group, mirroring
     * {@link CustodialPreprocessor}'s master-defendant grouping.
     */
    public static Map<String, String> buildDefendantDedupeKeys(final DraftValidationRequest request) {
        final Map<String, String> dedupeKeys = new LinkedHashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto d : request.getDefendants()) {
                final String masterId = d.getMasterDefendantId();
                final String dedupeKey = masterId != null && !masterId.isBlank()
                    ? masterId
                    : d.getDefendantId();
                dedupeKeys.put(d.getDefendantId(), dedupeKey);
            }
        }
        return dedupeKeys;
    }

    /** Builds a defendantId &rarr; full-name map (keyed by {@code id}), preserving order. */
    public static Map<String, String> buildDefendantNames(final DraftValidationRequest request) {
        final Map<String, String> names = new LinkedHashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto d : request.getDefendants()) {
                names.put(d.getDefendantId(), buildFullName(d));
            }
        }
        return names;
    }

    /** Concatenates first and last name, tolerating a null first or last name. */
    public static String buildFullName(final DefendantDto defendant) {
        final String first = defendant.getFirstName();
        final String last = defendant.getLastName();
        final String name;
        if (first != null && last != null) {
            name = first + " " + last;
        } else if (first != null) {
            name = first;
        } else {
            name = last;
        }
        return name;
    }

    /**
     * Parses the {@code promptValue} of the first prompt matching {@code promptRef}. Returns
     * {@code null} (and warns) when the prompt is missing, blank, or not an ISO-8601 date.
     */
    @SuppressWarnings("PMD.OnlyOneReturn")
    public static LocalDate parsePromptDate(final ResultLineDto line,
                                            final String promptRef,
                                            final String offenceId) {
        if (line.getPrompts() == null) {
            return null;
        }
        LocalDate found = null;
        for (final Prompt prompt : line.getPrompts()) {
            if (found == null && promptRef.equals(prompt.getPromptRef())) {
                found = parseDateValue(prompt.getPromptValue(), promptRef,
                    line.getShortCode(), offenceId);
            }
        }
        return found;
    }

    /**
     * True if any line matching {@code codes} carries a {@code promptRef} date strictly later than
     * {@code orderEndDate}.
     */
    public static boolean isRequirementViolated(final List<ResultLineDto> lines,
                                                final Set<String> codes,
                                                final String promptRef,
                                                final LocalDate orderEndDate,
                                                final String offenceId) {
        return lines.stream()
            .filter(rl -> hasUpperCode(rl, codes))
            .anyMatch(rl -> {
                final LocalDate reqDate = parsePromptDate(rl, promptRef, offenceId);
                return reqDate != null && reqDate.isAfter(orderEndDate);
            });
    }

    private static LocalDate parseDateValue(final String value, final String promptRef,
                                            final String shortCode, final String offenceId) {
        LocalDate result = null;
        if (value == null || value.isBlank()) {
            log.warn("Blank promptValue for promptRef={} on shortCode={} offenceId={}",
                promptRef, Encode.forJava(shortCode), Encode.forJava(offenceId));
        } else {
            try {
                result = LocalDate.parse(value.trim());
            } catch (DateTimeParseException e) {
                log.warn("Unparseable date '{}' for promptRef={} on shortCode={} offenceId={}",
                    Encode.forJava(value), promptRef, Encode.forJava(shortCode), Encode.forJava(offenceId));
            }
        }
        return result;
    }

    /** Groups result lines by offence id, preserving order; skips lines with a null id. */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public static Map<String, List<ResultLineDto>> groupResultsByOffence(
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

    /**
     * Result of {@link #groupLinesByDedupedDefendant}: result lines merged under each dedupe key,
     * plus a representative display name per dedupe key.
     */
    public record DedupedLineGroups(Map<String, List<ResultLineDto>> linesByGroup,
                                     Map<String, String> groupNames) {
    }

    /**
     * Groups a request's result lines by defendant, then folds each defendantId into its dedupe
     * key (see {@link #buildDefendantDedupeKeys}) so defendantIds representing the same linked-case
     * person share one group. Shared by preprocessors that emit one context per person rather than
     * per defendantId, e.g. {@link YouthRehabilitationPreprocessor} and
     * {@link CommunityOrderEndDatePreprocessor}.
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public static DedupedLineGroups groupLinesByDedupedDefendant(final DraftValidationRequest request) {
        final Map<String, String> dedupeKeys = buildDefendantDedupeKeys(request);
        final Map<String, String> defendantNames = buildDefendantNames(request);
        final Map<String, List<ResultLineDto>> linesByDefendant = groupByDefendant(request);

        final Map<String, List<ResultLineDto>> linesByGroup = new LinkedHashMap<>();
        final Map<String, String> groupNames = new LinkedHashMap<>();
        for (final Map.Entry<String, List<ResultLineDto>> entry : linesByDefendant.entrySet()) {
            final String defendantId = entry.getKey();
            final String groupKey = dedupeKeys.getOrDefault(defendantId, defendantId);
            linesByGroup.computeIfAbsent(groupKey, k -> new ArrayList<>()).addAll(entry.getValue());
            groupNames.putIfAbsent(groupKey, defendantNames.getOrDefault(defendantId, "Unknown"));
        }
        return new DedupedLineGroups(linesByGroup, groupNames);
    }

    /**
     * Groups an already-filtered line list by offence id, preserving encounter order of both
     * offences and lines; skips lines with a null offence id. Unlike {@link #groupResultsByOffence},
     * which reads the whole request, this groups a caller-supplied subset (e.g. one dedupe group's
     * lines).
     */
    public static Map<String, List<ResultLineDto>> groupByOffence(final List<ResultLineDto> lines) {
        return lines.stream()
            .filter(rl -> rl.getOffenceId() != null)
            .collect(Collectors.groupingBy(ResultLineDto::getOffenceId, LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * The first parseable date (per {@link #parsePromptDate}, at {@code promptRef}) found on a
     * line in {@code offenceLines} carrying one of {@code orderCodes}, or {@code null} if none
     * does or none parses.
     */
    public static LocalDate findOrderEndDate(final List<ResultLineDto> offenceLines,
                                              final Set<String> orderCodes,
                                              final String promptRef,
                                              final String offenceId) {
        return offenceLines.stream()
            .filter(rl -> hasUpperCode(rl, orderCodes))
            .map(rl -> parsePromptDate(rl, promptRef, offenceId))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }
}
