package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Unit tests for {@link ApplicationResultOffencePreprocessor} (feature
 * 010-application-result-offence-error). Covers the per-offence grouping (AC2A) and hearing-wide
 * label aggregation (AC2B) this preprocessor performs.
 */
class ApplicationResultOffencePreprocessorTest {

    private static final List<String> APPLICATION_ONLY_CODES = List.of(
            "ARBSFG", "ARBSPG", "ARBSR", "VT", "G", "LAREP", "LAREPCC", "ORDC", "LATG", "LATR",
            "LAWD", "RFSD", "SMAR");

    private final ApplicationResultOffencePreprocessor preprocessor =
            new ApplicationResultOffencePreprocessor();

    private static PreprocessingDefinition config() {
        return PreprocessingDefinition.builder()
                .applicationOnlyShortCodes(APPLICATION_ONLY_CODES)
                .build();
    }

    @Test
    void type_shouldReturnApplicationResultOffenceQualifier() {
        assertThat(preprocessor.type()).isEqualTo("application-result-offence");
    }

    /** (a) A breaching result line yields exactly one context keyed by its offenceId. */
    @Test
    void breachingResultLine_shouldYieldOneContextKeyedByOffenceId() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "LAWD", "d1", "off1")),
                List.of(offence("off1", 1, "Theft")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).hasSize(1).containsKey("off1");
        ApplicationResultBreachContext context = (ApplicationResultBreachContext) contexts.get("off1");
        assertThat(context.offenceId()).isEqualTo("off1");
        assertThat(context.resultLabels()).containsExactly("LAWD label");
        assertThat(context.globalResultLabels()).isEqualTo("LAWD label");
        assertThat(context.defendantId()).isEqualTo("d1");
        assertThat(context.defendantName()).isEqualTo("John Smith");
    }

    /** (b) A non-application-only short code yields no context. */
    @Test
    void nonApplicationOnlyShortCode_shouldYieldNoContext() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1")),
                List.of(offence("off1", 1, "Theft")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).isEmpty();
    }

    /** (c) An application-only short code with a null offenceId yields no context. */
    @Test
    void applicationOnlyShortCodeWithNullOffenceId_shouldYieldNoContext() {
        ResultLineDto line = ResultLineDto.builder()
                .resultLineId("rl1").shortCode("LAWD").label("Legal Aid Withdrawn")
                .defendantId("d1").offenceId(null).build();
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1").resultLines(List.of(line)).build();

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).isEmpty();
    }

    /** (c) ... and with a blank offenceId, likewise. */
    @Test
    void applicationOnlyShortCodeWithBlankOffenceId_shouldYieldNoContext() {
        ResultLineDto line = ResultLineDto.builder()
                .resultLineId("rl1").shortCode("LAWD").label("Legal Aid Withdrawn")
                .defendantId("d1").offenceId("   ").build();
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1").resultLines(List.of(line)).build();

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).isEmpty();
    }

    /**
     * (d) Two different application-only codes on the SAME offence consolidate into ONE context
     * whose {@code resultLabels} lists both, in encounter order (spec.md AC2A).
     */
    @Test
    void twoDifferentApplicationOnlyCodesOnSameOffence_shouldYieldOneConsolidatedContext() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "LAWD", "d1", "off1"),
                        resultLine("rl2", "RFSD", "d1", "off1")),
                List.of(offence("off1", 1, "Theft")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).hasSize(1).containsKey("off1");
        ApplicationResultBreachContext context = (ApplicationResultBreachContext) contexts.get("off1");
        assertThat(context.resultLabels()).containsExactly("LAWD label", "RFSD label");
        assertThat(context.getCalculatedValue("resultLabelByOffenceId", "off1"))
                .isEqualTo("LAWD label, RFSD label");
        assertThat(context.globalResultLabels()).isEqualTo("LAWD label, RFSD label");
    }

    /** (d) The same code repeated on the same offence is de-duplicated within that offence. */
    @Test
    void sameApplicationOnlyCodeTwiceOnSameOffence_shouldDeduplicateWithinOffence() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "LAWD", "d1", "off1"),
                        resultLine("rl2", "LAWD", "d1", "off1")),
                List.of(offence("off1", 1, "Theft")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).hasSize(1);
        ApplicationResultBreachContext context = (ApplicationResultBreachContext) contexts.get("off1");
        assertThat(context.resultLabels()).containsExactly("LAWD label");
    }

    /** (e) Short-code matching is case-insensitive. */
    @Test
    void shortCodeMatching_shouldBeCaseInsensitive() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "lawd", "d1", "off1")),
                List.of(offence("off1", 1, "Theft")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).hasSize(1);
    }

    /** (f) A blank/null label falls back to the short code itself, never null or empty. */
    @Test
    void blankLabel_shouldFallBackToShortCode() {
        ResultLineDto line = ResultLineDto.builder()
                .resultLineId("rl1").shortCode("LAWD").label("  ")
                .defendantId("d1").offenceId("off1").build();
        DraftValidationRequest request = buildRequest(List.of(line), List.of(offence("off1", 1, "Theft")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(((ApplicationResultBreachContext) contexts.get("off1")).resultLabels())
                .containsExactly("LAWD");
    }

    /** (f) ... and a null label likewise falls back to the short code. */
    @Test
    void nullLabel_shouldFallBackToShortCode() {
        ResultLineDto line = ResultLineDto.builder()
                .resultLineId("rl1").shortCode("LAWD").label(null)
                .defendantId("d1").offenceId("off1").build();
        DraftValidationRequest request = buildRequest(List.of(line), List.of(offence("off1", 1, "Theft")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(((ApplicationResultBreachContext) contexts.get("off1")).resultLabels())
                .containsExactly("LAWD");
    }

    /** (g) A defendantId with no matching DefendantDto resolves to "" rather than throwing. */
    @Test
    void unresolvableDefendant_shouldResolveToBlankNameRatherThanThrow() {
        ResultLineDto line = ResultLineDto.builder()
                .resultLineId("rl1").shortCode("LAWD").label("Legal Aid Withdrawn")
                .defendantId("d-unknown").offenceId("off1").build();
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1").resultLines(List.of(line)).offences(List.of(offence("off1", 1, "Theft")))
                .build();

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(((ApplicationResultBreachContext) contexts.get("off1")).defendantName()).isEmpty();
    }

    /**
     * (h) A result line with a null {@code resultLineId} is tolerated rather than throwing --
     * this preprocessor keys contexts by {@code offenceId}, not {@code resultLineId}, so it never
     * even reads that field -- and does not suppress an otherwise-valid breach elsewhere in the
     * same request.
     */
    @Test
    void nullResultLineId_shouldNotSuppressOtherBreachesInSameRequest() {
        ResultLineDto nullId = ResultLineDto.builder()
                .resultLineId(null).shortCode("LAWD").label("Legal Aid Withdrawn")
                .defendantId("d1").offenceId("off1").build();
        ResultLineDto valid = resultLine("rl2", "RFSD", "d1", "off2");
        DraftValidationRequest request = buildRequest(
                List.of(nullId, valid),
                List.of(offence("off1", 1, "Theft"), offence("off2", 2, "Assault")));

        assertThatCode(() -> preprocessor.preprocess(request, config())).doesNotThrowAnyException();

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());
        assertThat(contexts).containsKey("off2");
    }

    /** (i) No application-only short codes anywhere in the request yields an empty map. */
    @Test
    void noApplicationOnlyShortCodesPresent_shouldYieldEmptyMap() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"), resultLine("rl2", "FINE", "d1", "off2")),
                List.of(offence("off1", 1, "Theft"), offence("off2", 2, "Assault")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).isEmpty();
    }

    /**
     * (j) The same application-only code against two different offences for the same defendant
     * yields two separate per-offence contexts, but both share the SAME hearing-wide
     * {@code globalResultLabels} (a single, de-duplicated label), so the page-level message they
     * drive is identical text and merges into one entry (spec.md AC2B).
     */
    @Test
    void sameApplicationOnlyCodeAcrossTwoOffencesSameDefendant_shouldYieldTwoContextsSharingGlobalLabel() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "LAWD", "d1", "off1"),
                        resultLine("rl2", "LAWD", "d1", "off2")),
                List.of(offence("off1", 1, "Theft"), offence("off2", 2, "Assault")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).hasSize(2).containsKeys("off1", "off2");
        ApplicationResultBreachContext ctx1 = (ApplicationResultBreachContext) contexts.get("off1");
        ApplicationResultBreachContext ctx2 = (ApplicationResultBreachContext) contexts.get("off2");
        assertThat(ctx1.offenceId()).isEqualTo("off1");
        assertThat(ctx2.offenceId()).isEqualTo("off2");
        assertThat(ctx1.globalResultLabels()).isEqualTo("LAWD label");
        assertThat(ctx2.globalResultLabels()).isEqualTo("LAWD label");
    }

    /**
     * Three different breaching offences (spec.md AC2B's own example shape -- LAWD/off1,
     * RFSD/off2, G/off3) each get their own context, and every context's
     * {@code globalResultLabels} lists all three labels, comma-separated, in overall
     * result-line encounter order.
     */
    @Test
    void breachesAcrossThreeOffences_shouldShareGlobalLabelListInEncounterOrder() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "LAWD", "d1", "off1"),
                        resultLine("rl2", "RFSD", "d1", "off2"),
                        resultLine("rl3", "G", "d1", "off3")),
                List.of(offence("off1", 1, "Theft"), offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).hasSize(3);
        for (final ApplicationResultBreachContext context
                : contexts.values().stream().map(ApplicationResultBreachContext.class::cast).toList()) {
            assertThat(context.globalResultLabels())
                    .isEqualTo("LAWD label, RFSD label, G label");
        }
    }
}
