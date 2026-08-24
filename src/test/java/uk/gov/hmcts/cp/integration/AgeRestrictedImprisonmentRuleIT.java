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
 * End-to-end tests for DR-AGE-007 (imprisonment result age restriction) over the public validate
 * endpoint. Covers the graceful-degradation guarantee (Foundational phase), User Story 1 (no
 * false positive for defendants aged 21+), User Story 2 (blocking error + "This affects"
 * aggregation for under-21 defendants), and User Story 3 (correcting date of birth clears the
 * error).
 */
class AgeRestrictedImprisonmentRuleIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String RULE_ID = "DR-AGE-007";
    private static final String EXPECTED_BASE_MESSAGE =
            "The defendant is under 21 so cannot be sentenced to imprisonment. Amend the result or the date of birth.";
    private static final String EXPECTED_JAMIE_SMITH_AFFECTS_MESSAGE =
            EXPECTED_BASE_MESSAGE + " This affects: Jamie Smith.";

    @Nested
    @DisplayName("Graceful degradation on missing date of birth")
    class GracefulDegradation {

        @Test
        void missingDateOfBirth_shouldNotRaiseErrorAndOtherRulesStillEvaluate() throws Exception {
            String request = """
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

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-AGE-007')]", empty()))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SENT-001')]", hasSize(1)))
                    .andExpect(jsonPath("$.rulesEvaluated", hasItem(RULE_ID)));
        }
    }

    @Nested
    @DisplayName("User Story 1 - no error for defendants aged 21 or over")
    class Under21NotAffected {

        @Test
        void entersImprisonmentResult_defendantAged21OrOver_shouldNotRaiseError() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "2005-07-20"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-AGE-007')]", empty()));
        }

        @Test
        void entersExtivsOrSpeccResult_defendantWellOver21_shouldNotRaiseError() throws Exception {
            String request = """
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
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2", "firstName": "Alex", "lastName": "Jones",
                         "dateOfBirth": "1985-06-15"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 1}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-AGE-007')]", empty()));
        }

        @Test
        void entersSuspsOrSuspsnrResult_defendantWellOver21_shouldNotRaiseError() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "SUSPS", "label": "Suspended sentence",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "SUSPSNR", "label": "Suspended sentence not revoked",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "1990-01-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2", "firstName": "Alex", "lastName": "Jones",
                         "dateOfBirth": "1985-06-15"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 1}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-AGE-007')]", empty()));
        }
    }

    @Nested
    @DisplayName("User Story 2 - blocking error for under-21 defendants")
    class Under21Blocked {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"IMP", "EXTIVS", "SPECC", "SUSPS", "SUSPSNR"})
        void entersImprisonmentTypeResult_defendantUnder21_shouldRaiseBlockingError(final String shortCode)
                throws Exception {
            // Runs against the packaged DR-AGE-007.yaml, not a hard-coded list, so if any of the
            // five short codes were accidentally dropped from filterShortCodes this fails.
            String request = singleUnder21DefendantRequest(shortCode);

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].ruleId", is(RULE_ID)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].validationLevel", is("OFFENCE")))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            EXPECTED_JAMIE_SMITH_AFFECTS_MESSAGE)));
        }

        @Test
        void multipleQualifyingOffencesSameDefendant_shouldNameDefendantOnce() throws Exception {
            // rl2 carries isConcurrent=true purely so DR-SENT-001's custodial preprocessor
            // (which also treats IMP/EXTIVS as custodial short codes) sees relationship info on
            // both offences and stays silent — this test is isolating DR-AGE-007's own dedup
            // behaviour, not DR-SENT-001's.
            String request = """
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
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "2006-08-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].ruleId", is(RULE_ID)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences", hasSize(2)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[*].offenceId",
                            containsInAnyOrder("off1", "off2")))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            EXPECTED_JAMIE_SMITH_AFFECTS_MESSAGE)));
        }

        @Test
        void mixedAgeDefendants_shouldOnlyNameUnder21Defendant() throws Exception {
            String request = """
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
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "2006-08-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2", "firstName": "Alex", "lastName": "Jones",
                         "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].ruleId", is(RULE_ID)))
                    // off2 belongs to the 21+ defendant (d2) and must NOT appear here.
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            EXPECTED_JAMIE_SMITH_AFFECTS_MESSAGE)));
        }

        @Test
        void multipleUnder21Defendants_shouldNameAllInOneAggregatedError() throws Exception {
            String request = """
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
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "2006-08-01"},
                        {"defendantId": "d2", "masterDefendantId": "d2", "firstName": "Alex", "lastName": "Jones",
                         "dateOfBirth": "2007-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-AGE-007')]", hasSize(2)))
                    // One issue per defendant context, emitted in request order (d1 then d2) — each
                    // must carry only its own defendant's offence, not the other's.
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[1].affectedOffences[0].offenceId", is("off2")))
                    .andExpect(jsonPath("$.errors.errorMessages", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages[0]", is(
                            EXPECTED_BASE_MESSAGE + " This affects: Jamie Smith and Alex Jones.")));
        }
    }

    @Nested
    @DisplayName("User Story 3 - correcting date of birth clears the error")
    class DateOfBirthCorrection {

        @Test
        void correctingDateOfBirth_shouldClearPreviouslyRaisedError() throws Exception {
            performValidate(singleUnder21DefendantRequest())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-AGE-007')]", hasSize(1)));

            String correctedRequest = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "2005-07-20"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                      ]
                    }
                    """;

            performValidate(correctedRequest)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-AGE-007')]", empty()));
        }
    }

    private static String singleUnder21DefendantRequest() {
        return singleUnder21DefendantRequest("IMP");
    }

    private static String singleUnder21DefendantRequest(final String shortCode) {
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
                    {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2006-08-01"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """.formatted(shortCode, shortCode + " label");
    }

    private ResultActions performValidate(String request) throws Exception {
        return mockMvc.perform(post(VALIDATE_URL)
                .header("CJSCPPUID", "test-user")
                .header("CPP-ACTION", "validation-service.validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }
}
