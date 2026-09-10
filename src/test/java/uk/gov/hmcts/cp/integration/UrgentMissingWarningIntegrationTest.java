package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * End-to-end tests for DR-URG-008 (urgent result missing warning) over the public validate endpoint.
 *
 * <p>DR-URG-008 fires when all conditional-bail offences for a defendant are bail-ended
 * and no URGENT result has been recorded.
 *
 * <p>Every scenario pins three response slices:
 * <ul>
 *   <li>{@code $.errors.validationIssues} is empty (no other rule produces an error on the payload).</li>
 *   <li>{@code $.warnings[?(@.ruleId=='DR-URG-008')]} is the expected size for this rule.</li>
 *   <li>{@code $.warnings} total size, so an unrelated future rule cannot make tests pass silently.</li>
 * </ul>
 */
class UrgentMissingWarningIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_URG_WARNINGS = "$.warnings[?(@.ruleId=='DR-URG-008')]";

    private static final String EXPECTED_MESSAGE =
            "The defendant's conditional bail has ended. You may need to add the URGENT result"
                    + " and select \"Bail conditions cancelled\" on one of the offences before sharing.";

    @Nested
    @DisplayName("FireScenarios")
    class FireScenarios {

        @Test
        @DisplayName("DS bail-ending result on CB offence, no URGENT → warning emitted at DEFENDANT level")
        void ds_bail_ending_no_urgent_on_cb_offence_should_emit_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-URG-008")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].defendantId", is("d1")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].message", is(EXPECTED_MESSAGE)));
        }
    }

    @Nested
    @DisplayName("UrgentSuppressesWarning")
    class UrgentSuppressesWarning {

        @Test
        @DisplayName("URGENT result present on CB offence → warning suppressed")
        void urgent_result_present_on_cb_offence_should_suppress_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "URGENT", "label": "Urgent",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("BailNotFullyEnded")
    class BailNotFullyEnded {

        @Test
        @DisplayName("CB offence with non-bail-ending result → no warning")
        void non_bail_ending_result_on_cb_offence_should_produce_no_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "WDRN", "label": "Withdrawn",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("NoConditionalBailOffences")
    class NoConditionalBailOffences {

        @Test
        @DisplayName("No offences with bailStatus=B → no warning")
        void no_bail_status_offences_should_produce_no_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("MultiDefendant")
    class MultiDefendant {

        @Test
        @DisplayName("Qualifying defendant (d1) gets warning; non-qualifying (d2) does not")
        void qualifying_defendant_gets_warning_nonqualifying_does_not() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d2", "offenceId": "off-2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"},
                        {"defendantId": "d2", "firstName": "Sam", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off-1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"},
                        {"offenceId": "off-2", "offenceCode": "AS001", "offenceTitle": "Assault",
                         "orderIndex": 2}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-URG-008")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].defendantId", is("d1")));
        }
    }
}
