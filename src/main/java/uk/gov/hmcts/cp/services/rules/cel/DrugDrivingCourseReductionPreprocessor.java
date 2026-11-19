package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.anyShortCodeIn;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.buildDefendantDedupeKeys;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.buildDefendantNames;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupResultsByOffence;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Per-offence preprocessor for DR-DISQ-013. Produces one {@link DrugDrivingContext} per offence,
 * with {@code qualifyingCount=1} when a drug driving offence (one of five YAML-configured CJS
 * codes) has any result line carrying DDRCO or DDRCOT (YAML-configured course-reduction codes).
 *
 * <p>All result lines are checked regardless of category — DDRCO/DDRCOT is unlawful on a drug
 * driving offence at any result line category, not just final (category-F) lines.
 */
@Slf4j
@Component
public class DrugDrivingCourseReductionPreprocessor implements ValidationPreprocessor {

    public static final String QUALIFIER = "drug-driving-course-reduction";

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    public Map<String, DrugDrivingContext> preprocess(final DraftValidationRequest request,
                                                       final PreprocessingDefinition config) {
        final Set<String> relevantCodes = upperSet(config.relevantOffenceCodes());
        final Set<String> courseReductionCodes = upperSet(config.courseReductionShortCodes());
        final Map<String, String> defendantNames = buildDefendantNames(request);
        final Map<String, String> dedupeKeys = buildDefendantDedupeKeys(request);

        final Map<String, List<ResultLineDto>> resultsByOffence = groupResultsByOffence(request);
        final Map<String, DrugDrivingContext> result = new LinkedHashMap<>();

        if (request.getOffences() != null) {
            for (final OffenceDto offence : request.getOffences()) {
                result.put(offence.getOffenceId(),
                        buildContext(offence, resultsByOffence, relevantCodes, courseReductionCodes,
                                defendantNames, dedupeKeys));
            }
        }

        return result;
    }

    private DrugDrivingContext buildContext(final OffenceDto offence,
                                             final Map<String, List<ResultLineDto>> resultsByOffence,
                                             final Set<String> relevantCodes,
                                             final Set<String> courseReductionCodes,
                                             final Map<String, String> defendantNames,
                                             final Map<String, String> dedupeKeys) {
        final String offenceId = offence.getOffenceId();
        final boolean relevant = offence.getOffenceCode() != null
                && relevantCodes.contains(offence.getOffenceCode().toUpperCase(Locale.ROOT));
        final List<ResultLineDto> lines = resultsByOffence.getOrDefault(offenceId, List.of());
        final boolean hasCourseReduction = anyShortCodeIn(lines, courseReductionCodes);
        final boolean qualifying = relevant && hasCourseReduction;
        final String defendantId = offence.getDefendantId();
        final String defendantKey = defendantId != null
                ? dedupeKeys.getOrDefault(defendantId, defendantId)
                : null;
        final String defendantName = defendantId != null
                ? defendantNames.getOrDefault(defendantId, "Unknown")
                : "Unknown";

        return new DrugDrivingContext(
                offenceId,
                defendantKey,
                qualifying ? 1L : 0L,
                qualifying ? List.of(offenceId) : List.of(),
                defendantName);
    }
}
