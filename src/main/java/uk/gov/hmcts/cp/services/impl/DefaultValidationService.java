package uk.gov.hmcts.cp.services.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultValidationService implements ValidationService {

    private final List<ValidationRule> rules;
    private final MeterRegistry meterRegistry;

    @Override
    public DraftValidationResponse validate(DraftValidationRequest request) {
        log.info("Validating draft results for hearingId={}", request.getHearingId());
        Timer.Sample sample = Timer.start(meterRegistry);

        List<String> rulesEvaluated = new ArrayList<>();
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();

        for (ValidationRule rule : rules) {
            String ruleId = rule.getRuleDetail().getRuleId();
            rulesEvaluated.add(ruleId);

            List<ValidationIssue> issues = rule.evaluate(request);
            for (ValidationIssue issue : issues) {
                if (issue.getSeverity() == ValidationIssue.SeverityEnum.ERROR) {
                    errors.add(issue);
                } else {
                    warnings.add(issue);
                }
            }
        }

        Timer timer = Timer.builder("validation.request")
                .description("Time spent validating draft hearing results")
                .register(meterRegistry);
        long processingTimeNanos = sample.stop(timer);
        long processingTimeMs = TimeUnit.NANOSECONDS.toMillis(processingTimeNanos);

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
