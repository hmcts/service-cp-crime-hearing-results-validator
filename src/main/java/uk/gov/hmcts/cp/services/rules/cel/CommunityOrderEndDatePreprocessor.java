package uk.gov.hmcts.cp.services.rules.cel;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Preprocesses community order result lines into per-defendant
 * {@link CommunityOrderContext} summaries for the DR-COEW-001 rule.
 *
 * <p>AC2 — detects when any child requirement (CUR, CURE, CURA, AAR) on a community order
 * has a date strictly later than the parent order end date.
 *
 * <p>AC3 — detects when a community order containing a UPWR child result has an end date
 * less than 12 calendar months from the hearing date
 * ({@code orderEndDate.isBefore(hearingDay.plusMonths(12).minusDays(1))}).
 *
 * <p>Prompt ref keys are stable API-contract values from
 * {@code api-cp-crime-hearing-results-validator:0.1.6} and are intentionally hardcoded
 * rather than being YAML-configurable (see research.md Decision 3).
 */
@Slf4j
@Component
public class CommunityOrderEndDatePreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "community-order-end-date";

    // Prompt ref keys — stable API-contract values (research.md Decision 3)
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
        final Set<String> upwrCodes = upperSet(config.getUnpaidWorkShortCodes());

        final Map<String, String> defendantNames = buildDefendantNames(request);

        // Group all result lines by defendantId
        final Map<String, List<ResultLineDto>> linesByDefendant = groupByDefendant(request);

        final Map<String, CommunityOrderContext> result = new LinkedHashMap<>();

        for (final Map.Entry<String, List<ResultLineDto>> entry : linesByDefendant.entrySet()) {
            final String defendantId = entry.getKey();
            final List<ResultLineDto> lines = entry.getValue();

            // Skip defendants with no community order lines
            final boolean hasCommunityOrder = lines.stream()
                    .anyMatch(rl -> hasUpperCode(rl, orderCodes));
            if (!hasCommunityOrder) {
                continue;
            }

            // Accumulate violations across all offences for this defendant
            final List<String> curViolationIds = new ArrayList<>();
            final List<String> cureViolationIds = new ArrayList<>();
            final List<String> curaViolationIds = new ArrayList<>();
            final List<String> aarViolationIds = new ArrayList<>();
            final List<String> upwrViolationIds = new ArrayList<>();

            // Group lines by offenceId within this defendant
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

                // Find the community order line for this offence and parse its end date
                final LocalDate orderEndDate = offenceLines.stream()
                        .filter(rl -> hasUpperCode(rl, orderCodes))
                        .map(rl -> parsePromptDate(rl, PROMPT_END_DATE, offenceId))
                        .filter(d -> d != null)
                        .findFirst()
                        .orElse(null);

                if (orderEndDate == null) {
                    // No parseable order end date — skip AC2 and AC3 checks for this offence
                    continue;
                }

                // AC2a — CUR: compare "endDate" prompt
                checkRequirementViolation(offenceLines, curCodes, PROMPT_END_DATE,
                        orderEndDate, offenceId, curViolationIds);

                // AC2b — CURE: compare "endDateOfTagging" prompt
                checkRequirementViolation(offenceLines, cureCodes, PROMPT_END_DATE_OF_TAG,
                        orderEndDate, offenceId, cureViolationIds);

                // AC2c — CURA: compare "endDate" prompt
                checkRequirementViolation(offenceLines, curaCodes, PROMPT_END_DATE,
                        orderEndDate, offenceId, curaViolationIds);

                // AC2d — AAR: compare "until" prompt
                checkRequirementViolation(offenceLines, aarCodes, PROMPT_UNTIL,
                        orderEndDate, offenceId, aarViolationIds);

                // AC3 — UPWR: check order is at least hearingDay + 12m - 1d
                final boolean hasUpwr = offenceLines.stream()
                        .anyMatch(rl -> hasUpperCode(rl, upwrCodes));
                if (hasUpwr && request.getHearingDay() != null) {
                    final LocalDate minEndDate = request.getHearingDay().plusMonths(12).minusDays(1);
                    if (orderEndDate.isBefore(minEndDate)) {
                        upwrViolationIds.add(offenceId);
                    }
                }
            }

            result.put(defendantId, new CommunityOrderContext(
                    defendantNames.getOrDefault(defendantId, "Unknown"),
                    curViolationIds.size(),
                    cureViolationIds.size(),
                    curaViolationIds.size(),
                    aarViolationIds.size(),
                    upwrViolationIds.size(),
                    List.copyOf(curViolationIds),
                    List.copyOf(cureViolationIds),
                    List.copyOf(curaViolationIds),
                    List.copyOf(aarViolationIds),
                    List.copyOf(upwrViolationIds),
                    List.copyOf(allOffenceIds)));
        }

        return result;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Checks whether any result line in {@code lines} matching {@code codes} has a prompt
     * value (identified by {@code promptRef}) strictly after {@code orderEndDate}. If so,
     * adds {@code offenceId} to {@code violationIds}.
     */
    private void checkRequirementViolation(final List<ResultLineDto> lines,
                                            final Set<String> codes,
                                            final String promptRef,
                                            final LocalDate orderEndDate,
                                            final String offenceId,
                                            final List<String> violationIds) {
        final boolean violated = lines.stream()
                .filter(rl -> hasUpperCode(rl, codes))
                .anyMatch(rl -> {
                    final LocalDate reqDate = parsePromptDate(rl, promptRef, offenceId);
                    return reqDate != null && reqDate.isAfter(orderEndDate);
                });
        if (violated) {
            violationIds.add(offenceId);
        }
    }

    /**
     * Parses the {@code promptValue} for the given {@code promptRef} from the result line's
     * prompts list. Returns {@code null} if the prompt is missing, blank, or unparseable,
     * and logs a warning.
     */
    @SuppressWarnings("PMD.OnlyOneReturn")
    private LocalDate parsePromptDate(final ResultLineDto line,
                                       final String promptRef,
                                       final String offenceId) {
        if (line.getPrompts() == null) {
            return null;
        }
        LocalDate found = null;
        for (final Prompt prompt : line.getPrompts()) {
            if (found == null && promptRef.equals(prompt.getPromptRef())) {
                found = parseDateValue(prompt.getPromptValue(), promptRef,
                        line.getShortCode(), offenceId);
            }
        }
        return found;
    }

    private LocalDate parseDateValue(final String value, final String promptRef,
                                      final String shortCode, final String offenceId) {
        LocalDate result = null;
        if (value == null || value.isBlank()) {
            log.warn("Blank promptValue for promptRef={} on shortCode={} offenceId={}",
                    promptRef, shortCode, offenceId);
        } else {
            try {
                result = LocalDate.parse(value.trim());
            } catch (DateTimeParseException e) {
                log.warn("Unparseable date '{}' for promptRef={} on shortCode={} offenceId={}",
                        value, promptRef, shortCode, offenceId);
            }
        }
        return result;
    }

    private static boolean hasUpperCode(final ResultLineDto line, final Set<String> codes) {
        return line.getShortCode() != null
                && codes.contains(line.getShortCode().toUpperCase(Locale.ROOT));
    }

    private static Set<String> upperSet(final List<String> values) {
        final List<String> source = values == null ? List.of() : values;
        return source.stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static Map<String, List<ResultLineDto>> groupByDefendant(
            final DraftValidationRequest request) {
        final Map<String, List<ResultLineDto>> grouped = new LinkedHashMap<>();
        if (request.getResultLines() != null) {
            for (final ResultLineDto rl : request.getResultLines()) {
                if (rl.getDefendantId() != null) {
                    grouped.computeIfAbsent(rl.getDefendantId(), k -> new ArrayList<>()).add(rl);
                }
            }
        }
        return grouped;
    }

    private static Map<String, String> buildDefendantNames(final DraftValidationRequest request) {
        final Map<String, String> names = new LinkedHashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto d : request.getDefendants()) {
                names.put(d.getDefendantId(), buildFullName(d));
            }
        }
        return names;
    }

    private static String buildFullName(final DefendantDto defendant) {
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
}
