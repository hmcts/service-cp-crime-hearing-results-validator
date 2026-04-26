package uk.gov.hmcts.cp.services.rules.cel;

import java.util.Map;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;

/**
 * Transforms a {@link DraftValidationRequest} into the per-context inputs consumed by CEL
 * conditions. Implementations are selected at runtime by the YAML rule's
 * {@code preprocessing.type} field via {@link PreprocessorRegistry}.
 */
public interface ValidationPreprocessor {

    /**
     * Qualifier matching the YAML {@code preprocessing.type} field. Must be unique across all
     * registered preprocessors; duplicates fail fast at startup.
     *
     * @return the preprocessor's YAML qualifier
     */
    String type();

    /**
     * Produces zero or more contexts from the request. Map keys are opaque (e.g. defendant id,
     * offence id) and used only to differentiate contexts within a single rule evaluation.
     *
     * @param request draft validation request being evaluated
     * @param config preprocessing configuration loaded from YAML
     * @return map of grouping key to derived context
     */
    Map<String, ? extends RuleEvaluationContext> preprocess(
            DraftValidationRequest request,
            PreprocessingDefinition config);
}
