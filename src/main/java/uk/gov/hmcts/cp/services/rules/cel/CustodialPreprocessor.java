package uk.gov.hmcts.cp.services.rules.cel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Preprocesses custodial result lines into per-defendant summaries consumed by CEL conditions.
 */
@Component
public class CustodialPreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "custodial-concurrent-consecutive";

    @Override
    public String type() {
        return QUALIFIER;
    }

    /**
     * Groups custodial result lines by defendant (or master defendant) and derives the offence
     * counts and offence-id sets needed by the validation rule conditions.
     *
     * @param request draft validation request being evaluated
     * @param config preprocessing configuration loaded from YAML
     * @return map of defendant grouping key to derived context
     */
    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public Map<String, DefendantContext> preprocess(final DraftValidationRequest request,
                                                     final PreprocessingDefinition config) {
        final Set<String> shortCodes = config.getFilterShortCodes().stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        final Map<String, String> defendantGrouping = buildDefendantGrouping(request);
        final Map<String, String> defendantNames = buildDefendantNames(request);

        final Map<String, List<ResultLineDto>> linesByGroup = new LinkedHashMap<>();
        for (final ResultLineDto rl : request.getResultLines()) {
            final String groupKey = defendantGrouping.getOrDefault(rl.getDefendantId(), rl.getDefendantId());
            linesByGroup.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(rl);
        }

        final Map<String, DefendantContext> result = new LinkedHashMap<>();

        for (final Map.Entry<String, List<ResultLineDto>> groupEntry : linesByGroup.entrySet()) {
            final String groupKey = groupEntry.getKey();
            final List<ResultLineDto> groupLines = groupEntry.getValue();

            final List<ResultLineDto> custodialLines = groupLines.stream()
                    .filter(rl -> rl.getShortCode() != null
                            && shortCodes.contains(rl.getShortCode().toUpperCase(Locale.ROOT)))
                    .toList();

            if (custodialLines.isEmpty()) {
                continue;
            }

            final Map<String, List<ResultLineDto>> byOffence = custodialLines.stream()
                    .collect(Collectors.groupingBy(
                            ResultLineDto::getOffenceId,
                            LinkedHashMap::new,
                            Collectors.toList()));

            if (byOffence.size() <= config.getSkipWhenGroupCount()) {
                continue;
            }

            final List<String> noInfoOffenceIds = new ArrayList<>();
            final List<String> offencesWithInfo = new ArrayList<>();
            final List<String> offencesWithBoth = new ArrayList<>();
            String primaryOffenceId = null;
            boolean primaryClaimed = false;

            for (final Map.Entry<String, List<ResultLineDto>> entry : byOffence.entrySet()) {
                final String offenceId = entry.getKey();
                final List<ResultLineDto> lines = entry.getValue();

                final boolean anyConcurrent = lines.stream()
                        .anyMatch(rl -> Boolean.TRUE.equals(rl.getIsConcurrent()));
                final boolean anyConsecutive = lines.stream()
                        .anyMatch(rl -> rl.getConsecutiveToOffence() != null
                                && !rl.getConsecutiveToOffence().isBlank());

                if (anyConcurrent && anyConsecutive) {
                    offencesWithBoth.add(offenceId);
                } else if (anyConcurrent || anyConsecutive) {
                    offencesWithInfo.add(offenceId);
                } else if (primaryClaimed) {
                    noInfoOffenceIds.add(offenceId);
                } else {
                    primaryClaimed = true;
                    primaryOffenceId = offenceId;
                }
            }

            final List<String> allNoInfoOffenceIds = new ArrayList<>();
            if (primaryOffenceId != null) {
                allNoInfoOffenceIds.add(primaryOffenceId);
            }
            allNoInfoOffenceIds.addAll(noInfoOffenceIds);

            result.put(groupKey, new DefendantContext(
                    defendantNames.getOrDefault(groupKey, "Unknown"),
                    noInfoOffenceIds.size(),
                    offencesWithInfo.size(),
                    offencesWithBoth.size(),
                    primaryOffenceId != null ? 1 : 0,
                    byOffence.size(),
                    noInfoOffenceIds,
                    offencesWithInfo,
                    offencesWithBoth,
                    new ArrayList<>(byOffence.keySet()),
                    allNoInfoOffenceIds));
        }

        return result;
    }

    private Map<String, String> buildDefendantNames(final DraftValidationRequest request) {
        final Map<String, String> names = new HashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto d : request.getDefendants()) {
                final String groupKey = (d.getMasterDefendantId() != null && !d.getMasterDefendantId().isBlank())
                        ? d.getMasterDefendantId()
                        : d.getId();
                names.putIfAbsent(groupKey, buildFullName(d));
            }
        }
        return names;
    }

    private String buildFullName(final DefendantDto defendant) {
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

    private Map<String, String> buildDefendantGrouping(final DraftValidationRequest request) {
        final Map<String, String> grouping = new HashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto d : request.getDefendants()) {
                final String groupKey = (d.getMasterDefendantId() != null && !d.getMasterDefendantId().isBlank())
                        ? d.getMasterDefendantId()
                        : d.getId();
                grouping.put(d.getId(), groupKey);
            }
        }
        return grouping;
    }
}
