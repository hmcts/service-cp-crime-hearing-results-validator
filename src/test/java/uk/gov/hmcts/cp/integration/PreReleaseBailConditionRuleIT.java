package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
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
 * End-to-end integration tests for DR-BAIL-015 (Pre-release bail conditions on a conditional bail
 * result) over the public validate endpoint.
 *
 * <p>Covers AC1 (adult + remand bail → Error A), AC2 (youth + remand bail → Error B), AC2A (CCIC
 * → Error D), AC2B (CCSC → Error E), AC3 (multi-defendant "This affects" clause), AC4 (mixed
 * hearing → Error C summary with age-specific inline), AC4A (multiple parent types), AC5A
 * (coexists with other rule errors), AC7 (cleared when corrected), AC8 (not triggered), AC9
 * (no DOB → Error C). All 16 pre-release bail condition short codes are individually tested
 * against each triggering parent type (RC for Error A, REMCBY for Error B, CCIC for Error D,
 * CCSC for Error E) via parameterised tests in their respective {@code @Nested} classes.
 *
 * <p>Hearing date used throughout: {@code 2026-06-15}.
 * Adult DOB: {@code 1998-01-01} (28 years old). Youth DOB: {@code 2012-01-01} (14 years old).
 */
class PreReleaseBailConditionRuleIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String RULE_ID = "DR-BAIL-015";

    // Error A — adult, remand bail
    private static final String ERROR_A =
            "A conditional bail result cannot have pre-release bail conditions. "
            + "If the court ordered pre-release conditions, use 'Remanded in custody with "
            + "bail direction (RIB)' instead. If the conditions apply after release, remove "
            + "the pre-release conditions and enter them as bail conditions.";

    // Error B — youth, remand bail
    private static final String ERROR_B =
            "A conditional bail result cannot have pre-release bail conditions. "
            + "If the court ordered pre-release conditions, use 'Remand in care of Local "
            + "Authority with bail direction (RILAB)' instead. If the conditions apply after "
            + "release, remove the pre-release conditions and enter them as bail conditions.";

    // Error C — mixed or no-DOB, remand bail
    private static final String ERROR_C =
            "A conditional bail result cannot have pre-release bail conditions. "
            + "If the court ordered pre-release conditions, use 'Remanded in custody with "
            + "bail direction (RIB)' for an adult or 'Remand in care of Local Authority with "
            + "bail direction (RILAB)' for a youth instead. If the conditions apply after "
            + "release, remove the pre-release conditions and enter them as bail conditions.";

    // Error D — CCIC
    private static final String ERROR_D =
            "A conditional bail result cannot have pre-release bail conditions. "
            + "If the court ordered pre-release conditions, use 'Sent to Crown Court in "
            + "custody for trial with bail direction (CCIIB)' instead. If the conditions "
            + "apply after release, remove the pre-release conditions and enter them as "
            + "bail conditions.";

    // Error E — CCSC
    private static final String ERROR_E =
            "A conditional bail result cannot have pre-release bail conditions. "
            + "If the court ordered pre-release conditions, use 'Committed to Crown Court "
            + "for sentence - in custody with bail direction (CCSIB)' instead. If the "
            + "conditions apply after release, remove the pre-release conditions and enter "
            + "them as bail conditions.";

    private ResultActions performValidate(final String body) throws Exception {
        return mockMvc.perform(post(VALIDATE_URL)
                .header("CJSCPPUID", "test-user")
                .header("CPP-ACTION", "validation-service.validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // ------------------------------------------------------------------ AC1
    @Nested
    @DisplayName("AC1 — Adult defendant with remand bail parent raises Error A")
    class Ac1AdultRemandBail {

        @Test
        void adultDefendant_rcParent_prebcChild_shouldRaiseErrorA() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "Remanded on conditional bail",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "Pre-release bail conditions",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1998-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0001"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].validationLevel", is("OFFENCE")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_A)))
                    // single defendant → no "This affects" clause in summary
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_A)))
                    .andExpect(jsonPath("$.rulesEvaluated", hasItem(RULE_ID)));
        }

        @Test
        void adultDefendant_rcbvParent_prbco_shouldRaiseErrorA() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RCBV", "label": "Variation of bail conditions",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCO", "label": "Pre-release: Other",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Alice", "lastName": "Jones", "dateOfBirth": "1990-05-20"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0002"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_A)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_A)));
        }

        @Test
        void adultDefendant_remcbParent_prbcp1_shouldRaiseErrorA() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-g1a",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "REMCB", "label": "REMCB",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCP1", "label": "PRBCP1",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Tom", "lastName": "Hardy", "dateOfBirth": "1998-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0016"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_A)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_A)));
        }

        @Test
        void adultDefendant_rcccbParent_prbcp2_shouldRaiseErrorA() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-g1b",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RCCCB", "label": "RCCCB",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCP2", "label": "PRBCP2",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Laura", "lastName": "Wells", "dateOfBirth": "1998-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0017"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_A)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_A)));
        }

        @Test
        void adultDefendant_bailrevcParent_prbcp3_shouldRaiseErrorA() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-g1c",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "BAILREVC", "label": "BAILREVC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCP3",  "label": "PRBCP3",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Mark", "lastName": "Evans", "dateOfBirth": "1998-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0036"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_A)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_A)));
        }
    }

    // ------------------------------------------------------------------ AC2
    @Nested
    @DisplayName("AC2 — Youth defendant with remand bail parent raises Error B")
    class Ac2YouthRemandBail {

        @Test
        void youthDefendant_remcbyParent_prebcChild_shouldRaiseErrorB() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "REMCBY", "label": "Youth remittal conditional bail",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "Pre-release bail conditions",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Jamie", "lastName": "Taylor", "dateOfBirth": "2012-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0003"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_B)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_B)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"RC", "RCBV", "BAILREVC", "REMCB", "REMCBY", "RCCCB"})
        void youthDefendant_allRemandBailParents_shouldRaiseErrorB(final String parentCode)
                throws Exception {
            final String request = """
                    {
                      "hearingId": "h-g2-%s",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "%s", "label": "%s",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0018"}
                      ]
                    }
                    """.formatted(parentCode, parentCode, parentCode);

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_B)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_B)));
        }

        @ParameterizedTest(name = "{0} with REMCBY parent and youth defendant raises Error B")
        @ValueSource(strings = {
            "PREBC", "PRBCO", "PRBCP1", "PRBCP2", "PRBCP3", "PRBCP4", "PRBCP5", "PRBCP6",
            "PRBCP7", "PRBCP8", "PRBCS1", "PRBCS2", "PRBCS3", "PRBCSE1", "PRBCSE2", "PRBCT"})
        void allPreReleaseCodes_withRemcbyParentAndYouth_shouldRaiseErrorB(final String preReleaseCode)
                throws Exception {
            final String request = """
                    {
                      "hearingId": "h-ac2-youth-%s",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "REMCBY", "label": "REMCBY",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "%s", "label": "%s",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Jamie", "lastName": "Taylor", "dateOfBirth": "2012-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0033"}
                      ]
                    }
                    """.formatted(preReleaseCode, preReleaseCode, preReleaseCode);

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_B)));
        }
    }

    // ------------------------------------------------------------------ AC2A
    @Nested
    @DisplayName("AC2A — CCIC parent with pre-release child raises Error D")
    class Ac2aCcic {

        @Test
        void ccicParent_withPreRelease_shouldRaiseErrorD() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCIC", "label": "Sent to Crown Court for trial on bail",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCS1", "label": "Pre-release: Surety",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Sam", "lastName": "Patel", "dateOfBirth": "1985-03-10"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0004"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_D)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_D)));
        }

        @Test
        void ccicParent_youthDefendant_shouldRaiseErrorD() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-g3",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCIC", "label": "CCIC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCS2", "label": "PRBCS2",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Jamie", "lastName": "Taylor", "dateOfBirth": "2012-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0019"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    // CCIC condition fires regardless of age → Error D not Error B
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_D)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_D)));
        }

        @Test
        void ccicParent_noDobDefendant_shouldRaiseErrorD() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-ac2a-nodob",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCIC", "label": "CCIC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCSE2", "label": "PRBCSE2",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Unknown", "lastName": "Person"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0031"}
                      ]
                    }
                    """;

            // CCIC fires regardless of age/DOB → Error D even with no date of birth
            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_D)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_D)));
        }

        @ParameterizedTest(name = "{0} with CCIC parent raises Error D")
        @ValueSource(strings = {
            "PREBC", "PRBCO", "PRBCP1", "PRBCP2", "PRBCP3", "PRBCP4", "PRBCP5", "PRBCP6",
            "PRBCP7", "PRBCP8", "PRBCS1", "PRBCS2", "PRBCS3", "PRBCSE1", "PRBCSE2", "PRBCT"})
        void allPreReleaseCodes_withCcicParent_shouldRaiseErrorD(final String preReleaseCode)
                throws Exception {
            final String request = """
                    {
                      "hearingId": "h-ac2a-all-%s",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCIC", "label": "CCIC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "%s", "label": "%s",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Sam", "lastName": "Patel", "dateOfBirth": "1985-03-10"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0034"}
                      ]
                    }
                    """.formatted(preReleaseCode, preReleaseCode, preReleaseCode);

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_D)));
        }
    }

    // ------------------------------------------------------------------ AC2B
    @Nested
    @DisplayName("AC2B — CCSC parent with pre-release child raises Error E")
    class Ac2bCcsc {

        @Test
        void ccscParent_withPreRelease_shouldRaiseErrorE() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCSC", "label": "Committed to Crown Court for sentence on bail",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCT", "label": "Pre-release: Telephone number",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Chris", "lastName": "Brown", "dateOfBirth": "1995-07-22"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0005"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_E)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_E)));
        }

        @Test
        void ccscParent_youthDefendant_shouldRaiseErrorE() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-g4",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCSC", "label": "CCSC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCS3", "label": "PRBCS3",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Jamie", "lastName": "Taylor", "dateOfBirth": "2012-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0020"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    // CCSC condition fires regardless of age → Error E not Error B
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_E)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_E)));
        }

        @Test
        void ccscParent_noDobDefendant_shouldRaiseErrorE() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-ac2b-nodob",
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
                         "firstName": "Unknown", "lastName": "Person"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0032"}
                      ]
                    }
                    """;

            // CCSC fires regardless of age/DOB → Error E even with no date of birth
            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_E)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_E)));
        }

        @ParameterizedTest(name = "{0} with CCSC parent raises Error E")
        @ValueSource(strings = {
            "PREBC", "PRBCO", "PRBCP1", "PRBCP2", "PRBCP3", "PRBCP4", "PRBCP5", "PRBCP6",
            "PRBCP7", "PRBCP8", "PRBCS1", "PRBCS2", "PRBCS3", "PRBCSE1", "PRBCSE2", "PRBCT"})
        void allPreReleaseCodes_withCcscParent_shouldRaiseErrorE(final String preReleaseCode)
                throws Exception {
            final String request = """
                    {
                      "hearingId": "h-ac2b-all-%s",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCSC", "label": "CCSC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "%s", "label": "%s",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Chris", "lastName": "Brown", "dateOfBirth": "1995-07-22"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0035"}
                      ]
                    }
                    """.formatted(preReleaseCode, preReleaseCode, preReleaseCode);

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_E)));
        }
    }

    // ------------------------------------------------------------------ AC3
    @Nested
    @DisplayName("AC3 — Multi-defendant: 'This affects' names affected defendants")
    class Ac3MultiDefendant {

        @Test
        void twoAdultDefendantsBothTriggered_errorSummaryNamesBoth() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "RC", "label": "RC",
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
                         "offenceTitle": "Theft 1", "orderIndex": 1, "caseUrn": "32AH0006"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 2", "orderIndex": 2, "caseUrn": "32AH0006"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERROR_A + " This affects: John Smith and Jane Jones.")));
        }

        @Test
        void twoDefendantsOnlyOneTriggers_summaryNamesOnlyAffected() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "IMP",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Jane", "lastName": "Jones", "dateOfBirth": "1988-03-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0007"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "AS001",
                         "offenceTitle": "Assault", "orderIndex": 2, "caseUrn": "32AH0007"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERROR_A + " This affects: John Smith.")));
        }

        @Test
        void threeAdultDefendantsAllTriggered_errorSummaryNamesAllThree() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-g5",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "RC", "label": "RC",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl5", "shortCode": "RC", "label": "RC",
                         "defendantId": "d3", "offenceId": "off3"},
                        {"resultLineId": "rl6", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d3", "offenceId": "off3"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Jane", "lastName": "Jones", "dateOfBirth": "1992-06-15"},
                        {"defendantId": "d3", "masterDefendantId": "d3",
                         "firstName": "Chris", "lastName": "Brown", "dateOfBirth": "1995-03-20"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 1", "orderIndex": 1, "caseUrn": "32AH0021"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 2", "orderIndex": 2, "caseUrn": "32AH0021"},
                        {"offenceId": "off3", "defendantId": "d3", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 3", "orderIndex": 3, "caseUrn": "32AH0021"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(3)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERROR_A + " This affects: John Smith, Jane Jones and Chris Brown.")));
        }
    }

    // ------------------------------------------------------------------ AC4
    @Nested
    @DisplayName("AC4 — Mixed adult+youth: Error C summary with age-specific inline")
    class Ac4MixedHearing {

        @Test
        void adultAndYouthBothTriggered_singleErrorCSummaryWithBothNames() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "REMCBY", "label": "REMCBY",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "PRBCO", "label": "PRBCO",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Jamie", "lastName": "Taylor", "dateOfBirth": "2012-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 1", "orderIndex": 1, "caseUrn": "32AH0008"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 2", "orderIndex": 2, "caseUrn": "32AH0008"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    // Two inline issues: one per triggered offence
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(2)))
                    // Inline for adult → Error A
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.affectedOffences[0].offenceId=='off1')].affectedOffences[0].message",
                            hasItem(ERROR_A)))
                    // Inline for youth → Error B
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.affectedOffences[0].offenceId=='off2')].affectedOffences[0].message",
                            hasItem(ERROR_B)))
                    // One combined Error C summary with both names
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]",
                            is(ERROR_C + " This affects: John Smith and Jamie Taylor.")));
        }
    }

    // ------------------------------------------------------------------ AC4A
    @Nested
    @DisplayName("AC4A — Multiple parent types produce separate summary entries")
    class Ac4aMultipleParentTypes {

        @Test
        void remandBailAndCcicBothTrigger_twoSeparateSummaryEntries() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "CCIC", "label": "CCIC",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "PRBCO", "label": "PRBCO",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Sam", "lastName": "Patel", "dateOfBirth": "1985-05-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 1", "orderIndex": 1, "caseUrn": "32AH0009"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 2", "orderIndex": 2, "caseUrn": "32AH0009"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERROR_A + " This affects: John Smith.",
                                    ERROR_D + " This affects: Sam Patel.")));
        }

        @Test
        void remandBailAndCcscBothTrigger_twoSeparateSummaryEntries() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-g12",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "CCSC", "label": "CCSC",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "PRBCT", "label": "PRBCT",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Jane", "lastName": "Doe", "dateOfBirth": "1988-07-14"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 1", "orderIndex": 1, "caseUrn": "32AH0030"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 2", "orderIndex": 2, "caseUrn": "32AH0030"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERROR_A + " This affects: John Smith.",
                                    ERROR_E + " This affects: Jane Doe.")));
        }

        @Test
        void allThreeVariants_threeSummaryEntries() throws Exception {
            // d1 RC+PREBC → Error A, d2 CCIC+PRBCO → Error D, d3 CCSC+PRBCT → Error E
            final String request = """
                    {
                      "hearingId": "h-ac4a-three",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",   "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC","label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "CCIC", "label": "CCIC",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "PRBCO","label": "PRBCO",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl5", "shortCode": "CCSC", "label": "CCSC",
                         "defendantId": "d3", "offenceId": "off3"},
                        {"resultLineId": "rl6", "shortCode": "PRBCT","label": "PRBCT",
                         "defendantId": "d3", "offenceId": "off3"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John",  "lastName": "Smith",  "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Sam",   "lastName": "Patel",  "dateOfBirth": "1985-05-01"},
                        {"defendantId": "d3", "masterDefendantId": "d3",
                         "firstName": "Chris", "lastName": "Brown",  "dateOfBirth": "1995-07-22"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 1", "orderIndex": 1, "caseUrn": "32AH0033"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 2", "orderIndex": 2, "caseUrn": "32AH0033"},
                        {"offenceId": "off3", "defendantId": "d3", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 3", "orderIndex": 3, "caseUrn": "32AH0033"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(3)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(3)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERROR_A + " This affects: John Smith.",
                                    ERROR_D + " This affects: Sam Patel.",
                                    ERROR_E + " This affects: Chris Brown.")));
        }

        @Test
        void sameDefendantDifferentOffences_remandAndCcsc_twoSummaryEntries() throws Exception {
            // Single adult defendant: RC+PREBC on off1 fires Error A, CCSC+PRBCO on off2 fires Error E.
            final String request = """
                    {
                      "hearingId": "h-ac4a-samedef",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",   "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC","label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "CCSC", "label": "CCSC",
                         "defendantId": "d1", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "PRBCO","label": "PRBCO",
                         "defendantId": "d1", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 1", "orderIndex": 1, "caseUrn": "32AH0034"},
                        {"offenceId": "off2", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 2", "orderIndex": 2, "caseUrn": "32AH0034"}
                      ]
                    }
                    """;

            // Single defendant: no "This affects:" suffix (appended only when hearing has > 1 defendant)
            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_A, ERROR_E)));
        }
    }

    // ------------------------------------------------------------------ AC5A
    @Nested
    @DisplayName("AC5A — Coexists with other rule errors without suppression")
    class Ac5aAlongsideOtherErrors {

        @Test
        void drBail015AndDrSent001BothTriggered_neitherSuppressed() throws Exception {
            // DR-SENT-001 fires a WARNING when a defendant has two or more custodial results and
            // one of them has both isConcurrent=true AND consecutiveToOffence set simultaneously
            // (hasBothCount > 0). DR-BAIL-015 fires an ERROR on the separate RC+PREBC offence.
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "IMP",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "IMP", "label": "IMP",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0010"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "AS001",
                         "offenceTitle": "Assault", "orderIndex": 2, "caseUrn": "32AH0010"},
                        {"offenceId": "off3", "defendantId": "d2", "offenceCode": "RS001",
                         "offenceTitle": "Other", "orderIndex": 3, "caseUrn": "32AH0010"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SENT-001')]", hasSize(1)));
        }

        @Test
        void drBail015AndDrAge007BothErrors_bothInErrorSummary() throws Exception {
            // d1 age 20 on hearing day (DOB 2006-01-01): adult for DR-BAIL-015 (≥18),
            // under-21 for DR-AGE-007. RC+PREBC on off1 fires both separately.
            final String request = """
                    {
                      "hearingId": "h-ac5a-age007",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",  "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC","label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Young", "lastName": "Adult", "dateOfBirth": "2006-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0035"},
                        {"offenceId": "off2", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 2", "orderIndex": 2, "caseUrn": "32AH0035"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-AGE-007')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasItem(ERROR_A)));
        }
    }

    // ------------------------------------------------------------------ AC7
    @Nested
    @DisplayName("AC7 — Error cleared when pre-release child is removed")
    class Ac7ClearedWhenCorrected {

        @Test
        void correctedToRib_shouldClearError() throws Exception {
            // First confirm the error fires
            performValidate("""
                    {
                      "hearingId": "h1",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0011"}
                      ]
                    }
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)));

            // Then confirm the corrected request (parent changed to RIB) clears the error
            performValidate("""
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RIB", "label": "Remanded in custody with bail direction",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0011"}
                      ]
                    }
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", empty()));
        }

        @Test
        void youthCorrectedToRilab_shouldClearError() throws Exception {
            performValidate("""
                    {
                      "hearingId": "h-g6a",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0022"}
                      ]
                    }
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)));

            performValidate("""
                    {
                      "hearingId": "h-g6a",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RILAB", "label": "RILAB",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0022"}
                      ]
                    }
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", empty()));
        }

        @Test
        void ccicCorrectedToCciib_shouldClearError() throws Exception {
            performValidate("""
                    {
                      "hearingId": "h-g6b",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0023"}
                      ]
                    }
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)));

            performValidate("""
                    {
                      "hearingId": "h-g6b",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCIIB", "label": "CCIIB",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0023"}
                      ]
                    }
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", empty()));
        }

        @Test
        void ccscCorrectedToCcsib_shouldClearError() throws Exception {
            performValidate("""
                    {
                      "hearingId": "h-g6c",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0024"}
                      ]
                    }
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)));

            performValidate("""
                    {
                      "hearingId": "h-g6c",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "CCSIB", "label": "CCSIB",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0024"}
                      ]
                    }
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", empty()));
        }

        @Test
        void preReleaseChildRemoved_shouldClearError() throws Exception {
            // First confirm RC+PREBC fires
            performValidate("""
                    {
                      "hearingId": "h-ac7-removechild",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",   "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC","label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0036"}
                      ]
                    }
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)));

            // Then confirm the pre-release child removed (RC kept) clears the error
            performValidate("""
                    {
                      "hearingId": "h-ac7-removechild",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0036"}
                      ]
                    }
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", empty()));
        }
    }

    // ------------------------------------------------------------------ AC8
    @Nested
    @DisplayName("AC8 — Error not shown when condition is not met")
    class Ac8NotTriggered {

        @Test
        void nonConditionalBailParent_shouldNotTrigger() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0012"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", empty()));
        }

        @Test
        void preReleaseOnDifferentOffence_shouldNotTrigger() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0013"},
                        {"offenceId": "off2", "defendantId": "d1", "offenceCode": "AS001",
                         "offenceTitle": "Assault", "orderIndex": 2, "caseUrn": "32AH0013"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", empty()));
        }

        @Test
        void conditionalBailParentWithNoPrereleaseChild_shouldNotTrigger() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0014"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", empty()));
        }

        @ParameterizedTest(name = "{0} parent with PREBC child does not trigger")
        @ValueSource(strings = {
            "RIB", "RILAB", "CCIIB", "CCSIB", "UB", "RICD", "E1CB", "E1CONCB", "E2CB"})
        void nonConditionalBailParentCodes_withPreReleaseChild_shouldNotTrigger(
                final String parentCode) throws Exception {
            final String request = """
                    {
                      "hearingId": "h-ac8-%s",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "%s", "label": "%s",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0025"}
                      ]
                    }
                    """.formatted(parentCode, parentCode, parentCode);

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", empty()));
        }
    }

    // ------------------------------------------------------------------ AC9
    @Nested
    @DisplayName("AC9 — Defendant with no date of birth raises Error C")
    class Ac9NoDob {

        @Test
        void noDob_remandBailWithPreRelease_shouldRaiseErrorC() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "BAILREVC", "label": "Bail reviewed",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PRBCSE1", "label": "Pre-release: Security - lodge",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "Unknown", "lastName": "Person"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0015"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_C)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(ERROR_C)));
        }

        @Test
        void adultAndNoDobDefendants_bothTriggered_separateErrorVariants() throws Exception {
            // d1 adult → Error A; d2 no DOB → Error C. No-DOB does not flip to AC4 (mixed) path.
            final String request = """
                    {
                      "hearingId": "h-ac9-multi",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",   "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC","label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "RC",   "label": "RC",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl4", "shortCode": "PREBC","label": "PREBC",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John",    "lastName": "Smith", "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2",
                         "firstName": "Unknown", "lastName": "Person"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 1", "orderIndex": 1, "caseUrn": "32AH0037"},
                        {"offenceId": "off2", "defendantId": "d2", "offenceCode": "TH68001",
                         "offenceTitle": "Theft 2", "orderIndex": 2, "caseUrn": "32AH0037"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages",
                            containsInAnyOrder(
                                    ERROR_A + " This affects: John Smith.",
                                    ERROR_C + " This affects: Unknown Person.")));
        }
    }

    // ------------------------------------------------------------------ All pre-release codes
    @Nested
    @DisplayName("All pre-release codes — each triggers Error A when paired with RC parent (adult)")
    class AllPreReleaseCodes {

        @ParameterizedTest
        @ValueSource(strings = {
            "PREBC", "PRBCO", "PRBCP1", "PRBCP2", "PRBCP3", "PRBCP4", "PRBCP5", "PRBCP6",
            "PRBCP7", "PRBCP8", "PRBCS1", "PRBCS2", "PRBCS3", "PRBCSE1", "PRBCSE2", "PRBCT"})
        void allPreReleaseCodes_withRcParentAndAdult_shouldRaiseErrorA(final String preReleaseCode)
                throws Exception {
            final String request = """
                    {
                      "hearingId": "h-g8-%s",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC", "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "%s", "label": "%s",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1998-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0026"}
                      ]
                    }
                    """.formatted(preReleaseCode, preReleaseCode, preReleaseCode);

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_A)));
        }
    }

    // ------------------------------------------------------------------ Multiple pre-release children
    @Nested
    @DisplayName("Multiple pre-release children — exactly one issue per offence")
    class MultiplePreReleaseChildren {

        @Test
        void multiplePreReleaseCodesOnSameOffence_shouldProduceOneIssue() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-g11",
                      "hearingDay": "2026-06-15",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "RC",    "label": "RC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "PREBC", "label": "PREBC",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl3", "shortCode": "PRBCS1","label": "PRBCS1",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl4", "shortCode": "PRBCP1","label": "PRBCP1",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1",
                         "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "defendantId": "d1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0029"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='" + RULE_ID + "')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(ERROR_A)));
        }
    }

    // ------------------------------------------------------------------ Missing hearingDay
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
        void validate_should_return_400_when_hearing_day_is_absent() throws Exception {
            final String request = """
                    {
                      "hearingId": "bail-null-day",
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
                         "offenceTitle": "Theft", "orderIndex": 1, "caseUrn": "32AH0030"}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isBadRequest());
        }
    }
}
