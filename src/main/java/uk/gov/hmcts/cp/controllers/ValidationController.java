package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.ValidationApi;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationRequestWithConvictions;
import uk.gov.hmcts.cp.services.ValidationService;

/**
 * Exposes the draft validation endpoint defined by the generated OpenAPI contract.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ValidationController implements ValidationApi {

    private final ValidationService validationService;

    /**
     * Validates the supplied draft results payload and returns the aggregated validation outcome.
     *
     * @param cjsCppUid authenticated user identifier from the request header
     * @param validationRequestWithConvictions draft result payload and conviction data to validate
     * @param cppClientCorrelationId optional correlation identifier supplied by the client
     * @return HTTP 200 response containing the validation result
     */
    @Override
    public ResponseEntity<DraftValidationResponse> validateDraftResults(
            final String cjsCppUid,
            final ValidationRequestWithConvictions validationRequestWithConvictions,
            @Nullable final String cppClientCorrelationId) {

        log.info("Validate draft results request received");
        return ResponseEntity.ok(validationService.validate(validationRequestWithConvictions));
    }
}
