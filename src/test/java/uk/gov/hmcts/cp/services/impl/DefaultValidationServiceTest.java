package uk.gov.hmcts.cp.services.impl;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultValidationServiceTest {

    @Test
    void no_rules_should_return_valid_response() {
        DefaultValidationService service = new DefaultValidationService(List.of());
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

    @Test
    void rule_with_error_should_return_invalid_response() {
        ValidationRule errorRule = stubRule("RULE-001",
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-001")
                        .severity(ValidationIssue.SeverityEnum.ERROR)
                        .message("Test error")
                        .build()));
        DefaultValidationService service = new DefaultValidationService(List.of(errorRule));
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isFalse();
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getRulesEvaluated()).containsExactly("RULE-001");
    }

    @Test
    void rule_with_warning_should_return_valid_response() {
        ValidationRule warningRule = stubRule("RULE-002",
                List.of(ValidationIssue.builder()
                        .ruleId("RULE-002")
                        .severity(ValidationIssue.SeverityEnum.WARNING)
                        .message("Test warning")
                        .build()));
        DefaultValidationService service = new DefaultValidationService(List.of(warningRule));
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getWarnings()).hasSize(1);
        assertThat(response.getRulesEvaluated()).containsExactly("RULE-002");
    }

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
        DefaultValidationService service = new DefaultValidationService(List.of(rule1, rule2));
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isFalse();
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getWarnings()).hasSize(1);
        assertThat(response.getRulesEvaluated()).containsExactly("RULE-001", "RULE-002");
    }

    @Test
    void validate_should_generate_unique_validation_ids() {
        DefaultValidationService service = new DefaultValidationService(List.of());
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        String id1 = service.validate(request).getValidationId();
        String id2 = service.validate(request).getValidationId();

        assertThat(id1).isNotEqualTo(id2);
    }

    private static ValidationRule stubRule(String ruleId, List<ValidationIssue> issues) {
        return new ValidationRule() {
            @Override
            public RuleDetailResponse getRuleDetail() {
                return RuleDetailResponse.builder()
                        .ruleId(ruleId)
                        .title("Test rule " + ruleId)
                        .description("Test description")
                        .priority(1000)
                        .severity(RuleDetailResponse.SeverityEnum.ERROR)
                        .enabled(true)
                        .build();
            }

            @Override
            public List<ValidationIssue> evaluate(DraftValidationRequest request) {
                return issues;
            }
        };
    }
}
