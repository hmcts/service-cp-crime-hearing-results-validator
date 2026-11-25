package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Live HTTP coverage for DR-BAIL-015 (pre-release bail conditions on a conditional bail result)
 * against a running service instance.
 *
 * <p>DR-BAIL-015 defaults to enabled by its Flyway seed migration and nothing else in this suite
 * disables it, so no rule-state setup is needed here.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>AC1 — adult defendant with remand-bail parent + pre-release child → Error A (blocking)</li>
 *   <li>AC2 — youth defendant with remand-bail parent + pre-release child → Error B (blocking)</li>
 *   <li>AC2A — CCIC parent + pre-release child → Error D (blocking)</li>
 *   <li>AC2B — CCSC parent + pre-release child → Error E (blocking)</li>
 *   <li>AC3 — multi-defendant error summary includes "This affects" with defendant names</li>
 *   <li>AC4 — mixed adult/youth hearing: single Error C in summary, age-specific inline</li>
 *   <li>AC4A — multiple conditional bail parent types produce separate summary entries</li>
 *   <li>AC5A — DR-BAIL-015 co-exists with DR-SENT-001 without suppression</li>
 *   <li>AC7 — error cleared when parent result changed to RIB; also when pre-release child removed</li>
 *   <li>AC8 — non-conditional bail parent → no error; pre-release child on different offence → no error</li>
 *   <li>AC9 — no date of birth → Error C (blocking)</li>
 * </ul>
 */
class PreReleaseBailConditionApiHttpLiveTest {

    private static final String BAIL_ERROR_PREFIX =
            "A conditional bail result cannot have pre-release bail conditions. ";

    private static final String ERROR_A =
            BAIL_ERROR_PREFIX
            + "If the court ordered pre-release conditions, use 'Remanded in custody with "
            + "bail direction (RIB)' instead. If the conditions apply after release, remove "
            + "the pre-release conditions and enter them as bail conditions.";

    private static final String ERROR_B =
            BAIL_ERROR_PREFIX
            + "If the court ordered pre-release conditions, use 'Remand in care of Local "
            + "Authority with bail direction (RILAB)' instead. If the conditions apply after "
            + "release, remove the pre-release conditions and enter them as bail conditions.";

    private static final String ERROR_C =
            BAIL_ERROR_PREFIX
            + "If the court ordered pre-release conditions, use 'Remanded in custody with "
            + "bail direction (RIB)' for an adult or 'Remand in care of Local Authority with "
            + "bail direction (RILAB)' for a youth instead. If the conditions apply after "
            + "release, remove the pre-release conditions and enter them as bail conditions.";

    private static final String ERROR_D =
            BAIL_ERROR_PREFIX
            + "If the court ordered pre-release conditions, use 'Sent to Crown Court in "
            + "custody for trial with bail direction (CCIIB)' instead. If the conditions "
            + "apply after release, remove the pre-release conditions and enter them as "
            + "bail conditions.";

    private static final String ERROR_E =
            BAIL_ERROR_PREFIX
            + "If the court ordered pre-release conditions, use 'Committed to Crown Court "
            + "for sentence - in custody with bail direction (CCSIB)' instead. If the "
            + "conditions apply after release, remove the pre-release conditions and enter "
            + "them as bail conditions.";

    private static final String RULE_ID = "DR-BAIL-015";
    private static final String SENT_RULE_ID = "DR-SENT-001";
    private static final String JSON_IS_VALID = "isValid";
    private static final String JSON_ERRORS = "errors";
    private static final String JSON_RULE_ID = "ruleId";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode post(final String body) throws Exception {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("CJSCPPUID", "api-test-user");
        headers.set("CPP-ACTION", "validation-service.validate");
        final ResponseEntity<String> response =
                http.exchange(baseUrl + "/api/validation/validate",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody());
    }

