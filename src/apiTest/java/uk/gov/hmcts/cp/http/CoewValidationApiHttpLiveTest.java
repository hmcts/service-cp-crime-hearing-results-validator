package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

/**
 * Live HTTP coverage for DR-COEW-001 (community order end date validation) against a running
 * service instance.
 *
 * <p>Covers:
 * <ul>
 *   <li>Clean pass when no community order result lines are present</li>
 *   <li>AC2a–AC2d: each requirement type (CUR/CURE/CURA/AAR) exceeds the order end date</li>
 *   <li>AC2 valid path: order end date is on or after the requirement end date</li>
 *   <li>Multiple violations on one defendant (CUR + AAR)</li>
 *   <li>Multi-defendant isolation: only the violating defendant appears in error summary</li>
 * </ul>
 */
class CoewValidationApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String DR_COEW_RULE_ID = "DR-COEW-001";

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
    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Covers the clean pass: no community order result lines in the request means DR-COEW-001
     * should produce no errors or warnings, and the rule must appear in rulesEvaluated.
     */
    @Test
    void no_coew_result_lines_should_return_valid_with_rule_evaluated() throws Exception {
        final String body = """
                {
                  "hearingId": "h-coew-clean",
                  "hearingDay": "2026-05-14",
                  "courtType": "MAGISTRATES",
                  "resultLines": [],
                  "defendants": [],
                  "offences": []
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(rulesEvaluated(json)).contains(DR_COEW_RULE_ID);
    }

    /**
     * Covers AC2a: a COEW order whose end date is earlier than the CUR requirement end date,
     * producing a blocking ERROR with the correct inline message and error summary.
     */
    @Test
    void ac2a_cur_requirement_exceeds_order_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "h-coew-ac2a",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                    {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I",
                     "label": "Requirement", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        final JsonNode issues = coewIssues(json);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get("ruleId").asText()).isEqualTo(DR_COEW_RULE_ID);
        assertThat(issues.get(0).get("severity").asText()).isEqualTo("ERROR");
        assertThat(issues.get(0).get("affectedOffences").get(0).get("offenceId").asText())
                .isEqualTo("off1");
        assertThat(issues.get(0).get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(MSG_CUR);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualTo(ERR_MSG_BASE_CUR + "John Smith.");
    }

    /**
     * Covers AC2b: a COS order whose end date is earlier than the CURE (endDateOfTagging)
     * requirement, producing a blocking ERROR with the correct message.
     */
    @Test
    void ac2b_cure_requirement_exceeds_order_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "h-coew-ac2b",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "COS", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-01"}]},
                    {"resultLineId": "rl-cure", "shortCode": "CURE", "category": "I",
                     "label": "Requirement", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDateOfTagging", "promptValue": "2026-12-15"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(WARNINGS)).isEmpty();
        final JsonNode issues = coewIssues(json);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(MSG_CURE);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualTo(ERR_MSG_BASE_CURE + "Jane Doe.");
    }

    /**
     * Covers AC2c: a CONI order whose end date is earlier than the CURA requirement end date,
     * producing a blocking ERROR with the correct message.
     */
    @Test
    void ac2c_cura_requirement_exceeds_order_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "h-coew-ac2c",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "CONI", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]},
                    {"resultLineId": "rl-cura", "shortCode": "CURA", "category": "I",
                     "label": "Requirement", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-15"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Bob", "lastName": "Brown"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        final JsonNode issues = coewIssues(json);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(MSG_CURA);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualTo(ERR_MSG_BASE_CURA + "Bob Brown.");
    }

    /**
     * Covers AC2d: a COEW order whose end date is earlier than the AAR {@code until} date,
     * producing a blocking ERROR with the correct message.
     */
    @Test
    void ac2d_aar_requirement_exceeds_order_end_date_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "h-coew-ac2d",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-01"}]},
                    {"resultLineId": "rl-aar", "shortCode": "AAR", "category": "I",
                     "label": "Requirement", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "until", "promptValue": "2027-06-15"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Sarah", "lastName": "Green"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        final JsonNode issues = coewIssues(json);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(MSG_AAR);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualTo(ERR_MSG_BASE_AAR + "Sarah Green.");
    }

    /**
     * Covers the AC2 valid path: the order end date is on or after the CUR requirement end date,
     * so no errors or warnings should be produced.
     */
    @Test
    void ac2_order_end_date_on_or_after_requirement_should_be_valid() throws Exception {
        final String body = """
                {
                  "hearingId": "h-coew-ac2-valid",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-12-30"}]},
                    {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I",
                     "label": "Requirement", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Valid", "lastName": "Order"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers multiple violations on one defendant: both CUR and AAR requirements exceed the order
     * end date, producing two separate DR-COEW-001 errors.
     */
    @Test
    void multiple_violations_cur_and_aar_on_same_defendant_should_produce_two_errors()
            throws Exception {
        final String body = """
                {
                  "hearingId": "h-coew-multi",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-order", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-01"}]},
                    {"resultLineId": "rl-cur", "shortCode": "CUR", "category": "I",
                     "label": "Requirement", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-06-15"}]},
                    {"resultLineId": "rl-aar", "shortCode": "AAR", "category": "I",
                     "label": "Requirement", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "until", "promptValue": "2027-06-25"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Multi", "lastName": "Violator"}],
                  "offences": [{"offenceId": "off1", "offenceCode": "TH68001",
                                "offenceTitle": "Theft", "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(coewIssues(json)).hasSize(2);
        final String messages = json.get(ERRORS).get(VALIDATION_ISSUES).toString();
        assertThat(messages).contains(MSG_CUR).contains(MSG_AAR);
    }

    /**
     * Covers multi-defendant isolation: three defendants in one hearing, only the two with
     * violations appear in the error summary; the valid defendant is not referenced.
     */
    @Test
    void multi_defendant_only_violating_defendants_appear_in_error_summary() throws Exception {
        final String body = """
                {
                  "hearingId": "h-coew-isolation",
                  "hearingDay": "2026-01-01",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl-d1-order", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-12-31"}]},
                    {"resultLineId": "rl-d1-cur", "shortCode": "CUR", "category": "I",
                     "label": "Requirement", "defendantId": "d1", "offenceId": "off1",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                    {"resultLineId": "rl-d2-order", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d2", "offenceId": "off2",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-10-30"}]},
                    {"resultLineId": "rl-d2-cur", "shortCode": "CUR", "category": "I",
                     "label": "Requirement", "defendantId": "d2", "offenceId": "off2",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2026-11-30"}]},
                    {"resultLineId": "rl-d3-order", "shortCode": "COEW", "category": "F",
                     "label": "Community order", "defendantId": "d3", "offenceId": "off3",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2028-12-31"}]},
                    {"resultLineId": "rl-d3-cur", "shortCode": "CUR", "category": "I",
                     "label": "Requirement", "defendantId": "d3", "offenceId": "off3",
                     "prompts": [{"promptRef": "endDate", "promptValue": "2027-01-01"}]}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Valid", "lastName": "Defendant"},
                    {"defendantId": "d2", "firstName": "Curfew", "lastName": "Violator"},
                    {"defendantId": "d3", "firstName": "Also", "lastName": "Valid"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 2},
                    {"offenceId": "off3", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 3}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(coewIssues(json)).hasSize(1);
        final String errorMessages = json.get(ERRORS).get(ERROR_MESSAGES).toString();
        assertThat(errorMessages).contains("Curfew Violator");
        assertThat(errorMessages).doesNotContain("Valid Defendant").doesNotContain("Also Valid");
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

    private JsonNode coewIssues(final JsonNode json) {
        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                                json.get(ERRORS).get(VALIDATION_ISSUES).elements(),
                                Spliterator.ORDERED),
                        false)
                .filter(n -> DR_COEW_RULE_ID.equals(n.get("ruleId").asText()))
                .collect(
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance::arrayNode,
                        (arr, n) -> arr.add(n),
                        (a, b) -> b.elements().forEachRemaining(a::add));
    }

    private java.util.List<String> rulesEvaluated(final JsonNode json) {
        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                                json.get(RULES_EVALUATED).elements(),
                                Spliterator.ORDERED),
                        false)
                .map(JsonNode::asText)
                .toList();
    }
}
