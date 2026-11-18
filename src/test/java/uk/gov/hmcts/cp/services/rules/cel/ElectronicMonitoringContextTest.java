package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ElectronicMonitoringContext}.
 */
class ElectronicMonitoringContextTest {

    @Nested
    @DisplayName("toCelContext")
    class ToCelContext {

        @Test
        void toCelContext_withNonZeroCount_should_exposeUnresolvedEmCount() {
            ElectronicMonitoringContext ctx = new ElectronicMonitoringContext(
                    "def-1", "John Smith", 2L, List.of("off-1", "off-2"));

            Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsExactlyInAnyOrderEntriesOf(Map.of("unresolvedEmCount", 2L));
        }

        @Test
        void toCelContext_withZeroCount_should_exposeZero() {
            ElectronicMonitoringContext ctx = new ElectronicMonitoringContext(
                    "def-1", "John Smith", 0L, List.of());

            assertThat(ctx.toCelContext()).containsEntry("unresolvedEmCount", 0L);
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet")
    class GetOffenceIdSet {

        @Test
        void getOffenceIdSet_withKnownSetName_should_returnConfiguredList() {
            ElectronicMonitoringContext ctx = new ElectronicMonitoringContext(
                    "def-1", "John Smith", 2L, List.of("off-1", "off-2"));

            assertThat(ctx.getOffenceIdSet("unresolvedOffenceIds"))
                    .containsExactly("off-1", "off-2");
        }

        @Test
        void getOffenceIdSet_withUnknownSetName_should_throwIllegalArgumentException() {
            ElectronicMonitoringContext ctx = new ElectronicMonitoringContext(
                    "def-1", "John Smith", 1L, List.of("off-1"));

            assertThatThrownBy(() -> ctx.getOffenceIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }

    @Nested
    @DisplayName("allOffenceIds")
    class AllOffenceIds {

        @Test
        void allOffenceIds_withConfiguredList_should_returnAllIds() {
            ElectronicMonitoringContext ctx = new ElectronicMonitoringContext(
                    "def-1", "John Smith", 2L, List.of("off-1", "off-2"));

            assertThat(ctx.allOffenceIds()).containsExactly("off-1", "off-2");
        }
    }

    @Nested
    @DisplayName("defendantName")
    class DefendantNameMethod {

        @Test
        void defendantName_withConfiguredName_should_returnConfiguredName() {
            ElectronicMonitoringContext ctx = new ElectronicMonitoringContext(
                    "def-1", "John Smith", 1L, List.of("off-1"));

            assertThat(ctx.defendantName()).isEqualTo("John Smith");
        }

        @Test
        void defendantName_withNullInput_should_returnNull() {
            ElectronicMonitoringContext ctx = new ElectronicMonitoringContext(
                    "def-1", null, 1L, List.of("off-1"));

            assertThat(ctx.defendantName()).isNull();
        }
    }

    @Nested
    @DisplayName("defendantId")
    class DefendantIdMethod {

        @Test
        void defendantId_withConfiguredId_should_returnConfiguredId() {
            ElectronicMonitoringContext ctx = new ElectronicMonitoringContext(
                    "def-1", "John Smith", 1L, List.of("off-1"));

            assertThat(ctx.defendantId()).isEqualTo("def-1");
        }
    }
}
