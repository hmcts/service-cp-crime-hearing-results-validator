package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offenceWithCode;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClient;

/**
 * Unit tests for {@link EndorsableOffencePreprocessor} (DR-ENDORSEMENT-012).
 * {@link ReferencedataOffenceClient} is mocked — its HTTP/fail-open behaviour is covered by
 * {@code ReferencedataOffenceClientTest}. Code lists are loaded from the real
 * {@code DR-ENDORSEMENT-012.yaml} so a code added or removed in YAML fails loudly.
 */
@ExtendWith(MockitoExtension.class)
class EndorsableOffencePreprocessorTest {

    private static final RuleDefinition RULE_DEFINITION =
            RuleDefinitionLoader.load("rules/DR-ENDORSEMENT-012.yaml");

    private static final List<String> EXCLUDED_FINAL_SHORT_CODES =
            List.copyOf(RULE_DEFINITION.preprocessing().excludedFinalShortCodes());

    private static final List<String> SATISFYING_SHORT_CODES =
            List.copyOf(RULE_DEFINITION.preprocessing().satisfyingShortCodes());

    private static final List<String> EXPECTED_EXCLUDED_FINAL_SHORT_CODES = List.of(
            "WDRN", "WDRNOFF", "DISM", "DINE", "DINI", "DISCH", "DISC", "CTROF", "IREMFILE",
            "ERR", "ERRF", "DHD", "ONI", "DCS", "DCCFSA", "DCCFSTA", "CQUASH", "IQUASH",
            "RESTRAO", "STAYP", "RBBH", "SOCOR", "PDW", "RBBO");

    private static final List<String> EXPECTED_SATISFYING_SHORT_CODES = List.of(
            "LEP", "LEN", "LEA",
            "DDO", "DDOL", "DDOR", "DDOTE", "DDOTEL",
            "DDD", "DDDL", "DDDT", "DDDTL", "DDDTO",
            "DDP", "DDPL", "DDPR", "DDPTE", "DDPTEL",
            "DDRCOT",
            "NESR", "NDSR");

    @Mock
    private ReferencedataOffenceClient referencedataOffenceClient;

    private EndorsableOffencePreprocessor preprocessor;
    private PreprocessingDefinition config;

    @BeforeEach
    void setUp() {
        preprocessor = new EndorsableOffencePreprocessor(referencedataOffenceClient);
        config = RULE_DEFINITION.preprocessing();
    }

