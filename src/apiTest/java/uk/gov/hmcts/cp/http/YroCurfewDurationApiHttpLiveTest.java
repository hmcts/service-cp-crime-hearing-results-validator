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
 * Live HTTP coverage for the DD-42850 extension to DR-YRO-001 (curfew requirement duration
 * validation) against a running service instance.
 *
 * <p>DR-YRO-001 is inserted into the {@code validation_rule} table as disabled by the Flyway
 * migration. {@link #enableRule()} and {@link #disableRule()} mutate the DB row then poll
 * {@code GET /api/validation/rules/DR-YRO-001} until the service reflects the new state,
 * eliminating fixed sleeps and the flakiness they cause when cache TTL varies.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>DUR-YRC2 — Curfew (YRC2) end date does not match Start date + Curfew period − 1 day</li>
 *   <li>DUR-YRC1 — Curfew with electronic monitoring (YRC1) end date of tagging does not match
 *       Start date of tagging + period − 1 day</li>
 *   <li>Exact-match (no-error) cases for both conditions</li>
 *   <li>Combined case — a single offence failing both the existing AC2a order-end-date check and
 *       DUR-YRC2 simultaneously</li>
 * </ul>
 */
class YroCurfewDurationApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String WARNINGS = "warnings";
    private static final String RULE_ID = "DR-YRO-001";

    private static final String RULE_ID_FIELD = "ruleId";
    private static final String AFFECTED_OFFENCES = "affectedOffences";
    private static final String ISSUE_MESSAGE = "message";
    private static final String SEVERITY_FIELD = "severity";
    private static final String SEVERITY_ERROR = "ERROR";
    private static final String OFFENCE_ID_FIELD = "offenceId";
    private static final String TEST_OFFENCE_ID = "off1";

    private static final String MSG_DUR_BASE =
            "The end date for the Curfew Requirement does not match the period of the requirement.";
    private static final String MSG_YRC2_ORDER =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Curfew";

    private static final String DB_URL =
            System.getProperty("db.url", "jdbc:postgresql://localhost:5432/results-validator-db");
    private static final String DB_USER = System.getProperty("db.username", "postgres");
    private static final String DB_PASSWORD = System.getProperty("db.password", "postgres");

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * DUR-YRC2 — YRC2 end date is one day early relative to Start date + Curfew period − 1 day.
     * DR-YRO-001 must produce a single ERROR with the correct calculated end date inline.
     */
    @Test
    void dur_yrc2_end_date_mismatch_should_produce_error_with_calculated_date() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-dur-h1",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC2", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "startDate", "promptValue": "2026-09-01"},
                       {"promptRef": "curfewPeriod", "promptValue": "21 Days"},
                       {"promptRef": "endDate", "promptValue": "2026-09-20"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Priya", "lastName": "Nair"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get(RULE_ID_FIELD).asText())
                .isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get(SEVERITY_FIELD).asText())
                .isEqualTo(SEVERITY_ERROR);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get(AFFECTED_OFFENCES).get(0).get(OFFENCE_ID_FIELD).asText())
                .isEqualTo(TEST_OFFENCE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get(AFFECTED_OFFENCES).get(0).get(ISSUE_MESSAGE).asText())
                .isEqualToIgnoringWhitespace(MSG_DUR_BASE
                        + " The current recorded period would mean the end date should be 21/09/2026.");
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(MSG_DUR_BASE + " This affects Priya Nair.");
    }

    /**
     * DUR-YRC2 suppression — YRC2 end date exactly matches Start date + Curfew period − 1 day.
     * DR-YRO-001 must not fire.
     */
    @Test
    void dur_yrc2_end_date_matching_formula_should_not_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-dur-h2",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC2", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "startDate", "promptValue": "2026-09-01"},
                       {"promptRef": "curfewPeriod", "promptValue": "21 Days"},
                       {"promptRef": "endDate", "promptValue": "2026-09-21"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Liam", "lastName": "Osei"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * DUR-YRC1 — YRC1 end date of tagging does not match Start date of tagging + period − 1 day.
     * DR-YRO-001 must produce a single ERROR with the correct calculated end date inline.
     */
    @Test
    void dur_yrc1_end_date_of_tagging_mismatch_should_produce_error_with_calculated_date()
            throws Exception {
        final String body = """
                {
                  "hearingId": "yro-dur-h3",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YRONI", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC1", "category": "I",
                     "label": "Curfew with electronic monitoring", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "startDateOfTagging", "promptValue": "2026-09-01"},
                       {"promptRef": "curfewAndElectronicMonitoringPeriod", "promptValue": "60 Days"},
                       {"promptRef": "endDateOfTagging", "promptValue": "2026-11-01"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get(RULE_ID_FIELD).asText())
                .isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get(SEVERITY_FIELD).asText())
                .isEqualTo(SEVERITY_ERROR);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get(AFFECTED_OFFENCES).get(0).get(OFFENCE_ID_FIELD).asText())
                .isEqualTo(TEST_OFFENCE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get(AFFECTED_OFFENCES).get(0).get(ISSUE_MESSAGE).asText())
                .isEqualToIgnoringWhitespace(MSG_DUR_BASE
                        + " The current recorded period would mean the end date should be 30/10/2026.");
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(MSG_DUR_BASE + " This affects Jane Doe.");
    }

    /**
     * DUR-YRC1 suppression — YRC1 end date of tagging exactly matches Start date of tagging +
     * period − 1 day. DR-YRO-001 must not fire.
     */
    @Test
    void dur_yrc1_end_date_of_tagging_matching_formula_should_not_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-dur-h4",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YRONI", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC1", "category": "I",
                     "label": "Curfew with electronic monitoring", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "startDateOfTagging", "promptValue": "2026-09-01"},
                       {"promptRef": "curfewAndElectronicMonitoringPeriod", "promptValue": "60 Days"},
                       {"promptRef": "endDateOfTagging", "promptValue": "2026-10-30"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Marcus", "lastName": "Reid"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Combined case — a single offence fails both the existing AC2a order-end-date check and the
     * new DUR-YRC2 duration-mismatch check simultaneously. DR-YRO-001 must produce two separate
     * ERRORs referencing the same offence.
     */
    @Test
    void offence_failing_both_ac2a_and_dur_yrc2_should_produce_two_separate_errors()
            throws Exception {
        final String body = """
                {
                  "hearingId": "yro-dur-h5",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-09-15"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC2", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "startDate", "promptValue": "2026-09-01"},
                       {"promptRef": "curfewPeriod", "promptValue": "21 Days"},
                       {"promptRef": "endDate", "promptValue": "2026-09-30"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Sam", "lastName": "Taylor"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(2);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(2);

        final JsonNode issues = json.get(ERRORS).get(VALIDATION_ISSUES);
        assertThat(issues.get(0).get(AFFECTED_OFFENCES).get(0).get(OFFENCE_ID_FIELD).asText())
                .isEqualTo(TEST_OFFENCE_ID);
        assertThat(issues.get(1).get(AFFECTED_OFFENCES).get(0).get(OFFENCE_ID_FIELD).asText())
                .isEqualTo(TEST_OFFENCE_ID);

        final String firstMessage =
                issues.get(0).get(AFFECTED_OFFENCES).get(0).get(ISSUE_MESSAGE).asText();
        final String secondMessage =
                issues.get(1).get(AFFECTED_OFFENCES).get(0).get(ISSUE_MESSAGE).asText();
        assertThat(firstMessage.startsWith(MSG_YRC2_ORDER) || secondMessage.startsWith(MSG_YRC2_ORDER))
                .isTrue();
        assertThat(firstMessage.startsWith(MSG_DUR_BASE) || secondMessage.startsWith(MSG_DUR_BASE))
                .isTrue();
    }

    @BeforeAll
    static void enableRule() throws Exception {
        setRuleEnabled(true);
        awaitRuleState(true);
    }

    @AfterAll
    static void disableRule() throws Exception {
        setRuleEnabled(false);
        awaitRuleState(false);
    }

    private static void setRuleEnabled(final boolean enabled) throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE validation_rule SET enabled = ? WHERE id = 'DR-YRO-001'")) {
            ps.setBoolean(1, enabled);
            ps.executeUpdate();
        }
    }

    private static void awaitRuleState(final boolean expected) throws Exception {
        final RestTemplate client = new RestTemplate();
        final HttpHeaders headers = new HttpHeaders();
        headers.set("CJSCPPUID", "test-setup");
        final HttpEntity<Void> request = new HttpEntity<>(headers);
        final ObjectMapper objectMapper = new ObjectMapper();
        final String url = System.getProperty("app.baseUrl", "http://localhost:8082")
                + "/api/validation/rules/" + RULE_ID;
        final long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            final ResponseEntity<String> response = client.exchange(
                    url, HttpMethod.GET, request, String.class);
            final JsonNode json = objectMapper.readTree(response.getBody());
            if (json.get("enabled").asBoolean() == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException(
                "DR-YRO-001 did not reach enabled=" + expected + " within 5 s");
    }

    private JsonNode postValidate(final String body) throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("CJSCPPUID", "test-user");

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
