package uk.gov.hmcts.cp.services.rules.cel;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PreprocessorHelper} — the shared, stateless helpers used by every
 * {@link ValidationPreprocessor} (short-code normalisation/matching, result-line grouping,
 * defendant-name assembly, prompt-date parsing, and requirement-date comparison).
 */
class PreprocessorHelperTest {

    @Nested
    @DisplayName("upperSet / upperOrNull")
    class ShortCodeNormalisation {

        @Test
        @DisplayName("upperSet upper-cases values into a set")
        void upperSet_uppercases_values() {
            assertThat(PreprocessorHelper.upperSet(List.of("yroew", "Yrc2")))
                    .containsExactlyInAnyOrder("YROEW", "YRC2");
        }

        @Test
        @DisplayName("upperSet returns empty for null input")
        void upperSet_null_returns_empty() {
            assertThat(PreprocessorHelper.upperSet(null)).isEmpty();
        }

        @Test
        @DisplayName("upperOrNull upper-cases a value and passes null through")
        void upperOrNull_behaviour() {
            assertThat(PreprocessorHelper.upperOrNull("cur")).isEqualTo("CUR");
            assertThat(PreprocessorHelper.upperOrNull(null)).isNull();
        }
    }

    @Nested
    @DisplayName("hasUpperCode / anyShortCodeIn")
    class ShortCodeMatching {

        @Test
        @DisplayName("hasUpperCode matches case-insensitively and is false for null short code")
        void hasUpperCode_behaviour() {
            assertThat(PreprocessorHelper.hasUpperCode(line("yroew"), Set.of("YROEW"))).isTrue();
            assertThat(PreprocessorHelper.hasUpperCode(line("CUR"), Set.of("YROEW"))).isFalse();
            assertThat(PreprocessorHelper.hasUpperCode(line(null), Set.of("YROEW"))).isFalse();
        }

        @Test
        @DisplayName("anyShortCodeIn is true when at least one line matches")
        void anyShortCodeIn_behaviour() {
            assertThat(PreprocessorHelper.anyShortCodeIn(
                    List.of(line("RI"), line("CUR")), Set.of("CUR"))).isTrue();
            assertThat(PreprocessorHelper.anyShortCodeIn(
                    List.of(line("RI"), line("IMP")), Set.of("CUR"))).isFalse();
            assertThat(PreprocessorHelper.anyShortCodeIn(List.of(), Set.of("CUR"))).isFalse();
        }
    }

    @Nested
    @DisplayName("groupByDefendant / groupResultsByOffence")
    class Grouping {

        @Test
        @DisplayName("groupByDefendant groups lines and skips null defendant ids")
        void groupByDefendant_groups_and_skips_null() {
            ResultLineDto a = lineFor("d1", "off1");
            ResultLineDto b = lineFor("d1", "off2");
            ResultLineDto c = lineFor("d2", "off3");
            ResultLineDto nullDef = lineFor(null, "off4");

            Map<String, List<ResultLineDto>> grouped =
                    PreprocessorHelper.groupByDefendant(request(List.of(a, b, c, nullDef), List.of()));

            assertThat(grouped).containsOnlyKeys("d1", "d2");
            assertThat(grouped.get("d1")).containsExactly(a, b);
        }

        @Test
        @DisplayName("groupResultsByOffence groups lines and skips null offence ids")
        void groupResultsByOffence_groups_and_skips_null() {
            ResultLineDto a = lineFor("d1", "off1");
            ResultLineDto b = lineFor("d2", "off1");
            ResultLineDto nullOff = lineFor("d1", null);

            Map<String, List<ResultLineDto>> grouped =
                    PreprocessorHelper.groupResultsByOffence(request(List.of(a, b, nullOff), List.of()));

            assertThat(grouped).containsOnlyKeys("off1");
            assertThat(grouped.get("off1")).containsExactly(a, b);
        }

