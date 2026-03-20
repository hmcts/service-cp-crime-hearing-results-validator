package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ValidationControllerIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";

    private static final String EMPTY_ARRAYS_REQUEST = """
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

    @Test
    void validate_empty_arrays_should_return_valid() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_ARRAYS_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(true)))
                .andExpect(jsonPath("$.validationId", startsWith("val-")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.mode", is("advisory")))
                .andExpect(jsonPath("$.errors", empty()))
                .andExpect(jsonPath("$.warnings", empty()))
                .andExpect(jsonPath("$.rulesEvaluated", contains("DR-SENT-002")));
    }

    @Test
    void ac1_single_offence_without_info_should_be_valid() throws Exception {
        String request = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                    {"id": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "isConcurrent": true}
                  ],
                  "defendants": [{"id": "d1", "name": "John Doe"}],
                  "offences": [
                    {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"id": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

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
    void ac2_multiple_offences_missing_info_should_produce_error() throws Exception {
        String request = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                    {"id": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2"},
                    {"id": "rl3", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off3"},
                    {"id": "rl4", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off4", "isConcurrent": true}
                  ],
                  "defendants": [{"id": "d1", "name": "John Doe"}],
                  "offences": [
                    {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"id": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2},
                    {"id": "off3", "offenceCode": "BG001", "offenceTitle": "Burglary", "orderIndex": 3},
                    {"id": "off4", "offenceCode": "RB001", "offenceTitle": "Robbery", "orderIndex": 4}
                  ]
                }
                """;

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(false)))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].ruleId", is("DR-SENT-002")))
                .andExpect(jsonPath("$.errors[0].severity", is("ERROR")))
                .andExpect(jsonPath("$.errors[0].message", startsWith("John Doe offence/counts [2, 3]")));
    }

    @Test
    void ac3_offence_with_both_concurrent_and_consecutive_should_produce_warning() throws Exception {
        String request = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "CROWN",
                  "resultLines": [
                    {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                    {"id": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "isConcurrent": true, "consecutiveToOffence": "off1"}
                  ],
                  "defendants": [{"id": "d1", "name": "John Doe"}],
                  "offences": [
                    {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"id": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(true)))
                .andExpect(jsonPath("$.errors", empty()))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-SENT-002")))
                .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                .andExpect(jsonPath("$.warnings[0].message", startsWith("John Doe offence/counts [2]")));
    }

    @Test
    void ac4_all_offences_have_info_no_primary_should_produce_warning() throws Exception {
        String request = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"id": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1", "isConcurrent": true},
                    {"id": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "consecutiveToOffence": "off1"}
                  ],
                  "defendants": [{"id": "d1", "name": "John Doe"}],
                  "offences": [
                    {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"id": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(true)))
                .andExpect(jsonPath("$.errors", empty()))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-SENT-002")))
                .andExpect(jsonPath("$.warnings[0].message", startsWith("John Doe")));
    }

    @Test
    void validate_should_return_401_when_missing_CJSCPPUID_header() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_ARRAYS_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validate_should_return_400_when_no_request_body() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_should_return_400_when_malformed_json() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not valid json"))
                .andExpect(status().isBadRequest());
    }
}
