package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.services.impl.MockValidationRulesService;

import static org.assertj.core.api.Assertions.assertThat;

class MockValidationRulesServiceTest {

    private final MockValidationRulesService service = new MockValidationRulesService();

    @Test
    void list_rules_should_return_one_mock_rule() {
        RuleListResponse response = service.listRules();

        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getEnabledCount()).isEqualTo(1);
        assertThat(response.getRules()).hasSize(1);
        assertThat(response.getRules().getFirst().getRuleId()).isEqualTo("DR-SENT-001");
    }

    @Test
    void get_rule_by_id_should_return_rule_with_matching_id() {
        RuleDetailResponse response = service.getRuleById("DR-CUST-006");

        assertThat(response.getRuleId()).isEqualTo("DR-CUST-006");
        assertThat(response.getTitle()).isNotBlank();
        assertThat(response.getEnabled()).isTrue();
        assertThat(response.getSeverity()).isEqualTo(RuleDetailResponse.SeverityEnum.ERROR);
    }
}
