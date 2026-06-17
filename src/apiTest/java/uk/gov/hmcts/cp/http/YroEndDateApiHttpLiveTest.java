package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
 * <p>DR-YRO-001 is always enabled (no DB override row; YAML default {@code enabled: true}), so
 * these tests do not require any DB manipulation.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>Happy path — no YRO result lines; rule evaluates but produces no issues</li>
 *   <li>Happy path — valid YRO with a future end date and no requirement violations</li>
 *   <li>AC1 — YRO end date on or before the hearing date → ERROR</li>
 *   <li>AC2a — YRC2 (curfew) end date strictly after YRO end date → ERROR</li>
 *   <li>AC3 — YRUP1 (unpaid work) present; YRO end date less than 12 months from hearing → ERROR</li>
 * </ul>
 */
class YroEndDateApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-YRO-001";

    private static final String MSG_AC1 =
            "The end date must be in the future";
    private static final String MSG_YRC2 =
            "The end date of the order must match or be longer than the end date of "
                    + "Youth Rehabilitation Requirement: Curfew";
    private static final String MSG_YRUP1 =
            "The end date of the order must be at least 12 months as it includes an "
                    + "unpaid work requirement";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**4
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
     * Valid YRO with a future end date (2027-06-17), no curfew requirements and no unpaid work.
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
     * AC1 — YRO end date equals the hearing date (not in the future). DR-YRO-001 must produce a
     * single ERROR that is non-blocking for warnings but blocks sharing ({@code isValid=false}).
     */
    @Test
    void ac1_yro_end_date_equal_to_hearing_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h3",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-06-17"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Chris", "lastName": "Day"}],
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
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("ruleId").asText())
                .isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("severity").asText())
                .isEqualTo("ERROR");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("affectedOffences")).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get("affectedOffences").get(0).get("offenceId").asText())
                .isEqualTo("off1");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get("affectedOffences").get(0).get("message").asText())
                .isEqualToIgnoringWhitespace(MSG_AC1);
    }

    /**
     * AC1 — YRO end date strictly before the hearing date. DR-YRO-001 must produce an ERROR.
     */
    @Test
    void ac1_yro_end_date_before_hearing_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h4",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-06-16"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Dana", "lastName": "Fox"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("ruleId").asText())
                .isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get("affectedOffences").get(0).get("message").asText())
                .isEqualToIgnoringWhitespace(MSG_AC1);
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
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("ruleId").asText())
                .isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("severity").asText())
                .isEqualTo("ERROR");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get("affectedOffences").get(0).get("offenceId").asText())
                .isEqualTo("off1");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get("affectedOffences").get(0).get("message").asText())
                .isEqualToIgnoringWhitespace(MSG_YRC2);
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
     * AC3 — YRUP1 (unpaid work) present; YRO end date is less than 12 calendar months from the
     * hearing date. DR-YRO-001 must produce a single ERROR.
     *
     * <p>Hearing date: 2026-06-17. Minimum end date: 2027-06-16 (hearingDay + 12m − 1d).
     * Order end date: 2027-06-15 (one day short) → violation.
     */
    @Test
    void ac3_yrup1_order_under_12_months_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h7",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-15"}]},
                    {"resultLineId": "rl2", "shortCode": "YRUP1", "category": "I",
                     "label": "Unpaid work", "defendantId": "d1", "offenceId": "off1"}
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
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("ruleId").asText())
                .isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("severity").asText())
                .isEqualTo("ERROR");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get("affectedOffences").get(0).get("offenceId").asText())
                .isEqualTo("off1");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0)
                .get("affectedOffences").get(0).get("message").asText())
                .isEqualToIgnoringWhitespace(MSG_YRUP1);
    }

    /**
     * AC3 boundary — YRUP1 present; YRO end date exactly equals the minimum (hearingDay + 12m − 1d).
     * DR-YRO-001 must not fire because the order meets the 12-month requirement.
     *
     * <p>Hearing date: 2026-06-17. Minimum end date: 2027-06-16. Order end date: 2027-06-16 → PASS.
     */
    @Test
    void ac3_yrup1_order_exactly_at_12_month_boundary_should_not_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "yro-h8",
                  "hearingDay": "2026-06-17",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "YROEW", "category": "F",
                     "label": "YRO", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-16"}]},
                    {"resultLineId": "rl2", "shortCode": "YRUP1", "category": "I",
                     "label": "Unpaid work", "defendantId": "d1", "offenceId": "off1"}
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
