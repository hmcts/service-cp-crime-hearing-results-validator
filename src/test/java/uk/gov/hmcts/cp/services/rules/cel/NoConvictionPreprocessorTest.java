package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
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
 * Unit tests for the per-offence preprocessor that drives DR-CONV-006.
 *
 * <p>{@code excludedFinalShortCodes} is read from the real {@code DR-CONV-006.yaml} rule file via
 * {@link RuleDefinitionLoader} rather than duplicated here as a literal, so {@link #config} always
 * matches what ships in production, and {@link ExcludedFinalBypass}'s parameterized suppression
 * test (backed by {@link #excludedFinalShortCodes()}) automatically covers every code the YAML
 * declares. {@link #excludedFinalShortCodes_should_match_the_known_baseline_exactly()} pins that
 * set against a known baseline: if a code is ever added to or removed from the YAML without the
 * baseline being updated to match, that assertion fails loudly instead of the parameterized test
 * silently covering a different set of codes than intended.
 */
class NoConvictionPreprocessorTest {

    private static final RuleDefinition RULE_DEFINITION =
            RuleDefinitionLoader.load("rules/DR-CONV-006.yaml");

    private static final List<String> EXCLUDED_SHORT_CODES =
            List.copyOf(RULE_DEFINITION.preprocessing().excludedFinalShortCodes());

    /**
     * Every short code this suite expects DR-CONV-006.yaml's {@code excludedFinalShortCodes} to
     * declare -- kept in exact lockstep with the YAML by
     * {@link #excludedFinalShortCodes_should_match_the_known_baseline_exactly()}, so both a code
     * removed from the YAML and one added to it are caught here, and every code the YAML declares
     * is guaranteed to also flow into {@link ExcludedFinalBypass}'s parameterized suppression test
     * via the YAML-backed {@link #excludedFinalShortCodes()} method source.
     */
    private static final List<String> EXPECTED_EXCLUDED_SHORT_CODES = List.of(
            "wdrn", "WDRNOFF", "dism", "dine", "dini", "disch", "disc", "ctrof", "iremfile",
            "err", "errf", "dhd");

    private final NoConvictionPreprocessor preprocessor = new NoConvictionPreprocessor();

    private final PreprocessingDefinition config = RULE_DEFINITION.preprocessing();

    @Test
    @DisplayName("DR-CONV-006.yaml's excludedFinalShortCodes must exactly match the known baseline")
    void excludedFinalShortCodes_should_match_the_known_baseline_exactly() {
        assertThat(EXCLUDED_SHORT_CODES)
                .as("DR-CONV-006.yaml's excludedFinalShortCodes must exactly match "
                        + "EXPECTED_EXCLUDED_SHORT_CODES -- a code was added or removed in the YAML "
                        + "without this test's baseline being updated to match, which would leave it "
                        + "untested by ExcludedFinalBypass's parameterized suppression test")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_EXCLUDED_SHORT_CODES);
    }

    /** Factory method for {@code ExcludedFinalBypass}'s {@code @MethodSource}; YAML-backed. */
    static List<String> excludedFinalShortCodes() {
        return EXCLUDED_SHORT_CODES;
    }

    @Nested
    @DisplayName("PositivePath — US1")
    class PositivePath {

        @Test
        void final_non_excluded_result_with_no_conviction_should_warn() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "COEW", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off1", 1, "Theft").isConvicted(false)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount()).isEqualTo(1L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(1L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(0L);
            assertThat(ctx.convictedCount()).isEqualTo(0L);
            assertThat(ctx.warningOffenceIds()).containsExactly("off1");
            assertThat(ctx.allOffenceIds()).containsExactly("off1");
        }

        @Test
        void null_isConvicted_should_be_treated_as_not_convicted() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "COEW", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off1", 1, "Theft")));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount()).isEqualTo(1L);
            assertThat(ctx.convictedCount()).isEqualTo(0L);
        }

        @Test
        void applies_regardless_of_offence_code_fr009() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "SSO", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off1", 1, "Any offence").isConvicted(false)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount()).isEqualTo(1L);
        }

        @Test
        void two_offences_only_one_qualifying_should_isolate_the_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "wdrn", "d1", "off2")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(
                            offence("off1", 1, "Theft").isConvicted(false),
                            offence("off2", 2, "Burglary").isConvicted(false)));

            Map<String, NoConvictionContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("off1", "off2");
            assertThat(result.get("off1").unconvictedSentenceCount()).isEqualTo(1L);
            assertThat(result.get("off2").unconvictedSentenceCount()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("ExcludedFinalBypass — US1")
    class ExcludedFinalBypass {

        @ParameterizedTest
        @MethodSource("uk.gov.hmcts.cp.services.rules.cel.NoConvictionPreprocessorTest#excludedFinalShortCodes")
        void each_excluded_short_code_should_suppress(final String excludedCode) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", excludedCode, "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off1", 1, "Theft").isConvicted(false)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount())
                    .as("excluded short code %s should suppress", excludedCode)
                    .isEqualTo(0L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(1L);
            assertThat(ctx.warningOffenceIds()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"WDRN", "Wdrn", "WdRn", "WDRNOff", "IREMFILE", "Disch"})
        void mixed_case_excluded_short_codes_should_suppress(final String mixedCase) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", mixedCase, "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off1", 1, "Theft").isConvicted(false)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount())
                    .as("mixed-case excluded code %s should suppress", mixedCase)
                    .isEqualTo(0L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(1L);
        }

        /**
         * A null shortCode is not a member of {@code excludedFinalShortCodes}, so it must count as
         * non-excluded and can trigger the warning — pins the behaviour that {@code
         * PreprocessorHelper.hasUpperCode} already gives here, matching DR-DISQ-002's
         * (spec-mandated) treatment of the same case.
         */
        @Test
        void null_short_code_on_final_line_should_be_treated_as_non_excluded() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", null, "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off1", 1, "Theft").isConvicted(false)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount()).isEqualTo(1L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("NoFinalResultBypass — US1")
    class NoFinalResultBypass {

        @Test
        void offence_with_only_intermediary_line_should_not_warn() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "PLEA", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offence("off1", 1, "Theft").isConvicted(false)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount()).isEqualTo(0L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(0L);
        }

        @Test
        void offence_with_no_result_lines_should_not_warn() {
            DraftValidationRequest request = buildRequest(
                    List.of(),
                    List.of(offence("off1", 1, "Theft").isConvicted(false)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("ConvictionSuppression — US2 (AC1A / AC1B)")
    class ConvictionSuppression {

        @Test
        void convicted_offence_should_not_warn_even_with_qualifying_final_result() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "COEW", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off1", 1, "Theft").isConvicted(true)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount()).isEqualTo(0L);
            assertThat(ctx.convictedCount()).isEqualTo(1L);
        }

        @Test
        void convicted_offence_with_no_final_result_yet_should_not_warn() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "PLEA", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offence("off1", 1, "Theft").isConvicted(true)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount()).isEqualTo(0L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(0L);
            assertThat(ctx.convictedCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("MultipleResultLines")
    class MultipleResultLines {

        @Test
        void any_qualifying_final_line_among_several_should_trigger_the_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "wdrn", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off1", 1, "Theft").isConvicted(false)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount()).isEqualTo(1L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(2L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(1L);
        }

        @Test
        void mixing_final_and_non_final_lines_should_ignore_the_non_final_one() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "PLEA", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.I),
                            resultLine("rl2", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off1", 1, "Theft").isConvicted(false)));

            NoConvictionContext ctx = preprocess(request).get("off1");

            assertThat(ctx.unconvictedSentenceCount()).isEqualTo(1L);
            assertThat(ctx.finalCategoryCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("AllOffencesQualifying")
    class AllOffencesQualifying {

        @Test
        void every_offence_meeting_the_condition_should_warn_independently() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "SSO", "d1", "off2")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(
                            offence("off1", 1, "Theft").isConvicted(false),
                            offence("off2", 2, "Burglary").isConvicted(false)));

            Map<String, NoConvictionContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("off1", "off2");
            assertThat(result.get("off1").unconvictedSentenceCount()).isEqualTo(1L);
            assertThat(result.get("off1").warningOffenceIds()).containsExactly("off1");
            assertThat(result.get("off2").unconvictedSentenceCount()).isEqualTo(1L);
            assertThat(result.get("off2").warningOffenceIds()).containsExactly("off2");
        }
    }

    private Map<String, NoConvictionContext> preprocess(final DraftValidationRequest request) {
        return preprocessor.preprocess(request, config);
    }
}
