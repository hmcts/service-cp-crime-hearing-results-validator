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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper;

/**
 * Unit tests for {@link ConditionalBailPreprocessor} (DR-URG-008).
 *
 * <p>The {@code bailEndingShortCodes} list is read from the real {@code DR-URG-008.yaml} via
 * {@link RuleDefinitionLoader}, so {@link #config} always reflects the production YAML. The
 * baseline pin test {@link #bailEndingShortCodes_should_match_the_known_baseline_exactly()} catches
 * any silent addition/removal of codes in the YAML without updating this suite.
 */
class ConditionalBailPreprocessorTest {

    private static final RuleDefinition RULE_DEFINITION =
            RuleDefinitionLoader.load("rules/DR-URG-008.yaml");

    private static final List<String> BAIL_ENDING_SHORT_CODES =
            List.copyOf(RULE_DEFINITION.preprocessing().bailEndingShortCodes());

    private static final List<String> EXPECTED_BAIL_ENDING_SHORT_CODES = List.of(
            "DS", "RI", "RIYDA", "RIH", "RIB", "RILA", "RILAB", "REMYD", "WOFN");

    private final ConditionalBailPreprocessor preprocessor = new ConditionalBailPreprocessor();

    private final PreprocessingDefinition config = RULE_DEFINITION.preprocessing();

