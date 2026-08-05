package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Live HTTP coverage for the rule metadata endpoints against a running service instance.
 *
 * <p>{@link #restoreDefaultRuleStates()} runs in {@code @BeforeAll} to ensure any rule
 * state left by other test classes does not affect these assertions.
 */
class ValidationRulesApiHttpLiveTest {

    private static final String CJSCPPUID = "CJSCPPUID";

    private static final String DB_URL =
            System.getProperty("db.url", "jdbc:postgresql://localhost:5432/results-validator-db");
    private static final String DB_USER = System.getProperty("db.username", "postgres");
    private static final String DB_PASSWORD = System.getProperty("db.password", "postgres");

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void restoreDefaultRuleStates() throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE validation_rule SET enabled = false WHERE id = 'DR-YRO-001'")) {
            ps.executeUpdate();
        }
        awaitEnabledCount(1);
    }

    private static void awaitEnabledCount(final int expected) throws Exception {
        final RestTemplate client = new RestTemplate();
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID, "test-setup");
        final HttpEntity<Void> request = new HttpEntity<>(headers);
        final ObjectMapper objectMapper = new ObjectMapper();
        final String url = System.getProperty("app.baseUrl", "http://localhost:8082")
                + "/api/validation/rules";
        final long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            final ResponseEntity<String> response = client.exchange(
                    url, HttpMethod.GET, request, String.class);
            final JsonNode json = objectMapper.readTree(response.getBody());
            if (json.get("enabledCount").asInt() == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException(
                "enabledCount did not reach " + expected + " within 5 s");
    }

    /**
     * Verifies the rule-list endpoint returns the discovered rules and summary counts.
     */
    @Test
    void list_rules_should_return_ok_with_rules() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID, "test-user");

        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/validation/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        final JsonNode json = mapper.readTree(response.getBody());
        assertThat(json.get("count").asInt()).isEqualTo(4);
        assertThat(json.get("enabledCount").asInt()).isEqualTo(1);
        assertThat(json.get("rules")).hasSize(4);
        final List<String> ruleIds = new ArrayList<>();
        json.get("rules").forEach(r -> ruleIds.add(r.get("ruleId").asText()));
        assertThat(ruleIds).containsExactlyInAnyOrder(
                "DR-SENT-002", "DR-DISQ-001", "DR-CTL-001", "DR-YRO-001");
    }

    /**
     * Verifies the rule-detail endpoint returns metadata for a known rule id.
     */
    @Test
    void get_rule_by_id_should_return_ok_with_rule_detail() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID, "test-user");

        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/validation/rules/DR-SENT-002",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        final JsonNode json = mapper.readTree(response.getBody());
        assertThat(json.get("ruleId").asText()).isEqualTo("DR-SENT-002");
        assertThat(json.get("enabled").asBoolean()).isTrue();
        assertThat(json.get("title").asText()).isNotBlank();
    }

    /**
     * Verifies an unknown rule id is surfaced as an HTTP 404 response with a structured error body.
     */
    @Test
    void get_rule_by_id_should_return_not_found_for_unknown_rule() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CJSCPPUID, "test-user");

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
}
