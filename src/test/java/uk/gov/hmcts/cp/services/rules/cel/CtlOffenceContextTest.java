package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CtlOffenceContext}.
 */
class CtlOffenceContextTest {

    @Nested
    @DisplayName("toCelContext")
    class ToCelContext {

        @Test
        void warning_active_should_expose_ctlWarningCount_as_one() {
            CtlOffenceContext ctx = new CtlOffenceContext(
                    "off1", 1L, List.of("off1"), List.of("off1"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsExactly(Map.entry("ctlWarningCount", 1L));
        }

        @Test
        void warning_inactive_should_expose_ctlWarningCount_as_zero() {
            CtlOffenceContext ctx = new CtlOffenceContext(
                    "off1", 0L, List.of(), List.of("off1"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsExactly(Map.entry("ctlWarningCount", 0L));
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet")
    class GetOffenceIdSet {

        @Test
        void warningOffenceIds_should_return_warning_list() {
            CtlOffenceContext ctx = new CtlOffenceContext(
                    "off1", 1L, List.of("off1"), List.of("off1"));

            assertThat(ctx.getOffenceIdSet("warningOffenceIds")).containsExactly("off1");
        }

        @Test
        void allOffenceIds_should_return_all_list() {
            CtlOffenceContext ctx = new CtlOffenceContext(
                    "off1", 0L, List.of(), List.of("off1"));

            assertThat(ctx.getOffenceIdSet("allOffenceIds")).containsExactly("off1");
        }

        @Test
        void unknown_set_name_should_throw_illegal_argument_exception() {
            CtlOffenceContext ctx = new CtlOffenceContext(
                    "off1", 0L, List.of(), List.of("off1"));

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
            CtlOffenceContext ctx = new CtlOffenceContext(
                    "off1", 1L, List.of("off1"), List.of("off1"));

            assertThat(ctx.defendantName()).isNull();
        }
    }

    @Nested
    @DisplayName("allOffenceIds")
    class AllOffenceIds {

        @Test
        void should_always_contain_the_offence_id() {
            CtlOffenceContext ctx = new CtlOffenceContext(
                    "off1", 0L, List.of(), List.of("off1"));

            assertThat(ctx.allOffenceIds()).containsExactly("off1");
        }
    }
}
