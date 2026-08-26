package uk.gov.hmcts.cp.services.rules.cel;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClient;

/**
 * Per-offence preprocessor for the DR-SEX-008 sexual-offence notification-requirement rule.
 * Produces one {@link SexualOffenceNotificationContext} per offence that is BOTH convicted and
 * classified as a relevant sexual offence ({@code misCode} matching {@code
 * config.qualifyingMisCode()}, resolved via {@link ReferencedataOffenceClient}); every other
 * offence is excluded entirely (no context entry), including any offence whose reference-data
 * lookup fails (fail-open — see {@code contracts/referencedata-offences-integration.md}).
 *
 * <p>Age classification (Adult 18+ / Youth under 18, at {@code hearingDay}) determines which
 * short-code set clears the warning: {@code adultNotificationShortCodes} for Adults, {@code
 * youthNotificationShortCodes} for Youths. A defendant's date of birth resolves via the {@code
 * defendantId} on the offence's own result lines — {@code OffenceDto} carries no direct defendant
 * link. An offence whose result lines reference zero or more than one distinct {@code
 * defendantId} (no result lines yet, or a joint offence) cannot have its defendant's age safely
 * determined and is excluded from this rule entirely — the same "exclude rather than guess"
 * posture as a missing date of birth.
 */
@Component
public class SexualOffenceNotificationPreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "sexual-offence-notification-requirement";

    private static final int AGE_OF_MAJORITY = 18;

    private final ReferencedataOffenceClient referencedataOffenceClient;

    /**
     * Constructs the preprocessor with its reference-data lookup collaborator.
     *
     * @param referencedataOffenceClient fail-open client for the {@code misCode} classification
     *         lookup
     */
    public SexualOffenceNotificationPreprocessor(final ReferencedataOffenceClient referencedataOffenceClient) {
        this.referencedataOffenceClient = referencedataOffenceClient;
    }

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    public Map<String, SexualOffenceNotificationContext> preprocess(final DraftValidationRequest request,
                                                                     final PreprocessingDefinition config) {
        final Set<String> adultCodes = PreprocessorHelper.upperSet(config.adultNotificationShortCodes());
        final Set<String> youthCodes = PreprocessorHelper.upperSet(config.youthNotificationShortCodes());
        final Map<String, List<ResultLineDto>> resultsByOffence = PreprocessorHelper.groupResultsByOffence(request);
        final Map<String, String> defendantNames = PreprocessorHelper.buildDefendantNames(request);
        final Map<String, LocalDate> datesOfBirth = buildDatesOfBirth(request);

        final Map<String, SexualOffenceNotificationContext> result = new LinkedHashMap<>();
        if (request.getOffences() != null) {
            for (final OffenceDto offence : request.getOffences()) {
                buildContext(offence, config, resultsByOffence, defendantNames, datesOfBirth,
                        adultCodes, youthCodes, request.getHearingDay())
                        .ifPresent(context -> result.put(offence.getOffenceId(), context));
            }
        }
        return result;
    }

    private Optional<SexualOffenceNotificationContext> buildContext(
            final OffenceDto offence,
            final PreprocessingDefinition config,
            final Map<String, List<ResultLineDto>> resultsByOffence,
            final Map<String, String> defendantNames,
            final Map<String, LocalDate> datesOfBirth,
            final Set<String> adultCodes,
            final Set<String> youthCodes,
            final LocalDate hearingDay) {

        Optional<SexualOffenceNotificationContext> context = Optional.empty();
        if (Boolean.TRUE.equals(offence.getIsConvicted())) {
            final String offenceId = offence.getOffenceId();
            final Optional<String> misCode = referencedataOffenceClient.lookupMisCode(offenceId);
            if (misCode.isPresent() && config.qualifyingMisCode().equals(misCode.get())) {
                final List<ResultLineDto> lines = resultsByOffence.getOrDefault(offenceId, List.of());
                final Optional<String> defendantId = resolveSingleDefendant(lines);
                if (defendantId.isPresent()) {
                    final boolean isYouth = isYouth(datesOfBirth.get(defendantId.get()), hearingDay);
                    final Set<String> requiredCodes = isYouth ? youthCodes : adultCodes;
                    final boolean hasQualifyingNotification = PreprocessorHelper.anyShortCodeIn(lines, requiredCodes);
                    context = Optional.of(new SexualOffenceNotificationContext(
                            offenceId,
                            defendantNames.getOrDefault(defendantId.get(), "Unknown"),
                            isYouth,
                            hasQualifyingNotification));
                }
            }
        }
        return context;
    }

    /**
     * Resolves the single defendant charged with an offence from its result lines' {@code
     * defendantId} values. Returns empty when there are zero result lines (defendant unknown) or
     * more than one distinct {@code defendantId} (a joint offence, or inconsistent data) — this
     * rule fails safe by excluding the offence rather than guessing which defendant's age
     * applies.
     */
    private Optional<String> resolveSingleDefendant(final List<ResultLineDto> lines) {
        final Set<String> defendantIds = new LinkedHashSet<>();
        for (final ResultLineDto line : lines) {
            if (line.getDefendantId() != null) {
                defendantIds.add(line.getDefendantId());
            }
        }
        return defendantIds.size() == 1 ? Optional.of(defendantIds.iterator().next()) : Optional.empty();
    }

    /**
     * {@code true} when the defendant is under 18 at {@code hearingDay}; fails safe to {@code
     * false} (Adult) when either date is missing. Unlike {@code
     * AgeRestrictedImprisonmentPreprocessor}'s fail-safe (which avoids a false positive on a
     * blocking ERROR), both outcomes here are advisory WARNINGs, so the fail-safe direction is
     * simply the stricter, more common case (Adult, requiring only NORRR) rather than assuming an
     * unconfirmed youth-specific requirement.
     */
    private boolean isYouth(final LocalDate dateOfBirth, final LocalDate hearingDay) {
        return dateOfBirth != null && hearingDay != null
                && Period.between(dateOfBirth, hearingDay).getYears() < AGE_OF_MAJORITY;
    }

    private Map<String, LocalDate> buildDatesOfBirth(final DraftValidationRequest request) {
        final Map<String, LocalDate> datesOfBirth = new LinkedHashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto d : request.getDefendants()) {
                if (d.getDefendantId() != null) {
                    datesOfBirth.put(d.getDefendantId(), d.getDateOfBirth());
                }
            }
        }
        return datesOfBirth;
    }
}
