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
import uk.gov.hmcts.cp.services.rules.ValidationIssueRecorder;
import uk.gov.hmcts.cp.services.rules.ValidationIssueResult;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

/**
 * Validation rule implementation backed by a YAML rule definition and CEL expressions.
 */
@Slf4j
public class CelValidationRule implements ValidationRule {

    /** Default placeholder token name, preserved for every rule authored before DR-APP-009. */
    private static final String DEFAULT_CALCULATED_VALUE_PLACEHOLDER_NAME = "calculatedEndDate";

    private final RuleDefinition ruleDefinition;
    private final PreprocessorRegistry preprocessorRegistry;
    private final CelExpressionEvaluator evaluator;
    private final MessageTemplateResolver messageResolver;
    private final OffenceDisplayHelper offenceDisplayHelper;
    private final RuleOverrideService ruleOverrideService;
    private final ValidationIssueRecorder issueRecorder;

    private final ValidationPreprocessor preprocessor;

    /**
     * Constructs the rule from a YAML path and the required collaborators. Fails fast at
     * construction time if the YAML's {@code preprocessing.type} qualifier does not resolve in
     * the registry, surfacing the misconfiguration at application boot rather than on the first
     * validation request.
     */
    public CelValidationRule(final String rulePath,
                             final PreprocessorRegistry preprocessorRegistry,
                             final CelExpressionEvaluator evaluator,
                             final MessageTemplateResolver messageResolver,
                             final OffenceDisplayHelper offenceDisplayHelper,
                             final RuleOverrideService ruleOverrideService,
                             final ValidationIssueRecorder issueRecorder) {
        this.ruleDefinition = RuleDefinitionLoader.load(rulePath);
        this.preprocessorRegistry = preprocessorRegistry;
        this.evaluator = evaluator;
        this.messageResolver = messageResolver;
        this.offenceDisplayHelper = offenceDisplayHelper;
        this.ruleOverrideService = ruleOverrideService;
        this.issueRecorder = issueRecorder;
        preprocessor = preprocessorRegistry.require(ruleDefinition.preprocessing().type());
    }

    @Override
    public int getPriority() {
        return ruleDefinition.priority();
    }

    @Override
    public RuleDetailResponse getRuleDetail() {
        final ResolvedOverride override = resolveOverride();
        final String severity = Optional.ofNullable(SeverityCeiling.normalize(override.dbSeverity()))
                .orElse("ERROR");
        return RuleDetailResponse.builder()
                .ruleId(ruleDefinition.id())
                .title(ruleDefinition.title())
                .description(ruleDefinition.description())
                .priority(ruleDefinition.priority())
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
                    preprocessor.preprocess(request, ruleDefinition.preprocessing());

            for (final RuleEvaluationContext context : contexts.values()) {
                final Map<String, Long> celContext = context.toCelContext();

                for (final ConditionDefinition condition : ruleDefinition.conditions()) {
                    if (evaluator.evaluate(condition.expression(), celContext)) {
                        final boolean isDefendantLevel =
                                condition.validationLevel() == ValidationLevel.DEFENDANT;

                        final List<String> offenceIdsForTemplate =
                                condition.affectedOffenceSet() != null
                                        ? context.getOffenceIdSet(condition.affectedOffenceSet())
                                        : List.of();

                        final String normalizedSeverity = Optional
                                .ofNullable(SeverityCeiling.normalize(
                                        SeverityCeiling.resolve(
                                                condition.severity(), override.dbSeverity())))
                                .orElse("ERROR");

                        final boolean isError = "ERROR".equalsIgnoreCase(normalizedSeverity);
                        final ValidationIssue.ValidationLevelEnum level = isDefendantLevel
                                ? ValidationIssue.ValidationLevelEnum.DEFENDANT
                                : ValidationIssue.ValidationLevelEnum.OFFENCE;

                        final ValidationIssue.ValidationIssueBuilder issueBuilder = ValidationIssue.builder()
                                .ruleId(ruleDefinition.id())
                                .severity(ValidationIssue.SeverityEnum.valueOf(normalizedSeverity))
                                .validationLevel(level);

                        if (isDefendantLevel) {
                            final String message = messageResolver.resolve(
                                    condition.messageTemplate(),
                                    context.defendantName(),
                                    offenceIdsForTemplate,
                                    offenceMap,
                                    context.allOffenceIds());
                            issueBuilder.affectedDefendants(
                                    offenceDisplayHelper.buildAffectedDefendants(
                                            context.getDefendantIdSet(condition.affectedDefendantSet()),
                                            message));
                        } else {
                            final String calculatedValueSet = condition.calculatedValueSet();
                            issueBuilder.affectedOffences(
                                    offenceDisplayHelper.buildAffectedOffences(
                                            offenceIdsForTemplate,
                                            offenceMap,
                                            id -> calculatedValueSet == null
                                                    ? messageResolver.resolve(
                                                            condition.messageTemplate(),
                                                            context.defendantName(),
                                                            List.of(id),
                                                            offenceMap,
                                                            context.allOffenceIds())
                                                    : messageResolver.resolve(
                                                            condition.messageTemplate(),
                                                            context.defendantName(),
                                                            List.of(id),
                                                            offenceMap,
                                                            context.allOffenceIds(),
                                                            calculatedValuePlaceholder(
                                                                    condition.calculatedValuePlaceholderName(),
                                                                    context.getCalculatedValue(
                                                                            calculatedValueSet, id)))));
                        }

                        final String errorMessage = (isError && condition.errorMessageTemplate() != null)
                                ? messageResolver.resolve(
                                        condition.errorMessageTemplate(),
                                        context.defendantName(),
                                        offenceIdsForTemplate,
                                        offenceMap,
                                        context.allOffenceIds(),
                                        errorMessageCalculatedValuePlaceholder(
                                                condition, context, offenceIdsForTemplate))
                                : null;

                        final String affectedDefendantName =
                                (isError && condition.errorMessageTemplate() != null)
                                        ? context.defendantName()
                                        : null;

                        if (isError) {
                            results.add(ValidationIssueResult.forError(issueBuilder.build(), errorMessage, affectedDefendantName));
                        } else {
                            results.add(ValidationIssueResult.forWarning(issueBuilder.build()));
                        }
                        recordIssue(condition.id(),
                                ValidationIssue.SeverityEnum.valueOf(normalizedSeverity),
                                request.getHearingId(),
                                condition.name(),
                                level);
                    }
                }
            }
        } else {
            log.debug("Rule {} is disabled via database override", ruleDefinition.id());
        }

