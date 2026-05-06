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
 * End-to-end tests for DR-CTL-001 (CTL missing warning) over the public validate endpoint.
 *
 * <p>Every scenario pins three response slices:
 * <ul>
 *   <li>{@code $.errors} is empty (no other rule produced an error on the payload).</li>
 *   <li>{@code $.warnings[?(@.ruleId=='DR-CTL-001')]} is the expected size for this rule.</li>
 *   <li>{@code $.warnings} total size, so an unrelated future rule cannot make tests pass silently.</li>
 * </ul>
 */
class CtlMissingWarningIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_CTL_WARNINGS = "$.warnings[?(@.ruleId=='DR-CTL-001')]";

    private static final String EXPECTED_MESSAGE =
            "This offence does not have a CTL. If the trial has started a CTL is not "
                    + "needed. It is your responsibility to check and confirm.";

    @Nested
    @DisplayName("WarnsWhenAllConditionsMet")
    class WarnsWhenAllConditionsMet {

        @Test
        void ri_result_no_existing_ctl_no_ctl_result_not_convicted_should_produce_warning()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "RI", "label": "Remand in custody",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false}
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
                    .andExpect(jsonPath(DR_CTL_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-CTL-001")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.warnings[0].message", is(EXPECTED_MESSAGE)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")));
        }
    }

    @Nested
    @DisplayName("BypassConditionsSuppressWarning")
    class BypassConditionsSuppressWarning {

        @Test
        void existing_ctl_record_should_suppress_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "RI", "label": "Remand in custody",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1, "hasExistingCtlRecord": true, "isConvicted": false}
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
                    .andExpect(jsonPath(DR_CTL_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }

        @Test
        void ctl_result_in_current_hearing_should_suppress_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "RI", "label": "Remand in custody",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"id": "rl2", "shortCode": "CTL", "label": "Custody time limit",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false}
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
                    .andExpect(jsonPath(DR_CTL_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }

        @Test
        void convicted_offence_should_suppress_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "RI", "label": "Remand in custody",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": true}
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
                    .andExpect(jsonPath(DR_CTL_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }

        @Test
        void no_trigger_result_should_produce_no_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false}
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
                    .andExpect(jsonPath(DR_CTL_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("MultiOffenceScoping")
    class MultiOffenceScoping {

        @Test
        void warning_scoped_to_breaching_offence_only() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "RI", "label": "Remand in custody",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"id": "rl2", "shortCode": "RI", "label": "Remand in custody",
                         "defendantId": "d1", "offenceId": "off2"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft A",
                         "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false},
                        {"id": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft B",
                         "orderIndex": 2, "hasExistingCtlRecord": true, "isConvicted": false}
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
                    .andExpect(jsonPath(DR_CTL_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")));
        }
    }
}
