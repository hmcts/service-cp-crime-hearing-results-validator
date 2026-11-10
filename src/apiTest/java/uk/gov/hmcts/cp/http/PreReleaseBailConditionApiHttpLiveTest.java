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
 * Live HTTP coverage for DR-BAIL-016 (pre-release bail condition warning) against a running
 * service instance. Mirrors key scenarios from {@code PreReleaseBailConditionRuleIT} but exercises
 * the real docker-compose stack (real Postgres with Flyway-applied V1.018 seed) rather than
 * TestContainers.
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals") // warning message constants share a prefix by design
class PreReleaseBailConditionApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-BAIL-016";
    private static final String RULE_ID_FIELD = "ruleId";
    private static final String SEVERITY_FIELD = "severity";
    private static final String VALIDATION_LEVEL_FIELD = "validationLevel";

    private static final String WARNING_A =
            "Do not share until you have checked the remand result. This result releases the "
                    + "defendant from custody. If any bail conditions are pre-release conditions, "
                    + "use 'Remanded in custody with bail direction (RIB)' instead.";

    private static final String WARNING_B =
            "Do not share until you have checked the remand result. This result releases the "
                    + "defendant on bail. If any bail conditions are pre-release conditions, use "
                    + "'Remand in care of Local Authority with bail direction (RILAB)' instead.";

    private static final String WARNING_C =
            "Do not share until you have checked the remand result. This result releases the "
                    + "defendant on bail. If any bail conditions are pre-release conditions, use "
                    + "'Remanded in custody with bail direction (RIB)' for an adult or 'Remand "
                    + "in care of Local Authority with bail direction (RILAB)' for a youth instead.";

    private static final String WARNING_D =
            "Do not share until you have checked the remand result. This result releases the "
                    + "defendant on bail. If any bail conditions are pre-release conditions, use "
                    + "'Sent to Crown Court in custody for trial with bail direction (CCIIB)' instead.";

    private static final String WARNING_E =
            "Do not share until you have checked the remand result. This result releases the "
                    + "defendant on bail. If any bail conditions are pre-release conditions, use "
                    + "'Committed to Crown Court for sentence - in custody with bail direction "
                    + "(CCSIB)' instead.";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * AC1: adult defendant (DOB 1990-01-01) with result RC and trigger condition PASS5 and
     * bailStatus C → Warning A, isValid=true (sharing not blocked), validationLevel=OFFENCE.
     */
    @Test
    void adult_conditionalBail_withTriggerCondition_shouldEmitWarningA() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac1",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PASS5", "label": "Passport",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                  "dateOfBirth": "1990-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "C"}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(ruleIdsOf(json.get(ERRORS).get(VALIDATION_ISSUES))).doesNotContain(RULE_ID);
        final List<String> warningRuleIds = ruleIdsOf(json.get(WARNINGS));
        assertThat(warningRuleIds).contains(RULE_ID);
        final JsonNode bail016Warning = firstWarningForRule(json, RULE_ID);
        assertThat(bail016Warning.get(SEVERITY_FIELD).asText()).isEqualTo("WARNING");
        assertThat(bail016Warning.get(VALIDATION_LEVEL_FIELD).asText()).isEqualTo("OFFENCE");
        assertThat(bail016Warning.get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(WARNING_A);
    }

    /**
     * AC2: youth defendant (DOB 2010-01-01) with result REMCB and PORS and bailStatus S →
     * Warning B, isValid=true.
     */
    @Test
    void youth_conditionalBail_withTriggerCondition_shouldEmitWarningB() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac2",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "REMCB", "label": "REMCB",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PORS", "label": "PORS",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Young", "lastName": "P",
                                  "dateOfBirth": "2010-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "S"}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
        final JsonNode bail016Warning = firstWarningForRule(json, RULE_ID);
        assertThat(bail016Warning.get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(WARNING_B);
    }

    /**
     * AC2A: CCIC result with trigger condition (any age) → Warning D, isValid=true.
     */
    @Test
    void ccicResult_withTriggerCondition_shouldEmitWarningD() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac2a",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "CCIC", "label": "CCIC",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PASS1", "label": "PASS1",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Joe", "lastName": "B",
                                  "dateOfBirth": "1990-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "L"}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
        final JsonNode bail016Warning = firstWarningForRule(json, RULE_ID);
        assertThat(bail016Warning.get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(WARNING_D);
    }

    /**
     * AC2B: CCSC result with trigger condition (any age) → Warning E, isValid=true.
     */
    @Test
    void ccscResult_withTriggerCondition_shouldEmitWarningE() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac2b",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "CCSC", "label": "CCSC",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PASS8", "label": "PASS8",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Sam", "lastName": "C",
                                  "dateOfBirth": "1985-06-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "C"}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
        final JsonNode bail016Warning = firstWarningForRule(json, RULE_ID);
        assertThat(bail016Warning.get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(WARNING_E);
    }

    /**
     * AC3: PORO1 with English trigger keyword "passport" triggers Warning A for adult in custody.
     */
    @Test
    void poro1_englishPassportKeyword_shouldTriggerWarning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac3",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PORO1", "label": "Other",
                     "defendantId": "d1", "offenceId": "off-1",
                     "prompts": [{"promptRef": "detail", "promptValue": "surrender passport"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                  "dateOfBirth": "1990-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "C"}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
    }

    /**
     * AC3: PORO1 with Welsh keyword "mechnïwr" (surety) → Warning A for adult in custody.
     */
    @Test
    void poro1_welshMechniwr_shouldTriggerWarning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac3-cy",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PORO1", "label": "Other",
                     "defendantId": "d1", "offenceId": "off-1",
                     "prompts": [{"promptRef": "detail", "promptValue": "mechnïwr"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                  "dateOfBirth": "1990-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "C"}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
    }

    /**
     * AC5: non-conditional result (RIB) with trigger condition → no DR-BAIL-016 warning.
     */
    @Test
    void nonConditionalResult_shouldNotEmitWarning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac5-result",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RIB", "label": "RIB",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PASS5", "label": "Passport",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                  "dateOfBirth": "1990-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "C"}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID);
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
    }

    /**
     * AC5: PORO1 with no trigger keyword → no DR-BAIL-016 warning.
     */
    @Test
    void poro1_noKeyword_shouldNotEmitWarning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac5-nokw",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PORO1", "label": "Other",
                     "defendantId": "d1", "offenceId": "off-1",
                     "prompts": [{"promptRef": "detail", "promptValue": "report to probation"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                  "dateOfBirth": "1990-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "C"}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID);
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
    }

    /**
     * AC5: bailStatus=B (non-custodial) → no DR-BAIL-016 warning.
     */
    @Test
    void nonCustodialBailStatus_shouldNotEmitWarning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac5",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PASS5", "label": "Passport",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                  "dateOfBirth": "1990-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "B"}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID);
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
    }

    /**
     * AC6: defendant with no DOB, result RC and PORSC, bailStatus C → warning present (C/D/E
     * variant depends on result type; RC → Warning C for no-DOB defendants).
     */
    @Test
    void noDob_conditionalBail_shouldEmitWarning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac6",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PORSC", "label": "PORSC",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "No", "lastName": "Dob"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1, "bailStatus": "C"}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
        assertThat(firstWarningForRule(json, RULE_ID)
                .get("affectedOffences").get(0).get("message").asText())
                .isEqualTo(WARNING_C);
    }

    /**
     * AC7: offence with no bailStatus field → no DR-BAIL-016 warning (fail-open).
     */
    @Test
    void nullBailStatus_shouldNotEmitWarning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac7",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                     "defendantId": "d1", "offenceId": "off-1"},
                    {"resultLineId": "rl2", "shortCode": "PASS5", "label": "Passport",
                     "defendantId": "d1", "offenceId": "off-1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                  "dateOfBirth": "1990-01-01"}],
                  "offences": [{"offenceId": "off-1", "defendantId": "d1",
                                "offenceCode": "TH68001", "offenceTitle": "Theft",
                                "orderIndex": 1}]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(ruleIdsOf(json.get(WARNINGS))).doesNotContain(RULE_ID);
        assertThat(rulesEvaluated(json)).contains(RULE_ID);
    }

    /**
     * AC4A: DR-BAIL-016 coexists with DR-RESTRAO-010 — both warnings present, neither suppressed.
     */
    @Test
    void drBail016AndRestrainingOrder_shouldBothBePresent() throws Exception {
        final String body = """
                {
                  "hearingId": "h-live-ac4a",
                  "hearingDay": "2026-11-09",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                     "defendantId": "d1", "offenceId": "off-bail"},
                    {"resultLineId": "rl2", "shortCode": "PASS5", "label": "Passport",
                     "defendantId": "d1", "offenceId": "off-bail"},
                    {"resultLineId": "rl3", "shortCode": "RESTRAO", "label": "RESTRAO",
                     "defendantId": "d1", "offenceId": "off-rao",
                     "prompts": [{"promptRef": "protectedPersonsName",
                                  "promptValue": "John Smith & Jane Smith"}]}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Doe",
                                  "dateOfBirth": "1990-01-01"}],
                  "offences": [
                    {"offenceId": "off-bail", "defendantId": "d1",
                     "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "bailStatus": "C"},
                    {"offenceId": "off-rao", "defendantId": "d1",
                     "offenceCode": "TH68002", "offenceTitle": "RAO offence",
                     "orderIndex": 2, "bailStatus": "B"}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains(RULE_ID);
        assertThat(ruleIdsOf(json.get(WARNINGS))).contains("DR-RESTRAO-010");
    }

    private List<String> ruleIdsOf(final JsonNode issues) {
        final List<String> ids = new ArrayList<>();
        if (issues != null) {
            issues.forEach(n -> {
                final JsonNode ruleIdNode = n.get(RULE_ID_FIELD);
                if (ruleIdNode != null) {
                    ids.add(ruleIdNode.asText());
                }
            });
        }
        return ids;
    }

    private List<String> rulesEvaluated(final JsonNode json) {
        final List<String> ids = new ArrayList<>();
        json.get(RULES_EVALUATED).forEach(n -> ids.add(n.asText()));
        return ids;
    }

    private JsonNode firstWarningForRule(final JsonNode json, final String ruleId) {
        for (final JsonNode warning : json.get(WARNINGS)) {
            if (ruleId.equals(warning.get(RULE_ID_FIELD).asText())) {
                return warning;
            }
        }
        throw new AssertionError("No warning found for ruleId=" + ruleId);
    }

    private JsonNode postValidate(final String body) throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("CJSCPPUID", "test-user");
        headers.set("CPP-ACTION", "validation-service.validate");

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
