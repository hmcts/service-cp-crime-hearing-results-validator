package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.services.impl.MockValidationService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockValidationServiceTest {

    private final MockValidationService service = new MockValidationService();

    @Test
    void validate_should_return_valid_response() {
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        DraftValidationResponse response = service.validate(request);

        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getValidationId()).startsWith("val-");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getMode()).isEqualTo("advisory");
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getRulesEvaluated()).isEmpty();
        assertThat(response.getProcessingTimeMs()).isZero();
    }

    @Test
    void validate_should_generate_unique_validation_ids() {
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();

        String id1 = service.validate(request).getValidationId();
        String id2 = service.validate(request).getValidationId();

        assertThat(id1).isNotEqualTo(id2);
    }
}
