package uk.gov.hmcts.cp.services.rules;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.AffectedDefendant;
import uk.gov.hmcts.cp.openapi.model.AffectedOffence;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OffenceDisplayHelper}.
 */
class OffenceDisplayHelperTest {

    private final OffenceDisplayHelper helper = new OffenceDisplayHelper();

    @Nested
    @DisplayName("resolveDisplayNumber")
    class ResolveDisplayNumber {

        @Test
        void resolveDisplayNumber_withOrderIndexAndUrn_should_returnFormattedOffenceWithUrn() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off1").orderIndex(3).caseUrn("32AH9105826").build();
            Map<String, OffenceDto> map = Map.of("off1", offence);

            String result = helper.resolveDisplayNumber("off1", map, List.of("off1"));

            assertThat(result).isEqualTo("Offence 3 (URN:32AH9105826)");
        }

        @Test
        void resolveDisplayNumber_withOrderIndexAndNoUrn_should_returnOffenceNumberOnly() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off1").orderIndex(2).build();
            Map<String, OffenceDto> map = Map.of("off1", offence);

            String result = helper.resolveDisplayNumber("off1", map, List.of("off1"));

            assertThat(result).isEqualTo("Offence 2");
        }

        @Test
        void resolveDisplayNumber_withBlankUrn_should_returnOffenceNumberOnly() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off1").orderIndex(1).caseUrn("   ").build();
            Map<String, OffenceDto> map = Map.of("off1", offence);

            String result = helper.resolveDisplayNumber("off1", map, List.of("off1"));

            assertThat(result).isEqualTo("Offence 1");
        }

        @Test
        void resolveDisplayNumber_notInOffenceMap_should_usePositionalIndexFromAllOffenceIds() {
            List<String> all = List.of("off1", "off2", "off3");

            String result = helper.resolveDisplayNumber("off2", Map.of(), all);

            assertThat(result).isEqualTo("Offence 2");
        }

        @Test
        void resolveDisplayNumber_notInMapOrAllOffenceIds_should_returnOffencePrefixedId() {
            String result = helper.resolveDisplayNumber("unknown-id", Map.of(), List.of("off1"));

            assertThat(result).isEqualTo("Offence unknown-id");
        }

        @Test
        void resolveDisplayNumber_withNullOrderIndex_should_fallBackToPositionalIndex() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off2").orderIndex(null).build();
            Map<String, OffenceDto> map = Map.of("off2", offence);
            List<String> all = List.of("off1", "off2", "off3");

            String result = helper.resolveDisplayNumber("off2", map, all);

            assertThat(result).isEqualTo("Offence 2");
        }
    }

    @Nested
    @DisplayName("resolveOrderIndex")
    class ResolveOrderIndex {

        @Test
        void resolveOrderIndex_withOrderIndex_should_returnIt() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off1").orderIndex(5).build();
            Map<String, OffenceDto> map = Map.of("off1", offence);

            int result = helper.resolveOrderIndex("off1", map, List.of("off1"));

            assertThat(result).isEqualTo(5);
        }

        @Test
        void resolveOrderIndex_notInOffenceMap_should_usePositionalIndex() {
            List<String> all = List.of("off1", "off2", "off3");

            int result = helper.resolveOrderIndex("off3", Map.of(), all);

            assertThat(result).isEqualTo(3);
        }

        @Test
        void resolveOrderIndex_notInMapOrAllOffenceIds_should_returnMaxValue() {
            int result = helper.resolveOrderIndex("ghost", Map.of(), List.of("off1"));

            assertThat(result).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        void resolveOrderIndex_withNullOrderIndex_should_fallBackToPositionalIndex() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off2").orderIndex(null).build();
            Map<String, OffenceDto> map = Map.of("off2", offence);
            List<String> all = List.of("off1", "off2");

            int result = helper.resolveOrderIndex("off2", map, all);

            assertThat(result).isEqualTo(2);
        }
    }

    @Nested
    class BuildAffectedDefendants {

        @Test
        void should_return_empty_list_when_no_ids() {
            List<AffectedDefendant> result = helper.buildAffectedDefendants(List.of(), "some message");

            assertThat(result).isEmpty();
        }

        @Test
        void should_map_single_id_to_affected_defendant() {
            List<AffectedDefendant> result = helper.buildAffectedDefendants(List.of("d1"), "issue message");

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getDefendantId()).isEqualTo("d1");
            assertThat(result.getFirst().getMessage()).isEqualTo("issue message");
        }

        @Test
        void should_map_multiple_ids_preserving_order() {
            List<AffectedDefendant> result =
                    helper.buildAffectedDefendants(List.of("d1", "master-2", "d3"), "issue message");

            assertThat(result).extracting(AffectedDefendant::getDefendantId)
                    .containsExactly("d1", "master-2", "d3");
        }
    }

    @Nested
    class BuildAffectedOffences {

        @Test
        void should_include_title_and_message_from_offence_map() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off1")
                    .offenceTitle("Theft")
                    .build();

            List<AffectedOffence> result =
                    helper.buildAffectedOffences(List.of("off1"), Map.of("off1", offence), id -> "No CTL found");

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getOffenceId()).isEqualTo("off1");
            assertThat(result.getFirst().getOffenceTitle()).isEqualTo("Theft");
            assertThat(result.getFirst().getMessage()).isEqualTo("No CTL found");
        }

        @Test
        void should_set_null_title_when_offence_not_in_map() {
            List<AffectedOffence> result =
                    helper.buildAffectedOffences(List.of("off-unknown"), Map.of(), id -> "No CTL found");

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getOffenceId()).isEqualTo("off-unknown");
            assertThat(result.getFirst().getOffenceTitle()).isNull();
            assertThat(result.getFirst().getMessage()).isEqualTo("No CTL found");
        }
    }
}
