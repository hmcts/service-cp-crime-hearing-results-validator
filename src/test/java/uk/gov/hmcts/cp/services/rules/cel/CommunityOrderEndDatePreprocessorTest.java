package uk.gov.hmcts.cp.services.rules.cel;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CommunityOrderEndDatePreprocessor}.
 */
class CommunityOrderEndDatePreprocessorTest {

    private CommunityOrderEndDatePreprocessor preprocessor;
    private PreprocessingDefinition config;

    @BeforeEach
    void setUp() {
        preprocessor = new CommunityOrderEndDatePreprocessor();
        config = PreprocessingDefinition.builder()
                .communityOrderShortCodes(List.of("COEW", "COS", "CONI"))
                .curfewShortCodes(List.of("CUR"))
                .curfewTagShortCodes(List.of("CURE"))
                .furtherCurfewShortCodes(List.of("CURA"))
                .alcoholAbstinenceShortCodes(List.of("AAR"))
                .build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResultLineDto orderLine(String id, String shortCode, String defId, String offId,
                                    String endDate) {
        Prompt prompt = new Prompt("endDate", endDate);
        ResultLineDto rl = new ResultLineDto();
        rl.setResultLineId(id);
        rl.setShortCode(shortCode);
        rl.setDefendantId(defId);
        rl.setOffenceId(offId);
        rl.setPrompts(List.of(prompt));
        return rl;
    }

    private ResultLineDto requirementLine(String id, String shortCode, String defId, String offId,
                                           String promptRef, String promptValue) {
        Prompt prompt = new Prompt(promptRef, promptValue);
        ResultLineDto rl = new ResultLineDto();
        rl.setResultLineId(id);
        rl.setShortCode(shortCode);
        rl.setDefendantId(defId);
        rl.setOffenceId(offId);
        rl.setPrompts(List.of(prompt));
        return rl;
    }

    private ResultLineDto requirementLineWithPrompts(String id, String shortCode, String defId,
                                                       String offId, Map<String, String> prompts) {
        ResultLineDto rl = new ResultLineDto();
        rl.setResultLineId(id);
        rl.setShortCode(shortCode);
        rl.setDefendantId(defId);
        rl.setOffenceId(offId);
        rl.setPrompts(prompts.entrySet().stream()
                .map(e -> new Prompt(e.getKey(), e.getValue()))
                .toList());
        return rl;
    }

    private ResultLineDto noPromptLine(String id, String shortCode, String defId, String offId) {
        ResultLineDto rl = new ResultLineDto();
        rl.setResultLineId(id);
        rl.setShortCode(shortCode);
        rl.setDefendantId(defId);
        rl.setOffenceId(offId);
        return rl;
    }

    private DefendantDto defendant(String id, String first, String last) {
        DefendantDto d = new DefendantDto();
        d.setDefendantId(id);
        d.setFirstName(first);
        d.setLastName(last);
        return d;
    }

    private DraftValidationRequest request(LocalDate hearingDay,
                                            List<ResultLineDto> lines,
                                            List<DefendantDto> defendants) {
        DraftValidationRequest req = new DraftValidationRequest();
        req.setHearingId("h1");
        req.setHearingDay(hearingDay);
        req.setResultLines(lines);
        req.setDefendants(defendants);
        return req;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC2a — CUR end date after order end date")
    class Ac2aCur {

        @Test
        void cur_end_date_after_order_end_date_should_produce_curViolationCount_1() {
            // COEW ends 30/10/2026, CUR ends 30/11/2026 → violation
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "COEW", "d1", "off1", "2026-10-30"),
                            requirementLine("rl-cur", "CUR", "d1", "off1", "endDate", "2026-11-30")
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result).containsKey("d1");
            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.curViolationCount()).isEqualTo(1L);
            assertThat(ctx.curViolationOffenceIds()).containsExactly("off1");
            assertThat(ctx.cureViolationCount()).isZero();
            assertThat(ctx.curaViolationCount()).isZero();
            assertThat(ctx.aarViolationCount()).isZero();
        }

