package uk.gov.hmcts.cp.services.rules.cel;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        Map<String, DefendantContext> result = new LinkedHashMap<>();

        Set<String> defendantIds = request.getResultLines().stream()
                .map(ResultLineDto::getDefendantId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String defendantId : defendantIds) {
            List<ResultLineDto> custodialLines = request.getResultLines().stream()
                    .filter(rl -> defendantId.equals(rl.getDefendantId()))
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

            // The first custodial offence without concurrent/consecutive info
            // is the primary sentence — exclude it from noInfo.
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

            result.put(defendantId, new DefendantContext(
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
}
