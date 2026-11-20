package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.anyShortCodeIn;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.buildDefendantNames;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupResultsByOffence;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperOrNull;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Produces one {@link ElectronicMonitoringContext} per defendant who has at least one
 * unresolved EM-flagged offence, for DR-EMON-017.
 *
 * <p>An EM-flagged offence (hasActiveElectronicMonitoring == true) is resolved when it:
 * <ul>
 *   <li>has no result lines (blank),</li>
 *   <li>has an EMONE ending result,</li>
 *   <li>has a prompt with promptRef in the configured continuingEmPromptRefs set and
 *       promptValue "Yes" (case-insensitive),</li>
 *   <li>has an exempt main result — no category-F line and every category-I line has
 *       a shortCode in the configured exemptMainResultShortCodes set, with ≥1 such line, or</li>
 *   <li>has only ancillary results (no category-F or category-I lines).</li>
 * </ul>
 */
@Component
@SuppressWarnings("PMD.OnlyOneReturn") // isResolved() uses early-return guards that are clearer than a single-return form
public class ElectronicMonitoringPreprocessor implements ValidationPreprocessor {

    public static final String QUALIFIER = "electronic-monitoring-status";

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // per-defendant context and unresolved-id list must be new instances per iteration
    public Map<String, ElectronicMonitoringContext> preprocess(
            final DraftValidationRequest request,
            final PreprocessingDefinition config) {

        final Map<String, String> emOffenceToDefendant = collectEmFlaggedOffences(request);
        if (emOffenceToDefendant.isEmpty()) {
            return Map.of();
        }

        final Map<String, List<String>> emOffencesByDefendant = groupEmOffencesByDefendant(emOffenceToDefendant);
        final Map<String, List<ResultLineDto>> resultsByOffence = groupResultsByOffence(request);
        final Map<String, String> defendantNames = buildDefendantNames(request);
        final Set<String> endingCodes = upperSet(config.endingEmShortCodes());

        final Map<String, ElectronicMonitoringContext> contexts = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : emOffencesByDefendant.entrySet()) {
            final String defendantId = entry.getKey();
            final List<String> unresolvedIds = new ArrayList<>();
            for (final String offenceId : entry.getValue()) {
                final List<ResultLineDto> lines =
                        resultsByOffence.getOrDefault(offenceId, List.of());
                if (!isResolved(lines, config, endingCodes)) {
                    unresolvedIds.add(offenceId);
                }
            }
            if (!unresolvedIds.isEmpty()) {
                contexts.put(defendantId, new ElectronicMonitoringContext(
                        defendantId,
                        defendantNames.get(defendantId),
                        unresolvedIds.size(),
                        List.copyOf(unresolvedIds),
                        List.copyOf(entry.getValue())));
            }
        }
        return contexts;
    }

    private static Map<String, String> collectEmFlaggedOffences(final DraftValidationRequest request) {
        final Map<String, String> emOffenceToDefendant = new LinkedHashMap<>();
        if (request.getOffences() != null) {
            for (final OffenceDto offence : request.getOffences()) {
                if (Boolean.TRUE.equals(offence.getHasActiveElectronicMonitoring())
                        && offence.getOffenceId() != null
                        && offence.getDefendantId() != null) {
                    emOffenceToDefendant.put(offence.getOffenceId(), offence.getDefendantId());
                }
            }
        }
        return emOffenceToDefendant;
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // per-defendant ArrayList must be created inside the loop; no way to pre-allocate
    private static Map<String, List<String>> groupEmOffencesByDefendant(
            final Map<String, String> emOffenceToDefendant) {
        final Map<String, List<String>> byDefendant = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : emOffenceToDefendant.entrySet()) {
            byDefendant.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }
        return byDefendant;
    }

    private static boolean isResolved(final List<ResultLineDto> lines,
                                       final PreprocessingDefinition config,
                                       final Set<String> endingCodes) {
        if (lines.isEmpty()) {
            return true;
        }
        if (anyShortCodeIn(lines, endingCodes)) {
            return true;
        }
        if (hasContinuingEmResult(lines, config.continuingEmPromptRefs())) {
            return true;
        }
        if (isExemptMainResult(lines, config.exemptMainResultShortCodes())) {
            return true;
        }
        return isAncillaryOnly(lines);
    }

    private static boolean hasContinuingEmResult(final List<ResultLineDto> lines,
                                                  final List<String> promptRefs) {
        if (promptRefs == null || promptRefs.isEmpty()) {
            return false;
        }
        final Set<String> upperRefs = upperSet(promptRefs);
        for (final ResultLineDto line : lines) {
            if (line.getPrompts() != null) {
                for (final Prompt prompt : line.getPrompts()) {
                    final String upperRef = upperOrNull(prompt.getPromptRef());
                    if (upperRef != null && upperRefs.contains(upperRef)
                            && "Yes".equalsIgnoreCase(prompt.getPromptValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isExemptMainResult(final List<ResultLineDto> lines,
                                               final List<String> exemptCodes) {
        if (exemptCodes == null || exemptCodes.isEmpty()) {
            return false;
        }
        final Set<String> exemptUpper = upperSet(exemptCodes);
        boolean hasCategoryI = false;
        boolean allCategoryIAreExempt = true;
        for (final ResultLineDto line : lines) {
            if (line.getCategory() == null
                    || line.getCategory() == ResultLineDto.CategoryEnum.F) {
                return false;
            }
            if (line.getCategory() == ResultLineDto.CategoryEnum.I) {
                hasCategoryI = true;
                final String upper = upperOrNull(line.getShortCode());
                if (upper == null || !exemptUpper.contains(upper)) {
                    allCategoryIAreExempt = false;
                }
            }
        }
        return hasCategoryI && allCategoryIAreExempt;
    }

    private static boolean isAncillaryOnly(final List<ResultLineDto> lines) {
        for (final ResultLineDto line : lines) {
            if (line.getCategory() == null
                    || line.getCategory() == ResultLineDto.CategoryEnum.F
                    || line.getCategory() == ResultLineDto.CategoryEnum.I) {
                return false;
            }
        }
        return true;
    }
}
