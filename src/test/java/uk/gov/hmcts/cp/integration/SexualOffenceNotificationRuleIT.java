package uk.gov.hmcts.cp.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end tests for DR-SEX-008 (sexual offence notification requirement) over the public
 * validate endpoint. Covers User Story 1 (Adult warning), User Story 2 (Youth warning), the
 * reference-data fail-open contract and caching behaviour, and User Story 3 (this rule's warning
 * coexisting with another rule's defendant-level warning).
 *
 * <p>Every test uses a distinct {@code offenceId} — the {@code referencedataOffences} Caffeine
 * cache is keyed by {@code offenceId} and lives on the shared Spring context across test methods
 * (and across other IT classes reusing the same context), so reusing an id across tests stubbing
 * different {@code misCode} responses would read a stale cached value instead of the new stub.
 */
class SexualOffenceNotificationRuleIT extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String RULE_ID = "DR-SEX-008";
    private static final String ADULT_MESSAGE =
            "This offence does not have a sexual offences notification requirement (NORRR). "
                    + "Check if this is required before sharing";
    private static final String YOUTH_MESSAGE =
            "This offence does not have a sexual offences notification requirement "
                    + "(NORRR - defendant or NORPGP - parent and defendant). Check if this is required before sharing";

    @Nested
    @DisplayName("User Story 1 - Adult")
    class AdultWarningScenarios {

        @Test
        void convictedSexOffence_adultMissingNorrr_shouldRaiseWarning() throws Exception {
            stubReferencedataOffenceResponse("sex-adult-missing", "SEX");
            String request = adultRequest("sex-adult-missing", true, "IMP");

            // Only DR-SEX-008 can fire against this minimal payload, so plain positional
            // indexing is safe and avoids chaining a filter predicate with a further index
            // (`[?(...)][0].nested`), which JsonPathExpectationsHelper does not resolve the way
            // a naive reading suggests — it silently evaluates to an empty match rather than
            // erroring, so prefer either a bare filter (existence/size) or a bare index, never
            // both chained together.
            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is(RULE_ID)))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.warnings[0].validationLevel", is("OFFENCE")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("sex-adult-missing")))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(ADULT_MESSAGE)));
        }

        @Test
        void convictedSexOffence_adultWithNorrr_shouldNotRaiseWarning() throws Exception {
            stubReferencedataOffenceResponse("sex-adult-clear", "SEX");
            String request = adultRequest("sex-adult-clear", true, "NORRR");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SEX-008')]", empty()));
        }

        @Test
        void multipleOffences_onlyOneBreaching_shouldScopeWarningToThatOffenceOnly() throws Exception {
            stubReferencedataOffenceResponse("sex-multi-breach", "SEX");
            stubReferencedataOffenceResponse("sex-multi-clear", "SEX");
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-08-25",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "sex-multi-breach"},
                        {"resultLineId": "rl2", "shortCode": "NORRR", "label": "NORRR",
                         "defendantId": "d1", "offenceId": "sex-multi-clear"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "2000-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "sex-multi-breach", "offenceCode": "SX03007C",
                         "offenceTitle": "Sexual offence", "orderIndex": 1, "isConvicted": true},
                        {"offenceId": "sex-multi-clear", "offenceCode": "SX03007D",
                         "offenceTitle": "Sexual offence", "orderIndex": 2, "isConvicted": true}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].offenceId", is("sex-multi-breach")));
        }

        @Test
        void offenceNotConvicted_shouldNotRaiseWarning() throws Exception {
            String request = adultRequest("sex-not-convicted", false, "IMP");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SEX-008')]", empty()));
        }

        @Test
        void offenceNotSexualOffence_shouldNotRaiseWarning() throws Exception {
            stubReferencedataOffenceResponse("sex-not-sex-offence", "MOT");
            String request = adultRequest("sex-not-sex-offence", true, "IMP");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SEX-008')]", empty()));
        }
    }

    @Nested
    @DisplayName("User Story 2 - Youth")
    class YouthWarningScenarios {

        @Test
        void convictedSexOffence_youthMissingBothCodes_shouldRaiseYouthWarning() throws Exception {
            stubReferencedataOffenceResponse("sex-youth-missing", "SEX");
            String request = youthRequest("sex-youth-missing", "IMP");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedOffences[0].message", is(YOUTH_MESSAGE)));
        }

        @Test
        void convictedSexOffence_youthWithNorrrOnly_shouldNotRaiseWarning() throws Exception {
            stubReferencedataOffenceResponse("sex-youth-norrr", "SEX");
            String request = youthRequest("sex-youth-norrr", "NORRR");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SEX-008')]", empty()));
        }

        @Test
        void convictedSexOffence_youthWithNorpgpOnly_shouldNotRaiseWarning() throws Exception {
            stubReferencedataOffenceResponse("sex-youth-norpgp", "SEX");
            String request = youthRequest("sex-youth-norpgp", "NORPGP");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SEX-008')]", empty()));
        }
    }

    @Nested
    @DisplayName("Fail-open and caching contract")
    class FailOpenAndCachingContract {

        @Test
        void referencedataLookupFails_shouldNotRaiseWarningAndOtherRulesStillEvaluate() throws Exception {
            stubReferencedataOffenceNotFound("sex-lookup-fails");
            String request = adultRequest("sex-lookup-fails", true, "IMP");

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SEX-008')]", empty()))
                    .andExpect(jsonPath("$.rulesEvaluated", hasItem(RULE_ID)));
        }

        @Test
        void successfulLookup_shouldBeCachedAcrossRequests() throws Exception {
            stubReferencedataOffenceResponse("sex-cached-lookup", "SEX");
            String request = adultRequest("sex-cached-lookup", true, "IMP");

            performValidate(request).andExpect(status().isOk());
            performValidate(request).andExpect(status().isOk());

            REFERENCEDATA_OFFENCE_WIRE_MOCK.verify(1,
                    getRequestedFor(urlPathEqualTo(REFERENCEDATA_OFFENCE_PATH_PREFIX + "sex-cached-lookup")));
        }
    }

    @Nested
    @DisplayName("User Story 3 - combined offence and defendant level warnings")
    class CombinedWarningsDisplay {

        @Test
        void offenceLevelAndDefendantLevelWarnings_shouldBothAppearAndNotBlockSharing() throws Exception {
            stubReferencedataOffenceResponse("sex-combined", "SEX");
            // d2's two IMP offences, both marked concurrent, trigger DR-SENT-001's AC4
            // (DEFENDANT-level WARNING: "no primary sentence") — an independent rule, used here
            // purely to prove DR-SEX-008's offence-level warning coexists with another rule's
            // defendant-level warning in one response.
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-08-25",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "sex-combined"},
                        {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d2", "offenceId": "custodial-combined-1", "isConcurrent": true},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d2", "offenceId": "custodial-combined-2", "isConcurrent": true}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "2000-01-01"},
                        {"defendantId": "d2", "firstName": "Alex", "lastName": "Jones",
                         "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "sex-combined", "offenceCode": "SX03007C",
                         "offenceTitle": "Sexual offence", "orderIndex": 1, "isConvicted": true},
                        {"offenceId": "custodial-combined-1", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "orderIndex": 2},
                        {"offenceId": "custodial-combined-2", "offenceCode": "TH68002",
                         "offenceTitle": "Theft", "orderIndex": 3}
                      ]
                    }
                    """;

            // Compound filter predicates only (no further index chaining after a filter — see
            // the note in AdultWarningScenarios) — folding the validationLevel check into the
            // predicate itself proves both the presence and the level in one assertion.
            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.warnings", hasSize(2)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SEX-008' && @.validationLevel=='OFFENCE')]",
                            hasSize(1)))
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SENT-001' && @.validationLevel=='DEFENDANT')]",
                            hasSize(1)))
                    // no ERROR present anywhere — sharing is not blocked by warnings-only results
                    .andExpect(jsonPath("$.errors.validationIssues", empty()));
        }

        /**
         * US3 Acceptance Scenario 3: when more than one offence-level warning is triggered,
         * every one of them is shown, not just the first.
         */
        @Test
        void multipleOffenceLevelWarnings_shouldShowAllOfThem() throws Exception {
            stubReferencedataOffenceResponse("sex-multi-warn-1", "SEX");
            stubReferencedataOffenceResponse("sex-multi-warn-2", "SEX");
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-08-25",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "sex-multi-warn-1"},
                        {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d2", "offenceId": "sex-multi-warn-2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "2000-01-01"},
                        {"defendantId": "d2", "firstName": "Alex", "lastName": "Jones",
                         "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "sex-multi-warn-1", "offenceCode": "SX03007C",
                         "offenceTitle": "Sexual offence", "orderIndex": 1, "isConvicted": true},
                        {"offenceId": "sex-multi-warn-2", "offenceCode": "SX03007D",
                         "offenceTitle": "Sexual offence", "orderIndex": 2, "isConvicted": true}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings", hasSize(2)))
                    .andExpect(jsonPath("$.warnings[*].ruleId", everyItem(is("DR-SEX-008"))))
                    .andExpect(jsonPath("$.warnings[*].affectedOffences[0].offenceId",
                            containsInAnyOrder("sex-multi-warn-1", "sex-multi-warn-2")));
        }

        /**
         * US3 Acceptance Scenario 2: when more than one defendant-level warning is triggered,
         * every one of them is shown, not just the first. Uses two independent DR-SENT-001 AC4
         * triggers (one per defendant) rather than DR-SEX-008, since
         * DR-SEX-008 only ever produces OFFENCE-level issues — this proves the general multi-
         * warning display property at the DEFENDANT level using the same existing rule as the
         * single-warning combined scenario above.
         */
        @Test
        void multipleDefendantLevelWarnings_shouldShowAllOfThem() throws Exception {
            String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-08-25",
                      "courtType": "MAGISTRATES",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "custodial-multi-1a", "isConcurrent": true},
                        {"resultLineId": "rl2", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d1", "offenceId": "custodial-multi-1b", "isConcurrent": true},
                        {"resultLineId": "rl3", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d2", "offenceId": "custodial-multi-2a", "isConcurrent": true},
                        {"resultLineId": "rl4", "shortCode": "IMP", "label": "Imprisonment",
                         "defendantId": "d2", "offenceId": "custodial-multi-2b", "isConcurrent": true}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                         "dateOfBirth": "2000-01-01"},
                        {"defendantId": "d2", "firstName": "Alex", "lastName": "Jones",
                         "dateOfBirth": "1990-01-01"}
                      ],
                      "offences": [
                        {"offenceId": "custodial-multi-1a", "offenceCode": "TH68001", "offenceTitle": "Theft",
                         "orderIndex": 1},
                        {"offenceId": "custodial-multi-1b", "offenceCode": "TH68002", "offenceTitle": "Theft",
                         "orderIndex": 2},
                        {"offenceId": "custodial-multi-2a", "offenceCode": "TH68003", "offenceTitle": "Theft",
                         "orderIndex": 3},
                        {"offenceId": "custodial-multi-2b", "offenceCode": "TH68004", "offenceTitle": "Theft",
                         "orderIndex": 4}
                      ]
                    }
                    """;

            performValidate(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warnings[?(@.ruleId=='DR-SENT-001' && @.validationLevel=='DEFENDANT')]",
                            hasSize(2)));
        }
    }

    private static String adultRequest(final String offenceId, final boolean convicted, final String shortCode) {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-08-25",
                  "courtType": "MAGISTRATES",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "label": "%s",
                     "defendantId": "d1", "offenceId": "%s"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2000-01-01"}
                  ],
                  "offences": [
                    {"offenceId": "%s", "offenceCode": "SX03007C", "offenceTitle": "Sexual offence",
                     "orderIndex": 1, "isConvicted": %s}
                  ]
                }
                """.formatted(shortCode, shortCode + " label", offenceId, offenceId, convicted);
    }

    private static String youthRequest(final String offenceId, final String shortCode) {
        return """
                {
                  "hearingId": "h1",
                  "hearingDay": "2026-08-25",
                  "courtType": "YOUTH",
                  "resultLines": [
                    {"resultLineId": "rl1", "shortCode": "%s", "label": "%s",
                     "defendantId": "d1", "offenceId": "%s"}
                  ],
                  "defendants": [
                    {"defendantId": "d1", "firstName": "Jamie", "lastName": "Smith",
                     "dateOfBirth": "2012-01-01"}
                  ],
                  "offences": [
                    {"offenceId": "%s", "offenceCode": "SX03007C", "offenceTitle": "Sexual offence",
                     "orderIndex": 1, "isConvicted": true}
                  ]
                }
                """.formatted(shortCode, shortCode + " label", offenceId, offenceId);
    }

    private ResultActions performValidate(String request) throws Exception {
        return mockMvc.perform(post(VALIDATE_URL)
                .header("CJSCPPUID", "test-user")
                .header("CPP-ACTION", "validation-service.validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }
}
