package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.annotation.Resource;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;

/**
 * End-to-end integration tests for DR-YRO-001 covering the YRO end-date validation
 * scenario set (AC2) with hearing date 20/05/2026.
 *
 * <p>Scenarios map directly to the business acceptance criteria:
 * <ul>
 *   <li>AC2 — YRO end date is earlier than a linked curfew requirement end date
 *              (YRC2 / YRC1 / YRC3)</li>
 * </ul>
 */
class YroEndDateValidationIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_YRO_ERRORS =
            "$.errors.validationIssues[?(@.ruleId=='DR-YRO-001')]";
    private static final String YRO_RULE_ID = "DR-YRO-001";

    @Resource
    private ValidationRuleRepository repository;

    @Resource
    private CacheManager cacheManager;

    @BeforeEach
    void enableYroRule() {
        repository.save(ValidationRuleEntity.builder()
                .id(YRO_RULE_ID)
                .enabled(true)
                .severity("ERROR")
                .updatedAt(Instant.now())
                .updatedBy("test-setup")
                .build());
        evictOverrideCache(YRO_RULE_ID);
    }

    @AfterEach
    void disableYroRule() {
        repository.save(ValidationRuleEntity.builder()
                .id(YRO_RULE_ID)
                .enabled(false)
                .severity("ERROR")
                .updatedAt(Instant.now())
                .updatedBy("test-teardown")
                .build());
        evictOverrideCache(YRO_RULE_ID);
    }

    private void evictOverrideCache(final String ruleId) {
        Cache cache = cacheManager.getCache("ruleOverrides");
        if (cache != null) {
            cache.evict(ruleId);
        }
    }

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
    // AC2 error summary bases
    private static final String ERR_MSG_BASE_YRC2 = MSG_YRC2 + ". This affects ";
    private static final String ERR_MSG_BASE_YRC1 = MSG_YRC1 + ". This affects ";
    private static final String ERR_MSG_BASE_YRC3 = MSG_YRC3 + ". This affects ";

    // DD-42850 duration-mismatch messages
    private static final String MSG_DUR_BASE =
            "The end date for the Curfew Requirement does not match the period of the requirement.";
    private static final String ERR_MSG_DUR_BASE = MSG_DUR_BASE + " This affects ";

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
                        {"resultLineId": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"resultLineId": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "David", "lastName": "Evans"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                        {"resultLineId": "rl-ag-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-01"}]},
                        {"resultLineId": "rl-ag-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2026-10-15"}]},
                        {"resultLineId": "rl-cb-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-01"}]},
                        {"resultLineId": "rl-cb-yrc3", "shortCode": "YRC3", "category": "I",
                         "label": "Further curfew", "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-20"}]}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Alex", "lastName": "Green"},
                        {"defendantId": "d2", "firstName": "Chloe", "lastName": "Black"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2}
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
                        {"resultLineId": "rl-c1-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-09-01"}]},
                        {"resultLineId": "rl-c1-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-09-15"}]},
                        {"resultLineId": "rl-c2-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-01"}]},
                        {"resultLineId": "rl-c2-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2026-11-15"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Frances", "lastName": "Morgan"}],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2}
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

        @Test
        @DisplayName("Scenario T004 — All three curfew requirements (YRC1, YRC2, YRC3) "
                + "breach YRO end date simultaneously")
        void three_curfew_types_breach_simultaneously_produce_three_ac2_errors() throws Exception {
            // YRO end 10/01/2026; YRC2 end 10/02/2026, YRC1 tag end 10/03/2026,
            // YRC3 end 10/04/2026 — all three exceed the order end date
            String request = """
                    {
                      "hearingId": "h-sc-t004",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-01-10"}]},
                        {"resultLineId": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-02-10"}]},
                        {"resultLineId": "rl-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2026-03-10"}]},
                        {"resultLineId": "rl-yrc3", "shortCode": "YRC3", "category": "I",
                         "label": "Further curfew", "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-04-10"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Sam", "lastName": "Taylor"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(3)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(MSG_YRC2, MSG_YRC1, MSG_YRC3)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(3)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERR_MSG_BASE_YRC2 + "Sam Taylor.",
                                    ERR_MSG_BASE_YRC1 + "Sam Taylor.",
                                    ERR_MSG_BASE_YRC3 + "Sam Taylor.")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AC2 — No Violation (boundary / absent-requirement cases)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AC2 — no violation when curfew requirements do not exceed YRO end date")
    class Ac2NoViolation {

        @Test
        @DisplayName("Scenario T005 — YRC2 end date equal to YRO end date is not a violation (boundary)")
        void yrc2_end_date_equal_to_yro_end_date_should_not_produce_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-sc-t005",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-06-15"}]},
                        {"resultLineId": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-06-15"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Priya", "lastName": "Nair"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }

        @Test
        @DisplayName("Scenario T006 — YRC2 end date before YRO end date is not a violation")
        void yrc2_end_date_before_yro_end_date_should_not_produce_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-sc-t006",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-08-20"}]},
                        {"resultLineId": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-07-01"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Liam", "lastName": "Osei"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }

        @Test
        @DisplayName("Scenario T008 — YRO with no curfew child requirements does not trigger AC2")
        void yro_without_any_curfew_child_requirements_should_not_produce_ac2_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-sc-t008",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-09-01"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Nadia", "lastName": "Khan"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }

        @Test
        @DisplayName("Scenario T012 — Valid YRO: all curfew requirements within order end date "
                + "produces no errors")
        void yro_with_all_curfew_requirements_within_end_date_should_be_valid() throws Exception {
            // YRO end 31/12/2026; YRC2 end 31/12/2026 (equal), YRC1 tag end 01/12/2026 (before),
            // YRC3 end 01/11/2026 (before) — none exceed the order end date
            String request = """
                    {
                      "hearingId": "h-sc-t012",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"resultLineId": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"resultLineId": "rl-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2026-12-01"}]},
                        {"resultLineId": "rl-yrc3", "shortCode": "YRC3", "category": "I",
                         "label": "Further curfew", "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-01"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Oliver", "lastName": "Bennett"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DUR-YRC2 — Curfew requirement duration mismatch (DD-42850, US1)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DUR-YRC2 — Curfew requirement end date does not match calculated duration")
    class DurYrc2RequirementDuration {

        @Test
        @DisplayName("YRC2 end date mismatch blocks navigation with the correct calculated date")
        void yrc2_duration_mismatch_produces_dur_yrc2_error_with_calculated_date() throws Exception {
            // Start date 01/09/2026 + Curfew period 21 Days − 1 day = 21/09/2026 (correct value)
            // Recorded end date is 20/09/2026 — one day early
            String request = """
                    {
                      "hearingId": "h-dur-yrc2",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                        {"resultLineId": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-09-01"},
                           {"promptRef": "curfewPeriod", "promptValue": "21 Days"},
                           {"promptRef": "endDate", "promptValue": "2026-09-20"}
                         ]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Priya", "lastName": "Nair"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_DUR_BASE
                                    + " The current recorded period would mean the end date should be"
                                    + " 21/09/2026.")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_DUR_BASE + "Priya Nair.")));
        }

        @Test
        @DisplayName("YRC2 end date exactly matching the formula produces no error")
        void yrc2_end_date_matching_formula_should_not_produce_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-dur-yrc2-valid",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                        {"resultLineId": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-09-01"},
                           {"promptRef": "curfewPeriod", "promptValue": "21 Days"},
                           {"promptRef": "endDate", "promptValue": "2026-09-21"}
                         ]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Liam", "lastName": "Osei"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DUR-YRC1 — Curfew with electronic monitoring duration mismatch (DD-42850, US2)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DUR-YRC1 — Curfew with electronic monitoring end date of tagging does not match "
            + "calculated duration")
    class DurYrc1RequirementDuration {

        @Test
        @DisplayName("YRC1 end date of tagging mismatch blocks navigation with the correct "
                + "calculated date")
        void yrc1_duration_mismatch_produces_dur_yrc1_error_with_calculated_date() throws Exception {
            // Start date of tagging 01/09/2026 + period 60 Days − 1 day = 30/10/2026 (correct value)
            // Recorded end date of tagging is 01/11/2026 — mismatch
            String request = """
                    {
                      "hearingId": "h-dur-yrc1",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YRONI", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                        {"resultLineId": "rl-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDateOfTagging", "promptValue": "2026-09-01"},
                           {"promptRef": "curfewAndElectronicMonitoringPeriod", "promptValue": "60 Days"},
                           {"promptRef": "endDateOfTagging", "promptValue": "2026-11-01"}
                         ]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId",
                            is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message",
                            is(MSG_DUR_BASE
                                    + " The current recorded period would mean the end date should be"
                                    + " 30/10/2026.")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERR_MSG_DUR_BASE + "Jane Doe.")));
        }

        @Test
        @DisplayName("YRC1 end date of tagging exactly matching the formula produces no error")
        void yrc1_end_date_of_tagging_matching_formula_should_not_produce_error() throws Exception {
            String request = """
                    {
                      "hearingId": "h-dur-yrc1-valid",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YRONI", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                        {"resultLineId": "rl-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDateOfTagging", "promptValue": "2026-09-01"},
                           {"promptRef": "curfewAndElectronicMonitoringPeriod", "promptValue": "60 Days"},
                           {"promptRef": "endDateOfTagging", "promptValue": "2026-10-30"}
                         ]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Marcus", "lastName": "Reid"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // US3 — Duration-mismatch errors combine with other validation errors
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("US3 — duration-mismatch errors combine with each other and with the AC2 check")
    class DurationMismatchCombination {

        @Test
        @DisplayName("One defendant with a YRC2 duration mismatch and another with a YRC1 "
                + "duration mismatch both produce validation issues, combined into one error "
                + "summary message since both conditions share identical message text")
        void yrc2_and_yrc1_duration_mismatches_on_different_defendants_both_appear() throws Exception {
            String request = """
                    {
                      "hearingId": "h-dur-combined-1",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-d1-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                        {"resultLineId": "rl-d1-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-09-01"},
                           {"promptRef": "curfewPeriod", "promptValue": "21 Days"},
                           {"promptRef": "endDate", "promptValue": "2026-09-20"}
                         ]},
                        {"resultLineId": "rl-d2-order", "shortCode": "YRONI", "category": "F", "label": "YRO",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                        {"resultLineId": "rl-d2-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew+tag",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [
                           {"promptRef": "startDateOfTagging", "promptValue": "2026-09-01"},
                           {"promptRef": "curfewAndElectronicMonitoringPeriod", "promptValue": "60 Days"},
                           {"promptRef": "endDateOfTagging", "promptValue": "2026-11-01"}
                         ]}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Priya", "lastName": "Nair"},
                        {"defendantId": "d2", "firstName": "Jane", "lastName": "Doe"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2}
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
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].offenceId",
                            containsInAnyOrder("off1", "off2")))
                    // DUR-YRC2 and DUR-YRC1 share identical errorMessageTemplate text, so the
                    // service aggregates them into a single "This affects" entry naming both
                    // defendants (DefaultValidationService groups by ruleId + resolved message).
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(MSG_DUR_BASE + " This affects Priya Nair and Jane Doe.")));
        }

        @Test
        @DisplayName("A single offence failing both the AC2 order-end-date check and the "
                + "DUR-YRC2 duration-mismatch check produces two separate errors")
        void offence_failing_both_ac2_and_dur_yrc2_produces_two_separate_errors() throws Exception {
            // YRO end date 15/09/2026, YRC2 end date 30/09/2026 → AC2a violation (curfew exceeds order)
            // YRC2 start 01/09/2026 + period 21 Days − 1 = 21/09/2026 (correct), but recorded 30/09/2026
            // → DUR-YRC2 violation too — same offence fails both independent checks simultaneously
            String request = """
                    {
                      "hearingId": "h-dur-combined-2",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-09-15"}]},
                        {"resultLineId": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-09-01"},
                           {"promptRef": "curfewPeriod", "promptValue": "21 Days"},
                           {"promptRef": "endDate", "promptValue": "2026-09-30"}
                         ]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Sam", "lastName": "Taylor"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath(DR_YRO_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].offenceId",
                            containsInAnyOrder("off1", "off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[*].affectedOffences[0].message",
                            containsInAnyOrder(
                                    MSG_YRC2,
                                    MSG_DUR_BASE
                                            + " The current recorded period would mean the end date"
                                            + " should be 21/09/2026.")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERR_MSG_BASE_YRC2 + "Sam Taylor.",
                                    ERR_MSG_DUR_BASE + "Sam Taylor.")));
        }

        @Test
        @DisplayName("No duration-mismatch or other errors: navigation proceeds normally")
        void no_errors_should_allow_navigation() throws Exception {
            String request = """
                    {
                      "hearingId": "h-dur-combined-valid",
                      "hearingDay": "2026-05-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl-order", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                        {"resultLineId": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-09-01"},
                           {"promptRef": "curfewPeriod", "promptValue": "21 Days"},
                           {"promptRef": "endDate", "promptValue": "2026-09-21"}
                         ]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Oliver", "lastName": "Bennett"}],
                      "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
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
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath("$.warnings", empty()));
        }
    }

}
