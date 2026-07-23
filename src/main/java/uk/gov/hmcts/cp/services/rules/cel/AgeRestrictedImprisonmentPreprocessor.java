package uk.gov.hmcts.cp.services.rules.cel;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * Preprocesses imprisonment-type result lines into per-defendant age-eligibility summaries
 * consumed by the {@code DR-AGE-001} rule.
 */
@Component
public class AgeRestrictedImprisonmentPreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "age-restricted-imprisonment";

    private static final int AGE_OF_MAJORITY_FOR_IMPRISONMENT = 21;

    @Override
    public String type() {
        return QUALIFIER;
    }

    /**
     * Groups imprisonment-type result lines by defendant (or master defendant) and derives, for
     * each defendant with at least one qualifying result, whether they are under 21 on the
     * hearing date.
     *
     * @param request draft validation request being evaluated
     * @param config preprocessing configuration loaded from YAML
     * @return map of defendant grouping key to derived context
     */
    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public Map<String, AgeRestrictedResultContext> preprocess(final DraftValidationRequest request,
                                                               final PreprocessingDefinition config) {
        final Set<String> shortCodes = config.getFilterShortCodes().stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        final Map<String, String> defendantGrouping = buildDefendantGrouping(request);
        final Map<String, String> defendantNames = buildDefendantNames(request);
        final Map<String, LocalDate> defendantDatesOfBirth = buildDefendantDatesOfBirth(request);

        final Map<String, List<ResultLineDto>> linesByGroup = new LinkedHashMap<>();
        for (final ResultLineDto rl : request.getResultLines()) {
            final String groupKey = defendantGrouping.get(rl.getDefendantId());
            if (groupKey == null) {
                continue;
            }
            linesByGroup.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(rl);
        }

        final Map<String, AgeRestrictedResultContext> result = new LinkedHashMap<>();

        for (final Map.Entry<String, List<ResultLineDto>> groupEntry : linesByGroup.entrySet()) {
            final String groupKey = groupEntry.getKey();

            final List<String> qualifyingOffenceIds = groupEntry.getValue().stream()
                    .filter(rl -> rl.getShortCode() != null
                            && shortCodes.contains(rl.getShortCode().toUpperCase(Locale.ROOT)))
                    .map(ResultLineDto::getOffenceId)
                    .collect(Collectors.toCollection(LinkedHashSet::new))
                    .stream()
                    .toList();

            if (qualifyingOffenceIds.isEmpty()) {
                continue;
            }

            final boolean isUnder21 = isUnder21(defendantDatesOfBirth.get(groupKey), request.getHearingDay());

            result.put(groupKey, new AgeRestrictedResultContext(
                    groupKey,
                    defendantNames.getOrDefault(groupKey, "Unknown"),
                    isUnder21,
                    qualifyingOffenceIds));
        }

        return result;
    }

    /**
     * Fails safe (never {@code true}) when either date is missing, per spec FR-011 — a caller
     * that hasn't yet populated {@code dateOfBirth} must not have this rule silently block every
     * imprisonment result it submits.
     */
    private boolean isUnder21(final LocalDate dateOfBirth, final LocalDate hearingDay) {
        return dateOfBirth != null && hearingDay != null
                && Period.between(dateOfBirth, hearingDay).getYears() < AGE_OF_MAJORITY_FOR_IMPRISONMENT;
    }

    private Map<String, LocalDate> buildDefendantDatesOfBirth(final DraftValidationRequest request) {
        final Map<String, LocalDate> datesOfBirth = new HashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto d : request.getDefendants()) {
                if (hasMasterDefendantId(d)) {
                    datesOfBirth.putIfAbsent(d.getMasterDefendantId(), d.getDateOfBirth());
                }
            }
        }
        return datesOfBirth;
    }

    private Map<String, String> buildDefendantNames(final DraftValidationRequest request) {
        final Map<String, String> names = new HashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto d : request.getDefendants()) {
                if (hasMasterDefendantId(d)) {
                    names.putIfAbsent(d.getMasterDefendantId(), buildFullName(d));
                }
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
                if (hasMasterDefendantId(d)) {
                    grouping.put(d.getDefendantId(), d.getMasterDefendantId());
                }
            }
        }
        return grouping;
    }

    /**
     * A defendant without a {@code masterDefendantId} cannot be grouped or evaluated by this rule;
     * such a defendant is excluded entirely rather than falling back to another identifier.
     */
    private boolean hasMasterDefendantId(final DefendantDto defendant) {
        final String masterDefendantId = defendant.getMasterDefendantId();
        return masterDefendantId != null && !masterDefendantId.isBlank();
    }
}