        @Test
        void cur_end_date_equal_to_order_end_date_should_not_violate() {
            // Equal dates → no violation (spec A-002)
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "COEW", "d1", "off1", "2026-10-30"),
                            requirementLine("rl-cur", "CUR", "d1", "off1", "endDate", "2026-10-30")
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.curViolationCount()).isZero();
        }

        @Test
        void cur_end_date_before_order_end_date_should_not_violate() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "COEW", "d1", "off1", "2026-12-30"),
                            requirementLine("rl-cur", "CUR", "d1", "off1", "endDate", "2026-11-30")
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curViolationCount()).isZero();
        }
    }

    @Nested
    @DisplayName("AC2b — CURE endDateOfTag after order end date")
    class Ac2bCure {

        @Test
        void cure_endDateOfTag_after_order_should_produce_cureViolationCount_1() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "COS", "d1", "off1", "2026-12-01"),
                            requirementLine("rl-cure", "CURE", "d1", "off1", "endDateOfTagging", "2026-12-15")
                    ),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.cureViolationCount()).isEqualTo(1L);
            assertThat(ctx.cureViolationOffenceIds()).containsExactly("off1");
            assertThat(ctx.curViolationCount()).isZero();
        }
    }

    @Nested
    @DisplayName("AC2c — CURA end date after order end date")
    class Ac2cCura {

        @Test
        void cura_end_date_after_order_should_produce_curaViolationCount_1() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "CONI", "d1", "off1", "2027-01-01"),
                            requirementLine("rl-cura", "CURA", "d1", "off1", "endDate", "2027-01-15")
                    ),
                    List.of(defendant("d1", "Bob", "Brown")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.curaViolationCount()).isEqualTo(1L);
            assertThat(ctx.curaViolationOffenceIds()).containsExactly("off1");
        }
    }

    @Nested
    @DisplayName("AC2d — AAR until date after order end date")
    class Ac2dAar {

        @Test
        void aar_until_after_order_should_produce_aarViolationCount_1() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "COEW", "d1", "off1", "2027-06-01"),
                            requirementLine("rl-aar", "AAR", "d1", "off1", "until", "2027-06-15")
                    ),
                    List.of(defendant("d1", "Sarah", "Green")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.aarViolationCount()).isEqualTo(1L);
            assertThat(ctx.aarViolationOffenceIds()).containsExactly("off1");
        }
    }

    @Nested
    @DisplayName("Multiple violations and multi-offence/defendant")
    class MultipleViolations {

        @Test
        void multiple_requirement_violations_on_same_order_each_counted_independently() {
            // COEW ends 01/06/2027, CUR ends 15/06/2027, AAR until 25/06/2027
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "COEW", "d1", "off1", "2027-06-01"),
                            requirementLine("rl-cur", "CUR", "d1", "off1", "endDate", "2027-06-15"),
                            requirementLine("rl-aar", "AAR", "d1", "off1", "until", "2027-06-25")
                    ),
                    List.of(defendant("d1", "Multi", "Violator")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.curViolationCount()).isEqualTo(1L);
            assertThat(ctx.aarViolationCount()).isEqualTo(1L);
            assertThat(ctx.cureViolationCount()).isZero();
            assertThat(ctx.curaViolationCount()).isZero();
        }

        @Test
        void multiple_offences_only_one_violating_count_is_1() {
            // off1 has CUR violation, off2 does not
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order1", "COEW", "d1", "off1", "2026-10-30"),
                            requirementLine("rl-cur1", "CUR", "d1", "off1", "endDate", "2026-11-30"),
                            orderLine("rl-order2", "COEW", "d1", "off2", "2026-12-30"),
                            requirementLine("rl-cur2", "CUR", "d1", "off2", "endDate", "2026-11-30")
                    ),
                    List.of(defendant("d1", "Two", "Offences")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.curViolationCount()).isEqualTo(1L);
            assertThat(ctx.curViolationOffenceIds()).containsExactly("off1");
        }

        @Test
        void multiple_defendants_only_affected_one_has_nonzero_counts() {
            // d1 valid, d2 violates
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-d1-order", "COEW", "d1", "off1", "2026-12-31"),
                            requirementLine("rl-d1-cur", "CUR", "d1", "off1", "endDate", "2026-11-30"),
                            orderLine("rl-d2-order", "COEW", "d2", "off2", "2026-10-30"),
                            requirementLine("rl-d2-cur", "CUR", "d2", "off2", "endDate", "2026-11-30")
                    ),
                    List.of(
                            defendant("d1", "Valid", "Defendant"),
                            defendant("d2", "Violating", "Defendant")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curViolationCount()).isZero();
            assertThat(result.get("d2").curViolationCount()).isEqualTo(1L);
            assertThat(result.get("d2").curViolationOffenceIds()).containsExactly("off2");
        }
    }

    @Nested
    @DisplayName("Null and missing prompt handling")
    class NullPromptHandling {

        @Test
        void null_prompts_on_community_order_line_should_skip_offence_gracefully() {
            // Community order has no prompts — cannot parse end date, offence AC2/AC3 checks
            // are skipped. Defendant IS included in result map (with zero counts) so that the
            // CEL engine can evaluate and naturally produce no ValidationIssue.
            ResultLineDto orderNoPrompt = noPromptLine("rl-order", "COEW", "d1", "off1");
            ResultLineDto curLine = requirementLine("rl-cur", "CUR", "d1", "off1",
                    "endDate", "2026-11-30");

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderNoPrompt, curLine),
                    List.of(defendant("d1", "No", "Prompts")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            // Defendant present (has community order line) but no violations — CEL won't fire
            assertThat(result.get("d1").curViolationCount()).isZero();
            assertThat(result.get("d1").cureViolationCount()).isZero();
            assertThat(result.get("d1").curaViolationCount()).isZero();
            assertThat(result.get("d1").aarViolationCount()).isZero();
        }

        @Test
        void null_prompt_value_on_requirement_should_skip_gracefully_no_violation() {
            // CUR has null promptValue
            Prompt blankPrompt = new Prompt("endDate", null);
            ResultLineDto curLine = new ResultLineDto();
            curLine.setResultLineId("rl-cur");
            curLine.setShortCode("CUR");
            curLine.setDefendantId("d1");
            curLine.setOffenceId("off1");
            curLine.setPrompts(List.of(blankPrompt));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "COEW", "d1", "off1", "2026-10-30"),
                            curLine
                    ),
                    List.of(defendant("d1", "Null", "Value")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curViolationCount()).isZero();
        }

        @Test
        void blank_prompt_value_on_requirement_should_skip_gracefully_no_violation() {
            ResultLineDto curLine = requirementLine("rl-cur", "CUR", "d1", "off1",
                    "endDate", "   ");

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "COEW", "d1", "off1", "2026-10-30"),
                            curLine
                    ),
                    List.of(defendant("d1", "Blank", "Value")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curViolationCount()).isZero();
        }

        @Test
        void no_community_order_line_for_defendant_should_be_excluded_from_result() {
            // Defendant has only a CUR line, no parent COEW/COS/CONI → skip defendant
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(requirementLine("rl-cur", "CUR", "d1", "off1", "endDate", "2026-11-30")),
                    List.of(defendant("d1", "No", "Order")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result).doesNotContainKey("d1");
        }
    }

    @Nested
    @DisplayName("DUR-CUR — CUR end date does not match Start date + Curfew period - 1 (DD-41655)")
    class DurCur {

        @Test
        void end_date_equal_to_startDate_plus_period_minus_one_should_not_violate() {
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "30",
                    "endDate", "2026-09-30"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-09-30"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void end_date_one_day_early_should_violate_with_correct_calculated_date() {
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "30",
                    "endDate", "2026-09-29"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-09-30"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);
            CommunityOrderContext ctx = result.get("d1");

            assertThat(ctx.curDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.curDurationMismatchOffenceIds()).containsExactly("off1");
            assertThat(ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("30/09/2026");
        }

        @Test
        void end_date_one_day_late_should_violate() {
            // Clerk forgot to subtract 1 day
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "30",
                    "endDate", "2026-10-01"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-10-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isEqualTo(1L);
        }

        @Test
        void missing_startDate_should_skip_gracefully_no_violation() {
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "curfewPeriod", "30",
                    "endDate", "2026-09-29"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-09-30"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void missing_curfewPeriod_should_skip_gracefully_no_violation() {
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "endDate", "2026-09-29"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-09-30"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void missing_endDate_should_skip_gracefully_no_violation() {
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "30"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-09-30"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void unparseable_curfewPeriod_should_skip_gracefully_no_violation() {
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "thirty",
                    "endDate", "2026-09-29"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-09-30"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void curfewPeriod_with_days_suffix_should_parse_and_detect_violation() {
            // Real upstream payload sends curfewPeriod as "90 Days", not a bare integer.
            // startDate(2026-04-01) + period(90) - 1 = 2026-06-29, but recorded endDate is
            // 2026-06-10 -> should violate.
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-04-01",
                    "curfewPeriod", "90 Days",
                    "endDate", "2026-06-10"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);
            CommunityOrderContext ctx = result.get("d1");

            assertThat(ctx.curDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.curDurationMismatchOffenceIds()).containsExactly("off1");
            assertThat(ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("29/06/2026");
        }

        @Test
        void curfewPeriod_with_days_suffix_should_parse_and_not_violate_when_matching() {
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-04-01",
                    "curfewPeriod", "90 Days",
                    "endDate", "2026-06-29"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void curfewPeriod_with_single_day_singular_suffix_should_parse() {
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-04-01",
                    "curfewPeriod", "1 Day",
                    "endDate", "2026-04-01"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void curfewPeriod_with_unrecognized_unit_should_skip_gracefully_no_violation() {
            // Only Days/Weeks/Months are confirmed real-world units (DD-41655 follow-up);
            // anything else falls back to the existing WARN-and-skip behaviour rather than
            // guessing a conversion.
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-04-01",
                    "curfewPeriod", "3 Years",
                    "endDate", "2026-06-10"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void curfewPeriod_with_days_suffix_overflowing_long_should_skip_gracefully_no_violation() {
            // "\d+" has no digit-count limit, so a numerically overflowing value (beyond even
            // Long.MAX_VALUE, 19 digits) must still be caught and skipped, not throw uncaught.
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-04-01",
                    "curfewPeriod", "99999999999999999999 Days",
                    "endDate", "2026-06-10"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void curfewPeriod_with_days_suffix_out_of_localdate_range_should_skip_gracefully_no_violation() {
            // A value that parses fine as a long (well within Long.MAX_VALUE) but, once added
            // to startDate, produces a date beyond LocalDate's supported year range must be
            // caught (DateTimeException) and skipped, not throw uncaught.
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-04-01",
                    "curfewPeriod", "999999999999 Days",
                    "endDate", "2026-06-10"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void curfewPeriod_with_months_suffix_should_parse_and_detect_violation() {
            // Real upstream payload: startDate 2025-12-01, curfewPeriod "1 Months",
            // endDate 2026-02-02. Expected = 2025-12-01 + 1 month - 1 day = 2025-12-31,
            // so the recorded endDate is a genuine mismatch.
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2025-12-01",
                    "curfewPeriod", "1 Months",
                    "endDate", "2026-02-02"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);
            CommunityOrderContext ctx = result.get("d1");

            assertThat(ctx.curDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.curDurationMismatchOffenceIds()).containsExactly("off1");
            assertThat(ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("31/12/2025");
        }

        @Test
        void curfewPeriod_with_months_suffix_should_parse_and_not_violate_when_matching() {
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2025-12-01",
                    "curfewPeriod", "1 Months",
                    "endDate", "2025-12-31"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void curfewPeriod_with_months_suffix_should_use_calendar_aware_arithmetic() {
            // startDate is the last day of January in a non-leap year; a naive "period * 30
            // days" conversion would land on 2026-03-02, but calendar-aware plusMonths clamps
            // to the last valid day of February (28th in 2026), giving 2026-02-27.
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-01-31",
                    "curfewPeriod", "1 Months",
                    "endDate", "2026-02-27"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void curfewPeriod_with_weeks_suffix_should_parse_and_detect_violation() {
            // Real upstream payload sends curfewPeriod as "1 weeks" (lower-case, plural even
            // at count 1). Expected = 2026-04-01 + 1 week - 1 day = 2026-04-07.
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-04-01",
                    "curfewPeriod", "1 weeks",
                    "endDate", "2026-04-10"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);
            CommunityOrderContext ctx = result.get("d1");

            assertThat(ctx.curDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("07/04/2026");
        }

        @Test
        void curfewPeriod_with_weeks_suffix_should_parse_and_not_violate_when_matching() {
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-04-01",
                    "curfewPeriod", "1 weeks",
                    "endDate", "2026-04-07"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-12-01"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        void duration_check_should_still_run_when_order_end_date_is_missing() {
            // Community order has no parseable end date — AC2 checks are skipped for this
            // offence, but the CUR duration-mismatch check does not depend on the order at all.
            ResultLineDto orderNoPrompt = noPromptLine("rl-order", "COEW", "d1", "off1");
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "30",
                    "endDate", "2026-09-29"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderNoPrompt, cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isEqualTo(1L);
            assertThat(result.get("d1").curViolationCount()).isZero();
        }

        @Test
        void offence_failing_both_ac2_and_duration_check_appears_in_both_offence_id_lists() {
            // Order ends 2026-09-15, CUR endDate 2026-09-29 (after order -> AC2a) and
            // does not match startDate(2026-09-01) + period(30) - 1 = 2026-09-30 (-> DUR-CUR)
            ResultLineDto cur = requirementLineWithPrompts("rl-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "30",
                    "endDate", "2026-09-29"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-09-15"), cur),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);
            CommunityOrderContext ctx = result.get("d1");

            assertThat(ctx.curViolationOffenceIds()).containsExactly("off1");
            assertThat(ctx.curDurationMismatchOffenceIds()).containsExactly("off1");
        }

        @Test
        void offence_with_two_mismatching_cur_lines_should_appear_only_once_in_offence_ids() {
            // Two CUR lines on the same offence (e.g. a correction), both mismatching.
            ResultLineDto cur1 = requirementLineWithPrompts("rl-cur-1", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "30",
                    "endDate", "2026-09-29"));
            ResultLineDto cur2 = requirementLineWithPrompts("rl-cur-2", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "60",
                    "endDate", "2026-10-29"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-09-30"), cur1, cur2),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);
            CommunityOrderContext ctx = result.get("d1");

            assertThat(ctx.curDurationMismatchOffenceIds()).containsExactly("off1");
            assertThat(ctx.curDurationMismatchCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("DUR-CURE — CURE end date of tagging does not match duration (DD-41655)")
    class DurCure {

        @Test
        void endDateOfTagging_equal_to_calculated_should_not_violate() {
            ResultLineDto cure = requirementLineWithPrompts("rl-cure", "CURE", "d1", "off1", Map.of(
                    "startDateOfTagging", "2026-09-01",
                    "curfewAndElectronicMonitoringPeriod", "60",
                    "endDateOfTagging", "2026-10-30"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COS", "d1", "off1", "2026-10-30"), cure),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").cureDurationMismatchCount()).isZero();
        }

        @Test
        void endDateOfTagging_mismatch_should_violate_with_correct_calculated_date() {
            ResultLineDto cure = requirementLineWithPrompts("rl-cure", "CURE", "d1", "off1", Map.of(
                    "startDateOfTagging", "2026-09-01",
                    "curfewAndElectronicMonitoringPeriod", "60",
                    "endDateOfTagging", "2026-11-01"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COS", "d1", "off1", "2026-11-01"), cure),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);
            CommunityOrderContext ctx = result.get("d1");

            assertThat(ctx.cureDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.cureDurationMismatchOffenceIds()).containsExactly("off1");
            assertThat(ctx.getCalculatedValue("cureCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("30/10/2026");
        }

        @Test
        void missing_startDateOfTagging_should_skip_gracefully_no_violation() {
            ResultLineDto cure = requirementLineWithPrompts("rl-cure", "CURE", "d1", "off1", Map.of(
                    "curfewAndElectronicMonitoringPeriod", "60",
                    "endDateOfTagging", "2026-11-01"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COS", "d1", "off1", "2026-11-01"), cure),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").cureDurationMismatchCount()).isZero();
        }

        @Test
        void missing_endDateOfTagging_should_skip_gracefully_no_violation() {
            ResultLineDto cure = requirementLineWithPrompts("rl-cure", "CURE", "d1", "off1", Map.of(
                    "startDateOfTagging", "2026-09-01",
                    "curfewAndElectronicMonitoringPeriod", "60"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COS", "d1", "off1", "2026-11-01"), cure),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").cureDurationMismatchCount()).isZero();
        }

        @Test
        void does_not_affect_cur_duration_mismatch_count() {
            ResultLineDto cure = requirementLineWithPrompts("rl-cure", "CURE", "d1", "off1", Map.of(
                    "startDateOfTagging", "2026-09-01",
                    "curfewAndElectronicMonitoringPeriod", "60",
                    "endDateOfTagging", "2026-11-01"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COS", "d1", "off1", "2026-11-01"), cure),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }
    }

    @Nested
    @DisplayName("DUR-AAR — AAR until date does not match hearing date + days - 1 (DD-41655)")
    class DurAar {

        @Test
        void until_equal_to_hearingDay_plus_days_minus_one_should_not_violate() {
            // hearingDay 2026-01-01 + 90 days - 1 day = 2026-03-31
            ResultLineDto aar = requirementLineWithPrompts("rl-aar", "AAR", "d1", "off1", Map.of(
                    "numberOfDaysToAbstainFromConsumingAnyAlcohol", "90",
                    "until", "2026-03-31"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-03-31"), aar),
                    List.of(defendant("d1", "Sarah", "Green")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").aarDurationMismatchCount()).isZero();
        }

        @Test
        void until_mismatch_should_violate_with_correct_calculated_date() {
            // hearingDay 2026-01-01 + 90 days - 1 day = 2026-03-31 (entered as 2026-04-01, wrong)
            ResultLineDto aar = requirementLineWithPrompts("rl-aar", "AAR", "d1", "off1", Map.of(
                    "numberOfDaysToAbstainFromConsumingAnyAlcohol", "90",
                    "until", "2026-04-01"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-04-01"), aar),
                    List.of(defendant("d1", "Sarah", "Green")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);
            CommunityOrderContext ctx = result.get("d1");

            assertThat(ctx.aarDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.aarDurationMismatchOffenceIds()).containsExactly("off1");
            assertThat(ctx.getCalculatedValue("aarCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("31/03/2026");
        }

        @Test
        void missing_numberOfDaysToAbstain_should_skip_gracefully_no_violation() {
            ResultLineDto aar = requirementLineWithPrompts("rl-aar", "AAR", "d1", "off1", Map.of(
                    "until", "2026-04-01"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-04-01"), aar),
                    List.of(defendant("d1", "Sarah", "Green")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").aarDurationMismatchCount()).isZero();
        }

        @Test
        void missing_until_should_skip_gracefully_no_violation() {
            ResultLineDto aar = requirementLineWithPrompts("rl-aar", "AAR", "d1", "off1", Map.of(
                    "numberOfDaysToAbstainFromConsumingAnyAlcohol", "90"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-04-01"), aar),
                    List.of(defendant("d1", "Sarah", "Green")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").aarDurationMismatchCount()).isZero();
        }

        @Test
        void missing_hearingDay_should_skip_gracefully_no_violation() {
            ResultLineDto aar = requirementLineWithPrompts("rl-aar", "AAR", "d1", "off1", Map.of(
                    "numberOfDaysToAbstainFromConsumingAnyAlcohol", "90",
                    "until", "2026-04-01"));

            DraftValidationRequest req = request(
                    null,
                    List.of(orderLine("rl-order", "COEW", "d1", "off1", "2026-04-01"), aar),
                    List.of(defendant("d1", "Sarah", "Green")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result.get("d1").aarDurationMismatchCount()).isZero();
        }
    }

    @Nested
    @DisplayName("DD-41655 cross-cutting: CURA exclusion and multi-defendant isolation")
    class DurationMismatchCrossCutting {

        @Test
        void cura_requirement_with_duration_shaped_prompts_never_produces_any_duration_mismatch() {
            // CURA carries the same-shaped prompts (startDate/curfewPeriod/endDate) that would
            // mismatch if routed through the CUR duration check — but CURA is not one of
            // DD-41655's target requirement types (only CUR, CURE, AAR), so it must be a no-op
            // for all three duration-mismatch counters, even though AC2c (order-vs-CURA) still
            // fires independently.
            ResultLineDto cura = requirementLineWithPrompts("rl-cura", "CURA", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "30",
                    "endDate", "2026-09-29"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "CONI", "d1", "off1", "2026-09-15"), cura),
                    List.of(defendant("d1", "Cura", "Only")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);
            CommunityOrderContext ctx = result.get("d1");

            // AC2c still fires (CURA endDate 2026-09-29 is after order end 2026-09-15).
            assertThat(ctx.curaViolationCount()).isEqualTo(1L);
            // But none of the DD-41655 duration-mismatch counters are touched by CURA.
            assertThat(ctx.curDurationMismatchCount()).isZero();
            assertThat(ctx.cureDurationMismatchCount()).isZero();
            assertThat(ctx.aarDurationMismatchCount()).isZero();
            assertThat(ctx.curDurationMismatchOffenceIds()).isEmpty();
            assertThat(ctx.cureDurationMismatchOffenceIds()).isEmpty();
            assertThat(ctx.aarDurationMismatchOffenceIds()).isEmpty();
        }

        @Test
        void two_defendants_only_one_with_cur_duration_mismatch_should_be_isolated_per_defendant() {
            // d1's CUR end date is correct (start + period - 1) — no mismatch.
            ResultLineDto d1Cur = requirementLineWithPrompts("rl-d1-cur", "CUR", "d1", "off1", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "30",
                    "endDate", "2026-09-30"));
            // d2's CUR end date is wrong (one day early) — mismatch.
            ResultLineDto d2Cur = requirementLineWithPrompts("rl-d2-cur", "CUR", "d2", "off2", Map.of(
                    "startDate", "2026-09-01",
                    "curfewPeriod", "30",
                    "endDate", "2026-09-29"));

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-d1-order", "COEW", "d1", "off1", "2026-09-30"), d1Cur,
                            orderLine("rl-d2-order", "COEW", "d2", "off2", "2026-09-30"), d2Cur
                    ),
                    List.of(
                            defendant("d1", "Clean", "Defendant"),
                            defendant("d2", "Mismatched", "Defendant")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            CommunityOrderContext d1Ctx = result.get("d1");
            CommunityOrderContext d2Ctx = result.get("d2");

            assertThat(d1Ctx.curDurationMismatchCount()).isZero();
            assertThat(d1Ctx.curDurationMismatchOffenceIds()).isEmpty();

            assertThat(d2Ctx.curDurationMismatchCount()).isEqualTo(1L);
            assertThat(d2Ctx.curDurationMismatchOffenceIds()).containsExactly("off2");
            assertThat(d2Ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "off2"))
                    .isEqualTo("30/09/2026");
        }
    }

    @Nested
    @DisplayName("masterDefendantId grouping — mirrors CustodialPreprocessor (DD-41654 fix ported)")
    class MasterDefendantIdGrouping {

        @Test
        @DisplayName("community order and requirement split across two defendantIds sharing a "
                + "masterDefendantId are merged into one context")
        void lines_across_defendantIds_sharing_masterDefendantId_are_merged_into_one_context() {
            // Order recorded under d1, its CUR requirement recorded under linked defendantId d2 —
            // same person via masterDefendantId "master1". Without merging, the order's end date
            // (d1) and the CUR requirement's end date (d2) live in separate contexts and AC2a can
            // never compare them against each other.
            ResultLineDto order = orderLine("rl-order", "COEW", "d1", "off1", "2026-10-30");
            ResultLineDto cur = requirementLine("rl-cur", "CUR", "d2", "off1", "endDate", "2026-11-30");

            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(order, cur),
                    List.of(
                            defendant("d1", "Jaren", "Schroeder").masterDefendantId("master1"),
                            defendant("d2", "Jaren", "Schroeder").masterDefendantId("master1")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result).containsOnlyKeys("master1");
            CommunityOrderContext ctx = result.get("master1");
            assertThat(ctx.defendantName()).isEqualTo("Jaren Schroeder");
            assertThat(ctx.curViolationCount()).isEqualTo(1L);
            assertThat(ctx.curViolationOffenceIds()).containsExactly("off1");
        }

        @Test
        @DisplayName("defendant with blank masterDefendantId falls back to its own defendantId as "
                + "the group key")
        void defendant_with_blank_masterDefendantId_falls_back_to_own_defendantId() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "COEW", "d1", "off1", "2026-10-30"),
                            requirementLine("rl-cur", "CUR", "d1", "off1", "endDate", "2026-11-30")),
                    List.of(defendant("d1", "John", "Smith").masterDefendantId("  ")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, config);

            assertThat(result).containsOnlyKeys("d1");
        }
    }

}
