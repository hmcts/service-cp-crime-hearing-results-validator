package uk.gov.hmcts.cp.services.rules.cel;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Preprocesses community order result lines into per-defendant
 * {@link CommunityOrderContext} summaries for the DR-COEW-001 rule.
 *
 * <p>AC2 — detects when any child requirement (CUR, CURE, CURA, AAR) on a community order
 * has a date strictly later than the parent order end date.
 *
 * <p>DD-41655 — detects when a CUR/CURE/AAR requirement's own recorded end date does not equal
 * its calculated duration (Start date + period - 1 day, or hearing date + days - 1 day for AAR).
 * This is independent of the AC2 check above and does not require a parseable order end date.
 *
 * <p>Prompt ref keys are stable API-contract values from
 * {@code api-cp-crime-hearing-results-validator:0.1.6} and are intentionally hardcoded
 * rather than being YAML-configurable (see research.md Decision 3). The DD-41655 prompt ref
 * keys below are unverified assumptions (research.md Decision 10) pending confirmation against
 * the real upstream contract.
 */
@Slf4j
@Component
public class CommunityOrderEndDatePreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "community-order-end-date";

    private static final int SINGLE_DAY = 1;

    /** Display format for calculated end dates surfaced in validation messages (DD-41655). */
    private static final DateTimeFormatter CALCULATED_END_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Matches period prompt values as sent by the real upstream contract, e.g. {@code "90 Days"},
     * {@code "1 Day"}, {@code "1 Months"}, or {@code "1 weeks"} (DD-41655 follow-up — Days, Weeks,
     * and Months are all confirmed against real payloads; any other unit falls back to the
     * WARN-and-skip behaviour below rather than guessing a conversion).
     */
    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("^(\\d+)\\s*(Days?|Weeks?|Months?)$", Pattern.CASE_INSENSITIVE);

    // Prompt ref keys — stable API-contract values (research.md Decision 3)
    private static final String PROMPT_END_DATE = "endDate";
    private static final String PROMPT_END_DATE_OF_TAG = "endDateOfTagging";
    private static final String PROMPT_UNTIL = "until";

    // DD-41655 duration-mismatch prompt ref keys — unverified assumptions (research.md Decision 10)
    private static final String PROMPT_START_DATE = "startDate";
    private static final String PROMPT_CURFEW_PERIOD = "curfewPeriod";
    private static final String PROMPT_START_DATE_OF_TAGGING = "startDateOfTagging";
    private static final String PROMPT_CURFEW_TAG_PERIOD = "curfewAndElectronicMonitoringPeriod";
    private static final String PROMPT_DAYS_TO_ABSTAIN = "numberOfDaysToAbstain";

    @Override
    public String type() {
        return QUALIFIER;
    }

    /**
     * Groups result lines by defendant and produces one {@link CommunityOrderContext} per
     * defendant that has at least one community order result line.
     *
     * @param request draft validation request being evaluated
     * @param config preprocessing configuration loaded from YAML
     * @return map of defendantId to derived context
     */
    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public Map<String, CommunityOrderContext> preprocess(final DraftValidationRequest request,
                                                          final PreprocessingDefinition config) {
        final Set<String> orderCodes = upperSet(config.getCommunityOrderShortCodes());
        final Set<String> curCodes = upperSet(config.getCurfewShortCodes());
        final Set<String> cureCodes = upperSet(config.getCurfewTagShortCodes());
        final Set<String> curaCodes = upperSet(config.getFurtherCurfewShortCodes());
        final Set<String> aarCodes = upperSet(config.getAlcoholAbstinenceShortCodes());

        final Map<String, String> defendantNames = buildDefendantNames(request);
        final LocalDate hearingDay = request.getHearingDay();

        // Group all result lines by defendantId
        final Map<String, List<ResultLineDto>> linesByDefendant = groupByDefendant(request);

        final Map<String, CommunityOrderContext> result = new LinkedHashMap<>();

        for (final Map.Entry<String, List<ResultLineDto>> entry : linesByDefendant.entrySet()) {
            final String defendantId = entry.getKey();
            final List<ResultLineDto> lines = entry.getValue();

            // Skip defendants with no community order lines
            final boolean hasCommunityOrder = lines.stream()
                    .anyMatch(rl -> hasUpperCode(rl, orderCodes));
            if (!hasCommunityOrder) {
                continue;
            }

            // Accumulate violations across all offences for this defendant
            final List<String> curViolationIds = new ArrayList<>();
            final List<String> cureViolationIds = new ArrayList<>();
            final List<String> curaViolationIds = new ArrayList<>();
            final List<String> aarViolationIds = new ArrayList<>();

            // DD-41655 — duration-mismatch accumulators (independent of the AC2 checks above)
            final List<String> curDurationMismatchIds = new ArrayList<>();
            final List<String> cureDurationMismatchIds = new ArrayList<>();
            final List<String> aarDurationMismatchIds = new ArrayList<>();
            final Map<String, String> curCalculatedEndDates = new LinkedHashMap<>();
            final Map<String, String> cureCalculatedEndDates = new LinkedHashMap<>();
            final Map<String, String> aarCalculatedEndDates = new LinkedHashMap<>();

            // Group lines by offenceId within this defendant
            final Map<String, List<ResultLineDto>> linesByOffence = lines.stream()
                    .collect(Collectors.groupingBy(
                            ResultLineDto::getOffenceId,
                            LinkedHashMap::new,
                            Collectors.toList()));

            final Set<String> allOffenceIds = new LinkedHashSet<>();

            for (final Map.Entry<String, List<ResultLineDto>> offenceEntry : linesByOffence.entrySet()) {
                final String offenceId = offenceEntry.getKey();
                final List<ResultLineDto> offenceLines = offenceEntry.getValue();

                allOffenceIds.add(offenceId);

                // Find the community order line for this offence and parse its end date
                final LocalDate orderEndDate = offenceLines.stream()
                        .filter(rl -> hasUpperCode(rl, orderCodes))
                        .map(rl -> parsePromptDate(rl, PROMPT_END_DATE, offenceId))
                        .filter(d -> d != null)
                        .findFirst()
                        .orElse(null);

                if (orderEndDate != null) {
                    // AC2a — CUR: compare "endDate" prompt
                    checkRequirementViolation(offenceLines, curCodes, PROMPT_END_DATE,
                            orderEndDate, offenceId, curViolationIds);

                    // AC2b — CURE: compare "endDateOfTagging" prompt
                    checkRequirementViolation(offenceLines, cureCodes, PROMPT_END_DATE_OF_TAG,
                            orderEndDate, offenceId, cureViolationIds);

                    // AC2c — CURA: compare "endDate" prompt
                    checkRequirementViolation(offenceLines, curaCodes, PROMPT_END_DATE,
                            orderEndDate, offenceId, curaViolationIds);

                    // AC2d — AAR: compare "until" prompt
                    checkRequirementViolation(offenceLines, aarCodes, PROMPT_UNTIL,
                            orderEndDate, offenceId, aarViolationIds);
                }

                // DD-41655 — duration-mismatch checks do NOT depend on a parseable order end date
                checkDurationMismatch(offenceLines, curCodes, PROMPT_START_DATE, PROMPT_CURFEW_PERIOD,
                        PROMPT_END_DATE, offenceId, curDurationMismatchIds, curCalculatedEndDates);

                checkDurationMismatch(offenceLines, cureCodes, PROMPT_START_DATE_OF_TAGGING,
                        PROMPT_CURFEW_TAG_PERIOD, PROMPT_END_DATE_OF_TAG, offenceId,
                        cureDurationMismatchIds, cureCalculatedEndDates);

                if (hearingDay != null) {
                    checkDurationMismatchFromFixedStart(offenceLines, aarCodes, hearingDay,
                            PROMPT_DAYS_TO_ABSTAIN, PROMPT_UNTIL, offenceId,
                            aarDurationMismatchIds, aarCalculatedEndDates);
                }
            }

            result.put(defendantId, new CommunityOrderContext(
                    defendantNames.getOrDefault(defendantId, "Unknown"),
                    curViolationIds.size(),
                    cureViolationIds.size(),
                    curaViolationIds.size(),
                    aarViolationIds.size(),
                    List.copyOf(curViolationIds),
                    List.copyOf(cureViolationIds),
                    List.copyOf(curaViolationIds),
                    List.copyOf(aarViolationIds),
                    List.copyOf(allOffenceIds),
                    curDurationMismatchIds.size(),
                    cureDurationMismatchIds.size(),
                    aarDurationMismatchIds.size(),
                    List.copyOf(curDurationMismatchIds),
                    List.copyOf(cureDurationMismatchIds),
                    List.copyOf(aarDurationMismatchIds),
                    Map.copyOf(curCalculatedEndDates),
                    Map.copyOf(cureCalculatedEndDates),
                    Map.copyOf(aarCalculatedEndDates)));
        }

        return result;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Checks whether any result line in {@code lines} matching {@code codes} has a prompt
     * value (identified by {@code promptRef}) strictly after {@code orderEndDate}. If so,
     * adds {@code offenceId} to {@code violationIds}.
     */
    private void checkRequirementViolation(final List<ResultLineDto> lines,
                                            final Set<String> codes,
                                            final String promptRef,
                                            final LocalDate orderEndDate,
                                            final String offenceId,
                                            final List<String> violationIds) {
        final boolean violated = lines.stream()
                .filter(rl -> hasUpperCode(rl, codes))
                .anyMatch(rl -> {
                    final LocalDate reqDate = parsePromptDate(rl, promptRef, offenceId);
                    return reqDate != null && reqDate.isAfter(orderEndDate);
                });
        if (violated) {
            violationIds.add(offenceId);
        }
    }

    /**
     * DD-41655 — checks whether any result line in {@code lines} matching {@code codes} has a
     * recorded end date (identified by {@code endDatePromptRef}) that does not equal
     * {@code startDate} (from {@code startDatePromptRef} on the same line) plus
     * {@code period} (from {@code periodPromptRef} on the same line) minus one day. If so, adds
     * {@code offenceId} to {@code mismatchIds} and records the correctly calculated end date in
     * {@code calculatedEndDates}. Silently skips (no violation) when the start date, period, or
     * end date is missing or unparseable.
     */
    private void checkDurationMismatch(final List<ResultLineDto> lines,
                                        final Set<String> codes,
                                        final String startDatePromptRef,
                                        final String periodPromptRef,
                                        final String endDatePromptRef,
                                        final String offenceId,
                                        final List<String> mismatchIds,
                                        final Map<String, String> calculatedEndDates) {
        for (final ResultLineDto line : lines) {
            // An offence contributes at most one mismatch per condition, mirroring
            // checkRequirementViolation's anyMatch semantics — avoids duplicate
            // AffectedOffence entries if an offence ever has >1 matching requirement line.
            if (mismatchIds.contains(offenceId)) {
                break;
            }
            if (!hasUpperCode(line, codes)) {
                continue;
            }
            final LocalDate startDate = parsePromptDate(line, startDatePromptRef, offenceId);
            recordDurationMismatchIfAny(line, startDate, periodPromptRef, endDatePromptRef,
                    offenceId, mismatchIds, calculatedEndDates);
        }
    }

    /**
     * DD-41655 (AAR) — as {@link #checkDurationMismatch}, but the start date is a fixed value
     * (the hearing date) rather than a per-line prompt.
     */
    private void checkDurationMismatchFromFixedStart(final List<ResultLineDto> lines,
                                                       final Set<String> codes,
                                                       final LocalDate startDate,
                                                       final String periodPromptRef,
                                                       final String endDatePromptRef,
                                                       final String offenceId,
                                                       final List<String> mismatchIds,
                                                       final Map<String, String> calculatedEndDates) {
        for (final ResultLineDto line : lines) {
            if (mismatchIds.contains(offenceId)) {
                break;
            }
            if (!hasUpperCode(line, codes)) {
                continue;
            }
            recordDurationMismatchIfAny(line, startDate, periodPromptRef, endDatePromptRef,
                    offenceId, mismatchIds, calculatedEndDates);
        }
    }

    @SuppressWarnings("PMD.OnlyOneReturn")
    private void recordDurationMismatchIfAny(final ResultLineDto line,
                                              final LocalDate startDate,
                                              final String periodPromptRef,
                                              final String endDatePromptRef,
                                              final String offenceId,
                                              final List<String> mismatchIds,
                                              final Map<String, String> calculatedEndDates) {
        if (startDate == null) {
            return;
        }
        final ParsedPeriod period = parsePromptPeriod(line, periodPromptRef, offenceId);
        if (period == null) {
            return;
        }
        final LocalDate endDate = parsePromptDate(line, endDatePromptRef, offenceId);
        if (endDate == null) {
            return;
        }
        final LocalDate expectedEndDate;
        try {
            expectedEndDate = startDate.plus(period.amount(), period.unit()).minusDays(SINGLE_DAY);
        } catch (DateTimeException e) {
            log.warn("Period out of range for promptRef={} on shortCode={} offenceId={}",
                    periodPromptRef, line.getShortCode(), offenceId);
            return;
        }
        if (!endDate.isEqual(expectedEndDate)) {
            mismatchIds.add(offenceId);
            calculatedEndDates.put(offenceId, expectedEndDate.format(CALCULATED_END_DATE_FORMAT));
        }
    }

    /**
     * A parsed period prompt value: a count paired with the calendar unit it's expressed in
     * (DD-41655 follow-up — periods are recorded as e.g. "90 Days" or "1 Months", and month
     * arithmetic must use calendar-aware {@link LocalDate#plus} rather than a fixed day-count
     * conversion, since month lengths vary).
     */
    private record ParsedPeriod(long amount, ChronoUnit unit) {
    }

    /**
     * Parses the {@code promptValue} for the given {@code promptRef} as a period. Returns
     * {@code null} if the prompt is missing, blank, or unparseable, and logs a warning.
     */
    @SuppressWarnings("PMD.OnlyOneReturn")
    private ParsedPeriod parsePromptPeriod(final ResultLineDto line,
                                            final String promptRef,
                                            final String offenceId) {
        if (line.getPrompts() == null) {
            return null;
        }
        ParsedPeriod found = null;
        for (final Prompt prompt : line.getPrompts()) {
            if (found == null && promptRef.equals(prompt.getPromptRef())) {
                found = parsePeriodValue(prompt.getPromptValue(), promptRef,
                        line.getShortCode(), offenceId);
            }
        }
        return found;
    }

    private ParsedPeriod parsePeriodValue(final String value, final String promptRef,
                                           final String shortCode, final String offenceId) {
        ParsedPeriod result = null;
        if (value == null || value.isBlank()) {
            log.warn("Blank promptValue for promptRef={} on shortCode={} offenceId={}",
                    promptRef, shortCode, offenceId);
        } else {
            final String trimmed = value.trim();
            final Matcher periodMatcher = PERIOD_PATTERN.matcher(trimmed);
            final String digits = periodMatcher.matches() ? periodMatcher.group(1) : trimmed;
            final ChronoUnit unit = periodMatcher.matches()
                    ? unitFor(periodMatcher.group(2))
                    : ChronoUnit.DAYS;
            try {
                result = new ParsedPeriod(Long.parseLong(digits), unit);
            } catch (NumberFormatException e) {
                log.warn("Unparseable integer '{}' for promptRef={} on shortCode={} offenceId={}",
                        value, promptRef, shortCode, offenceId);
            }
        }
        return result;
    }

    private static ChronoUnit unitFor(final String unitToken) {
        final String upper = unitToken.toUpperCase(Locale.ROOT);
        final ChronoUnit unit;
        if (upper.startsWith("MONTH")) {
            unit = ChronoUnit.MONTHS;
        } else if (upper.startsWith("WEEK")) {
            unit = ChronoUnit.WEEKS;
        } else {
            unit = ChronoUnit.DAYS;
        }
        return unit;
    }

    /**
     * Parses the {@code promptValue} for the given {@code promptRef} from the result line's
     * prompts list. Returns {@code null} if the prompt is missing, blank, or unparseable,
     * and logs a warning.
     */
    @SuppressWarnings("PMD.OnlyOneReturn")
    private LocalDate parsePromptDate(final ResultLineDto line,
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

    private LocalDate parseDateValue(final String value, final String promptRef,
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

    private static boolean hasUpperCode(final ResultLineDto line, final Set<String> codes) {
        return line.getShortCode() != null
                && codes.contains(line.getShortCode().toUpperCase(Locale.ROOT));
    }

    private static Set<String> upperSet(final List<String> values) {
        final List<String> source = values == null ? List.of() : values;
        return source.stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static Map<String, List<ResultLineDto>> groupByDefendant(
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

    private static Map<String, String> buildDefendantNames(final DraftValidationRequest request) {
        final Map<String, String> names = new LinkedHashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto d : request.getDefendants()) {
                names.put(d.getDefendantId(), buildFullName(d));
            }
        }
        return names;
    }

    private static String buildFullName(final DefendantDto defendant) {
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
}
