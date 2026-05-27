package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Per-violation context produced by {@link CurfewPeriodPreprocessor}.
 *
 * <p>Each instance represents exactly one period-mismatch violation — one defendant, one offence,
 * one requirement type — with a pre-computed expected end date string for inclusion in the
 * rule message template via the {@code ${expectedEndDate}} placeholder.
 *
 * <p>CEL variable map always carries {@code violationCount = 1L}; the YAML condition
 * {@code violationCount > 0} is always true for any context that exists (contexts are only
 * created when a violation is detected).
 */
public record CurfewPeriodContext(
        String defendantId,
        String defendantName,
        String offenceId,
        long violationCount,
        String expectedEndDate
) implements RuleEvaluationContext {

    /**
     * Returns the CEL variable map containing {@code violationCount = 1L}.
     *
     * @return single-entry map consumed by the {@code violationCount > 0} CEL condition
     */
    @Override
    public Map<String, Long> toCelContext() {
        return Map.of("violationCount", violationCount);
    }

    /**
     * Returns the singleton offence-id list for this violation. Only the named set
     * {@code "violatedOffenceIds"} is recognised; any other name indicates a YAML
     * misconfiguration and will throw {@link IllegalArgumentException}.
     *
     * @param setName configured offence-id set name
     * @return singleton list containing the violated offence id
     * @throws IllegalArgumentException if {@code setName} is not {@code "violatedOffenceIds"}
     */
    @Override
    public List<String> getOffenceIdSet(final String setName) {
        if (!"violatedOffenceIds".equals(setName)) {
            throw new IllegalArgumentException("Unknown offence set for CurfewPeriodContext: " + setName);
        }
        return List.of(offenceId);
    }

    /**
     * Returns the defendant display name for use in message templates.
     *
     * @return defendant display name
     */
    @Override
    public String defendantName() {
        return defendantName;
    }

    /**
     * Returns all offence ids in this context — always the single violated offence.
     *
     * @return singleton list containing the violated offence id
     */
    @Override
    public List<String> allOffenceIds() {
        return List.of(offenceId);
    }

    /**
     * Returns the defendant id this context is anchored to. Non-null, enabling
     * {@link CelValidationRule} to populate {@code affectedDefendants} on the issued
     * {@code ValidationIssue}.
     *
     * @return defendant id; never {@code null}
     */
    @Override
    public String defendantId() {
        return defendantId;
    }

    /**
     * Returns a map containing the {@code expectedEndDate} key whose value is the
     * pre-computed correct end date in {@code dd/MM/yyyy} format. This map is consumed by
     * {@link MessageTemplateResolver} to resolve the {@code ${expectedEndDate}} placeholder
     * in message templates.
     *
     * @return map with {@code expectedEndDate} → formatted date string
     */
    @Override
    public Map<String, String> stringVariables() {
        return Map.of("expectedEndDate", expectedEndDate);
    }

    /**
     * Opts in to having {@code affectedDefendants} populated by {@link CelValidationRule} on
     * OFFENCE-level ERROR issues. Each {@code CurfewPeriodContext} is anchored to a specific
     * defendant, so per-defendant scoping on the issue is correct and required.
     *
     * @return {@code true} always
     */
    @Override
    public boolean populateAffectedDefendantsOnOffenceError() {
        return true;
    }
}
