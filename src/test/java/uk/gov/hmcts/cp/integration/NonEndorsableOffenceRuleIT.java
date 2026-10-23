package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Integration tests for DR-END-014 (endorsement or disqualification result on non-endorsable
 * offence). Covers:
 * <ul>
 *   <li>User Story 1 — endorsement trigger warns on non-endorsable offence (AC1)</li>
 *   <li>User Story 2 — disqualification trigger warns on non-endorsable offence (AC2)</li>
 *   <li>User Story 3 — both families on the same offence fire two warnings (AC1c)</li>
 *   <li>User Story 4 — multi-offence independence</li>
 *   <li>User Story 5 — coexistence with other rules</li>
 * </ul>
 *
 * <p>Each test uses a unique {@code offenceCode} (equal to its {@code offenceId}) to avoid cache
 * collisions — the {@code referencedataOffences} Caffeine cache is keyed by offence code and
 * shared across the Spring context.
 */
class NonEndorsableOffenceRuleIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String ENDORSEMENT_MESSAGE =
            "This offence is not endorsable. Remove the endorsement result.";
    private static final String DISQ_MESSAGE =
            "This offence is not endorsable. Remove the disqualification result, or record a"
                    + " disqualification for non-endorsable offence result instead.";

    @Nested
    @DisplayName("User Story 1 — endorsement trigger (AC1)")
    class EndorsementTriggerWarning {

        @Test
        void lepOnNonEndorsableOffence_shouldRaiseWarning() throws Exception {
            stubEndorsableFlagResponse("ne-end-lep", false);
            String request = singleOffenceRequest("ne-end-lep", "LEP");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')].severity", contains("WARNING")))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')].validationLevel", contains("OFFENCE")))
                    .andExpect(jsonPath(
                            "$.warnings[?(@.ruleId=='DR-END-014')].affectedOffences[0].offenceId",
                            contains("ne-end-lep")))
                    .andExpect(jsonPath(
                            "$.warnings[?(@.ruleId=='DR-END-014')].affectedOffences[0].message",
                            contains(ENDORSEMENT_MESSAGE)));
        }

        @Test
        void lenOnNonEndorsableOffence_shouldRaiseWarning() throws Exception {
            stubEndorsableFlagResponse("ne-end-len", false);
            String request = singleOffenceRequest("ne-end-len", "LEN");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(1)));
        }

        @Test
        void leaOnNonEndorsableOffence_shouldRaiseWarning() throws Exception {
            stubEndorsableFlagResponse("ne-end-lea", false);
            String request = singleOffenceRequest("ne-end-lea", "LEA");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(1)));
        }

        @Test
        void absentEndorsableFlag_shouldRaiseWarningViaFailOpen() throws Exception {
            stubEndorsableFlagResponse("ne-end-absent", null);
            String request = singleOffenceRequest("ne-end-absent", "LEP");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(1)));
        }

        @Test
        void lepOnEndorsableOffence_shouldNotRaiseWarning() throws Exception {
            stubEndorsableFlagResponse("ne-end-endorsable", true);
            String request = singleOffenceRequest("ne-end-endorsable", "LEP");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", empty()));
        }

        @Test
        void nonTriggerCodeOnNonEndorsableOffence_shouldNotRaiseWarning() throws Exception {
            String request = singleOffenceRequest("ne-end-no-trigger", "IMP");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", empty()));
        }
    }

    @Nested
    @DisplayName("User Story 2 — disqualification trigger (AC2)")
    class DisqualificationTriggerWarning {

        @Test
        void ddoOnNonEndorsableOffence_shouldRaiseWarning() throws Exception {
            stubEndorsableFlagResponse("ne-disq-ddo", false);
            String request = singleOffenceRequest("ne-disq-ddo", "DDO");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')].severity", contains("WARNING")))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')].validationLevel", contains("OFFENCE")))
                    .andExpect(jsonPath(
                            "$.warnings[?(@.ruleId=='DR-END-014')].affectedOffences[0].offenceId",
                            contains("ne-disq-ddo")))
                    .andExpect(jsonPath(
                            "$.warnings[?(@.ruleId=='DR-END-014')].affectedOffences[0].message",
                            contains(DISQ_MESSAGE)));
        }

        @Test
        void ddrcotOnNonEndorsableOffence_shouldRaiseWarning() throws Exception {
            stubEndorsableFlagResponse("ne-disq-ddrcot", false);
            String request = singleOffenceRequest("ne-disq-ddrcot", "DDRCOT");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(1)));
        }

        @Test
        void ddoOnEndorsableOffence_shouldNotRaiseWarning() throws Exception {
            stubEndorsableFlagResponse("ne-disq-end", true);
            String request = singleOffenceRequest("ne-disq-end", "DDO");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", empty()));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "DDO", "DDOL", "DDOR", "DDOTE", "DDOTEL",
            "DDD", "DDDL", "DDDT", "DDDTL", "DDDTO",
            "DDP", "DDPL", "DDPR", "DDPTE", "DDPTEL",
            "DDRCOT"})
        void allDisqTriggerCodes_onNonEndorsableOffence_shouldRaiseWarning(
                final String shortCode) throws Exception {
            final String offenceId = "ne-disq-param-" + shortCode;
            stubEndorsableFlagResponse(offenceId, false);
            final String request = singleOffenceRequest(offenceId, shortCode);

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("Non-endorsable disqualification family clear (AC3)")
    class NonEndorsableDisqualificationClear {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"DDRN", "DDRNL", "DDRA", "DDRAL", "DDRV", "DDRVL"})
        void allNonEndorsableDisqCodes_shouldNotRaiseWarning(final String shortCode) throws Exception {
            final String request = singleOffenceRequest("ne-ac3-" + shortCode, shortCode);

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", empty()));
        }
    }

    @Nested
    @DisplayName("User Story 3 — both families on same offence (AC1c)")
    class BothFamiliesOnSameOffence {

        @Test
        void lepAndDdo_bothOnNonEndorsableOffence_shouldRaiseTwoWarningsFromSameRule() throws Exception {
            stubEndorsableFlagResponse("ne-both-off", false);
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-10-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "LEP", "label": "LEP label",
                         "defendantId": "d1", "offenceId": "ne-both-off"},
                        {"resultLineId": "rl2", "shortCode": "DDO", "label": "DDO label",
                         "defendantId": "d1", "offenceId": "ne-both-off"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Test", "lastName": "User"}
                      ],
                      "offences": [
                        {"offenceId": "ne-both-off", "offenceCode": "ne-both-off",
                         "offenceTitle": "Non-endorsable offence", "orderIndex": 1, "defendantId": "d1"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(2)))
                    .andExpect(jsonPath(
                            "$.warnings[?(@.ruleId=='DR-END-014')].affectedOffences[0].offenceId",
                            contains("ne-both-off", "ne-both-off")));
        }
    }

    @Nested
    @DisplayName("User Story 4 — multi-offence independence")
    class MultiOffenceIndependence {

        @Test
        void threeOffences_onlyNonEndorsableWithLep_shouldWarnForThatOffenceOnly() throws Exception {
            stubEndorsableFlagResponse("ne-multi-nonend", false);
            stubEndorsableFlagResponse("ne-multi-end", true);
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-10-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "LEP", "label": "LEP label",
                         "defendantId": "d1", "offenceId": "ne-multi-nonend"},
                        {"resultLineId": "rl2", "shortCode": "LEP", "label": "LEP label",
                         "defendantId": "d1", "offenceId": "ne-multi-end"},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "IMP label",
                         "defendantId": "d1", "offenceId": "ne-multi-notrigger"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Test", "lastName": "User"}
                      ],
                      "offences": [
                        {"offenceId": "ne-multi-nonend", "offenceCode": "ne-multi-nonend",
                         "offenceTitle": "Offence A", "orderIndex": 1, "defendantId": "d1"},
                        {"offenceId": "ne-multi-end", "offenceCode": "ne-multi-end",
                         "offenceTitle": "Offence B", "orderIndex": 2, "defendantId": "d1"},
                        {"offenceId": "ne-multi-notrigger", "offenceCode": "ne-multi-nonend",
                         "offenceTitle": "Offence C", "orderIndex": 3, "defendantId": "d1"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(1)))
                    .andExpect(jsonPath(
                            "$.warnings[?(@.ruleId=='DR-END-014')].affectedOffences[0].offenceId",
                            contains("ne-multi-nonend")));
        }

        @Test
        void threeOffences_onlyNonEndorsableWithDdo_shouldWarnForThatOffenceOnly() throws Exception {
            stubEndorsableFlagResponse("ne-disq-multi-ne", false);
            stubEndorsableFlagResponse("ne-disq-multi-e", true);
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-10-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DDO", "label": "DDO label",
                         "defendantId": "d1", "offenceId": "ne-disq-multi-ne"},
                        {"resultLineId": "rl2", "shortCode": "DDO", "label": "DDO label",
                         "defendantId": "d1", "offenceId": "ne-disq-multi-e"},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "IMP label",
                         "defendantId": "d1", "offenceId": "ne-disq-multi-none"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Test", "lastName": "User"}
                      ],
                      "offences": [
                        {"offenceId": "ne-disq-multi-ne", "offenceCode": "ne-disq-multi-ne",
                         "offenceTitle": "Offence A", "orderIndex": 1, "defendantId": "d1"},
                        {"offenceId": "ne-disq-multi-e", "offenceCode": "ne-disq-multi-e",
                         "offenceTitle": "Offence B", "orderIndex": 2, "defendantId": "d1"},
                        {"offenceId": "ne-disq-multi-none", "offenceCode": "ne-disq-multi-ne",
                         "offenceTitle": "Offence C", "orderIndex": 3, "defendantId": "d1"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(1)))
                    .andExpect(jsonPath(
                            "$.warnings[?(@.ruleId=='DR-END-014')].affectedOffences[0].offenceId",
                            contains("ne-disq-multi-ne")));
        }
    }

    @Nested
    @DisplayName("User Story 5 — coexistence with other rules")
    class CoexistenceWithOtherRules {

        @Test
        void endorsementWarningAndCustodialWarning_shouldBothAppear() throws Exception {
            stubEndorsableFlagResponse("ne-coexist-off", false);
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-10-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "LEP", "label": "LEP label",
                         "defendantId": "d1", "offenceId": "ne-coexist-off"},
                        {"resultLineId": "rl2", "shortCode": "IMP", "label": "IMP label",
                         "defendantId": "d1", "offenceId": "ne-coexist-c1", "isConcurrent": true},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "IMP label",
                         "defendantId": "d1", "offenceId": "ne-coexist-c2", "isConcurrent": true}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Alex", "lastName": "Jones",
                         "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "ne-coexist-off", "offenceCode": "ne-coexist-off",
                         "offenceTitle": "Non-endorsable offence", "orderIndex": 1, "defendantId": "d1"},
                        {"offenceId": "ne-coexist-c1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 2, "defendantId": "d1"},
                        {"offenceId": "ne-coexist-c2", "offenceCode": "TH68002",
                         "offenceTitle": "Theft", "orderIndex": 3, "defendantId": "d1"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-END-014')]", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SENT-001')]", hasSize(1)));
        }
    }

    private static String singleOffenceRequest(final String offenceId, final String shortCode) {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-10-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "label": "%s label",
                     "defendantId": "d1", "offenceId": "%s"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Test", "lastName": "User"}
                  ],
                  "offences": [
                    {"offenceId": "%s", "offenceCode": "%s", "offenceTitle": "Test offence",
                     "orderIndex": 1, "defendantId": "d1"}
                  ]
                }
                """.formatted(shortCode, shortCode, offenceId, offenceId, offenceId);
    }

    private ResultActions performValidate(final String request) throws Exception {
        return mockMvc.perform(post(VALIDATE_URL)
                .header("CJSCPPUID", "test-user")
                .header("CPP-ACTION", "validation-service.validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }
}
