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
            long cur, long cure, long cura, long aar, long upwr,
            List<String> curIds, List<String> cureIds, List<String> curaIds,
            List<String> aarIds, List<String> upwrIds, List<String> allIds) {
        return new CommunityOrderContext(name, cur, cure, cura, aar, upwr,
                curIds, cureIds, curaIds, aarIds, upwrIds, allIds);
    }

    @Nested
    @DisplayName("toCelContext")
    class ToCelContext {

        @Test
        void toCelContext_should_return_all_five_violation_count_keys() {
            CommunityOrderContext ctx = context(
                    "John Smith", 1L, 2L, 3L, 4L, 5L,
                    List.of("off1"), List.of("off2"), List.of("off3"),
                    List.of("off4"), List.of("off5"), List.of("off1", "off2", "off3", "off4", "off5"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("curViolationCount", 1L);
            assertThat(cel).containsEntry("cureViolationCount", 2L);
            assertThat(cel).containsEntry("curaViolationCount", 3L);
            assertThat(cel).containsEntry("aarViolationCount", 4L);
            assertThat(cel).containsEntry("upwrViolationCount", 5L);
        }

        @Test
        void toCelContext_should_return_exactly_five_entries() {
            CommunityOrderContext ctx = context(
                    "A", 0L, 0L, 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

            assertThat(ctx.toCelContext()).hasSize(5);
        }

        @Test
        void toCelContext_all_zeros_when_no_violations() {
            CommunityOrderContext ctx = context(
                    "B", 0L, 0L, 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel.values()).allMatch(v -> v == 0L);
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet")
    class GetOffenceIdSet {

        private final CommunityOrderContext ctx = context(
                "Jane Doe", 1L, 1L, 1L, 1L, 1L,
                List.of("cur1"), List.of("cure1"), List.of("cura1"),
                List.of("aar1"), List.of("upwr1"), List.of("all1", "all2"));

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
        void getOffenceIdSet_upwrViolationOffenceIds_returns_correct_list() {
            assertThat(ctx.getOffenceIdSet("upwrViolationOffenceIds")).containsExactly("upwr1");
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
                    "James Bond", 0L, 0L, 0L, 0L, 0L,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of("off-x"));

            assertThat(ctx.defendantName()).isEqualTo("James Bond");
            assertThat(ctx.allOffenceIds()).containsExactly("off-x");
        }
    }
}
