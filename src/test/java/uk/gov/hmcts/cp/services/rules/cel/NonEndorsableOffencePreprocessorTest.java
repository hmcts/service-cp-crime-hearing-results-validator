package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClient;

/**
 * Unit tests for {@link NonEndorsableOffencePreprocessor} (DR-END-014). {@link
 * ReferencedataOffenceClient} is mocked — its own HTTP/fail-open behaviour is covered by
 * {@code ReferencedataOffenceClientTest}.
 */
@ExtendWith(MockitoExtension.class)
class NonEndorsableOffencePreprocessorTest {

    private static final List<String> ALL_TRIGGER_DISQ_CODES = List.of(
            "DDO", "DDOL", "DDOR", "DDOTE", "DDOTEL",
            "DDD", "DDDL", "DDDT", "DDDTL", "DDDTO",
            "DDP", "DDPL", "DDPR", "DDPTE", "DDPTEL",
            "DDRCOT");

    @Mock
    private ReferencedataOffenceClient referencedataOffenceClient;

    private NonEndorsableOffencePreprocessor preprocessor;
    private PreprocessingDefinition config;

    @BeforeEach
    void setUp() {
        preprocessor = new NonEndorsableOffencePreprocessor(referencedataOffenceClient);
        config = PreprocessingDefinition.builder()
                .type("non-endorsable-offence")
                .endorsementShortCodes(List.of("LEP", "LEN", "LEA"))
                .disqualificationTriggerShortCodes(ALL_TRIGGER_DISQ_CODES)
                .build();
    }

    @Test
    void type_should_return_the_registry_qualifier() {
        assertThat(preprocessor.type()).isEqualTo(NonEndorsableOffencePreprocessor.QUALIFIER);
    }

    @Nested
    @DisplayName("Endorsement trigger (AC1)")
    class EndorsementTrigger {

        @Test
        void lepOnNonEndorsableOffence_shouldYieldContextWithEndorsementCount() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88026")).thenReturn(Optional.of(false));
            DraftValidationRequest request = request(offence("off1", "RT88026"),
                    List.of(resultLine("rl1", "LEP", "off1")));

            Map<String, NonEndorsableOffenceContext> result = preprocessor.preprocess(request, config);

            assertThat(result).containsOnlyKeys("off1");
            assertThat(result.get("off1").endorsementCount()).isEqualTo(1L);
            assertThat(result.get("off1").disqualificationCount()).isEqualTo(0L);
        }

        @Test
        void lenOnNonEndorsableOffence_shouldYieldContext() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88026")).thenReturn(Optional.of(false));
            DraftValidationRequest request = request(offence("off1", "RT88026"),
                    List.of(resultLine("rl1", "LEN", "off1")));

