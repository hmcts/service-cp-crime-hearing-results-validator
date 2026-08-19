package uk.gov.hmcts.cp.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.exceptions.InvalidRuleUpdateException;
import uk.gov.hmcts.cp.exceptions.RuleNotFoundException;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.RuleListResponse;
import uk.gov.hmcts.cp.openapi.model.UpdateRuleRequest;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.ValidationIssueResult;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

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
        ValidationRule rule = stubRule("DR-SENT-001", true);
        DefaultValidationRulesService service =
                new DefaultValidationRulesService(List.of(rule), ruleOverrideService);

        RuleDetailResponse response = service.getRuleById("DR-SENT-001");

        assertThat(response.getRuleId()).isEqualTo("DR-SENT-001");
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
                .isInstanceOf(RuleNotFoundException.class)
                .hasMessageContaining("Rule not found: UNKNOWN");
    }

    /**
     * {@code updateRule} now applies the PATCH via a single atomic upsert
     * ({@link RuleOverrideService#applyPartialUpdate}) rather than a read-modify-write against a
     * separately-fetched entity, so there is no longer an "existing row" vs. "no existing row"
     * branch at this layer — the database's {@code ON CONFLICT} handles that distinction. These
     * tests verify the service passes the right override/default arguments through; the atomic
     * merge behaviour itself is covered against a real database in
     * {@code ValidationRuleRepositoryIntegrationTest}.
     */
    @Nested
    class UpdateRule {

        private DefaultValidationRulesService service;

        @BeforeEach
        void setUp() {
            ValidationRule existingRule = stubRule("DR-SENT-001", true);
            service = new DefaultValidationRulesService(List.of(existingRule), ruleOverrideService);
        }

        @Test
        void updateRule_withEnabledFalse_should_apply_enabled_override_and_return_detail() {
            UpdateRuleRequest request = new UpdateRuleRequest(false, null);

            RuleDetailResponse response = service.updateRule("DR-SENT-001", request, "test-user");

            verify(ruleOverrideService).applyPartialUpdate(
                    eq("DR-SENT-001"), eq(Boolean.FALSE), isNull(),
                    eq(true), eq("ERROR"), any(Instant.class), eq("test-user"));
            assertThat(response.getRuleId()).isEqualTo("DR-SENT-001");
        }

        @Test
        void updateRule_withSeverityWarning_should_apply_severity_override_and_return_detail() {
            UpdateRuleRequest request =
                    new UpdateRuleRequest(null, UpdateRuleRequest.SeverityEnum.WARNING);

            RuleDetailResponse response = service.updateRule("DR-SENT-001", request, "test-user");

            verify(ruleOverrideService).applyPartialUpdate(
                    eq("DR-SENT-001"), isNull(), eq("WARNING"),
                    eq(true), eq("ERROR"), any(Instant.class), eq("test-user"));
            assertThat(response.getRuleId()).isEqualTo("DR-SENT-001");
        }

        @Test
        void updateRule_withBothFields_should_apply_both_overrides() {
            UpdateRuleRequest request =
                    new UpdateRuleRequest(false, UpdateRuleRequest.SeverityEnum.WARNING);

            service.updateRule("DR-SENT-001", request, "test-user");

            verify(ruleOverrideService).applyPartialUpdate(
                    eq("DR-SENT-001"), eq(Boolean.FALSE), eq("WARNING"),
                    eq(true), eq("ERROR"), any(Instant.class), eq("test-user"));
        }

        @Test
        void updateRule_withNoFields_should_throw_400() {
            UpdateRuleRequest request = new UpdateRuleRequest(null, null);

            assertThatThrownBy(() -> service.updateRule("DR-SENT-001", request, "test-user"))
                    .isInstanceOf(InvalidRuleUpdateException.class)
                    .hasMessageContaining("At least one of 'enabled' or 'severity' must be provided");
            verifyNoInteractions(ruleOverrideService);
        }

        @Test
        void updateRule_withUnknownRuleId_should_throw_404() {
            UpdateRuleRequest request = new UpdateRuleRequest(false, null);

            assertThatThrownBy(() -> service.updateRule("DR-UNKNOWN", request, "test-user"))
                    .isInstanceOf(RuleNotFoundException.class)
                    .hasMessageContaining("Rule not found: DR-UNKNOWN");
            verifyNoInteractions(ruleOverrideService);
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
