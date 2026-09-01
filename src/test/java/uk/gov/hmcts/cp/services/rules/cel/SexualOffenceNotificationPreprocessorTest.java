package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.services.referencedata.ReferencedataOffenceClient;

/**
 * Unit tests for {@link SexualOffenceNotificationPreprocessor} (DR-SEX-008). {@link
 * ReferencedataOffenceClient} is mocked — its own HTTP/fail-open behaviour is covered by
 * {@code ReferencedataOffenceClientTest}.
 */
@ExtendWith(MockitoExtension.class)
class SexualOffenceNotificationPreprocessorTest {

    private static final LocalDate HEARING_DAY = LocalDate.of(2026, 8, 25);
    private static final String QUALIFYING_MIS_CODE = "SEX";

    @Mock
    private ReferencedataOffenceClient referencedataOffenceClient;

    private SexualOffenceNotificationPreprocessor preprocessor;
    private PreprocessingDefinition config;

    @BeforeEach
    void setUp() {
        preprocessor = new SexualOffenceNotificationPreprocessor(referencedataOffenceClient);
        config = PreprocessingDefinition.builder()
                .type("sexual-offence-notification-requirement")
                .qualifyingMisCode(QUALIFYING_MIS_CODE)
                .adultNotificationShortCodes(List.of("NORRR"))
                .youthNotificationShortCodes(List.of("NORRR", "NORPGP"))
                .build();
    }

    @Nested
    @DisplayName("Adult scenarios (US1)")
    class AdultScenarios {

