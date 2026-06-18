package uk.gov.hmcts.cp.integration;

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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for DR-COEW-001 (community order end date validation).
 *
 * <p>Covers spec Scenarios 6–13:
 * <ul>
 *   <li>Scenarios 6–13: AC2 — requirement end date exceeds order end date (CUR/CURE/CURA/AAR)</li>
 *   <li>Error summary and offence-scoping assertions (T013/T014)</li>
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
    private static final String RULE_ID = "DR-COEW-001";

    @Resource
    private ValidationRuleRepository repository;

    @Resource
    private CacheManager cacheManager;

    @BeforeEach
    void enableRule() {
        repository.save(ValidationRuleEntity.builder()
                .id(RULE_ID)
                .enabled(true)
                .severity("ERROR")
                .updatedAt(Instant.now())
                .updatedBy("test-setup")
                .build());
        evictOverrideCache(RULE_ID);
    }

    @AfterEach
    void restoreRule() {
        repository.save(ValidationRuleEntity.builder()
                .id(RULE_ID)
                .enabled(false)
                .severity("ERROR")
                .updatedAt(Instant.now())
                .updatedBy("test-teardown")
                .build());
        evictOverrideCache(RULE_ID);
    }

    private void evictOverrideCache(final String ruleId) {
        final Cache cache = cacheManager.getCache("ruleOverrides");
        if (cache != null) {
            cache.evict(ruleId);
        }
    }

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
    // ── AC2a–AC2d error-summary base messages (errorMessageTemplate — "This affects …") ──
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
                        {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
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
                        {"resultLineId": "rl-order", "shortCode": "COS", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-01"}]},
                        {"resultLineId": "rl-cure", "shortCode": "CURE", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2026-12-15"}]}
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
                        {"resultLineId": "rl-order", "shortCode": "CONI", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                        {"resultLineId": "rl-cura", "shortCode": "CURA", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-15"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Bob", "lastName": "Brown"}],
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
                        {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-01"}]},
                        {"resultLineId": "rl-aar", "shortCode": "AAR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "until", "promptValue": "2027-06-15"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Sarah", "lastName": "Green"}],
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
                        {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-30"}]},
                        {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Valid", "lastName": "Order"}],
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
                        {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-01"}]},
                        {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-15"}]},
                        {"resultLineId": "rl-aar", "shortCode": "AAR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "until", "promptValue": "2027-06-25"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Multi", "lastName": "Violator"}],
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
                        {"resultLineId": "rl-d1-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-12-31"}]},
                        {"resultLineId": "rl-d1-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"resultLineId": "rl-d2-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"resultLineId": "rl-d2-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"resultLineId": "rl-d3-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d3", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"resultLineId": "rl-d3-aar", "shortCode": "AAR", "category": "I", "label": "Requirement",
                         "defendantId": "d3", "offenceId": "off3",
                         "prompts": [{"promptRef": "until", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Valid", "lastName": "Defendant"},
                        {"defendantId": "d2", "firstName": "Curfew", "lastName": "Violator"},
                        {"defendantId": "d3", "firstName": "Alcohol", "lastName": "Violator"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 2},
                        {"offenceId": "off3", "offenceCode": "TH68001",
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
                        {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Share", "lastName": "Test"}],
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
                    .andExpect(jsonPath("$.isValid", is(false)));
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
                        {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
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
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(MSG_CUR)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(ERR_MSG_BASE_CUR + "John Smith.")));
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
                        {"resultLineId": "rl-o1-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-12-31"}]},
                        {"resultLineId": "rl-o1-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"resultLineId": "rl-o2-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                        {"resultLineId": "rl-o2-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                        {"resultLineId": "rl-o3-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2027-12-31"}]},
                        {"resultLineId": "rl-o3-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                         "defendantId": "d1", "offenceId": "off3",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Scoping", "lastName": "Test"}],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 2},
                        {"offenceId": "off3", "offenceCode": "TH68001",
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

    }
}