            assertThat(preprocessor.preprocess(request, config)).containsOnlyKeys("off1");
        }

        @Test
        void leaOnNonEndorsableOffence_shouldYieldContext() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88026")).thenReturn(Optional.of(false));
            DraftValidationRequest request = request(offence("off1", "RT88026"),
                    List.of(resultLine("rl1", "LEA", "off1")));

            assertThat(preprocessor.preprocess(request, config)).containsOnlyKeys("off1");
        }

        @Test
        void lepOnEndorsableOffence_shouldYieldNoContext() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88026")).thenReturn(Optional.of(true));
            DraftValidationRequest request = request(offence("off1", "RT88026"),
                    List.of(resultLine("rl1", "LEP", "off1")));

            assertThat(preprocessor.preprocess(request, config)).isEmpty();
        }

        @Test
        void absentEndorsableFlag_failOpen_shouldYieldContext() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88026")).thenReturn(Optional.empty());
            DraftValidationRequest request = request(offence("off1", "RT88026"),
                    List.of(resultLine("rl1", "LEP", "off1")));

            Map<String, NonEndorsableOffenceContext> result = preprocessor.preprocess(request, config);

            assertThat(result).containsOnlyKeys("off1");
            assertThat(result.get("off1").endorsementCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Disqualification trigger (AC2)")
    class DisqualificationTrigger {

        @Test
        void ddoOnNonEndorsableOffence_shouldYieldContextWithDisqualificationCount() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88026")).thenReturn(Optional.of(false));
            DraftValidationRequest request = request(offence("off1", "RT88026"),
                    List.of(resultLine("rl1", "DDO", "off1")));

            Map<String, NonEndorsableOffenceContext> result = preprocessor.preprocess(request, config);

            assertThat(result).containsOnlyKeys("off1");
            assertThat(result.get("off1").disqualificationCount()).isEqualTo(1L);
            assertThat(result.get("off1").endorsementCount()).isEqualTo(0L);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "DDO", "DDOL", "DDOR", "DDOTE", "DDOTEL",
            "DDD", "DDDL", "DDDT", "DDDTL", "DDDTO",
            "DDP", "DDPL", "DDPR", "DDPTE", "DDPTEL",
            "DDRCOT"})
        void allTriggerCodes_onNonEndorsableOffence_shouldYieldDisqualificationContext(
                final String shortCode) {
            when(referencedataOffenceClient.getEndorsableFlag("RT88026")).thenReturn(Optional.of(false));
            final DraftValidationRequest request = request(offence("off1", "RT88026"),
                    List.of(resultLine("rl1", shortCode, "off1")));

            assertThat(preprocessor.preprocess(request, config)).containsOnlyKeys("off1");
        }
    }

    @Nested
    @DisplayName("Non-endorsable disqualification family clear (AC3)")
    class NonEndorsableDisqualificationClear {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"DDRN", "DDRNL", "DDRA", "DDRAL", "DDRV", "DDRVL"})
        void allNonEndorsableDisqCodes_shouldYieldNoContextAndSkipRefdataCall(
                final String shortCode) {
            final DraftValidationRequest request = request(offence("off1", "RT88026"),
                    List.of(resultLine("rl1", shortCode, "off1")));

            final Map<String, NonEndorsableOffenceContext> result =
                    preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
            verify(referencedataOffenceClient, never()).getEndorsableFlag(anyString());
        }
    }

    @Nested
    @DisplayName("Both families on same offence (AC1c)")
    class BothFamiliesOnSameOffence {

        @Test
        void lepAndDdoOnNonEndorsableOffence_shouldYieldBothCounts_andCallRefDataOnce() {
            when(referencedataOffenceClient.getEndorsableFlag("RT88026")).thenReturn(Optional.of(false));
            DraftValidationRequest request = request(offence("off1", "RT88026"),
                    List.of(resultLine("rl1", "LEP", "off1"), resultLine("rl2", "DDO", "off1")));

            Map<String, NonEndorsableOffenceContext> result = preprocessor.preprocess(request, config);

            assertThat(result).containsOnlyKeys("off1");
            assertThat(result.get("off1").endorsementCount()).isEqualTo(1L);
            assertThat(result.get("off1").disqualificationCount()).isEqualTo(1L);
            verify(referencedataOffenceClient, times(1)).getEndorsableFlag("RT88026");
        }
    }

    @Nested
    @DisplayName("No trigger code")
    class NoTriggerCode {

        @Test
        void impOnly_shouldYieldNoContextAndSkipRefdataCall() {
            DraftValidationRequest request = request(offence("off1", "RT88026"),
                    List.of(resultLine("rl1", "IMP", "off1")));

            Map<String, NonEndorsableOffenceContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
            verify(referencedataOffenceClient, never()).getEndorsableFlag(anyString());
        }

        @Test
        void noResultLines_shouldYieldNoContext() {
            DraftValidationRequest request = request(offence("off1", "RT88026"), List.of());

            Map<String, NonEndorsableOffenceContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
            verify(referencedataOffenceClient, never()).getEndorsableFlag(anyString());
        }

        @Test
        void nullOffences_shouldYieldNoContextAndSkipRefdataCall() {
            DraftValidationRequest request = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                    .resultLines(List.of(resultLine("rl1", "LEP", "off1")))
                    .defendants(List.of())
                    .offences(null)
                    .build();

            Map<String, NonEndorsableOffenceContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
            verify(referencedataOffenceClient, never()).getEndorsableFlag(anyString());
        }
    }

    @Nested
    @DisplayName("Multi-offence independence")
    class MultiOffenceIndependence {

        @Test
        void threeOffences_onlyNonEndorsableWithTrigger_shouldYieldSingleContext() {
            when(referencedataOffenceClient.getEndorsableFlag("NON-END")).thenReturn(Optional.of(false));
            when(referencedataOffenceClient.getEndorsableFlag("END")).thenReturn(Optional.of(true));

            DraftValidationRequest request = new DraftValidationRequest(
                    "h1", "case1", null, DraftValidationRequest.CourtTypeEnum.MAGISTRATES,
                    List.of(resultLine("rl1", "LEP", "off-non-end"),
                            resultLine("rl2", "LEP", "off-end"),
                            resultLine("rl3", "IMP", "off-no-trigger")),
                    List.of(),
                    List.of(offence("off-non-end", "NON-END"),
                            offence("off-end", "END"),
                            offence("off-no-trigger", "NON-END")));

            Map<String, NonEndorsableOffenceContext> result = preprocessor.preprocess(request, config);

            assertThat(result).containsOnlyKeys("off-non-end");
        }
    }

    @Nested
    @DisplayName("Context method contracts")
    class ContextMethodContracts {

        @Test
        void context_toCelContext_shouldExposeEndorsementAndDisqualificationCounts() {
            NonEndorsableOffenceContext context = new NonEndorsableOffenceContext("off1", 1L, 0L);

            assertThat(context.toCelContext()).containsEntry("endorsementCount", 1L);
            assertThat(context.toCelContext()).containsEntry("disqualificationCount", 0L);
        }

        @Test
        void context_getOffenceIdSet_endorsementSet_whenEndorsementCountPositive_shouldReturnOffenceId() {
            NonEndorsableOffenceContext context = new NonEndorsableOffenceContext("off1", 1L, 0L);

            assertThat(context.getOffenceIdSet("nonEndorsableEndorsementOffenceIds")).containsExactly("off1");
        }

        @Test
        void context_getOffenceIdSet_endorsementSet_whenEndorsementCountZero_shouldReturnEmpty() {
            NonEndorsableOffenceContext context = new NonEndorsableOffenceContext("off1", 0L, 1L);

            assertThat(context.getOffenceIdSet("nonEndorsableEndorsementOffenceIds")).isEmpty();
        }

        @Test
        void context_getOffenceIdSet_disqualificationSet_whenDisqualificationCountPositive_shouldReturnOffenceId() {
            NonEndorsableOffenceContext context = new NonEndorsableOffenceContext("off1", 0L, 1L);

            assertThat(context.getOffenceIdSet("nonEndorsableDisqualificationOffenceIds")).containsExactly("off1");
        }

        @Test
        void context_getOffenceIdSet_disqualificationSet_whenDisqualificationCountZero_shouldReturnEmpty() {
            NonEndorsableOffenceContext context = new NonEndorsableOffenceContext("off1", 1L, 0L);

            assertThat(context.getOffenceIdSet("nonEndorsableDisqualificationOffenceIds")).isEmpty();
        }

        @Test
        void context_getOffenceIdSet_unknownSetName_shouldThrowIllegalArgumentException() {
            final NonEndorsableOffenceContext context = new NonEndorsableOffenceContext("off1", 1L, 1L);

            assertThatThrownBy(() -> context.getOffenceIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }

        @Test
        void context_defendantName_shouldReturnNull() {
            NonEndorsableOffenceContext context = new NonEndorsableOffenceContext("off1", 1L, 0L);

            assertThat(context.defendantName()).isNull();
        }

        @Test
        void context_allOffenceIds_shouldReturnOffenceId() {
            NonEndorsableOffenceContext context = new NonEndorsableOffenceContext("off1", 1L, 0L);

            assertThat(context.allOffenceIds()).containsExactly("off1");
        }
    }

    private static OffenceDto offence(final String offenceId, final String offenceCode) {
        return OffenceDto.builder()
                .offenceId(offenceId)
                .offenceCode(offenceCode)
                .offenceTitle("Test offence")
                .build();
    }

    private static ResultLineDto resultLine(final String id, final String shortCode, final String offenceId) {
        return ResultLineDto.builder()
                .resultLineId(id)
                .shortCode(shortCode)
                .label(shortCode + " label")
                .offenceId(offenceId)
                .build();
    }

    private static DraftValidationRequest request(final OffenceDto offence,
                                                   final List<ResultLineDto> resultLines) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(resultLines)
                .defendants(List.of())
                .offences(List.of(offence))
                .build();
    }
}
