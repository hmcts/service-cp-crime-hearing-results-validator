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
 * Live HTTP coverage for DR-COEW-005 (community order end-date validation) against a
 * running service instance.
 *
 * <p>DR-COEW-005 is inserted into the {@code validation_rule} table as enabled by the Flyway
 * migration. {@link #enableRule()} re-asserts that state (guarding against leakage from other
 * test classes) then polls {@code GET /api/validation/rules/DR-COEW-005} until the service
 * reflects it, eliminating fixed sleeps and the flakiness they cause when cache TTL varies.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>Happy path — no community order result lines; rule evaluates but produces no issues</li>
 *   <li>Happy path — valid community order with a future end date and no requirement violations</li>
 *   <li>AC2a — CUR (curfew) end date strictly after the order end date → ERROR</li>
 *   <li>AC2b — CURE (curfew with electronic monitoring) tag end date strictly after the order end date → ERROR</li>
 *   <li>AC2c — CURA (further curfew) end date strictly after the order end date → ERROR</li>
 *   <li>AC2d — AAR (alcohol abstinence) until date strictly after the order end date → ERROR</li>
 * </ul>
 */
class CommunityOrderEndDateApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-COEW-005";

    private static final String RULE_ID_FIELD = "ruleId";
    private static final String AFFECTED_OFFENCES = "affectedOffences";
    private static final String ISSUE_MESSAGE = "message";
    private static final String SEVERITY_FIELD = "severity";
    private static final String SEVERITY_ERROR = "ERROR";
    private static final String OFFENCE_ID_FIELD = "offenceId";
    private static final String TEST_OFFENCE_ID = "off1";

    private static final String MSG_PREFIX =
            "The end date of the order must match or be longer than the end date of ";
    private static final String MSG_CUR = MSG_PREFIX + "Curfew (community requirement)";
    private static final String MSG_CURE = MSG_PREFIX + "Curfew with electronic monitoring";
    private static final String MSG_CURA = MSG_PREFIX + "Further curfew requirement made";
    private static final String MSG_AAR = MSG_PREFIX + "Alcohol abstinence and monitoring";

    private static final String MSG_DUR_CUR_SUMMARY =
            "The end date for the Curfew Requirement does not match the period of the requirement";
    private static final String MSG_DUR_CUR_INLINE =
            "The end date for the Curfew Requirement does not match the period of the "
                    + "requirement. The current recorded period would mean the end date "
                    + "should be 30/09/2026.";

    private static final String DB_URL =
            System.getProperty("db.url", "jdbc:postgresql://localhost:5432/results-validator-db");
    private static final String DB_USER = System.getProperty("db.username", "postgres");
    private static final String DB_PASSWORD = System.getProperty("db.password", "postgres");

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * No community order result lines in the hearing; DR-COEW-005 must not fire and the response
     * must be valid with no errors or warnings.
     */
    @Test
    void validate_no_community_order_result_lines_should_return_valid_with_no_coew_issues()
            throws Exception {
        final String body = """
                {
                  "hearingId": "coew-h1",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "CUR", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-17"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Reed"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1, "isConvicted": true}
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
     * Valid community order with a future end date (2027-06-17) and no requirement violations.
     * DR-COEW-005 must not fire and the response must be valid.
     */
    @Test
    void validate_community_order_with_future_end_date_should_return_valid() throws Exception {
        final String body = """
                {
                  "hearingId": "coew-h2",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-17"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Beth", "lastName": "Cole"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * AC2a — CUR (curfew) end date is strictly after the order end date. DR-COEW-005 must
     * produce a single ERROR for the curfew breach.
     */
    @Test
    void ac2a_cur_end_date_after_order_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "coew-h5",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "CUR", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Ethan", "lastName": "Grant"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1, "isConvicted": true}
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
                .isEqualToIgnoringWhitespace(MSG_CUR);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(MSG_CUR + ". This affects Ethan Grant.");
    }

    /**
     * AC2a suppression — CUR end date matches the order end date (equal, not later).
     * DR-COEW-005 must not fire because the curfew does not exceed the order.
     */
    @Test
    void ac2a_cur_end_date_equal_to_order_end_date_should_not_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "coew-h6",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "CUR", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Fiona", "lastName": "Hart"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * AC2b — CURE (curfew with electronic monitoring) tag end date is strictly after the order
     * end date. DR-COEW-005 must produce a single ERROR.
     */
    @Test
    void ac2b_cure_tag_end_date_after_order_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "coew-h7",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "COS", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "CURE", "category": "I",
                     "label": "Curfew with electronic monitoring", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2027-01-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "George", "lastName": "Hill"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get(RULE_ID_FIELD).asText())
                .isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get(AFFECTED_OFFENCES).get(0).get(ISSUE_MESSAGE).asText())
                .isEqualToIgnoringWhitespace(MSG_CURE);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(MSG_CURE + ". This affects George Hill.");
    }

    /**
     * AC2c — CURA (further curfew) end date is strictly after the order end date.
     * DR-COEW-005 must produce a single ERROR.
     */
    @Test
    void ac2c_cura_end_date_after_order_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "coew-h8",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "CONI", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "CURA", "category": "I",
                     "label": "Further curfew requirement made", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "James", "lastName": "King"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get(RULE_ID_FIELD).asText())
                .isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get(AFFECTED_OFFENCES).get(0).get(ISSUE_MESSAGE).asText())
                .isEqualToIgnoringWhitespace(MSG_CURA);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(MSG_CURA + ". This affects James King.");
    }

    /**
     * AC2d — AAR (alcohol abstinence) until date is strictly after the order end date.
     * DR-COEW-005 must produce a single ERROR.
     */
    @Test
    void ac2d_aar_until_date_after_order_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "coew-h9",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "AAR", "category": "I",
                     "label": "Alcohol abstinence and monitoring", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "until", "promptValue": "2027-01-31"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Sarah", "lastName": "Green"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get(RULE_ID_FIELD).asText())
                .isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get(AFFECTED_OFFENCES).get(0).get(ISSUE_MESSAGE).asText())
                .isEqualToIgnoringWhitespace(MSG_AAR);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(MSG_AAR + ". This affects Sarah Green.");
    }

    /**
     * Combined AC2a + AC2b + AC2c + AC2d — all four requirements breach the order end date in a
     * single hearing. DR-COEW-005 must produce four independent ERRORs, one per condition.
     */
    @Test
    void ac2_all_four_requirements_breach_simultaneously_should_produce_four_errors()
            throws Exception {
        final String body = """
                {
                  "hearingId": "coew-h11",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-31"}]},
                    {"resultLineId": "rl2", "shortCode": "CUR", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-31"}]},
                    {"resultLineId": "rl3", "shortCode": "CURE", "category": "I",
                     "label": "Curfew with electronic monitoring", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2027-02-28"}]},
                    {"resultLineId": "rl4", "shortCode": "CURA", "category": "I",
                     "label": "Further curfew requirement made", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-03-31"}]},
                    {"resultLineId": "rl5", "shortCode": "AAR", "category": "I",
                     "label": "Alcohol abstinence and monitoring", "defendantId": "d1",
                     "offenceId": "off1",
                     "prompts": [{"promptRef": "until", "promptValue": "2027-04-30"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Noah", "lastName": "Blake"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();

        final JsonNode issues = json.get(ERRORS).get(VALIDATION_ISSUES);
        assertThat(issues).hasSize(4);
        for (int i = 0; i < 4; i++) {
            assertThat(issues.get(i).get(RULE_ID_FIELD).asText()).isEqualTo(RULE_ID);
            assertThat(issues.get(i).get(SEVERITY_FIELD).asText()).isEqualTo(SEVERITY_ERROR);
            assertThat(issues.get(i).get(AFFECTED_OFFENCES).get(0).get(OFFENCE_ID_FIELD).asText())
                    .isEqualTo(TEST_OFFENCE_ID);
        }

        final List<String> inlineMessages = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            inlineMessages.add(
                    issues.get(i).get(AFFECTED_OFFENCES).get(0).get(ISSUE_MESSAGE).asText());
        }
        assertThat(inlineMessages).containsExactlyInAnyOrder(MSG_CUR, MSG_CURE, MSG_CURA, MSG_AAR);

        final String affectsSuffix = ". This affects Noah Blake.";
        final List<String> errorMessages = new ArrayList<>();
        json.get(ERRORS).get(ERROR_MESSAGES).forEach(n -> errorMessages.add(n.asText()));
        assertThat(errorMessages).hasSize(4);
        assertThat(errorMessages).containsExactlyInAnyOrder(
                MSG_CUR + affectsSuffix,
                MSG_CURE + affectsSuffix,
                MSG_CURA + affectsSuffix,
                MSG_AAR + affectsSuffix
        );
    }

    /**
     * DUR-CUR — CUR's own recorded end date does not match its calculated duration (start date +
     * curfew period - 1 day), independent of the AC2 order-end-date check. DR-COEW-005 must
     * produce a single ERROR carrying the correctly calculated end date.
     */
    @Test
    void dur_cur_end_date_not_matching_calculated_duration_should_produce_error() throws Exception {
        // Start date 2026-09-01 + curfewPeriod 30 - 1 day = 2026-09-30; entered 2026-10-01 (wrong)
        final String body = """
                {
                  "hearingId": "coew-h12",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-01"}]},
                    {"resultLineId": "rl2", "shortCode": "CUR", "category": "I",
                     "label": "Curfew", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [
                       {"promptRef": "startDate", "promptValue": "2026-09-01"},
                       {"promptRef": "curfewPeriod", "promptValue": "30"},
                       {"promptRef": "endDate", "promptValue": "2026-10-01"}
                     ]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Priya", "lastName": "Shah"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get(RULE_ID_FIELD).asText())
                .isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get(AFFECTED_OFFENCES).get(0).get(ISSUE_MESSAGE).asText())
                .isEqualToIgnoringWhitespace(MSG_DUR_CUR_INLINE);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(MSG_DUR_CUR_SUMMARY + ". This affects Priya Shah.");
    }

    @BeforeAll
    static void enableRule() throws Exception {
        setRuleEnabled(true);
        awaitRuleState(true);
    }

    @AfterAll
    static void restoreRule() throws Exception {
        // restore the Flyway seed default (DR-COEW-005 ships enabled)
        setRuleEnabled(true);
        awaitRuleState(true);
    }

    private static void setRuleEnabled(final boolean enabled) throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE validation_rule SET enabled = ? WHERE id = 'DR-COEW-005'")) {
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
                "DR-COEW-005 did not reach enabled=" + expected + " within 5 s");
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
