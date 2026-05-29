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
 * Unit tests for {@link CommunityOrderEndDatePreprocessor} with DR-YRO-001 configuration
 * (YROEW/YRONI/YROFEW/YROISS/YROINI orders; YRC2/YRC1/YRC3 curfew; YRUP1 unpaid work).
 */
class CommunityOrderEndDatePreprocessorYroConfigTest {

    private static final PreprocessingDefinition YRO_CONFIG = PreprocessingDefinition.builder()
            .communityOrderShortCodes(List.of("YROEW", "YRONI", "YROFEW", "YROISS", "YROINI"))
            .curfewShortCodes(List.of("YRC2"))
            .curfewTagShortCodes(List.of("YRC1"))
            .furtherCurfewShortCodes(List.of("YRC3"))
            .alcoholAbstinenceShortCodes(List.of())
            .unpaidWorkShortCodes(List.of("YRUP1"))
            .build();

    private CommunityOrderEndDatePreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        preprocessor = new CommunityOrderEndDatePreprocessor();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResultLineDto orderLine(String id, String shortCode, String defId, String offId,
                                    String endDate) {
        Prompt prompt = new Prompt("endDate", endDate);
        ResultLineDto rl = new ResultLineDto();
        rl.setId(id);
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
        rl.setId(id);
        rl.setShortCode(shortCode);
        rl.setDefendantId(defId);
        rl.setOffenceId(offId);
        rl.setPrompts(List.of(prompt));
        return rl;
    }

    private ResultLineDto noPromptLine(String id, String shortCode, String defId, String offId) {
        ResultLineDto rl = new ResultLineDto();
        rl.setId(id);
        rl.setShortCode(shortCode);
        rl.setDefendantId(defId);
        rl.setOffenceId(offId);
        return rl;
    }

    private DefendantDto defendant(String id, String first, String last) {
        DefendantDto d = new DefendantDto();
        d.setId(id);
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
    @DisplayName("AC2a — YRC2 (Curfew) end date after YRO end date")
    class Ac2aYrc2 {

        @Test
        @DisplayName("YRC2 end date after YROEW end date produces curViolationCount 1")
        void yrc2_end_date_after_yroew_should_produce_curViolationCount_1() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2026-10-30"),
                            requirementLine("rl-yrc2", "YRC2", "d1", "off1", "endDate", "2026-11-30")
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, YRO_CONFIG);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.curViolationCount()).isEqualTo(1L);
            assertThat(ctx.curViolationOffenceIds()).containsExactly("off1");
            assertThat(ctx.cureViolationCount()).isZero();
            assertThat(ctx.curaViolationCount()).isZero();
            assertThat(ctx.upwrViolationCount()).isZero();
        }
    }

    @Nested
    @DisplayName("AC2b — YRC1 (Curfew with electronic monitoring) endDateOfTagging after YRO end date")
    class Ac2bYrc1 {

        @Test
        @DisplayName("YRC1 endDateOfTagging after YRONI end date produces cureViolationCount 1")
        void yrc1_endDateOfTagging_after_yroni_should_produce_cureViolationCount_1() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YRONI", "d1", "off1", "2026-12-01"),
                            requirementLine("rl-yrc1", "YRC1", "d1", "off1", "endDateOfTagging", "2026-12-15")
                    ),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, YRO_CONFIG);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.cureViolationCount()).isEqualTo(1L);
            assertThat(ctx.cureViolationOffenceIds()).containsExactly("off1");
            assertThat(ctx.curViolationCount()).isZero();
        }
    }

    @Nested
    @DisplayName("AC2c — YRC3 (Further curfew requirement made) end date after YRO end date")
    class Ac2cYrc3 {

        @Test
        @DisplayName("YRC3 end date after YROFEW end date produces curaViolationCount 1")
        void yrc3_end_date_after_yrofew_should_produce_curaViolationCount_1() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROFEW", "d1", "off1", "2027-01-01"),
                            requirementLine("rl-yrc3", "YRC3", "d1", "off1", "endDate", "2027-01-15")
                    ),
                    List.of(defendant("d1", "Bob", "Brown")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, YRO_CONFIG);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.curaViolationCount()).isEqualTo(1L);
            assertThat(ctx.curaViolationOffenceIds()).containsExactly("off1");
            assertThat(ctx.curViolationCount()).isZero();
            assertThat(ctx.cureViolationCount()).isZero();
        }
    }

    @Nested
    @DisplayName("AC2 — No curfew child requirements")
    class Ac2NoCurfewRequirements {

        @Test
        @DisplayName("YROISS with no curfew child requirements: all violation counts zero")
        void yroiss_without_curfew_requirements_produces_all_zero_counts() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "YROISS", "d1", "off1", "2026-10-30")),
                    List.of(defendant("d1", "No", "Requirements")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, YRO_CONFIG);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.curViolationCount()).isZero();
            assertThat(ctx.cureViolationCount()).isZero();
            assertThat(ctx.curaViolationCount()).isZero();
            assertThat(ctx.upwrViolationCount()).isZero();
        }
    }

    @Nested
    @DisplayName("AC3 — YRUP1 (Unpaid work) 12-month minimum")
    class Ac3Yrup1 {

        @Test
        @DisplayName("YRUP1 with order under 12 months produces upwrViolationCount 1")
        void yrup1_order_under_12_months_should_produce_upwrViolationCount_1() {
            // hearing 20/05/2026, minEndDate = 19/05/2027, orderEnd = 18/05/2027 → violation
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 5, 20),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-05-18"),
                            noPromptLine("rl-yrup1", "YRUP1", "d1", "off1")
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, YRO_CONFIG);

            CommunityOrderContext ctx = result.get("d1");
            assertThat(ctx.upwrViolationCount()).isEqualTo(1L);
            assertThat(ctx.upwrViolationOffenceIds()).containsExactly("off1");
        }

        @Test
        @DisplayName("YRUP1 at boundary (hearingDay + 12m − 1d = 2027-05-19): no violation")
        void yrup1_at_boundary_12_months_minus_1_day_should_not_violate() {
            // hearing 20/05/2026, minEndDate = 19/05/2027, orderEnd = 19/05/2027 → pass
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 5, 20),
                    List.of(
                            orderLine("rl-order", "YRONI", "d1", "off1", "2027-05-19"),
                            noPromptLine("rl-yrup1", "YRUP1", "d1", "off1")
                    ),
                    List.of(defendant("d1", "Boundary", "Pass")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, YRO_CONFIG);

            assertThat(result.get("d1").upwrViolationCount()).isZero();
        }

        @Test
        @DisplayName("YROINI without YRUP1: upwrViolationCount is zero")
        void yroini_without_yrup1_should_produce_upwrViolationCount_0() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 5, 20),
                    List.of(orderLine("rl-order", "YROINI", "d1", "off1", "2026-06-01")),
                    List.of(defendant("d1", "No", "Unpaid")));

            Map<String, CommunityOrderContext> result = preprocessor.preprocess(req, YRO_CONFIG);

            assertThat(result.get("d1").upwrViolationCount()).isZero();
        }
    }
}
