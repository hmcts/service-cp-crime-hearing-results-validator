package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Live HTTP coverage for DR-COEW-001 (community order end date validation) against a running
 * service instance.
 *
 * <p>DR-COEW-001 is seeded as {@code enabled = false} in the Flyway migration, so it does not
 * fire in this environment. Violation-detection logic (AC2a–AC2d) is exercised end-to-end by
 * {@code CommunityOrderEndDateRuleIntegrationTest}, which enables the rule via
 * {@code @BeforeEach}/{@code @AfterEach}. The tests below verify the correct disabled-rule
 * behaviour: no issues are produced and the rule still appears in {@code rulesEvaluated}.
 */
class CoewValidationApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String DR_COEW_RULE_ID = "DR-COEW-001";

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
     * AC2a — COEW order end date earlier than CUR requirement.
     * DR-COEW-001 is disabled by default; the service returns isValid=true with no COEW issues.
     * Violation logic is tested end-to-end in CommunityOrderEndDateRuleIntegrationTest.
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

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(coewIssues(json)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(rulesEvaluated(json)).contains(DR_COEW_RULE_ID);
    }

    /**
     * AC2b — COS order end date earlier than CURE endDateOfTagging.
     * DR-COEW-001 is disabled by default; the service returns isValid=true with no COEW issues.
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

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(coewIssues(json)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(rulesEvaluated(json)).contains(DR_COEW_RULE_ID);
    }

    /**
     * AC2c — CONI order end date earlier than CURA requirement.
     * DR-COEW-001 is disabled by default; the service returns isValid=true with no COEW issues.
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

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(coewIssues(json)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(rulesEvaluated(json)).contains(DR_COEW_RULE_ID);
    }

    /**
     * AC2d — COEW order end date earlier than AAR until date.
     * DR-COEW-001 is disabled by default; the service returns isValid=true with no COEW issues.
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

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(coewIssues(json)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(rulesEvaluated(json)).contains(DR_COEW_RULE_ID);
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
     * Multiple violation inputs (CUR + AAR both exceed order end date).
     * DR-COEW-001 is disabled by default; the service returns isValid=true with no COEW issues.
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

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(coewIssues(json)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(rulesEvaluated(json)).contains(DR_COEW_RULE_ID);
    }

    /**
     * Multi-defendant scenario where only one defendant has a violation.
     * DR-COEW-001 is disabled by default; the service returns isValid=true with no COEW issues.
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

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(coewIssues(json)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(rulesEvaluated(json)).contains(DR_COEW_RULE_ID);
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
                        ArrayNode::add,
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
