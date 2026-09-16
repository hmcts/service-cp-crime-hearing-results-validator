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
 * Live HTTP coverage for DR-APP-009 (application result recorded against an offence) against a
 * running service instance. Mirrors representative scenarios from
 * {@code ApplicationResultOffenceRuleIT} but exercises the real docker-compose stack (real
 * Postgres, real Flyway-applied {@code V1.010} seed) rather than TestContainers. No JDBC
 * enable/disable dance is needed -- {@code DR-APP-009} ships {@code enabled=true}, same as
 * {@code DR-AGE-007}/{@code DR-SEX-008}.
 */
class ApplicationResultOffenceApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-APP-009";
    private static final String RULE_ID_FIELD = "ruleId";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Covers graceful degradation: a non-breaching result line must not raise DR-APP-009 and
     * must not stop a different rule (DR-SENT-001) from evaluating on the same payload.
     */
    @Test
    void nonBreachingResultLine_shouldNotRaiseErrorAndOtherRulesStillEvaluate() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off2", "isConcurrent": true,
                     "consecutiveToOffence": "off3"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(ruleIdsOf(json.get(ERRORS).get(VALIDATION_ISSUES))).doesNotContain(RULE_ID);
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains("DR-SENT-001");
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
    }

    /**
     * Covers User Story 1: a single application-only result against an offence, single
     * defendant, blocks sharing with the exact required text and no "This affects" clause.
     */
    @Test
    void applicationOnlyResultAgainstOffence_singleDefendant_shouldRaiseBlockingError() throws Exception {
        final JsonNode json = postValidate(singleDefendantSingleBreachRequest("LAWD", "Legal Aid Withdrawn"));

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get(RULE_ID_FIELD).asText()).isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("severity").asText()).isEqualTo("ERROR");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("validationLevel").asText())
                .isEqualTo("OFFENCE");
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText()).isEqualToIgnoringWhitespace(
                "Legal Aid Withdrawn is an application result. It cannot be added to an offence. "
                        + "Remove it from the offence and add an application to the hearing.");
    }

    /**
     * Covers User Story 1's multi-defendant "This affects" behaviour.
     */
    @Test
    void applicationOnlyResultAgainstOffence_multipleDefendants_shouldNameAffectedDefendant() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d2", "offenceId": "off2"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"},
                    {"defendantId": "d2", "masterDefendantId": "d2", "firstName": "Alex", "lastName": "Jones"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText()).isEqualToIgnoringWhitespace(
                "Legal Aid Withdrawn is an application result. It cannot be added to an offence. "
                        + "Remove it from the offence and add an application to the hearing. "
                        + "This affects: Jamie Smith.");
    }

    /**
     * Covers User Story 2: two different application-only results on the same offence each
     * raise their own inline error and their own page-level message.
     */
    @Test
    void multipleApplicationOnlyResultsOnSameOffence_shouldRaiseSeparateInlineErrorPerResult() throws Exception {
        final JsonNode json = postValidate(twoBreachesOnSameOffenceRequest());

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(2);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(2);
    }

    /**
     * Covers the research.md R8 dedup fix end-to-end over live HTTP: the same defendant, same
     * breaching label, on two different offences must be named exactly once in the aggregated
     * page-level "This affects" text.
     */
    @Test
    void sameApplicationOnlyCodeAcrossTwoOffencesSameDefendant_shouldNameDefendantOnceInAggregatedMessage()
            throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                     "defendantId": "d1", "offenceId": "off2"},
                    {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d2", "offenceId": "off3"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"},
                    {"defendantId": "d2", "masterDefendantId": "d2", "firstName": "Alex", "lastName": "Jones"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2},
                    {"offenceId": "off3", "offenceCode": "BU001", "offenceTitle": "Burglary", "orderIndex": 3}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(2);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText()).isEqualToIgnoringWhitespace(
                "Legal Aid Withdrawn is an application result. It cannot be added to an offence. "
                        + "Remove it from the offence and add an application to the hearing. "
                        + "This affects: Jamie Smith.");
    }

    /**
     * Covers User Story 3: the amendment path raises the identical error, since this service
     * does not special-case an amendment request differently from an original share.
     */
    @Test
    void amendmentRequestWithApplicationOnlyResultAgainstOffence_shouldRaiseSameBlockingError() throws Exception {
        final JsonNode json = postValidate(singleDefendantSingleBreachRequest("RFSD", "Application refused"));

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get(RULE_ID_FIELD).asText()).isEqualTo(RULE_ID);
    }

    /**
     * Covers User Story 1's remediation path: removing the breaching result clears the error.
     */
    @Test
    void removingTheBreachingResult_shouldClearThePreviouslyRaisedError() throws Exception {
        final JsonNode firstResponse = postValidate(singleDefendantSingleBreachRequest("LAWD", "Legal Aid Withdrawn"));
        assertThat(firstResponse.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);

        final String correctedBody = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [],
                  "defendants": [
                    {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode secondResponse = postValidate(correctedBody);

        assertThat(ruleIdsOf(secondResponse.get(ERRORS).get(VALIDATION_ISSUES))).doesNotContain(RULE_ID);
    }

    private static String singleDefendantSingleBreachRequest(final String shortCode, final String label) {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "label": "%s",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """.formatted(shortCode, label);
    }

    private static String twoBreachesOnSameOffenceRequest() {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "RFSD", "label": "Application refused",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;
    }

    private List<String> ruleIdsOf(final JsonNode issues) {
        final List<String> ids = new ArrayList<>();
        issues.forEach(n -> ids.add(n.get(RULE_ID_FIELD).asText()));
        return ids;
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
