package uk.gov.hmcts.cp.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * End-to-end integration tests for DR-SENT-011 (Custodial Sentence Against Non-Imprisonable
 * Offence – Warning). Each test uses a distinct {@code offenceCode} so that the shared
 * {@code referencedataOffences} Caffeine cache (keyed by {@code offenceCode}, alive for the
 * full Spring context lifetime) does not leak stubbed values across test methods.
 */
class NonImprisonableOffenceRuleIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_SENT_011_WARNINGS = "$.warnings[?(@.ruleId=='DR-SENT-011')]";
    private static final String EXPECTED_MESSAGE =
            "A custodial sentence may not be available for this offence. "
                    + "Check the sentence is correct before continuing.";

    @BeforeEach
    void resetWireMock() {
        REFERENCEDATA_OFFENCE_WIRE_MOCK.resetAll();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String buildRequest(String offenceCode, String shortCode) {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "label": "%s label",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                  "offences": [
                    {"offenceId": "off1", "defendantId": "d1", "offenceCode": "%s",
                     "offenceTitle": "Test offence", "orderIndex": 1, "caseUrn": "32AH9105826"}
                  ]
                }
                """.formatted(shortCode, shortCode, offenceCode);
    }

    private static String columnOnlyBody(String indicator) {
        final String value = indicator == null ? "null" : "\"" + indicator + "\"";
        return "{\"offences\":[{\"offenceId\":\"ref-id\",\"custodialIndicator\":" + value + "}]}";
    }

    private static String columnAndDetailsBody(String columnIndicator, String detailsCode) {
        final String colValue = columnIndicator == null ? "null" : "\"" + columnIndicator + "\"";
        return "{\"offences\":[{\"offenceId\":\"ref-id\",\"custodialIndicator\":" + colValue
                + ",\"details\":{\"document\":{\"libra\":{\"custodialindicator\":"
                + "{\"code\":\"" + detailsCode + "\"}}}}}]}";
    }

    // -----------------------------------------------------------------------
    // AC1 – imprisonable offence, column Y → no warning
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC1 – column Y → no warning")
    class Ac1ColumnY {

        @Test
        void custodialResult_columnY_should_notEmitWarning() throws Exception {
            stubReferencedataOffenceCustodialIndicator("ac1-col-y", columnOnlyBody("Y"));

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("ac1-col-y", "IMP")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_SENT_011_WARNINGS, hasSize(0)));
        }
    }

    // -----------------------------------------------------------------------
    // AC2 – non-imprisonable offence, column N → warning
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC2 – column N → warning")
    class Ac2ColumnN {

        @Test
        void custodialResult_columnN_should_emitWarning_withCorrectText() throws Exception {
            stubReferencedataOffenceCustodialIndicator("ac2-col-n", columnOnlyBody("N"));

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("ac2-col-n", "IMP")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_SENT_011_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-SENT-011")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.warnings[0].validationLevel", is("OFFENCE")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message",
                            is(EXPECTED_MESSAGE)));
        }
    }

    // -----------------------------------------------------------------------
    // AC3 – column blank, details_json code N → warning
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC3 – column null, details_json code N → warning")
    class Ac3ColumnBlankJsonN {

        @Test
        void custodialResult_columnNull_jsonCodeN_should_emitWarning() throws Exception {
            stubReferencedataOffenceCustodialIndicator(
                    "ac3-json-n", columnAndDetailsBody(null, "N"));

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("ac3-json-n", "IMP")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_SENT_011_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message",
                            is(EXPECTED_MESSAGE)));
        }
    }

    // -----------------------------------------------------------------------
    // AC4 – column blank, details_json code Y → no warning
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC4 – column null, details_json code Y → no warning")
    class Ac4ColumnBlankJsonY {

        @Test
        void custodialResult_columnNull_jsonCodeY_should_notEmitWarning() throws Exception {
            stubReferencedataOffenceCustodialIndicator(
                    "ac4-json-y", columnAndDetailsBody(null, "Y"));

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("ac4-json-y", "IMP")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_SENT_011_WARNINGS, hasSize(0)));
        }
    }

    // -----------------------------------------------------------------------
    // AC5 – column Y takes precedence over details_json code N → no warning
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC5 – column Y beats details_json N → no warning")
    class Ac5ColumnTakesPrecedence {

        @Test
        void custodialResult_columnY_jsonCodeN_should_notEmitWarning() throws Exception {
            stubReferencedataOffenceCustodialIndicator(
                    "ac5-col-y-json-n", columnAndDetailsBody("Y", "N"));

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("ac5-col-y-json-n", "IMP")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_SENT_011_WARNINGS, hasSize(0)));
        }
    }

    // -----------------------------------------------------------------------
    // AC6 – no indicator in either source → no warning (fail-open)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC6 – no indicator → no warning (fail-open)")
    class Ac6NoIndicator {

        @Test
        void custodialResult_noIndicator_should_notEmitWarning() throws Exception {
            stubReferencedataOffenceCustodialIndicator(
                    "ac6-no-ind", columnOnlyBody(null));

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("ac6-no-ind", "IMP")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_SENT_011_WARNINGS, hasSize(0)));
        }
    }

    // -----------------------------------------------------------------------
    // AC7 – non-custodial result → no warning, no reference-data call
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC7 – non-custodial result → no warning and no reference-data call")
    class Ac7NonCustodialResult {

        @Test
        void nonCustodialResult_should_notEmitWarning_andNotCallClient() throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest("ac7-non-cust", "COEW")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_SENT_011_WARNINGS, hasSize(0)));

            REFERENCEDATA_OFFENCE_WIRE_MOCK.verify(0,
                    getRequestedFor(urlPathEqualTo(REFERENCEDATA_OFFENCE_PATH)));
        }
    }

    // -----------------------------------------------------------------------
    // Breadth coverage – all 18 custodial short codes trigger the warning
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Breadth coverage – all 18 custodial short codes")
    class BreadthCoverage {

        @ParameterizedTest(name = "shortCode=''{0}'' + column N → one DR-SENT-011 warning")
        @ValueSource(strings = {
            "IMP", "YOI", "DTO", "EXTDVS", "EXTDVSU", "EXTIVS", "STSDY",
            "SPECC", "SPECCC", "SPECCD", "SUSPS", "SUSPSS", "SUSPSNI", "SUSPSNR",
            "SUSPSD", "SUSPSDS", "SUSPSDNI", "SUSPSDNR"
        })
        void custodialShortCode_columnN_should_emitWarning(String shortCode) throws Exception {
            final String offenceCode = "bc-" + shortCode.toLowerCase(Locale.ROOT);
            stubReferencedataOffenceCustodialIndicator(offenceCode, columnOnlyBody("N"));

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildRequest(offenceCode, shortCode)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_SENT_011_WARNINGS, hasSize(1)));
        }
    }

    // -----------------------------------------------------------------------
    // US5 – Coexistence: DR-SENT-011 + DR-RESTRAO-010
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("US5 – Coexistence with DR-RESTRAO-010")
    class OffenceLevelCoexistence {

        @Test
        void warningCoexistsWithRestrainingOrderWarning_shouldDisplayBoth() throws Exception {
            stubReferencedataOffenceCustodialIndicator("coex-imp-off", columnOnlyBody("N"));
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-03-11",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "IMP label",
                         "defendantId": "d1", "offenceId": "off-a"},
                        {"resultLineId": "rl2", "shortCode": "RESTRAO", "label": "RESTRAO label",
                         "defendantId": "d1", "offenceId": "off-b",
                         "prompts": [{"promptRef": "protectedPersonsName",
                                      "promptValue": "John Smith & Jane Smith"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-a", "defendantId": "d1", "offenceCode": "coex-imp-off",
                         "offenceTitle": "Speeding", "orderIndex": 1, "caseUrn": "32AH9105826"},
                        {"offenceId": "off-b", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Restraining order offence", "orderIndex": 2,
                         "caseUrn": "32AH9105826"}
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
                    .andExpect(jsonPath(DR_SENT_011_WARNINGS, hasSize(1)))
                    // TODO CRA-260: after DR-RESTRAO-010 merges to main and this branch is rebased,
                    // change hasSize(0) → hasSize(1) for DR-RESTRAO-010 and hasSize(1) → hasSize(2) for $.warnings
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-RESTRAO-010')]", hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)));
        }
    }

    // -----------------------------------------------------------------------
    // US5 – Coexistence: DR-SENT-011 + DR-SENT-001 (defendant level)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("US5 – Coexistence with DR-SENT-001 defendant-level warning")
    class DefendantLevelCoexistence {

        @Test
        void custodialAllConcurrentWithOneNonImprisonable_should_emitBothWarnings()
                throws Exception {
            stubReferencedataOffenceCustodialIndicator("coex-def-off1", columnOnlyBody("Y"));
            stubReferencedataOffenceCustodialIndicator("coex-def-off2", columnOnlyBody("Y"));
            stubReferencedataOffenceCustodialIndicator("coex-def-off3", columnOnlyBody("N"));
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-03-11",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "IMP label",
                         "defendantId": "d1", "offenceId": "off1", "isConcurrent": true},
                        {"resultLineId": "rl2", "shortCode": "IMP", "label": "IMP label",
                         "defendantId": "d1", "offenceId": "off2",
                         "consecutiveToOffence": "off1"},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "IMP label",
                         "defendantId": "d1", "offenceId": "off3", "isConcurrent": true}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "coex-def-off1",
                         "offenceTitle": "Theft A", "orderIndex": 1},
                        {"offenceId": "off2", "defendantId": "d1", "offenceCode": "coex-def-off2",
                         "offenceTitle": "Theft B", "orderIndex": 2},
                        {"offenceId": "off3", "defendantId": "d1", "offenceCode": "coex-def-off3",
                         "offenceTitle": "Theft C", "orderIndex": 3}
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
                    .andExpect(jsonPath(DR_SENT_011_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath(
                            "$.warnings[?(@.ruleId=='DR-SENT-001' && @.validationLevel=='DEFENDANT')]",
                            hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(2)));
        }
    }
}
