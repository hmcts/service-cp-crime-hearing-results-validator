package uk.gov.hmcts.cp.integration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * End-to-end tests for DR-URG-008 (urgent result missing warning) over the public validate endpoint.
 *
 * <p>DR-URG-008 fires when all conditional-bail offences for a defendant are bail-ended
 * and no URGENT result has been recorded.
 *
 * <p>Every scenario pins three response slices:
 * <ul>
 *   <li>{@code $.errors.validationIssues} is empty (no other rule produces an error on the payload).</li>
 *   <li>{@code $.warnings[?(@.ruleId=='DR-URG-008')]} is the expected size for this rule.</li>
 *   <li>{@code $.warnings} total size, so an unrelated future rule cannot make tests pass silently.</li>
 * </ul>
 */
class UrgentMissingWarningIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String DR_URG_WARNINGS = "$.warnings[?(@.ruleId=='DR-URG-008')]";

    private static final String EXPECTED_MESSAGE =
            "The defendant's conditional bail has ended. You may need to add the URGENT result"
                    + " and select \"Bail conditions cancelled\" on one of the offences before sharing.";

    static Stream<Arguments> courtScopeScenarios() {
        return Stream.of("CROWN", "MAGISTRATES", "YOUTH").flatMap(court ->
                IntStream.rangeClosed(1, 2).boxed().flatMap(defendants ->
                        Stream.of("FO", "DS", "RI", "RIYDA", "RIH", "RIB", "RILA", "RILAB", "REMYD", "WOFN")
                                .map(code -> Arguments.of(court, defendants, code))));
    }

    @ParameterizedTest(name = "{0}, {1} defendant(s), {2}")
    @MethodSource("courtScopeScenarios")
    void only_crown_hearings_should_emit_urgent_warnings(final String courtType,
                                                        final int defendantCount,
                                                        final String shortCode) throws Exception {
        String category = "FO".equals(shortCode) ? "F" : "A";
        String defendants = IntStream.rangeClosed(1, defendantCount)
                .mapToObj("""
                        {"defendantId": "d%d", "firstName": "Alex", "lastName": "Jones"}
                        """::formatted)
                .collect(Collectors.joining(","));
        String offences = IntStream.rangeClosed(1, defendantCount)
                .mapToObj(i -> """
                        {"offenceId": "o%d", "defendantId": "d%d", "offenceCode": "TH68001",
                         "offenceTitle": "Theft", "bailStatus": "B",
                         "isConvicted": true, "hasExistingCtlRecord": true}
                        """.formatted(i, i))
                .collect(Collectors.joining(","));
        String results = IntStream.rangeClosed(1, defendantCount)
                .mapToObj(i -> """
                        {"resultLineId": "r%d", "defendantId": "d%d", "offenceId": "o%d",
                         "shortCode": "%s", "label": "%s", "category": "%s"}
                        """.formatted(i, i, i, shortCode, shortCode, category))
                .collect(Collectors.joining(","));
        String request = """
                {"hearingId": "court-scope", "hearingDay": "2026-09-12", "courtType": "%s",
                 "defendants": [%s], "offences": [%s], "resultLines": [%s]}
                """.formatted(courtType, defendants, offences, results);
        int expectedWarnings = "CROWN".equals(courtType) ? defendantCount : 0;

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid", is(true)))
                .andExpect(jsonPath("$.errors.validationIssues", empty()))
                .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(expectedWarnings)))
                .andExpect(jsonPath("$.warnings", hasSize(expectedWarnings)))
                .andExpect(jsonPath(DR_URG_WARNINGS + ".affectedDefendants[*].defendantId",
                        is("CROWN".equals(courtType)
                                ? IntStream.rangeClosed(1, defendantCount).mapToObj(i -> "d" + i).toList()
                                : List.of())));
    }

    @Nested
    @DisplayName("FireScenarios — US1 — warning emitted when all CB offences are bail-ended")
    class FireScenarios {

        @Test
        @DisplayName("AC1 — Category F result on CB offence, no URGENT → warning emitted at DEFENDANT level")
        void category_f_result_on_cb_offence_no_urgent_should_emit_warning() throws Exception {
            // WDRN with category F: excluded from DR-CONV-006's no-conviction check, safe from DR-SENT-001
            // and DR-CTL-003, yet still a Category-F result that bail-ends the CB offence.
            final String request = """
                    {
                      "hearingId": "h-ac1",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "WDRN", "category": "F", "label": "Withdrawn",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"}
                      ]
                    }
                    """;

            assertWarningFired(request);
        }

        @Test
        @DisplayName("AC2 — DS short code on CB offence, no URGENT → warning emitted at DEFENDANT level")
        void ds_bail_ending_no_urgent_on_cb_offence_should_emit_warning() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-ac2",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"}
                      ]
                    }
                    """;

            assertWarningFired(request);
        }

        @ParameterizedTest(name = "AC3 — {0} on CB offence, no URGENT → warning emitted")
        @ValueSource(strings = {"RI", "RIYDA", "RIH", "RIB", "RILA", "RILAB", "REMYD"})
        void ri_family_short_code_on_cb_offence_no_urgent_should_emit_warning(final String code)
                throws Exception {
            // RI codes also trigger DR-CTL-003 (no CTL record). Total warning count is not asserted
            // here — only that DR-URG-008 fires with the correct defendant and message.
            final String request = """
                    {
                      "hearingId": "h-ac3",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "%s", "label": "Remand",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"}
                      ]
                    }
                    """.formatted(code);

            assertUrg008WarningFired(request);
        }

        @Test
        @DisplayName("AC4 — WOFN short code on CB offence, no URGENT → warning emitted at DEFENDANT level")
        void wofn_on_cb_offence_no_urgent_should_emit_warning() throws Exception {
            final String request = """
                    {
                      "hearingId": "h-ac4",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "WOFN", "label": "Warrant Without Bail",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"}
                      ]
                    }
                    """;

            assertWarningFired(request);
        }

        @Test
        @DisplayName("AC5 — mixed bail-ending types (FO/F + DS + RI + RI) across CB offences, no URGENT → warning emitted")
        void mixed_bail_ending_types_across_cb_offences_no_urgent_should_emit_warning() throws Exception {
            // Exact AC5 fixture. Conviction and CTL flags isolate DR-URG-008 from other rules.
            final String request = """
                    {
                      "hearingId": "h-ac5",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "FO", "category": "F", "label": "Final order",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-2"},
                        {"resultLineId": "rl3", "shortCode": "RI", "label": "Remand in custody",
                         "defendantId": "d1", "offenceId": "off-3"},
                        {"resultLineId": "rl4", "shortCode": "RI", "label": "Remand in custody",
                         "defendantId": "d1", "offenceId": "off-4"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true},
                        {"offenceId": "off-2", "defendantId": "d1", "offenceCode": "TH68002", "offenceTitle": "Burglary",
                         "orderIndex": 2, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true},
                        {"offenceId": "off-3", "defendantId": "d1", "offenceCode": "TH68003", "offenceTitle": "Theft",
                         "orderIndex": 3, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true},
                        {"offenceId": "off-4", "defendantId": "d1", "offenceCode": "TH68004", "offenceTitle": "Theft",
                         "orderIndex": 4, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-URG-008")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.warnings[0].validationLevel", is("DEFENDANT")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].defendantId", is("d1")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].message", is(EXPECTED_MESSAGE)));
        }
    }

    @Nested
    @DisplayName("UrgentSuppressesWarning")
    class UrgentSuppressesWarning {

        @Test
        @DisplayName("URGENT result present on CB offence → warning suppressed")
        void urgent_result_present_on_cb_offence_should_suppress_warning() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "URGENT", "label": "Urgent",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("BailNotFullyEnded")
    class BailNotFullyEnded {

        @Test
        @DisplayName("AC5A — one CB offence bail-ended, one CB offence unresulted → no warning")
        void one_cb_offence_bail_ended_one_cb_offence_unresulted_should_produce_no_warning()
                throws Exception {
            // off-2 is CB but has no result lines → unresulted CB offence → bailEndedCount (1)
            // < conditionalBailOffenceCount (2) → CEL expression false → no warning.
            final String request = """
                    {
                      "hearingId": "h-ac5a",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"},
                        {"offenceId": "off-2", "defendantId": "d1", "offenceCode": "TH68002", "offenceTitle": "Burglary",
                         "orderIndex": 2, "bailStatus": "B"}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }

        @Test
        @DisplayName("Mix of bail-ending and continuing results across one defendant's CB offences → no warning")
        void mix_of_bail_ending_and_continuing_results_should_produce_no_warning() throws Exception {
            // off-1: CB, DS (bail-ending) — bail ended
            // off-2: CB, PLEA category I (non-bail-ending) — bail continues
            // bailEndedCount (1) < conditionalBailOffenceCount (2) → no warning
            final String request = """
                    {
                      "hearingId": "h-mix-bail-continuing",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "PLEA", "category": "I", "label": "Plea",
                         "defendantId": "d1", "offenceId": "off-2"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"},
                        {"offenceId": "off-2", "defendantId": "d1", "offenceCode": "TH68002", "offenceTitle": "Burglary",
                         "orderIndex": 2, "bailStatus": "B"}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }

        @Test
        @DisplayName("CB offence with non-bail-ending result → no warning")
        void non_bail_ending_result_on_cb_offence_should_produce_no_warning() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "WDRN", "label": "Withdrawn",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("NoConditionalBailOffences")
    class NoConditionalBailOffences {

        @Test
        @DisplayName("No offences with bailStatus=B → no warning")
        void no_bail_status_offences_should_produce_no_warning() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [{"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"}],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("MultiDefendant")
    class MultiDefendant {

        @Test
        @DisplayName("Defendant A (all CB bail-ended) gets warning; Defendant B (unresulted CB offence) does not")
        void defendant_a_all_cb_bail_ended_defendant_b_unresulted_cb_should_warn_only_a()
                throws Exception {
            // Defendant A (d1): 2 CB offences (off-1, off-2), both bail-ended with DS → WARNING
            // Defendant B (d2): 2 CB offences — off-3 bail-ended (DS), off-4 has NO result lines
            //   but carries defendantId "d2" on the offence, so the preprocessor scopes it to d2's
            //   group only → d2's conditionalBailOffenceCount (2) > bailEndedCount (1) → no warning.
            //
            // Without per-defendant scoping of unresulted CB offences, off-4 would leak into d1's
            // group and inflate d1's conditionalBailOffenceCount, suppressing d1's legitimate warning.
            final String request = """
                    {
                      "hearingId": "h-multi-unresulted",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-2"},
                        {"resultLineId": "rl3", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d2", "offenceId": "off-3"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"},
                        {"defendantId": "d2", "firstName": "Sam", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"},
                        {"offenceId": "off-2", "defendantId": "d1", "offenceCode": "TH68002", "offenceTitle": "Burglary",
                         "orderIndex": 2, "bailStatus": "B"},
                        {"offenceId": "off-3", "defendantId": "d2", "offenceCode": "TH68003", "offenceTitle": "Theft",
                         "orderIndex": 3, "bailStatus": "B"},
                        {"offenceId": "off-4", "offenceCode": "AS001", "offenceTitle": "Assault",
                         "orderIndex": 4, "bailStatus": "B", "defendantId": "d2"}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-URG-008")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.warnings[0].validationLevel", is("DEFENDANT")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].defendantId", is("d1")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].message", is(EXPECTED_MESSAGE)));
        }

        @Test
        @DisplayName("Defendant A bail-ended gets warning even when Defendant B has zero result lines and an unresulted CB offence")
        void defendant_a_warned_when_defendant_b_has_no_result_lines_and_unresulted_cb() throws Exception {
            // B owns off-3 explicitly even though B has no result lines.
            // That unresulted offence must suppress only B's warning.
            final String request = """
                    {
                      "hearingId": "h-multi-zero-lines",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"},
                        {"defendantId": "d2", "firstName": "Sam", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"},
                        {"offenceId": "off-3", "defendantId": "d2", "offenceCode": "AS001", "offenceTitle": "Assault",
                         "orderIndex": 2, "bailStatus": "B"}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-URG-008")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.warnings[0].validationLevel", is("DEFENDANT")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].defendantId", is("d1")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].message", is(EXPECTED_MESSAGE)));
        }

        @Test
        @DisplayName("URGENT on a different CB offence suppresses only that defendant's warning")
        void urgent_on_different_cb_offence_suppresses_only_that_defendants_warning() throws Exception {
            // Defendant A (d1): off-1 (CB, DS), off-2 (CB, RI + URGENT) → all bail-ended, URGENT on
            //   a different offence (off-2) still suppresses A's warning.
            // Defendant B (d2): off-3 (CB, DS) → all bail-ended, no URGENT → WARNING for B only.
            // isConvicted + hasExistingCtlRecord suppress DR-CTL-003 from the RI code.
            final String request = """
                    {
                      "hearingId": "h-urgent-cross-offence",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "RI", "label": "Remand in Custody",
                         "defendantId": "d1", "offenceId": "off-2"},
                        {"resultLineId": "rl3", "shortCode": "URGENT", "label": "Urgent",
                         "defendantId": "d1", "offenceId": "off-2"},
                        {"resultLineId": "rl4", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d2", "offenceId": "off-3"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"},
                        {"defendantId": "d2", "firstName": "Sam", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true},
                        {"offenceId": "off-2", "defendantId": "d1", "offenceCode": "TH68002", "offenceTitle": "Burglary",
                         "orderIndex": 2, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true},
                        {"offenceId": "off-3", "defendantId": "d2", "offenceCode": "TH68003", "offenceTitle": "Theft",
                         "orderIndex": 3, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-URG-008")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.warnings[0].validationLevel", is("DEFENDANT")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].defendantId", is("d2")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].message", is(EXPECTED_MESSAGE)));
        }

        @Test
        @DisplayName("Linked defendants (same masterDefendantId) produce exactly one warning")
        void linked_defendants_same_master_produce_exactly_one_warning() throws Exception {
            // d1 and d2 share masterDefendantId "m1" → deduplicated into one group.
            // d1: off-1 (CB, DS bail-ended), d2: off-2 (CB, RI bail-ended). No URGENT.
            // One merged group, all CB bail-ended → exactly one warning keyed by "m1".
            final String request = """
                    {
                      "hearingId": "h-linked-one-warning",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "RI", "label": "Remand in Custody",
                         "defendantId": "d2", "offenceId": "off-2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "m1", "firstName": "Alex", "lastName": "Jones"},
                        {"defendantId": "d2", "masterDefendantId": "m1", "firstName": "Alex", "lastName": "Jones"}
                      ],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true},
                        {"offenceId": "off-2", "defendantId": "d2", "offenceCode": "TH68002", "offenceTitle": "Burglary",
                         "orderIndex": 2, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-URG-008")))
                    .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                    .andExpect(jsonPath("$.isValid", is(true)))
                    .andExpect(jsonPath("$.warnings[0].validationLevel", is("DEFENDANT")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].defendantId", is("m1")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].message", is(EXPECTED_MESSAGE)));
        }

        @Test
        @DisplayName("URGENT on one linked record suppresses the merged group's warning")
        void urgent_on_one_linked_record_suppresses_merged_group_warning() throws Exception {
            // d1 and d2 share masterDefendantId "m1" → one merged group.
            // d1: off-1 (CB, DS bail-ended, no URGENT)
            // d2: off-2 (CB, RI bail-ended + URGENT)
            // URGENT on d2's offence crosses into the merged group → warning suppressed.
            final String request = """
                    {
                      "hearingId": "h-linked-urgent-suppressed",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "RI", "label": "Remand in Custody",
                         "defendantId": "d2", "offenceId": "off-2"},
                        {"resultLineId": "rl3", "shortCode": "URGENT", "label": "Urgent",
                         "defendantId": "d2", "offenceId": "off-2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "masterDefendantId": "m1", "firstName": "Alex", "lastName": "Jones"},
                        {"defendantId": "d2", "masterDefendantId": "m1", "firstName": "Alex", "lastName": "Jones"}
                      ],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true},
                        {"offenceId": "off-2", "defendantId": "d2", "offenceCode": "TH68002", "offenceTitle": "Burglary",
                         "orderIndex": 2, "bailStatus": "B", "isConvicted": true, "hasExistingCtlRecord": true}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(0)))
                    .andExpect(jsonPath("$.warnings", hasSize(0)));
        }

        @Test
        @DisplayName("Qualifying defendant (d1) gets warning; non-qualifying (d2) does not")
        void qualifying_defendant_gets_warning_nonqualifying_does_not() throws Exception {
            final String request = """
                    {
                      "hearingId": "h1",
                      "hearingDay": "2026-05-06",
                      "courtType": "CROWN",
                      "resultLines": [
                        {"resultLineId": "rl1", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d1", "offenceId": "off-1"},
                        {"resultLineId": "rl2", "shortCode": "DS", "label": "Defer Sentence",
                         "defendantId": "d2", "offenceId": "off-2"}
                      ],
                      "defendants": [
                        {"defendantId": "d1", "firstName": "Alex", "lastName": "Jones"},
                        {"defendantId": "d2", "firstName": "Sam", "lastName": "Smith"}
                      ],
                      "offences": [
                        {"offenceId": "off-1", "defendantId": "d1", "offenceCode": "TH68001", "offenceTitle": "Robbery",
                         "orderIndex": 1, "bailStatus": "B"},
                        {"offenceId": "off-2", "defendantId": "d2", "offenceCode": "AS001", "offenceTitle": "Assault",
                         "orderIndex": 2}
                      ]
                    }
                    """;

            mockMvc.perform(post(VALIDATE_URL)
                            .header("CJSCPPUID", "test-user")
                            .header("CPP-ACTION", "validation-service.validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors.validationIssues", empty()))
                    .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(1)))
                    .andExpect(jsonPath("$.warnings", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-URG-008")))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants", hasSize(1)))
                    .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].defendantId", is("d1")));
        }
    }

    /**
     * Asserts DR-URG-008 fires AND that no other rule also produces a warning on the same payload.
     * Use this for AC1, AC2, AC4, AC5 where the chosen short codes do not trigger any other rule.
     */
    private void assertWarningFired(final String request) throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors.validationIssues", empty()))
                .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(1)))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].ruleId", is("DR-URG-008")))
                .andExpect(jsonPath("$.warnings[0].severity", is("WARNING")))
                .andExpect(jsonPath("$.isValid", is(true)))
                .andExpect(jsonPath("$.warnings[0].validationLevel", is("DEFENDANT")))
                .andExpect(jsonPath("$.warnings[0].affectedDefendants", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].defendantId", is("d1")))
                .andExpect(jsonPath("$.warnings[0].affectedDefendants[0].message", is(EXPECTED_MESSAGE)));
    }

    /**
     * Asserts that DR-URG-008 fires with the correct defendant and message, without constraining
     * the total warning count. Use this for AC3 (RI family), where DR-CTL-003 also fires because
     * no CTL record is present in the test data.
     *
     * <p>JSONPath filter projections ({@code [?(@.ruleId=='DR-URG-008')].field}) return an array
     * of field values — one per matched warning. {@code contains} / {@code hasItem} are used
     * instead of {@code is} to match against that projected array.
     */
    private void assertUrg008WarningFired(final String request) throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "test-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors.validationIssues", empty()))
                .andExpect(jsonPath(DR_URG_WARNINGS, hasSize(1)))
                .andExpect(jsonPath(DR_URG_WARNINGS + ".severity", contains("WARNING")))
                .andExpect(jsonPath("$.isValid", is(true)))
                .andExpect(jsonPath(DR_URG_WARNINGS + ".validationLevel", contains("DEFENDANT")))
                .andExpect(jsonPath(DR_URG_WARNINGS + ".affectedDefendants[*].defendantId", hasItem("d1")))
                .andExpect(jsonPath(DR_URG_WARNINGS + ".affectedDefendants[*].message", hasItem(EXPECTED_MESSAGE)));
    }
}
