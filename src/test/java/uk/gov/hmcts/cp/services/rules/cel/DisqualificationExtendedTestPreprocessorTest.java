package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offenceWithCode;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Unit tests for the per-offence preprocessor that drives DR-DISQ-002.
 *
 * <p>{@code relevantOffenceCodes}, {@code excludedFinalShortCodes} and
 * {@code extendedTestShortCodes} are read from the real {@code DR-DISQ-002.yaml} rule file via
 * {@link RuleDefinitionLoader} rather than duplicated here as literals, so {@link #config} always
 * matches what ships in production, and the parameterized suppression tests (backed by
 * {@link #excludedFinalShortCodes()} / {@link #extendedTestShortCodes()}) automatically cover every
 * code the YAML declares. The {@code ..._should_match_the_known_baseline_exactly()} tests pin those
 * sets against known baselines so a code added to or removed from the YAML without the baseline
 * being updated fails loudly instead of silently changing what the parameterized tests cover.
 */
class DisqualificationExtendedTestPreprocessorTest {

    private static final RuleDefinition RULE_DEFINITION =
            RuleDefinitionLoader.load("rules/DR-DISQ-002.yaml");

    private static final List<String> RELEVANT_CODES =
            List.copyOf(RULE_DEFINITION.preprocessing().relevantOffenceCodes());
    private static final List<String> EXCLUDED_SHORT_CODES =
            List.copyOf(RULE_DEFINITION.preprocessing().excludedFinalShortCodes());
    private static final List<String> EXTENDED_TEST_SHORT_CODES =
            List.copyOf(RULE_DEFINITION.preprocessing().extendedTestShortCodes());

    /** Kept in lockstep with the YAML by {@link #relevantCodes_should_match_the_known_baseline_exactly()}. */
    private static final List<String> EXPECTED_RELEVANT_CODES = List.of(
            "RT88046", "RT88526", "RT88526A", "RT88526B", "RT88026", "RT88026B", "RT88530", "RT88531");

    /** Kept in lockstep with the YAML by {@link #excludedFinalShortCodes_should_match_the_known_baseline_exactly()}. */
    private static final List<String> EXPECTED_EXCLUDED_SHORT_CODES = List.of(
            "wdrn", "WDRNOFF", "dism", "dine", "dini", "disch", "disc", "ctrof", "iremfile",
            "err", "errf", "dhd");

    /** Kept in lockstep with the YAML by {@link #extendedTestShortCodes_should_match_the_known_baseline_exactly()}. */
    private static final List<String> EXPECTED_EXTENDED_TEST_SHORT_CODES = List.of("DDOTE", "DDOTEL");

    private final DisqualificationExtendedTestPreprocessor preprocessor =
            new DisqualificationExtendedTestPreprocessor();

    private final PreprocessingDefinition config = RULE_DEFINITION.preprocessing();

    @Test
    @DisplayName("DR-DISQ-002.yaml's relevantOffenceCodes must exactly match the known baseline")
    void relevantCodes_should_match_the_known_baseline_exactly() {
        assertThat(RELEVANT_CODES)
                .as("DR-DISQ-002.yaml's relevantOffenceCodes must exactly match "
                        + "EXPECTED_RELEVANT_CODES -- a code was added or removed in the YAML "
                        + "without this test's baseline being updated to match")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_RELEVANT_CODES);
    }

    @Test
    @DisplayName("DR-DISQ-002.yaml's excludedFinalShortCodes must exactly match the known baseline")
    void excludedFinalShortCodes_should_match_the_known_baseline_exactly() {
        assertThat(EXCLUDED_SHORT_CODES)
                .as("DR-DISQ-002.yaml's excludedFinalShortCodes must exactly match "
                        + "EXPECTED_EXCLUDED_SHORT_CODES -- a code was added or removed in the YAML "
                        + "without this test's baseline being updated to match, which would leave it "
                        + "untested by the parameterized excluded-short-code test")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_EXCLUDED_SHORT_CODES);
    }

    @Test
    @DisplayName("DR-DISQ-002.yaml's extendedTestShortCodes must exactly match the known baseline")
    void extendedTestShortCodes_should_match_the_known_baseline_exactly() {
        assertThat(EXTENDED_TEST_SHORT_CODES)
                .as("DR-DISQ-002.yaml's extendedTestShortCodes must exactly match "
                        + "EXPECTED_EXTENDED_TEST_SHORT_CODES -- a code was added or removed in the YAML "
                        + "without this test's baseline being updated to match, which would leave it "
                        + "untested by the parameterized extended-test-short-code test")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_EXTENDED_TEST_SHORT_CODES);
    }

    /** Factory method for {@code each_excluded_short_code_should_suppress}; YAML-backed. */
    static List<String> excludedFinalShortCodes() {
        return EXCLUDED_SHORT_CODES;
    }

    /** Factory method for {@code extended_test_disqualification_codes_should_suppress}; YAML-backed. */
    static List<String> extendedTestShortCodes() {
        return EXTENDED_TEST_SHORT_CODES;
    }

    @Nested
    @DisplayName("RelevanceGate")
    class RelevanceGate {

        @Test
        void rt88026_with_coew_and_no_ddote_should_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "COEW", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.relevantCount()).isEqualTo(1L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(1L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(0L);
            assertThat(ctx.disqExtTestCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
            assertThat(ctx.allOffenceIds()).containsExactly("off1");
        }

        @Test
        void each_relevant_offence_code_should_be_recognised() {
            for (String code : RELEVANT_CODES) {
                DraftValidationRequest request = buildRequest(
                        List.of(resultLine("rl1", "COEW", "d1", "off1")
                                .category(ResultLineDto.CategoryEnum.F)),
                        List.of(offenceWithCode("off1", 1, "RT-relevant", code)));

                DisqualificationContext ctx = preprocess(request).get("off1");

                assertThat(ctx.relevantCount())
                        .as("offenceCode=%s should be relevant", code)
                        .isEqualTo(1L);
                assertThat(ctx.qualifyingCount())
                        .as("offenceCode=%s should qualify when COEW is present", code)
                        .isEqualTo(1L);
            }
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"RT88526A", "RT88526B", "RT88026B", "RT88530", "RT88531"})
        void remaining_relevant_offence_codes_should_qualify_with_non_excluded_final_result(final String code) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "COEW", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Relevant offence", code)));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).as("offenceCode=%s should qualify", code).isEqualTo(1L);
            assertThat(ctx.relevantCount()).isEqualTo(1L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(1L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(0L);
            assertThat(ctx.disqExtTestCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"RT88526A", "RT88526B", "RT88026B", "RT88530", "RT88531"})
        void remaining_relevant_offence_codes_should_be_suppressed_by_ddote(final String code) {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "DDOTE", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offenceWithCode("off1", 1, "Relevant offence", code)));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).as("offenceCode=%s should be suppressed by DDOTE", code).isEqualTo(0L);
            assertThat(ctx.disqExtTestCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"RT88526A", "RT88526B", "RT88026B", "RT88530", "RT88531"})
        void remaining_relevant_offence_codes_should_be_suppressed_by_excluded_final_result(final String code) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "wdrn", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Relevant offence", code)));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).as("offenceCode=%s should be suppressed by wdrn", code).isEqualTo(0L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }

        @Test
        void unknown_short_code_should_be_treated_as_non_excluded() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "ZZZZ", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
        }

        /**
         * A null shortCode is not a member of {@code excludedFinalShortCodes} (set-membership
         * semantics, per research.md's {@code line.shortCode ∉ excludedFinalShortCodes} gate), and
         * spec.md's edge-case note treats any final line that's "unknown ... and not in the
         * excluded list" as conservative-by-design ⇒ fires. Must match DR-CONV-006's
         * {@code PreprocessorHelper.hasUpperCode}-based treatment of the same case.
         */
        @Test
        void null_short_code_on_final_line_should_be_treated_as_non_excluded() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", null, "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(0L);
        }

        @Test
        void two_defendants_charged_with_same_relevant_offence_should_produce_one_context() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "COEW", "d2", "off1")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            Map<String, DisqualificationContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("off1");
            DisqualificationContext ctx = result.get("off1");
            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
        }
    }

    @Nested
    @DisplayName("SuppressionSmoke")
    class SuppressionSmoke {

        @Test
        void wdrn_on_relevant_offence_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "wdrn", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }

        @Test
        void ddote_on_relevant_offence_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "DDOTE", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.disqExtTestCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("NonRelevantOffences")
    class NonRelevantOffences {

        @Test
        void th68001_with_coew_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "COEW", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Theft", "TH68001")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.relevantCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("OffenceWithoutResults")
    class OffenceWithoutResults {

        @Test
        void relevant_offence_with_no_result_lines_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.relevantCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("CaseInsensitivity")
    class CaseInsensitivity {

        @Test
        void lowercase_offence_code_and_short_code_should_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "coew", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "rt88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
        }
    }

    @Nested
    @DisplayName("Per-offence emission")
    class PerOffenceEmission {

        @Test
        void each_offence_should_produce_its_own_context_keyed_by_offence_id() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "COEW", "d1", "off2")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(
                            offenceWithCode("off1", 1, "Dangerous driving", "RT88026"),
                            offenceWithCode("off2", 2, "Theft", "TH68001")));

            Map<String, DisqualificationContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("off1", "off2");
            assertThat(result.get("off1").qualifyingCount()).isEqualTo(1L);
            assertThat(result.get("off2").qualifyingCount()).isEqualTo(0L);
        }

        @Test
        void two_defendants_with_different_relevant_offences_both_missing_ddote_should_each_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "COEW", "d2", "off2")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(
                            offenceWithCode("off1", 1, "Dangerous driving", "RT88026"),
                            offenceWithCode("off2", 2, "Causing serious injury by dangerous driving",
                                    "RT88526")));

            Map<String, DisqualificationContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("off1", "off2");
            assertThat(result.get("off1").qualifyingCount()).isEqualTo(1L);
            assertThat(result.get("off2").qualifyingCount()).isEqualTo(1L);
            assertThat(result.get("off1").qualifyingOffenceIds()).containsExactly("off1");
            assertThat(result.get("off2").qualifyingOffenceIds()).containsExactly("off2");
        }
    }

    @Nested
    @DisplayName("ExcludedFinalSuppression — Phase 4 / US2")
    class ExcludedFinalSuppression {

        @ParameterizedTest
        @MethodSource(
                "uk.gov.hmcts.cp.services.rules.cel.DisqualificationExtendedTestPreprocessorTest#excludedFinalShortCodes")
        void each_excluded_short_code_should_suppress(final String excludedCode) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", excludedCode, "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount())
                    .as("excluded short code %s should suppress", excludedCode)
                    .isEqualTo(0L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"WDRN", "Wdrn", "WdRn", "WDRNOff", "IREMFILE", "Disch"})
        void mixed_case_excluded_short_codes_should_suppress(final String mixedCase) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", mixedCase, "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount())
                    .as("mixed-case excluded code %s should suppress", mixedCase)
                    .isEqualTo(0L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(1L);
        }

        @Test
        void non_excluded_final_code_imp_should_still_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(0L);
        }

        @Test
        void excluded_short_code_on_non_relevant_offence_should_not_qualify_or_count_as_relevant() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "wdrn", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Theft", "TH68001")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.relevantCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("ExtendedTestSuppression — Phase 5 / US3")
    class ExtendedTestSuppression {

        @ParameterizedTest
        @MethodSource(
                "uk.gov.hmcts.cp.services.rules.cel.DisqualificationExtendedTestPreprocessorTest#extendedTestShortCodes")
        void extended_test_disqualification_codes_should_suppress(final String code) {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", code, "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount())
                    .as("extended-test code %s should suppress", code)
                    .isEqualTo(0L);
            assertThat(ctx.disqExtTestCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"ddote", "DdOtE", "ddotel", "DDoTeL"})
        void mixed_case_extended_test_codes_should_suppress(final String mixedCase) {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", mixedCase, "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount())
                    .as("mixed-case extended-test code %s should suppress", mixedCase)
                    .isEqualTo(0L);
            assertThat(ctx.disqExtTestCount()).isEqualTo(1L);
        }

        @Test
        void ddote_on_a_different_offence_should_not_suppress_first_offence() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "COEW", "d1", "off2")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl3", "DDOTE", "d1", "off2")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(
                            offenceWithCode("off1", 1, "Dangerous driving", "RT88026"),
                            offenceWithCode("off2", 2, "Causing death by dangerous driving",
                                    "RT88046")));

            Map<String, DisqualificationContext> result = preprocess(request);

            assertThat(result.get("off1").qualifyingCount()).isEqualTo(1L);
            assertThat(result.get("off1").disqExtTestCount()).isEqualTo(0L);
            assertThat(result.get("off2").qualifyingCount()).isEqualTo(0L);
            assertThat(result.get("off2").disqExtTestCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("NoFinalLine — Phase 7 / US4 (category gate)")
    class NoFinalLine {

        @Test
        void relevant_offence_with_only_adjournment_line_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "ADJN", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.A)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(0L);
        }

        @Test
        void relevant_offence_with_only_intermediary_line_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "PLEA", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(0L);
        }

        @Test
        void relevant_offence_with_multiple_non_final_lines_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "ADJN", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.A),
                            resultLine("rl2", "PLEA", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(0L);
        }

        @Test
        void relevant_offence_with_null_category_should_not_qualify_fr015_fail_safe() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "COEW", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("CategoryFGateBoundary — Phase 7 / US4")
    class CategoryFGateBoundary {

        @Test
        void offence_with_two_excluded_f_lines_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "wdrn", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "dism", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(2L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(2L);
        }

        @Test
        void offence_with_one_excluded_and_one_non_excluded_f_line_should_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "wdrn", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
        }

        @Test
        void ddote_on_intermediary_line_should_still_suppress_when_f_line_present() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "DDOTE", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.disqExtTestCount()).isEqualTo(1L);
        }
    }

    private Map<String, DisqualificationContext> preprocess(final DraftValidationRequest request) {
        return preprocessor.preprocess(request, config);
    }
}
