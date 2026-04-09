package uk.gov.hmcts.cp.services.rules;

import java.util.List;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;

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
     * Returns the rule's priority for startup ordering. Implementations should override this to
     * return the value from their static definition without incurring a database call.
     *
     * @return priority value (lower runs first)
     */
    default int getPriority() {
        return getRuleDetail().getPriority();
    }

    /**
     * Evaluates the rule against the supplied draft request.
     *
     * @param request draft results payload under validation
     * @return zero or more issues produced by the rule
     */
    List<ValidationIssue> evaluate(DraftValidationRequest request);
}
