package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link YouthRehabilitationContext}.
 */
class YouthRehabilitationContextTest {

    private YouthRehabilitationContext context(
            String name,
            long cur, long cure, long cura,
            List<String> curIds, List<String> cureIds,
            List<String> curaIds, List<String> allIds) {
        return context(name, cur, cure, cura, curIds, cureIds, curaIds, allIds,
                0L, 0L, List.of(), List.of(), Map.of(), Map.of());
    }

    private YouthRehabilitationContext context(
            String name,
            long cur, long cure, long cura,
            List<String> curIds, List<String> cureIds,
            List<String> curaIds, List<String> allIds,
            long curDurationMismatch, long cureDurationMismatch,
            List<String> curDurationMismatchIds, List<String> cureDurationMismatchIds,
            Map<String, String> curCalculatedEndDates, Map<String, String> cureCalculatedEndDates) {
        return new YouthRehabilitationContext(name, cur, cure, cura,
                curIds, cureIds, curaIds, allIds,
                curDurationMismatch, cureDurationMismatch,
                curDurationMismatchIds, cureDurationMismatchIds,
                curCalculatedEndDates, cureCalculatedEndDates);
    }

    @Nested
    @DisplayName("toCelContext")
    class ToCelContext {

        @Test
        void toCelContext_should_return_all_violation_count_keys() {
            YouthRehabilitationContext ctx = context(
                    "John Smith", 2L, 3L, 4L,
                    List.of("cur1"), List.of("cure1"),
                    List.of("cura1"), List.of("cur1", "cure1", "cura1"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("curViolationCount", 2L);
            assertThat(cel).containsEntry("cureViolationCount", 3L);
            assertThat(cel).containsEntry("curaViolationCount", 4L);
        }

        @Test
        void toCelContext_should_return_exactly_five_entries() {
            YouthRehabilitationContext ctx = context(
                    "A", 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of());

            assertThat(ctx.toCelContext()).hasSize(5);
        }

        @Test
        void toCelContext_all_zeros_when_no_violations() {
            YouthRehabilitationContext ctx = context(
                    "B", 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of());

            assertThat(ctx.toCelContext().values()).allMatch(v -> v == 0L);
        }

        @Test
        void toCelContext_should_include_duration_mismatch_counts() {
            YouthRehabilitationContext ctx = context(
                    "C", 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of(),
                    1L, 1L, List.of("off1"), List.of("off2"),
                    Map.of("off1", "21/09/2026"), Map.of("off2", "30/10/2026"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("curDurationMismatchCount", 1L);
            assertThat(cel).containsEntry("cureDurationMismatchCount", 1L);
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet")
    class GetOffenceIdSet {

        private final YouthRehabilitationContext ctx = context(
                "Jane Doe", 1L, 1L, 1L,
                List.of("cur1"), List.of("cure1"),
                List.of("cura1"), List.of("all1", "all2"));

        @Test
        void getOffenceIdSet_curViolationOffenceIds_returns_correct_list() {
            assertThat(ctx.getOffenceIdSet("curViolationOffenceIds")).containsExactly("cur1");
        }

        @Test
        void getOffenceIdSet_cureViolationOffenceIds_returns_correct_list() {
            assertThat(ctx.getOffenceIdSet("cureViolationOffenceIds")).containsExactly("cure1");
        }

        @Test
        void getOffenceIdSet_curaViolationOffenceIds_returns_correct_list() {
            assertThat(ctx.getOffenceIdSet("curaViolationOffenceIds")).containsExactly("cura1");
        }

        @Test
        void getOffenceIdSet_allOffenceIds_returns_all_ids() {
            assertThat(ctx.getOffenceIdSet("allOffenceIds")).containsExactly("all1", "all2");
        }

        @Test
        void getOffenceIdSet_unknown_name_throws_IllegalArgumentException() {
            assertThatThrownBy(() -> ctx.getOffenceIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }

        @Test
        void getOffenceIdSet_curDurationMismatchOffenceIds_returns_correct_list() {
            YouthRehabilitationContext durationCtx = context(
                    "Jane Doe", 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of("all1"),
                    1L, 0L, List.of("dur-cur-1"), List.of(),
                    Map.of("dur-cur-1", "21/09/2026"), Map.of());

            assertThat(durationCtx.getOffenceIdSet("curDurationMismatchOffenceIds"))
                    .containsExactly("dur-cur-1");
        }

        @Test
        void getOffenceIdSet_cureDurationMismatchOffenceIds_returns_correct_list() {
            YouthRehabilitationContext durationCtx = context(
                    "Jane Doe", 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of("all1"),
                    0L, 1L, List.of(), List.of("dur-cure-1"),
                    Map.of(), Map.of("dur-cure-1", "30/10/2026"));

            assertThat(durationCtx.getOffenceIdSet("cureDurationMismatchOffenceIds"))
                    .containsExactly("dur-cure-1");
        }
    }

    @Nested
    @DisplayName("getCalculatedValue")
    class GetCalculatedValue {

        private final YouthRehabilitationContext ctx = context(
                "Jane Doe", 0L, 0L, 0L,
                List.of(), List.of(), List.of(), List.of("all1"),
                1L, 1L, List.of("off1"), List.of("off2"),
                Map.of("off1", "21/09/2026"), Map.of("off2", "30/10/2026"));

        @Test
        void getCalculatedValue_curCalculatedEndDateByOffenceId_returns_expected_date() {
            assertThat(ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("21/09/2026");
        }

        @Test
        void getCalculatedValue_cureCalculatedEndDateByOffenceId_returns_expected_date() {
            assertThat(ctx.getCalculatedValue("cureCalculatedEndDateByOffenceId", "off2"))
                    .isEqualTo("30/10/2026");
        }

        @Test
        void getCalculatedValue_missing_offenceId_returns_null() {
            assertThat(ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "unknown-offence"))
                    .isNull();
        }

        @Test
        void getCalculatedValue_unknown_setName_throws_IllegalArgumentException() {
            assertThatThrownBy(() -> ctx.getCalculatedValue("unknownSet", "off1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }

    @Nested
    @DisplayName("record components")
    class RecordComponents {

        @Test
        void defendantName_and_allOffenceIds_are_accessible_from_record_components() {
            YouthRehabilitationContext ctx = context(
                    "James Bond", 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of("off-x"));

            assertThat(ctx.defendantName()).isEqualTo("James Bond");
            assertThat(ctx.allOffenceIds()).containsExactly("off-x");
        }
    }
}
