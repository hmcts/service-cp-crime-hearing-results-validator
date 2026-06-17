package uk.gov.hmcts.cp.services.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.feature.FeatureToggleService;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.ValidationIssueResult;
import uk.gov.hmcts.cp.services.rules.ValidationRule;
import uk.gov.hmcts.cp.services.rules.cel.MessageTemplateResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultValidationService}.
 */
class DefaultValidationServiceTest {

    private static final FeatureToggleService ALWAYS_ENABLED = featureName -> true;
    private static final MessageTemplateResolver RESOLVER =
            new MessageTemplateResolver(new OffenceDisplayHelper());

    private static final String MDC_VALIDATION_ID = "validationId";

    @AfterEach
    void clearMdc() {
        MDC.remove(MDC_VALIDATION_ID);
    }

    /**
     * Verifies the baseline response when no rules are configured: the request is valid, no issues
     * are returned, and the response metadata is still populated.
     */
    @Test
    void no_rules_should_return_valid_response() {
        DefaultValidationService service = new DefaultValidationService(List.of(), ALWAYS_ENABLED, RESOLVER);
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
                List.of(ValidationIssueResult.forError(
                        ValidationIssue.builder()
                                .ruleId("RULE-001")
                                .severity(ValidationIssue.SeverityEnum.ERROR)
                                .build(),
                        "Test error", null)));
        DefaultValidationService service = new DefaultValidationService(List.of(errorRule), ALWAYS_ENABLED, RESOLVER);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isFalse();
        assertThat(response.getErrors().getValidationIssues()).hasSize(1);
        assertThat(response.getErrors().getErrorMessages()).containsExactly("Test error");
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
                List.of(ValidationIssueResult.forWarning(
                        ValidationIssue.builder()
                                .ruleId("RULE-002")
                                .severity(ValidationIssue.SeverityEnum.WARNING)
                                .build())));
        DefaultValidationService service = new DefaultValidationService(List.of(warningRule), ALWAYS_ENABLED, RESOLVER);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getErrors().getValidationIssues()).isEmpty();
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
                List.of(ValidationIssueResult.forError(
                        ValidationIssue.builder()
                                .ruleId("RULE-001")
                                .severity(ValidationIssue.SeverityEnum.ERROR)
                                .build(),
                        "Error from rule 1", null)));
        ValidationRule rule2 = stubRule("RULE-002",
                List.of(ValidationIssueResult.forWarning(
                        ValidationIssue.builder()
                                .ruleId("RULE-002")
                                .severity(ValidationIssue.SeverityEnum.WARNING)
                                .build())));
        DefaultValidationService service = new DefaultValidationService(List.of(rule1, rule2), ALWAYS_ENABLED, RESOLVER);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isFalse();
        assertThat(response.getErrors().getValidationIssues()).hasSize(1);
        assertThat(response.getWarnings()).hasSize(1);
        assertThat(response.getRulesEvaluated()).containsExactly("RULE-001", "RULE-002");
    }

    /**
     * Verifies that each validation call receives a fresh generated validation id, even when the
     * same request is evaluated repeatedly.
     */
    @Test
    void validate_should_generate_unique_validation_ids() {
        DefaultValidationService service = new DefaultValidationService(List.of(), ALWAYS_ENABLED, RESOLVER);
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
                List.of(ValidationIssueResult.forWarning(
                        ValidationIssue.builder()
                                .ruleId("RULE-001")
                                .severity(ValidationIssue.SeverityEnum.WARNING)
                                .build())));
        ValidationRule throwingRule = stubRule("RULE-002", null);
        ValidationRule rule3 = stubRule("RULE-003",
                List.of(ValidationIssueResult.forError(
                        ValidationIssue.builder()
                                .ruleId("RULE-003")
                                .severity(ValidationIssue.SeverityEnum.ERROR)
                                .build(),
                        "Error from rule 3", null)));
        DefaultValidationService service = new DefaultValidationService(
                List.of(rule1, throwingRule, rule3), ALWAYS_ENABLED, RESOLVER);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getErrors().getValidationIssues()).hasSize(1);
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
                List.of(ValidationIssueResult.forWarning(
                        ValidationIssue.builder()
                                .ruleId("RULE-LOW")
                                .severity(ValidationIssue.SeverityEnum.WARNING)
                                .build())));
        ValidationRule highPriority = stubRule("RULE-HIGH", 100,
                List.of(ValidationIssueResult.forWarning(
                        ValidationIssue.builder()
                                .ruleId("RULE-HIGH")
                                .severity(ValidationIssue.SeverityEnum.WARNING)
                                .build())));
        ValidationRule medPriority = stubRule("RULE-MED", 500,
                List.of(ValidationIssueResult.forWarning(
                        ValidationIssue.builder()
                                .ruleId("RULE-MED")
                                .severity(ValidationIssue.SeverityEnum.WARNING)
                                .build())));

        // Simulate what ValidationRuleAutoConfiguration does: sort by priority
        List<ValidationRule> sorted = new ArrayList<>(List.of(lowPriority, highPriority, medPriority));
        sorted.sort(java.util.Comparator.comparingInt(r -> r.getRuleDetail().getPriority()));

        DefaultValidationService service = new DefaultValidationService(sorted, ALWAYS_ENABLED, RESOLVER);
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
                List.of(ValidationIssueResult.forError(
                        ValidationIssue.builder()
                                .ruleId("RULE-001")
                                .severity(ValidationIssue.SeverityEnum.ERROR)
                                .build(),
                        "Should not appear", null)));
        DefaultValidationService service = new DefaultValidationService(List.of(rule), disabled, RESOLVER);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getMode()).isEqualTo("disabled");
        assertThat(response.getValidationId()).startsWith("val-");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getErrors().getValidationIssues()).isEmpty();
        assertThat(response.getErrors().getErrorMessages()).isEmpty();
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
                List.of(ValidationIssueResult.forError(
                        ValidationIssue.builder()
                                .ruleId("RULE-001")
                                .severity(ValidationIssue.SeverityEnum.ERROR)
                                .build(),
                        "Error found", null)));
        DefaultValidationService service = new DefaultValidationService(List.of(rule), broken, RESOLVER);
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getMode()).isEqualTo("advisory");
        assertThat(response.getErrors().getValidationIssues()).hasSize(1);
        assertThat(response.getRulesEvaluated()).containsExactly("RULE-001");
    }

    /**
     * Verifies that a single defendant name in an error message is rendered as-is (no joining).
     */
    @Test
    void formatDefendantNames_single_name_should_appear_unjoined() {
        ValidationRule rule = stubRule("RULE-001",
                List.of(ValidationIssueResult.forError(
                        ValidationIssue.builder().ruleId("RULE-001")
                                .severity(ValidationIssue.SeverityEnum.ERROR).build(),
                        "Affects ${defendantNames}.", "Alice")));
        DraftValidationResponse response = new DefaultValidationService(
                List.of(rule), ALWAYS_ENABLED, RESOLVER).validate(minimalRequest());

        assertThat(response.getErrors().getErrorMessages()).containsExactly("Affects Alice.");
    }

    /**
     * Verifies two defendant names are joined with " and " and no comma.
     */
    @Test
    void formatDefendantNames_two_names_should_be_joined_with_and() {
        ValidationRule rule = stubRule("RULE-001",
                List.of(
                        ValidationIssueResult.forError(
                                ValidationIssue.builder().ruleId("RULE-001")
                                        .severity(ValidationIssue.SeverityEnum.ERROR).build(),
                                "Affects ${defendantNames}.", "Alice"),
                        ValidationIssueResult.forError(
                                ValidationIssue.builder().ruleId("RULE-001")
                                        .severity(ValidationIssue.SeverityEnum.ERROR).build(),
                                "Affects ${defendantNames}.", "Bob")));
        DraftValidationResponse response = new DefaultValidationService(
                List.of(rule), ALWAYS_ENABLED, RESOLVER).validate(minimalRequest());

        assertThat(response.getErrors().getErrorMessages()).containsExactly("Affects Alice and Bob.");
    }

    /**
     * Verifies three+ defendant names are comma-separated with a trailing " and ".
     */
    @Test
    void formatDefendantNames_three_names_should_be_comma_separated_with_and() {
        ValidationRule rule = stubRule("RULE-001",
                List.of(
                        ValidationIssueResult.forError(
                                ValidationIssue.builder().ruleId("RULE-001")
                                        .severity(ValidationIssue.SeverityEnum.ERROR).build(),
                                "Affects ${defendantNames}.", "Alice"),
                        ValidationIssueResult.forError(
                                ValidationIssue.builder().ruleId("RULE-001")
                                        .severity(ValidationIssue.SeverityEnum.ERROR).build(),
                                "Affects ${defendantNames}.", "Bob"),
                        ValidationIssueResult.forError(
                                ValidationIssue.builder().ruleId("RULE-001")
                                        .severity(ValidationIssue.SeverityEnum.ERROR).build(),
                                "Affects ${defendantNames}.", "Charlie")));
        DraftValidationResponse response = new DefaultValidationService(
                List.of(rule), ALWAYS_ENABLED, RESOLVER).validate(minimalRequest());

        assertThat(response.getErrors().getErrorMessages())
                .containsExactly("Affects Alice, Bob and Charlie.");
    }

    /**
     * Verifies that two conditions on the same rule, each with a distinct errorMessageTemplate,
     * each produce their own error message independently. Previously putIfAbsent keyed by ruleId
     * silently dropped the second template's message.
     */
    @Test
    void rule_with_two_different_errorMessageTemplates_should_produce_separate_error_messages() {
        ValidationRule rule = stubRule("RULE-001",
                List.of(
                        ValidationIssueResult.forError(
                                ValidationIssue.builder().ruleId("RULE-001")
                                        .severity(ValidationIssue.SeverityEnum.ERROR).build(),
                                "Condition A affects ${defendantNames}.", "Alice"),
                        ValidationIssueResult.forError(
                                ValidationIssue.builder().ruleId("RULE-001")
                                        .severity(ValidationIssue.SeverityEnum.ERROR).build(),
                                "Condition B affects ${defendantNames}.", "Bob")));
        DraftValidationResponse response = new DefaultValidationService(
                List.of(rule), ALWAYS_ENABLED, RESOLVER).validate(minimalRequest());

        assertThat(response.getErrors().getErrorMessages())
                .containsExactly("Condition A affects Alice.", "Condition B affects Bob.");
    }

    private static DraftValidationRequest minimalRequest() {
        return DraftValidationRequest.builder().hearingId("h1").build();
    }

    private static ValidationRule stubRule(String ruleId, List<ValidationIssueResult> results) {
        return stubRule(ruleId, 1000, results);
    }
     /* Verifies the generated validation id is visible in the MDC while rules evaluate (so issue
     * logs can carry it) and is removed once evaluation completes.
     */

    @Test
    void validate_should_expose_validationId_in_mdc_during_evaluation_and_remove_after() {
        AtomicReference<String> mdcDuringEvaluation = new AtomicReference<>();
        ValidationRule capturingRule = capturingRule("RULE-001", mdcDuringEvaluation);
        DefaultValidationService service = new DefaultValidationService(List.of(capturingRule), ALWAYS_ENABLED, RESOLVER);
        DraftValidationRequest request = DraftValidationRequest.builder().hearingId("h1").build();

        DraftValidationResponse response = service.validate(request);

        assertThat(mdcDuringEvaluation.get())
                .startsWith("val-")
                .isEqualTo(response.getValidationId());
        assertThat(MDC.get(MDC_VALIDATION_ID)).isNull();
    }

    /**
     * Verifies any pre-existing MDC validation id is restored after evaluation rather than removed.
     */
    @Test
    void validate_should_restore_previous_validationId_after_evaluation() {
        MDC.put(MDC_VALIDATION_ID, "pre-existing");
        DefaultValidationService service = new DefaultValidationService(List.of(), ALWAYS_ENABLED, RESOLVER);
        DraftValidationRequest request = DraftValidationRequest.builder().hearingId("h1").build();

        service.validate(request);

        assertThat(MDC.get(MDC_VALIDATION_ID)).isEqualTo("pre-existing");
    }

    /**
     * Verifies the MDC validation id is cleaned up even when a rule throws during evaluation.
     */
    @Test
    void validate_should_remove_validationId_from_mdc_even_when_rule_throws() {
        ValidationRule throwingRule = stubRule("RULE-001", null);
        DefaultValidationService service = new DefaultValidationService(List.of(throwingRule), ALWAYS_ENABLED, RESOLVER);
        DraftValidationRequest request = DraftValidationRequest.builder().hearingId("h1").build();

        service.validate(request);

        assertThat(MDC.get(MDC_VALIDATION_ID)).isNull();
    }

    private static ValidationRule capturingRule(String ruleId, AtomicReference<String> mdcHolder) {
        return new ValidationRule() {
            @Override
            public RuleDetailResponse getRuleDetail() {
                return RuleDetailResponse.builder()
                        .ruleId(ruleId)
                        .title("Capturing rule " + ruleId)
                        .priority(1000)
                        .severity(RuleDetailResponse.SeverityEnum.ERROR)
                        .enabled(true)
                        .build();
            }

            @Override
            public List<ValidationIssueResult> evaluate(DraftValidationRequest request) {
                mdcHolder.set(MDC.get(MDC_VALIDATION_ID));
                return List.of();
            }
        };
    }

    private static ValidationRule stubRule(String ruleId, int priority, List<ValidationIssueResult> results) {
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
            public List<ValidationIssueResult> evaluate(DraftValidationRequest request) {
                if (results == null) {
                    throw new RuntimeException("Simulated rule failure");
                }
                return results;
            }
        };
    }
}
