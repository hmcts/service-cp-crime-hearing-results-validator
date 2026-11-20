package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * End-to-end integration tests for DR-EMON-017 (Electronic Monitoring Status Validation)
 * over the public validate endpoint.
 *
 * <p>DR-EMON-017 defaults to enabled by its Flyway seed migration (V1.019) and nothing else
 * in the test suite disables it, so no rule-state setup is needed here.
 *
 * <p>Every error scenario pins three response slices:
 * <ul>
 *   <li>{@code $.errors.validationIssues[?(@.ruleId=='DR-EMON-017')]} has size 1.</li>
 *   <li>{@code $.errors.validationIssues[?(@.ruleId=='DR-EMON-017')].affectedOffences[*].message} contains the expected text.</li>
 *   <li>{@code $.warnings} is empty (no other rule fires on these minimal payloads).</li>
 * </ul>
 */
class ElectronicMonitoringStatusIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_EMON_ERRORS =
            "$.errors.validationIssues[?(@.ruleId=='DR-EMON-017')]";
    private static final String DR_EMON_AFFECTED =
            "$.errors.validationIssues[?(@.ruleId=='DR-EMON-017')].affectedOffences[*]";

    private static final String OFFENCE_LEVEL_MSG =
            "Electronic monitoring is in place on this offence. Add a result that continues"
                    + " electronic monitoring, or end it with"
                    + " 'Electronic Monitoring End - notify contractor (EMONE)'.";

    // ═════════════════════════════════════════════════════════════════════════
    // T015 — Core scenarios
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AC2 — Error: EM-flagged offence with result that neither continues nor ends EM")
    class Ac2ErrorScenarios {

        @Test
        @DisplayName("(a) EM-flagged offence with IMP result, no EMONE, no continuing prompt → ERROR with correct message")
        void emFlagged_withImpResult_noEmone_should_produceError() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-ac2a",
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

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-EMON-017')].severity",
                            hasItem("ERROR")))
                    .andExpect(jsonPath(DR_EMON_AFFECTED, hasSize(1)))
                    .andExpect(jsonPath(DR_EMON_AFFECTED + ".offenceId", hasItem("off-1")))
                    .andExpect(jsonPath(DR_EMON_AFFECTED + ".message", hasItem(OFFENCE_LEVEL_MSG)))
                    .andExpect(jsonPath("$.rulesEvaluated", hasItem("DR-EMON-017")));
        }
    }

    @Nested
    @DisplayName("AC1 — Pass: all EM-flagged offences resolved")
    class Ac1ResolvedScenarios {

        @Test
        @DisplayName("(b) EM-flagged offence with EMONE result → no errors")
        void emFlagged_withEmoneResult_should_produceNoErrors() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-ac1b",
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

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }

        @Test
        @DisplayName("(c) EM-flagged offence with RC that has ELMONDET=Yes prompt → no errors")
        void emFlagged_withRcAndElmondetYes_should_produceNoErrors() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-ac1c",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "category": "I",
                         "label": "Remand on conditional bail",
                         "defendantId": "d1", "offenceId": "off-1",
                         "prompts": [{"promptRef": "ELMONDET", "promptValue": "Yes"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }

        @Test
        @DisplayName("(g) All EM-flagged offences blank (no results) → no errors")
        void emFlagged_withNoResults_should_produceNoErrors() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-ac1g",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }
    }

    @Nested
    @DisplayName("AC3 — Error: exempt code as child of non-exempt main result does not resolve")
    class Ac3ExemptAsChild {

        @Test
        @DisplayName("(d) RC + NHMC (both category I, RC is non-exempt) → error fires")
        void rcAndNhmcBothCategoryI_should_produceError() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-ac3d",
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

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-EMON-017')].severity",
                            hasItem("ERROR")))
                    .andExpect(jsonPath(DR_EMON_AFFECTED, hasSize(1)))
                    .andExpect(jsonPath(DR_EMON_AFFECTED + ".offenceId", hasItem("off-1")))
                    .andExpect(jsonPath(DR_EMON_AFFECTED + ".message", hasItem(OFFENCE_LEVEL_MSG)));
        }
    }

    @Nested
    @DisplayName("AC9 — Pass: no EM-flagged offences")
    class Ac9NoEmFlag {

        @Test
        @DisplayName("(e) No EM-flagged offences → no errors")
        void noEmFlaggedOffences_should_produceNoErrors() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-ac9e",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": false}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }

        @Test
        @DisplayName("(e2) Zero offences in hearing → no errors")
        void zeroOffences_should_produceNoErrors() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-ac9e2",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": []
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }
    }

    @Nested
    @DisplayName("AC4 — Error message names affected defendants")
    class Ac4MultiDefendantNames {

        @Test
        @DisplayName("(f) Two defendants, both unresolved → error message contains both names")
        void twoDefendantsBothUnresolved_should_containBothNamesInErrorMessage() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-ac4f",
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

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            containsString("John Smith and Jane Jones")));
        }

        @Test
        @DisplayName("(g) Three defendants, all unresolved → error message names all three with 'and' before last")
        void threeDefendantsAllUnresolved_should_containAllNamesInErrorMessage() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-emon-ac4g",
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

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(3)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            containsString("John Smith, Jane Jones and Sam Patel")));
        }

        @Test
        @DisplayName("(g2) Two defendants, only one unresolved → error message names only the affected defendant")
        void twoDefendantsOnlyOneUnresolved_should_nameOnlyAffectedDefendant() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-emon-ac4g2",
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

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", containsString("Jane Jones")))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", not(containsString("John Smith"))));
        }

        @Test
        @DisplayName("(h) Single defendant, error triggered → error message does not include 'This affects'")
        void singleDefendant_errorTriggered_errorMessageShouldNotContainThisAffects()
                throws Exception {
            final String request = """
                    {
                      "hearingId": "h-emon-ac4h",
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

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", not(containsString("This affects"))));
        }

        @Test
        @DisplayName("(i) Five defendants, all unresolved → error message names all five with commas and 'and' before last")
        void fiveDefendantsAllUnresolved_should_containAllNamesWithCommaAndFormat() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-emon-ac4i",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment", "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment", "defendantId": "d2", "offenceId": "off-2"},
                        {"resultLineId": "rl3", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment", "defendantId": "d3", "offenceId": "off-3"},
                        {"resultLineId": "rl4", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment", "defendantId": "d4", "offenceId": "off-4"},
                        {"resultLineId": "rl5", "shortCode": "IMP", "category": "F",
                         "label": "Imprisonment", "defendantId": "d5", "offenceId": "off-5"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "John",  "lastName": "Smith"},
                        {"defendantId": "d2", "firstName": "Jane",  "lastName": "Jones"},
                        {"defendantId": "d3", "firstName": "Sam",   "lastName": "Patel"},
                        {"defendantId": "d4", "firstName": "Alex",  "lastName": "Lee"},
                        {"defendantId": "d5", "firstName": "Kim",   "lastName": "Davis"}
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
                         "hasActiveElectronicMonitoring": true},
                        {"offenceId": "off-4", "defendantId": "d4", "offenceCode": "CD98001",
                         "offenceTitle": "Theft D", "orderIndex": 4,
                         "hasActiveElectronicMonitoring": true},
                        {"offenceId": "off-5", "defendantId": "d5", "offenceCode": "CD98001",
                         "offenceTitle": "Theft E", "orderIndex": 5,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(5)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            containsString("John Smith, Jane Jones, Sam Patel, Alex Lee and Kim Davis")));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // T016 — Additional AC1 resolution paths
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("T016 — Additional AC1 resolution combinations")
    class AdditionalResolutionPaths {

        @Test
        @DisplayName("(a) EMREQ=Yes prompt → resolved")
        void emreqYesPrompt_should_resolveOffence() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-t16a",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                         "label": "Conditional order of discharge",
                         "defendantId": "d1", "offenceId": "off-1",
                         "prompts": [{"promptRef": "EMREQ", "promptValue": "Yes"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }

        @Test
        @DisplayName("(b) EMREQ=No prompt, no other resolution → error fires")
        void emreqNoPrompt_noOtherResolution_should_produceError() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-t16b",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                         "label": "Conditional order of discharge",
                         "defendantId": "d1", "offenceId": "off-1",
                         "prompts": [{"promptRef": "EMREQ", "promptValue": "No"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-EMON-017')].severity",
                            hasItem("ERROR")))
                    .andExpect(jsonPath(DR_EMON_AFFECTED, hasSize(1)))
                    .andExpect(jsonPath(DR_EMON_AFFECTED + ".offenceId", hasItem("off-1")))
                    .andExpect(jsonPath(DR_EMON_AFFECTED + ".message", hasItem(OFFENCE_LEVEL_MSG)));
        }

        @Test
        @DisplayName("(c) EMSO=Yes prompt → resolved")
        void emsoYesPrompt_should_resolveOffence() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-t16c",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "COEW", "category": "F",
                         "label": "Conditional order of discharge",
                         "defendantId": "d1", "offenceId": "off-1",
                         "prompts": [{"promptRef": "EMSO", "promptValue": "Yes"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }

        @ParameterizedTest(name = "continuing EM prompt {0}=Yes → no error")
        @ValueSource(strings = {"ELMONDET", "EMREQ", "EMREQV", "EMSO"})
        void eachContinuingPromptRef_withYesValue_should_produceNoErrors(final String promptRef)
                throws Exception {
            final String request = """
                    {
                      "hearingId": "h-emon-parameterized-prompt",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "category": "I",
                         "label": "Remand on conditional bail",
                         "defendantId": "d1", "offenceId": "off-1",
                         "prompts": [{"promptRef": "%s", "promptValue": "Yes"}]}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """.formatted(promptRef);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }

        @ParameterizedTest(name = "exempt main result {0} as sole category-I → no error")
        @ValueSource(strings = {"NHMC", "NHCC", "A", "ADJNN", "ADJAPP"})
        void eachExemptMainResultCode_asSoleCategoryI_should_produceNoErrors(final String shortCode)
                throws Exception {
            final String request = """
                    {
                      "hearingId": "h-emon-parameterized-exempt",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "%s", "category": "I",
                         "label": "Exempt result",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """.formatted(shortCode);

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }

        @Test
        @DisplayName("(d) NHMC as sole category-I result → exempt main result → resolved")
        void nhmcAsSoleCategoryI_should_resolveOffence() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-t16d",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "NHMC", "category": "I",
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

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }

        @Test
        @DisplayName("(d2) ADJNN as sole category-I result → exempt main result → resolved")
        void adjnnAsSoleCategoryI_should_resolveOffence() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-t16d2",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "ADJNN", "category": "I",
                         "label": "Adjournment no notice",
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

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }

        @Test
        @DisplayName("(e) Ancillary-only (ODIR, category A) → resolved")
        void ancillaryOnlyOdir_should_resolveOffence() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-t16e",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "ODIR", "category": "A",
                         "label": "Other directions",
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

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }

        @Test
        @DisplayName("(f) Mixed continue+end+blank across three offences → all resolved")
        void mixedContinueEndBlankThreeOffences_should_allResolve() throws Exception {
            String request = """
                    {
                      "hearingId": "h-emon-t16f",
                      "hearingDay": "2026-11-18",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "category": "I",
                         "label": "Remand on conditional bail",
                         "defendantId": "d1", "offenceId": "off-1",
                         "prompts": [{"promptRef": "ELMONDET", "promptValue": "Yes"}]},
                        {"resultLineId": "rl2", "shortCode": "EMONE",
                         "label": "Electronic Monitoring End",
                         "defendantId": "d1", "offenceId": "off-2"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "John", "lastName": "Smith"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft A", "orderIndex": 1,
                         "hasActiveElectronicMonitoring": true},
                        {"offenceId": "off-2", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft B", "orderIndex": 2,
                         "hasActiveElectronicMonitoring": true},
                        {"offenceId": "off-3", "defendantId": "d1", "offenceCode": "CD98001",
                         "offenceTitle": "Theft C", "orderIndex": 3,
                         "hasActiveElectronicMonitoring": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(DR_EMON_ERRORS, hasSize(0)));
        }
    }
}
