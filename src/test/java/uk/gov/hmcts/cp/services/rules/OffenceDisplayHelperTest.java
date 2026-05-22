package uk.gov.hmcts.cp.services.rules;

import java.util.List;
import java.util.Map;
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
                    .id("off1")
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
