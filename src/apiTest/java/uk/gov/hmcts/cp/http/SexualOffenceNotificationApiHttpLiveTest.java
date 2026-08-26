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
 * Live HTTP coverage for DR-SEX-008 (sexual offence notification requirement) against a running
 * service instance. Mirrors {@code SexualOffenceNotificationRuleIT}'s Adult and Youth scenarios
 * but exercises the real docker-compose stack rather than TestContainers.
 *
 * <p>The docker-compose stack's shared WireMock container mounts the whole {@code ./wiremock}
 * directory (see {@code docker-compose.yml}), so {@code wiremock/mappings/
 * referencedataoffences-stub.json} is picked up automatically alongside the existing identity
 * stub — no separate compose wiring was needed. That stub always returns {@code misCode: "SEX"},
 * so the positive scenarios below are expected to observe the real warning, not just the
 * fail-open path.
 */
class SexualOffenceNotificationApiHttpLiveTest {

    private static final String WARNINGS = "warnings";
    private static final String RULE_ID_FIELD = "ruleId";
    private static final String RULE_ID = "DR-SEX-008";
    private static final String ADULT_MESSAGE =
            "This offence does not have a sexual offences notification requirement (NORRR). "
                    + "Check if this is required before sharing";
    private static final String YOUTH_MESSAGE =
            "This offence does not have a sexual offences notification requirement "
                    + "(NORRR - defendant or NORPGP - parent and defendant). Check if this is required before sharing";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Covers User Story 1: an Adult defendant's convicted relevant sexual offence with no
     * {@code NORRR} result raises the exact adult warning.
     */
    @Test
    void convictedSexOffence_adultMissingNorrr_shouldRaiseWarning() throws Exception {
        final String body = adultRequest("live-sex-adult-missing", true, "IMP");

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
        final JsonNode warning = findByRuleId(json.get(WARNINGS), RULE_ID);
        assertThat(warning.get("severity").asText()).isEqualTo("WARNING");
        assertThat(warning.get("validationLevel").asText()).isEqualTo("OFFENCE");
        assertThat(warning.get("affectedOffences").get(0).get("message").asText()).isEqualTo(ADULT_MESSAGE);
    }

    /**
     * Covers User Story 1's clearing path: a {@code NORRR} result on the same offence means no
     * warning, regardless of whether the reference-data classification is available.
     */
    @Test
    void convictedSexOffence_adultWithNorrr_shouldNotRaiseWarning() throws Exception {
        final String body = adultRequest("live-sex-adult-clear", true, "NORRR");

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID);
    }

    /**
     * Covers User Story 2: a Youth defendant's convicted relevant sexual offence with neither
     * {@code NORRR} nor {@code NORPGP} raises the exact youth warning.
     */
    @Test
    void convictedSexOffence_youthMissingBothCodes_shouldRaiseYouthWarning() throws Exception {
        final String body = youthRequest("live-sex-youth-missing", "IMP");

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
        final JsonNode warning = findByRuleId(json.get(WARNINGS), RULE_ID);
        assertThat(warning.get("affectedOffences").get(0).get("message").asText()).isEqualTo(YOUTH_MESSAGE);
    }

    /**
     * Covers User Story 2's clearing path: either {@code NORRR} or {@code NORPGP} clears the
     * warning for a Youth defendant.
     */
    @Test
    void convictedSexOffence_youthWithNorrrOnly_shouldNotRaiseWarning() throws Exception {
        final String body = youthRequest("live-sex-youth-norrr", "NORRR");

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID);
    }

    private static String adultRequest(final String offenceId, final boolean convicted, final String shortCode) {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-08-25",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "label": "%s",
                     "defendantId": "d1", "offenceId": "%s"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2000-01-01"}
                  ],
                  "offences": [
                    {"offenceId": "%s", "offenceCode": "SX03007C", "offenceTitle": "Sexual offence",
                     "orderIndex": 1, "isConvicted": %s}
                  ]
                }
                """.formatted(shortCode, shortCode + " label", offenceId, offenceId, convicted);
    }

    private static String youthRequest(final String offenceId, final String shortCode) {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-08-25",
                  "courtType": "YOUTH",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "label": "%s",
                     "defendantId": "d1", "offenceId": "%s"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2012-01-01"}
                  ],
                  "offences": [
                    {"offenceId": "%s", "offenceCode": "SX03007C", "offenceTitle": "Sexual offence",
                     "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """.formatted(shortCode, shortCode + " label", offenceId, offenceId);
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
