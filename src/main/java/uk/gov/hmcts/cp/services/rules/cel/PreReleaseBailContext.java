package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link PreReleaseBailConditionPreprocessor} for the DR-BAIL-015
 * rule. One instance is emitted per offence where a conditional bail parent result (RC, RCBV,
 * BAILREVC, REMCB, REMCBY, RCCCB, CCIC, or CCSC) coexists with at least one pre-release bail
 * condition result (PREBC, PRBCO, PRBCP1–PRBCP8, PRBCS1–PRBCS3, PRBCSE1, PRBCSE2, PRBCT) on
 * the same offence.
 *
 * <p>Hearing-wide flags ({@code hearingHasRemandAdult}, {@code hearingHasRemandYouth}) are
 * pre-computed by the preprocessor's two-pass algorithm and baked into every context so that CEL
 * expressions can distinguish adult-only, youth-only, and mixed hearings without cross-context
 * visibility.
 *
 * @param offenceId              the triggered offence id (context key)
 * @param defendantId            the defendant id from the result line
 * @param defendantName          firstName + lastName from DefendantDto
 * @param isRemandBail           1 if parent short code is in remandConditionalBailShortCodes
 * @param isCcic                 1 if parent short code is CCIC
 * @param isCcsc                 1 if parent short code is CCSC
 * @param isAdult                1 if defendant is ≥18 on hearingDay
 * @param isYouth                1 if defendant is &lt;18 on hearingDay
 * @param hasDob                 1 if both defendantDateOfBirth and hearingDay are non-null
 *                               (age can only be determined when both are present; a known DOB
 *                               with a null hearingDay still produces 0, routing CEL to AC_NODOB)
 * @param hearingHasRemandAdult  hearing-wide: any triggered offence has remandBail parent + adult
 * @param hearingHasRemandYouth  hearing-wide: any triggered offence has remandBail parent + youth
 */
public record PreReleaseBailContext(
        String offenceId,
        String defendantId,
        String defendantName,
        long isRemandBail,
        long isCcic,
        long isCcsc,
        long isAdult,
        long isYouth,
        long hasDob,
        long hearingHasRemandAdult,
        long hearingHasRemandYouth
) implements RuleEvaluationContext {

    private static final String OFFENCE_SET_NAME = "triggeredOffenceId";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "isRemandBail", isRemandBail,
                "isCcic", isCcic,
                "isCcsc", isCcsc,
                "isAdult", isAdult,
                "isYouth", isYouth,
                "hasDob", hasDob,
                "hearingHasRemandAdult", hearingHasRemandAdult,
                "hearingHasRemandYouth", hearingHasRemandYouth);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (!OFFENCE_SET_NAME.equals(setName)) {
            throw new IllegalArgumentException("Unknown offence set: " + setName);
        }
        return List.of(offenceId);
    }

    @Override
    public List<String> allOffenceIds() {
        return List.of(offenceId);
    }
}
