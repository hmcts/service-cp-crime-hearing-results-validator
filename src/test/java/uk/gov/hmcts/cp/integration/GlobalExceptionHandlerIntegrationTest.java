package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.cp.services.ValidationService;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the generic 500 handler is wired into the real dispatch pipeline: an unexpected
 * exception from any downstream bean is surfaced as a structured ErrorResponse rather than a
 * default Spring error page. This proves the mechanism once, framework-level, rather than in
 * every controller test.
 */
class GlobalExceptionHandlerIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";

    private static final String EMPTY_ARRAYS_REQUEST = """
            {
              "hearingId": "h1",
              "caseId": "c1",
              "hearingDay": "2026-03-11",
              "courtType": "MAGISTRATES",
              "resultLines": [],
              "defendants": [],
              "offences": []
            }
            """;

    @MockitoBean
    private ValidationService validationService;

    /**
     * Verifies an unexpected exception from the service layer is mapped to a 500 ErrorResponse
     * with the current trace id populated, rather than leaking the underlying exception.
     */
    @Test
    void validate_should_return_500_error_response_when_service_throws_unexpectedly() throws Exception {
        given(validationService.validate(any())).willThrow(new IllegalStateException("boom"));

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_ARRAYS_REQUEST))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Internal Server Error")))
                .andExpect(jsonPath("$.message", is("An unexpected error occurred")))
                .andExpect(jsonPath("$.traceId", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }
}
