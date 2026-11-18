package uk.gov.hmcts.cp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for the validation-rule metadata endpoints.
 */
class ValidationRulesControllerIntegrationTest extends IntegrationTestBase {

    private static final List<String> ALL_RULE_IDS = List.of(
            "DR-SENT-001",
            "DR-DISQ-002",
            "DR-CTL-003",
            "DR-YRO-004",
            "DR-COEW-005",
            "DR-CONV-006",
            "DR-AGE-007",
            "DR-SEX-008",
            "DR-URG-008",
            "DR-APP-009",
            "DR-RESTRAO-010",
            "DR-SENT-011",
            "DR-END-012",
            "DR-DISQ-013",
            "DR-BAIL-016"
    );
    private static final int RULE_COUNT = 15;

    /**
     * Verifies listing rules returns the configured rule summary and identifiers.
     */
    @Test
    void list_rules_should_return_ok_with_rules() throws Exception {
        assertThat(ALL_RULE_IDS).hasSize(RULE_COUNT);
        mockMvc.perform(get("/api/validation/rules")
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(RULE_COUNT)))
                // All rules ship enabled by their Flyway seed migrations.
                .andExpect(jsonPath("$.enabledCount", is(RULE_COUNT)))
                .andExpect(jsonPath("$.rules", hasSize(RULE_COUNT)))
                .andExpect(jsonPath("$.rules[*].ruleId",
                        contains(ALL_RULE_IDS.toArray(String[]::new))));
    }

    /**
     * Verifies fetching a known rule id returns that rule's detail payload.
     */
    @Test
    void get_rule_by_id_should_return_ok_with_rule_detail() throws Exception {
        mockMvc.perform(get("/api/validation/rules/{ruleId}", "DR-SENT-001")
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.rules-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId", is("DR-SENT-001")))
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
                .andExpect(jsonPath("$.error", is("Rule not found")))
                .andExpect(jsonPath("$.message", containsString("UNKNOWN-RULE")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.traceId", notNullValue()));
    }
}
