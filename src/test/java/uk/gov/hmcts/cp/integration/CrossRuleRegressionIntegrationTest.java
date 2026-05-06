package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-rule regression coverage. Proves that DR-SENT-002 and DR-DISQ-001 evaluate
 * independently per Constitution Principle III and FR-011 — each produces its own issue on a
 * single hearing payload that triggers both rules, with no interference.
 *
 * <p>Implements the scenario from {@code research.md} R10. Guards against a future preprocessor
 * dispatch refactor accidentally short-circuiting one rule when the other matches, and against
 * a YAML/CEL change in either rule that silently affects the other.
 */
class CrossRuleRegressionIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";

    @Test
    void hearing_triggering_both_rules_should_emit_one_error_and_one_warning() throws Exception {
        // off1..off4 — IMP custodial sentences with off1 = primary (first noInfo), off2/off3 noInfo,
        //              off4 concurrent → triggers DR-SENT-002 AC2 ERROR.
        // off5 — RT88026 + COEW + no DDOTE → triggers DR-DISQ-001 AC1 WARNING.
        String request = """
                {
                  "hearingId": "h-cross",
                  "hearingDay": "2026-04-26",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"id": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off2"},
                    {"id": "rl3", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off3"},
                    {"id": "rl4", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off4", "isConcurrent": true},
                    {"id": "rl5", "shortCode": "COEW", "category": "F", "label": "Convicted",
                     "defendantId": "d1", "offenceId": "off5"}
                  ],
                  "defendants": [{"id": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1},
                    {"id": "off2", "offenceCode": "AS001", "offenceTitle": "Assault",
                     "orderIndex": 2},
                    {"id": "off3", "offenceCode": "BG001", "offenceTitle": "Burglary",
                     "orderIndex": 3},
                    {"id": "off4", "offenceCode": "RB001", "offenceTitle": "Robbery",
                     "orderIndex": 4},
                    {"id": "off5", "offenceCode": "RT88026",
                     "offenceTitle": "Dangerous driving", "orderIndex": 5}
                  ]
                }
                """;

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[*].ruleId", contains("DR-SENT-002")))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[*].ruleId", contains("DR-DISQ-001")))
                .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off5")))
                .andExpect(jsonPath("$.rulesEvaluated",
                        containsInAnyOrder("DR-SENT-002", "DR-DISQ-001")));
    }
}
