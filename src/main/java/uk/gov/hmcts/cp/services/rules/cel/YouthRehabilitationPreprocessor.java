package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.findOrderEndDate;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupByOffence;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupLinesByDedupedDefendant;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.hasUpperCode;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.isRequirementViolated;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.parsePromptDate;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Preprocesses Youth Rehabilitation Order result lines into per-defendant
 * {@link YouthRehabilitationContext} summaries for DR-YRO-004. Shared result-line grouping,
 * short-code matching, defendant-name assembly, and prompt-date parsing live in
 * {@link PreprocessorHelper}.
 *
 * <p>AC2 — detects when any curfew requirement (YRC2, YRC1, YRC3) has a date strictly later
 * than the parent YRO end date.
 *
 * <p>DUR-YRC2/DUR-YRC1 — detects when a YRC2/YRC1 requirement's own recorded end date does not
 * equal its calculated duration (Start date + period - 1 day). This is independent of the AC2
 * check above and does not require a parseable YRO order end date (DD-42850). Mirrors the
 * equivalent Community Order requirement-duration validation in
 * {@link CommunityOrderEndDatePreprocessor}.
 */
@Slf4j
@Component
public class YouthRehabilitationPreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "youth-rehabilitation-order";

    private static final int SINGLE_DAY = 1;

    /** Display format for calculated end dates surfaced in validation messages. */
    private static final DateTimeFormatter CALCULATED_END_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Matches period prompt values as sent by the real upstream contract, e.g. {@code "90 Days"},
     * {@code "1 Day"}, {@code "1 Months"}, or {@code "1 weeks"}; any other unit falls back to the
     * WARN-and-skip behaviour below rather than guessing a conversion. A bare integer (no unit
     * suffix) is also accepted and defaults to days.
     */
    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("^(\\d+)\\s*(Days?|Weeks?|Months?)$", Pattern.CASE_INSENSITIVE);

    private static final String PROMPT_END_DATE = "endDate";
    private static final String PROMPT_END_DATE_OF_TAG = "endDateOfTagging";

    // Duration-mismatch prompt ref keys
    private static final String PROMPT_START_DATE = "startDate";
    private static final String PROMPT_CURFEW_PERIOD = "curfewPeriod";
    private static final String PROMPT_START_DATE_OF_TAGGING = "startDateOfTagging";
    private static final String PROMPT_CURFEW_TAG_PERIOD = "curfewAndElectronicMonitoringPeriod";

    @Override
    public String type() {
        return QUALIFIER;
    }

    /**
     * Groups result lines by defendant and produces one {@link YouthRehabilitationContext} per
     * defendant that has at least one YRO result line.
     *
     * @param request draft validation request being evaluated
     * @param config preprocessing configuration loaded from YAML
     * @return map of defendantId to derived context
     */
    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public Map<String, YouthRehabilitationContext> preprocess(final DraftValidationRequest request,
                                                               final PreprocessingDefinition config) {

        final Set<String> orderCodes = upperSet(config.yroOrderShortCodes());
        final Set<String> curCodes = upperSet(config.curfewShortCodes());
        final Set<String> cureCodes = upperSet(config.curfewTagShortCodes());
        final Set<String> curaCodes = upperSet(config.furtherCurfewShortCodes());

        final PreprocessorHelper.DedupedLineGroups groups = groupLinesByDedupedDefendant(request);
        final Map<String, List<ResultLineDto>> linesByGroup = groups.linesByGroup();
        final Map<String, String> groupNames = groups.groupNames();

        final Map<String, YouthRehabilitationContext> result = new LinkedHashMap<>();

        for (final Map.Entry<String, List<ResultLineDto>> entry : linesByGroup.entrySet()) {
            final String groupKey = entry.getKey();
            final List<ResultLineDto> lines = entry.getValue();

            final boolean hasYro = lines.stream().anyMatch(rl -> hasUpperCode(rl, orderCodes));
            if (!hasYro) {
                continue;
            }

            final List<String> curViolationIds = new ArrayList<>();
            final List<String> cureViolationIds = new ArrayList<>();
            final List<String> curaViolationIds = new ArrayList<>();

            final List<String> curDurationMismatchIds = new ArrayList<>();
            final List<String> cureDurationMismatchIds = new ArrayList<>();
            final Map<String, String> curCalculatedEndDates = new LinkedHashMap<>();
            final Map<String, String> cureCalculatedEndDates = new LinkedHashMap<>();

            final Map<String, List<ResultLineDto>> linesByOffence = groupByOffence(lines);

            final Set<String> allOffenceIds = new LinkedHashSet<>();

            for (final Map.Entry<String, List<ResultLineDto>> offenceEntry : linesByOffence.entrySet()) {
                final String offenceId = offenceEntry.getKey();
                final List<ResultLineDto> offenceLines = offenceEntry.getValue();

                allOffenceIds.add(offenceId);

                final LocalDate orderEndDate =
                        findOrderEndDate(offenceLines, orderCodes, PROMPT_END_DATE, offenceId);

                if (orderEndDate != null) {
                    // AC2a — YRC2: curfew end date after YRO end date
                    if (isRequirementViolated(offenceLines, curCodes,
                            PROMPT_END_DATE, orderEndDate, offenceId)) {
                        curViolationIds.add(offenceId);
                    }

                    // AC2b — YRC1: end-of-tagging date after YRO end date
                    if (isRequirementViolated(offenceLines, cureCodes,
                            PROMPT_END_DATE_OF_TAG, orderEndDate, offenceId)) {
                        cureViolationIds.add(offenceId);
                    }

                    // AC2c — YRC3: further curfew end date after YRO end date
                    if (isRequirementViolated(offenceLines, curaCodes,
                            PROMPT_END_DATE, orderEndDate, offenceId)) {
                        curaViolationIds.add(offenceId);
                    }
                }

                // DUR-YRC2/DUR-YRC1 — duration-mismatch checks do NOT depend on a parseable
                // order end date.
                checkDurationMismatch(offenceLines, curCodes, PROMPT_START_DATE, PROMPT_CURFEW_PERIOD,
                        PROMPT_END_DATE, offenceId, curDurationMismatchIds, curCalculatedEndDates);

                checkDurationMismatch(offenceLines, cureCodes, PROMPT_START_DATE_OF_TAGGING,
                        PROMPT_CURFEW_TAG_PERIOD, PROMPT_END_DATE_OF_TAG, offenceId,
                        cureDurationMismatchIds, cureCalculatedEndDates);
            }

            result.put(groupKey, new YouthRehabilitationContext(
                    groupNames.getOrDefault(groupKey, "Unknown"),
                    curViolationIds.size(),
                    cureViolationIds.size(),
                    curaViolationIds.size(),
                    List.copyOf(curViolationIds),
                    List.copyOf(cureViolationIds),
                    List.copyOf(curaViolationIds),
                    List.copyOf(allOffenceIds),
                    curDurationMismatchIds.size(),
                    cureDurationMismatchIds.size(),
                    List.copyOf(curDurationMismatchIds),
                    List.copyOf(cureDurationMismatchIds),
                    Map.copyOf(curCalculatedEndDates),
                    Map.copyOf(cureCalculatedEndDates)));
        }

        return result;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Checks whether any result line in {@code lines} matching {@code codes} has a recorded end
     * date (identified by {@code endDatePromptRef}) that does not equal {@code startDate} (from
     * {@code startDatePromptRef} on the same line) plus {@code period} (from
     * {@code periodPromptRef} on the same line) minus one day. If so, adds {@code offenceId} to
     * {@code mismatchIds} and records the correctly calculated end date in
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
            // isRequirementViolated's anyMatch semantics — avoids duplicate AffectedOffence
            // entries if an offence ever has >1 matching requirement line.
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
        } catch (DateTimeException | ArithmeticException e) {
            // DateTimeException: calculated date outside LocalDate's supported year range.
            // ArithmeticException: LocalDate.plusDays/plusWeeks overflow (Math.addExact) for an
            // amount near Long.MAX_VALUE — plusMonths happens to surface as DateTimeException
            // instead, but both failure modes mean "period out of range" and must warn-and-skip
            // rather than propagate out of preprocess().
            log.warn("Period out of range for promptRef={} on shortCode={} offenceId={}",
                    periodPromptRef, Encode.forJava(line.getShortCode()), Encode.forJava(offenceId));
            return;
        }
        if (!endDate.isEqual(expectedEndDate)) {
            mismatchIds.add(offenceId);
            calculatedEndDates.put(offenceId, expectedEndDate.format(CALCULATED_END_DATE_FORMAT));
        }
    }

    /**
     * A parsed period prompt value: a count paired with the calendar unit it's expressed in.
     * Periods are recorded as e.g. {@code "90 Days"} or {@code "1 Months"}, and month arithmetic
     * must use calendar-aware {@link LocalDate#plus} rather than a fixed day-count conversion,
     * since month lengths vary.
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
                    promptRef, Encode.forJava(shortCode), Encode.forJava(offenceId));
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
                        Encode.forJava(value), promptRef, Encode.forJava(shortCode), Encode.forJava(offenceId));
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
}
