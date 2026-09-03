package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.checkDurationMismatch;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.checkDurationMismatchFromFixedStart;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.findOrderEndDate;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupByOffence;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupLinesByDedupedDefendant;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.hasUpperCode;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.isRequirementViolated;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Preprocesses community order result lines into per-defendant
 * {@link CommunityOrderContext} summaries for DR-COEW-005. Shared result-line grouping,
 * short-code matching, defendant-name assembly, and prompt-date/prompt-period parsing live in
 * {@link PreprocessorHelper}.
 *
 * <p>AC2 — detects when any requirement (CUR, CURE, CURA, AAR) has a date strictly later
 * than the parent community order end date.
 *
 * <p>DUR-CUR/DUR-CURE/DUR-AAR — detects when a CUR/CURE/AAR requirement's own recorded end
 * date does not equal its calculated duration (Start date + period - 1 day, or hearing date +
 * days - 1 day for AAR). This is independent of the AC2 check above and does not require a
 * parseable order end date. The CUR/CURE period prompt ref keys were assumptions at authoring
 * time; the AAR key has since been confirmed against a real payload as
 * {@code numberOfDaysToAbstainFromConsumingAnyAlcohol}, not {@code numberOfDaysToAbstain}.
 */
@Component
public class CommunityOrderEndDatePreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "community-order-end-date";

    private static final String PROMPT_END_DATE = "endDate";
    private static final String PROMPT_END_DATE_OF_TAG = "endDateOfTagging";
    private static final String PROMPT_UNTIL = "until";

    // Duration-mismatch prompt ref keys
    private static final String PROMPT_START_DATE = "startDate";
    private static final String PROMPT_CURFEW_PERIOD = "curfewPeriod";
    private static final String PROMPT_START_DATE_OF_TAGGING = "startDateOfTagging";
    private static final String PROMPT_CURFEW_TAG_PERIOD = "curfewAndElectronicMonitoringPeriod";
    private static final String PROMPT_DAYS_TO_ABSTAIN =
            "numberOfDaysToAbstainFromConsumingAnyAlcohol";

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

        final Set<String> orderCodes = upperSet(config.communityOrderShortCodes());
        final Set<String> curCodes = upperSet(config.curfewShortCodes());
        final Set<String> cureCodes = upperSet(config.curfewTagShortCodes());
        final Set<String> curaCodes = upperSet(config.furtherCurfewShortCodes());
        final Set<String> aarCodes = upperSet(config.alcoholAbstinenceShortCodes());

        final LocalDate hearingDay = request.getHearingDay();
        final PreprocessorHelper.DedupedLineGroups groups = groupLinesByDedupedDefendant(request);
        final Map<String, List<ResultLineDto>> linesByGroup = groups.linesByGroup();
        final Map<String, String> groupNames = groups.groupNames();

        final Map<String, CommunityOrderContext> result = new LinkedHashMap<>();

        for (final Map.Entry<String, List<ResultLineDto>> entry : linesByGroup.entrySet()) {
            final String groupKey = entry.getKey();
            final List<ResultLineDto> lines = entry.getValue();

            final boolean hasOrder = lines.stream().anyMatch(rl -> hasUpperCode(rl, orderCodes));
            if (!hasOrder) {
                continue;
            }

            final List<String> curViolationIds = new ArrayList<>();
            final List<String> cureViolationIds = new ArrayList<>();
            final List<String> curaViolationIds = new ArrayList<>();
            final List<String> aarViolationIds = new ArrayList<>();

            final List<String> curDurationMismatchIds = new ArrayList<>();
            final List<String> cureDurationMismatchIds = new ArrayList<>();
            final List<String> aarDurationMismatchIds = new ArrayList<>();
            final Map<String, String> curCalculatedEndDates = new LinkedHashMap<>();
            final Map<String, String> cureCalculatedEndDates = new LinkedHashMap<>();
            final Map<String, String> aarCalculatedEndDates = new LinkedHashMap<>();

            final Map<String, List<ResultLineDto>> linesByOffence = groupByOffence(lines);

            final Set<String> allOffenceIds = new LinkedHashSet<>();

            for (final Map.Entry<String, List<ResultLineDto>> offenceEntry : linesByOffence.entrySet()) {
                final String offenceId = offenceEntry.getKey();
                final List<ResultLineDto> offenceLines = offenceEntry.getValue();

                allOffenceIds.add(offenceId);

                final LocalDate orderEndDate =
                        findOrderEndDate(offenceLines, orderCodes, PROMPT_END_DATE, offenceId);

                if (orderEndDate != null) {
                    // AC2a — CUR: curfew end date after order end date
                    if (isRequirementViolated(offenceLines, curCodes,
                            PROMPT_END_DATE, orderEndDate, offenceId)) {
                        curViolationIds.add(offenceId);
                    }
                    // AC2b — CURE: end-of-tagging date after order end date
                    if (isRequirementViolated(offenceLines, cureCodes,
                            PROMPT_END_DATE_OF_TAG, orderEndDate, offenceId)) {
                        cureViolationIds.add(offenceId);
                    }
                    // AC2c — CURA: further curfew end date after order end date
                    if (isRequirementViolated(offenceLines, curaCodes,
                            PROMPT_END_DATE, orderEndDate, offenceId)) {
                        curaViolationIds.add(offenceId);
                    }
                    // AC2d — AAR: alcohol abstinence "until" date after order end date
                    if (isRequirementViolated(offenceLines, aarCodes,
                            PROMPT_UNTIL, orderEndDate, offenceId)) {
                        aarViolationIds.add(offenceId);
                    }
                }

                // DUR-CUR/CURE/AAR — duration-mismatch checks do NOT depend on a parseable
                // order end date.
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

            result.put(groupKey, new CommunityOrderContext(
                    groupNames.getOrDefault(groupKey, "Unknown"),
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
}
