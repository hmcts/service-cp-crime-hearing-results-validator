package uk.gov.hmcts.cp.services.impl;

import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationErrors;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.ValidationService;
import uk.gov.hmcts.cp.services.feature.FeatureToggleConstants;
import uk.gov.hmcts.cp.services.feature.FeatureToggleService;
import uk.gov.hmcts.cp.services.rules.ValidationIssueResult;
import uk.gov.hmcts.cp.services.rules.ValidationRule;
import uk.gov.hmcts.cp.services.rules.cel.MessageTemplateResolver;

/**
 * Runs every configured validation rule and aggregates their issues into the API response.
 */
@Service
@Slf4j
public class DefaultValidationService implements ValidationService {

    /** MDC key carrying the per-call validation id into structured logs (incl. issue logs). */
    private static final String MDC_VALIDATION_ID = "validationId";

    private final List<ValidationRule> rules;
    private final FeatureToggleService featureToggleService;
    private final MessageTemplateResolver messageTemplateResolver;

    /** Creates the service with the given rules, feature toggle, and template resolver. */
    public DefaultValidationService(
            @Qualifier("validationRules") final List<ValidationRule> rules,
            final FeatureToggleService featureToggleService,
            final MessageTemplateResolver messageTemplateResolver) {
        this.rules = rules;
        this.featureToggleService = featureToggleService;
        this.messageTemplateResolver = messageTemplateResolver;
    }

    @Override
    @Observed(name = "validation.request")
    public DraftValidationResponse validate(final DraftValidationRequest request) {
        final DraftValidationResponse response;

        if (isFeatureActive()) {
            response = evaluateRules(request);
        } else {
            log.info("Validation feature disabled, returning success for hearingId={}", request.getHearingId());
            response = buildDisabledResponse();
        }

        return response;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException") // rule failures must not abort
    private DraftValidationResponse evaluateRules(final DraftValidationRequest request) {
        final String validationId = "val-" + UUID.randomUUID();
        final String previousValidationId = MDC.get(MDC_VALIDATION_ID);
        MDC.put(MDC_VALIDATION_ID, validationId);
        try {
            return evaluateRulesWithMdc(request, validationId);
        } finally {
            if (previousValidationId == null) {
                MDC.remove(MDC_VALIDATION_ID);
            } else {
                MDC.put(MDC_VALIDATION_ID, previousValidationId);
            }
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException") // rule failures must not abort the whole validation run
    private DraftValidationResponse evaluateRulesWithMdc(final DraftValidationRequest request,
                                                         final String validationId) {
        log.info("Validating draft results for hearingId={}", request.getHearingId());
        final long startNanos = System.nanoTime();

        final List<String> rulesEvaluated = new ArrayList<>();
        final Map<String, String> errorBaseByTemplate = new LinkedHashMap<>();
        final Map<String, List<String>> errorNamesByTemplate = new LinkedHashMap<>();
        final List<String> standaloneMessages = new ArrayList<>();
        final List<ValidationIssue> errorItemsList = new ArrayList<>();
        final List<ValidationIssue> warnings = new ArrayList<>();

        for (final ValidationRule rule : rules) {
            String ruleId = "unknown";
            try {
                ruleId = rule.getRuleDetail().getRuleId();

                final List<ValidationIssueResult> results = rule.evaluate(request);
                for (final ValidationIssueResult result : results) {
                    if (result.issue().getSeverity() == ValidationIssue.SeverityEnum.ERROR) {
                        errorItemsList.add(result.issue());
                        if (result.errorMessage() != null) {
                            if (result.affectedDefendantName() != null) {
                                final String templateKey = ruleId + "::" + result.errorMessage();
                                errorBaseByTemplate.putIfAbsent(templateKey, result.errorMessage());
                                appendDefendantName(errorNamesByTemplate, templateKey, result.affectedDefendantName());
                            } else {
                                standaloneMessages.add(result.errorMessage());
                            }
                        }
                    } else {
                        warnings.add(result.issue());
                    }
                }

                rulesEvaluated.add(ruleId);
            } catch (Exception e) {
                log.error("Rule {} skipped due to evaluation failure for hearingId={}: {}",
                        ruleId, request.getHearingId(), e.getMessage(), e);
            }
        }

        final List<String> errorMessages = new ArrayList<>(standaloneMessages);
        for (final Map.Entry<String, String> entry : errorBaseByTemplate.entrySet()) {
            final List<String> names = errorNamesByTemplate.get(entry.getKey());
            errorMessages.add(messageTemplateResolver.resolveDefendantNames(entry.getValue(), names));
        }

        final long processingTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        final ValidationErrors errors = ValidationErrors.builder()
                .errorMessages(errorMessages)
                .validationIssues(errorItemsList)
                .build();

        return DraftValidationResponse.builder()
                .validationId(validationId)
                .timestamp(Instant.now())
                .mode("advisory")
                .rulesEvaluated(rulesEvaluated)
                .isValid(errorItemsList.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .processingTimeMs((int) processingTimeMs)
                .build();
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private boolean isFeatureActive() {
        boolean active = true;
        try {
            active = featureToggleService.isFeatureEnabled(
                    FeatureToggleConstants.RESULTS_VALIDATION.getFeatureName());
        } catch (Exception e) {
            log.warn("Feature toggle check failed, proceeding with validation: {}", e.getMessage());
        }
        return active;
    }

    private DraftValidationResponse buildDisabledResponse() {
        return DraftValidationResponse.builder()
                .validationId("val-" + UUID.randomUUID())
                .timestamp(Instant.now())
                .mode("disabled")
                .rulesEvaluated(List.of())
                .isValid(true)
                .errors(ValidationErrors.builder()
                        .validationIssues(List.of())
                        .errorMessages(List.of())
                        .build())
                .warnings(List.of())
                .processingTimeMs(0)
                .build();
    }

    private static void appendDefendantName(final Map<String, List<String>> errorNamesByRule,
                                             final String ruleId,
                                             final String name) {
        errorNamesByRule.computeIfAbsent(ruleId, k -> new ArrayList<>()).add(name);
    }
}