    @Test
    @DisplayName("DR-URG-008.yaml's bailEndingShortCodes must exactly match the known baseline")
    void bailEndingShortCodes_should_match_the_known_baseline_exactly() {
        assertThat(BAIL_ENDING_SHORT_CODES)
                .as("DR-URG-008.yaml bailEndingShortCodes must exactly match the expected baseline"
                        + " — a code was added or removed in the YAML without this test's baseline"
                        + " being updated to match")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_BAIL_ENDING_SHORT_CODES);
    }

    static List<String> bailEndingShortCodes() {
        return BAIL_ENDING_SHORT_CODES;
    }

    private static OffenceDto cbOffence(final String id, final int n, final String title) {
        return offence(id, n, title).defendantId("d1").bailStatus(OffenceDto.BailStatusEnum.B);
    }

    @Nested
    @DisplayName("FireScenarios — US1 — warning context emitted when all CB offences are bail-ended")
    class FireScenarios {

        @Test
        @DisplayName("AC1 — Category F result on CB offence, no URGENT → context emitted")
        void category_f_result_on_cb_offence_no_urgent_should_emit_context() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(cbOffence("off-1", 1, "Robbery")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.conditionalBailOffenceCount()).isEqualTo(1L);
            assertThat(ctx.bailEndedCount()).isEqualTo(1L);
            assertThat(ctx.hasUrgentCount()).isEqualTo(0L);
            assertThat(ctx.allOffenceIds()).containsExactly("off-1");
        }

        @Test
        @DisplayName("AC2 — DS short code on CB offence, no URGENT → context emitted")
        void ds_short_code_on_cb_offence_no_urgent_should_emit_context() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DS", "d1", "off-1")),
                    List.of(cbOffence("off-1", 1, "Robbery")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.conditionalBailOffenceCount()).isEqualTo(1L);
            assertThat(ctx.bailEndedCount()).isEqualTo(1L);
            assertThat(ctx.hasUrgentCount()).isEqualTo(0L);
        }

        @ParameterizedTest
        @ValueSource(strings = {"RI", "RIYDA", "RIH", "RIB", "RILA", "RILAB", "REMYD"})
        // DS, RI, RIYDA, RIH, RIB, RILA, RILAB, REMYD, WOFN
        @DisplayName("AC3 — RI family short codes on CB offence, no URGENT → context emitted")
        void ri_family_short_codes_on_cb_offence_no_urgent_should_emit_context(final String code) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", code, "d1", "off-1")),
                    List.of(cbOffence("off-1", 1, "Robbery")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.bailEndedCount())
                    .as("bail-ending code %s should increment bailEndedCount", code)
                    .isEqualTo(1L);
            assertThat(ctx.hasUrgentCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("AC4 — WOFN short code on CB offence, no URGENT → context emitted")
        void wofn_short_code_on_cb_offence_no_urgent_should_emit_context() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "WOFN", "d1", "off-1")),
                    List.of(cbOffence("off-1", 1, "Robbery")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.conditionalBailOffenceCount()).isEqualTo(1L);
            assertThat(ctx.bailEndedCount()).isEqualTo(1L);
            assertThat(ctx.hasUrgentCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("AC5 — mixed bail-ending types (Category F + DS + RI) across CB offences, no URGENT")
        void mixed_bail_ending_types_across_cb_offences_no_urgent_should_count_all() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "DS", "d1", "off-2"),
                            resultLine("rl3", "RI", "d1", "off-3")),
                    List.of(
                            cbOffence("off-1", 1, "Robbery"),
                            cbOffence("off-2", 2, "Assault"),
                            cbOffence("off-3", 3, "Theft")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.conditionalBailOffenceCount()).isEqualTo(3L);
            assertThat(ctx.bailEndedCount()).isEqualTo(3L);
            assertThat(ctx.hasUrgentCount()).isEqualTo(0L);
            assertThat(ctx.allOffenceIds()).containsExactlyInAnyOrder("off-1", "off-2", "off-3");
        }

        @ParameterizedTest
        @MethodSource("uk.gov.hmcts.cp.services.rules.cel.ConditionalBailPreprocessorTest#bailEndingShortCodes")
        @DisplayName("each bail-ending short code from YAML should be recognised as bail-ending")
        void each_bail_ending_short_code_should_increment_bail_ended_count(final String code) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", code, "d1", "off-1")),
                    List.of(cbOffence("off-1", 1, "Robbery")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.bailEndedCount())
                    .as("bail-ending short code %s should increment bailEndedCount", code)
                    .isEqualTo(1L);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ds", "rEmYd", "wOfN", "Ri", "rIyDa"})
        @DisplayName("bail-ending short codes are matched case-insensitively")
        void mixed_case_bail_ending_short_codes_should_be_recognised(final String mixedCase) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", mixedCase, "d1", "off-1")),
                    List.of(cbOffence("off-1", 1, "Robbery")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.bailEndedCount())
                    .as("mixed-case code %s should still be recognised as bail-ending", mixedCase)
                    .isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("UrgentSuppression — US2 — URGENT present on CB offence suppresses hasUrgentCount")
    class UrgentSuppression {

        @Test
        @DisplayName("URGENT on a CB offence that is also bail-ended → hasUrgentCount == 1")
        void urgent_on_cb_offence_sets_has_urgent_count() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "DS", "d1", "off-1"),
                            resultLine("rl2", "URGENT", "d1", "off-1")),
                    List.of(cbOffence("off-1", 1, "Robbery")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.bailEndedCount()).isEqualTo(1L);
            assertThat(ctx.hasUrgentCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("URGENT on one CB offence of two suppresses overall hasUrgentCount")
        void urgent_on_one_of_two_cb_offences_sets_has_urgent_count() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "DS", "d1", "off-1"),
                            resultLine("rl2", "URGENT", "d1", "off-1"),
                            resultLine("rl3", "RI", "d1", "off-2")),
                    List.of(
                            cbOffence("off-1", 1, "Robbery"),
                            cbOffence("off-2", 2, "Assault")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.conditionalBailOffenceCount()).isEqualTo(2L);
            assertThat(ctx.bailEndedCount()).isEqualTo(2L);
            assertThat(ctx.hasUrgentCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("URGENT case-insensitive — 'urgent' should also be recognised")
        void urgent_short_code_is_matched_case_insensitively() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "DS", "d1", "off-1"),
                            resultLine("rl2", "urgent", "d1", "off-1")),
                    List.of(cbOffence("off-1", 1, "Robbery")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.hasUrgentCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("NotAllBailEnded — bail-ended count less than CB offence count → no CEL fire")
    class NotAllBailEnded {

        @Test
        @DisplayName("one of two CB offences not bail-ended → bailEndedCount < conditionalBailOffenceCount")
        void one_cb_offence_not_bail_ended_should_leave_bail_ended_count_below_total() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "DS", "d1", "off-1"),
                            resultLine("rl2", "PLEA", "d1", "off-2")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(
                            cbOffence("off-1", 1, "Robbery"),
                            cbOffence("off-2", 2, "Assault")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.conditionalBailOffenceCount()).isEqualTo(2L);
            assertThat(ctx.bailEndedCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("NoConditionalBailOffences — no CB offences → no context emitted")
    class NoConditionalBailOffences {

        @Test
        @DisplayName("offence with non-B bailStatus → no context")
        void non_cb_offence_should_produce_no_context() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DS", "d1", "off-1")),
                    List.of(offence("off-1", 1, "Robbery")));

            Map<String, ConditionalBailContext> result = preprocess(request);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("offence with null bailStatus → no context")
        void null_bail_status_offence_should_produce_no_context() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DS", "d1", "off-1")),
                    List.of(offence("off-1", 1, "Robbery").bailStatus(null)));

            Map<String, ConditionalBailContext> result = preprocess(request);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("MultiDefendant — only qualifying defendant emits a context")
    class MultiDefendant {

        @Test
        @DisplayName("two defendants — d1 all CB bail-ended, d2 CB offence unresulted → d1 count 2/2, d2 absent")
        void two_defendants_d1_all_cb_bail_ended_d2_cb_unresulted_should_produce_d1_context_only() {
            // d1 has result lines for off-1 and off-2 (both CB, bail-ended, defendantId=d1).
            // d2 owns off-3 (CB, unresulted, defendantId=d2); d2 has no result lines.
            // off-3 is attributed to d2 via defendantId and must NOT inflate d1's count.
            final DraftValidationRequest request = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(LocalDate.of(2026, 3, 11))
                    .courtType(DraftValidationRequest.CourtTypeEnum.CROWN)
                    .resultLines(List.of(
                            resultLine("rl1", "DS", "d1", "off-1"),
                            resultLine("rl2", "DS", "d1", "off-2")))
                    .defendants(List.of(
                            defendant("d1", "Alex", "Jones"),
                            defendant("d2", "Sam", "Smith")))
                    .offences(List.of(
                            cbOffence("off-1", 1, "Robbery"),
                            cbOffence("off-2", 2, "Burglary"),
                            cbOffence("off-3", 3, "Theft").defendantId("d2")))
                    .build();

            final Map<String, ConditionalBailContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("d1");
            assertThat(result.get("d1").conditionalBailOffenceCount()).isEqualTo(2L);
            assertThat(result.get("d1").bailEndedCount()).isEqualTo(2L);
            assertThat(result.get("d1").hasUrgentCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("explicit defendantId — d2 has one CB bail-ended + one CB unresulted → d2 count 2/1, d1 count 1/1")
        void two_defendants_explicit_defendantId_d2_mixed_cb_should_inflate_d2_count() {
            // d1: off-1 (CB, bail-ended) via result line → count=1, bailEnded=1 → warning should fire
            // d2: off-2 (CB, bail-ended) via result line + off-3 (CB, unresulted, defendantId=d2)
            //   After T007: off-3 attributed via defendantId → d2 count=2, bailEnded=1 → warning suppressed
            //   Before T007 (no defendantId usage): d2 count=1, bailEnded=1 → this assertion fails
            final DraftValidationRequest request = DraftValidationRequest.builder()
                    .hearingId("h-explicit-id")
                    .hearingDay(LocalDate.of(2026, 9, 14))
                    .courtType(DraftValidationRequest.CourtTypeEnum.CROWN)
                    .resultLines(List.of(
                            resultLine("rl1", "DS", "d1", "off-1"),
                            resultLine("rl2", "DS", "d2", "off-2")))
                    .defendants(List.of(
                            defendant("d1", "Alex", "Jones"),
                            defendant("d2", "Sam", "Smith")))
                    .offences(List.of(
                            cbOffence("off-1", 1, "Robbery").defendantId("d1"),
                            cbOffence("off-2", 2, "Burglary").defendantId("d2"),
                            cbOffence("off-3", 3, "Theft").defendantId("d2")))
                    .build();

            final Map<String, ConditionalBailContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("d1", "d2");
            assertThat(result.get("d1").conditionalBailOffenceCount()).isEqualTo(1L);
            assertThat(result.get("d1").bailEndedCount()).isEqualTo(1L);
            assertThat(result.get("d2").conditionalBailOffenceCount()).isEqualTo(2L);
            assertThat(result.get("d2").bailEndedCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("two defendants — only the one with bail-ended CB offences emits context with hasUrgentCount 0")
        void only_defendant_with_bail_ended_cb_offences_emits_context() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "DS", "d1", "off-1"),
                            resultLine("rl2", "PLEA", "d2", "off-2")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(
                            cbOffence("off-1", 1, "Robbery"),
                            offence("off-2", 2, "Assault")));

            Map<String, ConditionalBailContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("d1");
            assertThat(result.get("d1").bailEndedCount()).isEqualTo(1L);
            assertThat(result.get("d1").hasUrgentCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("defendant A all CB bail-ended, defendant B has unresulted CB — A's context unaffected")
        void defendant_a_warned_when_defendant_b_has_unresulted_cb_offence() {
            // Defendant A: off-1 (CB, bail-ended via DS)
            // Defendant B: off-2 (CB, bail-ended via DS) + off-3 (CB, no result)
            // B's unresulted off-3 must NOT leak into A's context.
            DraftValidationRequest request = DraftValidationRequest.builder()
                    .hearingId("h-multi")
                    .hearingDay(LocalDate.of(2026, 9, 12))
                    .courtType(DraftValidationRequest.CourtTypeEnum.CROWN)
                    .defendants(List.of(
                            defendant("d1", "Alex", "Jones"),
                            defendant("d2", "Robin", "Taylor")))
                    .offences(List.of(
                            cbOffence("off-1", 1, "Robbery"),
                            offence("off-2", 2, "Assault").defendantId("d2")
                                    .bailStatus(OffenceDto.BailStatusEnum.B),
                            offence("off-3", 3, "Theft").defendantId("d2")
                                    .bailStatus(OffenceDto.BailStatusEnum.B)))
                    .resultLines(List.of(
                            resultLine("rl1", "DS", "d1", "off-1"),
                            resultLine("rl2", "DS", "d2", "off-2")))
                    .build();

            Map<String, ConditionalBailContext> result = preprocess(request);

            // A: 1 CB offence, 1 bail-ended → warning fires (bailEndedCount == conditionalBailOffenceCount)
            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").conditionalBailOffenceCount()).isEqualTo(1L);
            assertThat(result.get("d1").bailEndedCount()).isEqualTo(1L);

            // B: 2 CB offences, 1 bail-ended → warning does NOT fire (bailEndedCount < conditionalBailOffenceCount)
            assertThat(result).containsKey("d2");
            assertThat(result.get("d2").conditionalBailOffenceCount()).isEqualTo(2L);
            assertThat(result.get("d2").bailEndedCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("defendant B has no result lines at all — unresulted CB offence must not leak into defendant A")
        void defendant_b_no_result_lines_unresulted_cb_must_not_affect_defendant_a() {
            // Defendant A: off-1 (CB, bail-ended via DS)
            // Defendant B: off-2 (CB, no result lines at all) — B has zero result lines,
            // so B won't appear in linesByGroup. off-2's defendantId="d2" scopes it to B only.
            // With 2 deduplicated defendants the single-group fallback must NOT fire.
            DraftValidationRequest request = DraftValidationRequest.builder()
                    .hearingId("h-multi-no-lines")
                    .hearingDay(LocalDate.of(2026, 9, 12))
                    .courtType(DraftValidationRequest.CourtTypeEnum.CROWN)
                    .defendants(List.of(
                            defendant("d1", "Alex", "Jones"),
                            defendant("d2", "Robin", "Taylor")))
                    .offences(List.of(
                            cbOffence("off-1", 1, "Robbery"),
                            offence("off-2", 2, "Assault").defendantId("d2")
                                    .bailStatus(OffenceDto.BailStatusEnum.B)))
                    .resultLines(List.of(
                            resultLine("rl1", "DS", "d1", "off-1")))
                    .build();

            Map<String, ConditionalBailContext> result = preprocess(request);

            // A: 1 CB offence, 1 bail-ended — warning fires
            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").conditionalBailOffenceCount()).isEqualTo(1L);
            assertThat(result.get("d1").bailEndedCount()).isEqualTo(1L);

            // B has no result lines → no context emitted (the preprocessor skips groups with no CB result lines)
            assertThat(result).doesNotContainKey("d2");
        }
    }

    @Nested
    @DisplayName("NullSafety — null collections are treated as empty")
    class NullSafety {

        @Test
        @DisplayName("null offences list → empty result")
        void null_offences_list_should_return_empty_map() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DS", "d1", "off-1")),
                    null);

            Map<String, ConditionalBailContext> result = preprocess(request);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null resultLines → empty result")
        void null_result_lines_should_return_empty_map() {
            DraftValidationRequest request = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .resultLines(null)
                    .offences(List.of(cbOffence("off-1", 1, "Robbery")))
                    .defendants(List.of(DefendantDto.builder()
                            .defendantId("d1").firstName("John").lastName("Smith").build()))
                    .build();

            Map<String, ConditionalBailContext> result = preprocess(request);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("EdgeCases")
    class EdgeCases {

        @Test
        @DisplayName("EC1 — CB offence not bail-ended + non-CB offence bail-ended → bailEndedCount < conditionalBailOffenceCount")
        void ec1_non_cb_bail_ended_must_not_count_toward_cb_bail_ended() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "PLEA", "d1", "off-cb")
                                    .category(ResultLineDto.CategoryEnum.I),
                            resultLine("rl2", "DS", "d1", "off-non-cb")),
                    List.of(
                            cbOffence("off-cb", 1, "Robbery"),
                            offence("off-non-cb", 2, "Assault")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.conditionalBailOffenceCount()).isEqualTo(1L);
            assertThat(ctx.bailEndedCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("EC2 — CB offence with zero result lines → treated as not bail-ended")
        void ec2_cb_offence_with_no_result_lines_should_not_count_as_bail_ended() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DS", "d1", "off-1")),
                    List.of(
                            cbOffence("off-1", 1, "Robbery"),
                            cbOffence("off-2", 2, "Assault")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.conditionalBailOffenceCount()).isEqualTo(2L);
            assertThat(ctx.bailEndedCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("EC3 — URGENT on non-CB offence must NOT set hasUrgentCount for CB offences")
        void ec3_urgent_on_non_cb_offence_must_not_suppress_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "DS", "d1", "off-cb"),
                            resultLine("rl2", "URGENT", "d1", "off-non-cb")),
                    List.of(
                            cbOffence("off-cb", 1, "Robbery"),
                            offence("off-non-cb", 2, "Assault")));

            ConditionalBailContext ctx = preprocess(request).get("d1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.conditionalBailOffenceCount()).isEqualTo(1L);
            assertThat(ctx.bailEndedCount()).isEqualTo(1L);
            assertThat(ctx.hasUrgentCount())
                    .as("URGENT on a non-CB offence must not set hasUrgentCount")
                    .isEqualTo(0L);
        }

        @Test
        @DisplayName("EC4 — single defendant with explicit defendantId on offences — unresulted CB suppresses warning")
        void ec4_single_defendant_explicit_defendantId_on_unresulted_cb_should_suppress_warning() {
            // Regression guard: when offences carry explicit defendantId the new algorithm must
            // still attribute an unresulted CB offence to its defendant and count it,
            // keeping conditionalBailOffenceCount > bailEndedCount to suppress the warning.
            final DraftValidationRequest request = DraftValidationRequest.builder()
                    .hearingId("h-ec4")
                    .hearingDay(LocalDate.of(2026, 9, 14))
                    .courtType(DraftValidationRequest.CourtTypeEnum.CROWN)
                    .resultLines(List.of(resultLine("rl1", "DS", "d1", "off-1")))
                    .defendants(List.of(defendant("d1", "Alex", "Jones")))
                    .offences(List.of(
                            cbOffence("off-1", 1, "Robbery").defendantId("d1"),
                            cbOffence("off-2", 2, "Burglary").defendantId("d1")))
                    .build();

            final Map<String, ConditionalBailContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("d1");
            assertThat(result.get("d1").conditionalBailOffenceCount()).isEqualTo(2L);
            assertThat(result.get("d1").bailEndedCount()).isEqualTo(1L);
        }
    }

    @ParameterizedTest
    @EnumSource(value = DraftValidationRequest.CourtTypeEnum.class, names = {"MAGISTRATES", "YOUTH"})
    @NullSource
    void non_crown_hearing_should_not_produce_context(final DraftValidationRequest.CourtTypeEnum courtType) {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "DS", "d1", "off-1")),
                List.of(cbOffence("off-1", 1, "Robbery"))).courtType(courtType);

        assertThat(preprocess(request)).isEmpty();
    }

    private static DraftValidationRequest buildRequest(final List<ResultLineDto> lines,
                                                       final List<OffenceDto> offences) {
        return ValidationRuleTestHelper.buildRequest(lines, offences, DraftValidationRequest.CourtTypeEnum.CROWN);
    }

    private Map<String, ConditionalBailContext> preprocess(final DraftValidationRequest request) {
        return preprocessor.preprocess(request, config);
    }
}
