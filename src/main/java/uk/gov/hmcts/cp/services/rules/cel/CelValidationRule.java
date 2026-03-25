package uk.gov.hmcts.cp.services.rules.cel;

import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.SeverityCeiling;
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
    public int getPriority() {
        return ruleDefinition.getPriority();
    }

    @Override
    public RuleDetailResponse getRuleDetail() {
        final ResolvedOverride override = resolveOverride();
        final String severity = Optional.ofNullable(SeverityCeiling.normalize(override.dbSeverity()))
                .orElse("ERROR");
        return RuleDetailResponse.builder()
                .ruleId(ruleDefinition.getId())
                .title(ruleDefinition.getTitle())
                .description(ruleDefinition.getDescription())
                .priority(ruleDefinition.getPriority())
                .severity(RuleDetailResponse.SeverityEnum.valueOf(severity))
                .enabled(override.enabled())
                .build();
    }

    @Override
    public List<ValidationIssue> evaluate(final DraftValidationRequest request) {
        final ResolvedOverride override = resolveOverride();

        final List<ValidationIssue> issues = new ArrayList<>();

        if (override.enabled()) {
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

                        final String effectiveSeverity = SeverityCeiling.resolve(
                                condition.getSeverity(), override.dbSeverity());
                        final String normalizedSeverity = Optional
                                .ofNullable(SeverityCeiling.normalize(effectiveSeverity))
                                .orElse("ERROR");
                        issues.add(ValidationIssue.builder()
                                .ruleId(ruleDefinition.getId())
                                .severity(ValidationIssue.SeverityEnum.valueOf(normalizedSeverity))
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
     * Resolves the DB override once per call, consolidating the enabled flag and raw severity
     * into a single record. Logs a warning if the DB contains an unrecognised severity value.
     */
    private ResolvedOverride resolveOverride() {
        final Optional<ValidationRuleEntity> override =
                ruleOverrideService.findOverride(ruleDefinition.getId());
        final boolean enabled = override.map(ValidationRuleEntity::isEnabled)
                .orElse(ruleDefinition.isEnabled());
        final String dbSeverity = override.map(ValidationRuleEntity::getSeverity).orElse(null);
        if (dbSeverity != null && SeverityCeiling.normalize(dbSeverity) == null) {
            log.warn("Invalid severity override '{}' for rule {}, falling back to YAML severity",
                    dbSeverity, ruleDefinition.getId());
        }
        return new ResolvedOverride(enabled, dbSeverity);
    }

    private record ResolvedOverride(boolean enabled, String dbSeverity) {}
}
