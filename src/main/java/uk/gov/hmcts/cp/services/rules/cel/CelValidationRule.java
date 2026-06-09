package uk.gov.hmcts.cp.services.rules.cel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.SeverityCeiling;
import uk.gov.hmcts.cp.services.rules.ValidationIssueResult;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

/**
 * Validation rule implementation backed by a YAML rule definition and CEL expressions.
 */
@Slf4j
public class CelValidationRule implements ValidationRule {

    private final RuleDefinition ruleDefinition;
    private final CelExpressionEvaluator evaluator;
    private final MessageTemplateResolver messageResolver;
    private final OffenceDisplayHelper offenceDisplayHelper;
    private final RuleOverrideService ruleOverrideService;

    private final ValidationPreprocessor preprocessor;

    /**
     * Constructs the rule from a YAML path and the required collaborators. Fails fast at
     * construction time if the YAML's {@code preprocessing.type} qualifier does not resolve in
     * the registry or if any condition is missing its {@code id} field, surfacing
     * misconfigurations at application boot rather than on the first validation request.
     */
    public CelValidationRule(final String rulePath,
                             final PreprocessorRegistry preprocessorRegistry,
                             final CelExpressionEvaluator evaluator,
                             final MessageTemplateResolver messageResolver,
                             final OffenceDisplayHelper offenceDisplayHelper,
                             final RuleOverrideService ruleOverrideService) {
        this.ruleDefinition = RuleDefinitionLoader.load(rulePath);
        if (ruleDefinition.getConditions() != null) {
            for (final ConditionDefinition condition : ruleDefinition.getConditions()) {
                if (condition.getId() == null || condition.getId().isBlank()) {
                    throw new IllegalArgumentException(
                            "Rule " + ruleDefinition.getId()
                                    + " has a condition with a missing or blank id in: " + rulePath);
                }
            }
        }
        this.evaluator = evaluator;
        this.messageResolver = messageResolver;
        this.offenceDisplayHelper = offenceDisplayHelper;
        this.ruleOverrideService = ruleOverrideService;
        preprocessor = preprocessorRegistry.require(ruleDefinition.getPreprocessing().getType());
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
    public List<ValidationIssueResult> evaluate(final DraftValidationRequest request) {
        final ResolvedOverride override = resolveOverride();

        final List<ValidationIssueResult> results = new ArrayList<>();

        if (override.enabled()) {
            final Map<String, OffenceDto> offenceMap = request.getOffences().stream()
                    .collect(Collectors.toMap(OffenceDto::getOffenceId, o -> o, (a, b) -> a));

            final Map<String, ? extends RuleEvaluationContext> contexts =
                    preprocessor.preprocess(request, ruleDefinition.getPreprocessing());

            for (final RuleEvaluationContext context : contexts.values()) {
                final Map<String, Long> celContext = context.toCelContext();

                for (final ConditionDefinition condition : ruleDefinition.getConditions()) {
                    if (evaluator.evaluate(condition.getExpression(), celContext)) {
                        final boolean isDefendantLevel =
                                condition.getValidationLevel() == ValidationLevel.DEFENDANT;

                        final List<String> offenceIdsForTemplate =
                                condition.getAffectedOffenceSet() != null
                                        ? context.getOffenceIdSet(condition.getAffectedOffenceSet())
                                        : List.of();

                        final String normalizedSeverity = Optional
                                .ofNullable(SeverityCeiling.normalize(
                                        SeverityCeiling.resolve(
                                                condition.getSeverity(), override.dbSeverity())))
                                .orElse("ERROR");

                        final boolean isError = "ERROR".equalsIgnoreCase(normalizedSeverity);
                        final ValidationIssue.ValidationLevelEnum level = isDefendantLevel
                                ? ValidationIssue.ValidationLevelEnum.DEFENDANT
                                : ValidationIssue.ValidationLevelEnum.OFFENCE;

                        final ValidationIssue.ValidationIssueBuilder issueBuilder = ValidationIssue.builder()
                                .ruleId(ruleDefinition.getId())
                                .severity(ValidationIssue.SeverityEnum.valueOf(normalizedSeverity))
                                .validationLevel(level);

                        if (isDefendantLevel) {
                            final String message = messageResolver.resolve(
                                    condition.getMessageTemplate(),
                                    context.defendantName(),
                                    offenceIdsForTemplate,
                                    offenceMap,
                                    context.allOffenceIds());
                            final List<String> defendantIds = condition.getAffectedDefendantSet() != null
                                    ? context.getDefendantIdSet(condition.getAffectedDefendantSet())
                                    : List.of();
                            issueBuilder.affectedDefendants(
                                    offenceDisplayHelper.buildAffectedDefendants(defendantIds, message));
                        } else {
                            issueBuilder.affectedOffences(
                                    offenceDisplayHelper.buildAffectedOffences(
                                            offenceIdsForTemplate,
                                            offenceMap,
                                            id -> messageResolver.resolve(
                                                    condition.getMessageTemplate(),
                                                    context.defendantName(),
                                                    List.of(id),
                                                    offenceMap,
                                                    context.allOffenceIds())));
                        }

                        final String errorMessage = (isError && condition.getErrorMessageTemplate() != null)
                                ? messageResolver.resolve(
                                        condition.getErrorMessageTemplate(),
                                        context.defendantName(),
                                        offenceIdsForTemplate,
                                        offenceMap,
                                        context.allOffenceIds())
                                : null;

                        final String affectedDefendantName =
                                (isError && condition.getErrorMessageTemplate() != null)
                                        ? context.defendantName()
                                        : null;

                        if (isError) {
                            results.add(ValidationIssueResult.forError(
                                    issueBuilder.build(), errorMessage, affectedDefendantName, condition.getId()));
                        } else {
                            results.add(ValidationIssueResult.forWarning(issueBuilder.build()));
                        }
                    }
                }
            }
        } else {
            log.debug("Rule {} is disabled via database override", ruleDefinition.getId());
        }

        return results;
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
