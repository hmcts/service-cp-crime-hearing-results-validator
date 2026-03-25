package uk.gov.hmcts.cp.services.impl;

import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.ValidationService;
import uk.gov.hmcts.cp.services.rules.ValidationRule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Runs every configured validation rule and aggregates their issues into the API response.
 */
@Service
@Slf4j
public class DefaultValidationService implements ValidationService {

    private final List<ValidationRule> rules;

    public DefaultValidationService(@Qualifier("validationRules") final List<ValidationRule> rules) {
        this.rules = rules;
    }

    @Override
    @Observed(name = "validation.request")
    public DraftValidationResponse validate(final DraftValidationRequest request) {
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
}
