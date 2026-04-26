package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for DR-DISQ-001 (extended-test disqualification warning) over the public
 * validate endpoint. Covers Phase 3 / User Story 1 — the MVP path where a relevant Road
 * Traffic Act 1988 offence with a non-excluded final result and no DDOTE / DDOTEL recorded
 * surfaces as a single non-blocking warning above the affected offence.
 */
class DisqualificationExtendedTestRuleIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";

    private static final String EXPECTED_MESSAGE =
            "Check whether you need to add extended test disqualification with DDOTE "
                    + "(disqualification and extended test) or DDOTEL (disqualification for "
                    + "life and extended test)";

    @Nested
    @DisplayName("WarnsOnQualifyingOffence")
    class WarnsOnQualifyingOffence {

        @Test
        void rt88026_with_coew_and_no_ddote_should_produce_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-04-25",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Convicted",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Alex", "lastName": "Driver"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "RT88026",
                         "offenceTitle": "Dangerous driving", "orderIndex": 1}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors", empty()))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-DISQ-001")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.warnings[0].message", is(EXPECTED_MESSAGE)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")));
        }

        @Test
        void two_defendants_on_same_relevant_offence_should_produce_one_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h-multi",
                      "hearingDay": "2026-04-25",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Convicted",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "Alex", "lastName": "Driver"},
                        {"id": "d2", "firstName": "Sam", "lastName": "Passenger"}
                      ],
                      "offences": [
                        {"id": "off1", "offenceCode": "RT88026",
                         "offenceTitle": "Dangerous driving", "orderIndex": 1}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-DISQ-001")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")));
        }

        @Test
        void non_relevant_offence_should_produce_no_disqualification_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h2",
                      "hearingDay": "2026-04-25",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Convicted",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Alex", "lastName": "Driver"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }
    }
}
