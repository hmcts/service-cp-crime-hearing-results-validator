package uk.gov.hmcts.cp.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.services.ValidationService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class MockValidationService implements ValidationService {

    @Override
    public DraftValidationResponse validate(DraftValidationRequest request) {
        log.info("Mock validation for hearingId={}", request.getHearingId());
        return DraftValidationResponse.builder()
                .validationId("val-" + UUID.randomUUID())
                .timestamp(Instant.now())
                .mode("advisory")
                .rulesEvaluated(List.of())
                .isValid(true)
                .errors(List.of())
                .warnings(List.of())
                .processingTimeMs(0)
                .build();
    }
}
