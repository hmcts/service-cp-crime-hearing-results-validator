package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;

public interface ValidationRulesService {

    RuleListResponse listRules();

    RuleDetailResponse getRuleById(String ruleId);
}
