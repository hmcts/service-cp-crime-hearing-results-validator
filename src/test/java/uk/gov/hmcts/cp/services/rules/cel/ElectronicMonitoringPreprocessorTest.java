package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.defendant;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLineWithPrompt;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Unit tests for {@link ElectronicMonitoringPreprocessor} (DR-EMON-017).
 *
 * <p>The short-code and prompt-ref lists are loaded from the production YAML via
 * {@link RuleDefinitionLoader}, so the tests always reflect the actual rule configuration.
 */
class ElectronicMonitoringPreprocessorTest {

    private static final RuleDefinition RULE_DEFINITION =
            RuleDefinitionLoader.load("rules/DR-EMON-017.yaml");

    private final ElectronicMonitoringPreprocessor preprocessor =
            new ElectronicMonitoringPreprocessor();

    private final PreprocessingDefinition config = RULE_DEFINITION.preprocessing();

    // ─────────────────────────────────────────────────────────────────────────
    // Qualifier
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("type() must return the electronic-monitoring-status qualifier")
    void type_should_return_electronic_monitoring_status() {
        assertThat(preprocessor.type()).isEqualTo("electronic-monitoring-status");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T006 — AC2 error scenarios
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC2 — Error: EM-flagged offence with result that neither continues nor ends EM")
    class Ac2ErrorScenarios {

        @Test
        @DisplayName("(a) EM-flagged offence with final result (IMP, category F), no EMONE, no continuing prompt → unresolvedEmCount=1")
        void emFlaggedOffence_withFinalResultNoEmonNoPrompt_should_returnUnresolvedContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).containsKey("d1");
            ElectronicMonitoringContext ctx = contexts.get("d1");
            assertThat(ctx.unresolvedEmCount()).isEqualTo(1L);
            assertThat(ctx.unresolvedOffenceIds()).containsExactly("off-1");
        }

