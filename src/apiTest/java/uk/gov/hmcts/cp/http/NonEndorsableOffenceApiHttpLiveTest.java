package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Live HTTP coverage for DR-END-014 (endorsement or disqualification result on non-endorsable
 * offence) against a running service instance. DR-END-014 has two independent CEL conditions:
 * AC1 (endorsement family) and AC2 (disqualification family).
 *
 * <p>The docker-compose WireMock container mounts {@code ./wiremock/mappings/}, which includes
 * {@code referencedataoffences-non-endorsable-stub.json} — the stub used by this class.
 * Non-endorsable offence codes return {@code endorsableFlag: false}; endorsable codes return
 * {@code endorsableFlag: true}.
 *
 * <p>Each test uses a unique {@code offenceCode} (equal to its {@code offenceId}) to avoid
 * cache collisions in the running service's Caffeine cache.
 */
class NonEndorsableOffenceApiHttpLiveTest {

    private static final String WARNINGS = "warnings";
    private static final String RULE_ID_FIELD = "ruleId";
    private static final String RULE_ID_014 = "DR-END-014";
    private static final String ENDORSEMENT_MESSAGE =
            "This offence is not endorsable. Remove the endorsement result.";
    private static final String DISQ_MESSAGE =
            "This offence is not endorsable. Remove the disqualification result, or record a"
                    + " disqualification for non-endorsable offence result instead.";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("DR-END-014 — endorsement trigger")
    class EndorsementTrigger {

        @Test
        void lepOnNonEndorsableOffence_shouldRaiseEndorsementWarning() throws Exception {
            final String body = singleOffenceRequest("live-ne-end-trigger", "LEP");

            final JsonNode json = postValidate(body);

            assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID_014);
            final JsonNode warning = findByRuleId(json.get(WARNINGS), RULE_ID_014);
            assertThat(warning.get("severity").asText()).isEqualTo("WARNING");
            assertThat(warning.get("validationLevel").asText()).isEqualTo("OFFENCE");
            assertThat(warning.get("affectedOffences").get(0).get("message").asText())
                    .isEqualTo(ENDORSEMENT_MESSAGE);
        }

        @Test
        void lepOnEndorsableOffence_shouldNotRaiseEndorsementWarning() throws Exception {
            final String body = singleOffenceRequest("live-ne-endorsable", "LEP");

            final JsonNode json = postValidate(body);

            assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID_014);
        }
    }

    @Nested
    @DisplayName("DR-END-014 — disqualification trigger (AC2)")
    class DisqualificationTrigger {

        @Test
        void ddoOnNonEndorsableOffence_shouldRaiseDisqualificationWarning() throws Exception {
            final String body = singleOffenceRequest("live-ne-disq-trigger", "DDO");

            final JsonNode json = postValidate(body);

            assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID_014);
            final JsonNode warning = findByRuleId(json.get(WARNINGS), RULE_ID_014);
            assertThat(warning.get("severity").asText()).isEqualTo("WARNING");
            assertThat(warning.get("affectedOffences").get(0).get("message").asText())
                    .isEqualTo(DISQ_MESSAGE);
        }
    }

    @Nested
    @DisplayName("DR-END-014 — non-endorsable disqualification family clear (AC3)")
    class NonEndorsableDiqFamilyClear {

        @Test
        void ddrnOnNonEndorsableOffence_shouldNotRaiseAnyWarning() throws Exception {
            final String body = singleOffenceRequest("live-ne-disq-trigger", "DDRN");

            final JsonNode json = postValidate(body);

            assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID_014);
        }
    }

    @Nested
    @DisplayName("DR-END-014 — non-trigger result on non-endorsable offence (AC4)")
    class NonTriggerResult {

        @Test
        void impOnNonEndorsableOffence_shouldNotRaiseAnyWarning() throws Exception {
            final String body = singleOffenceRequest("live-ne-end-trigger", "IMP");

            final JsonNode json = postValidate(body);

            assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID_014);
        }
    }

    @Nested
    @DisplayName("Multiple offences assessed independently (AC5)")
    class MultiOffenceIndependence {

        @Test
        void lepOnNonEndorsableAndEndorsableOffence_shouldWarnOnlyForNonEndorsable() throws Exception {
            final String body = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-10-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "LEP", "label": "LEP label",
                         "defendantId": "d1", "offenceId": "live-ne-end-trigger"},
                        {"resultLineId": "rl2", "shortCode": "LEP", "label": "LEP label",
                         "defendantId": "d1", "offenceId": "live-ne-endorsable"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Test", "lastName": "User"}
                      ],
                      "offences": [
                        {"offenceId": "live-ne-end-trigger", "offenceCode": "live-ne-end-trigger",
                         "offenceTitle": "Non-endorsable offence", "orderIndex": 1, "defendantId": "d1"},
                        {"offenceId": "live-ne-endorsable", "offenceCode": "live-ne-endorsable",
                         "offenceTitle": "Endorsable offence", "orderIndex": 2, "defendantId": "d1"}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            assertThat(ruleIdsOf(json.get(WARNINGS)).stream().filter(RULE_ID_014::equals).count())
                    .as("only the non-endorsable offence should trigger DR-END-014")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Both families on the same offence (US3)")
    class BothFamilies {

        @Test
        void lepAndDdo_bothOnNonEndorsableOffence_shouldRaiseBothWarnings() throws Exception {
            final String body = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-10-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "LEP", "label": "LEP label",
                         "defendantId": "d1", "offenceId": "live-ne-both-off"},
                        {"resultLineId": "rl2", "shortCode": "DDO", "label": "DDO label",
                         "defendantId": "d1", "offenceId": "live-ne-both-off"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Test", "lastName": "User"}
                      ],
                      "offences": [
                        {"offenceId": "live-ne-both-off", "offenceCode": "live-ne-both-off",
                         "offenceTitle": "Non-endorsable offence", "orderIndex": 1, "defendantId": "d1"}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            final List<String> ruleIds = ruleIdsOf(json.get(WARNINGS));
            assertThat(ruleIds.stream().filter(RULE_ID_014::equals).count())
                    .as("both DR-END-014 conditions (AC1 endorsement + AC2 disqualification) should fire")
                    .isEqualTo(2);
        }
    }

    private static String singleOffenceRequest(final String offenceId, final String shortCode) {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-10-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "label": "%s label",
                     "defendantId": "d1", "offenceId": "%s"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Test", "lastName": "User"}
                  ],
                  "offences": [
                    {"offenceId": "%s", "offenceCode": "%s",
                     "offenceTitle": "Test offence", "orderIndex": 1, "defendantId": "d1"}
                  ]
                }
                """.formatted(shortCode, shortCode, offenceId, offenceId, offenceId);
    }

    private List<String> ruleIdsOf(final JsonNode issues) {
        final List<String> ids = new ArrayList<>();
        issues.forEach(n -> ids.add(n.get(RULE_ID_FIELD).asText()));
        return ids;
    }

    private JsonNode findByRuleId(final JsonNode issues, final String ruleId) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(issues.iterator(), 0), false)
                .filter(n -> ruleId.equals(n.get(RULE_ID_FIELD).asText()))
                .findFirst()
                .orElse(null);
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
