package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConditionalBailContext}.
 */
class ConditionalBailContextTest {

    @Nested
    @DisplayName("toCelContext")
    class ToCelContext {

        @Test
        void all_three_counts_should_be_exposed_to_cel() {
            ConditionalBailContext ctx = new ConditionalBailContext(
                    "def-1", "Jane Doe", 2L, 2L, 0L, List.of("off-1", "off-2"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "conditionalBailOffenceCount", 2L,
                    "bailEndedCount", 2L,
                    "hasUrgentCount", 0L));
        }

        @Test
        void hasUrgentCount_one_should_be_exposed_when_urgent_present() {
            ConditionalBailContext ctx = new ConditionalBailContext(
                    "def-1", "Jane Doe", 1L, 1L, 1L, List.of("off-1"));

            assertThat(ctx.toCelContext()).containsEntry("hasUrgentCount", 1L);
        }
    }

    @Nested
    @DisplayName("getDefendantIdSet")
    class GetDefendantIdSet {

        @Test
        void defendantId_set_should_return_single_element_list() {
            ConditionalBailContext ctx = new ConditionalBailContext(
                    "def-1", "Jane Doe", 1L, 1L, 0L, List.of("off-1"));

            assertThat(ctx.getDefendantIdSet("defendantId")).containsExactly("def-1");
        }

        @Test
        void unknown_set_name_should_throw_illegal_argument_exception() {
            ConditionalBailContext ctx = new ConditionalBailContext(
                    "def-1", "Jane Doe", 1L, 1L, 0L, List.of("off-1"));

            assertThatThrownBy(() -> ctx.getDefendantIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet")
    class GetOffenceIdSet {

        @Test
        void allOffenceIds_set_should_return_configured_list() {
            ConditionalBailContext ctx = new ConditionalBailContext(
                    "def-1", "Jane Doe", 2L, 2L, 0L, List.of("off-1", "off-2"));

            assertThat(ctx.getOffenceIdSet("allOffenceIds"))
                    .containsExactly("off-1", "off-2");
        }

        @Test
        void unknown_set_name_should_throw_illegal_argument_exception() {
            ConditionalBailContext ctx = new ConditionalBailContext(
                    "def-1", "Jane Doe", 1L, 1L, 0L, List.of("off-1"));

            assertThatThrownBy(() -> ctx.getOffenceIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }

    @Nested
    @DisplayName("allOffenceIds")
    class AllOffenceIds {

        @Test
        void should_return_all_configured_offence_ids() {
            ConditionalBailContext ctx = new ConditionalBailContext(
                    "def-1", "Jane Doe", 2L, 2L, 0L, List.of("off-1", "off-2"));

            assertThat(ctx.allOffenceIds()).containsExactly("off-1", "off-2");
        }
    }

    @Nested
    @DisplayName("defendantName")
    class DefendantName {

        @Test
        void should_return_configured_defendant_name() {
            ConditionalBailContext ctx = new ConditionalBailContext(
                    "def-1", "Jane Doe", 1L, 1L, 0L, List.of("off-1"));

            assertThat(ctx.defendantName()).isEqualTo("Jane Doe");
        }
    }
}
