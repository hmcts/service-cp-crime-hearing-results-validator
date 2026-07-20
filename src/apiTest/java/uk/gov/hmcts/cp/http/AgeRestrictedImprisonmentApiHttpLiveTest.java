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
 * Live HTTP coverage for DR-AGE-001 (imprisonment result age restriction) against a running
 * service instance. Mirrors {@code AgeRestrictedImprisonmentRuleIT}'s scenarios but exercises the
 * real docker-compose stack (real Postgres, real Flyway-applied {@code validation_rule} seed)
 * rather than TestContainers.
 */
class AgeRestrictedImprisonmentApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-AGE-001";
    private static final String EXPECTED_BASE_MESSAGE =
            "The defendant is under 21 years of age and cannot receive a sentence of imprisonment.";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Covers the graceful-degradation guarantee: a defendant with no recorded date of birth must
     * not raise DR-AGE-001, must not error the request, and must not stop a different rule
     * (DR-SENT-002) from evaluating and reporting on the same payload.
     */
    @Test
    void missingDateOfBirth_shouldNotRaiseErrorAndOtherRulesStillEvaluate() throws Exception {
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
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
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
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains("DR-SENT-002");
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
    }

    /**
     * Covers User Story 1: a defendant whose 21st birthday falls exactly on the hearing date is
     * treated as 21 or over and must not raise DR-AGE-001.
     */
    @Test
    void entersImprisonmentResult_defendantAged21OrOver_shouldNotRaiseError() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2005-07-20"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(ERRORS).get(VALIDATION_ISSUES))).doesNotContain(RULE_ID);
    }

    /**
     * Covers User Story 1 for the EXTIVS and SPECC short codes against defendants well over 21.
     */
    @Test
    void entersExtivsOrSpeccResult_defendantWellOver21_shouldNotRaiseError() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "EXTIVS", "label": "Extended sentence",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "SPECC", "label": "Special custodial",
                     "defendantId": "d2", "offenceId": "off2"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "1990-01-01"},
                    {"defendantId": "d2", "firstName": "Alex", "lastName": "Jones",
                     "dateOfBirth": "1985-06-15"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(ERRORS).get(VALIDATION_ISSUES))).doesNotContain(RULE_ID);
    }

    /**
     * Covers User Story 2: an imprisonment-type result against an under-21 defendant blocks
     * sharing with the exact required error text.
     */
    @Test
    void entersImprisonmentResult_defendantUnder21_shouldRaiseBlockingError() throws Exception {
        final String body = singleUnder21DefendantRequest();

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("ruleId").asText()).isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("severity").asText()).isEqualTo("ERROR");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("validationLevel").asText())
                .isEqualTo("OFFENCE");
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(EXPECTED_BASE_MESSAGE + " This affects: Jamie Smith.");
    }

    /**
     * Covers the same-defendant dedup property: two qualifying offences for one under-21
     * defendant must name that defendant exactly once in the aggregated error.
     */
    @Test
    void multipleQualifyingOffencesSameDefendant_shouldNameDefendantOnce() throws Exception {
        // rl2 carries isConcurrent=true purely so DR-SENT-002's custodial preprocessor (which
        // also treats IMP/EXTIVS as custodial short codes) sees relationship info on both
        // offences and stays silent — this test isolates DR-AGE-001's own dedup behaviour.
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "EXTIVS", "label": "Extended sentence",
                     "defendantId": "d1", "offenceId": "off2", "isConcurrent": true}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2006-08-01"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("ruleId").asText()).isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("affectedOffences")).hasSize(2);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(EXPECTED_BASE_MESSAGE + " This affects: Jamie Smith.");
    }

    /**
     * Covers the mixed-age property: only the under-21 defendant blocks sharing; the 21+
     * defendant's qualifying result does not appear in the affected list.
     */
    @Test
    void mixedAgeDefendants_shouldOnlyNameUnder21Defendant() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d2", "offenceId": "off2"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2006-08-01"},
                    {"defendantId": "d2", "firstName": "Alex", "lastName": "Jones",
                     "dateOfBirth": "1990-01-01"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("ruleId").asText()).isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(EXPECTED_BASE_MESSAGE + " This affects: Jamie Smith.");
    }

    /**
     * Covers the multi-defendant aggregation property: two under-21 defendants each with a
     * qualifying result must both be named in a single aggregated error, not two separate ones.
     */
    @Test
    void multipleUnder21Defendants_shouldNameAllInOneAggregatedError() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "EXTIVS", "label": "Extended sentence",
                     "defendantId": "d2", "offenceId": "off2"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2006-08-01"},
                    {"defendantId": "d2", "firstName": "Alex", "lastName": "Jones",
                     "dateOfBirth": "2007-01-01"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(2);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).hasSize(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualToIgnoringWhitespace(EXPECTED_BASE_MESSAGE + " This affects: Jamie Smith and Alex Jones.");
    }

    /**
     * Covers User Story 3: correcting the defendant's date of birth on a resubmission clears the
     * previously-raised error.
     */
    @Test
    void correctingDateOfBirth_shouldClearPreviouslyRaisedError() throws Exception {
        final JsonNode firstResponse = postValidate(singleUnder21DefendantRequest());
        assertThat(firstResponse.get(IS_VALID).asBoolean()).isFalse();
        assertThat(firstResponse.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);

        final String correctedBody = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2005-07-20"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;

        final JsonNode secondResponse = postValidate(correctedBody);

        assertThat(ruleIdsOf(secondResponse.get(ERRORS).get(VALIDATION_ISSUES))).doesNotContain(RULE_ID);
    }

    private static String singleUnder21DefendantRequest() {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2006-08-01"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;
    }

    private List<String> ruleIdsOf(final JsonNode issues) {
        final List<String> ids = new ArrayList<>();
        issues.forEach(n -> ids.add(n.get("ruleId").asText()));
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
