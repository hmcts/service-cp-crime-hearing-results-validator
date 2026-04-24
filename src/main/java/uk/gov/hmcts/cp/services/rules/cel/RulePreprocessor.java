package uk.gov.hmcts.cp.services.rules.cel;

import java.util.Map;
import uk.gov.hmcts.cp.openapi.model.ValidationRequestWithConvictions;

/**
 * Strategy interface for preprocessing a validation request into per-entry contexts
 * consumed by CEL rule conditions.
 */
public interface RulePreprocessor {

    /**
     * Preprocesses the validation request into a keyed map of rule contexts.
     *
     * @param request full validation request including conviction flags
     * @param config preprocessing configuration loaded from YAML
     * @return map of grouping key to derived context
     */
    Map<String, RuleContext> preprocess(ValidationRequestWithConvictions request,
                                        PreprocessingDefinition config);
}
