package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Polymorphic context produced by a {@link ValidationPreprocessor} and consumed by
 * {@link CelValidationRule}. Each implementation exposes a CEL variable map and a set of
 * named offence-id lists referenced by rule conditions.
 *
 * <p>This is intentionally an open extension point — every new {@code ValidationPreprocessor}
 * brings its own context shape, and consumers only ever invoke interface methods on it (no
 * pattern matching). If exhaustive case analysis on context types is later introduced, sealing
 * the interface should be reconsidered then.
 */
public interface RuleEvaluationContext {

    /**
     * Returns the variable map consumed by CEL expressions. Keys are the variable names
     * referenced in YAML condition expressions; values are {@code Long} counts.
     *
     * @return CEL variable map for this context
     */
    Map<String, Long> toCelContext();

    /**
     * Returns the offence-id list named by a YAML condition's {@code affectedOffenceSet} field.
     *
     * @param setName configured offence-id set name
     * @return matching offence-id list
     * @throws IllegalArgumentException if the set name is unknown to this context
     */
    List<String> getOffenceIdSet(String setName);

    /**
     * Display name used by message templates for the {@code ${defendantName}} placeholder.
     *
     * @return defendant display name, or {@code null} when this context is not anchored to a
     *         single defendant
     */
    String defendantName();

    /**
     * Full set of offence ids represented by this context, used by the message template resolver
     * for stable ordering.
     *
     * @return offence ids in this context
     */
    List<String> allOffenceIds();

    /**
     * The defendant (or defendant-group) identifier this context is anchored to. Used when
     * building {@code affectedDefendants} for DEFENDANT-level conditions.
     *
     * @return defendant id, or {@code null} when this context is not anchored to a defendant
     */
    default String defendantId() {
        return null;
    }

    /**
     * Returns the defendant-id list named by a YAML condition's {@code affectedDefendantSet} field.
     * Mirrors {@link #getOffenceIdSet(String)} for DEFENDANT-level conditions.
     *
     * @param setName configured defendant-id set name
     * @return matching defendant-id list
     * @throws IllegalArgumentException if the set name is unknown to this context
     */
    default List<String> getDefendantIdSet(final String setName) {
        throw new IllegalArgumentException("Unknown defendant set: " + setName);
    }

    /**
     * Returns a per-offence computed value named by a YAML condition's {@code calculatedValueSet}
     * field (e.g. a calculated correct end date), used to expand a message-template placeholder.
     *
     * @param setName configured calculated-value set name
     * @param offenceId offence id to look up within that set
     * @return the computed value for this offence, or {@code null} if not present
     * @throws IllegalArgumentException if the set name is unknown to this context
     */
    default String getCalculatedValue(final String setName, final String offenceId) {
        throw new IllegalArgumentException("Unknown calculated-value set: " + setName);
    }

    /**
     * Returns a hearing-wide aggregate value named by a YAML condition's {@code calculatedValueSet}
     * field, used only when that condition sets {@code consolidatePageLevelError: true} (e.g. the
     * comma-joined, de-duplicated list of every breaching result label across the whole rule
     * evaluation, not just this context's own offence). Every triggered context of the same rule
     * evaluation is expected to return the identical string for a given {@code setName}, so their
     * page-level messages resolve to identical text and merge into a single entry via
     * {@code DefaultValidationService}'s existing {@code ruleId::errorMessage} bucketing.
     *
     * @param setName configured calculated-value set name
     * @return the hearing-wide aggregate value for that set
     * @throws IllegalArgumentException if the set name is unknown to this context
     */
    default String getGlobalCalculatedValue(final String setName) {
        throw new IllegalArgumentException("Unknown calculated-value set: " + setName);
    }
}
