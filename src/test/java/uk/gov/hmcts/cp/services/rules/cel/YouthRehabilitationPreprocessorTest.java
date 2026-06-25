package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;

import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link YouthRehabilitationPreprocessor} with DR-YRO-001 configuration
 * (YROEW/YRONI/YROFEW/YROISS/YROINI orders; YRC2/YRC1/YRC3 curfew).
 */
class YouthRehabilitationPreprocessorTest {

    private static PreprocessingDefinition yroConfig;
    private YouthRehabilitationPreprocessor preprocessor;

    @BeforeAll
    static void setUpConfig() {
        yroConfig = PreprocessingDefinition.builder()
                .communityOrderShortCodes(List.of("YROEW", "YRONI", "YROFEW", "YROISS", "YROINI"))
                .curfewShortCodes(List.of("YRC2"))
                .curfewTagShortCodes(List.of("YRC1"))
                .furtherCurfewShortCodes(List.of("YRC3"))
                .build();
    }

    @BeforeEach
    void setUp() {
        preprocessor = new YouthRehabilitationPreprocessor();
    }


    // ── type qualifier ────────────────────────────────────────────────────────

    @Test
    @DisplayName("type() returns youth-rehabilitation-order qualifier")
    void type_returns_youth_rehabilitation_order_qualifier() {
        assertThat(preprocessor.type()).isEqualTo("youth-rehabilitation-order");
    }

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

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            YouthRehabilitationContext ctx = result.get("d1");
            assertThat(ctx.curViolationCount()).isEqualTo(1L);
            assertThat(ctx.curViolationOffenceIds()).containsExactly("off1");
            assertThat(ctx.cureViolationCount()).isZero();
            assertThat(ctx.curaViolationCount()).isZero();
        }

        @Test
        @DisplayName("YRC2 end date equal to YROEW end date: no violation")
        void yrc2_end_date_equal_to_order_should_not_violate() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2026-11-30"),
                            requirementLine("rl-yrc2", "YRC2", "d1", "off1", "endDate", "2026-11-30")
                    ),
                    List.of(defendant("d1", "Equal", "Date")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result.get("d1").curViolationCount()).isZero();
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

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            YouthRehabilitationContext ctx = result.get("d1");
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

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            YouthRehabilitationContext ctx = result.get("d1");
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
        @DisplayName("YROISS with no curfew child requirements: all AC2 violation counts zero")
        void yroiss_without_curfew_requirements_produces_all_zero_ac2_counts() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(orderLine("rl-order", "YROISS", "d1", "off1", "2026-10-30")),
                    List.of(defendant("d1", "No", "Requirements")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            YouthRehabilitationContext ctx = result.get("d1");
            assertThat(ctx.curViolationCount()).isZero();
            assertThat(ctx.cureViolationCount()).isZero();
            assertThat(ctx.curaViolationCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Defendant isolation — only defendants with YRO lines are included")
    class DefendantIsolation {

        @Test
        @DisplayName("Defendant with no YRO result line is excluded from context map")
        void defendant_without_yro_line_is_excluded_from_context() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 5, 20),
                    List.of(orderLine("rl-order", "YROEW", "d1", "off1", "2027-06-01")),
                    List.of(
                            defendant("d1", "Has", "Yro"),
                            defendant("d2", "No", "Yro")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result).containsKey("d1");
            assertThat(result).doesNotContainKey("d2");
        }
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
}
