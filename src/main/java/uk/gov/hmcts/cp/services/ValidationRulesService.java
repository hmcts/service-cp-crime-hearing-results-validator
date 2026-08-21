package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.openapi.model.UpdateRuleRequest;

/**
 * Provides access to validation rule metadata and runtime override management.
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

    /**
     * Partially updates a rule's enabled status and/or severity override in the database.
     * At least one of {@code enabled} or {@code severity} in the request must be non-null.
     *
     * @param ruleId    identifier of the rule to update
     * @param request   partial update payload — at least one field must be supplied
     * @param updatedBy caller identity written to the audit column
     * @return updated rule detail merging YAML metadata with the persisted override
     */
    RuleDetailResponse updateRule(String ruleId, UpdateRuleRequest request, String updatedBy);
}
