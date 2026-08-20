package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Live HTTP coverage for DR-CONV-006 (no-conviction-on-sentenced-offence warning) against a
 * running service instance.
 *
 * <p>DR-CONV-006 ships <b>disabled</b> by its Flyway seed migration
 * ({@code V1.007__insert_dr_conv_006.sql} — DD-43039). Per-rule live test classes must not
 * toggle rule state (see design_rules.md, "Test the framework once" — the rule-update write
 * path is exercised exactly once, by {@code ValidationRulesApiHttpLiveTest}), so this suite
 * never PATCHes the rule. Instead {@link #assumeRuleEnabled()} reads the current live state
 * before each test and skips the warning-producing scenarios when the rule is off — the
 * assertions still cover DR-CONV-006's actual logic in any environment where it has been
 * enabled, without this suite mutating shared state itself.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>US1 AC1 — a non-excluded final result with no recorded conviction produces a warning</li>
 *   <li>Bypass — an excluded final short code (wdrn) suppresses the warning</li>
 *   <li>Bypass — no final result yet (only an interim result) suppresses the warning</li>
 *   <li>US2 AC1A/AC1B — a convicted offence suppresses the warning despite a qualifying
 *       final result</li>
 *   <li>Multi-offence scoping — the warning is scoped to the breaching offence only</li>
 * </ul>
 */
class NoConvictionWarningApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-CONV-006";
    private static final String AFFECTED_OFFENCES = "affectedOffences";

    private static final String EXPECTED_MESSAGE =
            "No conviction has been added against the offence. Check whether you need to add a "
                    + "guilty plea or verdict";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * DR-CONV-006 ships disabled (V1.007 seed). This suite reads (never writes) the live enabled
     * flag and skips every scenario when the rule is off, rather than PATCHing it — per-rule live
     * tests must not toggle rule state (design_rules.md, "Test the framework once").
     */
    @BeforeEach
    void assumeRuleEnabled() throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.set("CJSCPPUID", "test-user");

        final ResponseEntity<String> response = http.exchange(
                baseUrl + "/api/validation/rules/" + RULE_ID,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        final boolean enabled = mapper.readTree(response.getBody()).get("enabled").asBoolean();
        assumeTrue(enabled, RULE_ID + " is disabled in this environment (ships disabled by the "
                + "V1.007 seed migration); skipping rather than toggling it from this suite.");
    }

    /**
     * Covers US1 AC1: a non-excluded final result recorded against an offence with no guilty
     * plea, finding of guilt, or recorded conviction date must produce a single non-blocking
     * warning.
     */
    @Test
    void final_non_excluded_result_with_no_conviction_should_produce_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-31",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "FO", "category": "F",
                     "label": "Fine", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "isConvicted": false}
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
     * Covers the excluded-short-code bypass: a final result whose short code is in the excluded
     * set (wdrn — withdrawn) must not produce a warning even with no recorded conviction.
     */
    @Test
    void excluded_final_short_code_should_suppress_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h2",
                  "hearingDay": "2026-07-31",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "wdrn", "category": "F",
                     "label": "Withdrawn", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "isConvicted": false}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers the no-final-result bypass: an interim result (no {@code category=F} line yet)
     * must not produce a warning.
     */
    @Test
    void no_final_result_yet_should_produce_no_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h3",
                  "hearingDay": "2026-07-31",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "PLEA", "category": "I",
                     "label": "Plea entered", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "isConvicted": false}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers US2 AC1A/AC1B: a convicted offence ({@code isConvicted=true}) must suppress the
     * warning even with a qualifying non-excluded final result and no recorded conviction line.
     */
    @Test
    void convicted_offence_should_suppress_warning_despite_qualifying_final_result()
            throws Exception {
        final String body = """
                {
                  "hearingId": "h4",
                  "hearingDay": "2026-07-31",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "FO", "category": "F",
                     "label": "Fine", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "isConvicted": true}
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
     * when another offence on the same hearing has an excluded final result.
     */
    @Test
    void warning_scoped_to_breaching_offence_only() throws Exception {
        final String body = """
                {
                  "hearingId": "h5",
                  "hearingDay": "2026-07-31",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "FO", "category": "F",
                     "label": "Fine", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "wdrn", "category": "F",
                     "label": "Withdrawn", "defendantId": "d1", "offenceId": "off2"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft A",
                     "orderIndex": 1, "isConvicted": false},
                    {"offenceId": "off2", "offenceCode": "TH68001", "offenceTitle": "Theft B",
                     "orderIndex": 2, "isConvicted": false}
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
