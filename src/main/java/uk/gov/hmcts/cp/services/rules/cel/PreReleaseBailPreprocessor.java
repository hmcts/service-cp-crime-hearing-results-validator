package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.anyShortCodeIn;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.groupResultsByOffence;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Per-offence preprocessor for DR-BAIL-016. Emits one {@link PreReleaseBailContext} for each
 * offence where:
 * <ul>
 *   <li>{@code offence.bailStatus} ∈ {C, L, S} (defendant currently in custody/care/detention)</li>
 *   <li>a conditional bail result code is present on the offence</li>
 *   <li>at least one trigger bail condition (PASS1–8, PORSC, PORS, or PORO1 with keyword) is
 *       present on the same offence</li>
 * </ul>
 * No external API calls — purely in-memory evaluation over {@link DraftValidationRequest}.
 */
@Component
public class PreReleaseBailPreprocessor implements ValidationPreprocessor {

    public static final String QUALIFIER = "pre-release-bail-condition";

    private static final String PORO1_UPPER = "PORO1";

    private static final Set<String> CCIC_CODE = Set.of("CCIC");
    private static final Set<String> CCSC_CODE = Set.of("CCSC");

    private static final Set<OffenceDto.BailStatusEnum> CUSTODIAL_STATUSES =
            Set.of(OffenceDto.BailStatusEnum.C, OffenceDto.BailStatusEnum.L,
                    OffenceDto.BailStatusEnum.S);

    private static final Set<String> TRIGGER_STEMS =
            Set.of("passport", "suret", "securit", "asbort", "echniw", "sicrwydd");

    private static final Pattern COMBINING_MARKS =
            Pattern.compile("\\p{M}");

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    // PreReleaseBailContext instances are per-offence and allocated inside the loop by design.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public Map<String, PreReleaseBailContext> preprocess(final DraftValidationRequest request,
                                                          final PreprocessingDefinition config) {
        final Set<String> conditionalBailCodes = upperSet(config.conditionalBailResultCodes());
        final Set<String> crownCourtCodes = upperSet(config.crownCourtBailCodes());
        final Set<String> triggerConditionCodes = upperSet(config.triggerBailConditionCodes());
        final Set<String> allConditionalCodes = Stream.concat(
                        conditionalBailCodes.stream(), crownCourtCodes.stream())
                .collect(Collectors.toUnmodifiableSet());
        final Map<String, OffenceDto> offenceById = indexOffences(request);
        final Map<String, DefendantDto> defendantById = indexDefendants(request);
        final Map<String, List<ResultLineDto>> resultsByOffence = groupResultsByOffence(request);

        final Map<String, PreReleaseBailContext> result = new LinkedHashMap<>();

        for (final Map.Entry<String, List<ResultLineDto>> entry : resultsByOffence.entrySet()) {
            final String offenceId = entry.getKey();
            final List<ResultLineDto> lines = entry.getValue();

            final OffenceDto offence = offenceById.get(offenceId);
            if (offence == null || offence.getBailStatus() == null
                    || !CUSTODIAL_STATUSES.contains(offence.getBailStatus())) {
                continue;
            }

            final String matchedCode = firstMatchingCode(lines, allConditionalCodes);
            if (matchedCode == null) {
                continue;
            }

            final boolean hasTrigger = anyShortCodeIn(lines, triggerConditionCodes)
                    || anyOtherBailConditionWithKeyword(lines, PORO1_UPPER);
            if (!hasTrigger) {
                continue;
            }

            final DefendantDto defendant = defendantById.get(offence.getDefendantId());
            final Integer age = computeAge(
                    defendant != null ? defendant.getDateOfBirth() : null,
                    request.getHearingDay());

            final long adultCount = age != null && age >= 18 ? 1L : 0L;
            final long youthCount = age != null && age < 18  ? 1L : 0L;
            final long noDobCount = age == null              ? 1L : 0L;
            // Scan ALL lines for CCIC/CCSC independently of which code was matched first,
            // so result-line ordering cannot suppress the crown-court-specific warnings.
            final long ccicCount  = firstMatchingCode(lines, CCIC_CODE) != null ? 1L : 0L;
            final long ccscCount  = firstMatchingCode(lines, CCSC_CODE) != null ? 1L : 0L;

            result.put(offenceId, new PreReleaseBailContext(
                    offenceId, 1L, adultCount, youthCount, noDobCount, ccicCount, ccscCount));
        }

        return result;
    }

    @SuppressWarnings("PMD.OnlyOneReturn")
    private static String firstMatchingCode(final List<ResultLineDto> lines,
                                             final Set<String> upperCodes) {
        for (final ResultLineDto line : lines) {
            final String code = line.getShortCode();
            if (code != null && upperCodes.contains(code.toUpperCase(Locale.ROOT))) {
                return code.toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    @SuppressWarnings("PMD.OnlyOneReturn")
    private static boolean anyOtherBailConditionWithKeyword(final List<ResultLineDto> lines,
                                                             final String otherCodeUpper) {
        for (final ResultLineDto line : lines) {
            final String code = line.getShortCode();
            if (otherCodeUpper.equals(code == null ? null : code.toUpperCase(Locale.ROOT))) {
                final List<Prompt> prompts = line.getPrompts();
                if (prompts != null) {
                    for (final Prompt prompt : prompts) {
                        if (containsTriggerKeyword(prompt.getPromptValue())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("PMD.OnlyOneReturn")
    /* default */ static boolean containsTriggerKeyword(final String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        final String normalised = COMBINING_MARKS
                .matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
        for (final String stem : TRIGGER_STEMS) {
            if (normalised.contains(stem)) {
                return true;
            }
        }
        return false;
    }

    private static Integer computeAge(final LocalDate dob, final LocalDate hearingDay) {
        return dob == null || hearingDay == null
                ? null
                : Period.between(dob, hearingDay).getYears();
    }

    private static Map<String, OffenceDto> indexOffences(final DraftValidationRequest request) {
        final List<OffenceDto> offences = request.getOffences();
        return offences == null ? Map.of()
                : offences.stream()
                        .filter(o -> o.getOffenceId() != null)
                        .collect(Collectors.toMap(OffenceDto::getOffenceId, o -> o,
                                (a, b) -> a, LinkedHashMap::new));
    }

    private static Map<String, DefendantDto> indexDefendants(final DraftValidationRequest request) {
        final List<DefendantDto> defendants = request.getDefendants();
        return defendants == null ? Map.of()
                : defendants.stream()
                        .filter(d -> d.getDefendantId() != null)
                        .collect(Collectors.toMap(DefendantDto::getDefendantId, d -> d,
                                (a, b) -> a, LinkedHashMap::new));
    }
}
