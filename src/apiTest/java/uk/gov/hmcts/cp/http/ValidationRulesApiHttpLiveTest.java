package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Live HTTP coverage for the rule metadata endpoints against a running service instance.
 *
 * <p>Every rule ships enabled by its Flyway seed migration, so these assertions run against that
 * steady state with no rule-state setup. The single exception is the PATCH round-trip test below,
 * which temporarily disables DR-SENT-001 and restores it within the same test — the only place
 * the live suite exercises the rule-update write path (per-rule live tests must not toggle rule
 * state; see design_rules.md, "Test the framework once"). The enabled/disabled count math itself
 * is covered by {@code DefaultValidationRulesServiceTest.listRules_should_return_all_rules()}.
 */
class ValidationRulesApiHttpLiveTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationRulesApiHttpLiveTest.class);

    private static final String CJSCPPUID = "CJSCPPUID";
    private static final String TEST_USER = "test-user";
    private static final String TOGGLED_RULE_ID = "DR-SENT-001";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate(new JdkClientHttpRequestFactory());
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Verifies the rule-list endpoint returns the discovered rules and summary counts.
     */
    @Test
    void list_rules_should_return_ok_with_rules() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID, TEST_USER);

        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/validation/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        final JsonNode json = mapper.readTree(response.getBody());
        assertThat(json.get("count").asInt()).isEqualTo(7);
        // All 7 rules ship enabled by their Flyway seed migrations.
        assertThat(json.get("enabledCount").asInt()).isEqualTo(7);
        assertThat(json.get("rules")).hasSize(7);
        final List<String> ruleIds = new ArrayList<>();
        json.get("rules").forEach(r -> ruleIds.add(r.get("ruleId").asText()));
        assertThat(ruleIds).containsExactlyInAnyOrder(
                "DR-SENT-001", "DR-DISQ-002", "DR-CTL-003", "DR-YRO-004", "DR-COEW-005", "DR-CONV-006",
                "DR-AGE-007");
    }

    /**
     * Verifies the rule-detail endpoint returns metadata for a known rule id.
     */
    @Test
    void get_rule_by_id_should_return_ok_with_rule_detail() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID, TEST_USER);

        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/validation/rules/DR-SENT-001",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        final JsonNode json = mapper.readTree(response.getBody());
        assertThat(json.get("ruleId").asText()).isEqualTo("DR-SENT-001");
        assertThat(json.get("enabled").asBoolean()).isTrue();
        assertThat(json.get("title").asText()).isNotBlank();
    }

    /**
     * Verifies an unknown rule id is surfaced as an HTTP 404 response with a structured error body.
     */
    @Test
    void get_rule_by_id_should_return_not_found_for_unknown_rule() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID, TEST_USER);

        final HttpClientErrorException.NotFound exception = catchThrowableOfType(
                () -> http.exchange(
                        baseUrl + "/api/validation/rules/UNKNOWN-RULE",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                ),
                HttpClientErrorException.NotFound.class
        );

        assertThat(exception).isNotNull();
        final JsonNode json = mapper.readTree(exception.getResponseBodyAsString());
        assertThat(json.get("error").asText()).isEqualTo("Rule not found");
        assertThat(json.get("message").asText()).contains("UNKNOWN-RULE");
        assertThat(json.get("traceId").asText()).isNotBlank();
        assertThat(json.get("timestamp").asText()).isNotBlank();
    }

    /**
     * Verifies the rule-update write path over live HTTP: PATCHing {@code enabled=false} takes
     * effect in the list summary, and PATCHing {@code enabled=true} restores the seeded steady
     * state. The restore runs in a finally block so the shared database is left in the
     * all-rules-enabled state every other test in this suite relies on, even if an assertion
     * fails mid-test. Counts are asserted relative to the pre-test baseline so this test does
     * not need touching when a new rule ships — {@code list_rules_should_return_ok_with_rules}
     * remains the single place asserting the absolute rule counts.
     */
    @Test
    void update_rule_should_toggle_enabled_state_and_restore_steady_state() throws Exception {
        final int baseline = enabledCount();
        try {
            final JsonNode disabled = patchRuleEnabled(TOGGLED_RULE_ID, false);
            assertThat(disabled.get("ruleId").asText()).isEqualTo(TOGGLED_RULE_ID);
            assertThat(disabled.get("enabled").asBoolean()).isFalse();
            assertThat(enabledCount()).isEqualTo(baseline - 1);
        } finally {
            restoreToggledRule();
        }
        assertThat(enabledCount()).isEqualTo(baseline);
    }

    /**
     * Restores the toggled rule without throwing, so a restore failure cannot mask the original
     * test failure. A silently failed restore is still caught by the post-finally
     * {@code enabledCount()} assertion.
     */
    private void restoreToggledRule() {
        try {
            patchRuleEnabled(TOGGLED_RULE_ID, true);
        } catch (final JsonProcessingException | RestClientException e) {
            LOGGER.error("Failed to restore {} to enabled=true; later tests may see a disabled rule",
                    TOGGLED_RULE_ID, e);
        }
    }

    private JsonNode patchRuleEnabled(final String ruleId, final boolean enabled)
            throws JsonProcessingException {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID, TEST_USER);
        headers.setContentType(MediaType.APPLICATION_JSON);

        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/validation/rules/" + ruleId,
                HttpMethod.PATCH,
                new HttpEntity<>("{\"enabled\": " + enabled + "}", headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody());
    }

    private int enabledCount() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID, TEST_USER);

        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/validation/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody()).get("enabledCount").asInt();
    }
}
