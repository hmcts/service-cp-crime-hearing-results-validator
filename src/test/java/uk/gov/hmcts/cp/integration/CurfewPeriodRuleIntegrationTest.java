package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for DR-COEW-002 (Curfew and AAR period end-date validation).
 *
 * <p>Every scenario posts a full JSON payload through MockMvc and asserts on the
 * response shape. The test uses the real {@link uk.gov.hmcts.cp.services.rules.cel.CurfewPeriodPreprocessor}
 * wired into the Spring context via {@code @SpringBootTest}.
 *
 * <p>Covers:
 * <ul>
 *   <li>S1 — CUR day-period mismatch under COEW (error emitted)</li>
 *   <li>S2 — CUR day-period exact match under COEW (no error)</li>
 *   <li>S3 — CURE week-period mismatch under COEW (error emitted)</li>
 *   <li>S4 — YRC2 under YROEW mismatch (error emitted)</li>
 *   <li>S5 — YRC1 exact match under YROEW (no error)</li>
 *   <li>S6 — AAR exact match under COEW (no error)</li>
 *   <li>S7 — AAR mismatch under COEW (error emitted)</li>
 *   <li>S8 — AAR mismatch under COEW correct date (no DR-COEW-002 error)</li>
 *   <li>S9 — AAR under YROEW-only (no DR-COEW-002 error emitted)</li>
 *   <li>S10 — Two defendants, one violated (error scoped to correct defendant)</li>
 *   <li>S11 — CUR period in months correct (no error)</li>
 *   <li>S12 — CUR period in months mismatch (error emitted with correct date)</li>
 * </ul>
 */
class CurfewPeriodRuleIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_COEW_002_ERRORS =
            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]";

    // ── S1 ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S1 — CUR day-period mismatch under COEW → error emitted")
    class Scenario1 {

        @Test
        void cur_day_period_mismatch_should_produce_error() throws Exception {
            // start=2026-06-01, period=60 Days → expected=2026-07-30, actual=2026-07-31
            final String request = """
                    {
                      "hearingId": "h-s1",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-06-01"},
                           {"promptRef": "curfewPeriod", "type": "DURATION",
                            "childPrompts": [{"promptRef": "Days", "promptValue": "60", "type": "INT"}]},
                           {"promptRef": "endDate", "promptValue": "2026-07-31"}
                         ]}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(1)))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')].severity",
                            hasItem("ERROR")))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]..offenceId",
                            hasItem("off1")))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]..message",
                            hasItem(containsString("30/07/2026"))))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]..defendantId",
                            hasItem("d1")))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            not(empty())));
        }
    }

    // ── S2 ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S2 — CUR day-period exact match under COEW → no DR-COEW-002 error")
    class Scenario2 {

        @Test
        void cur_day_period_exact_match_should_not_produce_error() throws Exception {
            // start=2026-06-01, period=60 Days → expected=2026-07-30, actual=2026-07-30
            final String request = """
                    {
                      "hearingId": "h-s2",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-06-01"},
                           {"promptRef": "curfewPeriod", "type": "DURATION",
                            "childPrompts": [{"promptRef": "Days", "promptValue": "60", "type": "INT"}]},
                           {"promptRef": "endDate", "promptValue": "2026-07-30"}
                         ]}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(0)));
        }
    }

    // ── S3 ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S3 — CURE week-period mismatch under COEW → error emitted")
    class Scenario3 {

        @Test
        void cure_week_period_mismatch_should_produce_error() throws Exception {
            // startDateOfTagging=2026-06-01, period=4 Weeks → 2026-06-29 - 1 = 2026-06-28, actual=2026-06-30
            final String request = """
                    {
                      "hearingId": "h-s3",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-cure", "shortCode": "CURE", "category": "I", "label": "Curfew tag",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDateOfTagging", "promptValue": "2026-06-01"},
                           {"promptRef": "curfewAndElectronicMonitoringPeriod", "type": "DURATION",
                            "childPrompts": [{"promptRef": "Weeks", "promptValue": "4", "type": "INT"}]},
                           {"promptRef": "endDateOfTagging", "promptValue": "2026-06-30"}
                         ]}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(1)))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]..message",
                            hasItem(containsString("28/06/2026"))));
        }
    }

    // ── S4 ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S4 — YRC2 mismatch under YROEW → error emitted with YRC2 key prefix")
    class Scenario4 {

        @Test
        void yrc2_mismatch_under_yroew_should_produce_error() throws Exception {
            // start=2026-06-01, period=30 Days → 2026-07-01 - 1 = 2026-06-30, actual=2026-07-01
            final String request = """
                    {
                      "hearingId": "h-s4",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-yro", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-yrc2", "shortCode": "YRC2", "category": "I", "label": "Curfew req",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-06-01"},
                           {"promptRef": "curfewPeriod", "type": "DURATION",
                            "childPrompts": [{"promptRef": "Days", "promptValue": "30", "type": "INT"}]},
                           {"promptRef": "endDate", "promptValue": "2026-07-01"}
                         ]}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(1)))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]..message",
                            hasItem(containsString("30/06/2026"))));
        }
    }

    // ── S5 ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S5 — YRC1 exact match under YROEW → no DR-COEW-002 error")
    class Scenario5 {

        @Test
        void yrc1_exact_match_should_not_produce_error() throws Exception {
            // startDateOfTagging=2026-07-10, period=14 Days → 2026-07-24 - 1 = 2026-07-23, actual=2026-07-23
            final String request = """
                    {
                      "hearingId": "h-s5",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-yro", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-yrc1", "shortCode": "YRC1", "category": "I", "label": "Curfew tag req",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDateOfTagging", "promptValue": "2026-07-10"},
                           {"promptRef": "curfewAndElectronicMonitoringPeriod", "type": "DURATION",
                            "childPrompts": [{"promptRef": "Days", "promptValue": "14", "type": "INT"}]},
                           {"promptRef": "endDateOfTagging", "promptValue": "2026-07-23"}
                         ]}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(0)));
        }
    }

    // ── S6 ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S6 — AAR exact match under COEW → no DR-COEW-002 error")
    class Scenario6 {

        @Test
        void aar_exact_match_should_not_produce_error() throws Exception {
            // hearingDay=2026-05-26, days=120 → 2026-05-26+120d=2026-09-23, -1d=2026-09-22
            final String request = """
                    {
                      "hearingId": "h-s6",
                      "hearingDay": "2026-05-26",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-aar", "shortCode": "AAR", "category": "I", "label": "AAR",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "numberOfDaysToAbstainFromConsumingAnyAlcohol", "promptValue": "120"},
                           {"promptRef": "until", "promptValue": "2026-09-22"}
                         ]}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(0)));
        }
    }

    // ── S7 ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S7 — AAR mismatch under COEW → error emitted with correct expected date")
    class Scenario7 {

        @Test
        void aar_mismatch_should_produce_error() throws Exception {
            // hearingDay=2026-05-26, days=120 → expected=2026-09-22, actual=2026-09-24
            final String request = """
                    {
                      "hearingId": "h-s7",
                      "hearingDay": "2026-05-26",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-aar", "shortCode": "AAR", "category": "I", "label": "AAR",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "numberOfDaysToAbstainFromConsumingAnyAlcohol", "promptValue": "120"},
                           {"promptRef": "until", "promptValue": "2026-09-24"}
                         ]}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(1)))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')].severity",
                            hasItem("ERROR")))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]..message",
                            hasItem(containsString("22/09/2026"))))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]..defendantId",
                            hasItem("d1")));
        }
    }

    // ── S8 ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S8 — AAR correct date under COEW → no DR-COEW-002 error")
    class Scenario8 {

        @Test
        void aar_correct_date_should_not_produce_error() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-s8",
                      "hearingDay": "2026-05-26",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COS", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-aar", "shortCode": "AAR", "category": "I", "label": "AAR",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "numberOfDaysToAbstainFromConsumingAnyAlcohol", "promptValue": "30"},
                           {"promptRef": "until", "promptValue": "2026-06-24"}
                         ]}
                      ],
                      "defendants": [{"id": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [{"id": "off1", "offenceCode": "TH68001",
                                    "offenceTitle": "Theft", "orderIndex": 1}]
                    }
                    """;
            // hearingDay=2026-05-26, days=30 → 2026-06-25 - 1 = 2026-06-24 ✓

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(0)));
        }
    }

    // ── S9 ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S9 — AAR under YROEW-only (no CO parent) → no DR-COEW-002 error emitted")
    class Scenario9 {

        @Test
        void aar_under_yroew_only_should_not_produce_error() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-s9",
                      "hearingDay": "2026-05-26",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-yro", "shortCode": "YROEW", "category": "F", "label": "YRO",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-aar", "shortCode": "AAR", "category": "I", "label": "AAR",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "numberOfDaysToAbstainFromConsumingAnyAlcohol", "promptValue": "120"},
                           {"promptRef": "until", "promptValue": "2026-09-24"}
                         ]}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(0)));
        }
    }

    // ── S10 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S10 — Two defendants, one violated → error scoped to correct defendant only")
    class Scenario10 {

        @Test
        void two_defendants_one_violated_should_scope_error_correctly() throws Exception {
            // d1: CUR correct (start=2026-06-01, 60 days → 2026-07-30, actual=2026-07-30)
            // d2: CUR mismatch (start=2026-06-01, 60 days → 2026-07-30, actual=2026-07-31)
            final String request = """
                    {
                      "hearingId": "h-s10",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-co1", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-cur1", "shortCode": "CUR", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-06-01"},
                           {"promptRef": "curfewPeriod", "type": "DURATION",
                            "childPrompts": [{"promptRef": "Days", "promptValue": "60", "type": "INT"}]},
                           {"promptRef": "endDate", "promptValue": "2026-07-30"}
                         ]},
                        {"id": "rl-co2", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-cur2", "shortCode": "CUR", "category": "I", "label": "Curfew",
                         "defendantId": "d2", "offenceId": "off2",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-06-01"},
                           {"promptRef": "curfewPeriod", "type": "DURATION",
                            "childPrompts": [{"promptRef": "Days", "promptValue": "60", "type": "INT"}]},
                           {"promptRef": "endDate", "promptValue": "2026-07-31"}
                         ]}
                      ],
                      "defendants": [
                        {"id": "d1", "firstName": "Alice", "lastName": "Smith"},
                        {"id": "d2", "firstName": "Bob", "lastName": "Jones"}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(1)))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]..defendantId",
                            hasItem("d2")))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]..offenceId",
                            hasItem("off2")));
        }
    }

    // ── S11 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S11 — CUR period in months correct → no DR-COEW-002 error")
    class Scenario11 {

        @Test
        void cur_month_period_correct_should_not_produce_error() throws Exception {
            // start=2026-06-01, period=3 Months → 2026-09-01 - 1 = 2026-08-31, actual=2026-08-31
            final String request = """
                    {
                      "hearingId": "h-s11",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-06-01"},
                           {"promptRef": "curfewPeriod", "type": "DURATION",
                            "childPrompts": [{"promptRef": "Months", "promptValue": "3", "type": "INT"}]},
                           {"promptRef": "endDate", "promptValue": "2026-08-31"}
                         ]}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(0)));
        }
    }

    // ── S12 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S12 — CUR period in months mismatch → error emitted with correct date")
    class Scenario12 {

        @Test
        void cur_month_period_mismatch_should_produce_error() throws Exception {
            // start=2026-06-01, period=3 Months → 2026-09-01 - 1 = 2026-08-31, actual=2026-09-01
            final String request = """
                    {
                      "hearingId": "h-s12",
                      "hearingDay": "2026-01-01",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"id": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                        {"id": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Curfew",
                         "defendantId": "d1", "offenceId": "off1",
                         "prompts": [
                           {"promptRef": "startDate", "promptValue": "2026-06-01"},
                           {"promptRef": "curfewPeriod", "type": "DURATION",
                            "childPrompts": [{"promptRef": "Months", "promptValue": "3", "type": "INT"}]},
                           {"promptRef": "endDate", "promptValue": "2026-09-01"}
                         ]}
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
                    .andExpect(jsonPath(DR_COEW_002_ERRORS, hasSize(1)))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-COEW-002')]..message",
                            hasItem(containsString("31/08/2026"))));
        }
    }
}
