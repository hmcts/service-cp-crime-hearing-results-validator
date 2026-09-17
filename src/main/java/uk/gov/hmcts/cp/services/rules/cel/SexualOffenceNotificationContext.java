package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link SexualOffenceNotificationPreprocessor} for the sexual
 * offence notification-requirement rule (DR-SEX-008). One instance per offence that is both
 * convicted and classified as a relevant sexual offence ({@code misCode} "SEX"); offences that
 * don't meet both preconditions have no context entry at all.
 *
 * @param offenceId the single offence this context represents
 * @param defendantName display name of the offence's charged defendant, resolved via {@link
 *         PreprocessorHelper#buildFullName}
 * @param isYouth {@code true} when the charged defendant is under 18 at the hearing date;
 *         {@code false} (Adult) when 18+ or when date of birth is unavailable (fail-safe)
 * @param hasQualifyingNotification {@code true} when at least one result line on this offence
 *         carries the age-appropriate notification-requirement short code ({@code NORRR} for
 *         Adult, {@code NORRR} or {@code NORPGP} for Youth) — already resolved by the
 *         preprocessor using {@code isYouth}, so CEL only ever tests this single boolean
 */
public record SexualOffenceNotificationContext(
        String offenceId,
        String defendantName,
        boolean isYouth,
        boolean hasQualifyingNotification
) implements RuleEvaluationContext {

    private static final String OFFENCE_ID_SET = "offenceId";

    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "isYouth", isYouth ? 1L : 0L,
                "hasQualifyingNotification", hasQualifyingNotification ? 1L : 0L);
    }

    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (OFFENCE_ID_SET.equals(setName)) {
            return List.of(offenceId);
        }
        throw new IllegalArgumentException("Unknown offence set: " + setName);
    }

    @Override
    public List<String> allOffenceIds() {
        return List.of(offenceId);
    }
}
