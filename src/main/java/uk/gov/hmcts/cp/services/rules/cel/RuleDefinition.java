package uk.gov.hmcts.cp.services.rules.cel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * YAML-backed definition of a validation rule and its CEL conditions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDefinition {

    private String id;
    private String title;
    private String description;
    private int priority;
    private boolean enabled;
    private PreprocessingDefinition preprocessing;
    private List<ConditionDefinition> conditions;
}
