package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * YAML-backed preprocessing configuration for a CEL validation rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreprocessingDefinition {

    private String type;
    private List<String> filterShortCodes;
    private String groupBy;
    private int skipWhenGroupCount;
    @Builder.Default
    private Map<String, String> requiredLabel = new java.util.HashMap<>();
}
