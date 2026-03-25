package uk.gov.hmcts.cp.services.rules.cel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
}
