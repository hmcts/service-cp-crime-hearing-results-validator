package uk.gov.hmcts.cp.services.impl;

import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.ValidationService;
import uk.gov.hmcts.cp.services.feature.FeatureToggleConstants;
import uk.gov.hmcts.cp.services.feature.FeatureToggleService;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

/**
 * Runs every configured validation rule and aggregates their issues into the API response.
 */
@Service
@Slf4j
public class DefaultValidationService implements ValidationService {

    private final List<ValidationRule> rules;
    private final FeatureToggleService featureToggleService;

    /** Creates the service with the given rules and feature toggle. */
    public DefaultValidationService(
            @Qualifier("validationRules") final List<ValidationRule> rules,
            final FeatureToggleService featureToggleService) {
        this.rules = rules;
        this.featureToggleService = featureToggleService;
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

    @SuppressWarnings("PMD.AvoidCatchingGenericException") // rule failures must not abort the whole validation run
    private DraftValidationResponse evaluateRules(final DraftValidationRequest request) {
        log.info("Validating draft results for hearingId={}", request.getHearingId());
        final long startNanos = System.nanoTime();

        final List<String> rulesEvaluated = new ArrayList<>();
        final List<ValidationIssue> errors = new ArrayList<>();
        final List<ValidationIssue> warnings = new ArrayList<>();

        for (final ValidationRule rule : rules) {
            String ruleId = "unknown";
            try {
                ruleId = rule.getRuleDetail().getRuleId();

                final List<ValidationIssue> issues = rule.evaluate(request);
                for (final ValidationIssue issue : issues) {
                    if (issue.getSeverity() == ValidationIssue.SeverityEnum.ERROR) {
                        errors.add(issue);
                    } else {
                        warnings.add(issue);
                    }
                }

                rulesEvaluated.add(ruleId);
            } catch (Exception e) {
                log.error("Rule {} skipped due to evaluation failure for hearingId={}: {}",
                        ruleId, request.getHearingId(), e.getMessage(), e);
            }
        }

        final long processingTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        return DraftValidationResponse.builder()
                .validationId("val-" + UUID.randomUUID())
                .timestamp(Instant.now())
                .mode("advisory")
                .rulesEvaluated(rulesEvaluated)
                .isValid(errors.isEmpty())
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
                .errors(List.of())
                .warnings(List.of())
                .processingTimeMs(0)
                .build();
    }
}
