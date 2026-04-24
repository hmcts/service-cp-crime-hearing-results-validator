package uk.gov.hmcts.cp.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.ValidationRule;
import uk.gov.hmcts.cp.services.rules.cel.CelExpressionEvaluator;
import uk.gov.hmcts.cp.services.rules.cel.CelValidationRule;
import uk.gov.hmcts.cp.services.rules.cel.CtlPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.CustodialPreprocessor;
import uk.gov.hmcts.cp.services.rules.cel.MessageTemplateResolver;
import uk.gov.hmcts.cp.services.rules.cel.RulePreprocessor;

/**
 * Discovers YAML-backed validation rules on the classpath and exposes them as application beans.
 */
@Configuration
@Slf4j
public class ValidationRuleAutoConfiguration {

    /** Discovers and registers YAML-backed validation rules from the classpath. */
    @Bean("validationRules")
    public List<ValidationRule> validationRules(
            final CustodialPreprocessor custodialPreprocessor,
            final CtlPreprocessor ctlPreprocessor,
            final CelExpressionEvaluator evaluator,
            final MessageTemplateResolver messageResolver,
            final OffenceDisplayHelper offenceDisplayHelper,
            final RuleOverrideService ruleOverrideService) throws IOException {

        final Map<String, RulePreprocessor> preprocessors = Map.of(
                "custodial-concurrent-consecutive", custodialPreprocessor,
                "ctl-offence", ctlPreprocessor);

        final Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:rules/*.yaml");

        final List<ValidationRule> rules = new ArrayList<>();
        for (final Resource resource : resources) {
            final String filename = resource.getFilename();
            if (filename == null || !filename.startsWith("DR-")) {
                continue;
            }
            final String path = "rules/" + filename;
            rules.add(new CelValidationRule(path, preprocessors, evaluator,
                    messageResolver, offenceDisplayHelper, ruleOverrideService));
        }

        rules.sort(Comparator.comparingInt(ValidationRule::getPriority));

        log.info("Auto-discovered {} validation rule(s) from classpath:rules/", rules.size());
        return rules;
    }
}
