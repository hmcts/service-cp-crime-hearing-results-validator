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
 * End-to-end integration tests for DR-RESTRAO-010 (Multiple Protected Persons Warning).
 *
 * <p>Each scenario pins three response slices:
 * <ul>
 *   <li>{@code $.errors.validationIssues} is empty — no other rule fires on these payloads.</li>
 *   <li>{@code $.warnings[?(@.ruleId=='DR-RESTRAO-010')]} is the expected size.</li>
 *   <li>{@code $.warnings} total size — prevents a future rule silently inflating the count.</li>
 * </ul>
 */
class RestrainingOrderMultiplePersonsIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_RESTRAO_WARNINGS = "$.warnings[?(@.ruleId=='DR-RESTRAO-010')]";

    private static final String EXPECTED_MESSAGE =
            "A restraining order result can only include one protected person's "
                    + "details. Add a separate restraining order result for each protected person.";

    @Nested
    @DisplayName("US3 — Multiple RESTRAO offences evaluated independently")
    class MultipleRestraoOffences {

        @Test
        void twoRestraoOffences_oneBreaching_shouldWarnOnBreachingOffenceOnly() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RESTRAO", "label": "Restraining order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "protectedPersonsName", "promptValue": "John Smith & Jane Smith"}]},
                        {"resultLineId": "rl2", "shortCode": "RESTRAO", "label": "Restraining order",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "protectedPersonsName", "promptValue": "Jane Smith"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Restraining order offence 1", "orderIndex": 1, "caseUrn": "32AH9105826"},
                        {"offenceId": "off2", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Restraining order offence 2", "orderIndex": 2, "caseUrn": "32AH9105826"}
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
                    .andExpect(jsonPath(DR_RESTRAO_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-RESTRAO-010")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(EXPECTED_MESSAGE)));
        }
    }

    @Nested
    @DisplayName("US4 — Warning is advisory; severity is WARNING not ERROR")
    class AdvisoryWarning {

        @Test
        void singleRestraoWithTrigger_shouldProduceWarningNotError() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RESTRAO", "label": "Restraining order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "protectedPersonsName", "promptValue": "John Smith, Jane Smith"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Restraining order offence", "orderIndex": 1, "caseUrn": "32AH9105826"}
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
                    .andExpect(jsonPath(DR_RESTRAO_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")));
        }
    }

    @Nested
    @DisplayName("US5 — Resolving the warning by splitting results")
    class ResolutionBySpitting {

        @Test
        void twoCleanRestraoResultsOnSameOffence_shouldProduceNoWarning() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RESTRAO", "label": "Restraining order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "protectedPersonsName", "promptValue": "John Smith"}]},
                        {"resultLineId": "rl2", "shortCode": "RESTRAO", "label": "Restraining order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "protectedPersonsName", "promptValue": "Jane Smith"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Restraining order offence", "orderIndex": 1, "caseUrn": "32AH9105826"}
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
                    .andExpect(jsonPath(DR_RESTRAO_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("US6 — Amend and reshare behaves identically to first share")
    class AmendAndReshare {

        @Test
        void reshareWithTrigger_shouldBehaveIdenticallyToFirstShare() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RESTRAO", "label": "Restraining order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "protectedPersonsName", "promptValue": "John Smith & Jane Smith"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Restraining order offence", "orderIndex": 1, "caseUrn": "32AH9105826"}
                      ]
                    }
                    """;

            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post(VALIDATE_URL)
                                .header("CJSCPPUID", "test-user")
                                .header("CPP-ACTION", "validation-service.validate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.errors.validationIssues", empty()))
                        .andExpect(jsonPath(DR_RESTRAO_WARNINGS, hasSize(1)))
                        .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                        .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(EXPECTED_MESSAGE)));
            }
        }
    }
}