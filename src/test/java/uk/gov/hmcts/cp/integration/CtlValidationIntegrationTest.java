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
 * Integration tests for DR-CTL-001: missing CTL for remanded offence.
 */
class CtlValidationIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";

    private static final String OFFENCE_ID = "11111111-1111-1111-1111-111111111111";

    private static final String CTL_WARNING_MESSAGE =
            "This offence does not have a CTL. If the trial has started a CTL is not needed. "
                    + "It is your responsibility to check and confirm.";

    @Nested
    @DisplayName("AC1 – Warning should fire")
    class WarningShouldFire {

        @Test
        @DisplayName("RI result + no CTL + not convicted → WARNING from DR-CTL-001")
        void ri_result_no_ctl_not_convicted_should_produce_warning() throws Exception {
            String request = """
                    {
                      "validationRequest": {
                        "hearingId": "h1",
                        "hearingDay": "2026-03-11",
                        "courtType": "MAGISTRATES",
                        "resultLines": [
                          {"id": "rl1", "shortCode": "RI", "label": "Remanded in custody",
                           "defendantId": "d1", "offenceId": "%s"}
                        ],
                        "defendants": [{"id": "d1", "firstName": "Jane", "lastName": "Smith"}],
                        "offences": [
                          {"id": "%s", "offenceCode": "TH68001", "offenceTitle": "Robbery", "orderIndex": 1}
                        ]
                      },
                      "offenceConvictions": []
                    }
                    """.formatted(OFFENCE_ID, OFFENCE_ID);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.errors", empty()))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-CTL-001")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.warnings[0].message", is(CTL_WARNING_MESSAGE)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is(OFFENCE_ID)));
        }
    }

    @Nested
    @DisplayName("AC1 – Warning should not fire")
    class WarningShouldNotFire {

        @Test
        @DisplayName("RI result + CTL present → no warning")
        void ri_result_with_ctl_present_should_produce_no_warning() throws Exception {
            String request = """
                    {
                      "validationRequest": {
                        "hearingId": "h1",
                        "hearingDay": "2026-03-11",
                        "courtType": "MAGISTRATES",
                        "resultLines": [
                          {"id": "rl1", "shortCode": "RI",  "label": "Remanded in custody",
                           "defendantId": "d1", "offenceId": "%s"},
                          {"id": "rl2", "shortCode": "CTL", "label": "Custody Time Limit",
                           "defendantId": "d1", "offenceId": "%s"}
                        ],
                        "defendants": [{"id": "d1", "firstName": "Jane", "lastName": "Smith"}],
                        "offences": [
                          {"id": "%s", "offenceCode": "TH68001", "offenceTitle": "Robbery", "orderIndex": 1}
                        ]
                      },
                      "offenceConvictions": []
                    }
                    """.formatted(OFFENCE_ID, OFFENCE_ID, OFFENCE_ID);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.errors", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }

        @Test
        @DisplayName("RI result + offence convicted → no warning")
        void ri_result_convicted_offence_should_produce_no_warning() throws Exception {
            String request = """
                    {
                      "validationRequest": {
                        "hearingId": "h1",
                        "hearingDay": "2026-03-11",
                        "courtType": "MAGISTRATES",
                        "resultLines": [
                          {"id": "rl1", "shortCode": "RI", "label": "Remanded in custody",
                           "defendantId": "d1", "offenceId": "%s"}
                        ],
                        "defendants": [{"id": "d1", "firstName": "Jane", "lastName": "Smith"}],
                        "offences": [
                          {"id": "%s", "offenceCode": "TH68001", "offenceTitle": "Robbery", "orderIndex": 1}
                        ]
                      },
                      "offenceConvictions": [
                        {"offenceId": "%s", "convicted": true}
                      ]
                    }
                    """.formatted(OFFENCE_ID, OFFENCE_ID, OFFENCE_ID);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.errors", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }

        @Test
        @DisplayName("Non-relevant result code only → no CTL warning")
        void non_relevant_result_only_should_produce_no_ctl_warning() throws Exception {
            String request = """
                    {
                      "validationRequest": {
                        "hearingId": "h1",
                        "hearingDay": "2026-03-11",
                        "courtType": "MAGISTRATES",
                        "resultLines": [
                          {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                           "defendantId": "d1", "offenceId": "%s"}
                        ],
                        "defendants": [{"id": "d1", "firstName": "Jane", "lastName": "Smith"}],
                        "offences": [
                          {"id": "%s", "offenceCode": "TH68001", "offenceTitle": "Robbery", "orderIndex": 1}
                        ]
                      },
                      "offenceConvictions": []
                    }
                    """.formatted(OFFENCE_ID, OFFENCE_ID);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.errors", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }
    }
}
