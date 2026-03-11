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
public class ValidationRulesController implements ValidationRulesApi {

    private final ValidationRulesService validationRulesService;

    @Override
    public ResponseEntity<RuleListResponse> listValidationRules(
            String CJSCPPUID,
            String CPPCLIENTCORRELATIONID) {

        log.info("List validation rules for user={}", CJSCPPUID);
        return ResponseEntity.ok(validationRulesService.listRules());
    }

    @Override
    public ResponseEntity<RuleDetailResponse> getValidationRuleById(
            String ruleId,
            String CJSCPPUID,
            String CPPCLIENTCORRELATIONID) {

        log.info("Get validation rule {} for user={}", ruleId, CJSCPPUID);
        return ResponseEntity.ok(validationRulesService.getRuleById(ruleId));
    }
}
