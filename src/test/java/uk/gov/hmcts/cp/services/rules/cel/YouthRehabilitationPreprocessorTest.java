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
                .yroOrderShortCodes(List.of("YROEW", "YRONI", "YROFEW", "YROISS", "YROINI"))
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

        @Test
        @DisplayName("YRC2 end date before YROEW end date: no violation")
        void yrc2_end_date_before_order_should_not_violate() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2026-08-20"),
                            requirementLine("rl-yrc2", "YRC2", "d1", "off1", "endDate", "2026-07-01")
                    ),
                    List.of(defendant("d1", "Before", "Date")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result.get("d1").curViolationCount()).isZero();
        }
    }

    @Nested
    @DisplayName("AC2 — multiple curfew types breach simultaneously")
    class Ac2AllTypesSimultaneously {

        @Test
        @DisplayName("YRC1, YRC2 and YRC3 all breaching the same order each produce their own violation count")
        void yrc1_yrc2_yrc3_all_breach_simultaneously_should_produce_all_three_violation_counts() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2026-01-10"),
                            requirementLine("rl-yrc2", "YRC2", "d1", "off1", "endDate", "2026-02-10"),
                            requirementLine("rl-yrc1", "YRC1", "d1", "off1", "endDateOfTagging", "2026-03-10"),
                            requirementLine("rl-yrc3", "YRC3", "d1", "off1", "endDate", "2026-04-10")
                    ),
                    List.of(defendant("d1", "Sam", "Taylor")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            YouthRehabilitationContext ctx = result.get("d1");
            assertThat(ctx.curViolationCount()).isEqualTo(1L);
            assertThat(ctx.curViolationOffenceIds()).containsExactly("off1");
            assertThat(ctx.cureViolationCount()).isEqualTo(1L);
            assertThat(ctx.cureViolationOffenceIds()).containsExactly("off1");
            assertThat(ctx.curaViolationCount()).isEqualTo(1L);
            assertThat(ctx.curaViolationOffenceIds()).containsExactly("off1");
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

        @Test
        @DisplayName("YROEW with no endDate prompt and breaching YRC2: no violation (null orderEndDate skips AC2)")
        void yroew_without_end_date_prompt_with_breaching_yrc2_should_produce_zero_violations() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLineNoPrompts("rl-order", "YROEW", "d1", "off1"),
                            requirementLine("rl-yrc2", "YRC2", "d1", "off1", "endDate", "2026-11-30")
                    ),
                    List.of(defendant("d1", "Missing", "Prompt")));

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

    @Nested
    @DisplayName("masterDefendantId grouping — mirrors CustodialPreprocessor")
    class MasterDefendantIdGrouping {

        @Test
        @DisplayName("YRO lines split across two defendantIds sharing a masterDefendantId are merged into one context")
        void yro_lines_across_defendantIds_sharing_masterDefendantId_are_merged_into_one_context() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2026-01-10"),
                            requirementLine("rl-yrc2", "YRC2", "d2", "off1", "endDate", "2026-02-10")
                    ),
                    List.of(
                            defendant("d1", "Sam", "Taylor").masterDefendantId("master1"),
                            defendant("d2", "Sam", "Taylor").masterDefendantId("master1")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result).containsOnlyKeys("master1");
            YouthRehabilitationContext ctx = result.get("master1");
            assertThat(ctx.curViolationCount()).isEqualTo(1L);
            assertThat(ctx.curViolationOffenceIds()).containsExactly("off1");
        }

        @Test
        @DisplayName("defendantId with blank masterDefendantId falls back to its own defendantId as the group key")
        void defendant_with_blank_masterDefendantId_falls_back_to_own_defendantId() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2026-10-30"),
                            requirementLine("rl-yrc2", "YRC2", "d1", "off1", "endDate", "2026-11-30")
                    ),
                    List.of(defendant("d1", "John", "Smith").masterDefendantId("  ")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result).containsOnlyKeys("d1");
        }
    }

    @Nested
    @DisplayName("DUR-YRC2 — Curfew requirement end date does not match calculated duration")
    class DurYrc2 {

        @Test
        @DisplayName("end date equal to Start date + Curfew period − 1 day: no violation")
        void end_date_matching_formula_should_not_violate() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("startDate", "2026-09-01"),
                                    new Prompt("curfewPeriod", "21 Days"),
                                    new Prompt("endDate", "2026-09-21"))
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        @DisplayName("end date one day early produces violation with correct calculated end date")
        void end_date_one_day_early_should_violate_with_calculated_date() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("startDate", "2026-09-01"),
                                    new Prompt("curfewPeriod", "21 Days"),
                                    new Prompt("endDate", "2026-09-20"))
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            YouthRehabilitationContext ctx = result.get("d1");
            assertThat(ctx.curDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.curDurationMismatchOffenceIds()).containsExactly("off1");
            assertThat(ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("21/09/2026");
        }

        @Test
        @DisplayName("end date one day late (forgot to subtract 1 day) produces violation")
        void end_date_one_day_late_should_violate() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("startDate", "2026-09-01"),
                                    new Prompt("curfewPeriod", "21 Days"),
                                    new Prompt("endDate", "2026-09-22"))
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            YouthRehabilitationContext ctx = result.get("d1");
            assertThat(ctx.curDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("21/09/2026");
        }

        @Test
        @DisplayName("missing startDate prompt: skip gracefully, no violation")
        void missing_start_date_should_skip_gracefully() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("curfewPeriod", "21 Days"),
                                    new Prompt("endDate", "2026-09-21"))
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        @DisplayName("missing curfewPeriod prompt: skip gracefully, no violation")
        void missing_period_should_skip_gracefully() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("startDate", "2026-09-01"),
                                    new Prompt("endDate", "2026-09-21"))
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        @DisplayName("unparseable curfewPeriod prompt: skip gracefully, no violation")
        void unparseable_period_should_skip_gracefully() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("startDate", "2026-09-01"),
                                    new Prompt("curfewPeriod", "not-a-period"),
                                    new Prompt("endDate", "2026-09-21"))
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        @DisplayName("missing endDate prompt: skip gracefully, no violation")
        void missing_end_date_should_skip_gracefully() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("startDate", "2026-09-01"),
                                    new Prompt("curfewPeriod", "21 Days"))
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }

        @Test
        @DisplayName("duration check still runs when the YRO's own end date prompt is missing")
        void duration_check_runs_even_when_order_end_date_missing() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLineNoPrompts("rl-order", "YROEW", "d1", "off1"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("startDate", "2026-09-01"),
                                    new Prompt("curfewPeriod", "21 Days"),
                                    new Prompt("endDate", "2026-09-20"))
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            YouthRehabilitationContext ctx = result.get("d1");
            assertThat(ctx.curDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.curViolationCount()).isZero();
        }

        @Test
        @DisplayName("period expressed in weeks computes the calendar-correct expected date")
        void period_in_weeks_should_compute_correct_date() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("startDate", "2026-09-01"),
                                    new Prompt("curfewPeriod", "3 Weeks"),
                                    new Prompt("endDate", "2026-09-20"))
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result.get("d1").getCalculatedValue("curCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("21/09/2026");
        }

        @Test
        @DisplayName("period expressed in months across a month-end boundary uses calendar arithmetic")
        void period_in_months_across_month_end_boundary_should_compute_correct_date() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("startDate", "2026-01-31"),
                                    new Prompt("curfewPeriod", "1 Months"),
                                    new Prompt("endDate", "2026-02-27"))
                    ),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            // 31 Jan + 1 month = 28 Feb (2026 not a leap year); minus 1 day = 27 Feb (no violation)
            assertThat(result.get("d1").curDurationMismatchCount()).isZero();
        }
    }

    @Nested
    @DisplayName("DUR-YRC1 — Curfew with electronic monitoring end date of tagging does not match calculated duration")
    class DurYrc1 {

        @Test
        @DisplayName("endDateOfTagging equal to Start date of tagging + period − 1 day: no violation")
        void end_date_of_tagging_matching_formula_should_not_violate() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YRONI", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc1", "YRC1", "d1", "off1",
                                    new Prompt("startDateOfTagging", "2026-09-01"),
                                    new Prompt("curfewAndElectronicMonitoringPeriod", "60 Days"),
                                    new Prompt("endDateOfTagging", "2026-10-30"))
                    ),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result.get("d1").cureDurationMismatchCount()).isZero();
        }

        @Test
        @DisplayName("endDateOfTagging mismatch produces violation with correct calculated end date")
        void end_date_of_tagging_mismatch_should_violate_with_calculated_date() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YRONI", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc1", "YRC1", "d1", "off1",
                                    new Prompt("startDateOfTagging", "2026-09-01"),
                                    new Prompt("curfewAndElectronicMonitoringPeriod", "60 Days"),
                                    new Prompt("endDateOfTagging", "2026-11-01"))
                    ),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            YouthRehabilitationContext ctx = result.get("d1");
            assertThat(ctx.cureDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.cureDurationMismatchOffenceIds()).containsExactly("off1");
            assertThat(ctx.getCalculatedValue("cureCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("30/10/2026");
        }

        @Test
        @DisplayName("missing startDateOfTagging prompt: skip gracefully, no violation")
        void missing_start_date_of_tagging_should_skip_gracefully() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YRONI", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc1", "YRC1", "d1", "off1",
                                    new Prompt("curfewAndElectronicMonitoringPeriod", "60 Days"),
                                    new Prompt("endDateOfTagging", "2026-10-30"))
                    ),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            assertThat(result.get("d1").cureDurationMismatchCount()).isZero();
        }

        @Test
        @DisplayName("YRC2 and YRC1 duration mismatches on the same defendant are independent")
        void yrc2_and_yrc1_duration_mismatches_are_independent() {
            DraftValidationRequest req = request(
                    LocalDate.of(2026, 1, 1),
                    List.of(
                            orderLine("rl-order", "YROEW", "d1", "off1", "2027-01-01"),
                            requirementLineWithPrompts("rl-yrc2", "YRC2", "d1", "off1",
                                    new Prompt("startDate", "2026-09-01"),
                                    new Prompt("curfewPeriod", "21 Days"),
                                    new Prompt("endDate", "2026-09-20")),
                            requirementLineWithPrompts("rl-yrc1", "YRC1", "d1", "off1",
                                    new Prompt("startDateOfTagging", "2026-09-01"),
                                    new Prompt("curfewAndElectronicMonitoringPeriod", "60 Days"),
                                    new Prompt("endDateOfTagging", "2026-11-01"))
                    ),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, YouthRehabilitationContext> result = preprocessor.preprocess(req, yroConfig);

            YouthRehabilitationContext ctx = result.get("d1");
            assertThat(ctx.curDurationMismatchCount()).isEqualTo(1L);
            assertThat(ctx.cureDurationMismatchCount()).isEqualTo(1L);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResultLineDto orderLineNoPrompts(String id, String shortCode, String defId, String offId) {
        ResultLineDto rl = new ResultLineDto();
        rl.setResultLineId(id);
        rl.setShortCode(shortCode);
        rl.setDefendantId(defId);
        rl.setOffenceId(offId);
        return rl;
    }

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
                                                     String offId, Prompt... prompts) {
        ResultLineDto rl = new ResultLineDto();
        rl.setResultLineId(id);
        rl.setShortCode(shortCode);
        rl.setDefendantId(defId);
        rl.setOffenceId(offId);
        rl.setPrompts(List.of(prompts));
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
