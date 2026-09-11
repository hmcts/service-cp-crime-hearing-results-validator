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
 * Live HTTP coverage for DR-URG-008 (urgent result missing warning) against a running service.
 *
 * <p>DR-URG-008 defaults to enabled by its Flyway seed migration and nothing else in this suite
 * disables it, so no rule-state setup is needed here.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>AC1 — all conditional-bail offences are bail-ended (DS) with no URGENT result →
 *       a defendant-level WARNING is emitted with the prescribed message</li>
 *   <li>Bypass — an URGENT result present on a conditional-bail offence suppresses the warning</li>
 * </ul>
 */
class UrgentMissingWarningApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-URG-008";
    private static final String AFFECTED_DEFENDANTS = "affectedDefendants";

    private static final String EXPECTED_MESSAGE =
            "The defendant's conditional bail has ended. You may need to add the URGENT result"
                    + " and select \"Bail conditions cancelled\" on one of the offences before sharing.";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Covers AC1: all conditional-bail offences bail-ended with DS, no URGENT result →
     * a defendant-level WARNING is emitted with the prescribed message.
     */
    @Test
    void ds_bail_ending_no_urgent_on_cb_offence_should_emit_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-urg-1",
                  "hearingDay": "2026-05-06",
                  "courtType": "CROWN",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off-1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                     "orderIndex": 1, "bailStatus": "B"}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).hasSize(1);
        assertThat(json.get(WARNINGS).get(0).get("ruleId").asText()).isEqualTo(RULE_ID);
        assertThat(json.get(WARNINGS).get(0).get("severity").asText()).isEqualTo("WARNING");
        assertThat(json.get(WARNINGS).get(0).get(AFFECTED_DEFENDANTS)).hasSize(1);
        assertThat(json.get(WARNINGS).get(0).get(AFFECTED_DEFENDANTS).get(0).get("defendantId").asText())
                .isEqualTo("d1");
        assertThat(json.get(WARNINGS).get(0).get(AFFECTED_DEFENDANTS).get(0).get("message").asText())
                .isEqualToIgnoringWhitespace(EXPECTED_MESSAGE);
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
    }

    /**
     * Covers the URGENT bypass: an URGENT result on the conditional-bail offence must suppress
     * the warning even when the bail-ending result is present.
     */
    @Test
    void urgent_result_present_should_suppress_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-urg-2",
                  "hearingDay": "2026-05-06",
                  "courtType": "CROWN",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "URGENT", "label": "Urgent",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off-1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                     "orderIndex": 1, "bailStatus": "B"}
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
