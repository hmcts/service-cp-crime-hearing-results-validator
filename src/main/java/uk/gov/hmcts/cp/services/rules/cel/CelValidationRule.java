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
import java.util.Locale;
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

    public CelValidationRule(final String rulePath,
                             final CustodialPreprocessor preprocessor,
                             final CelExpressionEvaluator evaluator,
                             final MessageTemplateResolver messageResolver,
                             final OffenceDisplayHelper offenceDisplayHelper,
                             final RuleOverrideService ruleOverrideService) {
        this.ruleDefinition = RuleDefinitionLoader.load(rulePath);
        this.preprocessor = preprocessor;
        this.evaluator = evaluator;
        this.messageResolver = messageResolver;
        this.offenceDisplayHelper = offenceDisplayHelper;
        this.ruleOverrideService = ruleOverrideService;
    }

    @Override
    public RuleDetailResponse getRuleDetail() {
        final Optional<ValidationRuleEntity> override = ruleOverrideService.findOverride(ruleDefinition.getId());

        final boolean enabled = override.map(ValidationRuleEntity::isEnabled)
                .orElse(ruleDefinition.isEnabled());
        final String rawSeverity = override.map(ValidationRuleEntity::getSeverity)
                .orElse("ERROR");
        final String normalized = normalizeSeverity(rawSeverity);
        if (normalized == null) {
            log.warn("Invalid severity override '{}' for rule {}, falling back to ERROR",
                    rawSeverity, ruleDefinition.getId());
        }
        final String severity = Optional.ofNullable(normalized).orElse("ERROR");

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
    public List<ValidationIssue> evaluate(final DraftValidationRequest request) {
        final Optional<ValidationRuleEntity> override = ruleOverrideService.findOverride(ruleDefinition.getId());

        final boolean enabled = override.map(ValidationRuleEntity::isEnabled)
                .orElse(ruleDefinition.isEnabled());

        final List<ValidationIssue> issues = new ArrayList<>();

        if (enabled) {
            final Map<String, OffenceDto> offenceMap = request.getOffences().stream()
                    .collect(Collectors.toMap(OffenceDto::getId, o -> o, (a, b) -> a));

            final Map<String, DefendantContext> defendantContexts =
                    preprocessor.preprocess(request, ruleDefinition.getPreprocessing());

            for (final DefendantContext context : defendantContexts.values()) {
                final Map<String, Long> celContext = context.toCelContext();

                for (final ConditionDefinition condition : ruleDefinition.getConditions()) {
                    if (evaluator.evaluate(condition.getExpression(), celContext)) {
                        final List<String> affectedIds = context.getOffenceIdSet(
                                condition.getAffectedOffenceSet());

                        final String message = messageResolver.resolve(
                                condition.getMessageTemplate(),
                                context.defendantName(),
                                affectedIds,
                                offenceMap,
                                context.allOffenceIds());

                        final String effectiveSeverity = resolveEffectiveSeverity(
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
        } else {
            log.debug("Rule {} is disabled via database override", ruleDefinition.getId());
        }

        return issues;
    }

    /**
     * Resolves the effective severity using the DB override as a ceiling.
     * WARNING &lt; ERROR, so a DB override of WARNING caps any ERROR condition down to WARNING.
     */
    private String resolveEffectiveSeverity(final String yamlSeverity,
                                            final Optional<ValidationRuleEntity> override) {
        final String dbSeverity = override.map(ValidationRuleEntity::getSeverity).orElse(null);
        final String result;
        if (dbSeverity == null) {
            result = yamlSeverity;
        } else {
            final String normalizedDb = normalizeSeverity(dbSeverity);
            if (normalizedDb == null) {
                log.warn("Invalid severity override '{}' for rule {}, falling back to YAML severity",
                        dbSeverity, ruleDefinition.getId());
                result = yamlSeverity;
            } else {
                final int yamlOrdinal = severityOrdinal(yamlSeverity);
                final int dbOrdinal = severityOrdinal(normalizedDb);
                result = yamlOrdinal <= dbOrdinal ? yamlSeverity : normalizedDb;
            }
        }
        return result;
    }

    private String normalizeSeverity(final String severity) {
        return Optional.ofNullable(severity)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(upper -> "ERROR".equals(upper) || "WARNING".equals(upper))
                .orElse(null);
    }

    private int severityOrdinal(final String severity) {
        return "WARNING".equalsIgnoreCase(severity) ? 0 : 1;
    }

    private ValidationIssue.SeverityEnum mapSeverity(final String severity) {
        return ValidationIssue.SeverityEnum.valueOf(normalizeSeverity(severity));
    }
}
