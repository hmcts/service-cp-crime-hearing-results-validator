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
 * End-to-end tests for DR-APP-009 (application result recorded against an offence) over the
 * public validate endpoint. Covers the graceful-degradation guarantee (Foundational phase),
 * User Story 1 (single breach), User Story 2 (multiple breaches reported in one pass, including
 * the defendant-name dedup fix from research.md R8), and User Story 3 (the amendment path).
 */
class ApplicationResultOffenceRuleIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String RULE_ID = "DR-APP-009";

    @Nested
    @DisplayName("Graceful degradation on non-breaching result lines")
    class GracefulDegradation {

        @Test
        void nonBreachingResultLine_shouldNotRaiseErrorAndOtherRulesStillEvaluate() throws Exception {
            // rl1/rl2 are plain IMP results with no relationship info -- shaped to trigger
            // DR-SENT-001's AC3 (both concurrent and consecutive) WARNING, mirroring the exact
            // convention used by AgeRestrictedImprisonmentRuleIT's own graceful-degradation test.
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
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", empty()))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SENT-001')]", hasSize(1)))
                    .andExpect(jsonPath("$.rulesEvaluated", hasItem(RULE_ID)));
        }
    }

    @Nested
    @DisplayName("User Story 1 - single application result against an offence")
    class SingleBreach {

        @Test
        void applicationOnlyResultAgainstOffence_singleDefendant_shouldRaiseBlockingErrorWithNoAffectsClause()
                throws Exception {
            performValidate(singleDefendantSingleBreachRequest("LAWD", "Legal Aid Withdrawn"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].validationLevel", is("OFFENCE")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].offenceId", is("off1")))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(
                            "Remove Legal Aid Withdrawn from this offence. It is an application result, "
                                    + "so it can only be added to an application.")))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            "Legal Aid Withdrawn is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing.")));
        }

        @Test
        void applicationOnlyResultAgainstOffence_multipleDefendants_shouldNameAffectedDefendant()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d2", "offenceId": "off2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"},
                        {"defendantId": "d2", "masterDefendantId": "d2", "firstName": "Alex", "lastName": "Jones"}
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
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            "Legal Aid Withdrawn is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing. "
                                    + "This affects: Jamie Smith.")));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"ARBSFG", "ARBSPG", "ARBSR", "VT", "G", "LAREP", "LAREPCC", "ORDC",
            "LATG", "LATR", "LAWD", "RFSD", "SMAR"})
        void applicationOnlyResultAgainstOffence_forEachCodeInScope_shouldRaiseError(final String shortCode)
                throws Exception {
            // Runs against the packaged DR-APP-009.yaml, not a hard-coded list, so if any of the
            // thirteen codes were accidentally dropped from applicationOnlyShortCodes this fails.
            String label = shortCode + " label";
            performValidate(singleDefendantSingleBreachRequest(shortCode, label))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            label + " is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing.")));
        }

        @Test
        void validResultAgainstOffence_shouldNotRaiseError() throws Exception {
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
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", empty()));
        }

        @Test
        void removingTheBreachingResult_shouldClearThePreviouslyRaisedError() throws Exception {
            performValidate(singleDefendantSingleBreachRequest("LAWD", "Legal Aid Withdrawn"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(1)));

            String correctedRequest = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                      ]
                    }
                    """;

            performValidate(correctedRequest)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", empty()));
        }

        @Test
        void mixedApplicationOnlyAndValidResultsOnSameOffence_shouldRaiseErrorOnlyForApplicationOnlyResult()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].affectedOffences[0].message", is(
                            "Remove Legal Aid Withdrawn from this offence. It is an application result, "
                                    + "so it can only be added to an application.")));
        }
    }

    @Nested
    @DisplayName("User Story 2 - multiple application results against offences")
    class MultipleBreaches {

        @Test
        void multipleApplicationOnlyResultsOnSameOffence_shouldRaiseSeparateInlineErrorPerResult()
                throws Exception {
            String request = twoBreachesOnSameOffenceRequest();

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(2)))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-APP-009')].affectedOffences[0].message",
                            containsInAnyOrder(
                                    "Remove Legal Aid Withdrawn from this offence. It is an application "
                                            + "result, so it can only be added to an application.",
                                    "Remove Application refused from this offence. It is an application "
                                            + "result, so it can only be added to an application.")))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            "Legal Aid Withdrawn is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing.",
                            "Application refused is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing.")));
        }

        @Test
        void applicationOnlyResultsAcrossMultipleOffencesSameDefendant_shouldRaiseErrorPerOffence()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "RFSD", "label": "Application refused",
                         "defendantId": "d1", "offenceId": "off2"}
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
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(2)))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-APP-009')].affectedOffences[0].offenceId",
                            containsInAnyOrder("off1", "off2")))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            "Legal Aid Withdrawn is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing.",
                            "Application refused is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing.")));
        }

        @Test
        void applicationOnlyResultsAcrossMultipleDefendants_shouldListAllAffectedDefendantsCommaSeparated()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                         "defendantId": "d2", "offenceId": "off2"},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d3", "offenceId": "off3"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"},
                        {"defendantId": "d2", "masterDefendantId": "d2", "firstName": "Alex", "lastName": "Jones"},
                        {"defendantId": "d3", "masterDefendantId": "d3", "firstName": "Sam", "lastName": "Lee"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2},
                        {"offenceId": "off3", "offenceCode": "BU001", "offenceTitle": "Burglary", "orderIndex": 3}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(2)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            "Legal Aid Withdrawn is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing. "
                                    + "This affects: Jamie Smith and Alex Jones.")));
        }

        @Test
        void partiallyResolvedBreaches_shouldStillBlockOnRemainingBreach() throws Exception {
            performValidate(twoBreachesOnSameOffenceRequest())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(2)));

            String partiallyResolved = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl2", "shortCode": "RFSD", "label": "Application refused",
                         "defendantId": "d1", "offenceId": "off1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                      ]
                    }
                    """;

            performValidate(partiallyResolved)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            "Application refused is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing.")));
        }

        @Test
        void allBreachesResolved_shouldClearAllApplicationResultOffenceErrors() throws Exception {
            performValidate(twoBreachesOnSameOffenceRequest())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(2)));

            String allResolved = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                      ]
                    }
                    """;

            performValidate(allResolved)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", empty()));
        }

        /**
         * End-to-end regression test for research.md R8 (identified during a second
         * /speckit.analyze pass): the same defendant, same breaching label, on two different
         * offences must collapse to a single occurrence of that defendant's name in the
         * aggregated page-level "This affects" text, not two.
         */
        @Test
        void sameApplicationOnlyCodeAcrossTwoOffencesSameDefendant_shouldNameDefendantOnceInAggregatedMessage()
                throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-07-20",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                         "defendantId": "d1", "offenceId": "off1"},
                        {"resultLineId": "rl2", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                         "defendantId": "d1", "offenceId": "off2"},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d2", "offenceId": "off3"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"},
                        {"defendantId": "d2", "masterDefendantId": "d2", "firstName": "Alex", "lastName": "Jones"}
                      ],
                      "offences": [
                        {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1},
                        {"offenceId": "off2", "offenceCode": "AS001", "offenceTitle": "Assault", "orderIndex": 2},
                        {"offenceId": "off3", "offenceCode": "BU001", "offenceTitle": "Burglary", "orderIndex": 3}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(2)))
                    .andExpect(jsonPath(
                            "$.errors.validationIssues[?(@.ruleId=='DR-APP-009')].affectedOffences[0].offenceId",
                            containsInAnyOrder("off1", "off2")))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            "Legal Aid Withdrawn is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing. "
                                    + "This affects: Jamie Smith.")));
        }
    }

    @Nested
    @DisplayName("User Story 3 - amendment of an already-shared hearing")
    class Amendment {

        @Test
        void amendmentRequestWithApplicationOnlyResultAgainstOffence_shouldRaiseSameBlockingError()
                throws Exception {
            // This service does not special-case "amendment" differently from any other
            // validation request -- DraftValidationRequest carries no explicit is-amendment
            // flag, so an amendment is submitted through the same /validate endpoint and
            // payload shape as an original share (see research.md/plan.md Technical Context).
            performValidate(singleDefendantSingleBreachRequest("LAWD", "Legal Aid Withdrawn"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(false)))
                    .andExpect(jsonPath("$.errors.validationIssues[?(@.ruleId=='DR-APP-009')]", hasSize(1)))
                    .andExpect(jsonPath("$.errors.validationIssues[0].severity", is("ERROR")))
                    .andExpect(jsonPath("$.errors.errorMessages", containsInAnyOrder(
                            "Legal Aid Withdrawn is an application result. It cannot be added to an offence. "
                                    + "Remove it from the offence and add an application to the hearing.")));
        }
    }

    private static String singleDefendantSingleBreachRequest(final String shortCode, final String label) {
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
                    {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """.formatted(shortCode, label);
    }

    private static String twoBreachesOnSameOffenceRequest() {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-07-20",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "LAWD", "label": "Legal Aid Withdrawn",
                     "defendantId": "d1", "offenceId": "off1"},
                    {"resultLineId": "rl2", "shortCode": "RFSD", "label": "Application refused",
                     "defendantId": "d1", "offenceId": "off1"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "masterDefendantId": "d1", "firstName": "Jamie", "lastName": "Smith"}
                  ],
                  "offences": [
                    {"offenceId": "off1", "offenceCode": "TH68001", "offenceTitle": "Theft", "orderIndex": 1}
                  ]
                }
                """;
    }

    private ResultActions performValidate(String request) throws Exception {
        return mockMvc.perform(post(VALIDATE_URL)
                .header("CJSCPPUID", "test-user")
                .header("CPP-ACTION", "validation-service.validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }
}
