package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.openapi.model.UpdateRuleRequest;
import uk.gov.hmcts.cp.services.ValidationRulesService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ValidationRulesController}.
 */
@ExtendWith(MockitoExtension.class)
class ValidationRulesControllerTest {

    @Mock
    ValidationRulesService validationRulesService;

    @InjectMocks
    ValidationRulesController validationRulesController;

    /**
     * Covers the list-rules endpoint when the service returns a successful rule summary.
     */
    @Test
    void list_rules_should_delegate_to_service_and_return_ok() {
        RuleListResponse expected = RuleListResponse.builder()
                .count(1)
                .enabledCount(1)
                .rules(List.of())
                .build();
        when(validationRulesService.listRules()).thenReturn(expected);

        ResponseEntity<RuleListResponse> response =
                validationRulesController.listValidationRules("user1", "corr1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    /**
     * Covers the rule-detail endpoint when the requested rule exists and is returned successfully.
     */
    @Test
    void get_rule_by_id_should_delegate_to_service_and_return_ok() {
        RuleDetailResponse expected = RuleDetailResponse.builder()
                .ruleId("DR-SENT-001")
                .build();
        when(validationRulesService.getRuleById("DR-SENT-001")).thenReturn(expected);

        ResponseEntity<RuleDetailResponse> response =
                validationRulesController.getValidationRuleById("DR-SENT-001", "user1", "corr1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    /**
     * Covers the update endpoint delegating to the service and returning the updated rule detail.
     */
    @Test
    void updateValidationRule_should_delegate_to_service_and_return_ok() {
        UpdateRuleRequest request = new UpdateRuleRequest(false, null);
        RuleDetailResponse expected = RuleDetailResponse.builder()
                .ruleId("DR-SENT-002")
                .enabled(false)
                .build();
        when(validationRulesService.updateRule("DR-SENT-002", request, "user1")).thenReturn(expected);

        ResponseEntity<RuleDetailResponse> response =
                validationRulesController.updateValidationRule("DR-SENT-002", "user1", request, "corr1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        assertThat(response.getBody().getEnabled()).isFalse();
    }

    /**
     * Covers the update endpoint delegating a severity-only request to the service.
     */
    @Test
    void updateValidationRule_severityOnly_should_delegate_to_service_and_return_ok() {
        UpdateRuleRequest request = new UpdateRuleRequest(null, UpdateRuleRequest.SeverityEnum.WARNING);
        RuleDetailResponse expected = RuleDetailResponse.builder()
                .ruleId("DR-SENT-002")
                .severity(RuleDetailResponse.SeverityEnum.WARNING)
                .build();
        when(validationRulesService.updateRule("DR-SENT-002", request, "user1")).thenReturn(expected);

        ResponseEntity<RuleDetailResponse> response =
                validationRulesController.updateValidationRule("DR-SENT-002", "user1", request, "corr1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        assertThat(response.getBody().getSeverity()).isEqualTo(RuleDetailResponse.SeverityEnum.WARNING);
    }

    /**
     * Covers the update endpoint delegating a request that sets both fields to the service.
     */
    @Test
    void updateValidationRule_bothFields_should_delegate_to_service_and_return_ok() {
        UpdateRuleRequest request = new UpdateRuleRequest(false, UpdateRuleRequest.SeverityEnum.WARNING);
        RuleDetailResponse expected = RuleDetailResponse.builder()
                .ruleId("DR-SENT-002")
                .enabled(false)
                .severity(RuleDetailResponse.SeverityEnum.WARNING)
                .build();
        when(validationRulesService.updateRule("DR-SENT-002", request, "user1")).thenReturn(expected);

        ResponseEntity<RuleDetailResponse> response =
                validationRulesController.updateValidationRule("DR-SENT-002", "user1", request, "corr1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        assertThat(response.getBody().getEnabled()).isFalse();
        assertThat(response.getBody().getSeverity()).isEqualTo(RuleDetailResponse.SeverityEnum.WARNING);
    }
}
