package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.ValidationRulesApi;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.services.ValidationRulesService;

@RestController
@RequiredArgsConstructor
@Slf4j
/**
 * Exposes read-only endpoints for listing validation rules and retrieving rule details.
 */
public class ValidationRulesController implements ValidationRulesApi {

    private final ValidationRulesService validationRulesService;

    /**
     * Returns the currently registered validation rules and their runtime status.
     *
     * @param CJSCPPUID authenticated user identifier from the request header
     * @param CPPCLIENTCORRELATIONID client-supplied correlation identifier
     * @return HTTP 200 response containing the rule list
     */
    @Override
    public ResponseEntity<RuleListResponse> listValidationRules(
            String CJSCPPUID,
            String CPPCLIENTCORRELATIONID) {

        log.info("List validation rules for user={}", CJSCPPUID);
        return ResponseEntity.ok(validationRulesService.listRules());
    }

    /**
     * Returns the detail for a single validation rule.
     *
     * @param ruleId identifier of the rule to fetch
     * @param CJSCPPUID authenticated user identifier from the request header
     * @param CPPCLIENTCORRELATIONID client-supplied correlation identifier
     * @return HTTP 200 response containing the rule detail
     */
    @Override
    public ResponseEntity<RuleDetailResponse> getValidationRuleById(
            String ruleId,
            String CJSCPPUID,
            String CPPCLIENTCORRELATIONID) {

        log.info("Get validation rule {} for user={}", ruleId, CJSCPPUID);
        return ResponseEntity.ok(validationRulesService.getRuleById(ruleId));
    }
}
