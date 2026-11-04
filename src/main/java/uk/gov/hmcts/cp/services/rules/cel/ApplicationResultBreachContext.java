package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-offence context produced by {@link ApplicationResultOffencePreprocessor} for the
 * DR-APP-009 rule. One instance per offence carrying at least one breaching result line --
 * <em>not</em> one per breaching result-line occurrence -- so that two or more breaches on the
 * same offence consolidate into a single inline error naming all of them, comma-separated
 * (spec.md AC2A).
 *
 * @param offenceId the offence id this context represents
 * @param resultLabels this offence's breaching results' human-readable labels, de-duplicated and
 *                     in first-encounter order (falls back to the short code when a label is
 *                     blank/absent -- see research.md R4)
 * @param globalResultLabels the same de-duplicated, first-encounter-order label list, but across
 *                            every breaching offence in the current rule evaluation, comma-joined
 *                            -- identical across every context {@link ApplicationResultOffencePreprocessor}
 *                            produces for one request, so the page-level message resolves to the
 *                            same text for every offence and merges into a single entry (AC2B)
 * @param globalResultLabelCount the number of distinct labels in {@code globalResultLabels} --
 *                                exposed to CEL so DR-APP-009 can pick singular ("is an
 *                                application result") or plural ("are application results")
 *                                page-level wording
 * @param defendantId the representative defendant id for this offence -- the first breaching
 *                     result line's own {@code defendantId} (an offence is recorded against a
 *                     single defendant's case in this domain, so its breaching lines share one)
 * @param defendantName {@code "first last"}, falling back to whichever of first/last name is
 *                       present, or {@code ""} (never {@code null}, never a placeholder such as
 *                       {@code "Unknown"}) when the defendant cannot be resolved at all -- see
 *                       research.md R8 for why {@code ""} specifically
 */
public record ApplicationResultBreachContext(
        String offenceId,
        List<String> resultLabels,
        String globalResultLabels,
        int globalResultLabelCount,
        String defendantId,
        String defendantName
) implements RuleEvaluationContext {

    private static final String OFFENCE_ID_SET_NAME = "breachOffenceId";
    private static final String DEFENDANT_ID_SET_NAME = "defendantId";
    private static final String CALCULATED_VALUE_SET_NAME = "resultLabelByOffenceId";
    private static final String LABEL_SEPARATOR = ", ";

    /**
     * Returns the CEL variables this rule's conditions evaluate: {@code hasBreach} is always
     * {@code 1}, since a context is only ever constructed for an offence with at least one actual
     * breach -- the preprocessor does the branching (short-code membership, null-offence guard),
     * not CEL; {@code offenceResultLabelCount} (this offence's distinct labels) and
     * {@code globalResultLabelCount} (the hearing's) select the singular or plural inline and
     * page-level wording respectively.
     *
     * @return {@code {"hasBreach": 1L, "offenceResultLabelCount": <count>,
     *         "globalResultLabelCount": <count>}}
     */
    @Override
    public Map<String, Long> toCelContext() {
        return Map.of(
                "hasBreach", 1L,
                "offenceResultLabelCount", (long) resultLabels.size(),
                "globalResultLabelCount", (long) globalResultLabelCount);
    }

    /**
     * Returns the singleton offence-id list named by the YAML condition's
     * {@code affectedOffenceSet: "breachOffenceId"}.
     *
     * @param setName configured offence-id set name
     * @return singleton list containing this context's offence id
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
     * Returns the singleton offence-id list for this context, used by the message template
     * resolver for stable ordering.
     *
     * @return singleton list containing this context's offence id
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
     * @return singleton list containing this context's representative defendant id
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
     * Returns this offence's own breaching result labels, comma-joined, when both the set name
     * and offence id match this context's own values, per the YAML condition's
     * {@code calculatedValueSet: "resultLabelByOffenceId"}. Drives the inline
     * {@code messageTemplate}'s {@code ${resultLabel}} placeholder (AC2A).
     *
     * @param setName configured calculated-value set name
     * @param offenceId offence id to look up
     * @return this offence's comma-joined breaching labels, or {@code null} for any other offence
     *         id
     * @throws IllegalArgumentException if the set name is not {@code "resultLabelByOffenceId"}
     */
    @Override
    public String getCalculatedValue(final String setName, final String offenceId) {
        if (!CALCULATED_VALUE_SET_NAME.equals(setName)) {
            throw new IllegalArgumentException("Unknown calculated-value set: " + setName);
        }
        return this.offenceId.equals(offenceId) ? String.join(LABEL_SEPARATOR, resultLabels) : null;
    }

    /**
     * Returns the hearing-wide, comma-joined breaching label list, identical across every context
     * {@link ApplicationResultOffencePreprocessor} builds for the current rule evaluation. Drives
     * the page-level {@code errorMessageTemplate}'s {@code ${resultLabel}} placeholder when the
     * YAML condition sets {@code consolidatePageLevelError: true} (AC2B).
     *
     * @param setName configured calculated-value set name
     * @return the hearing-wide comma-joined breaching label list
     * @throws IllegalArgumentException if the set name is not {@code "resultLabelByOffenceId"}
     */
    @Override
    public String getGlobalCalculatedValue(final String setName) {
        if (!CALCULATED_VALUE_SET_NAME.equals(setName)) {
            throw new IllegalArgumentException("Unknown calculated-value set: " + setName);
        }
        return globalResultLabels;
    }
}
