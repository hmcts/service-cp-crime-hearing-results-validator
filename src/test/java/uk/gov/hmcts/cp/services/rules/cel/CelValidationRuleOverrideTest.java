package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.wrap;

/**
 * Unit tests for database-backed overrides applied to {@link CelValidationRule}.
 */
@ExtendWith(MockitoExtension.class)
class CelValidationRuleOverrideTest {

    @Mock
    private RuleOverrideService ruleOverrideService;

    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();

    /**
     * Verifies that a persisted severity override changes the rule metadata exposed to clients
     * without disabling the rule.
     */
    @Test
    void getRuleDetail_should_use_db_severity_override() {
        when(ruleOverrideService.findOverride("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder()
                        .id("DR-SENT-002")
                        .enabled(true)
                        .severity("WARNING")
                        .updatedAt(Instant.now())
                        .build()));

        CelValidationRule rule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleOverrideService);
        RuleDetailResponse detail = rule.getRuleDetail();

        assertThat(detail.getSeverity()).isEqualTo(RuleDetailResponse.SeverityEnum.WARNING);
        assertThat(detail.getEnabled()).isTrue();
    }

    /**
     * Verifies that a persisted enabled flag override can disable the rule in its exposed metadata.
     */
    @Test
    void getRuleDetail_should_use_db_enabled_override() {
        when(ruleOverrideService.findOverride("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder()
                        .id("DR-SENT-002")
                        .enabled(false)
                        .severity("ERROR")
                        .updatedAt(Instant.now())
                        .build()));

        CelValidationRule rule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleOverrideService);
        RuleDetailResponse detail = rule.getRuleDetail();

        assertThat(detail.getEnabled()).isFalse();
    }

    /**
     * Verifies that evaluation is skipped entirely when the database override disables the rule.
     */
    @Test
    void evaluate_should_skip_when_disabled_in_db() {
        when(ruleOverrideService.findOverride("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder()
                        .id("DR-SENT-002")
                        .enabled(false)
                        .severity("ERROR")
                        .updatedAt(Instant.now())
                        .build()));

        CelValidationRule rule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleOverrideService);

        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).isEmpty();
    }

    /**
     * Verifies that the YAML definition is used unchanged when no database override exists and the
     * AC2 scenario still produces an error.
     */
    @Test
    void evaluate_should_work_when_no_db_override_exists() {
        when(ruleOverrideService.findOverride("DR-SENT-002")).thenReturn(Optional.empty());

        CelValidationRule rule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleOverrideService);

        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
    }

    /**
     * Verifies the same YAML fallback path when override lookup returns no value, matching the
     * service behaviour used after repository failures are swallowed upstream.
     */
    @Test
    void evaluate_should_fallback_to_yaml_when_db_throws_exception() {
        when(ruleOverrideService.findOverride("DR-SENT-002"))
                .thenReturn(Optional.empty());

        CelValidationRule rule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleOverrideService);

        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
    }

    /**
     * Verifies that DB severity override acts as a ceiling: an ERROR condition in YAML is capped
     * to WARNING when the DB override severity is WARNING.
     */
    @Test
    void evaluate_should_cap_error_to_warning_when_db_severity_is_warning() {
        when(ruleOverrideService.findOverride("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder()
                        .id("DR-SENT-002")
                        .enabled(true)
                        .severity("WARNING")
                        .updatedAt(Instant.now())
                        .build()));

        CelValidationRule rule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleOverrideService);

        // AC2 scenario: multiple custodial offences with no concurrent/consecutive info -> ERROR in YAML
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).isNotEmpty();
        assertThat(issues).allSatisfy(issue ->
                assertThat(issue.getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING));
    }

    /**
     * Verifies that a DB override of ERROR does not change an ERROR condition — it stays ERROR.
     */
    @Test
    void evaluate_should_keep_error_when_db_severity_is_error() {
        when(ruleOverrideService.findOverride("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder()
                        .id("DR-SENT-002")
                        .enabled(true)
                        .severity("ERROR")
                        .updatedAt(Instant.now())
                        .build()));

        CelValidationRule rule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleOverrideService);

        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).isNotEmpty();
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
    }

    /**
     * Verifies that a malformed DB severity value (e.g. a typo) falls back to YAML severity
     * rather than crashing with an IllegalArgumentException.
     */
    @Test
    void evaluate_should_fallback_to_yaml_severity_when_db_value_is_malformed() {
        when(ruleOverrideService.findOverride("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder()
                        .id("DR-SENT-002")
                        .enabled(true)
                        .severity("Warn")
                        .updatedAt(Instant.now())
                        .build()));

        CelValidationRule rule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleOverrideService);

        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).isNotEmpty();
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
    }

    /**
     * Verifies that getRuleDetail does not crash when the DB contains an invalid severity string.
     */
    @Test
    void getRuleDetail_should_fallback_to_error_when_db_severity_is_malformed() {
        when(ruleOverrideService.findOverride("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder()
                        .id("DR-SENT-002")
                        .enabled(true)
                        .severity("Warn")
                        .updatedAt(Instant.now())
                        .build()));

        CelValidationRule rule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                ruleOverrideService);

        RuleDetailResponse detail = rule.getRuleDetail();

        assertThat(detail.getSeverity()).isEqualTo(RuleDetailResponse.SeverityEnum.ERROR);
    }

    /**
     * Verifies that rule metadata falls back to the YAML defaults when no override is supplied.
     */
    @Test
    void getRuleDetail_should_fallback_to_yaml_when_no_override() {
        CelValidationRule rule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(RuleOverrideService.class));
        RuleDetailResponse detail = rule.getRuleDetail();

        assertThat(detail.getRuleId()).isEqualTo("DR-SENT-002");
        assertThat(detail.getSeverity()).isEqualTo(RuleDetailResponse.SeverityEnum.ERROR);
        assertThat(detail.getEnabled()).isTrue();
    }
}