        @Test
        void convictedSexOffenceMissingNorrr_adultDefendant_shouldYieldContextWithNoQualifyingNotification() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("SEX"));
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(defendant("d1", LocalDate.of(2000, 1, 1))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result).containsOnlyKeys("off1");
            SexualOffenceNotificationContext context = result.get("off1");
            assertThat(context.isYouth()).isFalse();
            assertThat(context.hasQualifyingNotification()).isFalse();
            assertThat(context.defendantName()).isEqualTo("Jamie Smith");
        }

        @Test
        void convictedSexOffenceWithNorrr_anyCasing_shouldYieldContextWithQualifyingNotification() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("SEX"));
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "norrr", "d1", "off1")),
                    List.of(defendant("d1", LocalDate.of(2000, 1, 1))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result.get("off1").hasQualifyingNotification()).isTrue();
        }

        @Test
        void offenceNotConvicted_shouldYieldNoContextEntry() {
            DraftValidationRequest request = buildRequest(
                    offence("off1", false),
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(defendant("d1", LocalDate.of(2000, 1, 1))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        void offenceMisCodeNotSex_shouldYieldNoContextEntry() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("MOT"));
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(defendant("d1", LocalDate.of(2000, 1, 1))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        void referencedataLookupFailsOpen_shouldYieldNoContextEntry() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.empty());
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(defendant("d1", LocalDate.of(2000, 1, 1))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        void dateOfBirthNull_shouldDefaultToAdult() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("SEX"));
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(defendant("d1", null)));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result.get("off1").isYouth()).isFalse();
        }

        @Test
        void doesNotCallReferencedataClient_whenOffenceNotConvicted() {
            DraftValidationRequest request = buildRequest(
                    offence("off1", false),
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(defendant("d1", LocalDate.of(2000, 1, 1))));

            preprocessor.preprocess(request, config);

            org.mockito.Mockito.verify(referencedataOffenceClient, org.mockito.Mockito.never())
                    .lookupMisCode(any());
        }
    }

    @Nested
    @DisplayName("Youth scenarios (US2)")
    class YouthScenarios {

        @Test
        void defendantUnder18_shouldBeClassifiedAsYouth() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("SEX"));
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(defendant("d1", HEARING_DAY.minusYears(17))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result.get("off1").isYouth()).isTrue();
        }

        @Test
        void defendantExactly18OnHearingDay_shouldBeClassifiedAsAdult() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("SEX"));
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(defendant("d1", HEARING_DAY.minusYears(18))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result.get("off1").isYouth()).isFalse();
        }

        @Test
        void youthWithNeitherCode_shouldYieldNoQualifyingNotification() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("SEX"));
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(defendant("d1", HEARING_DAY.minusYears(15))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result.get("off1").hasQualifyingNotification()).isFalse();
        }

        @Test
        void youthWithNorrrOnly_shouldYieldQualifyingNotification() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("SEX"));
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "NORRR", "d1", "off1")),
                    List.of(defendant("d1", HEARING_DAY.minusYears(15))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result.get("off1").hasQualifyingNotification()).isTrue();
        }

        @Test
        void youthWithNorpgpOnly_anyCasing_shouldYieldQualifyingNotification() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("SEX"));
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "norpgp", "d1", "off1")),
                    List.of(defendant("d1", HEARING_DAY.minusYears(15))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result.get("off1").hasQualifyingNotification()).isTrue();
        }

        @Test
        void youthWithBothCodes_shouldYieldQualifyingNotification() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("SEX"));
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "NORRR", "d1", "off1"),
                            resultLine("rl2", "NORPGP", "d1", "off1")),
                    List.of(defendant("d1", HEARING_DAY.minusYears(15))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result.get("off1").hasQualifyingNotification()).isTrue();
        }
    }

    @Nested
    @DisplayName("Defendant resolution edge cases")
    class DefendantResolution {

        @Test
        void offenceWithNoResultLines_shouldYieldNoContextEntry() {
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(),
                    List.of(defendant("d1", LocalDate.of(2000, 1, 1))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        void jointOffence_multipleDistinctDefendantIds_shouldYieldNoContextEntry() {
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(resultLine("rl1", "IMP", "d1", "off1"),
                            resultLine("rl2", "IMP", "d2", "off1")),
                    List.of(defendant("d1", LocalDate.of(2000, 1, 1)),
                            defendant("d2", LocalDate.of(2000, 1, 1))));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result).isEmpty();
        }

        @Test
        void doesNotCallReferencedataClient_whenDefendantCannotBeResolved() {
            DraftValidationRequest request = buildRequest(
                    offence("off1", true),
                    List.of(),
                    List.of(defendant("d1", LocalDate.of(2000, 1, 1))));

            preprocessor.preprocess(request, config);

            org.mockito.Mockito.verify(referencedataOffenceClient, org.mockito.Mockito.never())
                    .lookupMisCode(any());
        }
    }

    @Nested
    @DisplayName("Multi-offence, multi-defendant isolation (SC-005)")
    class Isolation {

        @Test
        void multipleOffencesDifferentDefendantsAndAges_shouldEvaluateEachIndependently() {
            when(referencedataOffenceClient.lookupMisCode("off1")).thenReturn(Optional.of("SEX"));
            when(referencedataOffenceClient.lookupMisCode("off2")).thenReturn(Optional.of("SEX"));
            DraftValidationRequest request = new DraftValidationRequest(
                    "h1", "case1", HEARING_DAY, DraftValidationRequest.CourtTypeEnum.MAGISTRATES,
                    List.of(resultLine("rl1", "IMP", "d1", "off1"),
                            resultLine("rl2", "IMP", "d2", "off2")),
                    List.of(defendant("d1", HEARING_DAY.minusYears(30)),
                            defendant("d2", HEARING_DAY.minusYears(15))),
                    List.of(offence("off1", true), offence("off2", true)));

            Map<String, SexualOffenceNotificationContext> result = preprocessor.preprocess(request, config);

            assertThat(result).containsOnlyKeys("off1", "off2");
            assertThat(result.get("off1").isYouth()).isFalse();
            assertThat(result.get("off2").isYouth()).isTrue();
        }
    }

    private static OffenceDto offence(final String offenceId, final boolean convicted) {
        return OffenceDto.builder()
                .offenceId(offenceId)
                .offenceCode("SX03007C")
                .offenceTitle("Sexual offence")
                .isConvicted(convicted)
                .build();
    }

    private static DefendantDto defendant(final String id, final LocalDate dateOfBirth) {
        return DefendantDto.builder()
                .defendantId(id)
                .firstName("Jamie")
                .lastName("Smith")
                .dateOfBirth(dateOfBirth)
                .build();
    }

    private static ResultLineDto resultLine(final String id, final String shortCode,
                                            final String defendantId, final String offenceId) {
        return ResultLineDto.builder()
                .resultLineId(id)
                .shortCode(shortCode)
                .label(shortCode + " label")
                .defendantId(defendantId)
                .offenceId(offenceId)
                .build();
    }

    private static DraftValidationRequest buildRequest(final OffenceDto offence,
                                                        final List<ResultLineDto> resultLines,
                                                        final List<DefendantDto> defendants) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(HEARING_DAY)
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(resultLines)
                .defendants(defendants)
                .offences(List.of(offence))
                .build();
    }
}
