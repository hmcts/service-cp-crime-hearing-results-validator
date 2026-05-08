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
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Unit tests for the per-offence preprocessor that drives DR-DISQ-001.
 */
class DisqualificationExtendedTestPreprocessorTest {

    private static final List<String> RELEVANT_CODES = List.of(
            "RT88046", "RT88526", "RT88026", "RT88530", "RT88531");
    private static final List<String> EXCLUDED_SHORT_CODES = List.of(
            "wdrn", "WDRNOFF", "dism", "dine", "dini", "disch", "disc", "ctrof", "iremfile");
    private static final List<String> EXTENDED_TEST_SHORT_CODES = List.of("DDOTE", "DDOTEL");

    private final DisqualificationExtendedTestPreprocessor preprocessor =
            new DisqualificationExtendedTestPreprocessor();

    private final PreprocessingDefinition config = PreprocessingDefinition.builder()
            .type(DisqualificationExtendedTestPreprocessor.QUALIFIER)
            .relevantOffenceCodes(RELEVANT_CODES)
            .excludedFinalShortCodes(EXCLUDED_SHORT_CODES)
            .extendedTestShortCodes(EXTENDED_TEST_SHORT_CODES)
            .build();

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

        @Test
        void unknown_short_code_should_be_treated_as_non_excluded() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "ZZZZ", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
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
    }

    @Nested
    @DisplayName("ExcludedFinalSuppression — Phase 4 / US2")
    class ExcludedFinalSuppression {

        @ParameterizedTest
        @ValueSource(strings = {
                "wdrn", "WDRNOFF", "dism", "dine", "dini",
                "disch", "disc", "ctrof", "iremfile"
        })
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
        @ValueSource(strings = {"DDOTE", "DDOTEL"})
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
