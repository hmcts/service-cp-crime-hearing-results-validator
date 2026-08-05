package uk.gov.hmcts.cp.services.rules.cel;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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
                promptRef, shortCode, offenceId);
        } else {
            try {
                result = LocalDate.parse(value.trim());
            } catch (DateTimeParseException e) {
                log.warn("Unparseable date '{}' for promptRef={} on shortCode={} offenceId={}",
                    value, promptRef, shortCode, offenceId);
            }
        }
        return result;
    }
}
