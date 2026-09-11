package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupByOffence;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupLinesByDedupedDefendant;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupResultsByOffence;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.hasUpperCode;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Produces one {@link ConditionalBailContext} per deduplicated defendant for DR-URG-008.
 *
 * <p>A defendant group qualifies for a context if it has at least one result line for a
 * conditional-bail offence (bailStatus == B). Conditional-bail offences that have no result lines
 * anywhere in the request are treated as "not bail-ended" and are included in the count for any
 * defendant group that already has CB result lines, preventing false positives when only some
 * CB offences have been resulted.
 */
@Component
public class ConditionalBailPreprocessor implements ValidationPreprocessor {

    public static final String QUALIFIER = "conditional-bail-urgent-check";

    private static final Set<String> URGENT_UPPER = upperSet(List.of("URGENT"));

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    @SuppressWarnings({"PMD.AvoidInstantiatingObjectsInLoops", "PMD.OnlyOneReturn"})
    public Map<String, ConditionalBailContext> preprocess(final DraftValidationRequest request,
                                                           final PreprocessingDefinition config) {
        final Map<String, OffenceDto> offenceMap = buildOffenceMap(request);
        if (offenceMap.isEmpty()) {
            return Map.of();
        }

        final Set<String> cbOffenceIds = new LinkedHashSet<>();
        for (final Map.Entry<String, OffenceDto> entry : offenceMap.entrySet()) {
            if (entry.getValue().getBailStatus() == OffenceDto.BailStatusEnum.B) {
                cbOffenceIds.add(entry.getKey());
            }
        }
        if (cbOffenceIds.isEmpty()) {
            return Map.of();
        }

        final Set<String> bailEndingUpper = upperSet(config.bailEndingShortCodes());

        // CB offences with no result lines anywhere — treated as not bail-ended for any defendant
        final Map<String, List<ResultLineDto>> allResultsByOffence = groupResultsByOffence(request);
        final Set<String> unresultedCbIds = new LinkedHashSet<>();
        for (final String cbId : cbOffenceIds) {
            if (!allResultsByOffence.containsKey(cbId)) {
                unresultedCbIds.add(cbId);
            }
        }

        final PreprocessorHelper.DedupedLineGroups groups = groupLinesByDedupedDefendant(request);
        final Map<String, List<ResultLineDto>> linesByGroup = groups.linesByGroup();
        final Map<String, String> groupNames = groups.groupNames();

        final Map<String, ConditionalBailContext> result = new LinkedHashMap<>();

        for (final Map.Entry<String, List<ResultLineDto>> entry : linesByGroup.entrySet()) {
            final String groupKey = entry.getKey();
            final List<ResultLineDto> groupLines = entry.getValue();

            // CB offences this defendant group has result lines for
            final Map<String, List<ResultLineDto>> cbLinesByOffence = new LinkedHashMap<>();
            for (final Map.Entry<String, List<ResultLineDto>> oe : groupByOffence(groupLines).entrySet()) {
                if (cbOffenceIds.contains(oe.getKey())) {
                    cbLinesByOffence.put(oe.getKey(), oe.getValue());
                }
            }

            if (cbLinesByOffence.isEmpty()) {
                continue; // no CB result lines for this defendant group
            }

            // Also count unresulted CB offences for groups that have CB result lines
            final Set<String> allCbIds = new LinkedHashSet<>(cbLinesByOffence.keySet());
            allCbIds.addAll(unresultedCbIds);

            long bailEndedCount = 0;
            boolean urgentSeen = false;

            for (final String cbOffId : allCbIds) {
                final List<ResultLineDto> cbLines = cbLinesByOffence.getOrDefault(cbOffId, List.of());
                boolean bailEnded = false;
                boolean hasUrgent = false;
                for (final ResultLineDto rl : cbLines) {
                    if (!bailEnded
                            && (rl.getCategory() == ResultLineDto.CategoryEnum.F
                                    || hasUpperCode(rl, bailEndingUpper))) {
                        bailEnded = true;
                    }
                    if (!hasUrgent && hasUpperCode(rl, URGENT_UPPER)) {
                        hasUrgent = true;
                    }
                }
                if (bailEnded) {
                    bailEndedCount++;
                }
                if (hasUrgent) {
                    urgentSeen = true;
                }
            }

            result.put(groupKey, new ConditionalBailContext(
                    groupKey,
                    groupNames.getOrDefault(groupKey, "Unknown"),
                    allCbIds.size(),
                    bailEndedCount,
                    urgentSeen ? 1L : 0L,
                    List.copyOf(allCbIds)));
        }

        return result;
    }

    private static Map<String, OffenceDto> buildOffenceMap(final DraftValidationRequest request) {
        final Map<String, OffenceDto> map = new LinkedHashMap<>();
        if (request.getOffences() != null) {
            for (final OffenceDto offence : request.getOffences()) {
                if (offence.getOffenceId() != null) {
                    map.put(offence.getOffenceId(), offence);
                }
            }
        }
        return map;
    }
}
