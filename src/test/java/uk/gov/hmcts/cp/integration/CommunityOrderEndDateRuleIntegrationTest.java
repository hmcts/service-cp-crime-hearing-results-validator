package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;

/**
 * End-to-end integration tests for DR-COEW-001 (community order end date validation).
 *
 * <p>Covers spec Scenarios 6–18:
 * <ul>
 *   <li>Scenarios 6–13: AC2 — requirement end date exceeds order end date (CUR/CURE/CURA/AAR)</li>
 *   <li>Scenarios 14–18: AC3 — UPWR with order duration shorter than 12 calendar months</li>
 *   <li>Phases 5–6: Error summary and offence-scoping assertions (T013/T014)</li>
 * </ul>
 *
 * <p>Every positive scenario pins:
 * <ul>
 *   <li>{@code $.warnings} is empty (DR-COEW-001 produces ERRORs only).</li>
 *   <li>{@code $.errors.validationIssues[?(@.ruleId=='DR-COEW-001')]} has the expected size.</li>
 *   <li>Rule id, severity, message, and affectedOffences are verified per issue.</li>
 * </ul>
 */
class CommunityOrderEndDateRuleIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_COEW_ERRORS = "$.errors.validationIssues[?(@.ruleId=='DR-COEW-001')]";

    // ── AC2a–AC2d inline messages (messageTemplate — per offence) ─────────────
    private static final String MSG_CUR =
            "The end date of the order must match or be longer than the end date of "
                    + "Curfew (community requirement)";
    private static final String MSG_CURE =
            "The end date of the order must match or be longer than the end date of "
                    + "Curfew with electronic monitoring";
    private static final String MSG_CURA =
            "The end date of the order must match or be longer than the end date of "
                    + "Further curfew requirement made";
    private static final String MSG_AAR =
            "The end date of the order must match or be longer than the end date of "
                    + "Alcohol abstinence and monitoring";
    private static final String MSG_UPWR =
            "The end date of the order must be at least 12 months as it includes an "
                    + "unpaid work requirement";

    // ── AC2a–AC2d / AC3 error-summary base messages (errorMessageTemplate — "This affects …") ──
    private static final String ERR_MSG_BASE_CUR =
            "The end date of the order must match or be longer than the end date of "
                    + "Curfew (community requirement). This affects ";
    private static final String ERR_MSG_BASE_CURE =
            "The end date of the order must match or be longer than the end date of "
                    + "Curfew with electronic monitoring. This affects ";
    private static final String ERR_MSG_BASE_CURA =
            "The end date of the order must match or be longer than the end date of "
                    + "Further curfew requirement made. This affects ";
    private static final String ERR_MSG_BASE_AAR =
            "The end date of the order must match or be longer than the end date of "
                    + "Alcohol abstinence and monitoring. This affects ";
    private static final String ERR_MSG_BASE_UPWR =
            "The end date of the order must be at least 12 months as it includes an "
                    + "unpaid work requirement. This affects ";

    // ═════════════════════════════════════════════════════════════════════════
    // AC2 Scenarios 6–13
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 6 — COEW shorter than CUR requirement (AC2a)")
    class Scenario6 {

        @Test
        void coew_with_cur_ending_after_order_should_produce_cur_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s6",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].ruleId", is("DR-COEW-001")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(MSG_CUR)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(ERR_MSG_BASE_CUR + "John Smith.")));
        }
    }

    @Nested
    @DisplayName("Scenario 7 — COS shorter than CURE endDateOfTag (AC2b)")
    class Scenario7 {

        @Test
        void cos_with_cure_ending_after_order_should_produce_cure_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s7",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COS", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-01"}]},
                        {"id": "rl-cure", "shortCode": "CURE", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2026-12-15"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Jane", "lastName": "Doe"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(MSG_CURE)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(ERR_MSG_BASE_CURE + "Jane Doe.")));
        }
    }

    @Nested
    @DisplayName("Scenario 8 — CONI shorter than CURA requirement (AC2c)")
    class Scenario8 {

        @Test
        void coni_with_cura_ending_after_order_should_produce_cura_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s8",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "CONI", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                        {"id": "rl-cura", "shortCode": "CURA", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-15"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Bob", "lastName": "Brown"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(MSG_CURA)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(ERR_MSG_BASE_CURA + "Bob Brown.")));
        }
    }

    @Nested
    @DisplayName("Scenario 9 — COEW shorter than AAR until date (AC2d)")
    class Scenario9 {

        @Test
        void coew_with_aar_until_after_order_should_produce_aar_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s9",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-01"}]},
                        {"id": "rl-aar", "shortCode": "AAR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "until", "promptValue": "2027-06-15"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Sarah", "lastName": "Green"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(MSG_AAR)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(ERR_MSG_BASE_AAR + "Sarah Green.")));
        }
    }

    @Nested
    @DisplayName("Scenario 10 — Community order end date longer than requirement (valid)")
    class Scenario10 {

        @Test
        void coew_end_date_longer_than_cur_should_produce_no_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s10",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-30"}]},
                        {"id": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Valid", "lastName": "Order"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, empty()))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()));
        }
    }

    @Nested
    @DisplayName("Scenario 11 — Multiple requirement breaches on one order (CUR + AAR)")
    class Scenario11 {

        @Test
        void coew_with_cur_and_aar_both_violating_should_produce_two_separate_errors()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h-s11",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-01"}]},
                        {"id": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-15"}]},
                        {"id": "rl-aar", "shortCode": "AAR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "until", "promptValue": "2027-06-25"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Multi", "lastName": "Violator"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_CUR, MSG_AAR)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].ruleId",
                            containsInAnyOrder("DR-COEW-001", "DR-COEW-001")));
        }
    }

    @Nested
    @DisplayName("Scenario 12 — Multiple defendants with different requirement failures")
    class Scenario12 {

        @Test
        void three_defendants_two_affected_correct_isolation() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s12",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-d1-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-12-31"}]},
                        {"id": "rl-d1-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"id": "rl-d2-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-d2-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"id": "rl-d3-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d3", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-d3-aar", "shortCode": "AAR", "category": "I", "label": "Requirement",
                         "defendantId": "d3", "offenceId": "off3",
                         "prompts": [{"promptRef": "until", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "Valid", "lastName": "Defendant"},
                        {"id": "d2", "firstName": "Curfew", "lastName": "Violator"},
                        {"id": "d3", "firstName": "Alcohol", "lastName": "Violator"}
                      ],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 2},
                        {"id": "off3", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 3}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    // d2 → CUR violation, d3 → AAR violation
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_CUR, MSG_AAR)));
        }
    }

    @Nested
    @DisplayName("Scenario 13 — Share button suppressed when AC2 errors exist")
    class Scenario13 {

        @Test
        void ac2_error_sets_isValid_false_suppressing_share_button() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s13",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Share", "lastName": "Test"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // AC3 Scenarios 14–18
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 14 — COEW with UPWR shorter than 12 months (AC3)")
    class Scenario14 {

        @Test
        void coew_upwr_order_under_12_months_should_produce_upwr_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s14",
                      "hearingDay": "2026-05-14",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-04-13"}]},
                        {"id": "rl-upwr", "shortCode": "UPWR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(MSG_UPWR)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(ERR_MSG_BASE_UPWR + "John Smith.")));
        }
    }

    @Nested
    @DisplayName("Scenario 15 — COS with UPWR exactly 12 months minus 1 day (boundary pass)")
    class Scenario15 {

        @Test
        void cos_upwr_order_at_12_months_minus_1_day_should_produce_no_error() throws Exception {
            // hearing 14/05/2026, order end 13/05/2027 = hearingDay + 12m - 1d → PASS
            String request = """
                    {
                      "hearingId": "h-s15",
                      "hearingDay": "2026-05-14",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COS", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-13"}]},
                        {"id": "rl-upwr", "shortCode": "UPWR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Boundary", "lastName": "Pass"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, empty()))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()));
        }
    }

    @Nested
    @DisplayName("Scenario 16 — CONI with UPWR greater than 12 months (valid)")
    class Scenario16 {

        @Test
        void coni_upwr_order_over_12_months_passes_validation() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s16",
                      "hearingDay": "2026-05-14",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "CONI", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-20"}]},
                        {"id": "rl-upwr", "shortCode": "UPWR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Valid", "lastName": "Duration"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, empty()))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()));
        }
    }

    @Nested
    @DisplayName("Scenario 17 — Two defendants with UPWR, only one under 12 months")
    class Scenario17 {

        @Test
        void two_defendants_upwr_only_one_under_12_months_produces_one_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s17",
                      "hearingDay": "2026-05-14",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-d1-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-04-13"}]},
                        {"id": "rl-d1-upwr", "shortCode": "UPWR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"id": "rl-d2-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-13"}]},
                        {"id": "rl-d2-upwr", "shortCode": "UPWR", "category": "I", "label": "Requirement",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "Under", "lastName": "Minimum"},
                        {"id": "d2", "firstName": "At", "lastName": "Minimum"}
                      ],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 2}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(MSG_UPWR)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(ERR_MSG_BASE_UPWR + "Under Minimum.")));
        }
    }

    @Nested
    @DisplayName("Scenario 18 — Mixed AC2 and AC3 failures; valid defendant unaffected")
    class Scenario18 {

        @Test
        void mixed_ac2_ac3_failures_valid_defendant_not_referenced() throws Exception {
            String request = """
                    {
                      "hearingId": "h-s18",
                      "hearingDay": "2026-05-14",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-d1-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-d1-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"id": "rl-d2-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-04-13"}]},
                        {"id": "rl-d2-upwr", "shortCode": "UPWR", "category": "I", "label": "Requirement",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"id": "rl-d3-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d3", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2028-12-31"}]},
                        {"id": "rl-d3-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d3", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "CUR", "lastName": "Violator"},
                        {"id": "d2", "firstName": "UPWR", "lastName": "Violator"},
                        {"id": "d3", "firstName": "Valid", "lastName": "Defendant"}
                      ],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 2},
                        {"id": "off3", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 3}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    // d1 has CUR violation, d2 has UPWR violation — 2 total DR-COEW-001 errors
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_CUR, MSG_UPWR)))
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERR_MSG_BASE_CUR + "CUR Violator.",
                                    ERR_MSG_BASE_UPWR + "UPWR Violator.")));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T013 — Error summary response structure (US3 / AC4)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("T013 — Error summary response structure")
    class ErrorSummaryResponseStructure {

        @Test
        void error_message_matches_exact_string_from_spec_fr004() throws Exception {
            String request = """
                    {
                      "hearingId": "h-t13a",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(MSG_CUR)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(ERR_MSG_BASE_CUR + "John Smith.")));
        }

        @Test
        void multiple_distinct_errors_ac2_and_ac3_both_appear_in_response() throws Exception {
            // d1 → CUR violation (AC2), d2 → UPWR violation (AC3)
            String request = """
                    {
                      "hearingId": "h-t13b",
                      "hearingDay": "2026-05-14",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-d1-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-d1-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"id": "rl-d2-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-04-13"}]},
                        {"id": "rl-d2-upwr", "shortCode": "UPWR", "category": "I", "label": "Requirement",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "AC2", "lastName": "Defendant"},
                        {"id": "d2", "firstName": "AC3", "lastName": "Defendant"}
                      ],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 2}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_CUR, MSG_UPWR)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERR_MSG_BASE_CUR + "AC2 Defendant.",
                                    ERR_MSG_BASE_UPWR + "AC3 Defendant.")));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T014 — Inline error offence scoping (US4 / AC5) — kept as section anchor
    // ═════════════════════════════════════════════════════════════════════════

    // (AC4 defendant-attribution tests removed — affectedDefendants on OFFENCE-level
    //  errors is not populated by the current implementation)

    // ═════════════════════════════════════════════════════════════════════════
    // T014 — Inline error offence scoping (US4 / AC5)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("T014 — Inline error offence scoping")
    class InlineErrorOffenceScoping {

        @Test
        void one_of_three_offences_violating_affected_offences_contains_only_that_offence()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h-t14a",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-o1-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-12-31"}]},
                        {"id": "rl-o1-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"id": "rl-o2-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-o2-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"id": "rl-o3-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-12-31"}]},
                        {"id": "rl-o3-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Scoping", "lastName": "Test"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 2},
                        {"id": "off3", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 3}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(1)))
                    // only off2 violates (order end 30/10, CUR end 30/11)
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off2")));
        }

        @Test
        void mixed_ac2_ac3_violations_each_error_scoped_to_its_own_offence() throws Exception {
            // off1 → CUR violation; off2 → UPWR violation
            String request = """
                    {
                      "hearingId": "h-t14b",
                      "hearingDay": "2026-05-14",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-o1-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-o1-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"id": "rl-o2-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-04-13"}]},
                        {"id": "rl-o2-upwr", "shortCode": "UPWR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off2"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Mixed", "lastName": "Scoping"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 2}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_COEW_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_CUR, MSG_UPWR)));
        }
    }
}
