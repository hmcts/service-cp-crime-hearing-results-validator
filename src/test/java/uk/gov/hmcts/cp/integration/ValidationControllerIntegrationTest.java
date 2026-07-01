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

/**
 * End-to-end tests for the draft validation endpoint and its main acceptance scenarios.
 */
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

    /**
     * Covers the empty-hearing scenario where no data is present and the endpoint should return a
     * clean advisory response with no issues.
     */
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
                .andExpect(jsonPath("$.errors.validationIssues", empty()))
                .andExpect(jsonPath("$.warnings", empty()))
                .andExpect(jsonPath("$.rulesEvaluated", contains("DR-SENT-002", "DR-DISQ-001")));
    }

    /**
     * Covers AC1 where there is exactly one primary custodial sentence and the remaining custodial
     * offence already carries concurrency information, so no issue is raised.
     */
    @Test
    void ac1_single_offence_without_info_should_be_valid() throws Exception {
        String request = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "isConcurrent": true}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Doe"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
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
                .andExpect(jsonPath("$.errors.validationIssues", empty()))
                .andExpect(jsonPath("$.warnings", empty()));
    }

    /**
     * Covers AC2 where multiple non-primary custodial offences omit concurrent or consecutive
     * information, so the payload is invalid and an error is returned.
     */
    @Test
    void ac2_multiple_offences_missing_info_should_produce_error() throws Exception {
        String request = """
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

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(false)))
                .andExpect(jsonPath("$.errors.validationIssues", hasSize(1)))
                .andExpect(jsonPath("$.errors.validationIssues[0].ruleId", is("DR-SENT-002")))
                .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                .andExpect(jsonPath("$.errors.errorMessages[0]", is(
                        "Some offences do not include details of whether they are concurrent or"
                                + " consecutive. There should be only one primary sentence for each"
                                + " defendant, therefore one result without concurrent or consecutive"
                                + " information. This affects John Doe.")));
    }

    /**
     * Covers AC3 where one offence is marked both concurrent and consecutive, which should produce
     * a warning without invalidating the request.
     */
    @Test
    void ac3_offence_with_both_concurrent_and_consecutive_should_produce_warning() throws Exception {
        String request = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "CROWN",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "isConcurrent": true, "consecutiveToOffence": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Doe"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
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
                .andExpect(jsonPath("$.errors.validationIssues", empty()))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-SENT-002")))
                .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                .andExpect(jsonPath("$.warnings[0].errorMessages").doesNotExist())
                .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(
                        "This offence has both concurrent and consecutive information."
                                + " Check this is correct before sharing")));
    }

    /**
     * Covers AC4 where all custodial offences have relationship data and therefore no primary
     * sentence can be inferred, resulting in a warning only.
     */
    @Test
    void ac4_all_offences_have_info_no_primary_should_produce_warning() throws Exception {
        String request = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1", "isConcurrent": true},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "consecutiveToOffence": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Doe"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
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
                .andExpect(jsonPath("$.errors.validationIssues", empty()))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-SENT-002")))
                .andExpect(jsonPath("$.warnings[0].errorMessages").doesNotExist())
                .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].message", is(
                        "All offences include details of being concurrent or consecutive with no"
                                + " primary sentence. Check that this is correct before sharing")));
    }

    /**
     * Verifies the authentication filter rejects validation requests that omit the user header.
     */
    @Test
    void validate_should_return_401_when_missing_CJSCPPUID_header() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_ARRAYS_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies the API returns HTTP 400 when the request body is missing entirely.
     */
    @Test
    void validate_should_return_400_when_no_request_body() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies malformed JSON is translated into the standard bad-request response.
     */
    @Test
    void validate_should_return_400_when_malformed_json() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not valid json"))
                .andExpect(status().isBadRequest());
    }
    private static final String NO_HEARING_ID_ARRAYS_REQUEST = """
            {
               "caseId": "c1",
              "hearingDay": "2026-03-11",
              "courtType": "MAGISTRATES",
              "resultLines": [],
              "defendants": [],
              "offences": []
            }
            """;

    /**
     * Verifies invalid JSON with missing field is translated into the standard bad-request response.
     */
    @Test
    void validate_should_return_400_when_no_hearing_id_json() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                .header("CJSCPPUID", "test-user")
                .header("CPP-ACTION", "validation-service.validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(NO_HEARING_ID_ARRAYS_REQUEST))
            .andExpect(status().isBadRequest());
    }

    private static final String MISSING_RESULT_LINE_ID_REQUEST = """
            {
              "hearingId": "4d0af157-4f04-4f87-a368-1e1a3e970bc4",
              "hearingDay": "2026-07-08",
              "courtType": "MAGISTRATES",
              "resultLines": [
                {
                  "shortCode": "yroew",
                  "label": "Youth Rehabilitation Order England and Wales",
                  "defendantId": "503cae2e-9a0a-47b9-943a-50ed65b9700a",
                  "offenceId": "71a13191-4c5d-46a1-aad7-31b06db39b53",
                  "category": "F"
                },
                {
                  "resultLineId": "80c464b2-a2ec-40be-8492-57d98750d118",
                  "shortCode": "rehr",
                  "label": "Rehabilitation requirements",
                  "defendantId": "503cae2e-9a0a-47b9-943a-50ed65b9700a",
                  "offenceId": "71a13191-4c5d-46a1-aad7-31b06db39b53",
                  "category": "A"
                },
                {
                  "resultLineId": "525e701a-3008-472d-a0cc-e4d397acc65b",
                  "shortCode": "yrc2",
                  "label": "Youth rehabilitation requirement: Curfew",
                  "defendantId": "503cae2e-9a0a-47b9-943a-50ed65b9700a",
                  "offenceId": "71a13191-4c5d-46a1-aad7-31b06db39b53",
                  "category": "A"
                },
                {
                  "resultLineId": "1a08bb8a-2a9d-447b-82b3-4c3d2ec58e1f",
                  "shortCode": "emreq",
                  "label": "Is electronic monitoring required",
                  "defendantId": "503cae2e-9a0a-47b9-943a-50ed65b9700a",
                  "offenceId": "71a13191-4c5d-46a1-aad7-31b06db39b53",
                  "category": "I"
                }
              ],
              "defendants": [
                {
                  "defendantId": "503cae2e-9a0a-47b9-943a-50ed65b9700a",
                  "firstName": "MO",
                  "lastName": "MO Bloggs",
                  "masterDefendantId": "e617b06e-db09-4417-a0be-02933bee11f2"
                }
              ],
              "offences": [
                {
                  "offenceId": "71a13191-4c5d-46a1-aad7-31b06db39b53",
                  "offenceCode": "TH68010",
                  "offenceTitle": "Theft from a shop",
                  "orderIndex": 1,
                  "caseUrn": "19BH5952026",
                  "hasExistingCtlRecord": false,
                  "isConvicted": false
                }
              ]
            }
            """;

    /**
     * Verifies that a result line missing resultLineId returns a 400 ErrorResponse
     * with the correct field-level validation message.
     */
    @Test
    void validate_should_return_400_error_response_when_result_line_missing_result_line_id() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MISSING_RESULT_LINE_ID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("resultLines[0].resultLineId: must not be null")))
                .andExpect(jsonPath("$.traceId", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }
}
