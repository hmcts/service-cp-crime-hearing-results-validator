package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.services.ValidationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ValidationController}.
 */
@ExtendWith(MockitoExtension.class)
class ValidationControllerTest {

    @Mock
    ValidationService validationService;

    @InjectMocks
    ValidationController validationController;

    /**
     * Verifies the happy-path controller scenario where the request is delegated to the validation
     * service and the returned payload is wrapped in an HTTP 200 response.
     */

    void validate_should_delegate_to_service_and_return_ok() {
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .build();
        DraftValidationResponse expected = DraftValidationResponse.builder()
                .isValid(true)
                .build();
        when(validationService.validate(request)).thenReturn(expected);

        ResponseEntity<DraftValidationResponse> response =
                validationController.validateDraftResults("user1", request, "corr1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }
}
