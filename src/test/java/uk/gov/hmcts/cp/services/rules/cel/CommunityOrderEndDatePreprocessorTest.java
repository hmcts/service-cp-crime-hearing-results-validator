package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.defendant;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.openapi.model.Prompt;

/**
 * Unit tests for {@link CommunityOrderEndDatePreprocessor} covering all three AC families.
 */
class CommunityOrderEndDatePreprocessorTest {

    private static final LocalDate HEARING_DATE = LocalDate.of(2026, 5, 12);

    private static final List<String> COMMUNITY_ORDER_CODES = List.of("COEW", "COS", "CONI");
    private static final List<String> CURFEW_CODES = List.of("CUR");
    private static final List<String> CURFEW_TAG_CODES = List.of("CURE");
    private static final List<String> FURTHER_CURFEW_CODES = List.of("CURA");
    private static final List<String> ALCOHOL_ABSTINENCE_CODES = List.of("AAR");
    private static final List<String> UNPAID_WORK_CODES = List.of("UPWR");

    private final CommunityOrderEndDatePreprocessor preprocessor =
            new CommunityOrderEndDatePreprocessor();

    private final PreprocessingDefinition config = PreprocessingDefinition.builder()
            .type(CommunityOrderEndDatePreprocessor.QUALIFIER)
            .communityOrderShortCodes(COMMUNITY_ORDER_CODES)
            .curfewShortCodes(CURFEW_CODES)
            .curfewTagShortCodes(CURFEW_TAG_CODES)
            .furtherCurfewShortCodes(FURTHER_CURFEW_CODES)
            .alcoholAbstinenceShortCodes(ALCOHOL_ABSTINENCE_CODES)
            .unpaidWorkShortCodes(UNPAID_WORK_CODES)
            .build();

    private Map<String, CommunityOrderContext> preprocess(final DraftValidationRequest request) {
        return preprocessor.preprocess(request, config);
    }

