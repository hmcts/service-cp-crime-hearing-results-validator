package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CustodialPreprocessor} and the derived {@link DefendantContext}.
 */
class CustodialPreprocessorTest {

    private final CustodialPreprocessor preprocessor = new CustodialPreprocessor();
    private final PreprocessingDefinition config = PreprocessingDefinition.builder()
            .type("custodial-concurrent-consecutive")
            .filterShortCodes(List.of("IMP", "DTO", "YOI", "extdvs", "extdvsu", "extivs",
                    "STSDY", "specc", "speccc", "speccd"))
            .groupBy("defendant-then-offence")
            .skipWhenGroupCount(1)
            .build();

    /**
     * Verifies the preprocessor skips requests that contain no custodial result lines.
     */
    @Test
    void should_return_empty_for_no_custodial_lines() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "FINE", "d1", "off1"),
                        resultLine("rl2", "EMONE", "d1", "off2")),
                List.of());

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result).isEmpty();
    }

    /**
     * Verifies a single custodial offence is skipped because there is no concurrency ambiguity to
     * analyse.
     */
    @Test
    void should_return_empty_for_single_offence() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1")),
                List.of());

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result).isEmpty();
    }

    /**
     * Verifies offences without relationship data are classified into the no-info bucket after the
     * primary sentence has been claimed.
     */
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

    /**
     * Verifies offences marked both concurrent and consecutive are classified into the has-both
     * bucket.
     */
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

    /**
     * Verifies result lines are grouped independently per defendant when no master defendant id is
     * present.
     */
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

    /**
     * Verifies non-custodial short codes are filtered out before offence grouping is calculated.
     */
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

    /**
     * Verifies the preprocessor populates the defendant display name from the supplied defendant
     * record.
     */
    @Test
    void should_populate_defendant_name() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")),
                List.of(),
                List.of(defendant("d1", "John Smith", null)));
        request.getResultLines().get(1).setIsConcurrent(true);

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result.get("d1").defendantName()).isEqualTo("John Smith");
    }

    /**
     * Verifies grouped defendants inherit the display name from the first member of the master
     * defendant group.
     */
    @Test
    void should_populate_defendant_name_from_first_defendant_in_group() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d2", "off2")),
                List.of(),
                List.of(defendant("d1", "John Smith", "master-1"),
                        defendant("d2", "John Smith", "master-1")));

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result.get("master-1").defendantName()).isEqualTo("John Smith");
    }

    /**
     * Verifies {@link DefendantContext#toCelContext()} exposes the derived count values expected by
     * CEL expressions.
     */
    @Test
    void toCelContext_should_return_count_map() {
        DefendantContext ctx = new DefendantContext(
                "John", 2, 1, 0, 0, 3,
                List.of(), List.of(), List.of(), List.of());

        Map<String, Long> celCtx = ctx.toCelContext();

        assertThat(celCtx).containsEntry("noInfoCount", 2L);
        assertThat(celCtx).containsEntry("hasInfoCount", 1L);
        assertThat(celCtx).containsEntry("hasBothCount", 0L);
        assertThat(celCtx).containsEntry("totalOffences", 3L);
    }

    /**
     * Verifies each named offence-id set can be retrieved from the derived defendant context.
     */
    @Test
    void getOffenceIdSet_should_return_correct_list() {
        DefendantContext ctx = new DefendantContext(
                "John", 0, 0, 0, 0, 0,
                List.of("a"), List.of("b"), List.of("c"), List.of("a", "b", "c"));

        assertThat(ctx.getOffenceIdSet("noInfoOffenceIds")).containsExactly("a");
        assertThat(ctx.getOffenceIdSet("hasInfoOffenceIds")).containsExactly("b");
        assertThat(ctx.getOffenceIdSet("hasBothOffenceIds")).containsExactly("c");
        assertThat(ctx.getOffenceIdSet("allOffenceIds")).containsExactly("a", "b", "c");
    }

    /**
     * Verifies requesting an unknown offence-id set name fails fast.
     */
    @Test
    void getOffenceIdSet_should_throw_for_unknown_set_name() {
        DefendantContext ctx = new DefendantContext(
                "John", 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> ctx.getOffenceIdSet("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown offence set: bogus");
    }

    /**
     * Verifies an empty consecutive-to-offence value is treated the same as missing information.
     */
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

    /**
     * Verifies a blank consecutive-to-offence value is treated the same as missing information.
     */
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

    /**
     * Verifies result lines are grouped under the master defendant id when one is present.
     */
    @Test
    void should_group_by_masterDefendantId_when_present() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d2", "off2")),
                List.of(),
                List.of(defendant("d1", "John", "master-1"),
                        defendant("d2", "John", "master-1")));

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result).hasSize(1);
        assertThat(result).containsKey("master-1");
        DefendantContext ctx = result.get("master-1");
        assertThat(ctx.totalOffences()).isEqualTo(2);
        assertThat(ctx.hasPrimaryCount()).isEqualTo(1);
        assertThat(ctx.noInfoCount()).isEqualTo(1);
        assertThat(ctx.noInfoOffenceIds()).containsExactly("off2");
    }

    /**
     * Verifies the raw defendant id is used as the grouping key when no master defendant id is
     * supplied.
     */
    @Test
    void should_fall_back_to_defendantId_when_masterDefendantId_absent() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")),
                List.of(),
                List.of(defendant("d1", "John", null)));
        request.getResultLines().get(1).setIsConcurrent(true);

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result).hasSize(1);
        assertThat(result).containsKey("d1");
        assertThat(result.get("d1").totalOffences()).isEqualTo(2);
    }

    /**
     * Verifies different master defendant ids are not merged into a single derived context.
     */
    @Test
    void should_not_group_different_masterDefendantIds_together() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d2", "off2")),
                List.of(),
                List.of(defendant("d1", "John", "master-1"),
                        defendant("d2", "Jane", "master-2")));

        Map<String, DefendantContext> result = preprocessor.preprocess(request, config);

        assertThat(result).isEmpty();
    }

    private static DefendantDto defendant(String id, String fullName, String masterDefendantId) {
        String[] parts = fullName.split(" ", 2);
        return DefendantDto.builder()
                .id(id)
                .firstName(parts[0])
                .lastName(parts.length > 1 ? parts[1] : null)
                .masterDefendantId(masterDefendantId)
                .build();
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
        return buildRequest(resultLines, offences, List.of());
    }

    private static DraftValidationRequest buildRequest(List<ResultLineDto> resultLines,
                                                        List<OffenceDto> offences,
                                                        List<DefendantDto> defendants) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(java.time.LocalDate.of(2026, 3, 11))
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(resultLines)
                .defendants(defendants)
                .offences(offences)
                .build();
    }
}
