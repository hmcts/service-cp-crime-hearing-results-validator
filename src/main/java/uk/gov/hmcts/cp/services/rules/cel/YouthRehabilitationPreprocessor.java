package uk.gov.hmcts.cp.services.rules.cel;

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
 * Preprocesses Youth Rehabilitation Order result lines into per-defendant
 * {@link YouthRehabilitationContext} summaries for DR-YRO-004. Shared result-line grouping,
 * short-code matching, defendant-name assembly, and prompt-date parsing live in
 * {@link PreprocessorHelper}.
 *
 * <p>AC2 — detects when any curfew requirement (YRC2, YRC1, YRC3) has a date strictly later
 * than the parent YRO end date.
 *
 */
@Component
public class YouthRehabilitationPreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "youth-rehabilitation-order";

    private static final String PROMPT_END_DATE = "endDate";
    private static final String PROMPT_END_DATE_OF_TAG = "endDateOfTagging";

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

            final Map<String, List<ResultLineDto>> linesByOffence = groupByOffence(lines);

            final Set<String> allOffenceIds = new LinkedHashSet<>();

            for (final Map.Entry<String, List<ResultLineDto>> offenceEntry : linesByOffence.entrySet()) {
                final String offenceId = offenceEntry.getKey();
                final List<ResultLineDto> offenceLines = offenceEntry.getValue();

                allOffenceIds.add(offenceId);

                final LocalDate orderEndDate =
                        findOrderEndDate(offenceLines, orderCodes, PROMPT_END_DATE, offenceId);

                if (orderEndDate == null) {
                    continue;
                }

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

            result.put(groupKey, new YouthRehabilitationContext(
                    groupNames.getOrDefault(groupKey, "Unknown"),
                    curViolationIds.size(),
                    cureViolationIds.size(),
                    curaViolationIds.size(),
                    List.copyOf(curViolationIds),
                    List.copyOf(cureViolationIds),
                    List.copyOf(curaViolationIds),
                    List.copyOf(allOffenceIds)));
        }

        return result;
    }
}
