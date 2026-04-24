package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ACL and identity-group based access around HTTP endpoints.
 */
@Disabled("temporarily disabled")

class AuthzFilterIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";

    private static final String EMPTY_REQUEST = """
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

    @AfterEach
    void restoreDefaultStub() {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("System Users");
    }

    /**
     * Verifies a user resolved into the Court Clerks group can call the validation endpoint.
     */
    @Test
    void request_with_user_in_court_clerks_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Clerks");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "court-clerk-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isOk());
    }

    /**
     * Verifies a user resolved into the Legal Advisers group can call the validation endpoint.
     */
    @Test
    void request_with_legal_adviser_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Legal Advisers");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "legal-adviser-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isOk());
    }

    /**
     * Verifies users outside the allowed groups are rejected with HTTP 403.
     */
    @Test
    void request_with_user_not_in_allowed_group_should_return_403() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Some Other Group");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "random-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies the request is rejected with HTTP 401 when the mandatory identity header is absent.
     */
    @Test
    void request_without_cjscppuid_header_should_return_401() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies unauthenticated access is still allowed for the health endpoint.
     */
    @Test
    void actuator_health_should_be_accessible_without_auth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
