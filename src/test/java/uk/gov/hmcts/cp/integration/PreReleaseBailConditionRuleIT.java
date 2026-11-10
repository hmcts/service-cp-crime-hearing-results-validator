package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * End-to-end integration tests for DR-BAIL-016 (Pre-release bail condition warning).
 *
 * <p>Each test posts a JSON payload to the validate endpoint and asserts the response contains
 * exactly the expected DR-BAIL-016 warnings (or none). No WireMock stubs are required — this
 * rule evaluates purely in-memory from {@code DraftValidationRequest} data.
 *
 * <p>Covered acceptance criteria: AC1, AC2, AC2A, AC2B, AC3 (EN + CY + no-keyword),
 * AC4 (sharing not blocked), AC4A (coexistence), AC5 (no false positives), AC6 (no DOB), FR-010.
 */
class PreReleaseBailConditionRuleIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_BAIL_016_WARNINGS = "$.warnings[?(@.ruleId=='DR-BAIL-016')]";

    private static final String WARNING_A =
            "Do not share until you have checked the remand result. This result releases the "
                    + "defendant from custody. If any bail conditions are pre-release conditions, "
                    + "use 'Remanded in custody with bail direction (RIB)' instead.";

    private static final String WARNING_B =
            "Do not share until you have checked the remand result. This result releases the "
                    + "defendant on bail. If any bail conditions are pre-release conditions, use "
                    + "'Remand in care of Local Authority with bail direction (RILAB)' instead.";

    private static final String WARNING_C =
            "Do not share until you have checked the remand result. This result releases the "
                    + "defendant on bail. If any bail conditions are pre-release conditions, use "
                    + "'Remanded in custody with bail direction (RIB)' for an adult or 'Remand "
                    + "in care of Local Authority with bail direction (RILAB)' for a youth instead.";

    private static final String WARNING_D =
            "Do not share until you have checked the remand result. This result releases the "
                    + "defendant on bail. If any bail conditions are pre-release conditions, use "
                    + "'Sent to Crown Court in custody for trial with bail direction (CCIIB)' instead.";

    private static final String WARNING_E =
            "Do not share until you have checked the remand result. This result releases the "
                    + "defendant on bail. If any bail conditions are pre-release conditions, use "
                    + "'Committed to Crown Court for sentence - in custody with bail direction "
                    + "(CCSIB)' instead.";

    // -----------------------------------------------------------------------
    // Helper: build a standard adult-custodial request JSON string
    // -----------------------------------------------------------------------

    private static String adultCustodialJson(String hearingId, String resultCode,
                                              String triggerCode, String bailStatus) {
        return """
                {
                  "hearingId": "%s",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "label": "%s",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "%s", "label": "%s",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                  "dateOfBirth": "1990-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "%s"}]
                }
                """.formatted(hearingId,
                resultCode, resultCode,
                triggerCode, triggerCode,
                bailStatus);
    }

    // -----------------------------------------------------------------------
    // AC1 — adult conditional bail with pre-release condition → Warning A
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC1 — adult defendant, conditional bail result → Warning A")
    class Ac1AdultWarningA {

        @Test
        @DisplayName("bailStatus=C, result=RC, PASS5, DOB=1990 → 1 WARNING DR-BAIL-016 AC1")
        void adult_rc_pass5_bailStatusC_should_emitWarningA() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac1-a",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS5", "label": "Passport",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
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
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-BAIL-016")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.warnings[0].validationLevel", is("OFFENCE")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off-1")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_A)));
        }

        @Test
        @DisplayName("result=RCBV — also fires Warning A")
        void adult_rcbv_should_emitWarningA() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac1-b",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RCBV", "label": "RCBV",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS1", "label": "PASS1",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_A)));
        }
    }

    // -----------------------------------------------------------------------
    // AC2 — youth conditional bail → Warning B
    // -----------------------------------------------------------------------

    private static String youthCustodialJson(String hearingId, String resultCode,
                                              String triggerCode, String bailStatus) {
        return """
                {
                  "hearingId": "%s",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "label": "%s",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "%s", "label": "%s",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Young", "lastName": "P",
                                  "dateOfBirth": "2010-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "%s"}]
                }
                """.formatted(hearingId,
                resultCode, resultCode,
                triggerCode, triggerCode,
                bailStatus);
    }

    @Nested
    @DisplayName("AC2 — youth defendant, conditional bail result → Warning B")
    class Ac2YouthWarningB {

        @Test
        @DisplayName("bailStatus=S, result=REMCB, PORS, DOB=2010 → Warning B")
        void youth_remcb_pors_bailStatusS_should_emitWarningB() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac2",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "REMCB", "label": "REMCB",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PORS", "label": "PORS",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Young", "lastName": "P",
                                      "dateOfBirth": "2010-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "S"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off-1")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_B)));
        }
    }

    @Nested
    @DisplayName("AC2 — all 6 conditional bail result codes → Warning B for youth")
    class Ac2AllResultCodesYouth {

        @ParameterizedTest(name = "result=''{0}'' → Warning B")
        @ValueSource(strings = {"RC", "RCBV", "BAILREVC", "REMCB", "REMCBY", "RCCCB"})
        @DisplayName("all conditional bail result codes → Warning B for youth in custody")
        void allConditionalBailResultCodes_youth_should_emitWarningB(String resultCode)
                throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(youthCustodialJson(
                                    "h-ac2-code-" + resultCode.toLowerCase(Locale.ROOT),
                                    resultCode, "PASS1", "C")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_B)));
        }
    }

    @Nested
    @DisplayName("AC2 — all 10 named trigger bail condition codes → Warning B for youth")
    class Ac2AllTriggerCodesYouth {

        @ParameterizedTest(name = "trigger=''{0}'' → Warning B")
        @ValueSource(strings = {"PASS1", "PASS2", "PASS3", "PASS4", "PASS5",
            "PASS6", "PASS7", "PASS8", "PORSC", "PORS"})
        @DisplayName("all named trigger bail condition codes → Warning B for youth in custody")
        void allNamedTriggerCodes_youth_should_emitWarningB(String triggerCode) throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(youthCustodialJson(
                                    "h-ac2-trig-" + triggerCode.toLowerCase(Locale.ROOT),
                                    "RC", triggerCode, "C")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_B)));
        }
    }

    @Nested
    @DisplayName("AC2 — all 3 custodial bail statuses (C, L, S) trigger Warning B for youth")
    class Ac2AllCustodialStatusesYouth {

        @ParameterizedTest(name = "bailStatus=''{0}'' → Warning B fires")
        @ValueSource(strings = {"C", "L", "S"})
        @DisplayName("bailStatus C, L, S all qualify as custodial → Warning B for youth")
        void allCustodialBailStatuses_youth_should_emitWarningB(String bailStatus)
                throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(youthCustodialJson(
                                    "h-ac2-status-" + bailStatus.toLowerCase(Locale.ROOT),
                                    "RC", "PASS1", bailStatus)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_B)));
        }
    }

    // -----------------------------------------------------------------------
    // AC6 — no DOB → Warning C
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC6 — defendant has no DOB → Warning C")
    class Ac6NoDobWarningC {

        @Test
        @DisplayName("no DOB, result=RC, PORSC, bailStatus=C → Warning C (AC6)")
        void noDob_rc_porsc_should_emitWarningC() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac6",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PORSC", "label": "PORSC",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "No", "lastName": "Dob"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_C)));
        }
    }

    // -----------------------------------------------------------------------
    // AC2A — CCIC result → Warning D (any age)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC2A — CCIC result with pre-release condition → Warning D (any age)")
    class Ac2ACcicWarningD {

        @Test
        @DisplayName("bailStatus=L, result=CCIC, PASS1, adult → Warning D (AC2A)")
        void ccic_pass1_bailStatusL_adult_should_emitWarningD() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac2a-adult",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCIC", "label": "CCIC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS1", "label": "PASS1",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Joe", "lastName": "B",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "L"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_D)));
        }

        @Test
        @DisplayName("bailStatus=C, result=CCIC, PASS5, youth → Warning D (any age — same as adult)")
        void ccic_pass5_bailStatusC_youth_should_emitWarningD() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac2a-youth",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCIC", "label": "CCIC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS5", "label": "PASS5",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Young", "lastName": "P",
                                      "dateOfBirth": "2010-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_D)));
        }

        @Test
        @DisplayName("bailStatus=S, result=CCIC, PORSC, no DOB → Warning D (any age — no-DOB same as adult)")
        void ccic_porsc_bailStatusS_noDob_should_emitWarningD() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac2a-nodb",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCIC", "label": "CCIC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PORSC", "label": "PORSC",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "No", "lastName": "Dob"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "S"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_D)));
        }
    }

    // -----------------------------------------------------------------------
    // AC2B — CCSC result → Warning E (any age)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC2B — CCSC result with pre-release condition → Warning E (any age)")
    class Ac2BCcscWarningE {

        @Test
        @DisplayName("bailStatus=C, result=CCSC, PASS8, adult → Warning E (AC2B)")
        void ccsc_pass8_bailStatusC_adult_should_emitWarningE() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac2b-adult",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCSC", "label": "CCSC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS8", "label": "PASS8",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Sam", "lastName": "C",
                                      "dateOfBirth": "1985-06-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_E)));
        }

        @Test
        @DisplayName("bailStatus=L, result=CCSC, PASS1, youth → Warning E (any age — same as adult)")
        void ccsc_pass1_bailStatusL_youth_should_emitWarningE() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac2b-youth",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCSC", "label": "CCSC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS1", "label": "PASS1",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Young", "lastName": "P",
                                      "dateOfBirth": "2010-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "L"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_E)));
        }

        @Test
        @DisplayName("bailStatus=C, result=CCSC, PORS, no DOB → Warning E (any age — no-DOB same as adult)")
        void ccsc_pors_noDob_should_emitWarningE() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac2b-nodb",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCSC", "label": "CCSC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PORS", "label": "PORS",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "No", "lastName": "Dob"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_E)));
        }
    }

    // -----------------------------------------------------------------------
    // AC2A/AC2B — all 2 crown court bail codes emit a warning
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC2A/AC2B — all 2 crown court bail codes → DR-BAIL-016 warning fires")
    class Ac2ACcicCcscAllCrownCourtCodes {

        @ParameterizedTest(name = "crownCourtCode=''{0}'' → warning fires")
        @ValueSource(strings = {"CCIC", "CCSC"})
        @DisplayName("all crown court bail codes (CCIC, CCSC) → DR-BAIL-016 warning fires")
        void allCrownCourtBailCodes_should_emitWarning(String crownCourtCode) throws Exception {
            String request = """
                    {
                      "hearingId": "h-cc-%s",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "%s", "label": "%s",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS1", "label": "PASS1",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Joe", "lastName": "B",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """.formatted(
                    crownCourtCode.toLowerCase(Locale.ROOT),
                    crownCourtCode, crownCourtCode);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)));
        }
    }

    // -----------------------------------------------------------------------
    // CCIC + CCSC on same offence — both Warning D and Warning E fire
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("CCIC and CCSC both on same offence — both Warning D and Warning E fire")
    class CcicAndCcscBothPresent {

        @Test
        @DisplayName("CCIC and CCSC result lines on same offence → Warning D and Warning E both present")
        void ccicAndCcscBothPresent_should_emitBothWarningDAndE() throws Exception {
            String request = """
                    {
                      "hearingId": "h-dual-cc",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCIC", "label": "CCIC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "CCSC", "label": "CCSC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl3", "shortCode": "PASS1", "label": "PASS1",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Joe", "lastName": "B",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(2)))
                    .andExpect(jsonPath(
                            "$.warnings[?(@.ruleId=='DR-BAIL-016')].affectedOffences[*].message",
                            hasItem(WARNING_D)))
                    .andExpect(jsonPath(
                            "$.warnings[?(@.ruleId=='DR-BAIL-016')].affectedOffences[*].message",
                            hasItem(WARNING_E)));
        }
    }

    // -----------------------------------------------------------------------
    // AC3 — PORO1 keyword matching (English and Welsh)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC3 — PORO1 free-text keyword matching")
    class Ac3Poro1Keyword {

        @Test
        @DisplayName("PORO1 prompt 'provide a passport' (English) → Warning A")
        void poro1_english_passport_should_emitWarningA() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac3-en",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PORO1", "label": "Other",
                         "defendantId": "d1", "offenceId": "off-1",
                         "prompts": [{"promptRef": "detail", "promptValue": "provide a passport"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_A)));
        }

        @Test
        @DisplayName("PORO1 prompt 'mechnïwr' (Welsh surety) → Warning A")
        void poro1_welsh_mechniwr_should_emitWarningA() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac3-cy",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PORO1", "label": "Other",
                         "defendantId": "d1", "offenceId": "off-1",
                         "prompts": [{"promptRef": "detail", "promptValue": "mechnïwr"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_A)));
        }

        @Test
        @DisplayName("PORO1 prompt 'report to probation' (no keyword) → NO DR-BAIL-016 warning")
        void poro1_noKeyword_should_notEmitWarning() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac3-none",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PORO1", "label": "Other",
                         "defendantId": "d1", "offenceId": "off-1",
                         "prompts": [{"promptRef": "detail", "promptValue": "report to probation"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(0)));
        }
    }

    // -----------------------------------------------------------------------
    // AC4 — sharing not blocked (isValid=true)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC4 — WARNING does not block sharing")
    class Ac4SharingNotBlocked {

        @Test
        @DisplayName("triggering scenario → isValid=true (sharing proceeds)")
        void warningScenario_should_returnIsValidTrue() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac4",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS3", "label": "PASS3",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
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
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)));
        }
    }

    // -----------------------------------------------------------------------
    // AC4A — coexistence with another rule
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC4A — DR-BAIL-016 coexists with DR-RESTRAO-010 (neither suppressed)")
    class Ac4ACoexistence {

        @Test
        @DisplayName("DR-BAIL-016 + DR-RESTRAO-010 both fire → both warnings present")
        void drBail016AndRestrainingOrder_should_bothBePresent() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac4a",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-bail"},
                        {"resultLineId": "rl2", "shortCode": "PASS5", "label": "PASS5",
                         "defendantId": "d1", "offenceId": "off-bail"},
                        {"resultLineId": "rl3", "shortCode": "RESTRAO", "label": "RESTRAO",
                         "defendantId": "d1", "offenceId": "off-rao",
                         "prompts": [{"promptRef": "protectedPersonsName",
                                      "promptValue": "John Smith & Jane Smith"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [
                        {"offenceId": "off-bail", "defendantId": "d1",
                         "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1, "bailStatus": "C"},
                        {"offenceId": "off-rao", "defendantId": "d1",
                         "offenceCode": "TH68002", "offenceTitle": "RAO offence",
                         "orderIndex": 2, "bailStatus": "B"}
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
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-RESTRAO-010')]", hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(greaterThanOrEqualTo(2))));
        }
    }

    // -----------------------------------------------------------------------
    // AC5 — no warning when trigger conditions absent
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC5 — no warning when trigger conditions absent")
    class Ac5NoWarning {

        @Test
        @DisplayName("bailStatus=B — not custodial → no DR-BAIL-016 warning")
        void bailStatusB_should_notEmitWarning() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac5-status",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS5", "label": "PASS5",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "B"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(0)));
        }

        @Test
        @DisplayName("bailStatus=C, result=RIB (non-conditional) → no DR-BAIL-016 warning")
        void nonConditionalResult_should_notEmitWarning() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac5-result",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RIB", "label": "RIB",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS5", "label": "PASS5",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(0)));
        }

        @Test
        @DisplayName("bailStatus=C, result=RC but no trigger bail condition → no warning")
        void noTriggerBailCondition_should_notEmitWarning() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac5-no-cond",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(0)));
        }
    }

    // -----------------------------------------------------------------------
    // FR-010 — warning appears only at offence level, not hearing/defendant level
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("FR-010 — DR-BAIL-016 warns at offence level only")
    class Fr010OffenceLevelOnly {

        @Test
        @DisplayName("triggering scenario — DR-BAIL-016 appears only in affectedOffences, not in top-level errors")
        void warning_should_appear_only_in_affectedOffences() throws Exception {
            String request = """
                    {
                      "hearingId": "h-fr010",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS5", "label": "PASS5",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1, "bailStatus": "C"}]
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
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].validationLevel", is("OFFENCE")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off-1")))
                    // DR-BAIL-016 must NOT appear as a top-level defendant-level item
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-BAIL-016' && @.validationLevel=='DEFENDANT')]", hasSize(0)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-BAIL-016' && @.validationLevel=='HEARING')]", hasSize(0)));
        }
    }

    // -----------------------------------------------------------------------
    // Multiple offences — only qualifying offence warns
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // AC1 parameterized — all 6 result codes, all 10 trigger codes, all 3 bail statuses
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC1 — all 6 conditional bail result codes → Warning A")
    class Ac1AllResultCodes {

        @ParameterizedTest(name = "result=''{0}'' → Warning A")
        @ValueSource(strings = {"RC", "RCBV", "BAILREVC", "REMCB", "REMCBY", "RCCCB"})
        @DisplayName("all conditional bail result codes → Warning A for adult in custody")
        void allConditionalBailResultCodes_adult_should_emitWarningA(String resultCode)
                throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adultCustodialJson(
                                    "h-ac1-code-" + resultCode.toLowerCase(Locale.ROOT),
                                    resultCode, "PASS1", "C")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_A)));
        }
    }

    @Nested
    @DisplayName("AC1 — all 10 named trigger bail condition codes → Warning A")
    class Ac1AllTriggerCodes {

        @ParameterizedTest(name = "trigger=''{0}'' → Warning A")
        @ValueSource(strings = {"PASS1", "PASS2", "PASS3", "PASS4", "PASS5",
            "PASS6", "PASS7", "PASS8", "PORSC", "PORS"})
        @DisplayName("all named trigger bail condition codes → Warning A for adult in custody")
        void allNamedTriggerCodes_adult_should_emitWarningA(String triggerCode) throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adultCustodialJson(
                                    "h-ac1-trig-" + triggerCode.toLowerCase(Locale.ROOT),
                                    "RC", triggerCode, "C")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(WARNING_A)));
        }
    }

    @Nested
    @DisplayName("AC1 — all 3 custodial bail statuses (C, L, S) trigger warning")
    class Ac1AllCustodialStatuses {

        @ParameterizedTest(name = "bailStatus=''{0}'' → Warning fires")
        @ValueSource(strings = {"C", "L", "S"})
        @DisplayName("bailStatus C, L, S all qualify as custodial → Warning A for adult")
        void allCustodialBailStatuses_should_emitWarning(String bailStatus) throws Exception {
            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adultCustodialJson(
                                    "h-ac1-status-" + bailStatus.toLowerCase(Locale.ROOT),
                                    "RC", "PASS1", bailStatus)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)));
        }
    }

    // -----------------------------------------------------------------------
    // AC7 — null bailStatus → no warning (fail-open)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AC7 — null bailStatus → no warning (fail-open)")
    class Ac7NullBailStatus {

        @Test
        @DisplayName("offence with no bailStatus field → no DR-BAIL-016 warning")
        void nullBailStatus_should_notEmitWarning() throws Exception {
            String request = """
                    {
                      "hearingId": "h-ac7",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PASS5", "label": "PASS5",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                    "offenceCode": "TH68001", "offenceTitle": "Theft",
                                    "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(0)));
        }
    }

    // -----------------------------------------------------------------------
    // Multiple offences — only qualifying offence warns
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Multiple offences — only qualifying offence produces warning")
    class MultipleOffences {

        @Test
        @DisplayName("two offences, only one is custodial with trigger → warning on qualifying only")
        void mixedOffences_should_warnOnlyForQualifyingOffence() throws Exception {
            String request = """
                    {
                      "hearingId": "h-multi",
                      "hearingDay": "2026-11-09",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off-cust"},
                        {"resultLineId": "rl2", "shortCode": "PASS5", "label": "PASS5",
                         "defendantId": "d1", "offenceId": "off-cust"},
                        {"resultLineId": "rl3", "shortCode": "FO", "label": "FO",
                         "defendantId": "d1", "offenceId": "off-bail"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                      "dateOfBirth": "1990-01-01"}],
                      "offences": [
                        {"offenceId": "off-cust", "defendantId": "d1",
                         "offenceCode": "TH68001", "offenceTitle": "Theft A",
                         "orderIndex": 1, "bailStatus": "C"},
                        {"offenceId": "off-bail", "defendantId": "d1",
                         "offenceCode": "TH68002", "offenceTitle": "Theft B",
                         "orderIndex": 2, "bailStatus": "B"}
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
                    .andExpect(jsonPath(DR_BAIL_016_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("off-cust")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences",
                            not(hasItem(hasItem("off-bail")))));
        }
    }
}
