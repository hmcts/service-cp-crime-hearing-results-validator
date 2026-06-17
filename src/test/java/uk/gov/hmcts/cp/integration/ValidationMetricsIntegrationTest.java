package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that a triggered validation issue is exposed as a Prometheus counter via the actuator
 * endpoint (not merely registered in the in-process registry).
 */
class ValidationMetricsIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";

    private static final String AC2_TRIGGERING_REQUEST = """
            {
              "hearingId": "h1",
              "hearingDay": "2026-03-11",
              "courtType": "MAGISTRATES",
              "resultLines": [
                {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2"},
                {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off3"},
                {"resultLineId": "rl4", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off4", "isConcurrent": true}
              ],
              "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Doe"}],
              "offences": [
                {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2},
                {"offenceId": "off3", "offenceCode": "BG001", "offenceTitle": "Burglary", "orderIndex": 3},
                {"offenceId": "off4", "offenceCode": "RB001", "offenceTitle": "Robbery", "orderIndex": 4}
              ]
            }
            """;

    /**
     * Triggers the DR-SENT-002 AC2 error then verifies the prometheus endpoint exposes the counter
     * series tagged with the rule, condition and severity. Asserts presence of the labelled series
     * rather than an absolute value, since counters accumulate across the shared test context.
     */
    @Test
    void triggered_issue_should_be_exposed_as_prometheus_counter() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AC2_TRIGGERING_REQUEST))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("validation_rule_issues_total")))
                .andExpect(content().string(containsString("ruleId=\"DR-SENT-002\"")))
                .andExpect(content().string(containsString("conditionId=\"AC2\"")))
                .andExpect(content().string(containsString("severity=\"ERROR\"")));
    }
}
