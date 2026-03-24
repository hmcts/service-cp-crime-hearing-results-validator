package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;

/**
 * Provides read-only access to validation rule metadata exposed by the API.
 */
public interface ValidationRulesService {

    /**
     * Lists every validation rule known to the application.
     *
     * @return rule list response with counts and rule detail entries
     */
    RuleListResponse listRules();

    /**
     * Retrieves a single rule definition by identifier.
     *
     * @param ruleId identifier of the rule to fetch
     * @return rule detail for the matching rule
     */
    RuleDetailResponse getRuleById(String ruleId);
}
