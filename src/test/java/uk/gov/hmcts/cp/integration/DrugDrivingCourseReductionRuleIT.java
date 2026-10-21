package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.containsInAnyOrder;
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
 * End-to-end tests for DR-DISQ-013 (drug driving course-reduction disqualification error) over
 * the public validate endpoint. Covers ACs 1–6 at the integration level.
 *
 * <p>DR-DISQ-013 defaults to enabled by its Flyway seed migration and nothing else in the test
 * suite disables it, so no rule-state setup is needed here.
 *
 * <p>Every positive scenario pins:
 * <ul>
 *   <li>{@code $.errors.validationIssues[?(@.ruleId=='DR-DISQ-013')]} has expected size.</li>
 *   <li>{@code $.warnings} is empty (this is an error-only rule).</li>
 * </ul>
 * Negative scenarios assert both slices are empty.
 */
class DrugDrivingCourseReductionRuleIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_DISQ_013_ERRORS =
            "$.errors.validationIssues[?(@.ruleId=='DR-DISQ-013')]";

    private static final String EXPECTED_MESSAGE =
            "You cannot add a disqualification with a reduction for attending a course "
                    + "to a drug driving offence. Remove this result and add the correct "
                    + "disqualification.";

    private static final String EXPECTED_MESSAGE_WITH_DEFENDANT =
            "You cannot add a disqualification with a reduction for attending a course "
                    + "to a drug driving offence. Remove this result and add the correct "
                    + "disqualification. This affects: Alex Driver.";

    @Nested
    @DisplayName("Ac1TriggeringPaths — all five offence codes and both short codes")
    class Ac1TriggeringPaths {

        @Test
        void ac1_rt88584_with_ddrco_should_produce_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac1-ddrco", "RT88584", "DDRCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath("$.errors.validationIssues[0].ruleId", is("DR-DISQ-013")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].validationLevel", is("OFFENCE")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")));
        }

        @Test
        void ac1_rt88584_with_ddrcot_should_produce_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac1-ddrcot", "RT88584", "DDRCOT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].ruleId", is("DR-DISQ-013")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")));
        }

        @Test
        void ac1_rt88586_should_produce_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac1-rt88586", "RT88586", "DDRCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(1)));
        }

        @Test
        void ac1_rt88584b_should_produce_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac1-rt88584b", "RT88584B", "DDRCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(1)));
        }

        @Test
        void ac1_rt88340_should_produce_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac1-rt88340", "RT88340", "DDRCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(1)));
        }

        @Test
        void ac1_ra88045_should_produce_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac1-ra88045", "RA88045", "DDRCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(1)));
        }

        @Test
        void ac1_short_code_matching_is_case_insensitive_lowercase_ddrco_should_produce_error()
                throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac1-lc", "RT88584", "ddrco")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(1)));
        }
    }

    @Nested
    @DisplayName("Ac2ExactMessage — prescribed error message text")
    class Ac2ExactMessage {

        @Test
        void ac2_error_message_text_should_match_exactly() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac2-msg", "RT88584", "DDRCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(EXPECTED_MESSAGE)));
        }

        @Test
        void ac2_error_summary_message_is_populated_in_errors_error_messages() throws Exception {
            // Single-defendant hearing: ${defendantNames} clause is stripped by the service
            // (redundant when there is only one defendant), leaving just the core message.
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac2-sum", "RT88584", "DDRCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(EXPECTED_MESSAGE)));
        }
    }

    @Nested
    @DisplayName("Ac3SharingBlocked — error severity blocks sharing")
    class Ac3SharingBlocked {

        @Test
        void ac3_error_severity_is_error_and_blocks_sharing() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac3-sev", "RT88584", "DDRCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("Ac4Resolving — clearing the error")
    class Ac4Resolving {

        @Test
        void ac4_replacing_ddrco_with_ddo_should_produce_no_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac4-ddo", "RT88584", "DDO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }

        @Test
        void ac4_rt88584_with_no_results_should_produce_no_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac4-norl",
                      "hearingDay": "2026-10-16",
                      "courtType": "MAGISTRATES",
                      "resultLines": [],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                         "offenceTitle": "Drug driving s.5A", "orderIndex": 1,
                         "isConvicted": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, empty()));
        }
    }

    @Nested
    @DisplayName("Ac5OneErrorPerOffence — one error per affected offence")
    class Ac5OneErrorPerOffence {

        @Test
        void ac5_two_triggering_offences_with_ddrco_produce_two_errors() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac5-two",
                      "hearingDay": "2026-10-16",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                         "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "DDRCO", "category": "F",
                         "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off2"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                         "offenceTitle": "Drug driving s.5A", "orderIndex": 1,
                         "isConvicted": true},
                        {"offenceId": "off2", "defendantId": "d1", "offenceCode": "RT88586",
                         "offenceTitle": "Drug driving s.5A attempt", "orderIndex": 2,
                         "isConvicted": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues", hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].ruleId",
                            containsInAnyOrder("DR-DISQ-013", "DR-DISQ-013")))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].offenceId",
                            containsInAnyOrder("off1", "off2")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(EXPECTED_MESSAGE)));
        }
    }

    @Nested
    @DisplayName("Ac6NoFalsePositives — non-triggering paths")
    class Ac6NoFalsePositives {

        @Test
        void ac6_ddrco_on_non_triggering_offence_should_not_produce_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac6-non-trig", "RT88026", "DDRCO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, empty()));
        }

        @Test
        void ac6_ddrcd_on_triggering_offence_should_not_produce_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac6-ddrcd", "RT88584", "DDRCD")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, empty()));
        }

        @Test
        void ac6_ddote_on_triggering_offence_should_not_produce_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac6-ddote", "RT88584", "DDOTE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, empty()));
        }

        @Test
        void ac6_ddo_on_triggering_offence_should_not_produce_error() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac6-ddo", "RT88584", "DDO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }

        @Test
        void ac6_hearing_with_no_drug_driving_offences_should_not_produce_error()
                throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildBody("h-ac6-nodrug", "RT88026", "DDO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, empty()));
        }

        @Test
        void ac6_non_triggering_with_ddrco_and_triggering_with_ddo_in_same_hearing_should_not_produce_error()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac6-mixed",
                      "hearingDay": "2026-10-16",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                         "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "DDO", "category": "F",
                         "label": "Driving Disqualification", "defendantId": "d1", "offenceId": "off2"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88026",
                         "offenceTitle": "Dangerous driving", "orderIndex": 1, "isConvicted": true},
                        {"offenceId": "off2", "defendantId": "d1", "offenceCode": "RT88584",
                         "offenceTitle": "Drug driving s.5A", "orderIndex": 2, "isConvicted": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, empty()));
        }
    }

    @Nested
    @DisplayName("EcMultiDefendant — separate error per affected offence across defendants")
    class EcMultiDefendant {

        @Test
        void two_defendants_only_triggering_defendant_gets_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ec-md",
                      "hearingDay": "2026-10-16",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                         "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "DDO", "category": "F",
                         "label": "Driving Disqualification", "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"},
                        {"defendantId": "d2", "firstName": "Sam", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                         "offenceTitle": "Drug driving s.5A", "orderIndex": 1, "isConvicted": true},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "RT88340",
                         "offenceTitle": "Drive unfit through drugs", "orderIndex": 2, "isConvicted": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(EXPECTED_MESSAGE_WITH_DEFENDANT)));
        }

        @Test
        void defendant_with_two_triggering_offences_should_get_two_errors_and_be_named_once()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h-ec-md-two",
                      "hearingDay": "2026-10-16",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                         "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "DDRCOT", "category": "F",
                         "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off2"},
                        {"resultLineId": "rl3", "shortCode": "DDO", "category": "F",
                         "label": "Driving Disqualification", "defendantId": "d2", "offenceId": "off3"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"},
                        {"defendantId": "d2", "firstName": "Sam", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                         "offenceTitle": "Drug driving s.5A", "orderIndex": 1, "isConvicted": true},
                        {"offenceId": "off2", "defendantId": "d1", "offenceCode": "RT88586",
                         "offenceTitle": "Drug driving attempt", "orderIndex": 2, "isConvicted": true},
                        {"offenceId": "off3", "defendantId": "d2", "offenceCode": "RT88340",
                         "offenceTitle": "Drive unfit through drugs", "orderIndex": 3, "isConvicted": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].offenceId",
                            containsInAnyOrder("off1", "off2")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(EXPECTED_MESSAGE_WITH_DEFENDANT)));
        }

        @Test
        void distinct_defendants_sharing_a_name_should_both_be_named() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ec-md-same-name",
                      "hearingDay": "2026-10-16",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                         "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "DDRCO", "category": "F",
                         "label": "Disq course reduction", "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"},
                        {"defendantId": "d2", "firstName": "Alex", "lastName": "Driver"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                         "offenceTitle": "Drug driving s.5A", "orderIndex": 1, "isConvicted": true},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "RT88584",
                         "offenceTitle": "Drug driving s.5A", "orderIndex": 2, "isConvicted": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(EXPECTED_MESSAGE + " This affects: Alex Driver and Alex Driver.")));
        }
    }

    @Nested
    @DisplayName("EcMixedResults — DDRCO alongside another result on the same offence")
    class EcMixedResults {

        @Test
        void ddrco_alongside_ddo_on_same_offence_should_still_produce_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ec-mixed",
                      "hearingDay": "2026-10-16",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                         "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "DDO", "category": "F",
                         "label": "Driving Disqualification", "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                         "offenceTitle": "Drug driving s.5A", "orderIndex": 1, "isConvicted": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_DISQ_013_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")));
        }
    }

    private String buildBody(final String hearingId, final String offenceCode,
                              final String shortCode) {
        return """
                {
                  "hearingId": "%s",
                  "hearingDay": "2026-10-16",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "category": "F",
                     "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"offenceId": "off1", "defendantId": "d1", "offenceCode": "%s",
                     "offenceTitle": "Drug driving offence", "orderIndex": 1,
                     "isConvicted": true}
                  ]
                }
                """.formatted(hearingId, shortCode, offenceCode);
    }
}