    @Test
    @DisplayName("DR-ENDORSEMENT-012.yaml's excludedFinalShortCodes must exactly match the known baseline")
    void excludedFinalShortCodes_should_match_the_known_baseline_exactly() {
        assertThat(EXCLUDED_FINAL_SHORT_CODES)
                .as("DR-ENDORSEMENT-012.yaml's excludedFinalShortCodes must exactly match "
                        + "EXPECTED_EXCLUDED_FINAL_SHORT_CODES")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_EXCLUDED_FINAL_SHORT_CODES);
    }

    @Test
    @DisplayName("DR-ENDORSEMENT-012.yaml's satisfyingShortCodes must exactly match the known baseline")
    void satisfyingShortCodes_should_match_the_known_baseline_exactly() {
        assertThat(SATISFYING_SHORT_CODES)
                .as("DR-ENDORSEMENT-012.yaml's satisfyingShortCodes must exactly match "
                        + "EXPECTED_SATISFYING_SHORT_CODES")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_SATISFYING_SHORT_CODES);
    }

    static List<String> excludedFinalShortCodes() {
        return EXCLUDED_FINAL_SHORT_CODES;
    }

    static List<String> satisfyingShortCodes() {
        return SATISFYING_SHORT_CODES;
    }

    @Nested
    @DisplayName("AC1 — endorsable offence, final result, no satisfying result")
    class Ac1EndorsableOffenceFinalNoSatisfying {

        @Test
        void preprocess_endorsableOffenceWithFinalResultAndNoSatisfyingCode_should_returnQualifyingCount1() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
        }

        @Test
        void preprocess_endorsableOffenceWithNonExcludedFinalAndNoSatisfying_should_returnQualifyingCount1() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "COEW", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
        }

        @Test
        void preprocess_endorsableOffenceWithOnlyNonFinalLine_should_returnQualifyingCount0() {
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.A)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_endorsableOffenceWithNoResultLines_should_returnQualifyingCount0() {
            final DraftValidationRequest request = buildRequest(
                    List.of(),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_unknownShortCode_shouldBeTreatedAsNonExcluded_and_returnQualifyingCount1() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "ZZZZ", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("AC2 — satisfying result present → no warning")
    class Ac2SatisfyingResultPresent {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"LEP", "DDO", "DDD", "DDP", "DDRCOT", "NESR"})
        void preprocess_endorsableOffenceWithSatisfyingCode_should_returnQualifyingCount0(
                final String satisfyingCode) {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", satisfyingCode, "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount())
                    .as("satisfying code %s should suppress warning", satisfyingCode)
                    .isEqualTo(0L);
        }

        @ParameterizedTest
        @MethodSource(
                "uk.gov.hmcts.cp.services.rules.cel.EndorsableOffencePreprocessorTest#satisfyingShortCodes")
        void each_satisfying_code_should_suppress(final String satisfyingCode) {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", satisfyingCode, "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount())
                    .as("satisfying code %s should suppress", satisfyingCode)
                    .isEqualTo(0L);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"lep", "LeP", "ddo", "DdO", "nesr", "NeSr"})
        void mixed_case_satisfying_codes_should_suppress(final String mixedCase) {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", mixedCase, "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount())
                    .as("mixed-case satisfying code %s should suppress", mixedCase)
                    .isEqualTo(0L);
        }

        @Test
        void satisfying_code_on_non_final_line_should_suppress() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "LEP", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.A)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("AC3 — excluded final result only → no warning")
    class Ac3ExcludedFinalOnly {

        @ParameterizedTest
        @MethodSource(
                "uk.gov.hmcts.cp.services.rules.cel.EndorsableOffencePreprocessorTest#excludedFinalShortCodes")
        void each_excluded_final_code_should_suppress(final String excludedCode) {
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", excludedCode, "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount())
                    .as("excluded final code %s should suppress", excludedCode)
                    .isEqualTo(0L);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"wdrn", "Wdrn", "dism", "DiSm", "Disc", "IREMFILE", "iremfile"})
        void mixed_case_excluded_codes_should_suppress(final String mixedCase) {
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", mixedCase, "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount())
                    .as("mixed-case excluded code %s should suppress", mixedCase)
                    .isEqualTo(0L);
        }

        @Test
        void excluded_final_plus_non_excluded_final_should_still_qualify() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "WDRN", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "IMP", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("AC4 — non-endorsable offence → no warning")
    class Ac4NonEndorsable {

        @Test
        void preprocess_nonEndorsableOffence_should_returnQualifyingCount0() {
            when(referencedataOffenceClient.getEndorsableFlag("TH68001")).thenReturn(Optional.of(0));
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Theft", "TH68001")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_endorsableFlagAbsent_should_returnQualifyingCount0() {
            when(referencedataOffenceClient.getEndorsableFlag("TH68001")).thenReturn(Optional.empty());
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Theft", "TH68001")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_endorsableFlagNull_should_returnQualifyingCount0() {
            when(referencedataOffenceClient.getEndorsableFlag("TH68001")).thenReturn(Optional.ofNullable(null));
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Theft", "TH68001")));

            final EndorsableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx.qualifyingCount()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("AC6 — multiple offences assessed independently")
    class Ac6MultipleOffences {

        @Test
        void preprocess_mixedEndorsableAndNonEndorsableOffences_should_returnWarningOnlyForEndorsable() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            when(referencedataOffenceClient.getEndorsableFlag("TH68001")).thenReturn(Optional.of(0));
            final DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "IMP", "d1", "off2")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(
                            offenceWithCode("off1", 1, "Speeding", "RT88010"),
                            offenceWithCode("off2", 2, "Theft", "TH68001")));

            final Map<String, EndorsableOffenceContext> result = preprocess(request);

            assertThat(result).containsOnlyKeys("off1", "off2");
            assertThat(result.get("off1").qualifyingCount()).isEqualTo(1L);
            assertThat(result.get("off2").qualifyingCount()).isEqualTo(0L);
        }

        @Test
        void preprocess_twoEndorsableOffences_bothWithFinalAndNoSatisfying_should_bothQualify() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            when(referencedataOffenceClient.getEndorsableFlag("RT88015")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "IMP", "d1", "off2")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(
                            offenceWithCode("off1", 1, "Speeding 1", "RT88010"),
                            offenceWithCode("off2", 2, "Speeding 2", "RT88015")));

            final Map<String, EndorsableOffenceContext> result = preprocess(request);

            assertThat(result.get("off1").qualifyingCount()).isEqualTo(1L);
            assertThat(result.get("off2").qualifyingCount()).isEqualTo(1L);
        }

        @Test
        void satisfying_code_on_one_offence_should_not_suppress_other_offence() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            when(referencedataOffenceClient.getEndorsableFlag("RT88015")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "IMP", "d1", "off2")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl3", "LEP", "d1", "off2")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(
                            offenceWithCode("off1", 1, "Speeding 1", "RT88010"),
                            offenceWithCode("off2", 2, "Speeding 2", "RT88015")));

            final Map<String, EndorsableOffenceContext> result = preprocess(request);

            assertThat(result.get("off1").qualifyingCount()).isEqualTo(1L);
            assertThat(result.get("off2").qualifyingCount()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Context shape")
    class ContextShape {

        @Test
        void context_should_contain_offenceId_and_be_keyed_by_offenceId() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(1));
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final Map<String, EndorsableOffenceContext> result = preprocess(request);

            assertThat(result).containsKey("off1");
            assertThat(result.get("off1").offenceId()).isEqualTo("off1");
        }

        @Test
        void context_is_emitted_for_every_offence_including_non_qualifying() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88010")).thenReturn(Optional.of(0));
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88010")));

            final Map<String, EndorsableOffenceContext> result = preprocess(request);

            assertThat(result).containsKey("off1");
        }
    }

    private Map<String, EndorsableOffenceContext> preprocess(final DraftValidationRequest request) {
        return preprocessor.preprocess(request, config);
    }
}
