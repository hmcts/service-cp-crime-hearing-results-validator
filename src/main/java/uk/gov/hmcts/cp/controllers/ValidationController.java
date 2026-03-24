package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.ValidationApi;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;
import uk.gov.hmcts.cp.services.ValidationService;

@RestController
@RequiredArgsConstructor
@Slf4j
/**
 * Exposes the draft validation endpoint defined by the generated OpenAPI contract.
 */
public class ValidationController implements ValidationApi {

    private final ValidationService validationService;

    /**
     * Validates the supplied draft results payload and returns the aggregated validation outcome.
     *
     * @param CJSCPPUID authenticated user identifier from the request header
     * @param draftValidationRequest draft result payload to validate
     * @param CPPCLIENTCORRELATIONID optional correlation identifier supplied by the client
     * @return HTTP 200 response containing the validation result
     */
    @Override
    public ResponseEntity<DraftValidationResponse> validateDraftResults(
            String CJSCPPUID,
            DraftValidationRequest draftValidationRequest,
            @Nullable String CPPCLIENTCORRELATIONID) {

        log.info("Validate draft results for user={}", CJSCPPUID);
        return ResponseEntity.ok(validationService.validate(draftValidationRequest));
    }
}