        return results;
    }

    /**
     * Records the triggered issue for monitoring, isolated behind a call-site guard so a recorder
     * failure can never escape into the evaluate loop and cause the rule's issues to be discarded.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // observability must never suppress an issue
    private void recordIssue(final String conditionId,
                             final ValidationIssue.SeverityEnum severity,
                             final String hearingId,
                             final String conditionDescription,
                             final ValidationIssue.ValidationLevelEnum validationLevel) {
        try {
            issueRecorder.record(ruleDefinition.id(), conditionId, severity, hearingId,
                    ruleDefinition.description(), conditionDescription, validationLevel);
        } catch (Exception e) {
            log.warn("Validation issue recorder failed for ruleId={} conditionId={}: {}",
                    ruleDefinition.id(), conditionId, e.getMessage());
        }
    }

    /**
     * Builds the {@code ${<placeholderName>}} placeholder map, leaving the token unexpanded
     * (empty map) rather than passing a null value into {@code Map.of}, which would throw.
     * {@code placeholderName} defaults to {@code "calculatedEndDate"} when the condition's YAML
     * omits {@code calculatedValuePlaceholderName} -- the pre-existing hardcoded behaviour,
     * unchanged for every rule that doesn't set the new field.
     */
    @SuppressWarnings("PMD.OnlyOneReturn") // early-return on the common null case reads clearer here
    private static Map<String, String> calculatedValuePlaceholder(final String placeholderName,
                                                                   final String calculatedValue) {
        if (calculatedValue == null) {
            return Map.of();
        }
        final String name = placeholderName == null
                ? DEFAULT_CALCULATED_VALUE_PLACEHOLDER_NAME
                : placeholderName;
        return Map.of(name, calculatedValue);
    }

    /**
     * Builds the extra-placeholder map for the page-level {@code errorMessageTemplate}, mirroring
     * the per-offence resolution already applied to the inline {@code messageTemplate}. Returns
     * an empty map (no-op) whenever the condition has no {@code calculatedValueSet} -- true for
     * every rule shipped before DR-APP-009, so their {@code errorMessage} text is built exactly
     * as before. When a context spans more than one affected offence id, only the first is used,
     * matching every current caller of {@code calculatedValueSet} (see data-model.md).
     */
    @SuppressWarnings("PMD.OnlyOneReturn") // early-return on the common no-op case reads clearer here
    private static Map<String, String> errorMessageCalculatedValuePlaceholder(
            final ConditionDefinition condition,
            final RuleEvaluationContext context,
            final List<String> offenceIdsForTemplate) {
        final String calculatedValueSet = condition.calculatedValueSet();
        if (calculatedValueSet == null || offenceIdsForTemplate.isEmpty()) {
            return Map.of();
        }
        final String calculatedValue =
                context.getCalculatedValue(calculatedValueSet, offenceIdsForTemplate.getFirst());
        return calculatedValuePlaceholder(condition.calculatedValuePlaceholderName(), calculatedValue);
    }

    /**
     * Resolves the DB override once per call, consolidating the enabled flag and raw severity
     * into a single record. Logs a warning if the DB contains an unrecognised severity value.
     */
    private ResolvedOverride resolveOverride() {
        final Optional<ValidationRuleEntity> override =
                ruleOverrideService.findOverride(ruleDefinition.id());
        final boolean enabled = override.map(ValidationRuleEntity::isEnabled)
                .orElse(ruleDefinition.enabled());
        final String dbSeverity = override.map(ValidationRuleEntity::getSeverity).orElse(null);
        if (dbSeverity != null && SeverityCeiling.normalize(dbSeverity) == null) {
            log.warn("Invalid severity override '{}' for rule {}, falling back to YAML severity",
                    dbSeverity, ruleDefinition.id());
        }
        return new ResolvedOverride(enabled, dbSeverity);
    }

    private record ResolvedOverride(boolean enabled, String dbSeverity) {}
}
