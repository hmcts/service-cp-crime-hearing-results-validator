package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.ValidationRule;
import uk.gov.hmcts.cp.services.rules.cel.CelExpressionEvaluator;
import uk.gov.hmcts.cp.services.rules.cel.CustodialPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.DisqualificationExtendedTestPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.MessageTemplateResolver;
import uk.gov.hmcts.cp.services.rules.cel.PreprocessorRegistry;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for YAML-driven validation rule discovery.
 */
class ValidationRuleAutoConfigurationTest {

    private final ValidationRuleAutoConfiguration config = new ValidationRuleAutoConfiguration();

    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();

    private final PreprocessorRegistry preprocessorRegistry = new PreprocessorRegistry(List.of(
            new CustodialPreprocessor(),
            new DisqualificationExtendedTestPreprocessor()));

    /**
     * Verifies the configuration discovers the bundled DR-SENT-002 YAML rule.
     */
    @Test
    void should_discover_DR_SENT_002_rule() throws IOException {
        List<ValidationRule> rules = config.validationRules(
                preprocessorRegistry,
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
                preprocessorRegistry,
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(RuleOverrideService.class));

        assertThat(rules).hasSize(2);
        assertThat(rules)
                .extracting(r -> r.getRuleDetail().getRuleId())
                .containsExactlyInAnyOrder("DR-SENT-002", "DR-DISQ-001");
    }

    /**
     * Verifies the bean factory propagates a missing-preprocessor failure so application
     * boot fails fast. The constructor-level check in {@code CelValidationRule} is exercised
     * by {@code CelValidationRuleTest}; this test pins the discovery path that Spring walks
     * when wiring the rule list bean. The specific qualifier in the message is not asserted —
     * which YAML rule is loaded first is not API-guaranteed by
     * {@code PathMatchingResourcePatternResolver}, so the qualifier in the message could be
     * either of the bundled rules' preprocessing.type values.
     */
    @Test
    void validationRules_should_throw_when_preprocessor_qualifier_unknown() {
        PreprocessorRegistry emptyRegistry = new PreprocessorRegistry(List.of());

        assertThatThrownBy(() -> config.validationRules(
                emptyRegistry,
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(RuleOverrideService.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No preprocessor registered for type:");
    }
}
