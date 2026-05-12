package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.containsString;
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
 * End-to-end tests for DR-COEW-001 (community order end-date validation) over the public
 * validate endpoint.
 *
 * <p>Hearing day is pinned to 2026-01-15 throughout so date arithmetic is deterministic.
 */
class CommunityOrderEndDateValidationIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_COEW_ERRORS = "$.errors[?(@.ruleId=='DR-COEW-001')]";
    private static final String AC1_MESSAGE = "The end date must be in the future";
    private static final String AC2A_MESSAGE =
            "The end date of the order must match or be longer than the end date of "
                    + "Curfew (community requirement) - CUR";
    private static final String AC2B_MESSAGE =
            "The end date of the order must match or be longer than the end date of "
                    + "Curfew with electronic monitoring - CURE";
    private static final String AC2C_MESSAGE =
            "The end date of the order must match or be longer than the end date of "
                    + "Further curfew requirement made - CURA";
    private static final String AC2D_MESSAGE =
            "The end date of the order must match or be longer than the end date of "
                    + "Alcohol abstinence and monitoring - AAR";
    private static final String AC3_MESSAGE =
            "The end date of the order must be at least 12 months as it includes "
                    + "an unpaid work requirement";

    @Nested
    @DisplayName("Ac1EndDateNotInFuture")
    class Ac1EndDateNotInFuture {

        @Test
        void validate_coewEndDateIsToday_should_returnAc1Error() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-01-15"}
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].ruleId", is("DR-COEW-001")))
                    .andExpect(jsonPath("$.errors[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors[0].message", is(AC1_MESSAGE)));
        }

        @Test
        void validate_coewEndDateInPast_should_returnAc1Error() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-01-14"}
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].ruleId", is("DR-COEW-001")))
                    .andExpect(jsonPath("$.errors[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors[0].message", is(AC1_MESSAGE)));
        }

        @Test
        void validate_coewEndDateInFuture_should_returnNoAc1Error() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-01-16"}
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, empty()));
        }
    }

    @Nested
    @DisplayName("Ac2RequirementEndDateViolation")
    class Ac2RequirementEndDateViolation {

        @Test
        void validate_coewEndDateBeforeCurEndDate_should_returnAc2aError() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-10-30"},
                        {"id": "rl2", "shortCode": "CUR", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-11-30"}
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].ruleId", is("DR-COEW-001")))
                    .andExpect(jsonPath("$.errors[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors[0].message", containsString("CUR")));
        }

        @Test
        void validate_coewEndDateBeforeCureEndDate_should_returnAc2bError() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-10-30"},
                        {"id": "rl2", "shortCode": "CURE", "label": "Curfew with tag",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-11-30"}
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].ruleId", is("DR-COEW-001")))
                    .andExpect(jsonPath("$.errors[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors[0].message", containsString("CURE")));
        }

        @Test
        void validate_coewEndDateBeforeCuraEndDate_should_returnAc2cError() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-10-30"},
                        {"id": "rl2", "shortCode": "CURA", "label": "Further curfew",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-11-30"}
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].ruleId", is("DR-COEW-001")))
                    .andExpect(jsonPath("$.errors[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors[0].message", containsString("CURA")));
        }

        @Test
        void validate_coewEndDateBeforeAarEndDate_should_returnAc2dError() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-10-30"},
                        {"id": "rl2", "shortCode": "AAR", "label": "Alcohol abstinence",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-11-30"}
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].ruleId", is("DR-COEW-001")))
                    .andExpect(jsonPath("$.errors[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors[0].message", containsString("AAR")));
        }

        @Test
        void validate_coewEndDateMatchingRequirementEndDate_should_returnNoAc2Error() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-11-30"},
                        {"id": "rl2", "shortCode": "CUR", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-11-30"}
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, empty()));
        }

        @Test
        void validate_multipleRequirementsViolated_should_returnAllAc2Errors() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-10-30"},
                        {"id": "rl2", "shortCode": "CUR", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-11-30"},
                        {"id": "rl3", "shortCode": "CURE", "label": "Curfew with tag",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-12-31"}
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(2)));
        }
    }

    @Nested
    @DisplayName("Ac3UpwrMinimumDuration")
    class Ac3UpwrMinimumDuration {

        @Test
        void validate_coewWithUpwrAndShortDuration_should_returnAc3Error() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2027-01-14"},
                        {"id": "rl2", "shortCode": "UPWR", "label": "Unpaid work",
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].ruleId", is("DR-COEW-001")))
                    .andExpect(jsonPath("$.errors[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors[0].message", is(AC3_MESSAGE)));
        }

        @Test
        void validate_coewWithUpwrAndExactly12Months_should_returnNoAc3Error() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2027-01-15"},
                        {"id": "rl2", "shortCode": "UPWR", "label": "Unpaid work",
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, empty()));
        }

        @Test
        void validate_coewWithUpwrAndOver12Months_should_returnNoAc3Error() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2027-06-15"},
                        {"id": "rl2", "shortCode": "UPWR", "label": "Unpaid work",
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, empty()));
        }

        @Test
        void validate_coewWithNoUpwr_should_returnNoAc3Error() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2027-01-14"}
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, empty()));
        }

        @Test
        void validate_allConstraintsSatisfied_should_returnNoErrors() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2027-06-15"},
                        {"id": "rl2", "shortCode": "CUR", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-06-15"},
                        {"id": "rl3", "shortCode": "UPWR", "label": "Unpaid work",
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, empty()));
        }

        @Test
        void validate_multipleConditionsFire_should_returnAllErrors() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-01-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl1", "shortCode": "COEW", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1", "endDate": "2026-01-15"},
                        {"id": "rl2", "shortCode": "UPWR", "label": "Unpaid work",
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
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(2)));
        }
    }
}
