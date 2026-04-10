package uk.gov.hmcts.cp.services.rules;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.AffectedOffence;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;

/**
 * Builds user-facing offence references for validation messages and affected offence payloads.
 */
@Component
public class OffenceDisplayHelper {

    /** Resolves a human-readable display number for the given offence. */
    public String resolveDisplayNumber(final String id,
                                       final Map<String, OffenceDto> offenceMap,
                                       final List<String> allOffenceIds) {
        final OffenceDto offence = offenceMap.get(id);
        final String countNumber;
        if (offence != null && offence.getOrderIndex() != null) {
            countNumber = String.valueOf(offence.getOrderIndex());
        } else {
            final int index = allOffenceIds.indexOf(id);
            countNumber = index >= 0 ? String.valueOf(index + 1) : id;
        }

        final String display;
        if (offence != null && offence.getCaseUrn() != null && !offence.getCaseUrn().isBlank()) {
            display = "Offence " + countNumber + " (URN:" + offence.getCaseUrn() + ")";
        } else {
            display = "Offence " + countNumber;
        }
        return display;
    }

    /** Resolves the numeric order index for the given offence. */
    public int resolveOrderIndex(final String id,
                                  final Map<String, OffenceDto> offenceMap,
                                  final List<String> allOffenceIds) {
        final OffenceDto offence = offenceMap.get(id);
        final int result;
        if (offence != null && offence.getOrderIndex() != null) {
            result = offence.getOrderIndex();
        } else {
            final int index = allOffenceIds.indexOf(id);
            result = index >= 0 ? index + 1 : Integer.MAX_VALUE;
        }
        return result;
    }

    /** Builds affected-offence payload entries for the supplied offence identifiers. */
    public List<AffectedOffence> buildAffectedOffences(final List<String> offenceIds,
                                                        final Map<String, OffenceDto> offenceMap) {
        return offenceIds.stream()
                .map(id -> {
                    final OffenceDto offence = offenceMap.get(id);
                    return AffectedOffence.builder()
                            .offenceId(id)
                            .offenceTitle(offence != null ? offence.getOffenceTitle() : null)
                            .build();
                })
                .toList();
    }
}
