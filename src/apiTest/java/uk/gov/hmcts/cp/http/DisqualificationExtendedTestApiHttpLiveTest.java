package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
 * Live HTTP coverage for DR-DISQ-001 (extended-test disqualification warning) against a
 * running service instance.
 */
class DisqualificationExtendedTestApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-DISQ-001";

    /**
     * Wait long enough to outlast the server-side rule-override Caffeine cache
     * ({@code RULE_OVERRIDE_CACHE_TTL=1}s in the api-test stack). Used to guarantee both that a
     * DB change has propagated before asserting, and that the cache no longer holds the toggled
     * value once this test restores shared state — otherwise a stale {@code enabled=true} can leak
     * into other test classes (e.g. the rule-list count assertion).
     */
    private static final long CACHE_TTL_EVICTION_WAIT_MS = 2000L;

    private static final String EXPECTED_MESSAGE =
            "Check whether you need to add extended test disqualification with DDOTE "
                    + "(disqualification and extended test) or DDOTEL (disqualification for "
                    + "life and extended test)";

    private static final String DB_URL =
            System.getProperty("db.url", "jdbc:postgresql://localhost:5432/results-validator-db");
    private static final String DB_USER = System.getProperty("db.username", "postgres");
    private static final String DB_PASSWORD = System.getProperty("db.password", "postgres");

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Covers the scenario where the hearing contains no relevant Road Traffic Act 1988 offences.
     * DR-DISQ-001 must not fire and the response must be valid with no warnings.
     */
    @Test
    void validate_non_relevant_offence_should_return_valid_with_no_disq_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-04-25",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                     "label": "Convicted", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001",
                     "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get("validationId").asText()).startsWith("val-");
        assertThat(json.get("mode").asText()).isEqualTo("advisory");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
    }

    /**
     * Covers the DDOTE suppression path: a relevant offence that carries an extended-test
     * disqualification result must not produce a DR-DISQ-001 warning.
     */
    @Test
    void ac1_relevant_offence_with_ddote_should_suppress_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h3",
                  "hearingDay": "2026-04-25",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                     "label": "Convicted", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "DDOTE", "category": "I",
                     "label": "Disqual extended test", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "RT88026",
                     "offenceTitle": "Dangerous driving", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers the excluded-result suppression path: a relevant offence whose only final result
     * is in the excluded set (wdrn — withdrawn) must not produce a DR-DISQ-001 warning.
     */
    @Test
    void ac1_excluded_result_on_relevant_offence_should_suppress_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h4",
                  "hearingDay": "2026-04-25",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "wdrn", "category": "F",
                     "label": "Withdrawn", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "RT88026",
                     "offenceTitle": "Dangerous driving", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers AC1 where DR-DISQ-001 is enabled at runtime: a relevant Road Traffic Act 1988
     * offence with a non-excluded final result and no DDOTE must produce a single non-blocking
     * warning. The rule is enabled via JDBC for this test and restored to disabled in a
     * finally block; a 2-second sleep ensures the 1-second Caffeine cache TTL has expired
     * before the validate call is made.
     */
    @Test
    void ac1_relevant_offence_without_ddote_should_produce_warning_when_rule_enabled() throws Exception {
        setRuleEnabled(true);
        try {
            Thread.sleep(CACHE_TTL_EVICTION_WAIT_MS);

            final String body = """
                    {
                      "hearingId": "h5",
                      "hearingDay": "2026-04-25",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                         "label": "Convicted", "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "RT88026",
                         "offenceTitle": "Dangerous driving", "orderIndex": 1}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            assertThat(json.get(IS_VALID).asBoolean()).isTrue();
            assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
            assertThat(json.get(WARNINGS)).hasSize(1);
            assertThat(json.get(WARNINGS).get(0).get("ruleId").asText()).isEqualTo(RULE_ID);
            assertThat(json.get(WARNINGS).get(0).get("severity").asText()).isEqualTo("WARNING");
            assertThat(json.get(WARNINGS).get(0).get("affectedOffences")).hasSize(1);
            assertThat(json.get(WARNINGS).get(0).get("affectedOffences").get(0).get("offenceId").asText())
                    .isEqualTo("off1");
            assertThat(json.get(WARNINGS).get(0).get("affectedOffences").get(0).get("message").asText())
                    .isEqualToIgnoringWhitespace(EXPECTED_MESSAGE);
        } finally {
            setRuleEnabled(false);
            // Restore shared state fully: the DB row is reset above, but the app still caches the
            // toggled override for up to the TTL. Wait it out so no stale enabled=true leaks into
            // other test classes (see CACHE_TTL_EVICTION_WAIT_MS).
            Thread.sleep(CACHE_TTL_EVICTION_WAIT_MS);
        }
    }

    private void setRuleEnabled(final boolean enabled) throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE validation_rule SET enabled = ? WHERE id = 'DR-DISQ-001'")) {
            ps.setBoolean(1, enabled);
            ps.executeUpdate();
        }
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
