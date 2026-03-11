package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;

public interface ValidationService {

    DraftValidationResponse validate(DraftValidationRequest request);
}
