package uk.gov.hmcts.cp.services.rules;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.AffectedOffence;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OffenceDisplayHelper}, covering display-number resolution, order-index
 * fallback, and affected-offence payload construction.
 */
class OffenceDisplayHelperTest {

    private static final List<String> ALL_OFFENCE_IDS = List.of("off1", "off2", "off3");

    private final OffenceDisplayHelper helper = new OffenceDisplayHelper();

    /**
     * Verifies the display number uses the offence order index and omits the URN suffix when the
     * offence has no case URN.
     */
    @Test
    void resolveDisplayNumber_with_orderIndex_and_no_urn_should_use_order_index() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", OffenceDto.builder().offenceId("off1").orderIndex(3).build());

        String result = helper.resolveDisplayNumber("off1", offenceMap, ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence 3");
    }

    /**
     * Verifies the display number includes the URN suffix when the offence carries a non-blank
     * case URN.
     */
    @Test
    void resolveDisplayNumber_with_orderIndex_and_urn_should_append_urn() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", OffenceDto.builder().offenceId("off1").orderIndex(3).caseUrn("32AH9105826").build());

        String result = helper.resolveDisplayNumber("off1", offenceMap, ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence 3 (URN:32AH9105826)");
    }

    /**
     * Verifies a blank case URN is treated as no URN and produces no suffix.
     */
    @Test
    void resolveDisplayNumber_with_blank_urn_should_omit_urn() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", OffenceDto.builder().offenceId("off1").orderIndex(3).caseUrn("   ").build());

        String result = helper.resolveDisplayNumber("off1", offenceMap, ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence 3");
    }

    /**
     * Verifies that, when the offence has no order index, the display number falls back to the
     * 1-based position of the id within the full offence-id list.
     */
    @Test
    void resolveDisplayNumber_without_orderIndex_should_fall_back_to_list_position() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off2", OffenceDto.builder().offenceId("off2").build());

        String result = helper.resolveDisplayNumber("off2", offenceMap, ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence 2");
    }

    /**
     * Verifies that an id absent from the offence map still resolves via its list position, and
     * that the missing offence produces no URN suffix.
     */
    @Test
    void resolveDisplayNumber_with_id_absent_from_map_should_use_list_position() {
        String result = helper.resolveDisplayNumber("off1", Map.of(), ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence 1");
    }

    /**
     * Verifies that an id absent from both the offence map and the full id list falls back to
     * displaying the raw id as the count.
     */
    @Test
    void resolveDisplayNumber_with_unknown_id_should_use_raw_id() {
        String result = helper.resolveDisplayNumber("offX", Map.of(), ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence offX");
    }

    /**
     * Verifies the order index is read directly from the offence when present.
     */
    @Test
    void resolveOrderIndex_with_orderIndex_should_return_it() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", OffenceDto.builder().offenceId("off1").orderIndex(7).build());

        assertThat(helper.resolveOrderIndex("off1", offenceMap, ALL_OFFENCE_IDS)).isEqualTo(7);
    }

    /**
     * Verifies the order index falls back to the 1-based list position when the offence has none.
     */
    @Test
    void resolveOrderIndex_without_orderIndex_should_fall_back_to_list_position() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off3", OffenceDto.builder().offenceId("off3").build());

        assertThat(helper.resolveOrderIndex("off3", offenceMap, ALL_OFFENCE_IDS)).isEqualTo(3);
    }

    /**
     * Verifies an id absent from both the map and the id list sorts last via {@link Integer#MAX_VALUE}.
     */
    @Test
    void resolveOrderIndex_with_unknown_id_should_return_max_value() {
        assertThat(helper.resolveOrderIndex("offX", Map.of(), ALL_OFFENCE_IDS))
                .isEqualTo(Integer.MAX_VALUE);
    }

    /**
     * Verifies affected-offence entries carry the offence title when the offence is known.
     */
    @Test
    void buildAffectedOffences_with_known_offence_should_populate_title() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", OffenceDto.builder().offenceId("off1").offenceTitle("Theft").build());

        List<AffectedOffence> result = helper.buildAffectedOffences(List.of("off1"), offenceMap);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getOffenceId()).isEqualTo("off1");
        assertThat(result.getFirst().getOffenceTitle()).isEqualTo("Theft");
    }

    /**
     * Verifies affected-offence entries leave the title null when the offence is unknown, and that
     * the input order is preserved.
     */
    @Test
    void buildAffectedOffences_with_unknown_offence_should_leave_title_null_and_keep_order() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", OffenceDto.builder().offenceId("off1").offenceTitle("Theft").build());

        List<AffectedOffence> result = helper.buildAffectedOffences(List.of("off1", "offX"), offenceMap);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOffenceId()).isEqualTo("off1");
        assertThat(result.get(0).getOffenceTitle()).isEqualTo("Theft");
        assertThat(result.get(1).getOffenceId()).isEqualTo("offX");
        assertThat(result.get(1).getOffenceTitle()).isNull();
    }
}
