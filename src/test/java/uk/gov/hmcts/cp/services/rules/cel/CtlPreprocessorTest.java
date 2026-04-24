package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceConviction;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.openapi.model.ValidationRequestWithConvictions;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CtlPreprocessor} and the derived {@link CtlOffenceContext}.
 */
class CtlPreprocessorTest {

    private static final String OFFENCE_ID_1 = "11111111-1111-1111-1111-111111111111";
    private static final String OFFENCE_ID_2 = "22222222-2222-2222-2222-222222222222";

    private final CtlPreprocessor preprocessor = new CtlPreprocessor();
    private final PreprocessingDefinition config = PreprocessingDefinition.builder()
            .type("ctl-offence")
            .filterShortCodes(List.of("RI", "RIYDA", "RIH", "RIB", "RILA", "RILAB", "REMYD"))
            .requiredLabel(Map.of("REMYD", "Remitted for trial"))
            .build();

    @Nested
    @DisplayName("Relevant result detection")
    class RelevantResultDetection {

        @Test
        @DisplayName("Offence with no result lines is not included in output")
        void offence_with_no_result_lines_is_not_included() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Offence with non-relevant result is not included in output")
        void offence_with_non_relevant_result_is_not_included() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Offence with RI result has hasRelevantResult=1")
        void offence_with_ri_result_has_relevant_result() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            assertThat(result).containsKey(OFFENCE_ID_1);
            CtlOffenceContext ctx = (CtlOffenceContext) result.get(OFFENCE_ID_1);
            assertThat(ctx.hasRelevantResult()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Each relevant shortcode (RIYDA, RIH, RIB, RILA, RILAB) is detected")
        void all_relevant_shortcodes_are_detected() {
            for (String code : List.of("RIYDA", "RIH", "RIB", "RILA", "RILAB")) {
                ValidationRequestWithConvictions request = buildRequest(
                        List.of(resultLine("rl1", code, "d1", OFFENCE_ID_1)),
                        List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                        Set.of());

                Map<String, RuleContext> result = preprocessor.preprocess(request, config);

                assertThat(result).as("Expected relevant result for shortcode: " + code)
                        .containsKey(OFFENCE_ID_1);
            }
        }

        @Test
        @DisplayName("Shortcode matching is case-insensitive")
        void shortcode_matching_is_case_insensitive() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "ri", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            assertThat(result).containsKey(OFFENCE_ID_1);
        }
    }

    @Nested
    @DisplayName("REMYD conditional label handling")
    class RemydLabelHandling {

        @Test
        @DisplayName("REMYD with 'Remitted for trial' label counts as relevant result")
        void remyd_with_required_label_counts_as_relevant() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLineWithLabel("rl1", "REMYD", "Remitted for trial", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            assertThat(result).containsKey(OFFENCE_ID_1);
            CtlOffenceContext ctx = (CtlOffenceContext) result.get(OFFENCE_ID_1);
            assertThat(ctx.hasRelevantResult()).isEqualTo(1L);
        }

        @Test
        @DisplayName("REMYD with a different label does not count as relevant result")
        void remyd_with_different_label_is_not_relevant() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLineWithLabel("rl1", "REMYD", "Remitted for sentence", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("REMYD with null label does not count as relevant result")
        void remyd_with_null_label_is_not_relevant() {
            ResultLineDto line = ResultLineDto.builder()
                    .id("rl1").shortCode("REMYD").label(null)
                    .defendantId("d1").offenceId(OFFENCE_ID_1).build();
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(line),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("CTL result detection")
    class CtlResultDetection {

        @Test
        @DisplayName("Offence with RI and CTL results has hasCtlResult=1")
        void offence_with_ctl_result_has_ctl_detected() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID_1),
                            resultLine("rl2", "CTL", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            CtlOffenceContext ctx = (CtlOffenceContext) result.get(OFFENCE_ID_1);
            assertThat(ctx.hasCtlResult()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Offence with RI but no CTL has hasCtlResult=0")
        void offence_without_ctl_result_has_ctl_zero() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            CtlOffenceContext ctx = (CtlOffenceContext) result.get(OFFENCE_ID_1);
            assertThat(ctx.hasCtlResult()).isEqualTo(0L);
        }

        @Test
        @DisplayName("CTL shortcode matching is case-insensitive")
        void ctl_shortcode_matching_is_case_insensitive() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID_1),
                            resultLine("rl2", "ctl", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            CtlOffenceContext ctx = (CtlOffenceContext) result.get(OFFENCE_ID_1);
            assertThat(ctx.hasCtlResult()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Conviction flag handling")
    class ConvictionFlagHandling {

        @Test
        @DisplayName("Offence with convicted=true has isConvicted=1")
        void convicted_offence_has_isConvicted_one() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of(conviction(OFFENCE_ID_1, true)));

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            CtlOffenceContext ctx = (CtlOffenceContext) result.get(OFFENCE_ID_1);
            assertThat(ctx.isConvicted()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Offence with convicted=false has isConvicted=0")
        void not_convicted_offence_has_isConvicted_zero() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of(conviction(OFFENCE_ID_1, false)));

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            CtlOffenceContext ctx = (CtlOffenceContext) result.get(OFFENCE_ID_1);
            assertThat(ctx.isConvicted()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Offence absent from convictions set has isConvicted=0")
        void offence_absent_from_convictions_has_isConvicted_zero() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            CtlOffenceContext ctx = (CtlOffenceContext) result.get(OFFENCE_ID_1);
            assertThat(ctx.isConvicted()).isEqualTo(0L);
        }

        @Test
        @DisplayName("UUID from OffenceConviction is correctly matched against String offence id")
        void uuid_conviction_is_matched_against_string_offence_id() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID_1)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery")),
                    Set.of(conviction(OFFENCE_ID_1, true)));

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            assertThat(((CtlOffenceContext) result.get(OFFENCE_ID_1)).isConvicted()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Multiple offences")
    class MultipleOffences {

        @Test
        @DisplayName("Multiple offences are processed independently")
        void multiple_offences_processed_independently() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID_1),
                            resultLine("rl2", "RI", "d1", OFFENCE_ID_2),
                            resultLine("rl3", "CTL", "d1", OFFENCE_ID_2)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery"),
                            offence(OFFENCE_ID_2, 2, "Assault")),
                    Set.of(conviction(OFFENCE_ID_1, true)));

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            assertThat(result).hasSize(2);
            CtlOffenceContext ctx1 = (CtlOffenceContext) result.get(OFFENCE_ID_1);
            assertThat(ctx1.hasRelevantResult()).isEqualTo(1L);
            assertThat(ctx1.hasCtlResult()).isEqualTo(0L);
            assertThat(ctx1.isConvicted()).isEqualTo(1L);

            CtlOffenceContext ctx2 = (CtlOffenceContext) result.get(OFFENCE_ID_2);
            assertThat(ctx2.hasRelevantResult()).isEqualTo(1L);
            assertThat(ctx2.hasCtlResult()).isEqualTo(1L);
            assertThat(ctx2.isConvicted()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Offences without relevant results are excluded from output")
        void offences_without_relevant_results_are_excluded() {
            ValidationRequestWithConvictions request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID_1),
                            resultLine("rl2", "FINE", "d1", OFFENCE_ID_2)),
                    List.of(offence(OFFENCE_ID_1, 1, "Robbery"),
                            offence(OFFENCE_ID_2, 2, "Speeding")),
                    Set.of());

