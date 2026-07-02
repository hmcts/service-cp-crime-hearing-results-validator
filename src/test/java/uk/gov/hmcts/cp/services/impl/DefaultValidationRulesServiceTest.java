package uk.gov.hmcts.cp.services.impl;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.openapi.model.UpdateRuleRequest;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.ValidationIssueResult;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultValidationRulesService}.
 */
@ExtendWith(MockitoExtension.class)
class DefaultValidationRulesServiceTest {

    @Mock
    RuleOverrideService ruleOverrideService;

    /**
     * Verifies the list endpoint scenario where enabled and disabled rules are both returned and
     * the enabled count is derived from the rule metadata.
     */
    @Test
    void listRules_should_return_all_rules() {
        ValidationRule rule1 = stubRule("RULE-001", true);
        ValidationRule rule2 = stubRule("RULE-002", false);
        DefaultValidationRulesService service =
                new DefaultValidationRulesService(List.of(rule1, rule2), ruleOverrideService);

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
        DefaultValidationRulesService service =
                new DefaultValidationRulesService(List.of(rule), ruleOverrideService);

        RuleDetailResponse response = service.getRuleById("DR-SENT-002");

        assertThat(response.getRuleId()).isEqualTo("DR-SENT-002");
        assertThat(response.getEnabled()).isTrue();
    }

    /**
     * Verifies an unknown rule id is translated into the not-found exception used by the API.
     */
    @Test
    void getRuleById_should_throw_404_when_not_found() {
        DefaultValidationRulesService service =
                new DefaultValidationRulesService(List.of(), ruleOverrideService);

        assertThatThrownBy(() -> service.getRuleById("UNKNOWN"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Rule not found");
    }

    @Nested
    class UpdateRule {

        private DefaultValidationRulesService service;

        @BeforeEach
        void setUp() {
            ValidationRule existingRule = stubRule("DR-SENT-002", true);
            service = new DefaultValidationRulesService(List.of(existingRule), ruleOverrideService);
        }

        @Test
        void updateRule_withEnabledFalse_should_persist_and_return_detail() {
            stubExistingDbRow();
            UpdateRuleRequest request = new UpdateRuleRequest(false, null);

            RuleDetailResponse response = service.updateRule("DR-SENT-002", request, "test-user");

            ArgumentCaptor<ValidationRuleEntity> saved =
                    ArgumentCaptor.forClass(ValidationRuleEntity.class);
            verify(ruleOverrideService).saveOverride(saved.capture());
            assertThat(saved.getValue().isEnabled()).isFalse();
            assertThat(saved.getValue().getSeverity()).isEqualTo("ERROR");
            assertThat(saved.getValue().getUpdatedBy()).isEqualTo("test-user");
            assertThat(response.getRuleId()).isEqualTo("DR-SENT-002");
        }

        @Test
        void updateRule_withSeverityWarning_should_persist_and_return_detail() {
            stubExistingDbRow();
            UpdateRuleRequest request =
                    new UpdateRuleRequest(null, UpdateRuleRequest.SeverityEnum.WARNING);

            RuleDetailResponse response = service.updateRule("DR-SENT-002", request, "test-user");

            ArgumentCaptor<ValidationRuleEntity> saved =
                    ArgumentCaptor.forClass(ValidationRuleEntity.class);
            verify(ruleOverrideService).saveOverride(saved.capture());
            assertThat(saved.getValue().getSeverity()).isEqualTo("WARNING");
            assertThat(saved.getValue().isEnabled()).isTrue();
            assertThat(response.getRuleId()).isEqualTo("DR-SENT-002");
        }

        @Test
        void updateRule_withBothFields_should_persist_both() {
            stubExistingDbRow();
            UpdateRuleRequest request =
                    new UpdateRuleRequest(false, UpdateRuleRequest.SeverityEnum.WARNING);

            service.updateRule("DR-SENT-002", request, "test-user");

            ArgumentCaptor<ValidationRuleEntity> saved =
                    ArgumentCaptor.forClass(ValidationRuleEntity.class);
            verify(ruleOverrideService).saveOverride(saved.capture());
            assertThat(saved.getValue().isEnabled()).isFalse();
            assertThat(saved.getValue().getSeverity()).isEqualTo("WARNING");
        }

        @Test
        void updateRule_withNoFields_should_throw_400() {
            UpdateRuleRequest request = new UpdateRuleRequest(null, null);

            assertThatThrownBy(() -> service.updateRule("DR-SENT-002", request, "test-user"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        void updateRule_withUnknownRuleId_should_throw_404() {
            UpdateRuleRequest request = new UpdateRuleRequest(false, null);

            assertThatThrownBy(() -> service.updateRule("DR-UNKNOWN", request, "test-user"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        void updateRule_withNoExistingDbRow_should_seed_from_yaml_defaults() {
            when(ruleOverrideService.findOverride("DR-SENT-002")).thenReturn(Optional.empty());
            when(ruleOverrideService.saveOverride(any())).thenAnswer(inv -> inv.getArgument(0));
            UpdateRuleRequest request = new UpdateRuleRequest(false, null);

            service.updateRule("DR-SENT-002", request, "test-user");

            ArgumentCaptor<ValidationRuleEntity> saved =
                    ArgumentCaptor.forClass(ValidationRuleEntity.class);
            verify(ruleOverrideService).saveOverride(saved.capture());
            assertThat(saved.getValue().getId()).isEqualTo("DR-SENT-002");
            assertThat(saved.getValue().isEnabled()).isFalse();
            assertThat(saved.getValue().getSeverity()).isEqualTo("ERROR");
        }

        private void stubExistingDbRow() {
            when(ruleOverrideService.findOverride("DR-SENT-002")).thenReturn(Optional.of(
                    ValidationRuleEntity.builder()
                            .id("DR-SENT-002")
                            .enabled(true)
                            .severity("ERROR")
                            .build()));
            when(ruleOverrideService.saveOverride(any())).thenAnswer(inv -> inv.getArgument(0));
        }
    }

    private static ValidationRule stubRule(final String ruleId, final boolean enabled) {
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
            public List<ValidationIssueResult> evaluate(final DraftValidationRequest request) {
                return List.of();
            }
        };
    }
}