        @Test
        @DisplayName("grouping a request with null result lines yields an empty map")
        void grouping_null_result_lines_is_empty() {
            DraftValidationRequest req = new DraftValidationRequest();
            assertThat(PreprocessorHelper.groupByDefendant(req)).isEmpty();
            assertThat(PreprocessorHelper.groupResultsByOffence(req)).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildDefendantNames / buildFullName")
    class DefendantNames {

        @Test
        @DisplayName("buildDefendantNames keys full names by defendant id")
        void buildDefendantNames_keyed_by_id() {
            Map<String, String> names = PreprocessorHelper.buildDefendantNames(
                    request(List.of(), List.of(defendant("d1", "John", "Smith"))));

            assertThat(names).containsEntry("d1", "John Smith");
        }

        @Test
        @DisplayName("buildDefendantNames is empty when the request has no defendants")
        void buildDefendantNames_null_defendants_is_empty() {
            assertThat(PreprocessorHelper.buildDefendantNames(new DraftValidationRequest())).isEmpty();
        }

        @Test
        @DisplayName("buildFullName tolerates a missing first or last name")
        void buildFullName_partial_names() {
            assertThat(PreprocessorHelper.buildFullName(defendant("d1", "John", "Smith"))).isEqualTo("John Smith");
            assertThat(PreprocessorHelper.buildFullName(defendant("d1", "John", null))).isEqualTo("John");
            assertThat(PreprocessorHelper.buildFullName(defendant("d1", null, "Smith"))).isEqualTo("Smith");
        }
    }

    @Nested
    @DisplayName("parsePromptDate")
    class ParsePromptDate {

        @Test
        @DisplayName("parses the first matching prompt as an ISO date")
        void parses_matching_prompt() {
            ResultLineDto rl = lineWithPrompts("CUR", new Prompt("endDate", "2026-10-30"));

            assertThat(PreprocessorHelper.parsePromptDate(rl, "endDate", "off1"))
                    .isEqualTo(LocalDate.of(2026, 10, 30));
        }

        @Test
        @DisplayName("returns null when the prompt ref is absent")
        void null_when_prompt_ref_absent() {
            ResultLineDto rl = lineWithPrompts("CUR", new Prompt("until", "2026-10-30"));

            assertThat(PreprocessorHelper.parsePromptDate(rl, "endDate", "off1")).isNull();
        }

        @Test
        @DisplayName("returns null for blank or unparseable values, and for null prompts")
        void null_for_blank_unparseable_and_no_prompts() {
            assertThat(PreprocessorHelper.parsePromptDate(
                    lineWithPrompts("CUR", new Prompt("endDate", "   ")), "endDate", "off1")).isNull();
            assertThat(PreprocessorHelper.parsePromptDate(
                    lineWithPrompts("CUR", new Prompt("endDate", "not-a-date")), "endDate", "off1")).isNull();
            assertThat(PreprocessorHelper.parsePromptDate(line("CUR"), "endDate", "off1")).isNull();
        }
    }

    @Nested
    @DisplayName("isRequirementViolated")
    class IsRequirementViolated {

        private final LocalDate orderEnd = LocalDate.of(2026, 10, 30);

        @Test
        @DisplayName("true when a matching requirement date is strictly after the order end date")
        void true_when_requirement_after_order_end() {
            ResultLineDto req = lineWithPrompts("CUR", new Prompt("endDate", "2026-11-30"));

            assertThat(PreprocessorHelper.isRequirementViolated(
                    List.of(req), Set.of("CUR"), "endDate", orderEnd, "off1")).isTrue();
        }

        @Test
        @DisplayName("false when the requirement date equals or precedes the order end date")
        void false_when_equal_or_before() {
            ResultLineDto equal = lineWithPrompts("CUR", new Prompt("endDate", "2026-10-30"));
            ResultLineDto before = lineWithPrompts("CUR", new Prompt("endDate", "2026-10-01"));

            assertThat(PreprocessorHelper.isRequirementViolated(
                    List.of(equal), Set.of("CUR"), "endDate", orderEnd, "off1")).isFalse();
            assertThat(PreprocessorHelper.isRequirementViolated(
                    List.of(before), Set.of("CUR"), "endDate", orderEnd, "off1")).isFalse();
        }

        @Test
        @DisplayName("false when no line carries a matching short code")
        void false_when_no_matching_code() {
            ResultLineDto req = lineWithPrompts("AAR", new Prompt("endDate", "2026-11-30"));

            assertThat(PreprocessorHelper.isRequirementViolated(
                    List.of(req), Set.of("CUR"), "endDate", orderEnd, "off1")).isFalse();
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static ResultLineDto line(final String shortCode) {
        ResultLineDto rl = new ResultLineDto();
        rl.setShortCode(shortCode);
        return rl;
    }

    private static ResultLineDto lineFor(final String defendantId, final String offenceId) {
        ResultLineDto rl = new ResultLineDto();
        rl.setShortCode("CUR");
        rl.setDefendantId(defendantId);
        rl.setOffenceId(offenceId);
        return rl;
    }

    private static ResultLineDto lineWithPrompts(final String shortCode, final Prompt... prompts) {
        ResultLineDto rl = new ResultLineDto();
        rl.setShortCode(shortCode);
        rl.setPrompts(List.of(prompts));
        return rl;
    }

    private static DefendantDto defendant(final String id, final String first, final String last) {
        DefendantDto d = new DefendantDto();
        d.setId(id);
        d.setFirstName(first);
        d.setLastName(last);
        return d;
    }

    private static DraftValidationRequest request(final List<ResultLineDto> lines,
                                                  final List<DefendantDto> defendants) {
        DraftValidationRequest req = new DraftValidationRequest();
        req.setResultLines(lines);
        req.setDefendants(defendants);
        return req;
    }
}
