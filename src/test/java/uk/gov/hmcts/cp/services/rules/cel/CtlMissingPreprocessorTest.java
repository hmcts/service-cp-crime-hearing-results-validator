package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offenceWithCtlFlags;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLineWithPrompt;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;

/**
 * Unit tests for the per-offence preprocessor that drives DR-CTL-003.
 */
class CtlMissingPreprocessorTest {

    private static final List<String> REMAND_SHORT_CODES =
            List.of("RI", "RIYDA", "RIH", "RIB", "RILA", "RILAB", "REMYD");
    private static final List<String> CTL_SHORT_CODES = List.of("CTL");

    private final CtlMissingPreprocessor preprocessor = new CtlMissingPreprocessor();

    private final PreprocessingDefinition config = PreprocessingDefinition.builder()
            .type(CtlMissingPreprocessor.QUALIFIER)
            .remandShortCodes(REMAND_SHORT_CODES)
            .ctlShortCodes(CTL_SHORT_CODES)
            .build();

    private Map<String, CtlOffenceContext> preprocess(DraftValidationRequest request) {
        return preprocessor.preprocess(request, config);
    }

    @Nested
    @DisplayName("PositivePath")
    class PositivePath {

        @Test
        void ri_result_no_existing_ctl_no_ctl_result_not_convicted_should_warn() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", "off1")),
                    List.of(offenceWithCtlFlags("off1", 1, "Theft", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(1L);
            assertThat(ctx.warningOffenceIds()).containsExactly("off1");
            assertThat(ctx.allOffenceIds()).containsExactly("off1");
        }

        @ParameterizedTest(name = "trigger code {0} should produce warning")
        @ValueSource(strings = {"RI", "RIYDA", "RIH", "RIB", "RILA", "RILAB", "REMYD"})
        void each_remand_trigger_code_should_produce_warning(String shortCode) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", shortCode, "d1", "off1")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(1L);
        }

        @Test
        void trigger_short_code_matching_is_case_insensitive() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "ri", "d1", "off1")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(1L);
        }

        @Test
        void other_prompt_ref_should_not_suppress_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RI", "d1", "off1", "endDate", "2026-05-06")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(1L);
        }

        @Test
        void ctldate_prompt_ref_matching_is_case_sensitive_by_design() {
            // Unlike short codes (case-insensitive), promptRef values are Java constants defined by
            // the caller rather than user input, so PreprocessorHelper.hasPromptRef compares them
            // exactly. A differently-cased "ctldate" prompt does not match "CTLDATE" and therefore
            // does not suppress the warning.
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RI", "d1", "off1", "ctldate", "2026-05-06")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("BypassConditions")
    class BypassConditions {

        @Test
        void existing_ctl_record_should_suppress_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", "off1")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", true, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(0L);
            assertThat(ctx.warningOffenceIds()).isEmpty();
        }

        @Test
        void ctl_result_in_current_hearing_should_suppress_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "RI", "d1", "off1"),
                            resultLine("rl2", "CTL", "d1", "off1")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(0L);
        }

        @Test
        void convicted_offence_should_suppress_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", "off1")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, true)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(0L);
        }

        @Test
        void no_trigger_result_should_produce_no_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(0L);
        }

        @Test
        void ctl_result_matching_is_case_insensitive() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "RI", "d1", "off1"),
                            resultLine("rl2", "ctl", "d1", "off1")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(0L);
        }

        @Test
        void ctl_short_code_should_suppress_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "RI", "d1", "off1"),
                            resultLine("rl2", "CTL", "d1", "off1")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(0L);
        }

        @Test
        void ctldate_prompt_on_the_remand_result_line_should_suppress_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RI", "d1", "off1", "CTLDATE", "2026-05-06")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(0L);
            assertThat(ctx.warningOffenceIds()).isEmpty();
        }

        @Test
        void ctldate_prompt_on_a_different_result_line_on_the_same_offence_should_suppress_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "RI", "d1", "off1"),
                            resultLineWithPrompt("rl2", "IMP", "d1", "off1", "CTLDATE", "2026-05-06")),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("MultiOffence")
    class MultiOffence {

        @Test
        void only_breaching_offence_should_receive_warning() {
            OffenceDto breaching = offenceWithCtlFlags("off1", 1, "Offence A", false, false);
            OffenceDto compliant = offenceWithCtlFlags("off2", 2, "Offence B", true, false);

            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "RI", "d1", "off1"),
                            resultLine("rl2", "RI", "d1", "off2")),
                    List.of(breaching, compliant));

            Map<String, CtlOffenceContext> contexts = preprocess(request);

            assertThat(contexts.get("off1").ctlWarningCount()).isEqualTo(1L);
            assertThat(contexts.get("off2").ctlWarningCount()).isEqualTo(0L);
        }

        @Test
        void ctldate_prompt_on_one_offence_should_not_suppress_warning_on_another() {
            OffenceDto offenceA = offenceWithCtlFlags("off1", 1, "Offence A", false, false);
            OffenceDto offenceB = offenceWithCtlFlags("off2", 2, "Offence B", false, false);

            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLineWithPrompt("rl1", "RI", "d1", "off1", "CTLDATE", "2026-05-06"),
                            resultLine("rl2", "RI", "d1", "off2")),
                    List.of(offenceA, offenceB));

            Map<String, CtlOffenceContext> contexts = preprocess(request);

            assertThat(contexts.get("off1").ctlWarningCount()).isEqualTo(0L);
            assertThat(contexts.get("off2").ctlWarningCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("EdgeCases")
    class EdgeCases {

        @Test
        void offence_with_no_result_lines_should_produce_no_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(),
                    List.of(offenceWithCtlFlags("off1", 1, "Offence", false, false)));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(0L);
        }

        @Test
        void null_hasExistingCtlRecord_should_default_to_false_and_not_suppress_warning() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off1")
                    .offenceCode("TH68001")
                    .offenceTitle("Offence")
                    .orderIndex(1)
                    .hasExistingCtlRecord(null)
                    .isConvicted(false)
                    .build();
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", "off1")),
                    List.of(offence));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(1L);
        }

        @Test
        void null_isConvicted_should_default_to_false_and_not_suppress_warning() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off1")
                    .offenceCode("TH68001")
                    .offenceTitle("Offence")
                    .orderIndex(1)
                    .hasExistingCtlRecord(false)
                    .isConvicted(null)
                    .build();
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", "off1")),
                    List.of(offence));

            CtlOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.ctlWarningCount()).isEqualTo(1L);
        }
    }
}
