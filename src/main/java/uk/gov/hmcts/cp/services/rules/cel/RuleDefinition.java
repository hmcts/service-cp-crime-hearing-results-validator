package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import lombok.Builder;

/**
 * YAML-backed definition of a validation rule and its CEL conditions.
 */
@Builder
public record RuleDefinition(
        String id,
        String title,
        String description,
        int priority,
        boolean enabled,
        PreprocessingDefinition preprocessing,
        List<ConditionDefinition> conditions) {
}
