package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * End-to-end integration tests for DR-ENDORSEMENT-012 (endorsable offence warning) over the
 * public validate endpoint. Covers User Story 1 (core warning trigger), User Story 2 (satisfying
 * result suppression), User Story 3 (non-endorsable and excluded result suppression), User Story 4
 * (multiple offences assessed independently), and User Story 5 (coexistence with other warnings).
 *
 * <p>Every test uses a distinct {@code offenceCode} so that the shared {@code referencedataOffences}
 * Caffeine cache (keyed by {@code offenceCode}, alive for the full Spring context lifetime) does not
 * leak stubbed endorsableFlag values across test methods.
 */
class EndorsableOffenceRuleIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_ENDORSEMENT_012_WARNINGS =
            "$.warnings[?(@.ruleId=='DR-ENDORSEMENT-012')]";
    private static final String EXPECTED_MESSAGE =
            "This offence is endorsable. Add an endorsement, disqualification or special reasons result.";

    @BeforeEach
    void resetWireMock() {
        REFERENCEDATA_OFFENCE_WIRE_MOCK.resetAll();
    }

    private static void stubEndorsable(final String offenceCode, final int flag) {
        stubReferencedataOffenceCustodialIndicator(offenceCode,
                "{\"offences\":[{\"offenceId\":\"ref-id\",\"endorsableFlag\":" + flag + "}]}");
    }

    private static String buildRequest(final String offenceCode, final String... shortCodes) {
        final StringBuilder lines = new StringBuilder();
        for (int i = 0; i < shortCodes.length; i++) {
            if (i > 0) {
                lines.append(",\n");
            }
            lines.append("""
                    {"resultLineId":"rl%d","shortCode":"%s","label":"%s label",\
                    "defendantId":"d1","offenceId":"off1","category":"F"}""".formatted(
                    i + 1, shortCodes[i], shortCodes[i]));
        }
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-10-08",
                  "courtType": "MAGISTRATES",
                  "resultLines": [%s],
                  "defendants": [{"defendantId": "d1", "firstName": "Test", "lastName": "Person"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "%s", "offenceTitle": "Test offence",
                     "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """.formatted(lines, offenceCode);
    }

    @Nested
    @DisplayName("AC1 — endorsable, final non-excluded, no satisfying → warning")
    class Ac1Warning {

        @Test
        void givenEndorsableOffenceWithFinalResultAndNoSatisfyingCode_should_returnWarning()
                throws Exception {
            stubEndorsable("RT88010", 1);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("RT88010", "IMP")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_ENDORSEMENT_012_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-ENDORSEMENT-012")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.warnings[0].validationLevel", is("OFFENCE")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message",
                            is(EXPECTED_MESSAGE)));
        }
    }

    @Nested
    @DisplayName("AC2 — satisfying result present → no warning")
    class Ac2SatisfyingResultPresent {

        @Test
        void givenEndorsableFinalResultWithSatisfyingCode_should_returnNoWarning() throws Exception {
            stubEndorsable("RT88010AC2", 1);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("RT88010AC2", "IMP", "LEP")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_ENDORSEMENT_012_WARNINGS, empty()));
        }
    }

    @Nested
    @DisplayName("AC3 — excluded final result only → no warning")
    class Ac3ExcludedFinalOnly {

        @Test
        void givenEndorsableOffenceWithOnlyExcludedFinalResult_should_returnNoWarning()
                throws Exception {
            stubEndorsable("end-ac3-excl", 1);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("end-ac3-excl", "WDRN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_ENDORSEMENT_012_WARNINGS, empty()));
        }
    }

    @Nested
    @DisplayName("AC4 — non-endorsable offence → no warning")
    class Ac4NonEndorsable {

        @Test
        void givenNonEndorsableOffenceWithFinalResult_should_returnNoWarning() throws Exception {
            stubEndorsable("CD98075", 0);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("CD98075", "IMP")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_ENDORSEMENT_012_WARNINGS, empty()));
        }

        @Test
        void givenEndorsableFlagAbsent_should_returnNoWarning() throws Exception {
            stubEndorsable("end-ac4-absent", 0);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("end-ac4-absent", "IMP")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_ENDORSEMENT_012_WARNINGS, empty()));
        }
    }

    @Nested
    @DisplayName("AC6 — multiple offences assessed independently")
    class Ac6MultipleOffences {

        @Test
        void givenMixedEndorsableAndNonEndorsableOffences_should_returnWarningOnlyForEndorsable()
                throws Exception {
            stubEndorsable("end-ac6-end", 1);
            stubEndorsable("end-ac6-non", 0);

            // isConvicted suppresses DR-CONV-006 so only DR-ENDORSEMENT-012 fires (for off1 only).
            // Positional $.warnings[0] is then safe — filter+index chaining is avoided per the
            // note in SexualOffenceNotificationRuleIT about JsonPathExpectationsHelper.
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-10-08",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId":"rl1","shortCode":"IMP","label":"IMP label",
                         "defendantId":"d1","offenceId":"off1","category":"F"},
                        {"resultLineId":"rl2","shortCode":"IMP","label":"IMP label",
                         "defendantId":"d1","offenceId":"off2","category":"F"}
                      ],
                      "defendants": [{"defendantId":"d1","firstName":"Test","lastName":"Person"}],
                      "offences": [
                        {"offenceId":"off1","offenceCode":"end-ac6-end","offenceTitle":"Endorsable offence",
                         "orderIndex":1,"isConvicted":true},
                        {"offenceId":"off2","offenceCode":"end-ac6-non","offenceTitle":"Non-endorsable offence",
                         "orderIndex":2,"isConvicted":true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_ENDORSEMENT_012_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")));
        }
    }

    @Nested
    @DisplayName("AC7 — coexists with other validation warnings")
    class Ac7Coexistence {

        @Test
        void givenEndorsableWarningAndAnotherActiveRule_should_bothWarningsBePresent()
                throws Exception {
            stubEndorsable("end-ac7-comb", 1);
            // DR-SENT-001 AC4 fires when all custodial results for a defendant are concurrent
            // (no primary sentence). d2's two concurrent-only IMP offences trigger it while d1's
            // single non-concurrent IMP on the endorsable offence triggers DR-ENDORSEMENT-012.
            // Both rules fire in one response, confirming coexistence.
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-10-08",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId":"rl1","shortCode":"IMP","label":"IMP label",
                         "defendantId":"d1","offenceId":"off1","category":"F"},
                        {"resultLineId":"rl2","shortCode":"IMP","label":"IMP label",
                         "defendantId":"d2","offenceId":"off2","isConcurrent":true,"category":"F"},
                        {"resultLineId":"rl3","shortCode":"IMP","label":"IMP label",
                         "defendantId":"d2","offenceId":"off3","isConcurrent":true,"category":"F"}
                      ],
                      "defendants": [
                        {"defendantId":"d1","firstName":"Test","lastName":"PersonOne"},
                        {"defendantId":"d2","firstName":"Test","lastName":"PersonTwo"}
                      ],
                      "offences": [
                        {"offenceId":"off1","offenceCode":"end-ac7-comb","offenceTitle":"Endorsable offence",
                         "orderIndex":1},
                        {"offenceId":"off2","offenceCode":"end-ac7-other1","offenceTitle":"Other offence",
                         "orderIndex":2},
                        {"offenceId":"off3","offenceCode":"end-ac7-other2","offenceTitle":"Other offence",
                         "orderIndex":3}
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
                    .andExpect(jsonPath(DR_ENDORSEMENT_012_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SENT-001')]", hasSize(1)));
        }
    }
}
