package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.buildDefendantDedupeKeys;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.buildDefendantNames;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupByDefendant;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.hasUpperCode;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.isRequirementViolated;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.parsePromptDate;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Preprocesses community order result lines into per-defendant
 * {@link CommunityOrderContext} summaries for DR-COEW-005. Shared result-line grouping,
 * short-code matching, defendant-name assembly, and prompt-date parsing live in
 * {@link PreprocessorHelper}.
 *
 * <p>AC2 — detects when any requirement (CUR, CURE, CURA, AAR) has a date strictly later
 * than the parent community order end date.
 *
 */
@Component
public class CommunityOrderEndDatePreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "community-order-end-date";

    private static final String PROMPT_END_DATE = "endDate";
    private static final String PROMPT_END_DATE_OF_TAG = "endDateOfTagging";
    private static final String PROMPT_UNTIL = "until";

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

                if (orderEndDate == null) {
                    continue;
                }

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
                    List.copyOf(allOffenceIds)));
        }

        return result;
    }
}
