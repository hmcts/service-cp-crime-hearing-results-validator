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
 * End-to-end tests for DR-CONV-006 (no conviction warning) over the public validate endpoint.
 *
 * <p>DR-CONV-006 defaults to enabled by its Flyway seed migration
 * ({@code V1.007__insert_dr_conv_006.sql}) and nothing else in the test suite disables it, so no
 * rule-state setup is needed here.
 *
 * <p>Every scenario pins three response slices:
 * <ul>
 *   <li>{@code $.errors.validationIssues} is empty (no other rule produced an error on the payload).</li>
 *   <li>{@code $.warnings[?(@.ruleId=='DR-CONV-006')]} is the expected size for this rule.</li>
 *   <li>{@code $.warnings} total size, so an unrelated future rule cannot make tests pass silently.</li>
 * </ul>
 */
class NoConvictionWarningIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_CONV_WARNINGS = "$.warnings[?(@.ruleId=='DR-CONV-006')]";

    private static final String EXPECTED_MESSAGE =
            "No conviction has been added against the offence. Check whether you need to add a "
                    + "guilty plea or verdict";

    @Nested
    @DisplayName("WarnsWhenSentencedAndUnconvicted — US1")
    class WarnsWhenSentencedAndUnconvicted {

        @Test
        void final_non_excluded_result_with_no_conviction_should_produce_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-31",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "FO", "label": "Fine",
                         "defendantId": "d1", "offenceId": "off1", "category": "F"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1, "isConvicted": false}
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
                    .andExpect(jsonPath(DR_CONV_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-CONV-006")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(EXPECTED_MESSAGE)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")));
        }
    }

    @Nested
    @DisplayName("BypassConditionsSuppressWarning — US1")
    class BypassConditionsSuppressWarning {

        @Test
        void excluded_final_short_code_should_suppress_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-31",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "wdrn", "label": "Withdrawn",
                         "defendantId": "d1", "offenceId": "off1", "category": "F"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1, "isConvicted": false}
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
                    .andExpect(jsonPath(DR_CONV_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }

        @Test
        void no_final_result_yet_should_produce_no_warning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-31",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "PLEA", "label": "Plea entered",
                         "defendantId": "d1", "offenceId": "off1", "category": "I"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1, "isConvicted": false}
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
                    .andExpect(jsonPath(DR_CONV_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("ConvictionClearsWarning — US2 (AC1A / AC1B)")
    class ConvictionClearsWarning {

        @Test
        void convicted_offence_should_suppress_warning_despite_qualifying_final_result()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-31",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "FO", "label": "Fine",
                         "defendantId": "d1", "offenceId": "off1", "category": "F"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1, "isConvicted": true}
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
                    .andExpect(jsonPath(DR_CONV_WARNINGS, hasSize(0)))
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
                      "hearingDay": "2026-07-31",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "FO", "label": "Fine",
                         "defendantId": "d1", "offenceId": "off1", "category": "F"},
                        {"resultLineId": "rl2", "shortCode": "wdrn", "label": "Withdrawn",
                         "defendantId": "d1", "offenceId": "off2", "category": "F"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft A",
                         "orderIndex": 1, "isConvicted": false},
                        {"offenceId": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft B",
                         "orderIndex": 2, "isConvicted": false}
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
                    .andExpect(jsonPath(DR_CONV_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")));
        }
    }
}
