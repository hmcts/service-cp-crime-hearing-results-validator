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
        ValidationLevel validationLevel) {
}
