package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.ValidationRulesApi;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.openapi.model.UpdateRuleRequest;
import uk.gov.hmcts.cp.services.ValidationRulesService;

/**
 * Exposes endpoints for listing, retrieving, and updating validation rules.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ValidationRulesController implements ValidationRulesApi {

    private final ValidationRulesService validationRulesService;

    /**
     * Returns the currently registered validation rules and their runtime status.
     *
     * @param cjsCppUid authenticated user identifier from the request header
     * @param cppClientCorrelationId client-supplied correlation identifier
     * @return HTTP 200 response containing the rule list
     */
    @Override
    public ResponseEntity<RuleListResponse> listValidationRules(
            final String cjsCppUid,
            final String cppClientCorrelationId) {

        log.info("List validation rules request received");
        return ResponseEntity.ok(validationRulesService.listRules());
    }

    /**
     * Returns the detail for a single validation rule.
     *
     * @param ruleId identifier of the rule to fetch
     * @param cjsCppUid authenticated user identifier from the request header
     * @param cppClientCorrelationId client-supplied correlation identifier
     * @return HTTP 200 response containing the rule detail
     */
    @Override
    public ResponseEntity<RuleDetailResponse> getValidationRuleById(
            final String ruleId,
            final String cjsCppUid,
            final String cppClientCorrelationId) {

        log.info("Get validation rule by id request received");
        return ResponseEntity.ok(validationRulesService.getRuleById(ruleId));
    }

    /**
     * Partially updates a validation rule's enabled status and/or severity override.
     *
     * @param ruleId identifier of the rule to update
     * @param cjsCppUid authenticated user identifier from the request header
     * @param updateRuleRequest partial update payload
     * @param cppClientCorrelationId client-supplied correlation identifier
     * @return HTTP 200 with the updated rule detail
     */
    @Override
    public ResponseEntity<RuleDetailResponse> updateValidationRule(
            final String ruleId,
            final String cjsCppUid,
            final UpdateRuleRequest updateRuleRequest,
            final String cppClientCorrelationId) {

        log.info("Update validation rule request received for ruleId={}", ruleId);
        return ResponseEntity.ok(validationRulesService.updateRule(ruleId, updateRuleRequest, cjsCppUid));
    }
}
