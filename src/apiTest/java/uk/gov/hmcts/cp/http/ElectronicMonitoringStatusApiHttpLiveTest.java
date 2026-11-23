package uk.gov.hmcts.cp.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Live HTTP coverage for DR-EMON-017 (electronic monitoring status not updated) against a running
 * service.
 *
 * <p>DR-EMON-017 defaults to enabled by its Flyway seed migration (V1.019) and nothing else in this
 * suite disables it, so no rule-state setup is needed here.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>AC2 — EM-flagged offence with IMP result, no continuing/ending result →
 *       an offence-level ERROR is emitted with the prescribed message</li>
 *   <li>AC1 — EM-flagged offence with EMONE ending result → no DR-EMON-017 error</li>
 *   <li>AC9 — no EM-flagged offences → no DR-EMON-017 error</li>
 * </ul>
 */
class ElectronicMonitoringStatusApiHttpLiveTest {

    private static final String IS_VALID = "isValid";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_ISSUES = "validationIssues";
    private static final String RULES_EVALUATED = "rulesEvaluated";
    private static final String RULE_ID = "DR-EMON-017";

    private static final String OFFENCE_LEVEL_MSG =
            "Electronic monitoring is in place on this offence. Add a result that continues"
                    + " electronic monitoring, or end it with"
                    + " 'Electronic Monitoring End - notify contractor (EMONE)'.";

    private final String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8082");
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("AC2 — EM-flagged offence with unresolved result → ERROR")
    class Ac2ErrorPath {

        /**
         * Covers AC2: EM-flagged offence with IMP (category F) result, no EMONE, no continuing
         * prompt → an offence-level ERROR is emitted.
         */
        @Test
        void emFlagged_withImpResult_noResolution_should_emitError() throws Exception {
            final String body = """
                    {
                      "hearingId": "h-emon-live-ac2",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            assertThat(json.get(IS_VALID).asBoolean()).isFalse();

            final List<JsonNode> emonErrors = emonErrors(json);
            assertThat(emonErrors).hasSize(1);
            assertThat(emonErrors.get(0).get("severity").asText()).isEqualTo("ERROR");
            assertThat(emonErrors.get(0).get("affectedOffences")).hasSize(1);
            assertThat(emonErrors.get(0).get("affectedOffences").get(0).get("offenceId").asText())
                    .isEqualTo("off-1");
            assertThat(emonErrors.get(0).get("affectedOffences").get(0).get("message").asText())
                    .isEqualToIgnoringWhitespace(OFFENCE_LEVEL_MSG);
            assertThat(rulesEvaluated(json)).contains(RULE_ID);
        }
    }

    @Nested
    @DisplayName("AC1 — EM-flagged offence with ending result → no error")
    class Ac1HappyPath {

        /**
         * Covers AC1: EM-flagged offence with EMONE ending result → no DR-EMON-017 error.
         */
        @Test
        void emFlagged_withEmoneResult_should_produceNoError() throws Exception {
            final String body = """
                    {
                      "hearingId": "h-emon-live-ac1",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "EMONE",
                         "label": "Electronic Monitoring End",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            assertThat(emonErrors(json)).isEmpty();
            assertThat(rulesEvaluated(json)).contains(RULE_ID);
        }
    }

    @Nested
    @DisplayName("AC3 — Exempt code as child of non-exempt main result → error")
    class Ac3ExemptAsChild {

