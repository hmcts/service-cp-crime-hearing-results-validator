package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Live HTTP coverage for the DR-COEW-001 requirement duration-mismatch conditions (DUR-CUR,
 * DUR-CURE, DUR-AAR — Jira DD-41655, User Stories 4-7) against a running service instance.
 *
 * <p>DR-COEW-001 is seeded {@code enabled=false} by {@code V1.003__insert_dr_coew_001.sql}, so
 * the rule is enabled via JDBC for the whole class and restored to disabled afterwards; a
 * 2-second sleep either side allows the 1-second Caffeine cache TTL to expire.
 */
class RequirementDurationApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String WARNINGS = "warnings";
    private static final String RULE_ID = "DR-COEW-001";

    private static final String MSG_DUR_CURFEW_SUMMARY_BASE =
            "The end date for the Curfew Requirement does not match the period of the "
                    + "requirement. This affects ";
    private static final String MSG_DUR_AAR_SUMMARY_BASE =
            "The end date for the Alcohol Abstinence Monitoring Requirement does not match "
                    + "the period of the requirement. This affects ";

    private static final String DB_URL =
            System.getProperty("db.url", "jdbc:postgresql://localhost:5432/results-validator-db");
    private static final String DB_USER = System.getProperty("db.username", "postgres");
    private static final String DB_PASSWORD = System.getProperty("db.password", "postgres");

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void enableRule() throws Exception {
        setRuleEnabled(true);
        Thread.sleep(2000);
    }

    @AfterAll
    static void restoreRule() throws Exception {
        setRuleEnabled(false);
        Thread.sleep(2000);
    }

    private static void setRuleEnabled(final boolean enabled) throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE validation_rule SET enabled = ? WHERE id = 'DR-COEW-001'")) {
            ps.setBoolean(1, enabled);
            ps.executeUpdate();
        }
    }

    /**
     * Covers User Story 4 (DUR-CUR): a CUR requirement's End date does not equal
     * Start date + Curfew period - 1 day, which must block navigation with an ERROR.
     */
    @Test
    void cur_end_date_mismatch_should_produce_dur_cur_error() throws Exception {
        // Start date 2026-09-01 + curfewPeriod 30 - 1 day = 2026-09-30; entered 2026-10-01 (wrong)
        final String body = """
                {
                  "hearingId": "h-dur-cur",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                     "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-01"}]},
                    {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                     "defendantId": "d1", "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "startDate", "promptValue": "2026-09-01"},
                       {"promptRef": "curfewPeriod", "promptValue": "30"},
                       {"promptRef": "endDate", "promptValue": "2026-10-01"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("ruleId").asText()).isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("severity").asText()).isEqualTo("ERROR");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(durCurfewInlineMessage("30/09/2026"));
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualTo(MSG_DUR_CURFEW_SUMMARY_BASE + "John Smith.");
    }

    /**
     * Covers the non-violation half of User Story 4: a CUR End date exactly equal to
     * Start date + Curfew period - 1 day must not raise a duration-mismatch error.
     */
    @Test
    void cur_end_date_matching_calculated_duration_should_not_error() throws Exception {
        final String body = """
                {
                  "hearingId": "h-dur-cur-valid",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                     "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-09-30"}]},
                    {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                     "defendantId": "d1", "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "startDate", "promptValue": "2026-09-01"},
                       {"promptRef": "curfewPeriod", "promptValue": "30"},
                       {"promptRef": "endDate", "promptValue": "2026-09-30"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
    }

    /**
     * Covers User Story 5 (DUR-CURE): a CURE requirement's End date of tagging does not equal
     * Start date of tagging + Curfew and electronic monitoring period - 1 day.
     */
    @Test
    void cure_end_date_of_tagging_mismatch_should_produce_dur_cure_error() throws Exception {
        // Start of tagging 2026-09-01 + 60 - 1 = 2026-10-30; entered 2026-11-01 (wrong)
        final String body = """
                {
                  "hearingId": "h-dur-cure",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "COS", "category": "F", "label": "Community order",
                     "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-01"}]},
                    {"resultLineId": "rl-cure", "shortCode": "CURE", "category": "I", "label": "Requirement",
                     "defendantId": "d1", "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "startDateOfTagging", "promptValue": "2026-09-01"},
                       {"promptRef": "curfewAndElectronicMonitoringPeriod", "promptValue": "60"},
                       {"promptRef": "endDateOfTagging", "promptValue": "2026-11-01"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(durCurfewInlineMessage("30/10/2026"));
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualTo(MSG_DUR_CURFEW_SUMMARY_BASE + "Jane Doe.");
    }

    /**
     * Covers User Story 6 (DUR-AAR): an AAR requirement's Until date does not equal
     * hearing date + Number of days to abstain - 1 day.
     */
    @Test
    void aar_until_mismatch_should_produce_dur_aar_error() throws Exception {
        // hearingDay 2026-01-01 + 90 - 1 = 2026-03-31; entered 2026-04-01 (wrong)
        final String body = """
                {
                  "hearingId": "h-dur-aar",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                     "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-04-01"}]},
                    {"resultLineId": "rl-aar", "shortCode": "AAR", "category": "I", "label": "Requirement",
                     "defendantId": "d1", "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "numberOfDaysToAbstainFromConsumingAnyAlcohol", "promptValue": "90"},
                       {"promptRef": "until", "promptValue": "2026-04-01"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Sarah", "lastName": "Green"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(durAarInlineMessage("31/03/2026"));
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualTo(MSG_DUR_AAR_SUMMARY_BASE + "Sarah Green.");
    }

    /**
     * Covers User Story 7: a duration-mismatch error and an AC2 order-end-date error on the
     * same offence must both appear as separate entries, not collapse into one.
     */
    @Test
    void offence_failing_both_ac2_and_dur_cur_produces_two_separate_errors() throws Exception {
        // Order ends 2026-09-15 (before CUR's recorded 2026-09-29 end date -> AC2a fires).
        // CUR start 2026-09-01 + period 30 - 1 = 2026-09-30, entered 2026-09-29 -> DUR-CUR fires.
        final String body = """
                {
                  "hearingId": "h-dur-combo",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F", "label": "Community order",
                     "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-09-15"}]},
                    {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I", "label": "Requirement",
                     "defendantId": "d1", "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "startDate", "promptValue": "2026-09-01"},
                       {"promptRef": "curfewPeriod", "promptValue": "30"},
                       {"promptRef": "endDate", "promptValue": "2026-09-29"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Combo", "lastName": "Violator"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(2);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(2);
    }

    private static String durCurfewInlineMessage(final String calculatedEndDate) {
        return "The end date for the Curfew Requirement does not match the period of the "
                + "requirement. The current recorded period would mean the end date should be "
                + calculatedEndDate + ".";
    }

    private static String durAarInlineMessage(final String calculatedEndDate) {
        return "The end date for the Alcohol Abstinence Monitoring Requirement does not match "
                + "the period of the requirement. The current recorded period would mean the "
                + "end date should be " + calculatedEndDate + ".";
    }

    private JsonNode postValidate(final String body) throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("CJSCPPUID", "test-user");
        headers.set("CPP-ACTION", "validation-service.validate");

        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/validation/validate",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody());
    }
}
