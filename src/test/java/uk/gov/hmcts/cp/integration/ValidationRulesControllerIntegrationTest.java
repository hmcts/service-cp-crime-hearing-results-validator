package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ValidationRulesControllerIntegrationTest extends IntegrationTestBase {

    @Test
    void list_rules_should_return_ok_with_mock_rules() throws Exception {
        mockMvc.perform(get("/api/validation/rules")
                        .header("CJSCPPUID", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)))
                .andExpect(jsonPath("$.enabledCount", is(1)))
                .andExpect(jsonPath("$.rules", hasSize(1)))
                .andExpect(jsonPath("$.rules[0].ruleId", is("DR-SENT-001")));
    }

    @Test
    void get_rule_by_id_should_return_ok_with_rule_detail() throws Exception {
        mockMvc.perform(get("/api/validation/rules/{ruleId}", "DR-SENT-001")
                        .header("CJSCPPUID", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId", is("DR-SENT-001")))
                .andExpect(jsonPath("$.enabled", is(true)));
    }
}