        /**
         * Covers AC3: RC (non-exempt) + NHMC (exempt but only as child) → error fires.
         */
        @Test
        void rcAndNhmc_bothCategoryI_should_emitError() throws Exception {
            final String body = """
                    {
                      "hearingId": "h-emon-live-ac3",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "category": "I",
                         "label": "Remand on conditional bail",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "NHMC", "category": "I",
                         "label": "No hearing case management",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            assertThat(json.get(IS_VALID).asBoolean()).isFalse();
            assertThat(emonErrors(json)).hasSize(1);
            assertThat(emonErrors(json).get(0).get("severity").asText()).isEqualTo("ERROR");
            assertThat(emonErrors(json).get(0).get("affectedOffences")).hasSize(1);
            assertThat(emonErrors(json).get(0).get("affectedOffences").get(0).get("offenceId").asText())
                    .isEqualTo("off-1");
            assertThat(emonErrors(json).get(0).get("affectedOffences").get(0).get("message").asText())
                    .isEqualToIgnoringWhitespace(OFFENCE_LEVEL_MSG);
            assertThat(rulesEvaluated(json)).contains(RULE_ID);
        }
    }

    @Nested
    @DisplayName("AC4 — Error summary names affected defendants")
    class Ac4DefendantNames {

        /**
         * Covers AC4: two defendants both unresolved → error message contains "and" separator.
         */
        @Test
        void twoDefendantsBothUnresolved_should_containBothNamesWithAndSeparator() throws Exception {
            final String body = """
                    {
                      "hearingId": "h-emon-live-ac4a",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment",
                         "defendantId": "d2", "offenceId": "off-2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "John", "lastName": "Smith"},
                        {"defendantId": "d2", "firstName": "Jane", "lastName": "Jones"}
                      ],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft A", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true},
                        {"offenceId": "off-2", "defendantId": "d2", "offenceCode": "CD98001",
                         "offenceTitle": "Theft B", "orderIndex": 2,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            assertThat(json.get(IS_VALID).asBoolean()).isFalse();
            final String errorMsg = json.get(ERRORS).get("errorMessages").get(0).asText();
            assertThat(errorMsg).contains("John Smith and Jane Jones");
            assertThat(rulesEvaluated(json)).contains(RULE_ID);
        }

        /**
         * Covers AC4 partial case: two defendants but only one unresolved → error names only the
         * affected defendant, not both.
         */
        @Test
        void twoDefendantsOnlyOneUnresolved_should_nameOnlyAffectedDefendant() throws Exception {
            final String body = """
                    {
                      "hearingId": "h-emon-live-ac4d",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "EMONE",
                         "label": "Electronic Monitoring End",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment",
                         "defendantId": "d2", "offenceId": "off-2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "John", "lastName": "Smith"},
                        {"defendantId": "d2", "firstName": "Jane", "lastName": "Jones"}
                      ],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft A", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true},
                        {"offenceId": "off-2", "defendantId": "d2", "offenceCode": "CD98001",
                         "offenceTitle": "Theft B", "orderIndex": 2,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            assertThat(json.get(IS_VALID).asBoolean()).isFalse();
            final String errorMsg = json.get(ERRORS).get("errorMessages").get(0).asText();
            assertThat(errorMsg).contains("Jane Jones");
            assertThat(errorMsg).doesNotContain("John Smith");
            assertThat(rulesEvaluated(json)).contains(RULE_ID);
        }

        /**
         * Covers AC4 three-or-more defendants: comma-separated with "and" before the last name.
         */
        @Test
        void threeDefendantsAllUnresolved_should_containCommaAndBeforeLastName() throws Exception {
            final String body = """
                    {
                      "hearingId": "h-emon-live-ac4c",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment",
                         "defendantId": "d2", "offenceId": "off-2"},
                        {"resultLineId": "rl3", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment",
                         "defendantId": "d3", "offenceId": "off-3"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "John", "lastName": "Smith"},
                        {"defendantId": "d2", "firstName": "Jane", "lastName": "Jones"},
                        {"defendantId": "d3", "firstName": "Sam", "lastName": "Patel"}
                      ],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft A", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true},
                        {"offenceId": "off-2", "defendantId": "d2", "offenceCode": "CD98001",
                         "offenceTitle": "Theft B", "orderIndex": 2,
                         "hasActiveElectronicMonitoring": true},
                        {"offenceId": "off-3", "defendantId": "d3", "offenceCode": "CD98001",
                         "offenceTitle": "Theft C", "orderIndex": 3,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            assertThat(json.get(IS_VALID).asBoolean()).isFalse();
            final String errorMsg = json.get(ERRORS).get("errorMessages").get(0).asText();
            assertThat(errorMsg).contains("John Smith, Jane Jones and Sam Patel");
            assertThat(rulesEvaluated(json)).contains(RULE_ID);
        }

        /**
         * Covers AC4 single-defendant clause: when only one defendant is in the hearing,
         * the "This affects" sentence is stripped from the error summary.
         */
        @Test
        void singleDefendant_errorTriggered_should_notContainThisAffects() throws Exception {
            final String body = """
                    {
                      "hearingId": "h-emon-live-ac4b",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            assertThat(json.get(IS_VALID).asBoolean()).isFalse();
            final String errorMsg = json.get(ERRORS).get("errorMessages").get(0).asText();
            assertThat(errorMsg).doesNotContain("This affects");
            assertThat(rulesEvaluated(json)).contains(RULE_ID);
        }
    }

    @Nested
    @DisplayName("AC9 — No EM-flagged offences → no error")
    class Ac9NoEmFlag {

        /**
         * Covers AC9: no EM-flagged offences → no DR-EMON-017 error regardless of results.
         */
        @Test
        void noEmFlaggedOffences_should_produceNoError() throws Exception {
            final String body = """
                    {
                      "hearingId": "h-emon-live-ac9",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": false}
                      ]
                    }
                    """;

            final JsonNode json = postValidate(body);

            assertThat(emonErrors(json)).isEmpty();
            assertThat(rulesEvaluated(json)).contains(RULE_ID);
        }
    }

    private List<JsonNode> emonErrors(final JsonNode json) {
        final List<JsonNode> result = new ArrayList<>();
        json.get(ERRORS).get(VALIDATION_ISSUES)
                .forEach(issue -> {
                    if (RULE_ID.equals(issue.get("ruleId").asText())) {
                        result.add(issue);
                    }
                });
        return result;
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
