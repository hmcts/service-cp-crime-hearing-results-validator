package uk.gov.hmcts.cp.services.rules.cel;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CelValidationRule implements ValidationRule {

    private static final String RULE_PATH = "rules/DR-SENT-002.yaml";

    private final RuleDefinition ruleDefinition;
    private final CustodialPreprocessor preprocessor;
    private final CelExpressionEvaluator evaluator;
    private final MessageTemplateResolver messageResolver;
    private final OffenceDisplayHelper offenceDisplayHelper;
    private final ValidationRuleRepository ruleRepository;
    private volatile Optional<ValidationRuleEntity> cachedOverride;

    public CelValidationRule(CustodialPreprocessor preprocessor,
                             CelExpressionEvaluator evaluator,
                             MessageTemplateResolver messageResolver,
                             OffenceDisplayHelper offenceDisplayHelper,
                             ValidationRuleRepository ruleRepository) {
        this.ruleDefinition = RuleDefinitionLoader.load(RULE_PATH);
        this.preprocessor = preprocessor;
        this.evaluator = evaluator;
        this.messageResolver = messageResolver;
        this.offenceDisplayHelper = offenceDisplayHelper;
        this.ruleRepository = ruleRepository;
    }

    @PostConstruct
    void loadOverride() {
        try {
            this.cachedOverride = ruleRepository.findById(ruleDefinition.getId());
        } catch (Exception e) {
            log.warn("Failed to load rule override for {}: {}", ruleDefinition.getId(), e.getMessage());
            this.cachedOverride = Optional.empty();
        }
    }

    @Override
    public RuleDetailResponse getRuleDetail() {
        Optional<ValidationRuleEntity> override = findOverride();

        boolean enabled = override.map(ValidationRuleEntity::isEnabled)
                .orElse(ruleDefinition.isEnabled());
        String severity = override.map(ValidationRuleEntity::getSeverity)
                .orElse("ERROR");

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
        Optional<ValidationRuleEntity> override = findOverride();

        boolean enabled = override.map(ValidationRuleEntity::isEnabled)
                .orElse(ruleDefinition.isEnabled());
        if (!enabled) {
            log.debug("Rule {} is disabled via database override", ruleDefinition.getId());
            return List.of();
        }

        List<ValidationIssue> issues = new ArrayList<>();

        List<String> allOffenceIds = request.getOffences().stream()
                .map(OffenceDto::getId)
                .toList();
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
                            affectedIds,
                            offenceMap,
                            allOffenceIds);

                    issues.add(ValidationIssue.builder()
                            .ruleId(ruleDefinition.getId())
                            .severity(mapSeverity(condition.getSeverity()))
                            .message(message)
                            .affectedOffences(offenceDisplayHelper.buildAffectedOffences(affectedIds, offenceMap))
                            .build());
                }
            }
        }

        return issues;
    }

    private Optional<ValidationRuleEntity> findOverride() {
        if (cachedOverride != null) {
            return cachedOverride;
        }
        try {
            cachedOverride = ruleRepository.findById(ruleDefinition.getId());
            return cachedOverride;
        } catch (Exception e) {
            log.warn("Failed to load rule override for {}: {}", ruleDefinition.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private ValidationIssue.SeverityEnum mapSeverity(String severity) {
        return ValidationIssue.SeverityEnum.valueOf(severity);
    }
}
