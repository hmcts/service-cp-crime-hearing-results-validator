package uk.gov.hmcts.cp.services.rules.cel;

import lombok.Builder;

/**
 * YAML-backed definition of a single condition within a validation rule.
 *
 * @param calculatedValuePlaceholderName names the {@code ${...}} token that the calculated-value
 *                                       mechanism expands. When absent (the case for every rule
 *                                       shipped before DR-APP-009), defaults to
 *                                       {@code "calculatedEndDate"} in {@link CelValidationRule}
 *                                       -- the pre-existing hardcoded behaviour, unchanged.
 * @param consolidatePageLevelError when {@code true}, the page-level {@code errorMessageTemplate}'s
 *                                  calculated-value placeholder is resolved from
 *                                  {@link RuleEvaluationContext#getGlobalCalculatedValue} (a
 *                                  hearing-wide aggregate) instead of the per-context
 *                                  {@link RuleEvaluationContext#getCalculatedValue}, so every
 *                                  triggered context's page-level message is identical text and
 *                                  merges into one entry. Defaults to {@code false} when absent
 *                                  from the YAML -- the pre-existing per-context behaviour,
 *                                  unchanged for every rule shipped before this field.
 */
@Builder
public record ConditionDefinition(
        String id,
        String name,
        String expression,
        String severity,
        String messageTemplate,
        String errorMessageTemplate,
        String affectedOffenceSet,
        String affectedDefendantSet,
        String calculatedValueSet,
        String calculatedValuePlaceholderName,
        boolean consolidatePageLevelError,
        ValidationLevel validationLevel) {
}
