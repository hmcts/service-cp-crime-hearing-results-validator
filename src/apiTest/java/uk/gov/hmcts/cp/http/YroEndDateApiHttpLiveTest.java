package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
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
 * Live HTTP coverage for DR-YRO-001 (Youth Rehabilitation Order end-date validation) against a
 * running service instance.
 *
 * <p>DR-YRO-001 is inserted into the {@code validation_rule} table as disabled by the Flyway
 * migration. {@link #enableRule()} and {@link #disableRule()} mutate the DB row then poll
 * {@code GET /api/validation/rules/DR-YRO-001} until the service reflects the new state,
 * eliminating fixed sleeps and the flakiness they cause when cache TTL varies.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>Happy path — no YRO result lines; rule evaluates but produces no issues</li>
 *   <li>Happy path — valid YRO with a future end date and no requirement violations</li>
 *   <li>AC2a — YRC2 (curfew) end date strictly after YRO end date → ERROR</li>
 *   <li>AC2b — YRC1 (curfew with electronic monitoring) end date strictly after YRO end date → ERROR</li>
 *   <li>AC2c — YRC3 (further curfew) end date strictly after YRO end date → ERROR</li>
 * </ul>
 */
class YroEndDateApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-YRO-001";

    private static final String RULE_ID_FIELD = "ruleId";
    private static final String AFFECTED_OFFENCES = "affectedOffences";
    private static final String ISSUE_MESSAGE = "message";
    private static final String SEVERITY_FIELD = "severity";
    private static final String SEVERITY_ERROR = "ERROR";
    private static final String OFFENCE_ID_FIELD = "offenceId";
    private static final String TEST_OFFENCE_ID = "off1";

    private static final String MSG_YRC2 =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Curfew";
    private static final String MSG_YRC1 =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Curfew with electronic monitoring";
    private static final String MSG_YRC3 =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Further curfew requirement made";

    private static final String DB_URL =
            System.getProperty("db.url", "jdbc:postgresql://localhost:5432/results-validator-db");
    private static final String DB_USER = System.getProperty("db.username", "postgres");
    private static final String DB_PASSWORD = System.getProperty("db.password", "postgres");

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * No YRO result lines in the hearing; DR-YRO-001 must not fire and the response must be
     * valid with no errors or warnings.
     */
    @Test
    void validate_no_yro_result_lines_should_return_valid_with_no_yro_issues() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h1",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                     "label": "Convicted", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Reed"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get("validationId").asText()).startsWith("val-");
        assertThat(json.get("mode").asText()).isEqualTo("advisory");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
    }

    /**
     * Valid YRO with a future end date (2027-06-17) and no curfew requirements.
     * DR-YRO-001 must not fire and the response must be valid.
     */
    @Test
    void validate_yro_with_future_end_date_should_return_valid() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h2",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-17"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Beth", "lastName": "Cole"}],
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
     * AC2a — YRC2 (curfew) end date is strictly after the YRO end date. DR-YRO-001 must produce
     * a single ERROR for the curfew breach.
     */
    @Test
    void ac2a_yrc2_curfew_end_date_after_yro_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h5",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC2", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Ethan", "lastName": "Grant"}],
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
                .isEqualToIgnoringWhitespace(MSG_YRC2);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(MSG_YRC2 + ". This affects Ethan Grant.");
    }

    /**
     * AC2a suppression — YRC2 end date matches the YRO end date (equal, not later). DR-YRO-001
     * must not fire because the curfew does not exceed the order.
     */
    @Test
    void ac2a_yrc2_end_date_equal_to_yro_end_date_should_not_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h6",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC2", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Fiona", "lastName": "Hart"}],
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
     * AC2b — YRC1 (curfew with electronic monitoring) end date is strictly after the YRO end date.
     * DR-YRO-001 must produce a single ERROR.
     */
    @Test
    void ac2b_yrc1_end_date_after_yro_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h7",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC1", "category": "I",
                     "label": "Curfew with electronic monitoring", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2027-01-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "George", "lastName": "Hill"}],
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
                .isEqualToIgnoringWhitespace(MSG_YRC1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(MSG_YRC1 + ". This affects George Hill.");
    }

    /**
     * AC2b suppression — YRC1 end date matches the YRO end date (equal, not later). DR-YRO-001
     * must not fire.
     */
    @Test
    void ac2b_yrc1_end_date_equal_to_yro_end_date_should_not_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h8",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC1", "category": "I",
                     "label": "Curfew with electronic monitoring", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2026-12-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Hannah", "lastName": "Iris"}],
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
     * AC2c — YRC3 (further curfew) end date is strictly after the YRO end date.
     * DR-YRO-001 must produce a single ERROR.
     */
    @Test
    void ac2c_yrc3_end_date_after_yro_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h9",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC3", "category": "I",
                     "label": "Further curfew requirement made", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "James", "lastName": "King"}],
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
                .isEqualToIgnoringWhitespace(MSG_YRC3);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(MSG_YRC3 + ". This affects James King.");
    }

    /**
     * AC2c suppression — YRC3 end date matches the YRO end date (equal, not later). DR-YRO-001
     * must not fire.
     */
    @Test
    void ac2c_yrc3_end_date_equal_to_yro_end_date_should_not_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h10",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC3", "category": "I",
                     "label": "Further curfew requirement made", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Laura", "lastName": "Moore"}],
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
     * Combined AC2a + AC2b + AC2c — all three curfew requirements breach the YRO end date in a
     * single hearing. DR-YRO-001 must produce three independent ERRORs, one per condition.
     */
    @Test
    void ac2_all_three_curfew_requirements_breach_simultaneously_should_produce_three_errors()
            throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h11",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "YRC2", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-31"}]},
                    {"resultLineId": "rl3", "shortCode": "YRC1", "category": "I",
                     "label": "Curfew with electronic monitoring", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2027-02-28"}]},
                    {"resultLineId": "rl4", "shortCode": "YRC3", "category": "I",
                     "label": "Further curfew requirement made", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-03-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Noah", "lastName": "Blake"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();

        final JsonNode issues = json.get(ERRORS).get(VALIDATION_ISSUES);
        assertThat(issues).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(issues.get(i).get(RULE_ID_FIELD).asText()).isEqualTo(RULE_ID);
            assertThat(issues.get(i).get(SEVERITY_FIELD).asText()).isEqualTo(SEVERITY_ERROR);
            assertThat(issues.get(i).get(AFFECTED_OFFENCES).get(0).get(OFFENCE_ID_FIELD).asText())
                    .isEqualTo(TEST_OFFENCE_ID);
        }

        final List<String> inlineMessages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            inlineMessages.add(
                    issues.get(i).get(AFFECTED_OFFENCES).get(0).get(ISSUE_MESSAGE).asText());
        }
        assertThat(inlineMessages).containsExactlyInAnyOrder(MSG_YRC2, MSG_YRC1, MSG_YRC3);

        final List<String> errorMessages = new ArrayList<>();
        json.get(ERRORS).get(ERROR_MESSAGES).forEach(n -> errorMessages.add(n.asText()));
        assertThat(errorMessages).hasSize(3);
        assertThat(errorMessages).containsExactlyInAnyOrder(
                MSG_YRC2 + ". This affects Noah Blake.",
                MSG_YRC1 + ". This affects Noah Blake.",
                MSG_YRC3 + ". This affects Noah Blake."
        );
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

    private List<String> rulesEvaluated(final JsonNode json) {
        final List<String> ids = new ArrayList<>();
        json.get(RULES_EVALUATED).forEach(n -> ids.add(n.asText()));
        return ids;
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