    private DraftValidationRequest buildRequest(final LocalDate hearingDay,
                                                 final List<ResultLineDto> resultLines,
                                                 final List<OffenceDto> offences) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(hearingDay)
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(resultLines)
                .defendants(List.of(
                        defendant("d1", "John", "Smith"),
                        defendant("d2", "Jane", "Doe")))
                .offences(offences)
                .build();
    }

    private ResultLineDto coew(final String id, final String defendantId,
                                final String offenceId, final LocalDate endDate) {
        return withEndDate(resultLine(id, "COEW", defendantId, offenceId), endDate);
    }

    private ResultLineDto req(final String id, final String shortCode,
                               final String defendantId, final String offenceId,
                               final LocalDate endDate) {
        return withEndDate(resultLine(id, shortCode, defendantId, offenceId), endDate);
    }

    private static ResultLineDto withEndDate(final ResultLineDto line, final LocalDate endDate) {
        line.addPromptsItem(Prompt.builder()
                .promptRef("endDate")
                .promptValue(endDate.toString())
                .build());
        return line;
    }

    // -------------------------------------------------------------------------
    // AC2 — order end date before requirement end dates
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("AC2_RequirementEndDateViolation")
    class Ac2RequirementEndDateViolation {

        private static final LocalDate ORDER_END = LocalDate.of(2026, 10, 30);
        private static final LocalDate LATER = LocalDate.of(2026, 11, 30);

        @Test
        void preprocess_coewEndDateBeforeCurEndDate_should_returnCurViolationCountOne() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", ORDER_END),
                            req("rl2", "CUR", "d1", "off1", LATER)),
                    List.of(offence("off1", 1, "Theft")));

            CommunityOrderContext ctx = preprocess(request).get("d1_off1");

            assertThat(ctx.curViolationCount()).isEqualTo(1L);
            assertThat(ctx.cureViolationCount()).isEqualTo(0L);
            assertThat(ctx.curaViolationCount()).isEqualTo(0L);
            assertThat(ctx.aarViolationCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_coewEndDateEqualCurEndDate_should_returnCurViolationCountZero() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", ORDER_END),
                            req("rl2", "CUR", "d1", "off1", ORDER_END)),
                    List.of(offence("off1", 1, "Theft")));

            assertThat(preprocess(request).get("d1_off1").curViolationCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_coewEndDateBeforeCureEndDate_should_returnCureViolationCountOne() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", ORDER_END),
                            req("rl2", "CURE", "d1", "off1", LATER)),
                    List.of(offence("off1", 1, "Theft")));

            assertThat(preprocess(request).get("d1_off1").cureViolationCount()).isEqualTo(1L);
        }

        @Test
        void preprocess_coewEndDateBeforeCuraEndDate_should_returnCuraViolationCountOne() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", ORDER_END),
                            req("rl2", "CURA", "d1", "off1", LATER)),
                    List.of(offence("off1", 1, "Theft")));

            assertThat(preprocess(request).get("d1_off1").curaViolationCount()).isEqualTo(1L);
        }

        @Test
        void preprocess_coewEndDateBeforeAarEndDate_should_returnAarViolationCountOne() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", ORDER_END),
                            req("rl2", "AAR", "d1", "off1", LATER)),
                    List.of(offence("off1", 1, "Theft")));

            assertThat(preprocess(request).get("d1_off1").aarViolationCount()).isEqualTo(1L);
        }

        @Test
        void preprocess_coewEndDateAfterAllRequirementEndDates_should_returnAllAc2ViolationCountsZero() {
            LocalDate earlier = LocalDate.of(2026, 9, 30);
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", ORDER_END),
                            req("rl2", "CUR", "d1", "off1", earlier),
                            req("rl3", "CURE", "d1", "off1", earlier),
                            req("rl4", "CURA", "d1", "off1", earlier),
                            req("rl5", "AAR", "d1", "off1", earlier)),
                    List.of(offence("off1", 1, "Theft")));

            CommunityOrderContext ctx = preprocess(request).get("d1_off1");
            assertThat(ctx.curViolationCount()).isEqualTo(0L);
            assertThat(ctx.cureViolationCount()).isEqualTo(0L);
            assertThat(ctx.curaViolationCount()).isEqualTo(0L);
            assertThat(ctx.aarViolationCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_requirementEndDateNull_should_notCountAsAc2Violation() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", ORDER_END),
                            resultLine("rl2", "CUR", "d1", "off1")),
                    List.of(offence("off1", 1, "Theft")));

            assertThat(preprocess(request).get("d1_off1").curViolationCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_multipleRequirementsViolated_should_returnAllViolationCountsOne() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", ORDER_END),
                            req("rl2", "CUR", "d1", "off1", LATER),
                            req("rl3", "CURE", "d1", "off1", LATER),
                            req("rl4", "CURA", "d1", "off1", LATER),
                            req("rl5", "AAR", "d1", "off1", LATER)),
                    List.of(offence("off1", 1, "Theft")));

            CommunityOrderContext ctx = preprocess(request).get("d1_off1");
            assertThat(ctx.curViolationCount()).isEqualTo(1L);
            assertThat(ctx.cureViolationCount()).isEqualTo(1L);
            assertThat(ctx.curaViolationCount()).isEqualTo(1L);
            assertThat(ctx.aarViolationCount()).isEqualTo(1L);
        }

        @Test
        void preprocess_requirementOnDifferentOffence_should_notCountAsAc2Violation() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", ORDER_END),
                            req("rl2", "CUR", "d1", "off2", LATER)),
                    List.of(offence("off1", 1, "Theft A"),
                            offence("off2", 2, "Theft B")));

            CommunityOrderContext ctx = preprocess(request).get("d1_off1");
            assertThat(ctx).isNotNull();
            assertThat(ctx.curViolationCount()).isEqualTo(0L);
        }
    }

    // -------------------------------------------------------------------------
    // AC3 — UPWR 12-month minimum
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("AC3_UpwrMinimumDuration")
    class Ac3UpwrMinimumDuration {

        private static final LocalDate UNDER_12_MONTHS = HEARING_DATE.plusMonths(12).minusDays(1);
        private static final LocalDate EXACTLY_12_MONTHS = HEARING_DATE.plusMonths(12);
        private static final LocalDate OVER_12_MONTHS = HEARING_DATE.plusMonths(12).plusDays(1);

        @Test
        void preprocess_coewWithUpwrAndEndDateUnder12Months_should_returnUpwrViolationCountOne() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", UNDER_12_MONTHS),
                            resultLine("rl2", "UPWR", "d1", "off1")),
                    List.of(offence("off1", 1, "Theft")));

            assertThat(preprocess(request).get("d1_off1").upwrViolationCount()).isEqualTo(1L);
        }

        @Test
        void preprocess_coewWithUpwrAndEndDateExactly12Months_should_returnUpwrViolationCountZero() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", EXACTLY_12_MONTHS),
                            resultLine("rl2", "UPWR", "d1", "off1")),
                    List.of(offence("off1", 1, "Theft")));

            assertThat(preprocess(request).get("d1_off1").upwrViolationCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_coewWithUpwrAndEndDateOver12Months_should_returnUpwrViolationCountZero() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", OVER_12_MONTHS),
                            resultLine("rl2", "UPWR", "d1", "off1")),
                    List.of(offence("off1", 1, "Theft")));

            assertThat(preprocess(request).get("d1_off1").upwrViolationCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_coewWithNoUpwr_should_returnUpwrViolationCountZero() {
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coew("rl1", "d1", "off1", UNDER_12_MONTHS)),
                    List.of(offence("off1", 1, "Theft")));

            assertThat(preprocess(request).get("d1_off1").upwrViolationCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_multipleDefendantsOnSameOffence_should_returnSeparateContextPerDefendant() {
            ResultLineDto coewD1 = coew("rl1", "d1", "off1", UNDER_12_MONTHS);
            ResultLineDto coewD2 = coew("rl2", "d2", "off1", UNDER_12_MONTHS);
            DraftValidationRequest request = buildRequest(
                    HEARING_DATE,
                    List.of(coewD1, coewD2),
                    List.of(offence("off1", 1, "Theft")));

            Map<String, CommunityOrderContext> result = preprocess(request);

            assertThat(result).containsKeys("d1_off1", "d2_off1");
            assertThat(result.get("d1_off1").defendantName()).isEqualTo("John Smith");
            assertThat(result.get("d2_off1").defendantName()).isEqualTo("Jane Doe");
        }
    }
}
