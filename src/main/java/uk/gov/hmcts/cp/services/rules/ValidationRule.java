package uk.gov.hmcts.cp.services.rules;

import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;

import java.util.List;

/**
 * Contract implemented by every validation rule participating in draft result evaluation.
 */
public interface ValidationRule {

    /**
     * Returns the API-facing metadata that describes the rule.
     *
     * @return rule detail for list/detail endpoints
     */
    RuleDetailResponse getRuleDetail();

    /**
     * Evaluates the rule against the supplied draft request.
     *
     * @param request draft results payload under validation
     * @return zero or more issues produced by the rule
     */
    List<ValidationIssue> evaluate(DraftValidationRequest request);
}
