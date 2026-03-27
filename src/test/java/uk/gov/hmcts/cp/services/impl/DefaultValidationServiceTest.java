package uk.gov.hmcts.cp.services.impl;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.feature.FeatureToggleService;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultValidationService}.
 */
class DefaultValidationServiceTest {

    private static final FeatureToggleService ALWAYS_ENABLED = featureName -> true;

    /**
     * Verifies the baseline response when no rules are configured: the request is valid, no issues
     * are returned, and the response metadata is still populated.
     */
    @Test
    void no_rules_should_return_valid_response() {
        DefaultValidationService service = new DefaultValidationService(List.of(), ALWAYS_ENABLED);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getValidationId()).startsWith("val-");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getMode()).isEqualTo("advisory");
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getRulesEvaluated()).isEmpty();
    }

    /**
     * Verifies that a single rule emitting an error makes the overall response invalid and places
     * the issue in the error collection.
     */
    @Test
    void rule_with_error_should_return_invalid_response() {
        ValidationRule errorRule = stubRule("RULE-001",
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-001")
                        .severity(ValidationIssue.SeverityEnum.ERROR)
                        .message("Test error")
                        .build()));
        DefaultValidationService service = new DefaultValidationService(List.of(errorRule), ALWAYS_ENABLED);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isFalse();
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getRulesEvaluated()).containsExactly("RULE-001");
    }

    /**
     * Verifies that warning-only rules keep the overall response valid while surfacing the warning
     * and recording the evaluated rule id.
     */
    @Test
    void rule_with_warning_should_return_valid_response() {
        ValidationRule warningRule = stubRule("RULE-002",
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-002")
                        .severity(ValidationIssue.SeverityEnum.WARNING)
                        .message("Test warning")
                        .build()));
        DefaultValidationService service = new DefaultValidationService(List.of(warningRule), ALWAYS_ENABLED);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getWarnings()).hasSize(1);
        assertThat(response.getRulesEvaluated()).containsExactly("RULE-002");
    }

    /**
     * Verifies that issues from multiple rules are aggregated into one response and that the
     * service preserves the rule evaluation order.
     */
    @Test
    void multiple_rules_should_be_aggregated() {
        ValidationRule rule1 = stubRule("RULE-001",
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-001")
                        .severity(ValidationIssue.SeverityEnum.ERROR)
                        .message("Error from rule 1")
                        .build()));
        ValidationRule rule2 = stubRule("RULE-002",
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-002")
                        .severity(ValidationIssue.SeverityEnum.WARNING)
                        .message("Warning from rule 2")
                        .build()));
        DefaultValidationService service = new DefaultValidationService(List.of(rule1, rule2), ALWAYS_ENABLED);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isFalse();
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getWarnings()).hasSize(1);
        assertThat(response.getRulesEvaluated()).containsExactly("RULE-001", "RULE-002");
    }

    /**
     * Verifies that each validation call receives a fresh generated validation id, even when the
     * same request is evaluated repeatedly.
     */
    @Test
    void validate_should_generate_unique_validation_ids() {
        DefaultValidationService service = new DefaultValidationService(List.of(), ALWAYS_ENABLED);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        String id1 = service.validate(request).getValidationId();
        String id2 = service.validate(request).getValidationId();

        assertThat(id1).isNotEqualTo(id2);
    }

    /**
     * Verifies that an exception thrown by one rule does not prevent subsequent rules from running.
     */
    @Test
    void rule_that_throws_should_not_prevent_other_rules() {
        ValidationRule rule1 = stubRule("RULE-001",
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-001")
                        .severity(ValidationIssue.SeverityEnum.WARNING)
                        .message("Warning from rule 1")
                        .build()));
        ValidationRule throwingRule = stubRule("RULE-002", null);
        ValidationRule rule3 = stubRule("RULE-003",
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-003")
                        .severity(ValidationIssue.SeverityEnum.ERROR)
                        .message("Error from rule 3")
                        .build()));
        DefaultValidationService service = new DefaultValidationService(
                List.of(rule1, throwingRule, rule3), ALWAYS_ENABLED);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getWarnings()).hasSize(1);
        assertThat(response.getRulesEvaluated())
                .containsExactly("RULE-001", "RULE-003")
                .doesNotContain("RULE-002");
    }

    /**
     * Verifies that rules are evaluated in priority order (lower number = higher priority).
     * This tests the contract: if rules are sorted before injection, the service preserves that order.
     */
    @Test
    void rules_should_execute_in_provided_order() {
        ValidationRule lowPriority = stubRule("RULE-LOW", 1000,
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-LOW")
                        .severity(ValidationIssue.SeverityEnum.WARNING)
                        .message("Low priority")
                        .build()));
        ValidationRule highPriority = stubRule("RULE-HIGH", 100,
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-HIGH")
                        .severity(ValidationIssue.SeverityEnum.WARNING)
                        .message("High priority")
                        .build()));
        ValidationRule medPriority = stubRule("RULE-MED", 500,
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-MED")
                        .severity(ValidationIssue.SeverityEnum.WARNING)
                        .message("Med priority")
                        .build()));

        // Simulate what ValidationRuleAutoConfiguration does: sort by priority
        List<ValidationRule> sorted = new ArrayList<>(List.of(lowPriority, highPriority, medPriority));
        sorted.sort(java.util.Comparator.comparingInt(r -> r.getRuleDetail().getPriority()));

        DefaultValidationService service = new DefaultValidationService(sorted, ALWAYS_ENABLED);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getRulesEvaluated())
                .containsExactly("RULE-HIGH", "RULE-MED", "RULE-LOW");
    }

    /**
     * Verifies that when the feature toggle returns disabled, the service returns an immediate
     * success response without evaluating any rules.
     */
    @Test
    void validate_returns_disabled_response_when_feature_disabled() {
        FeatureToggleService disabled = featureName -> false;
        ValidationRule rule = stubRule("RULE-001",
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-001")
                        .severity(ValidationIssue.SeverityEnum.ERROR)
                        .message("Should not appear")
                        .build()));
        DefaultValidationService service = new DefaultValidationService(List.of(rule), disabled);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getMode()).isEqualTo("disabled");
        assertThat(response.getValidationId()).startsWith("val-");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getRulesEvaluated()).isEmpty();
        assertThat(response.getProcessingTimeMs()).isZero();
    }

    /**
     * Verifies that when the feature toggle throws an exception, the service proceeds with
     * normal validation (fail-open defence-in-depth).
     */
    @Test
    void validate_runs_rules_when_toggle_check_throws() {
        FeatureToggleService broken = featureName -> { throw new RuntimeException("Toggle broken"); };
        ValidationRule rule = stubRule("RULE-001",
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-001")
                        .severity(ValidationIssue.SeverityEnum.ERROR)
                        .message("Error found")
                        .build()));
        DefaultValidationService service = new DefaultValidationService(List.of(rule), broken);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getMode()).isEqualTo("advisory");
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getRulesEvaluated()).containsExactly("RULE-001");
    }

    private static ValidationRule stubRule(String ruleId, List<ValidationIssue> issues) {
        return stubRule(ruleId, 1000, issues);
    }

    private static ValidationRule stubRule(String ruleId, int priority, List<ValidationIssue> issues) {
        return new ValidationRule() {
            @Override
            public RuleDetailResponse getRuleDetail() {
                return RuleDetailResponse.builder()
                        .ruleId(ruleId)
                        .title("Test rule " + ruleId)
                        .description("Test description")
                        .priority(priority)
                        .severity(RuleDetailResponse.SeverityEnum.ERROR)
                        .enabled(true)
                        .build();
            }

            @Override
            public List<ValidationIssue> evaluate(DraftValidationRequest request) {
                if (issues == null) {
                    throw new RuntimeException("Simulated rule failure");
                }
                return issues;
            }
        };
    }
}
