package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CurfewPeriodPreprocessor}.
 *
 * <p>Covers T011 (CUR/YRC2), T013 (CURE/YRC1), and T015 (AAR) scenarios.
 */
class CurfewPeriodPreprocessorTest {

    private CurfewPeriodPreprocessor preprocessor;
    private PreprocessingDefinition config;

    @BeforeEach
    void setUp() {
        preprocessor = new CurfewPeriodPreprocessor();
        config = PreprocessingDefinition.builder()
                .communityOrderShortCodes(List.of("COEW", "COS", "CONI"))
                .yroShortCodes(List.of("YROEW", "YRONI", "YROFEW", "YROISS", "YROINI"))
                .curfewShortCodes(List.of("CUR", "YRC2"))
                .curfewTagShortCodes(List.of("CURE", "YRC1"))
                .alcoholAbstinenceShortCodes(List.of("AAR"))
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Build a DURATION prompt (e.g. curfewPeriod with Days/Weeks/Months child). */
    private static Prompt durationPrompt(final String promptRef, final String unit,
                                          final String quantity) {
        final Prompt child = Prompt.builder()
                .promptRef(unit)
                .promptValue(quantity)
                .type("INT")
                .build();
        return Prompt.builder()
                .promptRef(promptRef)
                .type("DURATION")
                .childPrompts(List.of(child))
                .build();
    }

    /** Build a DATE or INT prompt. */
    private static Prompt valuePrompt(final String promptRef, final String value) {
        return Prompt.builder().promptRef(promptRef).promptValue(value).build();
    }

    private static ResultLineDto line(final String id, final String shortCode,
                                       final String defId, final String offId,
                                       final Prompt... prompts) {
        final ResultLineDto rl = new ResultLineDto();
        rl.setId(id);
        rl.setShortCode(shortCode);
        rl.setDefendantId(defId);
        rl.setOffenceId(offId);
        rl.setPrompts(List.of(prompts));
        return rl;
    }

    private static DefendantDto defendant(final String id, final String name) {
        final String[] parts = name.split(" ", 2);
        return DefendantDto.builder()
                .id(id)
                .firstName(parts[0])
                .lastName(parts.length > 1 ? parts[1] : "")
                .build();
    }

    private static DraftValidationRequest request(final LocalDate hearingDay,
                                                    final List<ResultLineDto> lines,
                                                    final DefendantDto... defendants) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(hearingDay)
                .resultLines(lines)
                .defendants(List.of(defendants))
                .offences(List.of())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // T011 — CUR / YRC2 scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("T011 — CUR / YRC2 period check")
    class CurYrc2PeriodCheck {

        @Test
        @DisplayName("CUR: end date off by 1 day (too late) → violation context emitted with correct expectedEndDate")
        void cur_endDate_too_late_should_emit_violation() {
            // start=2026-06-01, period=60 Days → expected end=2026-07-30, actual=2026-07-31
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    durationPrompt("curfewPeriod", "Days", "60"),
                                    valuePrompt("endDate", "2026-07-31"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("CUR:d1:off1");
            final CurfewPeriodContext ctx = result.get("CUR:d1:off1");
            assertThat(ctx.expectedEndDate()).isEqualTo("30/07/2026");
            assertThat(ctx.offenceId()).isEqualTo("off1");
            assertThat(ctx.defendantId()).isEqualTo("d1");
            assertThat(ctx.violationCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("CUR: end date off by 1 day (too early) → violation context emitted")
        void cur_endDate_too_early_should_emit_violation() {
            // start=2026-06-01, period=60 Days → expected=2026-07-30, actual=2026-07-29
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    durationPrompt("curfewPeriod", "Days", "60"),
                                    valuePrompt("endDate", "2026-07-29"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("CUR:d1:off1");
            assertThat(result.get("CUR:d1:off1").expectedEndDate()).isEqualTo("30/07/2026");
        }

        @Test
        @DisplayName("CUR: exact match → no context emitted")
        void cur_exact_match_should_not_emit_violation() {
            // start=2026-06-01, period=60 Days → expected=2026-07-30, actual=2026-07-30
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    durationPrompt("curfewPeriod", "Days", "60"),
                                    valuePrompt("endDate", "2026-07-30"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CUR: period in weeks — violation detected using plusWeeks arithmetic")
        void cur_period_in_weeks_should_compute_correctly() {
            // start=2026-06-01, period=4 Weeks → expected=2026-06-28 (4*7=28, -1=27 days → 28/06)
            // 2026-06-01 + 4 weeks = 2026-06-29, minus 1 = 2026-06-28
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    durationPrompt("curfewPeriod", "Weeks", "4"),
                                    valuePrompt("endDate", "2026-06-30"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result.get("CUR:d1:off1").expectedEndDate()).isEqualTo("28/06/2026");
        }

        @Test
        @DisplayName("CUR: period in months — violation detected using plusMonths arithmetic")
        void cur_period_in_months_should_compute_correctly() {
            // start=2026-06-01, period=3 Months → 2026-09-01 minus 1 = 2026-08-31
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    durationPrompt("curfewPeriod", "Months", "3"),
                                    valuePrompt("endDate", "2026-09-01"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result.get("CUR:d1:off1").expectedEndDate()).isEqualTo("31/08/2026");
        }

        @Test
        @DisplayName("CUR: missing startDate prompt → skip + no violation")
        void cur_missing_startDate_should_skip() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    durationPrompt("curfewPeriod", "Days", "60"),
                                    valuePrompt("endDate", "2026-07-31"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CUR: missing curfewPeriod prompt → skip + no violation")
        void cur_missing_curfewPeriod_should_skip() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    valuePrompt("endDate", "2026-07-31"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CUR: DURATION prompt has empty child list → skip + no violation")
        void cur_empty_duration_children_should_skip() {
            final Prompt emptyDuration = Prompt.builder()
                    .promptRef("curfewPeriod")
                    .type("DURATION")
                    .childPrompts(List.of())
                    .build();

            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    emptyDuration,
                                    valuePrompt("endDate", "2026-07-31"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CUR: unknown unit string in DURATION child promptRef → skip + no violation")
        void cur_unknown_unit_should_skip() {
            final Prompt badUnit = Prompt.builder()
                    .promptRef("curfewPeriod")
                    .type("DURATION")
                    .childPrompts(List.of(
                            Prompt.builder().promptRef("Fortnights").promptValue("2").build()))
                    .build();

            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    badUnit,
                                    valuePrompt("endDate", "2026-07-31"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CUR: no CO or YRO parent on same offence → no context emitted, no exception")
        void cur_without_parent_order_should_not_emit_violation() {
            // Only a CUR line, no COEW/YROEW parent
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    durationPrompt("curfewPeriod", "Days", "60"),
                                    valuePrompt("endDate", "2026-07-31"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CUR: curfewPeriod quantity = 0 → skip + no violation")
        void cur_zero_period_quantity_should_skip() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    durationPrompt("curfewPeriod", "Days", "0"),
                                    valuePrompt("endDate", "2026-06-01"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CUR: curfewPeriod quantity is negative → skip + no violation")
        void cur_negative_period_quantity_should_skip() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    durationPrompt("curfewPeriod", "Days", "-10"),
                                    valuePrompt("endDate", "2026-05-22"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CUR: under YROEW parent → violation detected")
        void cur_under_yroew_parent_should_detect_violation() {
            // CUR is also valid under a YRO parent
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("yro", "YROEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cur", "CUR", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    durationPrompt("curfewPeriod", "Days", "60"),
                                    valuePrompt("endDate", "2026-07-31"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("CUR:d1:off1");
        }

        @Test
        @DisplayName("YRC2: violation detected with YROEW parent using same arithmetic as CUR")
        void yrc2_under_yroew_should_get_distinct_key() {
            // start=2026-06-01, period=30 Days → expected=2026-06-30, actual=2026-07-01
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("yro", "YROEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("yrc2", "YRC2", "d1", "off1",
                                    valuePrompt("startDate", "2026-06-01"),
                                    durationPrompt("curfewPeriod", "Days", "30"),
                                    valuePrompt("endDate", "2026-07-01"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("YRC2:d1:off1");
            assertThat(result.get("YRC2:d1:off1").expectedEndDate()).isEqualTo("30/06/2026");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // T013 — CURE / YRC1 scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("T013 — CURE / YRC1 period check")
    class CureYrc1PeriodCheck {

        @Test
        @DisplayName("CURE: end date mismatch under COEW → violation emitted")
        void cure_mismatch_under_coew_should_emit_violation() {
            // startDateOfTagging=2026-06-01, period=90 Days → expected=2026-08-29, actual=2026-08-30
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cure", "CURE", "d1", "off1",
                                    valuePrompt("startDateOfTagging", "2026-06-01"),
                                    durationPrompt("curfewAndElectronicMonitoringPeriod", "Days", "90"),
                                    valuePrompt("endDateOfTagging", "2026-08-30"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("CURE:d1:off1");
            assertThat(result.get("CURE:d1:off1").expectedEndDate()).isEqualTo("29/08/2026");
        }

        @Test
        @DisplayName("CURE: exact match → no violation")
        void cure_exact_match_should_not_emit_violation() {
            // startDateOfTagging=2026-06-01, period=90 Days → expected=2026-08-29, actual=2026-08-29
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cure", "CURE", "d1", "off1",
                                    valuePrompt("startDateOfTagging", "2026-06-01"),
                                    durationPrompt("curfewAndElectronicMonitoringPeriod", "Days", "90"),
                                    valuePrompt("endDateOfTagging", "2026-08-29"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("YRC1: mismatch under YROEW → violation emitted with YRC1 key prefix")
        void yrc1_mismatch_under_yroew_should_emit_violation() {
            // startDateOfTagging=2026-07-10, period=14 Days → expected=2026-07-23, actual=2026-07-24
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("yro", "YROEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("yrc1", "YRC1", "d1", "off1",
                                    valuePrompt("startDateOfTagging", "2026-07-10"),
                                    durationPrompt("curfewAndElectronicMonitoringPeriod", "Days", "14"),
                                    valuePrompt("endDateOfTagging", "2026-07-24"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("YRC1:d1:off1");
            assertThat(result.get("YRC1:d1:off1").expectedEndDate()).isEqualTo("23/07/2026");
        }

        @Test
        @DisplayName("CURE: missing startDateOfTagging → skip, no violation")
        void cure_missing_startDateOfTagging_should_skip() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cure", "CURE", "d1", "off1",
                                    durationPrompt("curfewAndElectronicMonitoringPeriod", "Days", "90"),
                                    valuePrompt("endDateOfTagging", "2026-08-30"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CURE: missing curfewAndElectronicMonitoringPeriod → skip, no violation")
        void cure_missing_period_prompt_should_skip() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 1, 1),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("cure", "CURE", "d1", "off1",
                                    valuePrompt("startDateOfTagging", "2026-06-01"),
                                    valuePrompt("endDateOfTagging", "2026-08-30"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // T015 — AAR scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("T015 — AAR period check")
    class AarPeriodCheck {

        @Test
        @DisplayName("AAR: until date correct → no context emitted")
        void aar_correct_until_should_not_emit_violation() {
            // hearingDay=2026-05-26, days=120 → 2026-05-26+120d=2026-09-23, -1d=2026-09-22
            final DraftValidationRequest req = request(LocalDate.of(2026, 5, 26),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar", "AAR", "d1", "off1",
                                    valuePrompt("numberOfDaysToAbstainFromConsumingAnyAlcohol", "120"),
                                    valuePrompt("until", "2026-09-22"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AAR: until date wrong → context emitted with correct expectedEndDate")
        void aar_wrong_until_should_emit_violation() {
            // hearingDay=2026-05-26, days=120 → 2026-05-26+120d=2026-09-23, -1d=2026-09-22, actual=2026-09-24
            final DraftValidationRequest req = request(LocalDate.of(2026, 5, 26),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar", "AAR", "d1", "off1",
                                    valuePrompt("numberOfDaysToAbstainFromConsumingAnyAlcohol", "120"),
                                    valuePrompt("until", "2026-09-24"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("AAR:d1:off1");
            assertThat(result.get("AAR:d1:off1").expectedEndDate()).isEqualTo("22/09/2026");
        }

        @Test
        @DisplayName("AAR: under YROEW only (no CO parent) → NOT checked (CO-only rule)")
        void aar_under_yroew_only_should_not_check() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 5, 26),
                    List.of(
                            line("yro", "YROEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar", "AAR", "d1", "off1",
                                    valuePrompt("numberOfDaysToAbstainFromConsumingAnyAlcohol", "120"),
                                    valuePrompt("until", "2026-09-24"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AAR: under CONI parent → violation detected")
        void aar_under_coni_parent_should_be_checked() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 5, 26),
                    List.of(
                            line("co", "CONI", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar", "AAR", "d1", "off1",
                                    valuePrompt("numberOfDaysToAbstainFromConsumingAnyAlcohol", "120"),
                                    valuePrompt("until", "2026-09-24"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("AAR:d1:off1");
        }

        @Test
        @DisplayName("AAR: hearingDay null on request → skip + no violation")
        void aar_null_hearingDay_should_skip() {
            final DraftValidationRequest req = request(null,
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar", "AAR", "d1", "off1",
                                    valuePrompt("numberOfDaysToAbstainFromConsumingAnyAlcohol", "120"),
                                    valuePrompt("until", "2026-09-24"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AAR: missing numberOfDaysToAbstain prompt → skip + no violation")
        void aar_missing_days_prompt_should_skip() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 5, 26),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar", "AAR", "d1", "off1",
                                    valuePrompt("until", "2026-09-24"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AAR: missing until prompt → skip + no violation")
        void aar_missing_until_prompt_should_skip() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 5, 26),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar", "AAR", "d1", "off1",
                                    valuePrompt("numberOfDaysToAbstainFromConsumingAnyAlcohol", "120"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AAR: numberOfDaysToAbstain value = '0' → skip + no violation")
        void aar_zero_days_should_skip() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 5, 26),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar", "AAR", "d1", "off1",
                                    valuePrompt("numberOfDaysToAbstainFromConsumingAnyAlcohol", "0"),
                                    valuePrompt("until", "2026-05-25"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AAR: numberOfDaysToAbstain value is negative → skip + no violation")
        void aar_negative_days_should_skip() {
            final DraftValidationRequest req = request(LocalDate.of(2026, 5, 26),
                    List.of(
                            line("co", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar", "AAR", "d1", "off1",
                                    valuePrompt("numberOfDaysToAbstainFromConsumingAnyAlcohol", "-1"),
                                    valuePrompt("until", "2026-05-24"))
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AAR: two offences, one correct one wrong → exactly one context for wrong offence")
        void aar_two_offences_one_correct_one_wrong_should_emit_one_violation() {
            // off1 correct, off2 wrong
            final DraftValidationRequest req = request(LocalDate.of(2026, 5, 26),
                    List.of(
                            line("co1", "COEW", "d1", "off1",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar1", "AAR", "d1", "off1",
                                    valuePrompt("numberOfDaysToAbstainFromConsumingAnyAlcohol", "120"),
                                    valuePrompt("until", "2026-09-22")), // correct (2026-05-26 + 120d - 1d)
                            line("co2", "COEW", "d1", "off2",
                                    valuePrompt("endDate", "2026-12-31")),
                            line("aar2", "AAR", "d1", "off2",
                                    valuePrompt("numberOfDaysToAbstainFromConsumingAnyAlcohol", "120"),
                                    valuePrompt("until", "2026-09-24")) // wrong — should be 2026-09-23
                    ),
                    defendant("d1", "John Smith"));

            final Map<String, CurfewPeriodContext> result = preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("AAR:d1:off2");
        }
    }
}
