package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

@ExtendWith(MockitoExtension.class)
class CelValidationRuleOverrideTest {

    @Mock
    private ValidationRuleRepository ruleRepository;

    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();

    @Test
    void getRuleDetail_should_use_db_severity_override() {
        when(ruleRepository.findById("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder()
                        .id("DR-SENT-002")
                        .enabled(true)
                        .severity("WARNING")
                        .updatedAt(Instant.now())
                        .build()));

        CelValidationRule rule = new CelValidationRule(
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleRepository);
        RuleDetailResponse detail = rule.getRuleDetail();

        assertThat(detail.getSeverity()).isEqualTo(RuleDetailResponse.SeverityEnum.WARNING);
        assertThat(detail.getEnabled()).isTrue();
    }

    @Test
    void getRuleDetail_should_use_db_enabled_override() {
        when(ruleRepository.findById("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder()
                        .id("DR-SENT-002")
                        .enabled(false)
                        .severity("ERROR")
                        .updatedAt(Instant.now())
                        .build()));

        CelValidationRule rule = new CelValidationRule(
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleRepository);
        RuleDetailResponse detail = rule.getRuleDetail();

        assertThat(detail.getEnabled()).isFalse();
    }

    @Test
    void evaluate_should_skip_when_disabled_in_db() {
        when(ruleRepository.findById("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder()
                        .id("DR-SENT-002")
                        .enabled(false)
                        .severity("ERROR")
                        .updatedAt(Instant.now())
                        .build()));

        CelValidationRule rule = new CelValidationRule(
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleRepository);

        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(request);

        assertThat(issues).isEmpty();
    }

    @Test
    void evaluate_should_work_when_no_db_override_exists() {
        when(ruleRepository.findById("DR-SENT-002")).thenReturn(Optional.empty());

        CelValidationRule rule = new CelValidationRule(
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleRepository);

        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(request);

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
    }

    @Test
    void evaluate_should_fallback_to_yaml_when_db_throws_exception() {
        when(ruleRepository.findById("DR-SENT-002"))
                .thenThrow(new RuntimeException("DB connection failed"));

        CelValidationRule rule = new CelValidationRule(
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleRepository);

        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(request);

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
    }

    @Test
    void getRuleDetail_should_fallback_to_yaml_when_null_repository() {
        CelValidationRule rule = new CelValidationRule(
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(ValidationRuleRepository.class));
        RuleDetailResponse detail = rule.getRuleDetail();

        assertThat(detail.getRuleId()).isEqualTo("DR-SENT-002");
        assertThat(detail.getSeverity()).isEqualTo(RuleDetailResponse.SeverityEnum.ERROR);
        assertThat(detail.getEnabled()).isTrue();
    }
}
