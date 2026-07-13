package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CommunityOrderContext}.
 */
class CommunityOrderContextTest {

    private CommunityOrderContext context(
            String name,
            long cur, long cure, long cura, long aar,
            List<String> curIds, List<String> cureIds, List<String> curaIds,
            List<String> aarIds, List<String> allIds) {
        return new CommunityOrderContext(name, cur, cure, cura, aar,
                curIds, cureIds, curaIds, aarIds, allIds,
                0L, 0L, 0L, List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of());
    }

    @Nested
    @DisplayName("toCelContext")
    class ToCelContext {

        @Test
        void toCelContext_should_return_all_four_violation_count_keys() {
            CommunityOrderContext ctx = context(
                    "John Smith", 1L, 2L, 3L, 4L,
                    List.of("off1"), List.of("off2"), List.of("off3"),
                    List.of("off4"), List.of("off1", "off2", "off3", "off4"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("curViolationCount", 1L);
            assertThat(cel).containsEntry("cureViolationCount", 2L);
            assertThat(cel).containsEntry("curaViolationCount", 3L);
            assertThat(cel).containsEntry("aarViolationCount", 4L);
        }

        @Test
        void toCelContext_should_return_exactly_seven_entries() {
            CommunityOrderContext ctx = context(
                    "A", 0L, 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of(), List.of());

            assertThat(ctx.toCelContext()).hasSize(7);
        }

        @Test
        void toCelContext_all_zeros_when_no_violations() {
            CommunityOrderContext ctx = context(
                    "B", 0L, 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of(), List.of());

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel.values()).allMatch(v -> v == 0L);
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet")
    class GetOffenceIdSet {

        private final CommunityOrderContext ctx = context(
                "Jane Doe", 1L, 1L, 1L, 1L,
                List.of("cur1"), List.of("cure1"), List.of("cura1"),
                List.of("aar1"), List.of("all1", "all2"));

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
        void getOffenceIdSet_aarViolationOffenceIds_returns_correct_list() {
            assertThat(ctx.getOffenceIdSet("aarViolationOffenceIds")).containsExactly("aar1");
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
    }

    @Nested
    @DisplayName("record components")
    class RecordComponents {

        @Test
        void defendantName_and_allOffenceIds_are_accessible_from_record_components() {
            CommunityOrderContext ctx = context(
                    "James Bond", 0L, 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of(), List.of("off-x"));

            assertThat(ctx.defendantName()).isEqualTo("James Bond");
            assertThat(ctx.allOffenceIds()).containsExactly("off-x");
        }
    }

    @Nested
    @DisplayName("duration-mismatch fields (DD-41655)")
    class DurationMismatch {

        private final CommunityOrderContext ctx = new CommunityOrderContext(
                "Jane Doe", 0L, 0L, 0L, 0L,
                List.of(), List.of(), List.of(), List.of(), List.of("off1", "off2", "off3"),
                1L, 1L, 1L,
                List.of("off1"), List.of("off2"), List.of("off3"),
                Map.of("off1", "30/09/2026"),
                Map.of("off2", "30/10/2026"),
                Map.of("off3", "29/11/2026"));

        @Test
        void toCelContext_should_include_all_three_duration_mismatch_counts() {
            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("curDurationMismatchCount", 1L);
            assertThat(cel).containsEntry("cureDurationMismatchCount", 1L);
            assertThat(cel).containsEntry("aarDurationMismatchCount", 1L);
        }

        @Test
        void toCelContext_should_return_exactly_seven_entries() {
            assertThat(ctx.toCelContext()).hasSize(7);
        }

        @Test
        void getOffenceIdSet_curDurationMismatchOffenceIds_returns_correct_list() {
            assertThat(ctx.getOffenceIdSet("curDurationMismatchOffenceIds")).containsExactly("off1");
        }

        @Test
        void getOffenceIdSet_cureDurationMismatchOffenceIds_returns_correct_list() {
            assertThat(ctx.getOffenceIdSet("cureDurationMismatchOffenceIds")).containsExactly("off2");
        }

        @Test
        void getOffenceIdSet_aarDurationMismatchOffenceIds_returns_correct_list() {
            assertThat(ctx.getOffenceIdSet("aarDurationMismatchOffenceIds")).containsExactly("off3");
        }

        @Test
        void getCalculatedValue_curCalculatedEndDateByOffenceId_returns_expected_date() {
            assertThat(ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "off1"))
                    .isEqualTo("30/09/2026");
        }

        @Test
        void getCalculatedValue_cureCalculatedEndDateByOffenceId_returns_expected_date() {
            assertThat(ctx.getCalculatedValue("cureCalculatedEndDateByOffenceId", "off2"))
                    .isEqualTo("30/10/2026");
        }

        @Test
        void getCalculatedValue_aarCalculatedEndDateByOffenceId_returns_expected_date() {
            assertThat(ctx.getCalculatedValue("aarCalculatedEndDateByOffenceId", "off3"))
                    .isEqualTo("29/11/2026");
        }

        @Test
        void getCalculatedValue_missing_offence_id_returns_null() {
            assertThat(ctx.getCalculatedValue("curCalculatedEndDateByOffenceId", "unknown-offence"))
                    .isNull();
        }

        @Test
        void getCalculatedValue_unknown_set_name_throws_IllegalArgumentException() {
            assertThatThrownBy(() -> ctx.getCalculatedValue("unknownSet", "off1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }
}
