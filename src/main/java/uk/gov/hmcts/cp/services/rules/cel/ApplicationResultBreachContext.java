package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-breach-occurrence context produced by {@link ApplicationResultOffencePreprocessor} for the
 * DR-APP-009 rule. One instance per {@code ResultLineDto} whose short code is in the
 * application-only set and whose {@code offenceId} is non-blank -- <em>not</em> one per offence
 * or per defendant, so that two breaches on the same offence (or two breaches sharing a
 * defendant across offences) each surface their own inline error (spec.md AC2).
 *
 * @param defendantId the breaching result line's own {@code defendantId}, taken as-is (no
 *                     master-defendant collapsing -- each breach is reported individually)
 * @param defendantName {@code "first last"}, falling back to whichever of first/last name is
 *                       present, or {@code ""} (never {@code null}, never a placeholder such as
 *                       {@code "Unknown"}) when the defendant cannot be resolved at all -- see
 *                       research.md R8 for why {@code ""} specifically
 * @param offenceId the breaching result line's {@code offenceId}
 * @param resultLabel the breaching result's human-readable label (falls back to its short code
 *                     when the label is blank/absent -- see research.md R4)
 */
public record ApplicationResultBreachContext(
        String defendantId,
        String defendantName,
        String offenceId,
        String resultLabel
) implements RuleEvaluationContext {

    private static final String OFFENCE_ID_SET_NAME = "breachOffenceId";
    private static final String DEFENDANT_ID_SET_NAME = "defendantId";
    private static final String CALCULATED_VALUE_SET_NAME = "resultLabelByOffenceId";

    /**
     * Returns the single CEL variable this rule's condition evaluates -- always {@code 1}, since
     * a context is only ever constructed for an actual breach; the preprocessor does the
     * branching (short-code membership, null-offence guard), not CEL.
     *
     * @return {@code {"hasBreach": 1L}}
     */
    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("hasBreach", 1L);
    }

    /**
     * Returns the singleton offence-id list named by the YAML condition's
     * {@code affectedOffenceSet: "breachOffenceId"}.
     *
     * @param setName configured offence-id set name
     * @return singleton list containing this breach's offence id
     * @throws IllegalArgumentException if the set name is not {@code "breachOffenceId"}
     */
    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (!OFFENCE_ID_SET_NAME.equals(setName)) {
            throw new IllegalArgumentException("Unknown offence set: " + setName);
        }
        return List.of(offenceId);
    }

    /**
     * Returns the singleton offence-id list for this breach, used by the message template
     * resolver for stable ordering.
     *
     * @return singleton list containing this breach's offence id
     */
    @Override
    public List<String> allOffenceIds() {
        return List.of(offenceId);
    }

    /**
     * Returns the singleton defendant-id list named by the YAML condition's
     * {@code affectedDefendantSet: "defendantId"}.
     *
     * @param setName configured defendant-id set name
     * @return singleton list containing this breach's defendant id
     * @throws IllegalArgumentException if the set name is not {@code "defendantId"}
     */
    @Override
    public List<String> getDefendantIdSet(final String setName) {
        if (!DEFENDANT_ID_SET_NAME.equals(setName)) {
            throw new IllegalArgumentException("Unknown defendant set: " + setName);
        }
        return List.of(defendantId);
    }

    /**
     * Returns this breach's result label when both the set name and offence id match this
     * context's own values, per the YAML condition's
     * {@code calculatedValueSet: "resultLabelByOffenceId"}.
     *
     * @param setName configured calculated-value set name
     * @param offenceId offence id to look up
     * @return the result label for this breach's own offence id, or {@code null} for any other
     *         offence id
     * @throws IllegalArgumentException if the set name is not {@code "resultLabelByOffenceId"}
     */
    @Override
    public String getCalculatedValue(final String setName, final String offenceId) {
        if (!CALCULATED_VALUE_SET_NAME.equals(setName)) {
            throw new IllegalArgumentException("Unknown calculated-value set: " + setName);
        }
        return this.offenceId.equals(offenceId) ? resultLabel : null;
    }
}
