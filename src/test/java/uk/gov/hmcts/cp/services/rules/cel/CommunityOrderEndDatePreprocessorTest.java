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

}
