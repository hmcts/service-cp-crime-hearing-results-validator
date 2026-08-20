package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NoConvictionContext}.
 */
class NoConvictionContextTest {

    @Nested
    @DisplayName("toCelContext")
    class ToCelContext {

        @Test
        void warning_active_should_expose_all_four_counts() {
            NoConvictionContext ctx = new NoConvictionContext(
                    "off1", 1L, 1L, 0L, 0L, List.of("off1"), List.of("off1"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "unconvictedSentenceCount", 1L,
                    "finalCategoryCount", 1L,
                    "excludedFinalCount", 0L,
                    "convictedCount", 0L));
        }

        @Test
        void warning_inactive_should_expose_all_four_counts_as_zero() {
            NoConvictionContext ctx = new NoConvictionContext(
                    "off1", 0L, 0L, 0L, 0L, List.of(), List.of("off1"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "unconvictedSentenceCount", 0L,
                    "finalCategoryCount", 0L,
                    "excludedFinalCount", 0L,
                    "convictedCount", 0L));
        }

        @Test
        void convicted_offence_should_expose_convictedCount_as_one() {
            NoConvictionContext ctx = new NoConvictionContext(
                    "off1", 0L, 1L, 0L, 1L, List.of(), List.of("off1"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("convictedCount", 1L);
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet")
    class GetOffenceIdSet {

        @Test
        void warningOffenceIds_should_return_warning_list() {
            NoConvictionContext ctx = new NoConvictionContext(
                    "off1", 1L, 1L, 0L, 0L, List.of("off1"), List.of("off1"));

            assertThat(ctx.getOffenceIdSet("warningOffenceIds")).containsExactly("off1");
        }

        @Test
        void allOffenceIds_should_return_all_list() {
            NoConvictionContext ctx = new NoConvictionContext(
                    "off1", 0L, 0L, 0L, 0L, List.of(), List.of("off1"));

            assertThat(ctx.getOffenceIdSet("allOffenceIds")).containsExactly("off1");
        }

        @Test
        void unknown_set_name_should_throw_illegal_argument_exception() {
            NoConvictionContext ctx = new NoConvictionContext(
                    "off1", 0L, 0L, 0L, 0L, List.of(), List.of("off1"));

            assertThatThrownBy(() -> ctx.getOffenceIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }

    @Nested
    @DisplayName("defendantName")
    class DefendantName {

        @Test
        void should_return_null_because_context_is_per_offence_not_per_defendant() {
            NoConvictionContext ctx = new NoConvictionContext(
                    "off1", 1L, 1L, 0L, 0L, List.of("off1"), List.of("off1"));

            assertThat(ctx.defendantName()).isNull();
        }
    }

    @Nested
    @DisplayName("allOffenceIds")
    class AllOffenceIds {

        @Test
        void should_always_contain_the_offence_id() {
            NoConvictionContext ctx = new NoConvictionContext(
                    "off1", 0L, 0L, 0L, 0L, List.of(), List.of("off1"));

            assertThat(ctx.allOffenceIds()).containsExactly("off1");
        }
    }
}
