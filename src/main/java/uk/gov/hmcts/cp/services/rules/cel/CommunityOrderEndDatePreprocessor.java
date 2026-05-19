package uk.gov.hmcts.cp.services.rules.cel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDtoPromptsInner;

/**
 * Per-(defendant, offence) preprocessor for the DR-COEW-001 community order end-date validation
 * rule. Produces one {@link CommunityOrderContext} per community order result line
 * (COEW / COS / CONI) that carries a non-null {@code endDate}, keyed by
 * {@code defendantId + "_" + offenceId}.
 */
@Component
@Slf4j
public class CommunityOrderEndDatePreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "community-order-end-date";

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    public Map<String, CommunityOrderContext> preprocess(final DraftValidationRequest request,
                                                          final PreprocessingDefinition config) {
        final Set<String> orderCodes = upperSet(config.getCommunityOrderShortCodes());
        final Set<String> curCodes = upperSet(config.getCurfewShortCodes());
        final Set<String> cureCodes = upperSet(config.getCurfewTagShortCodes());
        final Set<String> curaCodes = upperSet(config.getFurtherCurfewShortCodes());
        final Set<String> aarCodes = upperSet(config.getAlcoholAbstinenceShortCodes());
        final Set<String> upwrCodes = upperSet(config.getUnpaidWorkShortCodes());

        final Map<String, DefendantDto> defendantMap = buildDefendantMap(request);
        final Map<String, List<ResultLineDto>> linesByGroup = groupByDefendantAndOffence(request);
        final Map<String, CommunityOrderContext> result = new LinkedHashMap<>();

        for (final Map.Entry<String, List<ResultLineDto>> entry : linesByGroup.entrySet()) {
            final ResultLineDto orderLine = findOrderLine(entry.getValue(), orderCodes);
            if (orderLine != null) {
                result.put(entry.getKey(),
                        buildContext(entry.getValue(), orderLine, defendantMap,
                                curCodes, cureCodes, curaCodes, aarCodes, upwrCodes,
                                request.getHearingDay()));
            }
        }

        return result;
    }

    private static CommunityOrderContext buildContext(final List<ResultLineDto> lines,
                                                       final ResultLineDto orderLine,
                                                       final Map<String, DefendantDto> defendantMap,
                                                       final Set<String> curCodes,
                                                       final Set<String> cureCodes,
                                                       final Set<String> curaCodes,
                                                       final Set<String> aarCodes,
                                                       final Set<String> upwrCodes,
                                                       final LocalDate hearingDate) {
        final LocalDate orderEndDate = getPromptDate(orderLine, "endDate");
        final long curViolationCount = hasRequirementAfter(lines, curCodes, orderEndDate) ? 1L : 0L;
        final long cureViolationCount = hasRequirementAfter(lines, cureCodes, orderEndDate) ? 1L : 0L;
        final long curaViolationCount = hasRequirementAfter(lines, curaCodes, orderEndDate) ? 1L : 0L;
        final long aarViolationCount = hasRequirementAfter(lines, aarCodes, orderEndDate) ? 1L : 0L;
        final long upwrViolationCount = computeUpwrViolation(lines, upwrCodes, orderEndDate, hearingDate);
        final String defendantName = resolveDefendantName(defendantMap, orderLine.getDefendantId());
        return new CommunityOrderContext(
                defendantName,
                curViolationCount,
                cureViolationCount,
                curaViolationCount,
                aarViolationCount,
                upwrViolationCount,
                List.of(orderLine.getOffenceId()));
    }

    private static ResultLineDto findOrderLine(final List<ResultLineDto> lines,
                                                final Set<String> orderCodes) {
        return lines.stream()
                .filter(line -> getPromptDate(line, "endDate") != null
                        && upperCodes(line.getShortCode(), orderCodes))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasRequirementAfter(final List<ResultLineDto> lines,
                                                final Set<String> codes,
                                                final LocalDate orderEndDate) {
        return lines.stream()
                .anyMatch(line -> {
                    final LocalDate reqDate = getPromptDate(line, "endDate");
                    return reqDate != null
                            && upperCodes(line.getShortCode(), codes)
                            && reqDate.isAfter(orderEndDate);
                });
    }

    private static LocalDate getPromptDate(final ResultLineDto line, final String ref) {
        final List<ResultLineDtoPromptsInner> prompts = line.getPrompts();
        return prompts == null ? null : prompts.stream()
                .filter(p -> ref.equals(p.getPromptRef()) && p.getValue() != null)
                .map(ResultLineDtoPromptsInner::getValue)
                .map(LocalDate::parse)
                .findFirst()
                .orElse(null);
    }

    private static long computeUpwrViolation(final List<ResultLineDto> lines,
                                              final Set<String> upwrCodes,
                                              final LocalDate orderEndDate,
                                              final LocalDate hearingDate) {
        final boolean hasUpwr = lines.stream()
                .anyMatch(line -> upperCodes(line.getShortCode(), upwrCodes));
        return hasUpwr && orderEndDate.isBefore(hearingDate.plusMonths(12)) ? 1L : 0L;
    }

    private static boolean upperCodes(final String shortCode, final Set<String> codes) {
        return shortCode != null && codes.contains(shortCode.toUpperCase(Locale.ROOT));
    }

    private static Map<String, DefendantDto> buildDefendantMap(
            final DraftValidationRequest request) {
        final Map<String, DefendantDto> map = new LinkedHashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto defendant : request.getDefendants()) {
                if (defendant.getId() != null) {
                    map.put(defendant.getId(), defendant);
                }
            }
        }
        return map;
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static Map<String, List<ResultLineDto>> groupByDefendantAndOffence(
            final DraftValidationRequest request) {
        final Map<String, List<ResultLineDto>> grouped = new LinkedHashMap<>();
        if (request.getResultLines() != null) {
            for (final ResultLineDto line : request.getResultLines()) {
                if (line.getDefendantId() != null && line.getOffenceId() != null) {
                    final String key = line.getDefendantId() + "_" + line.getOffenceId();
                    grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
                }
            }
        }
        return grouped;
    }

    private static String resolveDefendantName(final Map<String, DefendantDto> defendantMap,
                                                final String defendantId) {
        final DefendantDto defendant = defendantMap.get(defendantId);
        final String firstName = defendant != null && defendant.getFirstName() != null
                ? defendant.getFirstName() : "";
        final String lastName = defendant != null && defendant.getLastName() != null
                ? defendant.getLastName() : "";
        return (firstName + " " + lastName).strip();
    }

    private static Set<String> upperSet(final List<String> values) {
        final List<String> source = values == null ? List.of() : values;
        return source.stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
