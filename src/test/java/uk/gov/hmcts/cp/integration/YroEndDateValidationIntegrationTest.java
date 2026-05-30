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
 * End-to-end integration tests for DR-YRO-001 covering the full YRO end-date validation
 * scenario set (AC1 – AC5) with hearing date 20/05/2026.
 *
 * <p>Scenarios map directly to the business acceptance criteria:
 * <ul>
 *   <li>AC1 — YRO end date is on or before the hearing date (not in the future)</li>
 *   <li>AC2 — YRO end date is earlier than a linked curfew requirement end date
 *              (YRC2 / YRC1 / YRC3)</li>
 *   <li>AC3 — YRO containing YRUP1 (unpaid work) spans less than 12 months from hearing</li>
 *   <li>AC4 — Multiple distinct error types shown simultaneously across defendants</li>
 *   <li>AC5 — Multiple errors shown above the same result or across separate offences</li>
 * </ul>
 */
class YroEndDateValidationIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_YRO_ERRORS =
            "$.errors.validationIssues[?(@.ruleId=='DR-YRO-001')]";

    // AC1 messages
    private static final String MSG_AC1 =
            "The end date must be in the future";
    private static final String ERR_MSG_BASE_AC1 =
            "The end date of the Youth rehabilitation order must be in the future. This affects ";

    // AC2 inline messages
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

    // AC2/AC3 error summary bases
    private static final String ERR_MSG_BASE_YRC2 = MSG_YRC2 + ". This affects ";
    private static final String ERR_MSG_BASE_YRC1 = MSG_YRC1 + ". This affects ";
    private static final String ERR_MSG_BASE_YRC3 = MSG_YRC3 + ". This affects ";
    private static final String ERR_MSG_BASE_YRUP1 = MSG_YRUP1 + ". This affects ";

    // ═══════════════════════════════════════════════════════════════════════
    // AC1 — YRO End Date in the Past (on or before hearing date 20/05/2026)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AC1 — YRO end date is not in the future")
    class Ac1PastEndDate {

        @Test
        @DisplayName("Scenario 1 — Single defendant; end date equals hearing date produces AC1 ERROR")
        void single_defendant_end_date_equal_to_hearing_date_should_produce_ac1_error()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h-sc1",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-05-20"}]}
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
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_AC1)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_AC1 + "John Smith.")));
        }

        @Test
        @DisplayName("Scenario 2 — Single defendant, multiple offences; only past-date offence errors")
        void single_defendant_multiple_offences_only_past_date_offence_produces_error()
                throws Exception {
            // off1: endDate 2026-05-19 (before hearing) → ERROR
            // off2: endDate 2027-05-19 (future) → valid
            // off3: no YRO result → no error
            String request = """
                    {
                      "hearingId": "h-sc2",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-o1", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-05-19"}]},
                        {"id": "rl-o2", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-19"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Sarah", "lastName": "Jones"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2},
                        {"id": "off3", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 3}
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
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_AC1)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_AC1 + "Sarah Jones.")));
        }

        @Test
        @DisplayName("Scenario 3 — Multiple cases, same defendant; only past-date case errors")
        void same_defendant_two_cases_only_past_end_date_case_produces_error() throws Exception {
            // off1 (case 1): endDate 2026-05-18 → ERROR
            // off2 (case 2): endDate 2027-05-18 → valid
            String request = """
                    {
                      "hearingId": "h-sc3",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-c1", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-05-18"}]},
                        {"id": "rl-c2", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-18"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Michael", "lastName": "Brown"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2}
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_AC1)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_AC1 + "Michael Brown.")));
        }

        @Test
        @DisplayName("Scenario 4 — Multiple defendants; only the past-date defendant errors")
        void two_defendants_only_past_date_defendant_produces_error() throws Exception {
            // James Hall: endDate 2026-05-20 (= hearing, not future) → ERROR
            // Emma White: endDate 2027-05-20 (future) → valid
            String request = """
                    {
                      "hearingId": "h-sc4",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-d1", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-05-20"}]},
                        {"id": "rl-d2", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-20"}]}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "James", "lastName": "Hall"},
                        {"id": "d2", "firstName": "Emma", "lastName": "White"}
                      ],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2}
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_AC1)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_AC1 + "James Hall.")));
        }

        @Test
        @DisplayName("Scenario 5 — Multiple defendants, multiple offences; two defendants error, two do not")
        void multiple_defendants_two_past_date_two_valid_produces_two_errors() throws Exception {
            // Defendant Alpha (YROEW): endDate 2026-05-19 → ERROR
            // Defendant Beta (YROINI): endDate 2026-05-19 → ERROR
            // Defendant Charlie (YROFEW): endDate 2026-09-25 (future) → valid
            // Defendant Delta: no YRO result → no error
            String request = """
                    {
                      "hearingId": "h-sc5",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-da", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "da", "offenceId": "off-a",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-05-19"}]},
                        {"id": "rl-db", "shortCode": "YROINI", "category": "F", "label": "YRO",
                         "defendantId": "db", "offenceId": "off-b",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-05-19"}]},
                        {"id": "rl-dc", "shortCode": "YROFEW", "category": "F", "label": "YRO",
                         "defendantId": "dc", "offenceId": "off-c",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-09-25"}]}
                      ],
                      "defendants": [
                        {"id": "da", "firstName": "Defendant", "lastName": "Alpha"},
                        {"id": "db", "firstName": "Defendant", "lastName": "Beta"},
                        {"id": "dc", "firstName": "Defendant", "lastName": "Charlie"},
                        {"id": "dd", "firstName": "Defendant", "lastName": "Delta"}
                      ],
                      "offences": [
                        {"id": "off-a", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off-b", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2},
                        {"id": "off-c", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 3}
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_AC1, MSG_AC1)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].offenceId",
                            containsInAnyOrder("off-a", "off-b")))
                    // Both defendants fail the same condition; the framework merges them into
                    // one combined error message listing all affected names.
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_AC1 + "Defendant Alpha and Defendant Beta.")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AC2 — YRO End Date Earlier Than Requirement End Date
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AC2 — YRO end date earlier than requirement end date")
    class Ac2RequirementExceedsOrder {

        @Test
        @DisplayName("Scenario 6 — Single defendant, YRC2 exceeds YRO end date produces AC2a ERROR")
        void single_defendant_yrc2_exceeds_yro_end_date_produces_ac2a_error() throws Exception {
            // YRO end 30/10/2026, YRC2 end 30/11/2026 → curfew extends beyond order
            String request = """
                    {
                      "hearingId": "h-sc6",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"id": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "David", "lastName": "Evans"}],
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
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_YRC2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRC2 + "David Evans.")));
        }

        @Test
        @DisplayName("Scenario 7 — Two defendants, each with a different curfew type breach")
        void two_defendants_different_curfew_requirement_types_produce_two_ac2_errors()
                throws Exception {
            // Alex Green: YRO end 01/10/2026, YRC1 tag end 15/10/2026 → AC2b ERROR
            // Chloe Black: YRO end 01/11/2026, YRC3 further curfew end 20/11/2026 → AC2c ERROR
            String request = """
                    {
                      "hearingId": "h-sc7",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-ag-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-01"}]},
                        {"id": "rl-ag-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2026-10-15"}]},
                        {"id": "rl-cb-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-01"}]},
                        {"id": "rl-cb-yrc3", "shortCode": "YRC3", "category": "I",
                         "label": "Further curfew", "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-20"}]}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "Alex", "lastName": "Green"},
                        {"id": "d2", "firstName": "Chloe", "lastName": "Black"}
                      ],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2}
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
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_YRC1, MSG_YRC3)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERR_MSG_BASE_YRC1 + "Alex Green.",
                                    ERR_MSG_BASE_YRC3 + "Chloe Black.")));
        }

        @Test
        @DisplayName("Scenario 8 — Same defendant, two cases; only the breaching case errors")
        void same_defendant_two_cases_only_breaching_case_produces_error() throws Exception {
            // off1 (case 1): YRO end 01/09/2026, YRC2 end 15/09/2026 → AC2a ERROR
            // off2 (case 2): YRO end 01/12/2026, YRC1 tag end 15/11/2026 → valid (tag < order)
            String request = """
                    {
                      "hearingId": "h-sc8",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-c1-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-09-01"}]},
                        {"id": "rl-c1-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-09-15"}]},
                        {"id": "rl-c2-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-01"}]},
                        {"id": "rl-c2-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2026-11-15"}]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Frances", "lastName": "Morgan"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2}
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
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_YRC2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRC2 + "Frances Morgan.")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AC3 — YRO End Date Less Than 12 Months With Unpaid Work (YRUP1)
    // hearingDay 20/05/2026 → minimum end date 19/05/2027
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AC3 — YRO end date less than 12 months with unpaid work")
    class Ac3UnpaidWorkDuration {

        @Test
        @DisplayName("Scenario 9 — Single defendant; YRUP1 with order under 12 months produces AC3 ERROR")
        void single_defendant_yrup1_order_under_12_months_produces_ac3_error() throws Exception {
            // hearing 20/05/2026, minEndDate = 19/05/2027, orderEnd = 18/05/2027 → ERROR
            String request = """
                    {
                      "hearingId": "h-sc9",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-18"}]},
                        {"id": "rl-yrup1", "shortCode": "YRUP1", "category": "I",
                         "label": "Unpaid work", "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "George", "lastName": "Harris"}],
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
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_YRUP1)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRUP1 + "George Harris.")));
        }

        @Test
        @DisplayName("Scenario 10 — Three defendants; only the under-12-month YRUP1 defendant errors")
        void three_defendants_only_one_yrup1_under_12_months_produces_one_error() throws Exception {
            // d1: orderEnd 2027-05-18 < minEnd 2027-05-19 → ERROR
            // d2: orderEnd 2027-05-19 = minEnd 2027-05-19 → PASS (boundary)
            // d3: no YRUP1 → no AC3 check
            String request = """
                    {
                      "hearingId": "h-sc10",
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
                         "label": "Unpaid work", "defendantId": "d2", "offenceId": "off2"},
                        {"id": "rl-d3-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d3", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "Defendant", "lastName": "One"},
                        {"id": "d2", "firstName": "Defendant", "lastName": "Two"},
                        {"id": "d3", "firstName": "Defendant", "lastName": "Three"}
                      ],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2},
                        {"id": "off3", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 3}
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
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_YRUP1)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_BASE_YRUP1 + "Defendant One.")));
        }

        @Test
        @DisplayName("Scenario 11 — Same defendant, two offences with different error types (AC2 + AC3)")
        void same_defendant_ac2a_on_one_offence_and_ac3_on_another_produces_two_errors()
                throws Exception {
            // off-a1: YROEW end 01/04/2027, YRUP1 → AC3 (order < minEnd 19/05/2027)
            // off-a2: YROEW end 01/10/2026, YRC2 end 01/12/2026 → AC2a (YRC2 > YRO)
            // Defendant Beta: valid
            String request = """
                    {
                      "hearingId": "h-sc11",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-a1-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "da", "offenceId": "off-a1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-04-01"}]},
                        {"id": "rl-a1-yrup1", "shortCode": "YRUP1", "category": "I",
                         "label": "Unpaid work", "defendantId": "da", "offenceId": "off-a1"},
                        {"id": "rl-a2-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "da", "offenceId": "off-a2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-01"}]},
                        {"id": "rl-a2-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "da", "offenceId": "off-a2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-01"}]},
                        {"id": "rl-b-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "db", "offenceId": "off-b",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-01"}]}
                      ],
                      "defendants": [
                        {"id": "da", "firstName": "Defendant", "lastName": "Alpha"},
                        {"id": "db", "firstName": "Defendant", "lastName": "Beta"}
                      ],
                      "offences": [
                        {"id": "off-a1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off-a2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2},
                        {"id": "off-b", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 3}
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
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_YRC2, MSG_YRUP1)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].offenceId",
                            containsInAnyOrder("off-a1", "off-a2")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERR_MSG_BASE_YRC2 + "Defendant Alpha.",
                                    ERR_MSG_BASE_YRUP1 + "Defendant Alpha.")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AC4 — Multiple Distinct Error Types Across Multiple Defendants
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AC4 — Multiple error types shown simultaneously")
    class Ac4MultipleErrorTypes {

        @Test
        @DisplayName("Scenario 12 — Four defendants; three distinct errors, one valid")
        void four_defendants_three_distinct_error_types_one_valid_shows_all_errors()
                throws Exception {
            // d1: YROEW endDate 2026-05-19 (past) → AC1
            // d2: YROEW endDate 2026-10-01, YRC2 end 2026-11-01 → AC2a
            // d3: YROEW endDate 2027-05-18, YRUP1 → AC3
            // d4: YROEW endDate 2027-06-01 → valid
            String request = """
                    {
                      "hearingId": "h-sc12",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-d1", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-05-19"}]},
                        {"id": "rl-d2-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-01"}]},
                        {"id": "rl-d2-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-01"}]},
                        {"id": "rl-d3-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d3", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-18"}]},
                        {"id": "rl-d3-yrup1", "shortCode": "YRUP1", "category": "I",
                         "label": "Unpaid work", "defendantId": "d3", "offenceId": "off3"},
                        {"id": "rl-d4", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d4", "offenceId": "off4",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-01"}]}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "Harriet", "lastName": "King"},
                        {"id": "d2", "firstName": "Ivan", "lastName": "Lee"},
                        {"id": "d3", "firstName": "Julia", "lastName": "Moore"},
                        {"id": "d4", "firstName": "Kevin", "lastName": "Nash"}
                      ],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2},
                        {"id": "off3", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 3},
                        {"id": "off4", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 4}
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
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(3)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_AC1, MSG_YRC2, MSG_YRUP1)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(3)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERR_MSG_BASE_AC1 + "Harriet King.",
                                    ERR_MSG_BASE_YRC2 + "Ivan Lee.",
                                    ERR_MSG_BASE_YRUP1 + "Julia Moore.")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AC5 — Error Display Above Each Result (multiple errors per offence / per defendant)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AC5 — Errors shown above each result")
    class Ac5ErrorAboveResult {

        @Test
        @DisplayName("Scenario 13 — Single offence with both AC2a and AC3 violations simultaneously")
        void single_offence_ac2a_and_ac3_both_fire_simultaneously() throws Exception {
            // YROEW end 01/10/2026 (future, so no AC1)
            // YRC2 end 01/11/2026 > YRO end → AC2a
            // YRUP1 present; 2026-10-01 < minEnd 2027-05-19 → AC3
            String request = """
                    {
                      "hearingId": "h-sc13",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-01"}]},
                        {"id": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-01"}]},
                        {"id": "rl-yrup1", "shortCode": "YRUP1", "category": "I",
                         "label": "Unpaid work", "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Laura", "lastName": "Parker"}],
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
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_YRC2, MSG_YRUP1)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].offenceId",
                            containsInAnyOrder("off1", "off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERR_MSG_BASE_YRC2 + "Laura Parker.",
                                    ERR_MSG_BASE_YRUP1 + "Laura Parker.")));
        }

        @Test
        @DisplayName("Scenario 14 — Three offences, each with a different error type (AC1/AC2a/AC3)")
        void three_offences_each_with_a_different_error_type_produces_three_independent_errors()
                throws Exception {
            // off1: YROEW end 2026-05-19 (= day before hearing) → AC1
            // off2: YROEW end 2026-10-01, YRC2 end 2026-11-01 → AC2a
            // off3: YROEW end 2027-05-18, YRUP1 → AC3
            String request = """
                    {
                      "hearingId": "h-sc14",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-o1", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-05-19"}]},
                        {"id": "rl-o2-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-01"}]},
                        {"id": "rl-o2-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-01"}]},
                        {"id": "rl-o3-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-05-18"}]},
                        {"id": "rl-o3-yrup1", "shortCode": "YRUP1", "category": "I",
                         "label": "Unpaid work", "defendantId": "d1", "offenceId": "off3"}
                      ],
                      "defendants": [{"id": "d1", "firstName": "Mark", "lastName": "Quinn"}],
                      "offences": [
                        {"id": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"id": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2},
                        {"id": "off3", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 3}
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
                    .andExpect(jsonPath("$.warnings", empty()))
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(3)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_AC1, MSG_YRC2, MSG_YRUP1)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].offenceId",
                            containsInAnyOrder("off1", "off2", "off3")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(3)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERR_MSG_BASE_AC1 + "Mark Quinn.",
                                    ERR_MSG_BASE_YRC2 + "Mark Quinn.",
                                    ERR_MSG_BASE_YRUP1 + "Mark Quinn.")));
        }
    }
}
