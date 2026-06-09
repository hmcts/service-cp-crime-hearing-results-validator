package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Live HTTP coverage for the draft validation endpoint against a running service instance.
 */
class ValidationApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String WARNINGS = "warnings";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-SENT-002";
    private static final String CTL_RULE_ID = "DR-CTL-001";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Covers the empty-hearing scenario where no result lines exist and validation should return a
     * clean advisory response with no issues.
     */
    @Test
    void validate_empty_arrays_should_return_valid() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "caseId": "c1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [],
                  "defendants": [],
                  "offences": []
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get("validationId").asText()).startsWith("val-");
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.get("mode").asText()).isEqualTo("advisory");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
        assertThat(json.get(RULES_EVALUATED).get(0).asText()).isEqualTo(RULE_ID);
    }

    /**
     * Covers AC1 for a single primary custodial offence plus a second offence marked concurrent, so
     * the request should remain valid with no warnings or errors.
     */
    @Test
    void ac1_single_offence_without_info_should_be_valid() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "isConcurrent": true}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Doe"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers AC2 where more than one non-primary custodial offence omits concurrent or consecutive
     * information, producing a blocking error.
     */
    @Test
    void ac2_multiple_offences_missing_info_should_produce_error() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2"},
                    {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off3"},
                    {"resultLineId": "rl4", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off4", "isConcurrent": true}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Doe"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2},
                    {"offenceId": "off3", "offenceCode": "BG001", "offenceTitle": "Burglary", "orderIndex": 3},
                    {"offenceId": "off4", "offenceCode": "RB001", "offenceTitle": "Robbery", "orderIndex": 4}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isFalse();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).hasSize(1);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("ruleId").asText()).isEqualTo(RULE_ID);
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("severity").asText()).isEqualTo("ERROR");
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES).get(0).get("affectedOffences")).hasSize(3);
        assertThat(json.get(ERRORS).get(ERROR_MESSAGES).get(0).asText())
                .startsWith("Some offences do not include details")
                .contains("John Doe");
    }

    /**
     * Covers AC3 where a custodial offence is marked both concurrent and consecutive, producing a
     * warning but not invalidating the payload.
     */
    @Test
    void ac3_offence_with_both_concurrent_and_consecutive_should_produce_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "CROWN",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "isConcurrent": true, "consecutiveToOffence": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Doe"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).hasSize(1);
        assertThat(json.get(WARNINGS).get(0).get("ruleId").asText()).isEqualTo(RULE_ID);
        assertThat(json.get(WARNINGS).get(0).get("severity").asText()).isEqualTo("WARNING");
        assertThat(json.get(WARNINGS).get(0).get("affectedOffences").get(0).get("message").asText())
                .startsWith("John Doe")
                .contains("Offence 2").contains("concurrent").contains("consecutive");
    }

    /**
     * Covers AC4 where every custodial offence has relationship data and no primary sentence can be
     * identified, producing a warning only.
     */
    @Test
    void ac4_all_offences_have_info_no_primary_should_produce_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off1", "isConcurrent": true},
                    {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment", "defendantId": "d1", "offenceId": "off2", "consecutiveToOffence": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Doe"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                    {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).hasSize(1);
        assertThat(json.get(WARNINGS).get(0).get("ruleId").asText()).isEqualTo(RULE_ID);
        assertThat(json.get(WARNINGS).get(0).get("affectedDefendants").get(0).get("message").asText())
                .startsWith("John Doe")
                .contains("All offences include details")
                .contains("no primary sentence");
    }

    /**
     * Covers DR-CTL-001 AC1: a remand result (RI) with no existing CTL record, no CTL result in
     * the current hearing, and a non-convicted offence triggers a WARNING on that offence.
     */
    @Test
    void ctl001_remand_result_without_ctl_should_produce_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-ctl1",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RI", "label": "Remand in custody",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Smith"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).hasSize(1);
        assertThat(json.get(WARNINGS).get(0).get("ruleId").asText()).isEqualTo(CTL_RULE_ID);
        assertThat(json.get(WARNINGS).get(0).get("severity").asText()).isEqualTo("WARNING");
        assertThat(json.get(WARNINGS).get(0).get("validationLevel").asText()).isEqualTo("OFFENCE");
        assertThat(json.get(WARNINGS).get(0).get("affectedOffences")).hasSize(1);
        assertThat(json.get(WARNINGS).get(0).get("affectedOffences").get(0).get("offenceId").asText())
                .isEqualTo("off1");
        assertThat(json.get(WARNINGS).get(0).get("affectedOffences").get(0).get("message").asText())
                .isEqualTo("This offence does not have a CTL. If the trial has started a CTL is not"
                        + " needed. It is your responsibility to check and confirm.");
    }

    /**
     * Covers DR-CTL-001 suppression: when a CTL result is already present in the same hearing,
     * the warning is not raised.
     */
    @Test
    void ctl001_remand_with_ctl_result_in_hearing_should_be_valid() throws Exception {
        final String body = """
                {
                  "hearingId": "h-ctl2",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RI",  "label": "Remand in custody",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "CTL", "label": "Custody time limit",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Smith"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": false}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers DR-CTL-001 suppression: an existing CTL record from a prior hearing disables the warning.
     */
    @Test
    void ctl001_remand_with_existing_ctl_record_should_be_valid() throws Exception {
        final String body = """
                {
                  "hearingId": "h-ctl3",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RI", "label": "Remand in custody",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Smith"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "hasExistingCtlRecord": true, "isConvicted": false}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
    }

    /**
     * Covers DR-CTL-001 suppression: a convicted offence does not trigger the CTL missing warning.
     */
    @Test
    void ctl001_convicted_offence_should_not_produce_warning() throws Exception {
        final String body = """
                {
                  "hearingId": "h-ctl4",
                  "hearingDay": "2026-03-11",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "RI", "label": "Remand in custody",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [{"defendantId": "d1", "firstName": "Jane", "lastName": "Smith"}],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft",
                     "orderIndex": 1, "hasExistingCtlRecord": false, "isConvicted": true}
                  ]
                }
                """;

        final JsonNode json = postValidate(body);

        assertThat(json.get(IS_VALID).asBoolean()).isTrue();
        assertThat(json.get(ERRORS).get(VALIDATION_ISSUES)).isEmpty();
        assertThat(json.get(WARNINGS)).isEmpty();
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
