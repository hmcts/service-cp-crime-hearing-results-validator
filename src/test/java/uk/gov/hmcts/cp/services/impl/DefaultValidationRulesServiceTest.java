package uk.gov.hmcts.cp.services.impl;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationRequestWithConvictions;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultValidationRulesService}.
 */
class DefaultValidationRulesServiceTest {

    /**
     * Verifies the list endpoint scenario where enabled and disabled rules are both returned and
     * the enabled count is derived from the rule metadata.
     */
    @Test
    void listRules_should_return_all_rules() {
        ValidationRule rule1 = stubRule("RULE-001", true);
        ValidationRule rule2 = stubRule("RULE-002", false);
        DefaultValidationRulesService service = new DefaultValidationRulesService(List.of(rule1, rule2));

        RuleListResponse response = service.listRules();

        assertThat(response.getCount()).isEqualTo(2);
        assertThat(response.getEnabledCount()).isEqualTo(1);
        assertThat(response.getRules()).hasSize(2);
    }

    /**
     * Verifies a known rule id is resolved to the corresponding rule detail.
     */
    @Test
    void getRuleById_should_return_matching_rule() {
        ValidationRule rule = stubRule("DR-SENT-002", true);
        DefaultValidationRulesService service = new DefaultValidationRulesService(List.of(rule));

        RuleDetailResponse response = service.getRuleById("DR-SENT-002");

        assertThat(response.getRuleId()).isEqualTo("DR-SENT-002");
        assertThat(response.getEnabled()).isTrue();
    }

    /**
     * Verifies an unknown rule id is translated into the not-found exception used by the API.
     */
    @Test
    void getRuleById_should_throw_404_when_not_found() {
        DefaultValidationRulesService service = new DefaultValidationRulesService(List.of());

        assertThatThrownBy(() -> service.getRuleById("UNKNOWN"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Rule not found");
    }

    private static ValidationRule stubRule(String ruleId, boolean enabled) {
        return new ValidationRule() {
            @Override
            public RuleDetailResponse getRuleDetail() {
                return RuleDetailResponse.builder()
                        .ruleId(ruleId)
                        .title("Test rule " + ruleId)
                        .description("Test description")
                        .priority(1000)
                        .severity(RuleDetailResponse.SeverityEnum.ERROR)
                        .enabled(enabled)
                        .build();
            }

            @Override
            public List<ValidationIssue> evaluate(ValidationRequestWithConvictions request) {
                return List.of();
            }
        };
    }
}
