package uk.gov.hmcts.cp.services.rules.cel;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Preprocessor that detects period end-date mismatches for Curfew (CUR, CURE, YRC2, YRC1)
 * and Alcohol Abstinence and Monitoring (AAR) requirements.
 *
 * <p>For each (defendant, offence) pair that has a qualifying parent order (CO or YRO),
 * computes the expected end date for each requirement line and compares it with the recorded
 * end date. Any mismatch produces a {@link CurfewPeriodContext} in the output map keyed by
 * {@code "<SHORT_CODE>:<defendantId>:<offenceId>"}.
 *
 * <p>Missing, blank, or unparseable prompt data is handled gracefully: the check for that
 * requirement is skipped, a {@code WARN}-level log is emitted, and no
 * {@code ValidationIssue} is raised.
 */
@Slf4j
@Component("curfew-period-check")
@SuppressWarnings("PMD.OnlyOneReturn") // parsing helpers use early null-return for readability; refactoring to single-exit would obscure intent
public class CurfewPeriodPreprocessor implements ValidationPreprocessor {

    private static final DateTimeFormatter OUTPUT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Local record used to carry DURATION prompt child values. */
    private record DurationValue(ChronoUnit unit, long quantity) {}

    @Override
    public String type() {
        return "curfew-period-check";
    }

