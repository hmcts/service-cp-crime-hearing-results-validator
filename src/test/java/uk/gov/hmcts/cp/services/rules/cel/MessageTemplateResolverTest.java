package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MessageTemplateResolver}.
 */
class MessageTemplateResolverTest {

    private static final List<String> ALL_OFFENCE_IDS = List.of("off1", "off2", "off3");
    private final MessageTemplateResolver resolver = new MessageTemplateResolver(new OffenceDisplayHelper());

    /**
     * Verifies that two affected offences are rendered as a human-readable list joined with
     * {@code and} and expanded into the message template.
     */
    @Test
    void resolve_should_replace_offenceNumbers_with_and_separated_list() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", offence("off1", 1),
                "off2", offence("off2", 2));

        String result = resolver.resolve(
                "${offenceNumbers} have issues",
                "John Smith",
                List.of("off1", "off2"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence 1 (URN:32AH9105826) and Offence 2 (URN:32AH9105826) have issues");
    }

    /**
     * Verifies that the defendant name placeholder is replaced when a non-null name is provided.
     */
    @Test
    void resolve_should_replace_defendantName_placeholder() {
        Map<String, OffenceDto> offenceMap = Map.of("off1", offence("off1", 1));

        String result = resolver.resolve(
                "${defendantName} ${offenceNumbers} have issues",
                "John Smith",
                List.of("off1"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("John Smith Offence 1 (URN:32AH9105826) have issues");
    }

    /**
     * Verifies formatting for a single affected offence so the template receives one offence label
     * without list punctuation.
     */
    @Test
    void resolve_should_format_single_offence_number() {
        Map<String, OffenceDto> offenceMap = Map.of("off2", offence("off2", 2));

        String result = resolver.resolve(
                "${offenceNumbers} show issues",
                "Jane Doe",
                List.of("off2"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence 2 (URN:32AH9105826) show issues");
    }

    /**
     * Verifies that offences on different cases retain distinct URNs in the rendered message.
     */
    @Test
    void resolve_should_include_different_urns_for_cross_case_offences() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", offenceWithUrn("off1", 1, "32AH9105826"),
                "off2", offenceWithUrn("off2", 1, "32AH9105999"));

        String result = resolver.resolve(
                "${offenceNumbers} have issues",
                "John Smith",
                List.of("off1", "off2"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence 1 (URN:32AH9105826) and Offence 1 (URN:32AH9105999) have issues");
    }

    /**
     * Verifies that the helper falls back to the offence position in the full offence list when an
     * offence has no explicit order index.
     */
    @Test
    void resolve_should_use_position_fallback_when_orderIndex_missing() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", OffenceDto.builder().id("off1").offenceCode("TH68001")
                        .offenceTitle("Theft").build());

        String result = resolver.resolve(
                "${offenceNumbers} missing",
                "John Smith",
                List.of("off1"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence 1 missing");
    }

    /**
     * Verifies that templates without supported placeholders are returned unchanged.
     */
    @Test
    void resolve_with_no_placeholders_should_return_unchanged() {
        String template = "All offences have info and there is no primary sentence";

        String result = resolver.resolve(template, "John Smith", List.of("off1"), Map.of(), ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo(template);
    }

    /**
     * Verifies that an unknown offence id falls back to the raw id when no map entry or list
     * position is available.
     */
    @Test
    void resolve_should_use_id_when_offence_not_in_map_and_not_in_list() {
        String result = resolver.resolve(
                "${offenceNumbers} unknown",
                "John Smith",
                List.of("unknown-id"),
                Map.of(),
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("Offence unknown-id unknown");
    }

    /**
     * Verifies that a null defendant name leaves the placeholder untouched while still expanding
     * offence references.
     */
    @Test
    void resolve_should_handle_null_defendantName() {
        Map<String, OffenceDto> offenceMap = Map.of("off1", offence("off1", 1));

        String result = resolver.resolve(
                "${defendantName} ${offenceNumbers} have issues",
                null,
                List.of("off1"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo("${defendantName} Offence 1 (URN:32AH9105826) have issues");
    }

    /**
     * Verifies the comma-plus-and formatting used when three offences are inserted into one
     * validation message.
     */
    @Test
    void resolve_should_use_and_for_three_offences() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off1", offence("off1", 1),
                "off2", offence("off2", 2),
                "off3", offence("off3", 3));

        String result = resolver.resolve(
                "${offenceNumbers} have issues",
                "John Smith",
                List.of("off1", "off2", "off3"),
                offenceMap,
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo(
                "Offence 1 (URN:32AH9105826), Offence 2 (URN:32AH9105826) and Offence 3 (URN:32AH9105826) have issues");
    }

    /**
     * Verifies that offences with double-digit order indexes are sorted numerically (2 before 10),
     * not lexicographically (10 before 2).
     */
    @Test
    void resolve_should_sort_offences_numerically_not_lexicographically() {
        Map<String, OffenceDto> offenceMap = Map.of(
                "off10", offence("off10", 10),
                "off2", offence("off2", 2));

        String result = resolver.resolve(
                "${offenceNumbers} have issues",
                "John Smith",
                List.of("off10", "off2"),
                offenceMap,
                List.of("off2", "off10"));

        assertThat(result).isEqualTo(
                "Offence 2 (URN:32AH9105826) and Offence 10 (URN:32AH9105826) have issues");
    }

    /**
     * Verifies that an empty offence list produces an empty string rather than throwing.
     */
    @Test
    void resolve_should_handle_empty_offence_list() {
        String result = resolver.resolve(
                "${offenceNumbers} have issues",
                "John Smith",
                List.of(),
                Map.of(),
                ALL_OFFENCE_IDS);

        assertThat(result).isEqualTo(" have issues");
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
