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
 * Live HTTP coverage for DR-DISQ-013 (drug driving course-reduction disqualification error)
 * against a running service instance.
 *
 * <p>DR-DISQ-013 defaults to enabled by its Flyway seed migration and nothing else in this suite
 * disables it, so no rule-state setup is needed here.
 */
class DrugDrivingCourseReductionApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String WARNINGS = "warnings";
    private static final String RULE_ID = "DR-DISQ-013";
    private static final String DRUG_DRIVING_CODE = "RT88584";
    private static final String DDRCO_CODE = "DDRCO";
    private static final String AFFECTED_OFFENCES = "affectedOffences";
    private static final String SEVERITY = "severity";
    private static final String ERROR_SEVERITY = "ERROR";
    private static final String OFFENCE_ID_FIELD = "offenceId";
    private static final String SINGLE_OFFENCE_ID = "off1";

    private static final String ERROR_MESSAGES = "errorMessages";

    private static final String EXPECTED_MESSAGE =
            "You cannot add a disqualification with a reduction for attending a course "
                    + "to a drug driving offence. Remove this result and add the correct "
                    + "disqualification.";

    private static final String EXPECTED_MESSAGE_WITH_DEFENDANT =
            "You cannot add a disqualification with a reduction for attending a course "
                    + "to a drug driving offence. Remove this result and add the correct "
                    + "disqualification. This affects: Alex Driver.";

    private static final String EXPECTED_MESSAGE_WITH_TWO_DEFENDANTS =
            "You cannot add a disqualification with a reduction for attending a course "
                    + "to a drug driving offence. Remove this result and add the correct "
                    + "disqualification. This affects: Alex Driver and Sam Smith.";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * AC1: DDRCO on a drug driving offence (RT88584) must produce a single ERROR from DR-DISQ-013.
     */
    @Test
    void ac1_rt88584_with_ddrco_should_return_error() throws Exception {
        final JsonNode json = postValidate(buildBody("h-api-ac1-ddrco", DRUG_DRIVING_CODE, DDRCO_CODE));

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        final List<JsonNode> issues = findIssuesByRuleId(json, RULE_ID);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get(SEVERITY).asText()).isEqualTo(ERROR_SEVERITY);
        assertThat(issues.get(0).get(AFFECTED_OFFENCES).get(0).get(OFFENCE_ID_FIELD).asText())
                .isEqualTo(SINGLE_OFFENCE_ID);
    }

    /**
     * AC1: DDRCOT triggers the same DR-DISQ-013 ERROR as DDRCO.
     */
    @Test
    void ac1_ddrcot_triggers_same_error() throws Exception {
        final JsonNode json = postValidate(buildBody("h-api-ac1-ddrcot", DRUG_DRIVING_CODE, "DDRCOT"));

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        final List<JsonNode> issues = findIssuesByRuleId(json, RULE_ID);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get(SEVERITY).asText()).isEqualTo(ERROR_SEVERITY);
    }

    /**
     * AC2: The error message text must match the prescribed wording exactly.
     */
    @Test
    void ac2_error_message_text_should_match_exactly() throws Exception {
        final JsonNode json = postValidate(buildBody("h-api-ac2-msg", DRUG_DRIVING_CODE, DDRCO_CODE));

        final List<JsonNode> issues = findIssuesByRuleId(json, RULE_ID);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get(AFFECTED_OFFENCES).get(0).get("message").asText())
                .isEqualTo(EXPECTED_MESSAGE);
    }

    /**
     * AC3: The response errors section is non-empty when the rule fires, blocking sharing.
     */
    @Test
    void ac3_response_errors_section_is_non_empty() throws Exception {
        final JsonNode json = postValidate(buildBody("h-api-ac3-sev", DRUG_DRIVING_CODE, DDRCO_CODE));

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isNotEmpty();
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).size()).isEqualTo(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText()).isEqualTo(EXPECTED_MESSAGE);
    }

    /**
     * AC5: Two drug driving offences each with DDRCO must produce two separate DR-DISQ-013 errors.
     */
    @Test
    void ac5_two_triggering_offences_produce_two_errors() throws Exception {
        final String body = """
                {
                  "hearingId": "h-api-ac5-two",
                  "hearingDay": "2026-10-16",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                     "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "DDRCO", "category": "F",
                     "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off2"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                     "offenceTitle": "Drug driving s.5A", "orderIndex": 1, "isConvicted": true},
                    {"offenceId": "off2", "defendantId": "d1", "offenceCode": "RT88586",
                     "offenceTitle": "Drug driving attempt", "orderIndex": 2, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);
        final List<JsonNode> issues = findIssuesByRuleId(json, RULE_ID);

        assertThat(issues).hasSize(2);
        final List<String> offenceIds = new ArrayList<>();
        issues.forEach(n -> offenceIds.add(
                n.get(AFFECTED_OFFENCES).get(0).get(OFFENCE_ID_FIELD).asText()));
        assertThat(offenceIds).containsExactlyInAnyOrder("off1", "off2");
        // Single defendant in the hearing — "This affects" clause is stripped from the summary.
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).size()).isEqualTo(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText()).isEqualTo(EXPECTED_MESSAGE);
    }

    /**
     * AC6: DDRCO on a non-drug-driving offence must not produce a DR-DISQ-013 error.
     */
    @Test
    void ac6_ddrco_on_non_triggering_offence_should_not_produce_error() throws Exception {
        final JsonNode json = postValidate(buildBody("h-api-ac6-non-trig", "RT88026", DDRCO_CODE));

        assertThat(findIssuesByRuleId(json, RULE_ID)).isEmpty();
    }

    /**
     * AC6: DDO on a drug driving offence must not produce a DR-DISQ-013 error or any warnings.
     */
    @Test
    void ac6_ddo_on_triggering_offence_should_not_produce_error() throws Exception {
        final JsonNode json = postValidate(buildBody("h-api-ac6-ddo", DRUG_DRIVING_CODE, "DDO"));

        assertThat(findIssuesByRuleId(json, RULE_ID)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * AC7: The same ERROR is produced on the amendment path (re-share after edit).
     */
    @Test
    void ac7_amendment_with_ddrco_produces_error() throws Exception {
        final JsonNode json = postValidate(buildBody("h-api-ac7-amend", DRUG_DRIVING_CODE, DDRCO_CODE));

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        final List<JsonNode> issues = findIssuesByRuleId(json, RULE_ID);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get(SEVERITY).asText()).isEqualTo(ERROR_SEVERITY);
        assertThat(issues.get(0).get(AFFECTED_OFFENCES).get(0).get(OFFENCE_ID_FIELD).asText())
                .isEqualTo(SINGLE_OFFENCE_ID);
        assertThat(issues.get(0).get(AFFECTED_OFFENCES).get(0).get("message").asText())
                .isEqualTo(EXPECTED_MESSAGE);
    }

    /**
     * EC: Two defendants — d1 triggers error (RT88584+DDRCO), d2 does not (RT88340+DDO).
     * Spec edge case: separate ERROR per affected offence; unaffected defendant gets none.
     */
    @Test
    void ec_two_defendants_only_triggering_defendant_gets_error() throws Exception {
        final String body = """
                {
                  "hearingId": "h-api-ec-md",
                  "hearingDay": "2026-10-16",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                     "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "DDO", "category": "F",
                     "label": "Driving Disqualification", "defendantId": "d2", "offenceId": "off2"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"},
                    {"defendantId": "d2", "firstName": "Sam", "lastName": "Smith"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                     "offenceTitle": "Drug driving s.5A", "orderIndex": 1, "isConvicted": true},
                    {"offenceId": "off2", "defendantId": "d2", "offenceCode": "RT88340",
                     "offenceTitle": "Drive unfit through drugs", "orderIndex": 2, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);
        final List<JsonNode> issues = findIssuesByRuleId(json, RULE_ID);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get(AFFECTED_OFFENCES).get(0).get(OFFENCE_ID_FIELD).asText())
                .isEqualTo(SINGLE_OFFENCE_ID);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).size()).isEqualTo(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualTo(EXPECTED_MESSAGE_WITH_DEFENDANT);
    }

    /**
     * MD2: Both defendants trigger (d1 DDRCO on RT88584, d2 DDRCOT on RT88340) — two separate
     * errors, one summary listing both names.
     */
    @Test
    void ec_both_defendants_trigger_should_produce_two_errors_and_name_both_in_summary()
            throws Exception {
        final String body = """
                {
                  "hearingId": "h-api-ec-md2",
                  "hearingDay": "2026-10-16",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                     "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "DDRCOT", "category": "F",
                     "label": "Disq course reduction", "defendantId": "d2", "offenceId": "off2"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"},
                    {"defendantId": "d2", "firstName": "Sam", "lastName": "Smith"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                     "offenceTitle": "Drug driving s.5A", "orderIndex": 1, "isConvicted": true},
                    {"offenceId": "off2", "defendantId": "d2", "offenceCode": "RT88340",
                     "offenceTitle": "Drive unfit through drugs", "orderIndex": 2, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);
        final List<JsonNode> issues = findIssuesByRuleId(json, RULE_ID);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(issues).hasSize(2);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).size()).isEqualTo(1);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .isEqualTo(EXPECTED_MESSAGE_WITH_TWO_DEFENDANTS);
    }

    /**
     * AC4/Resolving: Triggering offence (RT88584) with no result lines must not produce a
     * DR-DISQ-013 error.
     */
    @Test
    void ac4_triggering_offence_with_no_results_should_not_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "h-api-ac4-norl",
                  "hearingDay": "2026-10-16",
                  "courtType": "MAGISTRATES",
                  "resultLines": [],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                     "offenceTitle": "Drug driving s.5A", "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);
        assertThat(findIssuesByRuleId(json, RULE_ID)).isEmpty();
    }

    /**
     * AC6: Non-triggering offence with DDRCO and triggering offence with DDO in the same
     * hearing must not produce a DR-DISQ-013 error.
     */
    @Test
    void ac6_mixed_hearing_non_triggering_with_ddrco_and_triggering_with_ddo_should_not_produce_error()
            throws Exception {
        final String body = """
                {
                  "hearingId": "h-api-ac6-mixed",
                  "hearingDay": "2026-10-16",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                     "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "DDO", "category": "F",
                     "label": "Driving Disqualification", "defendantId": "d1", "offenceId": "off2"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88026",
                     "offenceTitle": "Dangerous driving", "orderIndex": 1, "isConvicted": true},
                    {"offenceId": "off2", "defendantId": "d1", "offenceCode": "RT88584",
                     "offenceTitle": "Drug driving s.5A", "orderIndex": 2, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);
        assertThat(findIssuesByRuleId(json, RULE_ID)).isEmpty();
    }

    /**
     * US3-AC3: A hearing where no offence is a drug driving offence must not produce any
     * DR-DISQ-013 errors.
     */
    @Test
    void ac6_hearing_with_no_drug_driving_offences_should_not_produce_error() throws Exception {
        final JsonNode json = postValidate(buildBody("h-api-ac6-nodrug", "RT88026", "DDO"));
        assertThat(findIssuesByRuleId(json, RULE_ID)).isEmpty();
    }

    /**
     * EC: DDRCO alongside another result on the same triggering offence — rule fires regardless
     * of other results present (spec edge case).
     */
    @Test
    void ec_ddrco_alongside_ddo_on_same_offence_still_produces_error() throws Exception {
        final String body = """
                {
                  "hearingId": "h-api-ec-mixed",
                  "hearingDay": "2026-10-16",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "DDRCO", "category": "F",
                     "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "DDO", "category": "F",
                     "label": "Driving Disqualification", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"offenceId": "off1", "defendantId": "d1", "offenceCode": "RT88584",
                     "offenceTitle": "Drug driving s.5A", "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);
        final List<JsonNode> issues = findIssuesByRuleId(json, RULE_ID);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get(SEVERITY).asText()).isEqualTo(ERROR_SEVERITY);
    }

    private String buildBody(final String hearingId, final String offenceCode,
                              final String shortCode) {
        return """
                {
                  "hearingId": "%s",
                  "hearingDay": "2026-10-16",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "category": "F",
                     "label": "Disq course reduction", "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Driver"}],
                  "offences": [
                    {"offenceId": "off1", "defendantId": "d1", "offenceCode": "%s",
                     "offenceTitle": "Drug driving offence", "orderIndex": 1,
                     "isConvicted": true}
                  ]
                }
                """.formatted(hearingId, shortCode, offenceCode);
    }

    private List<JsonNode> findIssuesByRuleId(final JsonNode json, final String ruleId) {
        final List<JsonNode> issues = new ArrayList<>();
        json.get(ERRORS).get(VALIDATION_ISSUES).forEach(n -> {
            if (ruleId.equals(n.get("ruleId").asText())) {
                issues.add(n);
            }
        });
        return issues;
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
