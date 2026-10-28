package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.buildDefendantNames;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperOrNull;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Per-offence preprocessor for the DR-BAIL-015 rule. Produces one {@link PreReleaseBailContext}
 * per offence where a conditional bail parent result (RC, RCBV, BAILREVC, REMCB, REMCBY, RCCCB,
 * CCIC, or CCSC) coexists with at least one pre-release bail condition result (PREBC, PRBCO,
 * PRBCP1–PRBCP8, PRBCS1–PRBCS3, PRBCSE1, PRBCSE2, PRBCT) on the same offence.
 *
 * <p>A three-pass algorithm is used: Pass 1 collects triggered offences; Pass 2 computes
 * hearing-wide flags ({@code hearingHasRemandAdult}, {@code hearingHasRemandYouth}) so that CEL
 * conditions can distinguish adult-only, youth-only, and mixed hearings; Pass 3 emits one
 * {@link PreReleaseBailContext} per triggered offence.
 */
@Slf4j
@Component
public class PreReleaseBailConditionPreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "pre-release-bail-condition";

    private static final int AGE_OF_MAJORITY = 18;

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    @SuppressWarnings({"PMD.OnlyOneReturn", "PMD.AvoidCatchingGenericException",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.LinguisticNaming"})
    // early-return on empty input reads clearer; catch is deliberate; PreReleaseBailContext is
    // constructed once per triggered offence; hasDob is a CEL-variable long (0/1), not a boolean.
    public Map<String, PreReleaseBailContext> preprocess(final DraftValidationRequest request,
                                                          final PreprocessingDefinition config) {
        if (request.getResultLines() == null || request.getResultLines().isEmpty()) {
            return Map.of();
        }

        final Set<String> remandCodes = upperSet(config.remandConditionalBailShortCodes());
        final Set<String> ccicCodes = upperSet(config.ccicShortCodes());
        final Set<String> ccscCodes = upperSet(config.ccscShortCodes());
        final Set<String> preRelCodes = upperSet(config.preReleaseBailConditionShortCodes());

        final Set<String> allParentCodes = new LinkedHashSet<>();
        allParentCodes.addAll(remandCodes);
        allParentCodes.addAll(ccicCodes);
        allParentCodes.addAll(ccscCodes);

        // Pass 1 — collect triggered offences
        final Map<String, String> parentCodeByOffence = new LinkedHashMap<>();
        final Set<String> preReleaseOffences = new LinkedHashSet<>();
        final Map<String, String> defendantByOffence = new LinkedHashMap<>();

        for (final ResultLineDto rl : request.getResultLines()) {
            try {
                collectResultLine(rl, allParentCodes, preRelCodes,
                        parentCodeByOffence, preReleaseOffences, defendantByOffence);
            } catch (RuntimeException e) {
                log.warn("Skipping result line while evaluating pre-release bail condition "
                        + "breaches ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            }
        }

        // offences that have both a conditional bail parent AND a pre-release child
        final Set<String> triggeredOffences = new LinkedHashSet<>(parentCodeByOffence.keySet());
        triggeredOffences.retainAll(preReleaseOffences);

        if (triggeredOffences.isEmpty()) {
            return Map.of();
        }

        final Map<String, LocalDate> dobByDefendant = buildDobMap(request);
        final Map<String, String> nameByDefendant = buildDefendantNames(request);
        final LocalDate hearingDay = request.getHearingDay();

        // Pass 2 — compute hearing-wide flags across all triggered offences
        boolean hearingHasRemandAdult = false;
        boolean hearingHasRemandYouth = false;

        for (final String offenceId : triggeredOffences) {
            final String parentCode = parentCodeByOffence.get(offenceId);
            if (remandCodes.contains(parentCode)) {
                final LocalDate dob = dobByDefendant.get(defendantByOffence.get(offenceId));
                if (dob != null && hearingDay != null) {
                    if (isAdult(dob, hearingDay)) {
                        hearingHasRemandAdult = true;
                    } else {
                        hearingHasRemandYouth = true;
                    }
                }
            }
        }

        // Pass 3 — emit one context per triggered offence
        final Map<String, PreReleaseBailContext> contexts = new LinkedHashMap<>();
        for (final String offenceId : triggeredOffences) {
            final String parentCode = parentCodeByOffence.get(offenceId);
            final String defendantId = defendantByOffence.get(offenceId);
            final LocalDate dob = dobByDefendant.get(defendantId);
            // Age can only be determined when both DOB and hearing date are present;
            // null hearing date routes to AC_NODOB (Error C) even when DOB is known.
            final long hasDob = (dob != null && hearingDay != null) ? 1L : 0L;
            final long adultFlag = hasDob == 1L && isAdult(dob, hearingDay) ? 1L : 0L;
            final long youthFlag = hasDob == 1L && !isAdult(dob, hearingDay) ? 1L : 0L;

            contexts.put(offenceId, new PreReleaseBailContext(
                    offenceId,
                    defendantId,
                    nameByDefendant.getOrDefault(defendantId, ""),
                    remandCodes.contains(parentCode) ? 1L : 0L,
                    ccicCodes.contains(parentCode) ? 1L : 0L,
                    ccscCodes.contains(parentCode) ? 1L : 0L,
                    adultFlag,
                    youthFlag,
                    hasDob,
                    hearingHasRemandAdult ? 1L : 0L,
                    hearingHasRemandYouth ? 1L : 0L));
        }
        return contexts;
    }

    @SuppressWarnings("PMD.OnlyOneReturn")
    private void collectResultLine(final ResultLineDto rl,
                                    final Set<String> allParentCodes,
                                    final Set<String> preRelCodes,
                                    final Map<String, String> parentCodeByOffence,
                                    final Set<String> preReleaseOffences,
                                    final Map<String, String> defendantByOffence) {
        final String offenceId = rl.getOffenceId();
        if (offenceId == null) {
            return;
        }
        final String upper = upperOrNull(rl.getShortCode());
        if (upper == null) {
            return;
        }
        if (allParentCodes.contains(upper)) {
            parentCodeByOffence.putIfAbsent(offenceId, upper);
        }
        if (preRelCodes.contains(upper)) {
            preReleaseOffences.add(offenceId);
        }
        defendantByOffence.putIfAbsent(offenceId, rl.getDefendantId());
    }

    private Map<String, LocalDate> buildDobMap(final DraftValidationRequest request) {
        final Map<String, LocalDate> dobMap = new LinkedHashMap<>();
        if (request.getDefendants() != null) {
            for (final DefendantDto d : request.getDefendants()) {
                dobMap.put(d.getDefendantId(), d.getDateOfBirth());
            }
        }
        return dobMap;
    }

    /** Returns true when dob is non-null, hearingDay is non-null, and defendant is ≥18. */
    @SuppressWarnings("PMD.OnlyOneReturn") // early-return on null-guard reads clearer here
    private boolean isAdult(final LocalDate dob, final LocalDate hearingDay) {
        if (dob == null || hearingDay == null) {
            return false;
        }
        return Period.between(dob, hearingDay).getYears() >= AGE_OF_MAJORITY;
    }
}
