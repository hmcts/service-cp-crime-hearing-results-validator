package uk.gov.hmcts.cp.services.rules.cel;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CustodialPreprocessor {

    public Map<String, DefendantContext> preprocess(DraftValidationRequest request,
                                                     PreprocessingDefinition config) {
        Set<String> shortCodes = config.getFilterShortCodes().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toUnmodifiableSet());

        Map<String, String> defendantGrouping = buildDefendantGrouping(request);
        Map<String, String> defendantNames = buildDefendantNames(request);

        Map<String, List<ResultLineDto>> linesByGroup = new LinkedHashMap<>();
        for (ResultLineDto rl : request.getResultLines()) {
            String groupKey = defendantGrouping.getOrDefault(rl.getDefendantId(), rl.getDefendantId());
            linesByGroup.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(rl);
        }

        Map<String, DefendantContext> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<ResultLineDto>> groupEntry : linesByGroup.entrySet()) {
            String groupKey = groupEntry.getKey();
            List<ResultLineDto> groupLines = groupEntry.getValue();

            List<ResultLineDto> custodialLines = groupLines.stream()
                    .filter(rl -> rl.getShortCode() != null
                            && shortCodes.contains(rl.getShortCode().toUpperCase()))
                    .toList();

            if (custodialLines.isEmpty()) {
                continue;
            }

            Map<String, List<ResultLineDto>> byOffence = custodialLines.stream()
                    .collect(Collectors.groupingBy(
                            ResultLineDto::getOffenceId,
                            LinkedHashMap::new,
                            Collectors.toList()));

            if (byOffence.size() <= config.getSkipWhenGroupCount()) {
                continue;
            }

            List<String> noInfoOffenceIds = new ArrayList<>();
            List<String> hasInfoOffenceIds = new ArrayList<>();
            List<String> hasBothOffenceIds = new ArrayList<>();
            boolean primaryFound = false;
            boolean primaryClaimed = false;

            for (Map.Entry<String, List<ResultLineDto>> entry : byOffence.entrySet()) {
                String offenceId = entry.getKey();
                List<ResultLineDto> lines = entry.getValue();

                boolean anyConcurrent = lines.stream()
                        .anyMatch(rl -> Boolean.TRUE.equals(rl.getIsConcurrent()));
                boolean anyConsecutive = lines.stream()
                        .anyMatch(rl -> rl.getConsecutiveToOffence() != null
                                && !rl.getConsecutiveToOffence().isBlank());

                if (anyConcurrent && anyConsecutive) {
                    hasBothOffenceIds.add(offenceId);
                } else if (anyConcurrent || anyConsecutive) {
                    hasInfoOffenceIds.add(offenceId);
                } else if (!primaryClaimed) {
                    primaryClaimed = true;
                    primaryFound = true;
                } else {
                    noInfoOffenceIds.add(offenceId);
                }
            }

            result.put(groupKey, new DefendantContext(
                    defendantNames.getOrDefault(groupKey, "Unknown"),
                    noInfoOffenceIds.size(),
                    hasInfoOffenceIds.size(),
                    hasBothOffenceIds.size(),
                    primaryFound ? 1 : 0,
                    byOffence.size(),
                    noInfoOffenceIds,
                    hasInfoOffenceIds,
                    hasBothOffenceIds,
                    new ArrayList<>(byOffence.keySet())));
        }

        return result;
    }

    private Map<String, String> buildDefendantNames(DraftValidationRequest request) {
        Map<String, String> names = new HashMap<>();
        if (request.getDefendants() != null) {
            for (DefendantDto d : request.getDefendants()) {
                String groupKey = (d.getMasterDefendantId() != null && !d.getMasterDefendantId().isBlank())
                        ? d.getMasterDefendantId()
                        : d.getId();
                names.putIfAbsent(groupKey, buildFullName(d));
            }
        }
        return names;
    }

    private String buildFullName(DefendantDto defendant) {
        String first = defendant.getFirstName();
        String last = defendant.getLastName();
        if (first != null && last != null) {
            return first + " " + last;
        }
        return first != null ? first : last;
    }

    private Map<String, String> buildDefendantGrouping(DraftValidationRequest request) {
        Map<String, String> grouping = new HashMap<>();
        if (request.getDefendants() != null) {
            for (DefendantDto d : request.getDefendants()) {
                String groupKey = (d.getMasterDefendantId() != null && !d.getMasterDefendantId().isBlank())
                        ? d.getMasterDefendantId()
                        : d.getId();
                grouping.put(d.getId(), groupKey);
            }
        }
        return grouping;
    }
}