        @Test
        @DisplayName("(b) EM-flagged offence with non-exempt interim result (RC, category I), no EM → error")
        void emFlaggedOffence_withNonExemptInterimResultNoEm_should_returnUnresolvedContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RC", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).containsKey("d1");
            assertThat(contexts.get("d1").unresolvedEmCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("(c) EM-flagged offence with COEW (category F), no EMONE → error")
        void emFlaggedOffence_withCoewFinalResultNoEmone_should_returnUnresolvedContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "COEW", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).containsKey("d1");
        }

        @Test
        @DisplayName("(d) EMREQ prompt present but value is 'No' → NOT a continuing result → error")
        void emFlaggedOffence_withEmreqPromptValueNo_should_returnUnresolvedContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RC", "d1", "off-1", "EMREQ", "No")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).containsKey("d1");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T007 — AC3 scenarios: exempt code as child of non-exempt main result
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC3 — Exempt code as child does NOT resolve the offence")
    class Ac3ExemptAsChild {

        @Test
        @DisplayName("(a) NHMC as category I alongside RC (also category I) → RC disqualifies → error")
        void nhmc_alongside_rc_both_categoryI_should_returnUnresolvedContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.I),
                            resultLine("rl2", "NHMC", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).containsKey("d1");
            assertThat(contexts.get("d1").unresolvedOffenceIds()).containsExactly("off-1");
        }

        @Test
        @DisplayName("(b) NHCC alongside non-exempt main result → error")
        void nhcc_alongside_nonExemptMainResult_should_returnUnresolvedContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "REMUB", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.I),
                            resultLine("rl2", "NHCC", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).containsKey("d1");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T008 — AC1 happy paths: all nine resolution combinations
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC1 — No error: all EM-flagged offences resolved")
    class Ac1ResolvedCombinations {

        @Test
        @DisplayName("(a) All EM-flagged offences have EMONE → resolved")
        void allOffencesWithEmone_should_returnEmptyContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "EMONE", "d1", "off-1"),
                            resultLine("rl2", "EMONE", "d1", "off-2"),
                            resultLine("rl3", "EMONE", "d1", "off-3")),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d1"), emOffence("off-3", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).isEmpty();
        }

        @Test
        @DisplayName("(b) All EM-flagged offences have RC with ELMONDET=Yes → resolved")
        void allOffencesWithElmondetYes_should_returnEmptyContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLineWithPrompt("rl1", "RC", "d1", "off-1", "ELMONDET", "Yes")
                                    .category(ResultLineDto.CategoryEnum.I),
                            resultLineWithPrompt("rl2", "RC", "d1", "off-2", "ELMONDET", "Yes")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).isEmpty();
        }

        @Test
        @DisplayName("(c) All EM-flagged offences have COEW with EMREQ=Yes → resolved")
        void allOffencesWithEmreqYes_should_returnEmptyContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLineWithPrompt("rl1", "COEW", "d1", "off-1", "EMREQ", "YES")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).isEmpty();
        }

        @Test
        @DisplayName("(d) Mixed: COEW+EMREQ=Yes plus two EMONE → resolved")
        void mixedContinueAndEndResults_should_returnEmptyContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLineWithPrompt("rl1", "COEW", "d1", "off-1", "EMREQ", "Yes")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "EMONE", "d1", "off-2"),
                            resultLine("rl3", "EMONE", "d1", "off-3")),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d1"), emOffence("off-3", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).isEmpty();
        }

        @Test
        @DisplayName("(e) Mixed: RC+ELMONDET=Yes plus two EMONE → resolved")
        void mixedRcContinueAndEmone_should_returnEmptyContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLineWithPrompt("rl1", "RC", "d1", "off-1", "ELMONDET", "Yes")
                                    .category(ResultLineDto.CategoryEnum.I),
                            resultLine("rl2", "EMONE", "d1", "off-2"),
                            resultLine("rl3", "EMONE", "d1", "off-3")),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d1"), emOffence("off-3", "d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("(f) Mixed: RC+ELMONDET=Yes plus EMONE plus blank offence → resolved")
        void mixedContinueEndAndBlank_should_returnEmptyContext() {
            DraftValidationRequest request = buildRequestWithExplicitDefendants(
                    List.of(
                            resultLineWithPrompt("rl1", "RC", "d1", "off-1", "ELMONDET", "Yes")
                                    .category(ResultLineDto.CategoryEnum.I),
                            resultLine("rl2", "EMONE", "d1", "off-2")),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d1"), emOffence("off-3", "d1")),
                    List.of(defendant("d1", "John", "Smith")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("(g) All blank: no results on any EM-flagged offence → resolved")
        void allBlankEmOffences_should_returnEmptyContext() {
            DraftValidationRequest request = buildRequestWithExplicitDefendants(
                    List.of(),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d1")),
                    List.of(defendant("d1", "John", "Smith")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("(h) Exempt main results: NHMC, A, ADJNN each as sole category-I result → resolved")
        void allExemptMainResults_should_returnEmptyContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "NHMC", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.I),
                            resultLine("rl2", "A", "d1", "off-2")
                                    .category(ResultLineDto.CategoryEnum.I),
                            resultLine("rl3", "ADJNN", "d1", "off-3")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d1"), emOffence("off-3", "d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("(i) Ancillary-only: ODIR and SMP, all category A → resolved")
        void ancillaryOnlyResults_should_returnEmptyContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "ODIR", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.A),
                            resultLine("rl2", "SMP", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.A)),
                    List.of(emOffence("off-1", "d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @ParameterizedTest(name = "continuing EM prompt {0}=Yes resolves EM offence")
        @ValueSource(strings = {"ELMONDET", "EMREQ", "EMREQV", "EMSO"})
        void eachContinuingPromptRef_withYesValue_should_resolveEmOffence(final String promptRef) {
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RC", "d1", "off-1", promptRef, "Yes")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @ParameterizedTest(name = "exempt main result {0} as sole category-I result resolves EM offence")
        @ValueSource(strings = {"NHMC", "NHCC", "A", "ADJNN", "ADJAPP"})
        void eachExemptMainResultCode_asSoleCategoryI_should_resolveEmOffence(final String shortCode) {
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", shortCode, "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T010 — Edge cases
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("EMSO prompt with promptValue=Yes → resolved")
        void emsoPromptYes_should_resolveOffence() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "COEW", "d1", "off-1", "EMSO", "Yes")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("EMREQV prompt with promptValue=Yes → resolved")
        void emreqvPromptYes_should_resolveOffence() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "COEW", "d1", "off-1", "EMREQV", "Yes")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("Offence with IMP + EMONE → resolved (EMONE is ending result)")
        void impAndEmone_should_resolveOffence() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "EMONE", "d1", "off-1")),
                    List.of(emOffence("off-1", "d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("Offence with IMP + ELMONDET=Yes → resolved")
        void impAndElmondetYes_should_resolveOffence() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLineWithPrompt("rl2", "RC", "d1", "off-1", "ELMONDET", "Yes")
                                    .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("NHMC as only category-I result (sole exempt code) → resolved")
        void nhmcAsSoleExemptMainResult_should_resolveOffence() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "NHMC", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("Multiple EM-flagged offences, only one unresolved → only unresolved in context")
        void multipleEmOffences_onlyOneUnresolved_should_listOnlyUnresolvedOffence() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "EMONE", "d1", "off-1"),
                            resultLine("rl2", "IMP", "d1", "off-2")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d1")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).containsKey("d1");
            assertThat(contexts.get("d1").unresolvedOffenceIds()).containsExactly("off-2");
            assertThat(contexts.get("d1").unresolvedEmCount()).isEqualTo(1L);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T011 — Blank / partial scenarios
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Blank and partial results")
    class BlankAndPartialResults {

        @Test
        @DisplayName("EM-flagged offence with no result lines → resolved (blank)")
        void emFlaggedOffence_withNoResults_should_resolveOffence() {
            DraftValidationRequest request = buildRequestWithExplicitDefendants(
                    List.of(),
                    List.of(emOffence("off-1", "d1")),
                    List.of(defendant("d1", "Jane", "Doe")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("Two EM-flagged offences, only one has a result → unresolved offence listed, blank offence resolved")
        void twoEmOffences_oneResultOneBlank_should_listOnlyResultedUnresolvedOffence() {
            DraftValidationRequest request = buildRequestWithExplicitDefendants(
                    List.of(resultLine("rl1", "IMP", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d1")),
                    List.of(defendant("d1", "Jane", "Doe")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).containsKey("d1");
            assertThat(contexts.get("d1").unresolvedOffenceIds()).containsExactly("off-1");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T012 — AC9: no EM-flagged offences
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC9 — No validation when no offence has the EM flag")
    class Ac9NoEmFlag {

        @Test
        @DisplayName("Hearing with offences all having hasActiveElectronicMonitoring=false → empty context")
        void noEmFlaggedOffences_shouldReturnEmptyContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off-1", 1, "Theft").defendantId("d1")
                            .hasActiveElectronicMonitoring(false)));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        @DisplayName("Hearing with offence having hasActiveElectronicMonitoring=null → empty context")
        void nullEmFlag_shouldBeTreatedAsFalse_andReturnEmptyContext() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(offence("off-1", 1, "Theft").defendantId("d1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T013 — AC4: multi-defendant scenarios
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC4 — Multi-defendant: separate contexts per affected defendant")
    class Ac4MultiDefendant {

        @Test
        @DisplayName("Two defendants, both with unresolved EM offences → two contexts with distinct ids")
        void twoDefendantsBothUnresolved_should_emitTwoContexts() {
            DraftValidationRequest request = buildRequestWithExplicitDefendants(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "IMP", "d2", "off-2")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d2")),
                    List.of(defendant("d1", "John", "Smith"), defendant("d2", "Jane", "Jones")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).containsKeys("d1", "d2");
            assertThat(contexts.get("d1").defendantId()).isEqualTo("d1");
            assertThat(contexts.get("d2").defendantId()).isEqualTo("d2");
        }

        @Test
        @DisplayName("Two defendants, only one unresolved → only affected defendant emits context")
        void twoDefendantsOnlyOneUnresolved_should_emitOnlyAffectedContext() {
            DraftValidationRequest request = buildRequestWithExplicitDefendants(
                    List.of(
                            resultLine("rl1", "EMONE", "d1", "off-1"),
                            resultLine("rl2", "IMP", "d2", "off-2")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d2")),
                    List.of(defendant("d1", "John", "Smith"), defendant("d2", "Jane", "Jones")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).containsOnlyKeys("d2");
        }

        @Test
        @DisplayName("Three defendants, all unresolved → three contexts with correct names")
        void threeDefendantsAllUnresolved_should_emitThreeContextsWithCorrectNames() {
            DraftValidationRequest request = buildRequestWithExplicitDefendants(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off-1")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl2", "IMP", "d2", "off-2")
                                    .category(ResultLineDto.CategoryEnum.F),
                            resultLine("rl3", "IMP", "d3", "off-3")
                                    .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1"), emOffence("off-2", "d2"), emOffence("off-3", "d3")),
                    List.of(
                            defendant("d1", "John", "Smith"),
                            defendant("d2", "Jane", "Jones"),
                            defendant("d3", "Sam", "Patel")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts).hasSize(3);
            assertThat(contexts.get("d1").defendantName()).isEqualTo("John Smith");
            assertThat(contexts.get("d2").defendantName()).isEqualTo("Jane Jones");
            assertThat(contexts.get("d3").defendantName()).isEqualTo("Sam Patel");
        }

        @Test
        @DisplayName("Single defendant, error triggered → context has correct defendantName")
        void singleDefendant_errorTriggered_should_populateDefendantName() {
            DraftValidationRequest request = buildRequestWithExplicitDefendants(
                    List.of(resultLine("rl1", "IMP", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.F)),
                    List.of(emOffence("off-1", "d1")),
                    List.of(defendant("d1", "John", "Smith")));

            Map<String, ElectronicMonitoringContext> contexts = preprocessor.preprocess(request, config);

            assertThat(contexts.get("d1").defendantName()).isEqualTo("John Smith");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static OffenceDto emOffence(final String id, final String defendantId) {
        return offence(id, 1, "Theft").defendantId(defendantId).hasActiveElectronicMonitoring(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Null-safety / defensive-branch coverage
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Null-safety and defensive branch coverage")
    class NullSafetyEdgeCases {

        @Test
        @DisplayName("null offences list → empty map (no EM-flagged offences found)")
        void nullOffencesList_should_returnEmptyMap() {
            final DraftValidationRequest request = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(LocalDate.of(2026, 3, 11))
                    .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                    .resultLines(List.of())
                    .defendants(List.of())
                    .offences(null)
                    .build();

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("EM-flagged offence with null offenceId → not collected → empty map")
        void emFlaggedOffenceWithNullOffenceId_should_returnEmptyMap() {
            final OffenceDto offenceNullId = OffenceDto.builder()
                    .offenceCode("TH68001")
                    .offenceTitle("Theft")
                    .orderIndex(1)
                    .defendantId("d1")
                    .hasActiveElectronicMonitoring(true)
                    .build();
            final DraftValidationRequest request = buildRequest(List.of(), List.of(offenceNullId));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("EM-flagged offence with null defendantId → not collected → empty map")
        void emFlaggedOffenceWithNullDefendantId_should_returnEmptyMap() {
            final OffenceDto offenceNullDefendant = OffenceDto.builder()
                    .offenceId("off-1")
                    .offenceCode("TH68001")
                    .offenceTitle("Theft")
                    .orderIndex(1)
                    .hasActiveElectronicMonitoring(true)
                    .build();
            final DraftValidationRequest request = buildRequest(List.of(), List.of(offenceNullDefendant));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("continuingEmPromptRefs=null in config → prompt check skipped → unresolved")
        void nullContinuingEmPromptRefs_should_skipPromptCheck() {
            final PreprocessingDefinition nullPromptsConfig = PreprocessingDefinition.builder()
                    .type("electronic-monitoring-status")
                    .continuingEmPromptRefs(null)
                    .endingEmShortCodes(List.of("EMONE"))
                    .exemptMainResultShortCodes(List.of("NHMC", "NHCC", "A", "ADJNN", "ADJAPP"))
                    .build();
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RC", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, nullPromptsConfig);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("exemptMainResultShortCodes=null in config → exempt check skipped → unresolved")
        void nullExemptMainResultShortCodes_should_skipExemptCheck() {
            final PreprocessingDefinition nullExemptConfig = PreprocessingDefinition.builder()
                    .type("electronic-monitoring-status")
                    .continuingEmPromptRefs(List.of("ELMONDET", "EMREQ", "EMREQV", "EMSO"))
                    .endingEmShortCodes(List.of("EMONE"))
                    .exemptMainResultShortCodes(null)
                    .build();
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "NHMC", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, nullExemptConfig);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("prompt with unknown promptRef → not a continuing result → error fires")
        void unknownPromptRef_should_notResolveOffence() {
            final ResultLineDto lineWithUnknownPrompt = resultLine("rl1", "COEW", "d1", "off-1")
                    .category(ResultLineDto.CategoryEnum.F);
            lineWithUnknownPrompt.setPrompts(List.of(new Prompt("UNKNOWN_REF", "Yes")));
            final DraftValidationRequest request = buildRequest(
                    List.of(lineWithUnknownPrompt),
                    List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("category-I result with null shortCode → not exempt → error fires")
        void categoryIResultWithNullShortCode_should_notBeExempt() {
            final ResultLineDto lineNullShortCode = ResultLineDto.builder()
                    .resultLineId("rl1")
                    .label("some label")
                    .category(ResultLineDto.CategoryEnum.I)
                    .defendantId("d1")
                    .offenceId("off-1")
                    .build();
            final DraftValidationRequest request = buildRequest(
                    List.of(lineNullShortCode),
                    List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("continuingEmPromptRefs=empty list in config → prompt check skipped → unresolved")
        void emptyContinuingEmPromptRefs_should_skipPromptCheck() {
            final PreprocessingDefinition emptyPromptsConfig = PreprocessingDefinition.builder()
                    .type("electronic-monitoring-status")
                    .continuingEmPromptRefs(List.of())
                    .endingEmShortCodes(List.of("EMONE"))
                    .exemptMainResultShortCodes(List.of("NHMC", "NHCC", "A", "ADJNN", "ADJAPP"))
                    .build();
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RC", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, emptyPromptsConfig);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("exemptMainResultShortCodes=empty list in config → exempt check skipped → unresolved")
        void emptyExemptMainResultShortCodes_should_skipExemptCheck() {
            final PreprocessingDefinition emptyExemptConfig = PreprocessingDefinition.builder()
                    .type("electronic-monitoring-status")
                    .continuingEmPromptRefs(List.of("ELMONDET", "EMREQ", "EMREQV", "EMSO"))
                    .endingEmShortCodes(List.of("EMONE"))
                    .exemptMainResultShortCodes(List.of())
                    .build();
            final DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "NHMC", "d1", "off-1")
                            .category(ResultLineDto.CategoryEnum.I)),
                    List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, emptyExemptConfig);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("result line with non-null but empty prompts list → not a continuing result")
        void emptyPromptsList_should_notResolveOffence() {
            final ResultLineDto lineEmptyPrompts = resultLine("rl1", "COEW", "d1", "off-1")
                    .category(ResultLineDto.CategoryEnum.F);
            lineEmptyPrompts.setPrompts(List.of());
            final DraftValidationRequest request = buildRequest(
                    List.of(lineEmptyPrompts),
                    List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("result line with null prompts list → not a continuing result")
        void nullPromptsList_should_notResolveOffence() {
            final ResultLineDto lineNullPrompts = resultLine("rl1", "COEW", "d1", "off-1")
                    .category(ResultLineDto.CategoryEnum.F);
            lineNullPrompts.setPrompts(null);
            final DraftValidationRequest request = buildRequest(
                    List.of(lineNullPrompts),
                    List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("result line with null category → not ancillary-only → offence is unresolved")
        void nullCategory_should_notBeAncillaryOnly() {
            final ResultLineDto lineNullCategory = ResultLineDto.builder()
                    .resultLineId("rl1")
                    .shortCode("IMP")
                    .label("Imprisonment")
                    .defendantId("d1")
                    .offenceId("off-1")
                    .build();
            final DraftValidationRequest request = buildRequest(
                    List.of(lineNullCategory),
                    List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("prompt with matching promptRef but null promptValue → not a continuing result → error fires")
        void matchingPromptRef_withNullPromptValue_should_notResolveOffence() {
            final ResultLineDto line = resultLine("rl1", "COEW", "d1", "off-1")
                    .category(ResultLineDto.CategoryEnum.F);
            line.setPrompts(List.of(new Prompt("EMREQ", null)));
            final DraftValidationRequest request = buildRequest(
                    List.of(line), List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("prompt with null promptRef and promptValue=Yes → not a continuing result → error fires")
        void nullPromptRef_withYesValue_should_notResolveOffence() {
            final ResultLineDto line = resultLine("rl1", "COEW", "d1", "off-1")
                    .category(ResultLineDto.CategoryEnum.F);
            line.setPrompts(List.of(new Prompt(null, "Yes")));
            final DraftValidationRequest request = buildRequest(
                    List.of(line), List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("result line with null category → not an exempt main result")
        void nullCategory_should_notBeExemptMainResult() {
            final ResultLineDto lineNullCategory = ResultLineDto.builder()
                    .resultLineId("rl1")
                    .shortCode("NHMC")
                    .label("No hearing case management")
                    .defendantId("d1")
                    .offenceId("off-1")
                    .build();
            final DraftValidationRequest request = buildRequest(
                    List.of(lineNullCategory),
                    List.of(emOffence("off-1", "d1")));

            final Map<String, ElectronicMonitoringContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).containsKey("d1");
            assertThat(result.get("d1").unresolvedEmCount()).isEqualTo(1);
        }
    }

    private static DraftValidationRequest buildRequest(final List<ResultLineDto> resultLines,
                                                        final List<OffenceDto> offences) {
        Map<String, DefendantDto> defendants = new LinkedHashMap<>();
        for (ResultLineDto rl : resultLines) {
            if (rl.getDefendantId() != null) {
                defendants.computeIfAbsent(rl.getDefendantId(),
                        id -> defendant(id, "John", "Smith"));
            }
        }
        for (OffenceDto off : offences) {
            if (off.getDefendantId() != null) {
                defendants.computeIfAbsent(off.getDefendantId(),
                        id -> defendant(id, "John", "Smith"));
            }
        }
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(LocalDate.of(2026, 3, 11))
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(resultLines)
                .defendants(List.copyOf(defendants.values()))
                .offences(offences)
                .build();
    }

    private static DraftValidationRequest buildRequestWithExplicitDefendants(
            final List<ResultLineDto> resultLines,
            final List<OffenceDto> offences,
            final List<DefendantDto> defendants) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(LocalDate.of(2026, 3, 11))
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(resultLines)
                .defendants(defendants)
                .offences(offences)
                .build();
    }
}
