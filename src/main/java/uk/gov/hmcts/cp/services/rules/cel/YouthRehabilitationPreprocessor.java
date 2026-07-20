package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.buildDefendantDedupeKeys;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.buildDefendantNames;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupByDefendant;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.hasUpperCode;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.isRequirementViolated;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.parsePromptDate;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.parsePromptPeriod;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Preprocesses Youth Rehabilitation Order result lines into per-defendant
 * {@link YouthRehabilitationContext} summaries for DR-YRO-001. Shared result-line grouping,
 * short-code matching, defendant-name assembly, and prompt-date/period parsing live in
 * {@link PreprocessorHelper}.
 *
 * <p>AC2 — detects when any curfew requirement (YRC2, YRC1, YRC3) has a date strictly later
 * than the parent YRO end date.
 *
 * <p>DD-42850 — detects when a YRC2/YRC1 requirement's own recorded end date does not equal its
 * calculated duration (Start date + period - 1 day). This is independent of the AC2 check above
 * and does not require a parseable YRO order end date.
 */
@Slf4j
@Component
public class YouthRehabilitationPreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "youth-rehabilitation-order";

    private static final int SINGLE_DAY = 1;

    /** Display format for calculated end dates surfaced in validation messages (DD-42850). */
    private static final DateTimeFormatter CALCULATED_END_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String PROMPT_END_DATE = "endDate";
    private static final String PROMPT_END_DATE_OF_TAG = "endDateOfTagging";

    // DD-42850 duration-mismatch prompt ref keys — unverified assumptions (research.md Decision 2)
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

        final Set<String> orderCodes = upperSet(config.getYroOrderShortCodes());
        final Set<String> curCodes = upperSet(config.getCurfewShortCodes());
        final Set<String> cureCodes = upperSet(config.getCurfewTagShortCodes());
        final Set<String> curaCodes = upperSet(config.getFurtherCurfewShortCodes());

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

            // DD-42850 — duration-mismatch accumulators (independent of the AC2 checks above)
            final List<String> curDurationMismatchIds = new ArrayList<>();
            final List<String> cureDurationMismatchIds = new ArrayList<>();
            final Map<String, String> curCalculatedEndDates = new LinkedHashMap<>();
            final Map<String, String> cureCalculatedEndDates = new LinkedHashMap<>();

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

                final LocalDate orderEndDate = offenceLines.stream()
                        .filter(rl -> hasUpperCode(rl, orderCodes))
                        .map(rl -> parsePromptDate(rl, PROMPT_END_DATE, offenceId))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

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

                // DD-42850 — duration-mismatch checks do NOT depend on a parseable order end date
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

    /**
     * DD-42850 — checks whether any result line matching {@code codes} has a recorded end date
     * (identified by {@code endDatePromptRef}) that does not equal {@code startDatePromptRef}
     * (from the same line) plus {@code periodPromptRef} (from the same line) minus one day. If
     * so, adds {@code offenceId} to {@code mismatchIds} and records the correctly calculated end
     * date in {@code calculatedEndDates}. Silently skips (no violation) when the start date,
     * period, or end date is missing or unparseable.
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
            // entries if an offence ever has more than one matching requirement line.
            if (mismatchIds.contains(offenceId)) {
                break;
            }
            if (!hasUpperCode(line, codes)) {
                continue;
            }
            final LocalDate startDate = parsePromptDate(line, startDatePromptRef, offenceId);
            if (startDate == null) {
                continue;
            }
            final PreprocessorHelper.ParsedPeriod period =
                    parsePromptPeriod(line, periodPromptRef, offenceId);
            if (period == null) {
                continue;
            }
            final LocalDate endDate = parsePromptDate(line, endDatePromptRef, offenceId);
            if (endDate == null) {
                continue;
            }
            final LocalDate expectedEndDate;
            try {
                expectedEndDate = startDate.plus(period.amount(), period.unit()).minusDays(SINGLE_DAY);
            } catch (DateTimeException e) {
                log.warn("Period out of range for promptRef={} on shortCode={} offenceId={}",
                        periodPromptRef, line.getShortCode(), offenceId);
                continue;
            }
            if (!endDate.isEqual(expectedEndDate)) {
                mismatchIds.add(offenceId);
                calculatedEndDates.put(offenceId, expectedEndDate.format(CALCULATED_END_DATE_FORMAT));
            }
        }
    }
}
