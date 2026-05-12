package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.ValidationRule;
import uk.gov.hmcts.cp.services.rules.cel.CelExpressionEvaluator;
import uk.gov.hmcts.cp.services.rules.cel.CustodialPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.MessageTemplateResolver;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for YAML-driven validation rule discovery.
 */
@Disabled
class ValidationRuleAutoConfigurationTest {

    private final ValidationRuleAutoConfiguration config = new ValidationRuleAutoConfiguration();

    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();

    /**
     * Verifies the configuration discovers the bundled DR-SENT-002 YAML rule.
     */
    @Test
    void should_discover_DR_SENT_002_rule() throws IOException {
        List<ValidationRule> rules = config.validationRules(
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(RuleOverrideService.class));

        assertThat(rules).isNotEmpty();
        assertThat(rules).anyMatch(r -> "DR-SENT-002".equals(r.getRuleDetail().getRuleId()));
    }

    /**
     * Verifies each YAML file contributes exactly one validation rule instance.
     */
    @Test
    void should_create_one_rule_per_yaml_file() throws IOException {
        List<ValidationRule> rules = config.validationRules(
                new CustodialPreprocessor(),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(RuleOverrideService.class));

        assertThat(rules).hasSize(1);
    }
}
