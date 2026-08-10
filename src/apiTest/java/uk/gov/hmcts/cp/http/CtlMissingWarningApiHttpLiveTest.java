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
 * Live HTTP coverage for DR-CTL-003 (CTL missing warning) against a running service instance.
 *
 * <p>DR-CTL-003 defaults to enabled by its Flyway seed migration and nothing else in this suite
 * disables it, so no rule-state setup is needed here.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>AC1 — a remand-type result with no existing CTL record, no CTL result in the current
 *       hearing, and no recorded conviction produces a warning</li>
 *   <li>Bypass — an existing CTL record from a previous hearing suppresses the warning</li>
 *   <li>Bypass — a CTL result in the current hearing suppresses the warning</li>
 *   <li>Bypass — a convicted offence suppresses the warning</li>
 *   <li>Bypass — a result whose short code is not a remand code produces no warning</li>
 *   <li>Multi-offence scoping — the warning is scoped to the breaching offence only</li>
 * </ul>
 */
class CtlMissingWarningApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-CTL-003";
    private static final String AFFECTED_OFFENCES = "affectedOffences";

    private static final String EXPECTED_MESSAGE =
            "This offence does not have a CTL. If the trial has started a CTL is not "
                    + "needed. It is your responsibility to check and confirm.";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Covers AC1: a remand-type result (RI) with no existing CTL record, no CTL result in the
     * current hearing, and no recorded conviction must produce a single non-blocking warning.
     */
    @Test
    void ri_result_no_existing_ctl_no_ctl_result_not_convicted_should_produce_warning()
            throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-05-06",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RI", "label": "Remand in custody",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).hasSize(1);
        assertThat(json.get(WARNINGS).get(0).get("ruleId").asText()).isEqualTo(RULE_ID);
        assertThat(json.get(WARNINGS).get(0).get("severity").asText()).isEqualTo("WARNING");
        assertThat(json.get(WARNINGS).get(0).get(AFFECTED_OFFENCES)).hasSize(1);
        assertThat(json.get(WARNINGS).get(0).get(AFFECTED_OFFENCES).get(0).get("offenceId").asText())
                .isEqualTo("off1");
        assertThat(json.get(WARNINGS).get(0).get(AFFECTED_OFFENCES).get(0).get("message").asText())
                .isEqualToIgnoringWhitespace(EXPECTED_MESSAGE);
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
    }

    /**
     * Covers the existing-CTL bypass: an offence with a CTL record from a previous hearing must
     * not produce a warning even on a fresh remand result.
     */
    @Test
    void existing_ctl_record_should_suppress_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h2",
                  "hearingDay": "2026-05-06",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RI", "label": "Remand in custody",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "hasExistingCtlRecord": true, "isConvicted": false}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers the current-hearing-CTL bypass: a CTL result line recorded against the same
     * offence in the current hearing must suppress the warning.
     */
    @Test
    void ctl_result_in_current_hearing_should_suppress_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h3",
                  "hearingDay": "2026-05-06",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RI", "label": "Remand in custody",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "CTL", "label": "Custody time limit",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers the convicted-offence bypass: a convicted offence must suppress the warning even
     * with a qualifying remand result and no CTL record.
     */
    @Test
    void convicted_offence_should_suppress_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h4",
                  "hearingDay": "2026-05-06",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RI", "label": "Remand in custody",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers the non-remand bypass: a result whose short code is not one of the configured
     * remand codes must produce no warning.
     */
    @Test
    void no_trigger_result_should_produce_no_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h5",
                  "hearingDay": "2026-05-06",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers multi-offence scoping: the warning is scoped to the breaching offence only, even
     * when another offence on the same hearing has a remand result but an existing CTL record.
     */
    @Test
    void warning_scoped_to_breaching_offence_only() throws Exception {
        final String body = """
                {
                  "hearingId": "h6",
                  "hearingDay": "2026-05-06",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RI", "label": "Remand in custody",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "RI", "label": "Remand in custody",
                     "defendantId": "d1", "offenceId": "off2"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft A",
                     "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false},
                    {"offenceId": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft B",
                     "orderIndex": 2, "hasExistingCtlRecord": true, "isConvicted": false}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).hasSize(1);
        assertThat(json.get(WARNINGS).get(0).get(AFFECTED_OFFENCES)).hasSize(1);
        assertThat(json.get(WARNINGS).get(0).get(AFFECTED_OFFENCES).get(0).get("offenceId").asText())
                .isEqualTo("off1");
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
