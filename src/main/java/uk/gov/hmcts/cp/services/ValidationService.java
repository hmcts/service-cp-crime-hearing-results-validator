package uk.gov.hmcts.cp.services;

import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.DraftValidationResponse;

/**
 * Performs validation over a draft hearing results payload.
 */
public interface ValidationService {

    /**
     * Evaluates the configured validation rules for the supplied request.
     *
     * @param request draft results payload to validate
     * @return aggregated validation response
     */
    DraftValidationResponse validate(DraftValidationRequest request);
}
