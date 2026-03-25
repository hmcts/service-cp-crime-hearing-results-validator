package uk.gov.hmcts.cp.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live HTTP coverage for the rule metadata endpoints against a running service instance.
 */
class ValidationRulesApiHttpLiveTest {

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Verifies the rule-list endpoint returns the discovered rules and summary counts.
     */
    @Test
    void list_rules_should_return_ok_with_rules() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set("CJSCPPUID", "test-user");

        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/validation/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        final JsonNode json = mapper.readTree(response.getBody());
        assertThat(json.get("count").asInt()).isEqualTo(1);
        assertThat(json.get("enabledCount").asInt()).isEqualTo(1);
        assertThat(json.get("rules")).hasSize(1);
        assertThat(json.get("rules").get(0).get("ruleId").asText()).isEqualTo("DR-SENT-002");
    }

    /**
     * Verifies the rule-detail endpoint returns metadata for a known rule id.
     */
    @Test
    void get_rule_by_id_should_return_ok_with_rule_detail() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set("CJSCPPUID", "test-user");

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
     * Verifies an unknown rule id is surfaced as an HTTP 404 response.
     */
    @Test
    void get_rule_by_id_should_return_404_for_unknown_rule() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set("CJSCPPUID", "test-user");

        assertThatThrownBy(() -> http.exchange(
                baseUrl + "/api/validation/rules/UNKNOWN-RULE",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        )).isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
