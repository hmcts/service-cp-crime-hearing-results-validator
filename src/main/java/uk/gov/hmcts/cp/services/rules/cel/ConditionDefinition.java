package uk.gov.hmcts.cp.services.rules.cel;

import lombok.Builder;

/**
 * YAML-backed definition of a single condition within a validation rule.
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
        ValidationLevel validationLevel) {
}
