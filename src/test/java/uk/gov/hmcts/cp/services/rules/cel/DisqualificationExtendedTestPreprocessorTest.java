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
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;

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
                    List.of(resultLine("rl1", "COEW", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.relevantCount()).isEqualTo(1L);
            assertThat(ctx.excludedFinalCount()).isEqualTo(0L);
            assertThat(ctx.disqExtTestCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
            assertThat(ctx.allOffenceIds()).containsExactly("off1");
        }

        @Test
        void each_relevant_offence_code_should_be_recognised() {
            for (String code : RELEVANT_CODES) {
                DraftValidationRequest request = buildRequest(
                        List.of(resultLine("rl1", "COEW", "d1", "off1")),
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
                    List.of(resultLine("rl1", "ZZZZ", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DisqualificationContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
        }

        @Test
        void two_defendants_charged_with_same_relevant_offence_should_produce_one_context() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "COEW", "d1", "off1"),
                            resultLine("rl2", "COEW", "d2", "off1")),
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
                    List.of(resultLine("rl1", "wdrn", "d1", "off1")),
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
                            resultLine("rl1", "COEW", "d1", "off1"),
                            resultLine("rl2", "DDOTE", "d1", "off1")),
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
                    List.of(resultLine("rl1", "COEW", "d1", "off1")),
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
                    List.of(resultLine("rl1", "coew", "d1", "off1")),
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
                            resultLine("rl1", "COEW", "d1", "off1"),
                            resultLine("rl2", "COEW", "d1", "off2")),
                    List.of(
                            offenceWithCode("off1", 1, "Dangerous driving", "RT88026"),
                            offenceWithCode("off2", 2, "Theft", "TH68001")));

            Map<String, DisqualificationContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("off1", "off2");
            assertThat(result.get("off1").qualifyingCount()).isEqualTo(1L);
            assertThat(result.get("off2").qualifyingCount()).isEqualTo(0L);
        }
    }

    private Map<String, DisqualificationContext> preprocess(final DraftValidationRequest request) {
        return preprocessor.preprocess(request, config);
    }
}