            Map<String, RuleContext> result = preprocessor.preprocess(request, config);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey(OFFENCE_ID_1);
            assertThat(result).doesNotContainKey(OFFENCE_ID_2);
        }
    }

    @Nested
    @DisplayName("CtlOffenceContext behaviour")
    class CtlOffenceContextBehaviour {

        @Test
        @DisplayName("toCelContext returns all three flags as Long values")
        void toCelContext_returns_all_flags() {
            CtlOffenceContext ctx = new CtlOffenceContext(OFFENCE_ID_1, 1L, 0L, 0L);

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("hasRelevantResult", 1L);
            assertThat(cel).containsEntry("hasCtlResult", 0L);
            assertThat(cel).containsEntry("isConvicted", 0L);
        }

        @Test
        @DisplayName("getOffenceIdSet with 'offenceIds' returns the single offence id")
        void getOffenceIdSet_offenceIds_returns_single_id() {
            CtlOffenceContext ctx = new CtlOffenceContext(OFFENCE_ID_1, 1L, 0L, 0L);

            assertThat(ctx.getOffenceIdSet("offenceIds")).containsExactly(OFFENCE_ID_1);
        }

        @Test
        @DisplayName("getOffenceIdSet with unknown set name throws IllegalArgumentException")
        void getOffenceIdSet_unknown_set_throws() {
            CtlOffenceContext ctx = new CtlOffenceContext(OFFENCE_ID_1, 1L, 0L, 0L);

            assertThatThrownBy(() -> ctx.getOffenceIdSet("bogus"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown offence set: bogus");
        }

        @Test
        @DisplayName("allOffenceIds returns the single offence id")
        void allOffenceIds_returns_single_id() {
            CtlOffenceContext ctx = new CtlOffenceContext(OFFENCE_ID_1, 1L, 0L, 0L);

            assertThat(ctx.allOffenceIds()).containsExactly(OFFENCE_ID_1);
        }

        @Test
        @DisplayName("displayName returns empty string")
        void displayName_returns_empty_string() {
            CtlOffenceContext ctx = new CtlOffenceContext(OFFENCE_ID_1, 1L, 0L, 0L);

            assertThat(ctx.displayName()).isEmpty();
        }
    }

    private static ValidationRequestWithConvictions buildRequest(List<ResultLineDto> resultLines,
                                                                   List<OffenceDto> offences,
                                                                   Set<OffenceConviction> convictions) {
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(LocalDate.of(2026, 3, 11))
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(resultLines)
                .defendants(List.of())
                .offences(offences)
                .build();
        return ValidationRequestWithConvictions.builder()
                .validationRequest(request)
                .offenceConvictions(convictions)
                .build();
    }

    private static ResultLineDto resultLine(String id, String shortCode,
                                             String defendantId, String offenceId) {
        return ResultLineDto.builder()
                .id(id).shortCode(shortCode).label(shortCode + " label")
                .defendantId(defendantId).offenceId(offenceId).build();
    }

    private static ResultLineDto resultLineWithLabel(String id, String shortCode, String label,
                                                      String defendantId, String offenceId) {
        return ResultLineDto.builder()
                .id(id).shortCode(shortCode).label(label)
                .defendantId(defendantId).offenceId(offenceId).build();
    }

    private static OffenceDto offence(String id, int orderIndex, String title) {
        return OffenceDto.builder()
                .id(id).offenceCode("TH68001").offenceTitle(title)
                .orderIndex(orderIndex).caseUrn("32AH9105826").build();
    }

    private static OffenceConviction conviction(String offenceIdStr, boolean convicted) {
        return OffenceConviction.builder()
                .offenceId(UUID.fromString(offenceIdStr))
                .convicted(convicted)
                .build();
    }
}
