package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the validation-rule metadata endpoints.
 */
class ValidationRulesControllerIntegrationTest extends IntegrationTestBase {

    /**
     * Verifies listing rules returns the configured rule summary and identifiers.
     */
    @Test
    void list_rules_should_return_ok_with_rules() throws Exception {
        mockMvc.perform(get("/api/validation/rules")
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)))
                .andExpect(jsonPath("$.enabledCount", is(1)))
                .andExpect(jsonPath("$.rules", hasSize(1)))
                .andExpect(jsonPath("$.rules[0].ruleId", is("DR-SENT-002")));
    }

    /**
     * Verifies fetching a known rule id returns that rule's detail payload.
     */
    @Test
    void get_rule_by_id_should_return_ok_with_rule_detail() throws Exception {
        mockMvc.perform(get("/api/validation/rules/{ruleId}", "DR-SENT-002")
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId", is("DR-SENT-002")))
                .andExpect(jsonPath("$.enabled", is(true)));
    }

    /**
     * Verifies unknown rule ids are converted into the structured 404 error contract.
     */
    @Test
    void get_rule_by_id_should_return_404_with_structured_error_body() throws Exception {
        mockMvc.perform(get("/api/validation/rules/{ruleId}", "UNKNOWN-RULE")
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules-detail"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.title", is("Not Found")))
                .andExpect(jsonPath("$.detail", containsString("Rule not found")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.traceId", notNullValue()));
    }
}
