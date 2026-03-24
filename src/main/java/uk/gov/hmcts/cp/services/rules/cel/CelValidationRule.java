package uk.gov.hmcts.cp.services.rules.cel;

import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validation rule implementation backed by a YAML rule definition and CEL expressions.
 */
@Slf4j
public class CelValidationRule implements ValidationRule {

    private final RuleDefinition ruleDefinition;
    private final CustodialPreprocessor preprocessor;
    private final CelExpressionEvaluator evaluator;
    private final MessageTemplateResolver messageResolver;
    private final OffenceDisplayHelper offenceDisplayHelper;
    private final RuleOverrideService ruleOverrideService;

    public CelValidationRule(String rulePath,
                             CustodialPreprocessor preprocessor,
                             CelExpressionEvaluator evaluator,
                             MessageTemplateResolver messageResolver,
                             OffenceDisplayHelper offenceDisplayHelper,
                             RuleOverrideService ruleOverrideService) {
        this.ruleDefinition = RuleDefinitionLoader.load(rulePath);
        this.preprocessor = preprocessor;
        this.evaluator = evaluator;
        this.messageResolver = messageResolver;
        this.offenceDisplayHelper = offenceDisplayHelper;
        this.ruleOverrideService = ruleOverrideService;
    }

    @Override
    public RuleDetailResponse getRuleDetail() {
        Optional<ValidationRuleEntity> override = ruleOverrideService.findOverride(ruleDefinition.getId());

        boolean enabled = override.map(ValidationRuleEntity::isEnabled)
                .orElse(ruleDefinition.isEnabled());
        String rawSeverity = override.map(ValidationRuleEntity::getSeverity)
                .orElse("ERROR");
        String severity = normalizeSeverity(rawSeverity);
        if (severity == null) {
            log.warn("Invalid severity override '{}' for rule {}, falling back to ERROR",
                    rawSeverity, ruleDefinition.getId());
            severity = "ERROR";
        }

        return RuleDetailResponse.builder()
                .ruleId(ruleDefinition.getId())
                .title(ruleDefinition.getTitle())
                .description(ruleDefinition.getDescription())
                .priority(ruleDefinition.getPriority())
                .severity(RuleDetailResponse.SeverityEnum.valueOf(severity))
                .enabled(enabled)
                .build();
    }

    @Override
    public List<ValidationIssue> evaluate(DraftValidationRequest request) {
        Optional<ValidationRuleEntity> override = ruleOverrideService.findOverride(ruleDefinition.getId());

        boolean enabled = override.map(ValidationRuleEntity::isEnabled)
                .orElse(ruleDefinition.isEnabled());
        if (!enabled) {
            log.debug("Rule {} is disabled via database override", ruleDefinition.getId());
            return List.of();
        }

        List<ValidationIssue> issues = new ArrayList<>();

        Map<String, OffenceDto> offenceMap = request.getOffences().stream()
                .collect(Collectors.toMap(OffenceDto::getId, o -> o, (a, b) -> a));

        Map<String, DefendantContext> defendantContexts =
                preprocessor.preprocess(request, ruleDefinition.getPreprocessing());

        for (DefendantContext context : defendantContexts.values()) {
            Map<String, Long> celContext = context.toCelContext();

            for (ConditionDefinition condition : ruleDefinition.getConditions()) {
                if (evaluator.evaluate(condition.getExpression(), celContext)) {
                    List<String> affectedIds = context.getOffenceIdSet(
                            condition.getAffectedOffenceSet());

                    String message = messageResolver.resolve(
                            condition.getMessageTemplate(),
                            context.defendantName(),
                            affectedIds,
                            offenceMap,
                            context.allOffenceIds());

                    String effectiveSeverity = resolveEffectiveSeverity(
                            condition.getSeverity(), override);
                    issues.add(ValidationIssue.builder()
                            .ruleId(ruleDefinition.getId())
                            .severity(mapSeverity(effectiveSeverity))
                            .message(message)
                            .affectedOffences(offenceDisplayHelper.buildAffectedOffences(affectedIds, offenceMap))
                            .build());
                }
            }
        }

        return issues;
    }

    /**
     * Resolves the effective severity using the DB override as a ceiling.
     * WARNING &lt; ERROR, so a DB override of WARNING caps any ERROR condition down to WARNING.
     */
    private String resolveEffectiveSeverity(String yamlSeverity,
                                            Optional<ValidationRuleEntity> override) {
        String dbSeverity = override.map(ValidationRuleEntity::getSeverity).orElse(null);
        if (dbSeverity == null) {
            return yamlSeverity;
        }
        String normalizedDb = normalizeSeverity(dbSeverity);
        if (normalizedDb == null) {
            log.warn("Invalid severity override '{}' for rule {}, falling back to YAML severity",
                    dbSeverity, ruleDefinition.getId());
            return yamlSeverity;
        }
        int yamlOrdinal = severityOrdinal(yamlSeverity);
        int dbOrdinal = severityOrdinal(normalizedDb);
        return yamlOrdinal <= dbOrdinal ? yamlSeverity : normalizedDb;
    }

    private String normalizeSeverity(String severity) {
        if (severity == null) {
            return null;
        }
        String upper = severity.trim().toUpperCase();
        if ("ERROR".equals(upper) || "WARNING".equals(upper)) {
            return upper;
        }
        return null;
    }

    private int severityOrdinal(String severity) {
        return "WARNING".equalsIgnoreCase(severity) ? 0 : 1;
    }

    private ValidationIssue.SeverityEnum mapSeverity(String severity) {
        return ValidationIssue.SeverityEnum.valueOf(normalizeSeverity(severity));
    }
}
