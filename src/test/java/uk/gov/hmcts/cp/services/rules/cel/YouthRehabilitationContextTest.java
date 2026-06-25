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
        return new YouthRehabilitationContext(name, cur, cure, cura,
                curIds, cureIds, curaIds, allIds);
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
        void toCelContext_should_return_exactly_three_entries() {
            YouthRehabilitationContext ctx = context(
                    "A", 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of());

            assertThat(ctx.toCelContext()).hasSize(3);
        }

        @Test
        void toCelContext_all_zeros_when_no_violations() {
            YouthRehabilitationContext ctx = context(
                    "B", 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of());

            assertThat(ctx.toCelContext().values()).allMatch(v -> v == 0L);
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
