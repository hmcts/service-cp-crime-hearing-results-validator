package uk.gov.hmcts.cp.services.rules;

import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;

import java.util.List;

public interface ValidationRule {

    RuleDetailResponse getRuleDetail();

    List<ValidationIssue> evaluate(DraftValidationRequest request);
}
