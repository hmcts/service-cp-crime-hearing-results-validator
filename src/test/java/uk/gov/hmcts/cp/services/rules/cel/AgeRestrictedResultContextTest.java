package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AgeRestrictedResultContext}.
 */
class AgeRestrictedResultContextTest {

    @Nested
    @DisplayName("toCelContext")
    class ToCelContext {

        @Test
        void under21_should_expose_isUnder21_as_one() {
            AgeRestrictedResultContext ctx = new AgeRestrictedResultContext(
                    "def1", "Jamie Smith", true, List.of("off1"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsExactly(Map.entry("isUnder21", 1L));
        }

        @Test
        void twentyOneOrOver_should_expose_isUnder21_as_zero() {
            AgeRestrictedResultContext ctx = new AgeRestrictedResultContext(
                    "def1", "Jamie Smith", false, List.of("off1"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsExactly(Map.entry("isUnder21", 0L));
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet")
    class GetOffenceIdSet {

        @Test
        void qualifyingOffenceIds_should_return_configured_list() {
            AgeRestrictedResultContext ctx = new AgeRestrictedResultContext(
                    "def1", "Jamie Smith", true, List.of("off1", "off2"));

            assertThat(ctx.getOffenceIdSet("qualifyingOffenceIds")).containsExactly("off1", "off2");
        }

        @Test
        void unknown_set_name_should_throw_illegal_argument_exception() {
            AgeRestrictedResultContext ctx = new AgeRestrictedResultContext(
                    "def1", "Jamie Smith", true, List.of("off1"));

            assertThatThrownBy(() -> ctx.getOffenceIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }

    @Nested
    @DisplayName("getDefendantIdSet")
    class GetDefendantIdSet {

        @Test
        void defendantId_should_return_single_element_list() {
            AgeRestrictedResultContext ctx = new AgeRestrictedResultContext(
                    "def1", "Jamie Smith", true, List.of("off1"));

            assertThat(ctx.getDefendantIdSet("defendantId")).containsExactly("def1");
        }

        @Test
        void unknown_set_name_should_throw_illegal_argument_exception() {
            AgeRestrictedResultContext ctx = new AgeRestrictedResultContext(
                    "def1", "Jamie Smith", true, List.of("off1"));

            assertThatThrownBy(() -> ctx.getDefendantIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }

    @Nested
    @DisplayName("allOffenceIds")
    class AllOffenceIds {

        @Test
        void should_return_the_qualifying_offence_ids() {
            AgeRestrictedResultContext ctx = new AgeRestrictedResultContext(
                    "def1", "Jamie Smith", true, List.of("off1", "off2"));

            assertThat(ctx.allOffenceIds()).containsExactly("off1", "off2");
        }
    }

    @Nested
    @DisplayName("defendantName / defendantId")
    class Accessors {

        @Test
        void defendantName_should_return_the_configured_name() {
            AgeRestrictedResultContext ctx = new AgeRestrictedResultContext(
                    "def1", "Jamie Smith", true, List.of("off1"));

            assertThat(ctx.defendantName()).isEqualTo("Jamie Smith");
        }

        @Test
        void defendantId_should_return_the_configured_id() {
            AgeRestrictedResultContext ctx = new AgeRestrictedResultContext(
                    "def1", "Jamie Smith", true, List.of("off1"));

            assertThat(ctx.defendantId()).isEqualTo("def1");
        }
    }
}
