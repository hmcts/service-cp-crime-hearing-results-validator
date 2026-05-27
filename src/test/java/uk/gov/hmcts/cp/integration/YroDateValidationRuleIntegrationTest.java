package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for DR-YRO-001 (Youth Rehabilitation Order end-date validation).
 *
 * <p>Covers:
 * <ul>
 *   <li>AC2a — YRC2 (Curfew) end date exceeds YRO end date</li>
 *   <li>AC2b — YRC1 (Curfew with electronic monitoring) end-of-tag exceeds YRO end date</li>
 *   <li>AC2c — YRC3 (Further curfew requirement made) end date exceeds YRO end date</li>
 *   <li>AC3 — YRUP1 (Unpaid work) present; YRO end date less than hearingDay + 12m − 1d</li>
 * </ul>
 */
class YroDateValidationRuleIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_YRO_ERRORS =
            "$.errors.validationIssues[?(@.ruleId=='DR-YRO-001')]";

    // Inline messages (messageTemplate — per offence in affectedOffences[].message)
    private static final String MSG_YRC2 =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Curfew";
    private static final String MSG_YRC1 =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Curfew with electronic monitoring";
    private static final String MSG_YRC3 =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Further curfew requirement made";
    private static final String MSG_YRUP1 =
            "The end date of the order must be at least 12 months as it includes an "
                    + "unpaid work requirement";

    // Error summary base messages (errorMessageTemplate — "This affects …")
    private static final String ERR_MSG_BASE_YRC2 =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Curfew. This affects ";
    private static final String ERR_MSG_BASE_YRC1 =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Curfew with electronic monitoring. This affects ";
    private static final String ERR_MSG_BASE_YRC3 =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Further curfew requirement made. This affects ";
    private static final String ERR_MSG_BASE_YRUP1 =
            "The end date of the order must be at least 12 months as it includes an "
                    + "unpaid work requirement. This affects ";

    // ═══════════════════════════════════════════════════════════════════════
    // AC2 — Curfew requirement end date exceeds YRO end date
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AC2a — YRC2 (Curfew) end date after YRO end date")
    class Ac2aYrc2CurfewViolation {

        @Test
        @DisplayName("T001 — YRC2 end date after YRO end date produces AC2a ERROR")
        void yroew_yrc2_ending_after_order_should_produce_yrc2_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-yro-001",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].ruleId", is("DR-YRO-001")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_YRC2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRC2 + "John Smith.")));
        }

        @Test
        @DisplayName("T005 — YRC2 end date equal to YRO end date: no error (boundary)")
        void yroew_yrc2_end_date_equal_to_order_should_produce_no_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-yro-005",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"id": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Equal", "lastName": "Date"}],
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, empty()))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()));
        }

        @Test
        @DisplayName("T006 — YRC2 end date before YRO end date: no error")
        void yroew_yrc2_end_date_before_order_should_produce_no_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-yro-006",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-30"}]},
                        {"id": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, empty()))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()));
        }
    }

    @Nested
    @DisplayName("AC2b — YRC1 (Curfew with electronic monitoring) end-of-tag after YRO end date")
    class Ac2bYrc1TagViolation {

        @Test
        @DisplayName("T002 — YRC1 endDateOfTagging after YRO end date produces AC2b ERROR")
        void yroni_yrc1_tag_date_after_order_should_produce_yrc1_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-yro-002",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YRONI", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-01"}]},
                        {"id": "rl-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_YRC1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRC1 + "Jane Doe.")));
        }
    }

    @Nested
    @DisplayName("AC2c — YRC3 (Further curfew requirement made) end date after YRO end date")
    class Ac2cYrc3FurtherCurfewViolation {

        @Test
        @DisplayName("T003 — YRC3 end date after YRO end date produces AC2c ERROR")
        void yrofew_yrc3_ending_after_order_should_produce_yrc3_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-yro-003",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROFEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                        {"id": "rl-yrc3", "shortCode": "YRC3", "category": "I", "label": "Further curfew",
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_YRC3)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRC3 + "Bob Brown.")));
        }
    }

    @Nested
    @DisplayName("AC2 — All three curfew types breach simultaneously")
    class Ac2AllThreeCurfewTypesViolation {

        @Test
        @DisplayName("T004 — YRC2, YRC1, and YRC3 all breach: three independent AC2 errors")
        void yroew_all_curfew_requirements_breaching_produces_three_errors() throws Exception {
            String request = """
                    {
                      "hearingId": "h-yro-004",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-01"}]},
                        {"id": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-15"}]},
                        {"id": "rl-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2027-06-20"}]},
                        {"id": "rl-yrc3", "shortCode": "YRC3", "category": "I", "label": "Further curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-25"}]}
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(3)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_YRC2, MSG_YRC1, MSG_YRC3)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].ruleId",
                            containsInAnyOrder("DR-YRO-001", "DR-YRO-001", "DR-YRO-001")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(3)));
        }
    }

    @Nested
    @DisplayName("AC2 — Multi-defendant isolation and no-requirement case")
    class Ac2MultiDefendantAndNoRequirement {

        @Test
        @DisplayName("T007 — Two defendants, only one with YRC2 breach; only that defendant in error")
        void two_defendants_only_one_with_yrc2_breach_produces_one_error() throws Exception {
            // d1: order end 2027-12-31 > YRC2 end 2026-11-30 → valid
            // d2: order end 2026-10-30 < YRC2 end 2026-11-30 → AC2a ERROR
            String request = """
                    {
                      "hearingId": "h-yro-007",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-d1-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-12-31"}]},
                        {"id": "rl-d1-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"id": "rl-d2-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-d2-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "Valid", "lastName": "Defendant"},
                        {"id": "d2", "firstName": "Curfew", "lastName": "Violator"}
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_YRC2)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off2")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRC2 + "Curfew Violator.")));
        }

        @Test
        @DisplayName("T008 — YRO with no curfew child requirements: no AC2 error")
        void yroew_without_curfew_requirements_produces_no_ac2_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-yro-008",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "No", "lastName": "Requirements"}],
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, empty()))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AC3 — YRUP1 requires YRO duration of at least hearingDay + 12m − 1d
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AC3 — YRUP1 (Unpaid work) minimum 12-month duration")
    class Ac3UnpaidWorkDuration {

        @Test
        @DisplayName("T009 — YRUP1 present; end date < hearingDay+12m-1d produces AC3 ERROR")
        void yroew_yrup1_order_under_12_months_should_produce_yrup1_error() throws Exception {
            // hearing 20/05/2026, minEndDate = 19/05/2027, orderEnd = 18/05/2027 → ERROR
            String request = """
                    {
                      "hearingId": "h-yro-009",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-18"}]},
                        {"id": "rl-yrup1", "shortCode": "YRUP1", "category": "I", "label": "Unpaid work",
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_YRUP1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRUP1 + "John Smith.")));
        }

        @Test
        @DisplayName("T010 — YRUP1; end date equals hearingDay+12m-1d: no error (boundary pass)")
        void yroew_yrup1_at_boundary_12_months_minus_1_day_should_produce_no_error() throws Exception {
            // hearing 20/05/2026, minEndDate = 19/05/2027, orderEnd = 19/05/2027 → PASS
            String request = """
                    {
                      "hearingId": "h-yro-010",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-19"}]},
                        {"id": "rl-yrup1", "shortCode": "YRUP1", "category": "I", "label": "Unpaid work",
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, empty()))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()));
        }

        @Test
        @DisplayName("T011 — YRUP1; end date beyond hearingDay+12m-1d: no error")
        void yroew_yrup1_order_over_12_months_passes_validation() throws Exception {
            // hearing 20/05/2026, minEndDate = 19/05/2027, orderEnd = 20/05/2027 → PASS
            String request = """
                    {
                      "hearingId": "h-yro-011",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YRONI", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-20"}]},
                        {"id": "rl-yrup1", "shortCode": "YRUP1", "category": "I", "label": "Unpaid work",
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, empty()))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()));
        }

        @Test
        @DisplayName("T012 — YRO without YRUP1 and short end date: no AC3 error")
        void yroew_without_yrup1_does_not_trigger_ac3() throws Exception {
            String request = """
                    {
                      "hearingId": "h-yro-012",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-06-01"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "No", "lastName": "Unpaid"}],
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, empty()))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()));
        }

        @Test
        @DisplayName("T013 — Two defendants; only one with YRUP1 under 12m: one error, correct defendant")
        void two_defendants_yrup1_only_one_under_12_months_produces_one_error() throws Exception {
            // d1: orderEnd 2027-05-18 < minEndDate 2027-05-19 → ERROR
            // d2: orderEnd 2027-05-19 = minEndDate 2027-05-19 → PASS
            String request = """
                    {
                      "hearingId": "h-yro-013",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-d1-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-18"}]},
                        {"id": "rl-d1-yrup1", "shortCode": "YRUP1", "category": "I",
                         "label": "Unpaid work", "defendantId": "d1", "offenceId": "off1"},
                        {"id": "rl-d2-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-19"}]},
                        {"id": "rl-d2-yrup1", "shortCode": "YRUP1", "category": "I",
                         "label": "Unpaid work", "defendantId": "d2", "offenceId": "off2"}
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_YRUP1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRUP1 + "Under Minimum.")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Combined and response-structure scenarios (T014–T017)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Combined — AC2 and AC3 fire independently")
    class CombinedAc2AndAc3 {

        @Test
        @DisplayName("T014 — Same defendant: AC2a breach and AC3 breach produce two independent errors")
        void defendant_yrc2_breach_and_yrup1_short_duration_produces_two_errors() throws Exception {
            // orderEnd 2027-05-18: < yrc2End 2027-06-30 → AC2a; < minEndDate 2027-05-19 → AC3
            String request = """
                    {
                      "hearingId": "h-yro-014",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-18"}]},
                        {"id": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-30"}]},
                        {"id": "rl-yrup1", "shortCode": "YRUP1", "category": "I",
                         "label": "Unpaid work", "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Both", "lastName": "Violations"}],
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_YRC2, MSG_YRUP1)))
                    .andExpect(jsonPath("$.isValid", is(false)));
        }
    }

    @Nested
    @DisplayName("Response structure — error summary, offence scoping, valid case")
    class ResponseStructure {

        @Test
        @DisplayName("T015 — errorMessageTemplate correctly expands defendant name")
        void error_message_contains_resolved_defendant_name() throws Exception {
            String request = """
                    {
                      "hearingId": "h-yro-015",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROISS", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Alice", "lastName": "Walker"}],
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
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRC2 + "Alice Walker.")));
        }

        @Test
        @DisplayName("T016 — Inline error scoped to only the breaching offence")
        void inline_error_scoped_to_violating_offence_only() throws Exception {
            // off1: orderEnd 2027-12-31 > yrc2End 2026-11-30 → VALID
            // off2: orderEnd 2026-10-30 < yrc2End 2026-11-30 → ERROR on off2 only
            String request = """
                    {
                      "hearingId": "h-yro-016",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-o1-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-12-31"}]},
                        {"id": "rl-o1-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"id": "rl-o2-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-o2-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Scoping", "lastName": "Test"}],
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off2")));
        }

        @Test
        @DisplayName("T017 — Fully valid YRO: isValid true, no errors, DR-YRO-001 in rulesEvaluated")
        void fully_valid_yro_produces_no_errors_and_rule_appears_in_evaluated() throws Exception {
            // YROINI, orderEnd 2027-12-31: all requirements within order date; YRUP1 >12m
            String request = """
                    {
                      "hearingId": "h-yro-017",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROINI", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-12-31"}]},
                        {"id": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-30"}]},
                        {"id": "rl-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2027-07-31"}]},
                        {"id": "rl-yrup1", "shortCode": "YRUP1", "category": "I",
                         "label": "Unpaid work", "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "All", "lastName": "Valid"}],
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
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath("$.rulesEvaluated", hasItem("DR-YRO-001")));
        }
    }
}
