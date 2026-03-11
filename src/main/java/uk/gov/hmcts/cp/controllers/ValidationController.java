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
public class ValidationController implements ValidationApi {

    private final ValidationService validationService;

    @Override
    public ResponseEntity<DraftValidationResponse> validateDraftResults(
            String CJSCPPUID,
            DraftValidationRequest draftValidationRequest,
            @Nullable String CPPCLIENTCORRELATIONID) {

        log.info("Validate draft results for user={}", CJSCPPUID);
        return ResponseEntity.ok(validationService.validate(draftValidationRequest));
    }
}
