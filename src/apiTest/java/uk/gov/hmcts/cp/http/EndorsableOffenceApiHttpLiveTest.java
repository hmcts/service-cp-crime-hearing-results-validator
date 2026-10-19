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
 * Live HTTP coverage for DR-END-012 (endorsable offence warning) against a running service.
 *
 * <p>DR-END-012 defaults to enabled by its Flyway seed migration and nothing in this suite
 * disables it, so no rule-state setup is needed here.
 *
 * <p>The docker-compose WireMock mount serves {@code wiremock/mappings/
 * referencedataoffences-endorsable-stub.json} automatically. That file maps:
 * <ul>
 *   <li>{@code live-endorsable-flag1} → {@code endorsableFlag: true}</li>
 *   <li>{@code live-endorsable-flag0} → {@code endorsableFlag: false}</li>
 * </ul>
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>AC1 — endorsable offence with a Category-F non-excluded result and no satisfying code →
 *       an offence-level WARNING is emitted with the prescribed message</li>
 *   <li>AC2 — satisfying result present on an endorsable offence → no warning</li>
 *   <li>AC3 — excluded final result only on an endorsable offence → no warning</li>
 *   <li>AC4 — non-endorsable offence (endorsableFlag=false) → no warning</li>
 *   <li>AC5 — warning clears when a satisfying result is added (stateless equivalent of AC2)</li>
 *   <li>AC6 — mixed endorsable/non-endorsable offences: warning only on the endorsable one</li>
 *   <li>AC7 — DR-END-012 and DR-SENT-001 both appear in the same response</li>
 * </ul>
 */
class EndorsableOffenceApiHttpLiveTest {

    private static final String WARNINGS = "warnings";
    private static final String RULE_ID_FIELD = "ruleId";
    private static final String RULE_ID = "DR-END-012";
    private static final String ENDORSABLE_CODE = "live-endorsable-flag1";
    private static final String NON_ENDORSABLE_CODE = "live-endorsable-flag0";
    private static final String IMP_CODE = "IMP";
    private static final String EXPECTED_MESSAGE =
            "This offence is endorsable. Add an endorsement, disqualification or special reasons result.";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * AC1: endorsable offence with a final non-excluded result and no satisfying code emits a
     * WARNING with the prescribed message, severity, and validation level.
     */
    @Test
    void endorsableOffence_finalResultNoSatisfyingCode_shouldEmitWarning() throws Exception {
        final JsonNode json = postValidate(buildRequest(ENDORSABLE_CODE, IMP_CODE));

        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
        final JsonNode warning = findByRuleId(json.get(WARNINGS), RULE_ID);
        assertThat(warning.get("severity").asText()).isEqualTo("WARNING");
        assertThat(warning.get("validationLevel").asText()).isEqualTo("OFFENCE");
        assertThat(warning.get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(EXPECTED_MESSAGE);
    }

    /**
     * AC2: a satisfying result (e.g. LEP) on an endorsable offence suppresses the warning.
     */
    @Test
    void endorsableOffence_withSatisfyingCode_shouldNotEmitWarning() throws Exception {
        final JsonNode json = postValidate(buildRequest(ENDORSABLE_CODE, IMP_CODE, "LEP"));

        assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID);
    }

