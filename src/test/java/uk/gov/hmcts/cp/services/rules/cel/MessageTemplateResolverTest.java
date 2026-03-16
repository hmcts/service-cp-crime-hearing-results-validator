package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTemplateResolverTest {

    private static final List<String> ALL_OFFENCE_IDS = List.of("off1", "off2", "off3");
    private final MessageTemplateResolver resolver = new MessageTemplateResolver(new OffenceDisplayHelper());

    @Test
    void resolve_should_replace_offenceNumbers_with_formatted_list() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", offence("off1", 1),
                "off2", offence("off2", 2));

        String result = resolver.resolve(
                "Offence/counts ${offenceNumbers} have issues",
                List.of("off1", "off2"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence/counts [1, 2] have issues");
    }

    @Test
    void resolve_should_format_single_offence_number() {
        Map<String, OffenceDto> offenceMap = Map.of("off2", offence("off2", 2));

        String result = resolver.resolve(
                "Offence/counts ${offenceNumbers} show issues",
                List.of("off2"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence/counts [2] show issues");
    }

    @Test
    void resolve_should_use_position_fallback_when_orderIndex_missing() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", OffenceDto.builder().id("off1").offenceCode("TH68001")
                        .offenceTitle("Theft").build());

        String result = resolver.resolve(
                "Offence/counts ${offenceNumbers} missing",
                List.of("off1"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence/counts [1] missing");
    }

    @Test
    void resolve_with_no_placeholders_should_return_unchanged() {
        String template = "All offences have info and there is no primary sentence";

        String result = resolver.resolve(template, List.of("off1"), Map.of(), ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo(template);
    }

    @Test
    void resolve_should_use_id_when_offence_not_in_map_and_not_in_list() {
        String result = resolver.resolve(
                "Offence/counts ${offenceNumbers} unknown",
                List.of("unknown-id"),
                Map.of(),
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence/counts [unknown-id] unknown");
    }

    private static OffenceDto offence(String id, int orderIndex) {
        return OffenceDto.builder()
                .id(id)
                .offenceCode("TH68001")
                .offenceTitle("Test offence")
                .orderIndex(orderIndex)
                .build();
    }
}
