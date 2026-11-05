package uk.gov.hmcts.cp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClient;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.ValidationIssueRecorder;
import uk.gov.hmcts.cp.services.rules.ValidationRule;
import uk.gov.hmcts.cp.services.rules.cel.AgeRestrictedImprisonmentPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.ApplicationResultOffencePreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.CelExpressionEvaluator;
import uk.gov.hmcts.cp.services.rules.cel.CommunityOrderEndDatePreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.ConditionalBailPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.CtlMissingPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.CustodialPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.DisqualificationExtendedTestPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.MessageTemplateResolver;
import uk.gov.hmcts.cp.services.rules.cel.NoConvictionPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.NonImprisonableOffencePreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.PreprocessorRegistry;
import uk.gov.hmcts.cp.services.rules.cel.RestrainingOrderMultiplePersonsPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.SexualOffenceNotificationPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.YouthRehabilitationPreprocessor;

/**
 * Tests for YAML-driven validation rule discovery.
 */
class ValidationRuleAutoConfigurationTest {

    private final ValidationRuleAutoConfiguration config = new ValidationRuleAutoConfiguration();

    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();

    private final PreprocessorRegistry preprocessorRegistry = new PreprocessorRegistry(List.of(
            new CustodialPreprocessor(),
            new DisqualificationExtendedTestPreprocessor(),
            new CtlMissingPreprocessor(),
            new YouthRehabilitationPreprocessor(),
            new CommunityOrderEndDatePreprocessor(),
            new NoConvictionPreprocessor(),
            new AgeRestrictedImprisonmentPreprocessor(),
            new ConditionalBailPreprocessor(),
            new SexualOffenceNotificationPreprocessor(mock(ReferencedataOffenceClient.class)),
            new ApplicationResultOffencePreprocessor(),
            new RestrainingOrderMultiplePersonsPreprocessor(),
            new NonImprisonableOffencePreprocessor(mock(ReferencedataOffenceClient.class))));

    private final ValidationIssueRecorder issueRecorder =
            new ValidationIssueRecorder(new SimpleMeterRegistry());

    /**
     * Verifies the configuration discovers the bundled DR-SENT-001 YAML rule.
     */
    @Test
    void should_discover_DR_SENT_001_rule() throws IOException {
        List<ValidationRule> rules = config.validationRules(
                preprocessorRegistry,
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(RuleOverrideService.class),
                issueRecorder);

        assertThat(rules).isNotEmpty();
        assertThat(rules).anyMatch(r -> "DR-SENT-001".equals(r.getRuleDetail().getRuleId()));
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
                mock(RuleOverrideService.class),
                issueRecorder);

        assertThat(rules).hasSize(12);
        assertThat(rules)
                .extracting(r -> r.getRuleDetail().getRuleId())
                .containsExactlyInAnyOrder("DR-SENT-001", "DR-DISQ-002", "DR-CTL-003", "DR-YRO-004",
                    "DR-COEW-005", "DR-CONV-006", "DR-AGE-007", "DR-SEX-008", "DR-URG-008", "DR-APP-009",
                    "DR-RESTRAO-010", "DR-SENT-011");
    }

    /**
     * Verifies every rule has a unique priority and each rule ID is paired with its expected
     * priority value. A duplicate priority causes undefined evaluation order; this test
     * fails fast when a new rule is added with a colliding or wrong priority.
     */
    @Test
    void should_have_unique_priorities_and_expected_rule_id_priority_pairs() throws IOException {
        List<ValidationRule> rules = config.validationRules(
                preprocessorRegistry,
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(RuleOverrideService.class),
                issueRecorder);

        assertThat(rules).hasSize(12);

        assertThat(rules)
                .extracting(r -> r.getRuleDetail().getRuleId(), ValidationRule::getPriority)
                .containsExactlyInAnyOrder(
                        tuple("DR-SENT-001",    1000),
                        tuple("DR-DISQ-002",    2000),
                        tuple("DR-CTL-003",     3000),
                        tuple("DR-YRO-004",     4000),
                        tuple("DR-COEW-005",    5000),
                        tuple("DR-CONV-006",    6000),
                        tuple("DR-AGE-007",     7000),
                        tuple("DR-SEX-008",     8000),
                        tuple("DR-URG-008",     9000),
                        tuple("DR-APP-009",    10_000),
                        tuple("DR-RESTRAO-010", 11_000),
                        tuple("DR-SENT-011",   12_000));

        assertThat(rules)
                .extracting(ValidationRule::getPriority)
                .doesNotHaveDuplicates();
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
                mock(RuleOverrideService.class),
                issueRecorder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No preprocessor registered for type:");
    }
}
