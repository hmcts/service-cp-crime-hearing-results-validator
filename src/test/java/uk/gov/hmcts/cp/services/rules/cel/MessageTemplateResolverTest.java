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
                "John Smith",
                List.of("off1", "off2"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence/counts [1 (32AH9105826), 2 (32AH9105826)] have issues");
    }

    @Test
    void resolve_should_replace_defendantName_placeholder() {
        Map<String, OffenceDto> offenceMap = Map.of("off1", offence("off1", 1));

        String result = resolver.resolve(
                "${defendantName} offence/counts ${offenceNumbers} have issues",
                "John Smith",
                List.of("off1"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("John Smith offence/counts [1 (32AH9105826)] have issues");
    }

    @Test
    void resolve_should_format_single_offence_number() {
        Map<String, OffenceDto> offenceMap = Map.of("off2", offence("off2", 2));

        String result = resolver.resolve(
                "Offence/counts ${offenceNumbers} show issues",
                "Jane Doe",
                List.of("off2"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence/counts [2 (32AH9105826)] show issues");
    }

    @Test
    void resolve_should_include_different_urns_for_cross_case_offences() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", offenceWithUrn("off1", 1, "32AH9105826"),
                "off2", offenceWithUrn("off2", 1, "32AH9105999"));

        String result = resolver.resolve(
                "Offence/counts ${offenceNumbers} have issues",
                "John Smith",
                List.of("off1", "off2"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence/counts [1 (32AH9105826), 1 (32AH9105999)] have issues");
    }

    @Test
    void resolve_should_use_position_fallback_when_orderIndex_missing() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", OffenceDto.builder().id("off1").offenceCode("TH68001")
                        .offenceTitle("Theft").build());

        String result = resolver.resolve(
                "Offence/counts ${offenceNumbers} missing",
                "John Smith",
                List.of("off1"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence/counts [1] missing");
    }

    @Test
    void resolve_with_no_placeholders_should_return_unchanged() {
        String template = "All offences have info and there is no primary sentence";

        String result = resolver.resolve(template, "John Smith", List.of("off1"), Map.of(), ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo(template);
    }

    @Test
    void resolve_should_use_id_when_offence_not_in_map_and_not_in_list() {
        String result = resolver.resolve(
                "Offence/counts ${offenceNumbers} unknown",
                "John Smith",
                List.of("unknown-id"),
                Map.of(),
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence/counts [unknown-id] unknown");
    }

    @Test
    void resolve_should_handle_null_defendantName() {
        Map<String, OffenceDto> offenceMap = Map.of("off1", offence("off1", 1));

        String result = resolver.resolve(
                "${defendantName} offence/counts ${offenceNumbers} have issues",
                null,
                List.of("off1"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("${defendantName} offence/counts [1 (32AH9105826)] have issues");
    }

    private static OffenceDto offence(String id, int orderIndex) {
        return OffenceDto.builder()
                .id(id)
                .offenceCode("TH68001")
                .offenceTitle("Test offence")
                .orderIndex(orderIndex)
                .caseUrn("32AH9105826")
                .build();
    }

    private static OffenceDto offenceWithUrn(String id, int orderIndex, String caseUrn) {
        return OffenceDto.builder()
                .id(id)
                .offenceCode("TH68001")
                .offenceTitle("Test offence")
                .orderIndex(orderIndex)
                .caseUrn(caseUrn)
                .build();
    }
}
