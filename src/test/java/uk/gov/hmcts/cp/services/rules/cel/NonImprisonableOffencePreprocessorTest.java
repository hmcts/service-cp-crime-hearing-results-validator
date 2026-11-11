package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClient;

@ExtendWith(MockitoExtension.class)
class NonImprisonableOffencePreprocessorTest {

    @Mock
    ReferencedataOffenceClient referencedataOffenceClient;

    private NonImprisonableOffencePreprocessor preprocessor;

    private final PreprocessingDefinition config = PreprocessingDefinition.builder()
            .type(NonImprisonableOffencePreprocessor.QUALIFIER)
            .filterShortCodes(List.of(
                    "IMP", "YOI", "DTO", "EXTDVS", "EXTDVSU", "EXTIVS", "STSDY",
                    "SPECC", "SPECCC", "SPECCD", "SUSPS", "SUSPSS", "SUSPSNI", "SUSPSNR",
                    "SUSPSD", "SUSPSDS", "SUSPSDNI", "SUSPSDNR"))
            .build();

    @BeforeEach
    void setUp() {
        preprocessor = new NonImprisonableOffencePreprocessor(referencedataOffenceClient);
    }

    private Map<String, NonImprisonableOffenceContext> preprocess(DraftValidationRequest request) {
        return preprocessor.preprocess(request, config);
    }

    @Test
    void type_should_return_registry_qualifier() {
        assertThat(preprocessor.type()).isEqualTo("non-imprisonable-offence");
    }

    @Nested
    @DisplayName("Client return value → nonImprisonableCount mapping")
    class IndicatorMapping {

        @Test
        void clientReturnsN_should_yieldNonImprisonableCount_one() {
            when(referencedataOffenceClient.getCustodialIndicator("RT88026"))
                    .thenReturn(Optional.of("N"));
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88026")));

            NonImprisonableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.nonImprisonableCount()).isEqualTo(1L);
        }

        @Test
        void clientReturnsY_should_yieldNonImprisonableCount_zero() {
            when(referencedataOffenceClient.getCustodialIndicator("RT88026"))
                    .thenReturn(Optional.of("Y"));
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88026")));

            NonImprisonableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.nonImprisonableCount()).isEqualTo(0L);
        }

        @Test
        void clientReturnsEmpty_should_yieldNonImprisonableCount_zero() {
            when(referencedataOffenceClient.getCustodialIndicator("RT88026"))
                    .thenReturn(Optional.empty());
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88026")));

            NonImprisonableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.nonImprisonableCount()).isEqualTo(0L);
        }

        @Test
        void multipleCustodialResultLines_sameOffence_clientN_should_callClientOnce_yieldOne() {
            when(referencedataOffenceClient.getCustodialIndicator("RT88026"))
                    .thenReturn(Optional.of("N"));
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off1"),
                            resultLine("rl2", "YOI", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88026")));

            Map<String, NonImprisonableOffenceContext> result = preprocess(request);

            assertThat(result).hasSize(1);
            assertThat(result.get("off1").nonImprisonableCount()).isEqualTo(1L);
            verify(referencedataOffenceClient).getCustodialIndicator("RT88026");
        }
    }

    @Nested
    @DisplayName("Non-custodial results")
    class NonCustodialResults {

        @Test
        void nonCustodialResult_should_returnEmptyMap_and_notCallClient() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "COEW", "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88026")));

            Map<String, NonImprisonableOffenceContext> result = preprocess(request);

            assertThat(result).isEmpty();
            verifyNoInteractions(referencedataOffenceClient);
        }
    }

    @Nested
    @DisplayName("Breadth coverage — all 18 custodial short codes")
    class BreadthCoverage {

        @ParameterizedTest(name = "code ''{0}'' + client returns N → nonImprisonableCount=1")
        @ValueSource(strings = {
            "IMP", "YOI", "DTO", "EXTDVS", "EXTDVSU", "EXTIVS", "STSDY",
            "SPECC", "SPECCC", "SPECCD", "SUSPS", "SUSPSS", "SUSPSNI", "SUSPSNR",
            "SUSPSD", "SUSPSDS", "SUSPSDNI", "SUSPSDNR"
        })
        void custodialShortCode_clientReturnsN_should_yieldNonImprisonableCount_one(
                String shortCode) {
            when(referencedataOffenceClient.getCustodialIndicator("RT88026"))
                    .thenReturn(Optional.of("N"));
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", shortCode, "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88026")));

            NonImprisonableOffenceContext ctx = preprocess(request).get("off1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.nonImprisonableCount()).isEqualTo(1L);
        }

        @ParameterizedTest(name = "non-custodial code ''{0}'' should not call client")
        @ValueSource(strings = {"COEW", "YROEW", "FO", "DDOTE", "FINE", "wdrn", "ADA"})
        void nonCustodialShortCode_should_returnEmptyMap_and_notCallClient(String shortCode) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", shortCode, "d1", "off1")),
                    List.of(offenceWithCode("off1", 1, "Speeding", "RT88026")));

            Map<String, NonImprisonableOffenceContext> result = preprocess(request);

            assertThat(result).isEmpty();
            verifyNoInteractions(referencedataOffenceClient);
        }
    }
}
