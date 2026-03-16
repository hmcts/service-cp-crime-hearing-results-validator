package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustodialPreprocessorTest {

    private final CustodialPreprocessor preprocessor = new CustodialPreprocessor();
    private final PreprocessingDefinition config = PreprocessingDefinition.builder()
            .type("custodial-concurrent-consecutive")
            .filterShortCodes(List.of("IMP", "DTO", "YOI", "extdvs", "extdvsu", "extivs",
                    "STSDY", "specc", "speccc", "speccd"))
            .groupBy("defendant-then-offence")
            .skipWhenGroupCount(1)
            .build();

    @Test
    void should_return_empty_for_no_custodial_lines() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "FINE", "d1", "off1"),
                        resultLine("rl2", "EMONE", "d1", "off2")),
                List.of());

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_for_single_offence() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1")),
                List.of());

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result).isEmpty();
    }

    @Test
    void should_classify_no_info_offences() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of());
        request.getResultLines().get(2).setIsConcurrent(true);

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result).containsKey("d1");
        DefendantContext ctx = result.get("d1");
        assertThat(ctx.noInfoCount()).isEqualTo(1);
        assertThat(ctx.hasInfoCount()).isEqualTo(1);
        assertThat(ctx.hasBothCount()).isEqualTo(0);
        assertThat(ctx.hasPrimaryCount()).isEqualTo(1);
        assertThat(ctx.noInfoOffenceIds()).containsExactly("off2");
    }

    @Test
    void should_classify_has_both_offences() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")),
                List.of());
        request.getResultLines().get(1).setIsConcurrent(true);
        request.getResultLines().get(1).setConsecutiveToOffence("off1");

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        DefendantContext ctx = result.get("d1");
        assertThat(ctx.hasBothCount()).isEqualTo(1);
        assertThat(ctx.hasBothOffenceIds()).containsExactly("off2");
        assertThat(ctx.noInfoCount()).isEqualTo(0);
        assertThat(ctx.hasPrimaryCount()).isEqualTo(1);
    }

    @Test
    void should_group_by_defendant() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d2", "off3"),
                        resultLine("rl4", "IMP", "d2", "off4")),
                List.of());
        request.getResultLines().get(1).setIsConcurrent(true);
        request.getResultLines().get(3).setIsConcurrent(true);

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result).hasSize(2);
        assertThat(result.get("d1").noInfoCount()).isEqualTo(0);
        assertThat(result.get("d1").hasPrimaryCount()).isEqualTo(1);
        assertThat(result.get("d2").noInfoCount()).isEqualTo(0);
        assertThat(result.get("d2").hasPrimaryCount()).isEqualTo(1);
    }

    @Test
    void should_filter_by_short_codes() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "FINE", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of());
        request.getResultLines().get(2).setIsConcurrent(true);

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        DefendantContext ctx = result.get("d1");
        assertThat(ctx.totalOffences()).isEqualTo(2);
        assertThat(ctx.allOffenceIds()).containsExactly("off1", "off3");
    }

    @Test
    void toCelContext_should_return_count_map() {
        DefendantContext ctx = new DefendantContext(
                2, 1, 0, 0, 3,
                List.of(), List.of(), List.of(), List.of());

        Map<String, Long> celCtx = ctx.toCelContext();

        assertThat(celCtx).containsEntry("noInfoCount", 2L);
        assertThat(celCtx).containsEntry("hasInfoCount", 1L);
        assertThat(celCtx).containsEntry("hasBothCount", 0L);
        assertThat(celCtx).containsEntry("totalOffences", 3L);
    }

    @Test
    void getOffenceIdSet_should_return_correct_list() {
        DefendantContext ctx = new DefendantContext(
                0, 0, 0, 0, 0,
                List.of("a"), List.of("b"), List.of("c"), List.of("a", "b", "c"));

        assertThat(ctx.getOffenceIdSet("noInfoOffenceIds")).containsExactly("a");
        assertThat(ctx.getOffenceIdSet("hasInfoOffenceIds")).containsExactly("b");
        assertThat(ctx.getOffenceIdSet("hasBothOffenceIds")).containsExactly("c");
        assertThat(ctx.getOffenceIdSet("allOffenceIds")).containsExactly("a", "b", "c");
    }

    @Test
    void getOffenceIdSet_should_throw_for_unknown_set_name() {
        DefendantContext ctx = new DefendantContext(
                0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> ctx.getOffenceIdSet("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown offence set: bogus");
    }

    @Test
    void should_treat_empty_consecutiveToOffence_as_no_info() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")),
                List.of());
        request.getResultLines().get(1).setConsecutiveToOffence("");

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        DefendantContext ctx = result.get("d1");
        assertThat(ctx.noInfoCount()).isEqualTo(1);
        assertThat(ctx.hasPrimaryCount()).isEqualTo(1);
        assertThat(ctx.noInfoOffenceIds()).containsExactly("off2");
    }

    @Test
    void should_treat_blank_consecutiveToOffence_as_no_info() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")),
                List.of());
        request.getResultLines().get(1).setConsecutiveToOffence("   ");

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        DefendantContext ctx = result.get("d1");
        assertThat(ctx.noInfoCount()).isEqualTo(1);
        assertThat(ctx.hasPrimaryCount()).isEqualTo(1);
        assertThat(ctx.noInfoOffenceIds()).containsExactly("off2");
    }

    private static ResultLineDto resultLine(String id, String shortCode, String defendantId, String offenceId) {
        return ResultLineDto.builder()
                .id(id)
                .shortCode(shortCode)
                .label(shortCode + " label")
                .defendantId(defendantId)
                .offenceId(offenceId)
                .build();
    }

    private static DraftValidationRequest buildRequest(List<ResultLineDto> resultLines, List<OffenceDto> offences) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(java.time.LocalDate.of(2026, 3, 11))
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(resultLines)
                .defendants(List.of())
                .offences(offences)
                .build();
    }
}
