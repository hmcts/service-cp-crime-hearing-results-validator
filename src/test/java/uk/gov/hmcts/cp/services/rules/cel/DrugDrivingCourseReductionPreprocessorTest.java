package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.defendant;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offenceWithCode;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Unit tests for the per-offence preprocessor that drives DR-DISQ-013.
 *
 * <p>{@code relevantOffenceCodes} and {@code courseReductionShortCodes} are read from the real
 * {@code DR-DISQ-013.yaml} rule file via {@link RuleDefinitionLoader} rather than duplicated here
 * as literals, so {@link #config} always matches what ships in production, and the baseline tests
 * automatically catch any YAML changes.
 */
class DrugDrivingCourseReductionPreprocessorTest {

    private static final RuleDefinition RULE_DEFINITION =
            RuleDefinitionLoader.load("rules/DR-DISQ-013.yaml");

    private static final List<String> RELEVANT_CODES =
            List.copyOf(RULE_DEFINITION.preprocessing().relevantOffenceCodes());
    private static final List<String> COURSE_REDUCTION_CODES =
            List.copyOf(RULE_DEFINITION.preprocessing().courseReductionShortCodes());

    private final DrugDrivingCourseReductionPreprocessor preprocessor =
            new DrugDrivingCourseReductionPreprocessor();
    private final PreprocessingDefinition config = RULE_DEFINITION.preprocessing();

    @Test
    @DisplayName("DR-DISQ-013.yaml must declare exactly five relevant offence codes")
    void yaml_contains_exactly_five_relevant_offence_codes() {
        assertThat(RELEVANT_CODES)
                .as("DR-DISQ-013.yaml's relevantOffenceCodes must contain exactly 5 drug driving codes")
                .hasSize(5)
                .containsExactlyInAnyOrder("RT88584", "RT88586", "RT88584B", "RT88340", "RA88045");
    }

    @Test
    @DisplayName("DR-DISQ-013.yaml must declare exactly two course-reduction short codes")
    void yaml_contains_exactly_two_course_reduction_short_codes() {
        assertThat(COURSE_REDUCTION_CODES)
                .as("DR-DISQ-013.yaml's courseReductionShortCodes must contain exactly DDRCO and DDRCOT")
                .hasSize(2)
                .containsExactlyInAnyOrder("DDRCO", "DDRCOT");
    }

    @Nested
    @DisplayName("Ac1Qualifying — triggering offence with DDRCO or DDRCOT")
    class Ac1Qualifying {

        @Test
        void rt88584_with_ddrco_category_f_should_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DDRCO", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Drug driving s.5A", "RT88584")));

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
            assertThat(ctx.allOffenceIds()).containsExactly("off1");
        }

        @Test
        void rt88584_with_ddrco_category_a_should_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DDRCO", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.A)),
                    List.of(offenceWithCode("off1", 1, "Drug driving s.5A", "RT88584")));

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
        }

        @Test
        void rt88584_with_ddrcot_should_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DDRCOT", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Drug driving s.5A", "RT88584")));

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
        }

        @Test
        void all_five_offence_codes_with_ddrco_should_qualify() {
            for (String code : RELEVANT_CODES) {
                DraftValidationRequest request = buildRequest(
                        List.of(resultLine("rl1", "DDRCO", "d1", "off1")),
                        List.of(offenceWithCode("off1", 1, "Drug driving offence", code)));

                DrugDrivingContext ctx = preprocess(request).get("off1");

                assertThat(ctx.qualifyingCount())
                        .as("offenceCode=%s with DDRCO should qualify", code)
                        .isEqualTo(1L);
                assertThat(ctx.qualifyingOffenceIds())
                        .as("offenceCode=%s should produce qualifyingOffenceIds=[off1]", code)
                        .containsExactly("off1");
            }
        }
    }

    @Nested
    @DisplayName("Ac4Resolving — no DDRCO/DDRCOT present")
    class Ac4Resolving {

        @Test
        void rt88584_with_ddo_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DDO", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Drug driving s.5A", "RT88584")));

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
            assertThat(ctx.allOffenceIds()).containsExactly("off1");
        }

        @Test
        void rt88584_with_no_results_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(),
                    List.of(offenceWithCode("off1", 1, "Drug driving s.5A", "RT88584")));

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Ac5MultiOffence — one context per offence")
    class Ac5MultiOffence {

        @Test
        void two_triggering_offences_both_qualify_independently() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "DDRCO", "d1", "off1"),
                            resultLine("rl2", "DDRCO", "d1", "off2")),
                    List.of(
                            offenceWithCode("off1", 1, "Drug driving s.5A", "RT88584"),
                            offenceWithCode("off2", 2, "Drug driving s.5A attempt", "RT88586")));

            Map<String, DrugDrivingContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("off1", "off2");
            assertThat(result.get("off1").qualifyingCount()).isEqualTo(1L);
            assertThat(result.get("off2").qualifyingCount()).isEqualTo(1L);
            assertThat(result.get("off1").qualifyingOffenceIds()).containsExactly("off1");
            assertThat(result.get("off2").qualifyingOffenceIds()).containsExactly("off2");
        }
    }

    @Nested
    @DisplayName("Ac6NoFalsePositives — non-triggering paths")
    class Ac6NoFalsePositives {

        @Test
        void non_triggering_offence_with_ddrco_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DDRCO", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Dangerous driving", "RT88026")));

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }

        @Test
        void triggering_offence_with_ddo_should_not_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DDO", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Drug driving s.5A", "RT88584")));

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
            assertThat(ctx.qualifyingOffenceIds()).isEmpty();
        }

        @Test
        void offence_code_matching_is_case_insensitive() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "DDRCO", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Drug driving s.5A", "rt88584")));

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
        }

        @Test
        void short_code_matching_is_case_insensitive() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "ddrco", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Drug driving s.5A", "RT88584")));

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
        }
    }

    @Nested
    @DisplayName("EcMixedResults — DDRCO alongside another result on the same offence")
    class EcMixedResults {

        @Test
        void rt88584_with_ddrco_and_ddo_should_still_qualify() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "DDRCO", "d1", "off1"),
                            resultLine("rl2", "DDO", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Drug driving s.5A", "RT88584")));

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
            assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
        }
    }

    @Nested
    @DisplayName("Ac2DefendantName — defendant name is populated from request")
    class Ac2DefendantName {

        @Test
        void qualifying_context_carries_defendant_name_from_request() {
            DraftValidationRequest request = requestWithLinkedOffence("DDRCO");

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.defendantName()).isEqualTo("Alex Driver");
        }

        @Test
        void non_qualifying_context_still_carries_defendant_name_from_request() {
            DraftValidationRequest request = requestWithLinkedOffence("DDO");

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.defendantName()).isEqualTo("Alex Driver");
        }

        @Test
        void offence_with_null_defendant_id_should_produce_null_defendant_name() {
            final OffenceDto offence = OffenceDto.builder()
                    .offenceId("off1")
                    .offenceCode("RT88584")
                    .offenceTitle("Drug driving s.5A")
                    .orderIndex(1)
                    .build();
            DraftValidationRequest request = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(LocalDate.of(2026, 10, 16))
                    .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                    .resultLines(List.of(resultLine("rl1", "DDRCO", "d1", "off1")))
                    .defendants(List.of(defendant("d1", "Alex", "Driver")))
                    .offences(List.of(offence))
                    .build();

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.defendantName()).isNull();
        }

        @Test
        void offence_with_unmatched_defendant_id_should_produce_unknown_defendant_name() {
            final OffenceDto offence = OffenceDto.builder()
                    .offenceId("off1")
                    .defendantId("d99")
                    .offenceCode("RT88584")
                    .offenceTitle("Drug driving s.5A")
                    .orderIndex(1)
                    .build();
            DraftValidationRequest request = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(LocalDate.of(2026, 10, 16))
                    .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                    .resultLines(List.of(resultLine("rl1", "DDRCO", "d1", "off1")))
                    .defendants(List.of(defendant("d1", "Alex", "Driver")))
                    .offences(List.of(offence))
                    .build();

            DrugDrivingContext ctx = preprocess(request).get("off1");

            assertThat(ctx.defendantName()).isEqualTo("Unknown");
        }

        private DraftValidationRequest requestWithLinkedOffence(final String shortCode) {
            final OffenceDto offence = OffenceDto.builder()
                    .offenceId("off1")
                    .defendantId("d1")
                    .offenceCode("RT88584")
                    .offenceTitle("Drug driving s.5A")
                    .orderIndex(1)
                    .build();
            return DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(LocalDate.of(2026, 10, 16))
                    .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                    .resultLines(List.of(resultLine("rl1", shortCode, "d1", "off1")))
                    .defendants(List.of(defendant("d1", "Alex", "Driver")))
                    .offences(List.of(offence))
                    .build();
        }
    }

    @Nested
    @DisplayName("DrugDrivingContextContract — getOffenceIdSet guard")
    class DrugDrivingContextContract {

        @Test
        void getOffenceIdSet_with_unknown_name_should_throw() {
            final DrugDrivingContext ctx = new DrugDrivingContext("off1", 1L, List.of("off1"), null);

            assertThatThrownBy(() -> ctx.getOffenceIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }

    private Map<String, DrugDrivingContext> preprocess(final DraftValidationRequest request) {
        return preprocessor.preprocess(request, config);
    }
}
