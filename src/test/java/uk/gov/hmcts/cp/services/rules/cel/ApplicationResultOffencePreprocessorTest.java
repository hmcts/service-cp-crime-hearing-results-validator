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
 * 010-application-result-offence-error).
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

    /** (a) A breaching result line yields exactly one context keyed by its own resultLineId. */
    @Test
    void breachingResultLine_shouldYieldOneContextKeyedByResultLineId() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "LAWD", "d1", "off1")),
                List.of(offence("off1", 1, "Theft")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).hasSize(1).containsKey("rl1");
        ApplicationResultBreachContext context = (ApplicationResultBreachContext) contexts.get("rl1");
        assertThat(context.offenceId()).isEqualTo("off1");
        assertThat(context.resultLabel()).isEqualTo("LAWD label");
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

    /** (d) Two different application-only codes on the same offence each get their own context. */
    @Test
    void twoDifferentApplicationOnlyCodesOnSameOffence_shouldYieldTwoContexts() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "LAWD", "d1", "off1"),
                        resultLine("rl2", "RFSD", "d1", "off1")),
                List.of(offence("off1", 1, "Theft")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).hasSize(2).containsKeys("rl1", "rl2");
        assertThat(((ApplicationResultBreachContext) contexts.get("rl1")).resultLabel())
                .isEqualTo("LAWD label");
        assertThat(((ApplicationResultBreachContext) contexts.get("rl2")).resultLabel())
                .isEqualTo("RFSD label");
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

        assertThat(((ApplicationResultBreachContext) contexts.get("rl1")).resultLabel())
                .isEqualTo("LAWD");
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

        assertThat(((ApplicationResultBreachContext) contexts.get("rl1")).resultLabel())
                .isEqualTo("LAWD");
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

        assertThat(((ApplicationResultBreachContext) contexts.get("rl1")).defendantName()).isEmpty();
    }

    /**
     * (h) A result line with a null {@code resultLineId} is tolerated (used as-is as the
     * context map's key -- {@code LinkedHashMap} permits a null key) rather than throwing, and
     * does not suppress an otherwise-valid breach elsewhere in the same request. The
     * {@code preprocess()} loop's {@code catch (RuntimeException e)} guard exists for whatever
     * future malformed-data shape genuinely does throw; this test documents that a null
     * {@code resultLineId} specifically is not such a case, since nothing in
     * {@code buildContextIfBreach} reads it.
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
        assertThat(contexts).containsKey("rl2");
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
     * yields two separate contexts -- the preprocessor performs no dedup of its own; collapsing
     * the defendant's name in the aggregated page-level message is the aggregation layer's job
     * (see DefaultValidationServiceTest / research.md R8), exercised end-to-end by the
     * integration test, not asserted here.
     */
    @Test
    void sameApplicationOnlyCodeAcrossTwoOffencesSameDefendant_shouldYieldTwoContexts() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "LAWD", "d1", "off1"),
                        resultLine("rl2", "LAWD", "d1", "off2")),
                List.of(offence("off1", 1, "Theft"), offence("off2", 2, "Assault")));

        Map<String, ? extends RuleEvaluationContext> contexts =
                preprocessor.preprocess(request, config());

        assertThat(contexts).hasSize(2).containsKeys("rl1", "rl2");
        assertThat(((ApplicationResultBreachContext) contexts.get("rl1")).offenceId()).isEqualTo("off1");
        assertThat(((ApplicationResultBreachContext) contexts.get("rl2")).offenceId()).isEqualTo("off2");
    }
}