    /**
     * AC3: an excluded final result (e.g. WDRN) as the only Category-F result suppresses the
     * warning because no non-excluded final result is present.
     */
    @Test
    void endorsableOffence_excludedFinalResultOnly_shouldNotEmitWarning() throws Exception {
        final JsonNode json = postValidate(buildRequest(ENDORSABLE_CODE, "WDRN"));

        assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID);
    }

    /**
     * AC4: non-endorsable offence (endorsableFlag=false) with a final result emits no warning.
     */
    @Test
    void nonEndorsableOffence_finalResult_shouldNotEmitWarning() throws Exception {
        final JsonNode json = postValidate(buildRequest(NON_ENDORSABLE_CODE, IMP_CODE));

        assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID);
    }

    /**
     * AC5: adding a satisfying result (DDO) to an endorsable offence clears the warning.
     * Semantically equivalent to AC2 on a stateless service; retained for Jira AC traceability.
     */
    @Test
    void endorsableOffence_satisfyingResultAdded_shouldClearWarning() throws Exception {
        final JsonNode json = postValidate(buildRequest(ENDORSABLE_CODE, IMP_CODE, "DDO"));

        assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID);
    }

    /**
     * AC6: when a hearing contains both an endorsable and a non-endorsable offence, the warning
     * is raised only for the endorsable one.
     */
    @Test
    void mixedOffences_shouldWarnOnlyForEndorsable() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-10-08",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "IMP label",
                     "defendantId": "d1", "offenceId": "live-endorsable-flag1", "category": "F"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "IMP label",
                     "defendantId": "d1", "offenceId": "live-endorsable-flag0", "category": "F"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Test", "lastName": "Person"}],
                  "offences": [
                    {"offenceId": "live-endorsable-flag1", "defendantId": "d1",
                     "offenceCode": "live-endorsable-flag1", "offenceTitle": "Endorsable offence",
                     "orderIndex": 1, "isConvicted": true},
                    {"offenceId": "live-endorsable-flag0", "defendantId": "d1",
                     "offenceCode": "live-endorsable-flag0", "offenceTitle": "Non-endorsable offence",
                     "orderIndex": 2, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
        final JsonNode warning = findByRuleId(json.get(WARNINGS), RULE_ID);
        assertThat(warning.get("affectedOffences")).hasSize(1);
        assertThat(warning.get("affectedOffences").get(0).get("offenceId").asText())
                .isEqualTo(ENDORSABLE_CODE);
    }

    /**
     * AC7: DR-END-012 coexists with other rule warnings in the same response. d1's endorsable
     * offence with a non-concurrent IMP triggers DR-END-012; d2's two concurrent-only IMP results
     * (no primary sentence) trigger DR-SENT-001.
     */
    @Test
    void endorsableWarningAndDrSent001_shouldBothBePresent() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-10-08",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "IMP label",
                     "defendantId": "d1", "offenceId": "live-endorsable-flag1", "category": "F"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "IMP label",
                     "defendantId": "d2", "offenceId": "d2-off1",
                     "isConcurrent": true, "category": "F"},
                    {"resultLineId": "rl3", "shortCode": "IMP", "label": "IMP label",
                     "defendantId": "d2", "offenceId": "d2-off2",
                     "isConcurrent": true, "category": "F"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Test", "lastName": "PersonOne"},
                    {"defendantId": "d2", "firstName": "Test", "lastName": "PersonTwo"}
                  ],
                  "offences": [
                    {"offenceId": "live-endorsable-flag1", "defendantId": "d1",
                     "offenceCode": "live-endorsable-flag1", "offenceTitle": "Endorsable offence",
                     "orderIndex": 1},
                    {"offenceId": "d2-off1", "defendantId": "d2",
                     "offenceCode": "live-endorsable-flag0", "offenceTitle": "Other offence",
                     "orderIndex": 2},
                    {"offenceId": "d2-off2", "defendantId": "d2",
                     "offenceCode": "live-endorsable-flag0", "offenceTitle": "Other offence",
                     "orderIndex": 3}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains("DR-SENT-001");
    }

    private static String buildRequest(final String offenceCode, final String... shortCodes) {
        final StringBuilder lines = new StringBuilder();
        for (int i = 0; i < shortCodes.length; i++) {
            if (i > 0) {
                lines.append(",\n");
            }
            lines.append("""
                    {"resultLineId":"rl%d","shortCode":"%s","label":"%s label",\
                    "defendantId":"d1","offenceId":"%s","category":"F"}""".formatted(
                    i + 1, shortCodes[i], shortCodes[i], offenceCode));
        }
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-10-08",
                  "courtType": "MAGISTRATES",
                  "resultLines": [%s],
                  "defendants": [{"defendantId": "d1", "firstName": "Test", "lastName": "Person"}],
                  "offences": [
                    {"offenceId": "%s", "defendantId": "d1", "offenceCode": "%s",
                     "offenceTitle": "Test offence", "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """.formatted(lines, offenceCode, offenceCode);
    }

    private List<String> ruleIdsOf(final JsonNode issues) {
        final List<String> ids = new ArrayList<>();
        issues.forEach(n -> ids.add(n.get(RULE_ID_FIELD).asText()));
        return ids;
    }

    private JsonNode findByRuleId(final JsonNode issues, final String ruleId) {
        JsonNode found = null;
        for (final JsonNode issue : issues) {
            if (found == null && ruleId.equals(issue.get(RULE_ID_FIELD).asText())) {
                found = issue;
            }
        }
        assertThat(found).as("Expected to find an issue with ruleId=%s", ruleId).isNotNull();
        return found;
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