    private void assertRuleTriggered(final JsonNode root, final String expectedInlineMessage) {
        assertThat(root.path(JSON_IS_VALID).asBoolean()).isFalse();
        final JsonNode issues = root.path(JSON_ERRORS).path("validationIssues");
        assertThat(issues.isArray()).isTrue();
        boolean found = false;
        for (final JsonNode issue : issues) {
            if (RULE_ID.equals(issue.path(JSON_RULE_ID).asText())) {
                assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
                final String inline = issue.path("affectedOffences").get(0)
                        .path("message").asText();
                assertThat(inline).isEqualTo(expectedInlineMessage);
                found = true;
            }
        }
        assertThat(found).as(RULE_ID + " issue not found in response").isTrue();
    }

    private void assertRuleNotTriggered(final JsonNode root) {
        final JsonNode issues = root.path(JSON_ERRORS).path("validationIssues");
        if (issues.isArray()) {
            for (final JsonNode issue : issues) {
                assertThat(issue.path(JSON_RULE_ID).asText()).isNotEqualTo(RULE_ID);
            }
        }
    }

    @Nested
    @DisplayName("AC1 — Adult defendant with remand bail parent raises Error A")
    class Ac1Adult {

        @Test
        void adultDefendant_rcParent_prebcChild_shouldRaiseErrorA() throws Exception {
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API001"}
                      ]
                    }
                    """);

            assertRuleTriggered(root, ERROR_A);
        }
    }

    @Nested
    @DisplayName("AC2 — Youth defendant with remand bail parent raises Error B")
    class Ac2Youth {

        @Test
        void youthDefendant_remcbyParent_prebcChild_shouldRaiseErrorB() throws Exception {
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac2",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "REMCBY", "label": "REMCBY",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Jamie", "lastName": "Taylor", "dateOfBirth": "2012-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API002"}
                      ]
                    }
                    """);

            assertRuleTriggered(root, ERROR_B);
        }
    }

    @Nested
    @DisplayName("AC2A — CCIC parent raises Error D")
    class Ac2aCcic {

        @Test
        void ccicParent_withPreRelease_shouldRaiseErrorD() throws Exception {
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac2a",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCIC", "label": "CCIC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCS1", "label": "PRBCS1",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Sam", "lastName": "Patel", "dateOfBirth": "1985-03-10"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API003"}
                      ]
                    }
                    """);

            assertRuleTriggered(root, ERROR_D);
        }
    }

    @Nested
    @DisplayName("AC2B — CCSC parent raises Error E")
    class Ac2bCcsc {

        @Test
        void ccscParent_withPreRelease_shouldRaiseErrorE() throws Exception {
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac2b",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCSC", "label": "CCSC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCT", "label": "PRBCT",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Chris", "lastName": "Brown", "dateOfBirth": "1995-07-22"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API004"}
                      ]
                    }
                    """);

            assertRuleTriggered(root, ERROR_E);
        }
    }

    @Nested
    @DisplayName("AC3 — Multi-defendant error summary includes 'This affects' with defendant names")
    class Ac3MultiDefendantNames {

        @Test
        void twoAdultDefendants_bothTriggerErrorA_namesAppendedWithAnd() throws Exception {
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac3",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",    "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "RC",    "label": "RC",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Jane", "lastName": "Jones", "dateOfBirth": "1992-06-15"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft (d1)", "orderIndex": 1, "caseUrn": "API005"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft (d2)", "orderIndex": 2, "caseUrn": "API005"}
                      ]
                    }
                    """);

            assertThat(root.path(JSON_IS_VALID).asBoolean()).isFalse();
            final JsonNode errorMessages = root.path(JSON_ERRORS).path("errorMessages");
            assertThat(errorMessages.isArray()).isTrue();
            assertThat(errorMessages.size()).isEqualTo(1);
            final String summary = errorMessages.get(0).asText();
            assertThat(summary).contains("This affects");
            assertThat(summary).contains("John Smith");
            assertThat(summary).contains("Jane Jones");
        }
    }

    @Nested
    @DisplayName("AC4 — Mixed adult/youth hearing: single Error C summary, age-specific inline")
    class Ac4MixedAdultYouth {

        @Test
        void mixedAdultYouth_summaryShowsErrorC_inlineIsAgeSpecific() throws Exception {
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac4",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",     "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC",  "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "REMCBY", "label": "REMCBY",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "PRBCO",  "label": "PRBCO",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John",  "lastName": "Smith",  "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Jamie", "lastName": "Taylor", "dateOfBirth": "2012-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft (adult)", "orderIndex": 1, "caseUrn": "API006"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft (youth)", "orderIndex": 2, "caseUrn": "API006"}
                      ]
                    }
                    """);

            assertThat(root.path(JSON_IS_VALID).asBoolean()).isFalse();
            final JsonNode errorMessages = root.path(JSON_ERRORS).path("errorMessages");
            assertThat(errorMessages.size()).isEqualTo(1);
            final String summary = errorMessages.get(0).asText();
            assertThat(summary).contains("RIB").contains("RILAB"); // Error C: mentions both
            assertThat(summary).contains("John Smith").contains("Jamie Taylor");
            boolean hasErrorAInline = false;
            boolean hasErrorBInline = false;
            for (final JsonNode issue : root.path(JSON_ERRORS).path("validationIssues")) {
                if (RULE_ID.equals(issue.path(JSON_RULE_ID).asText())) {
                    final String inline = issue.path("affectedOffences").get(0).path("message").asText();
                    if (ERROR_A.equals(inline)) {
                        hasErrorAInline = true;
                    } else if (ERROR_B.equals(inline)) {
                        hasErrorBInline = true;
                    }
                }
            }
            assertThat(hasErrorAInline).as("Error A inline for adult offence").isTrue();
            assertThat(hasErrorBInline).as("Error B inline for youth offence").isTrue();
        }
    }

    @Nested
    @DisplayName("AC4A — Multiple conditional bail parent types produce separate summary entries")
    class Ac4aMultipleParentTypes {

        @Test
        void remandBailAndCcicBothTrigger_twoSeparateSummaryEntries() throws Exception {
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac4a",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",    "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "CCIC",  "label": "CCIC",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "PRBCO", "label": "PRBCO",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Sam",  "lastName": "Patel", "dateOfBirth": "1985-05-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft (d1)", "orderIndex": 1, "caseUrn": "API007"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft (d2)", "orderIndex": 2, "caseUrn": "API007"}
                      ]
                    }
                    """);

            assertThat(root.path(JSON_IS_VALID).asBoolean()).isFalse();
            final JsonNode errorMessages = root.path(JSON_ERRORS).path("errorMessages");
            assertThat(errorMessages.size()).isEqualTo(2);
            boolean foundErrorA = false;
            boolean foundErrorD = false;
            for (final JsonNode msg : errorMessages) {
                final String text = msg.asText();
                if (text.contains("RIB)' instead")) {
                    foundErrorA = true;
                }
                if (text.contains("CCIIB")) {
                    foundErrorD = true;
                }
            }
            assertThat(foundErrorA).as("Error A summary entry present").isTrue();
            assertThat(foundErrorD).as("Error D summary entry present").isTrue();
        }
    }

    @Nested
    @DisplayName("AC5A — DR-BAIL-015 co-exists with other rule errors without suppression")
    class Ac5aAlongsideOtherErrors {

        @Test
        void drBail015AlongsideOtherRuleError_neitherSuppressed() throws Exception {
            // DR-BAIL-015 fires ERROR on d1 (RC+PREBC); DR-SENT-001 fires WARNING on d2
            // (two IMP results where one has isConcurrent=true AND consecutiveToOffence set).
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac5a",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",    "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "IMP",   "label": "IMP",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "IMP",   "label": "IMP",
                         "defendantId": "d2", "offenceId": "off3",
                         "isConcurrent": true, "consecutiveToOffence": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Alex", "lastName": "Green"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft",   "orderIndex": 1, "caseUrn": "API008"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "AS001",
                         "offenceTitle": "Assault", "orderIndex": 2, "caseUrn": "API008"},
                        {"offenceId": "off3", "defendantId": "d2", "offenceCode": "RS001",
                         "offenceTitle": "Other",   "orderIndex": 3, "caseUrn": "API008"}
                      ]
                    }
                    """);

            assertRuleTriggered(root, ERROR_A);
            boolean foundSent001 = false;
            final JsonNode warnings = root.path("warnings");
            if (warnings.isArray()) {
                for (final JsonNode warning : warnings) {
                    if (SENT_RULE_ID.equals(warning.path(JSON_RULE_ID).asText())) {
                        foundSent001 = true;
                    }
                }
            }
            assertThat(foundSent001).as("DR-SENT-001 warning present alongside DR-BAIL-015 error")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("AC7 — Error cleared when parent result changed to suggested alternative")
    class Ac7ErrorCleared {

        @Test
        void correctedToRib_shouldClearError() throws Exception {
            assertRuleTriggered(post("""
                    {
                      "hearingId": "api-bail-ac7-before",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",    "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API009"}
                      ]
                    }
                    """), ERROR_A);

            final JsonNode corrected = post("""
                    {
                      "hearingId": "api-bail-ac7-after",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RIB",   "label": "RIB",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API009"}
                      ]
                    }
                    """);
            assertThat(corrected.path(JSON_IS_VALID).asBoolean()).isTrue();
            assertRuleNotTriggered(corrected);
        }

        @Test
        void preReleaseChildRemoved_shouldClearError() throws Exception {
            assertRuleTriggered(post("""
                    {
                      "hearingId": "api-bail-ac7b-before",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",    "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API013"}
                      ]
                    }
                    """), ERROR_A);

            final JsonNode corrected = post("""
                    {
                      "hearingId": "api-bail-ac7b-after",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API013"}
                      ]
                    }
                    """);
            assertThat(corrected.path(JSON_IS_VALID).asBoolean()).isTrue();
            assertRuleNotTriggered(corrected);
        }
    }

    @Nested
    @DisplayName("AC8 — Non-conditional bail parent does not trigger the rule")
    class Ac8NotTriggered {

        @Test
        void nonConditionalBailParent_shouldNotTrigger() throws Exception {
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac8",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "IMP",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Jane", "lastName": "Doe", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API010"}
                      ]
                    }
                    """);

            assertRuleNotTriggered(root);
        }

        @Test
        void preReleaseOnDifferentOffence_shouldNotTrigger() throws Exception {
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac8b",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",    "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Jane", "lastName": "Doe", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft",   "orderIndex": 1, "caseUrn": "API014"},
                        {"offenceId": "off2", "defendantId": "d1", "offenceCode": "AS001",
                         "offenceTitle": "Assault", "orderIndex": 2, "caseUrn": "API014"}
                      ]
                    }
                    """);

            assertRuleNotTriggered(root);
        }
    }

    @Nested
    @DisplayName("AC9 — Defendant with no date of birth raises Error C")
    class Ac9NoDob {

        @Test
        void noDob_remandBailWithPreRelease_shouldRaiseErrorC() throws Exception {
            final JsonNode root = post("""
                    {
                      "hearingId": "api-bail-ac9",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "BAILREVC", "label": "BAILREVC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCSE1", "label": "PRBCSE1",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Unknown", "lastName": "Person"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API011"}
                      ]
                    }
                    """);

            assertRuleTriggered(root, ERROR_C);
        }
    }

    @Nested
    @DisplayName("Missing hearingDay — API boundary: 400 before preprocessor is reached")
    class MissingHearingDay {

        /**
         * hearingDay is a required field. When it is absent the API returns HTTP 400 before
         * the preprocessor runs. This test documents the boundary that makes the null-hearingDay
         * guard in PreReleaseBailConditionPreprocessor a defensive layer, with the actual
         * null-path logic covered by PreReleaseBailConditionPreprocessorTest.
         */
        @Test
        void missingHearingDay_should_returnBadRequest() {
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("CJSCPPUID", "api-test-user");
            headers.set("CPP-ACTION", "validation-service.validate");

            final String body = """
                    {
                      "hearingId": "api-bail-null-day",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",    "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "API012"}
                      ]
                    }
                    """;

            final HttpClientErrorException.BadRequest ex = catchThrowableOfType(
                    HttpClientErrorException.BadRequest.class,
                    () -> http.exchange(
                            baseUrl + "/api/validation/validate",
                            HttpMethod.POST,
                            new HttpEntity<>(body, headers),
                            String.class));

            assertThat(ex).isNotNull();
        }
    }
}