    @Override
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // CurfewPeriodContext is keyed per-violation; pre-allocation is not possible
    public Map<String, CurfewPeriodContext> preprocess(
            final DraftValidationRequest request,
            final PreprocessingDefinition config) {

        // 1. Normalise short-code sets (uppercase)
        final Set<String> coShortCodes = normalise(config.getCommunityOrderShortCodes());
        final Set<String> yroShortCodes = normalise(config.getYroShortCodes());
        final Set<String> curCodes = normalise(config.getCurfewShortCodes());
        final Set<String> cureCodes = normalise(config.getCurfewTagShortCodes());
        final Set<String> aarCodes = normalise(config.getAlcoholAbstinenceShortCodes());

        // 2. Build defendantId → name lookup
        final Map<String, String> defendantNames = request.getDefendants() == null
                ? Map.of()
                : request.getDefendants().stream()
                        .collect(Collectors.toMap(
                                DefendantDto::getId,
                                d -> d.getFirstName() + " " + d.getLastName(),
                                (a, b) -> a));

        // 3. Group result lines by defendant then by offence
        final Map<String, List<ResultLineDto>> byDefendant = request.getResultLines() == null
                ? Map.of()
                : request.getResultLines().stream()
                        .collect(Collectors.groupingBy(
                                ResultLineDto::getDefendantId,
                                LinkedHashMap::new,
                                Collectors.toList()));

        final Map<String, CurfewPeriodContext> violations = new LinkedHashMap<>();

        final LocalDate hearingDay = request.getHearingDay();

        for (final Map.Entry<String, List<ResultLineDto>> defEntry : byDefendant.entrySet()) {
            final String defendantId = defEntry.getKey();
            final String defendantName = defendantNames.getOrDefault(defendantId, defendantId);

            final Map<String, List<ResultLineDto>> byOffence = defEntry.getValue().stream()
                    .collect(Collectors.groupingBy(
                            ResultLineDto::getOffenceId,
                            LinkedHashMap::new,
                            Collectors.toList()));

            for (final Map.Entry<String, List<ResultLineDto>> offEntry : byOffence.entrySet()) {
                final String offenceId = offEntry.getKey();
                final List<ResultLineDto> offenceLines = offEntry.getValue();

                final boolean hasCo = offenceLines.stream()
                        .anyMatch(rl -> coShortCodes.contains(upper(rl.getShortCode())));
                final boolean hasYro = offenceLines.stream()
                        .anyMatch(rl -> yroShortCodes.contains(upper(rl.getShortCode())));

                if (!hasCo && !hasYro) {
                    continue; // no qualifying parent order
                }

                // CUR / YRC2 check
                for (final ResultLineDto line : offenceLines) {
                    if (!curCodes.contains(upper(line.getShortCode()))) {
                        continue;
                    }
                    final LocalDate startDate = parseDate(line, "startDate");
                    final DurationValue period = parseDuration(line, "curfewPeriod");
                    final LocalDate endDate = parseDate(line, "endDate");
                    if (startDate == null || period == null || endDate == null) {
                        log.warn("Skipping curfew period check for shortCode={} offenceId={}: "
                                + "missing or invalid prompt", line.getShortCode(), offenceId);
                        continue;
                    }
                    if (period.quantity() <= 0) {
                        log.warn("Skipping curfew period check for shortCode={} offenceId={}: "
                                + "non-positive period quantity {}", line.getShortCode(), offenceId,
                                period.quantity());
                        continue;
                    }
                    final LocalDate expectedEnd;
                    try {
                        expectedEnd = startDate.plus(period.quantity(), period.unit()).minusDays(1);
                    } catch (final DateTimeException e) {
                        log.warn("Skipping curfew period check for shortCode={} offenceId={}: "
                                + "date arithmetic overflow: {}", line.getShortCode(), offenceId, e.getMessage());
                        continue;
                    }
                    if (!endDate.equals(expectedEnd)) {
                        final String key = upper(line.getShortCode()) + ":" + defendantId + ":" + offenceId;
                        violations.put(key, new CurfewPeriodContext(
                                defendantId, defendantName, offenceId, 1L,
                                expectedEnd.format(OUTPUT_DATE_FORMAT)));
                    }
                }

                // CURE / YRC1 check
                for (final ResultLineDto line : offenceLines) {
                    if (!cureCodes.contains(upper(line.getShortCode()))) {
                        continue;
                    }
                    final LocalDate startDate = parseDate(line, "startDateOfTagging");
                    final DurationValue period = parseDuration(line, "curfewAndElectronicMonitoringPeriod");
                    final LocalDate endDate = parseDate(line, "endDateOfTagging");
                    if (startDate == null || period == null || endDate == null) {
                        log.warn("Skipping curfew-tag period check for shortCode={} offenceId={}: "
                                + "missing or invalid prompt", line.getShortCode(), offenceId);
                        continue;
                    }
                    if (period.quantity() <= 0) {
                        log.warn("Skipping curfew-tag period check for shortCode={} offenceId={}: "
                                + "non-positive period quantity {}", line.getShortCode(), offenceId,
                                period.quantity());
                        continue;
                    }
                    final LocalDate expectedEnd;
                    try {
                        expectedEnd = startDate.plus(period.quantity(), period.unit()).minusDays(1);
                    } catch (final DateTimeException e) {
                        log.warn("Skipping curfew-tag period check for shortCode={} offenceId={}: "
                                + "date arithmetic overflow: {}", line.getShortCode(), offenceId, e.getMessage());
                        continue;
                    }
                    if (!endDate.equals(expectedEnd)) {
                        final String key = upper(line.getShortCode()) + ":" + defendantId + ":" + offenceId;
                        violations.put(key, new CurfewPeriodContext(
                                defendantId, defendantName, offenceId, 1L,
                                expectedEnd.format(OUTPUT_DATE_FORMAT)));
                    }
                }

                // AAR check — CO parent only
                if (hasCo) {
                    for (final ResultLineDto line : offenceLines) {
                        if (!aarCodes.contains(upper(line.getShortCode()))) {
                            continue;
                        }
                        if (hearingDay == null) {
                            log.warn("Skipping AAR period check for offenceId={}: hearingDay is null",
                                    offenceId);
                            continue;
                        }
                        final Integer days = parseInt(line,
                                "numberOfDaysToAbstainFromConsumingAnyAlcohol");
                        final LocalDate untilDate = parseDate(line, "until");
                        if (days == null || untilDate == null) {
                            log.warn("Skipping AAR period check for offenceId={}: "
                                    + "missing or invalid prompt", offenceId);
                            continue;
                        }
                        if (days <= 0) {
                            log.warn("Skipping AAR period check for offenceId={}: "
                                    + "non-positive days value {}", offenceId, days);
                            continue;
                        }
                        final LocalDate expectedEnd;
                        try {
                            expectedEnd = hearingDay.plusDays(days).minusDays(1);
                        } catch (final DateTimeException e) {
                            log.warn("Skipping AAR period check for offenceId={}: "
                                    + "date arithmetic overflow: {}", offenceId, e.getMessage());
                            continue;
                        }
                        if (!untilDate.equals(expectedEnd)) {
                            final String key = "AAR:" + defendantId + ":" + offenceId;
                            violations.put(key, new CurfewPeriodContext(
                                    defendantId, defendantName, offenceId, 1L,
                                    expectedEnd.format(OUTPUT_DATE_FORMAT)));
                        }
                    }
                }
            }
        }

        return violations;
    }

