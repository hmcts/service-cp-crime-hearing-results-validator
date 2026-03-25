package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.ValidationRulesApi;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.services.ValidationRulesService;

/**
 * Exposes read-only endpoints for listing validation rules and retrieving rule details.
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

        log.info("List validation rules for user={}", sanitize(cjsCppUid));
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

        log.info("Get validation rule {} for user={}", sanitize(ruleId), sanitize(cjsCppUid));
        return ResponseEntity.ok(validationRulesService.getRuleById(ruleId));
    }

    private static String sanitize(final String value) {
        return value == null ? "" : value.replaceAll("[\r\n]", "");
    }
}
