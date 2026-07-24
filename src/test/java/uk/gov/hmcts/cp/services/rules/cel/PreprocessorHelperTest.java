package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Unit tests for {@link PreprocessorHelper}.
 */
class PreprocessorHelperTest {

    // ── upperSet ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upperSet")
    class UpperSet {

        @Test
        void upperSet_null_input_returns_empty_set() {
            assertThat(PreprocessorHelper.upperSet(null)).isEmpty();
        }

        @Test
        void upperSet_empty_list_returns_empty_set() {
            assertThat(PreprocessorHelper.upperSet(List.of())).isEmpty();
        }

        @Test
        void upperSet_mixed_case_values_are_upper_cased() {
            Set<String> result = PreprocessorHelper.upperSet(List.of("yroew", "YRC2", "Yroni"));

            assertThat(result).containsExactlyInAnyOrder("YROEW", "YRC2", "YRONI");
        }

        @Test
        void upperSet_already_upper_values_are_preserved() {
            Set<String> result = PreprocessorHelper.upperSet(List.of("IMP", "COEW"));

            assertThat(result).containsExactlyInAnyOrder("IMP", "COEW");
        }

        @Test
        void upperSet_result_is_unmodifiable() {
            Set<String> result = PreprocessorHelper.upperSet(List.of("YRC1"));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> result.add("EXTRA"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ── upperOrNull ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upperOrNull")
    class UpperOrNull {

        @Test
        void upperOrNull_null_input_returns_null() {
            assertThat(PreprocessorHelper.upperOrNull(null)).isNull();
        }

        @Test
        void upperOrNull_lower_case_input_returns_upper() {
            assertThat(PreprocessorHelper.upperOrNull("yroew")).isEqualTo("YROEW");
        }

        @Test
        void upperOrNull_already_upper_input_is_unchanged() {
            assertThat(PreprocessorHelper.upperOrNull("COEW")).isEqualTo("COEW");
        }

        @Test
        void upperOrNull_mixed_case_input_returns_upper() {
            assertThat(PreprocessorHelper.upperOrNull("yRc2")).isEqualTo("YRC2");
        }
    }

    // ── hasUpperCode ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("hasUpperCode")
    class HasUpperCode {

        @Test
        void hasUpperCode_returns_true_for_matching_code() {
            ResultLineDto line = resultLine("YROEW");
            Set<String> codes = Set.of("YROEW", "YRONI");

            assertThat(PreprocessorHelper.hasUpperCode(line, codes)).isTrue();
        }

        @Test
        void hasUpperCode_match_is_case_insensitive() {
            ResultLineDto line = resultLine("yroew");
            Set<String> codes = Set.of("YROEW");

            assertThat(PreprocessorHelper.hasUpperCode(line, codes)).isTrue();
        }

        @Test
        void hasUpperCode_returns_false_when_code_not_in_set() {
            ResultLineDto line = resultLine("IMP");
            Set<String> codes = Set.of("YROEW", "YRONI");

            assertThat(PreprocessorHelper.hasUpperCode(line, codes)).isFalse();
        }

        @Test
        void hasUpperCode_null_short_code_returns_false() {
            ResultLineDto line = resultLine(null);
            Set<String> codes = Set.of("YROEW");

            assertThat(PreprocessorHelper.hasUpperCode(line, codes)).isFalse();
        }
    }

    // ── anyShortCodeIn ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("anyShortCodeIn")
    class AnyShortCodeIn {

        @Test
        void anyShortCodeIn_returns_true_when_one_line_matches() {
            List<ResultLineDto> lines = List.of(resultLine("IMP"), resultLine("YROEW"));
            Set<String> codes = Set.of("YROEW");

            assertThat(PreprocessorHelper.anyShortCodeIn(lines, codes)).isTrue();
        }

        @Test
        void anyShortCodeIn_returns_false_when_no_line_matches() {
            List<ResultLineDto> lines = List.of(resultLine("IMP"), resultLine("COEW"));
            Set<String> codes = Set.of("YROEW", "YRONI");

            assertThat(PreprocessorHelper.anyShortCodeIn(lines, codes)).isFalse();
        }

        @Test
        void anyShortCodeIn_empty_list_returns_false() {
            assertThat(PreprocessorHelper.anyShortCodeIn(List.of(), Set.of("YROEW"))).isFalse();
        }

        @Test
        void anyShortCodeIn_match_is_case_insensitive() {
            List<ResultLineDto> lines = List.of(resultLine("yroew"));
            Set<String> codes = Set.of("YROEW");

            assertThat(PreprocessorHelper.anyShortCodeIn(lines, codes)).isTrue();
        }
    }

    // ── groupByDefendant ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("groupByDefendant")
    class GroupByDefendant {

        @Test
        void groupByDefendant_null_result_lines_returns_empty_map() {
            DraftValidationRequest request = new DraftValidationRequest();
            request.setResultLines(null);

            assertThat(PreprocessorHelper.groupByDefendant(request)).isEmpty();
        }

        @Test
        void groupByDefendant_groups_lines_by_defendant_id() {
            ResultLineDto rl1 = resultLineWithDefendant("rl1", "d1");
            ResultLineDto rl2 = resultLineWithDefendant("rl2", "d2");
            ResultLineDto rl3 = resultLineWithDefendant("rl3", "d1");

            DraftValidationRequest request = requestWithLines(List.of(rl1, rl2, rl3));

            Map<String, List<ResultLineDto>> grouped = PreprocessorHelper.groupByDefendant(request);

            assertThat(grouped).containsOnlyKeys("d1", "d2");
            assertThat(grouped.get("d1")).containsExactly(rl1, rl3);
            assertThat(grouped.get("d2")).containsExactly(rl2);
        }

        @Test
        void groupByDefendant_skips_lines_with_null_defendant_id() {
            ResultLineDto withId = resultLineWithDefendant("rl1", "d1");
            ResultLineDto withoutId = resultLineWithDefendant("rl2", null);

            DraftValidationRequest request = requestWithLines(List.of(withId, withoutId));

            Map<String, List<ResultLineDto>> grouped = PreprocessorHelper.groupByDefendant(request);

            assertThat(grouped).containsOnlyKeys("d1");
        }

        @Test
        void groupByDefendant_preserves_insertion_order() {
            ResultLineDto rl1 = resultLineWithDefendant("rl1", "d3");
            ResultLineDto rl2 = resultLineWithDefendant("rl2", "d1");
            ResultLineDto rl3 = resultLineWithDefendant("rl3", "d2");

            DraftValidationRequest request = requestWithLines(List.of(rl1, rl2, rl3));

            Map<String, List<ResultLineDto>> grouped = PreprocessorHelper.groupByDefendant(request);

            assertThat(grouped.keySet()).containsExactly("d3", "d1", "d2");
        }
    }

    // ── buildDefendantDedupeKeys ─────────────────────────────────────────────

    @Nested
    @DisplayName("buildDefendantDedupeKeys")
    class BuildDefendantDedupeKeys {

        @Test
        void buildDefendantDedupeKeys_null_defendants_returns_empty_map() {
            DraftValidationRequest request = new DraftValidationRequest();
            request.setDefendants(null);

            assertThat(PreprocessorHelper.buildDefendantDedupeKeys(request)).isEmpty();
        }

        @Test
        void buildDefendantDedupeKeys_maps_defendantId_to_itself_when_no_masterDefendantId() {
            DraftValidationRequest request = new DraftValidationRequest();
            request.setDefendants(List.of(defendant("d1", "Alice", "Smith")));

            Map<String, String> keys = PreprocessorHelper.buildDefendantDedupeKeys(request);

            assertThat(keys).containsEntry("d1", "d1");
        }

        @Test
        void buildDefendantDedupeKeys_different_defendantIds_sharing_masterDefendantId_map_to_same_key() {
            DraftValidationRequest request = new DraftValidationRequest();
            request.setDefendants(List.of(
                    defendant("d1", "Test", "Ark").masterDefendantId("master1"),
                    defendant("d2", "Test", "Ark").masterDefendantId("master1")));

            Map<String, String> keys = PreprocessorHelper.buildDefendantDedupeKeys(request);

            assertThat(keys).containsEntry("d1", "master1");
            assertThat(keys).containsEntry("d2", "master1");
        }

        @Test
        void buildDefendantDedupeKeys_falls_back_to_defendantId_when_masterDefendantId_blank() {
            DraftValidationRequest request = new DraftValidationRequest();
            request.setDefendants(List.of(defendant("d1", "Alice", "Smith").masterDefendantId("  ")));

            Map<String, String> keys = PreprocessorHelper.buildDefendantDedupeKeys(request);

            assertThat(keys).containsEntry("d1", "d1");
        }
    }

    // ── buildDefendantNames ──────────────────────────────────────────────────

    @Nested
    @DisplayName("buildDefendantNames")
    class BuildDefendantNames {

        @Test
        void buildDefendantNames_null_defendants_returns_empty_map() {
            DraftValidationRequest request = new DraftValidationRequest();
            request.setDefendants(null);

            assertThat(PreprocessorHelper.buildDefendantNames(request)).isEmpty();
        }

        @Test
        void buildDefendantNames_builds_full_name_keyed_by_id() {
            DraftValidationRequest request = new DraftValidationRequest();
            request.setDefendants(List.of(
                    defendant("d1", "Alice", "Smith"),
                    defendant("d2", "Bob", "Jones")));

            Map<String, String> names = PreprocessorHelper.buildDefendantNames(request);

            assertThat(names).containsEntry("d1", "Alice Smith");
            assertThat(names).containsEntry("d2", "Bob Jones");
        }

        @Test
        void buildDefendantNames_preserves_insertion_order() {
            DraftValidationRequest request = new DraftValidationRequest();
            request.setDefendants(List.of(
                    defendant("d3", "Charlie", "X"),
                    defendant("d1", "Alice", "Y"),
                    defendant("d2", "Bob", "Z")));

            assertThat(PreprocessorHelper.buildDefendantNames(request).keySet())
                    .containsExactly("d3", "d1", "d2");
        }
    }

    // ── buildFullName ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildFullName")
    class BuildFullName {

        @Test
        void buildFullName_both_names_present_returns_first_space_last() {
            assertThat(PreprocessorHelper.buildFullName(defendant("d1", "Alice", "Smith")))
                    .isEqualTo("Alice Smith");
        }

        @Test
        void buildFullName_only_first_name_returns_first_name() {
            assertThat(PreprocessorHelper.buildFullName(defendant("d1", "Alice", null)))
                    .isEqualTo("Alice");
        }

        @Test
        void buildFullName_only_last_name_returns_last_name() {
            assertThat(PreprocessorHelper.buildFullName(defendant("d1", null, "Smith")))
                    .isEqualTo("Smith");
        }

        @Test
        @DisplayName("both names null returns null — known limitation: getOrDefault fallback is bypassed in the preprocessor")
        void buildFullName_both_null_returns_null() {
            // NOTE: callers that store this result in a Map and then call Map.getOrDefault()
            // will receive null rather than the default value because the key IS present.
            // Fix: return a non-null sentinel (e.g. "") when both components are null.
            assertThat(PreprocessorHelper.buildFullName(defendant("d1", null, null))).isNull();
        }
    }

    // ── parsePromptDate ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("parsePromptDate")
    class ParsePromptDate {

        @Test
        void parsePromptDate_null_prompts_returns_null() {
            ResultLineDto line = resultLine("YROEW");
            line.setPrompts(null);

            assertThat(PreprocessorHelper.parsePromptDate(line, "endDate", "off1")).isNull();
        }

        @Test
        void parsePromptDate_prompt_ref_not_found_returns_null() {
            ResultLineDto line = lineWithPrompt("YROEW", "otherRef", "2026-10-30");

            assertThat(PreprocessorHelper.parsePromptDate(line, "endDate", "off1")).isNull();
        }

        @Test
        void parsePromptDate_valid_iso_date_is_parsed() {
            ResultLineDto line = lineWithPrompt("YROEW", "endDate", "2026-10-30");

            assertThat(PreprocessorHelper.parsePromptDate(line, "endDate", "off1"))
                    .isEqualTo(LocalDate.of(2026, 10, 30));
        }

        @Test
        void parsePromptDate_blank_prompt_value_returns_null() {
            ResultLineDto line = lineWithPrompt("YROEW", "endDate", "   ");

            assertThat(PreprocessorHelper.parsePromptDate(line, "endDate", "off1")).isNull();
        }

        @Test
        void parsePromptDate_null_prompt_value_returns_null() {
            ResultLineDto line = lineWithPrompt("YROEW", "endDate", null);

            assertThat(PreprocessorHelper.parsePromptDate(line, "endDate", "off1")).isNull();
        }

        @Test
        void parsePromptDate_non_iso_value_returns_null() {
            ResultLineDto line = lineWithPrompt("YROEW", "endDate", "30/10/2026");

            assertThat(PreprocessorHelper.parsePromptDate(line, "endDate", "off1")).isNull();
        }

        @Test
        void parsePromptDate_value_with_surrounding_whitespace_is_trimmed_and_parsed() {
            ResultLineDto line = lineWithPrompt("YROEW", "endDate", "  2026-10-30  ");

            assertThat(PreprocessorHelper.parsePromptDate(line, "endDate", "off1"))
                    .isEqualTo(LocalDate.of(2026, 10, 30));
        }

        @Test
        void parsePromptDate_first_matching_prompt_wins_when_duplicates_exist() {
            ResultLineDto line = resultLine("YROEW");
            line.setPrompts(List.of(
                    new Prompt("endDate", "2026-10-30"),
                    new Prompt("endDate", "2027-01-01")));

            assertThat(PreprocessorHelper.parsePromptDate(line, "endDate", "off1"))
                    .isEqualTo(LocalDate.of(2026, 10, 30));
        }
    }

    // ── isRequirementViolated ────────────────────────────────────────────────

    @Nested
    @DisplayName("isRequirementViolated")
    class IsRequirementViolated {

        private final LocalDate orderEnd = LocalDate.of(2026, 12, 31);

        @Test
        void isRequirementViolated_requirement_date_strictly_after_order_returns_true() {
            List<ResultLineDto> lines = List.of(lineWithPrompt("YRC2", "endDate", "2027-01-31"));
            Set<String> codes = Set.of("YRC2");

            assertThat(PreprocessorHelper.isRequirementViolated(
                    lines, codes, "endDate", orderEnd, "off1")).isTrue();
        }

        @Test
        void isRequirementViolated_requirement_date_equal_to_order_returns_false() {
            List<ResultLineDto> lines = List.of(lineWithPrompt("YRC2", "endDate", "2026-12-31"));
            Set<String> codes = Set.of("YRC2");

            assertThat(PreprocessorHelper.isRequirementViolated(
                    lines, codes, "endDate", orderEnd, "off1")).isFalse();
        }

        @Test
        void isRequirementViolated_requirement_date_before_order_returns_false() {
            List<ResultLineDto> lines = List.of(lineWithPrompt("YRC2", "endDate", "2026-11-30"));
            Set<String> codes = Set.of("YRC2");

            assertThat(PreprocessorHelper.isRequirementViolated(
                    lines, codes, "endDate", orderEnd, "off1")).isFalse();
        }

        @Test
        void isRequirementViolated_no_lines_matching_codes_returns_false() {
            List<ResultLineDto> lines = List.of(lineWithPrompt("IMP", "endDate", "2027-06-01"));
            Set<String> codes = Set.of("YRC2");

            assertThat(PreprocessorHelper.isRequirementViolated(
                    lines, codes, "endDate", orderEnd, "off1")).isFalse();
        }

        @Test
        void isRequirementViolated_empty_lines_list_returns_false() {
            assertThat(PreprocessorHelper.isRequirementViolated(
                    List.of(), Set.of("YRC2"), "endDate", orderEnd, "off1")).isFalse();
        }

        @Test
        void isRequirementViolated_matching_line_with_no_prompt_returns_false() {
            ResultLineDto line = resultLine("YRC2");
            line.setPrompts(null);
            Set<String> codes = Set.of("YRC2");

            assertThat(PreprocessorHelper.isRequirementViolated(
                    List.of(line), codes, "endDate", orderEnd, "off1")).isFalse();
        }

        @Test
        void isRequirementViolated_matching_line_with_unparseable_date_returns_false() {
            List<ResultLineDto> lines = List.of(lineWithPrompt("YRC2", "endDate", "not-a-date"));
            Set<String> codes = Set.of("YRC2");

            assertThat(PreprocessorHelper.isRequirementViolated(
                    lines, codes, "endDate", orderEnd, "off1")).isFalse();
        }

        @Test
        void isRequirementViolated_match_is_case_insensitive_on_short_code() {
            List<ResultLineDto> lines = List.of(lineWithPrompt("yrc2", "endDate", "2027-01-31"));
            Set<String> codes = Set.of("YRC2");

            assertThat(PreprocessorHelper.isRequirementViolated(
                    lines, codes, "endDate", orderEnd, "off1")).isTrue();
        }

        @Test
        void isRequirementViolated_uses_specified_prompt_ref_not_a_different_one() {
            List<ResultLineDto> lines = List.of(
                    lineWithPrompt("YRC1", "endDateOfTagging", "2027-01-31"));
            Set<String> codes = Set.of("YRC1");

            // looking for wrong ref — should not trigger
            assertThat(PreprocessorHelper.isRequirementViolated(
                    lines, codes, "endDate", orderEnd, "off1")).isFalse();

            // correct ref — should trigger
            assertThat(PreprocessorHelper.isRequirementViolated(
                    lines, codes, "endDateOfTagging", orderEnd, "off1")).isTrue();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResultLineDto resultLine(final String shortCode) {
        ResultLineDto rl = new ResultLineDto();
        rl.setShortCode(shortCode);
        return rl;
    }

    private ResultLineDto resultLineWithDefendant(final String id, final String defendantId) {
        ResultLineDto rl = new ResultLineDto();
        rl.setResultLineId(id);
        rl.setDefendantId(defendantId);
        return rl;
    }

    private ResultLineDto lineWithPrompt(final String shortCode, final String ref,
                                         final String value) {
        ResultLineDto rl = resultLine(shortCode);
        rl.setPrompts(List.of(new Prompt(ref, value)));
        return rl;
    }

    private DefendantDto defendant(final String id, final String first, final String last) {
        DefendantDto d = new DefendantDto();
        d.setDefendantId(id);
        d.setFirstName(first);
        d.setLastName(last);
        return d;
    }

    private DraftValidationRequest requestWithLines(final List<ResultLineDto> lines) {
        DraftValidationRequest req = new DraftValidationRequest();
        req.setResultLines(lines);
        return req;
    }
}