    // ── Prompt parsing helpers ────────────────────────────────────────────────

    private LocalDate parseDate(final ResultLineDto line, final String promptRef) {
        if (line.getPrompts() == null) {
            return null;
        }
        return line.getPrompts().stream()
                .filter(p -> promptRef.equals(p.getPromptRef()))
                .findFirst()
                .map(p -> {
                    final String value = p.getPromptValue();
                    if (value == null || value.isBlank()) {
                        return null;
                    }
                    try {
                        return LocalDate.parse(value);
                    } catch (final DateTimeParseException e) {
                        log.warn("Cannot parse date prompt '{}' with value '{}': {}",
                                promptRef, value, e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    private DurationValue parseDuration(final ResultLineDto line, final String promptRef) {
        if (line.getPrompts() == null) {
            return null;
        }
        return line.getPrompts().stream()
                .filter(p -> promptRef.equals(p.getPromptRef()))
                .findFirst()
                .map(p -> {
                    final List<Prompt> children = p.getChildPrompts();
                    if (children == null || children.isEmpty()) {
                        log.warn("DURATION prompt '{}' has no child prompts", promptRef);
                        return null;
                    }
                    final Prompt child = children.getFirst();
                    final ChronoUnit unit = mapUnit(child.getPromptRef(), promptRef);
                    if (unit == null) {
                        return null;
                    }
                    final String qtyStr = child.getPromptValue();
                    if (qtyStr == null || qtyStr.isBlank()) {
                        log.warn("DURATION prompt '{}' child has blank quantity", promptRef);
                        return null;
                    }
                    try {
                        return new DurationValue(unit, Long.parseLong(qtyStr));
                    } catch (final NumberFormatException e) {
                        log.warn("Cannot parse DURATION quantity '{}' for prompt '{}'",
                                qtyStr, promptRef);
                        return null;
                    }
                })
                .orElse(null);
    }

    private Integer parseInt(final ResultLineDto line, final String promptRef) {
        if (line.getPrompts() == null) {
            return null;
        }
        return line.getPrompts().stream()
                .filter(p -> promptRef.equals(p.getPromptRef()))
                .findFirst()
                .map(p -> {
                    final String value = p.getPromptValue();
                    if (value == null || value.isBlank()) {
                        return null;
                    }
                    try {
                        return Integer.parseInt(value);
                    } catch (final NumberFormatException e) {
                        log.warn("Cannot parse INT prompt '{}' with value '{}'", promptRef, value);
                        return null;
                    }
                })
                .orElse(null);
    }

    private ChronoUnit mapUnit(final String unitStr, final String parentPromptRef) {
        if (unitStr == null) {
            log.warn("DURATION child has null promptRef for parent '{}'", parentPromptRef);
            return null;
        }
        return switch (unitStr) {
            case "Days" -> ChronoUnit.DAYS;
            case "Weeks" -> ChronoUnit.WEEKS;
            case "Months" -> ChronoUnit.MONTHS;
            default -> {
                log.warn("Unknown DURATION unit '{}' for parent prompt '{}'",
                        unitStr, parentPromptRef);
                yield null;
            }
        };
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static Set<String> normalise(final List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        return codes.stream().map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static String upper(final String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }
}
