package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Unit tests for {@link AgeRestrictedImprisonmentPreprocessor}.
 */
class AgeRestrictedImprisonmentPreprocessorTest {

    private static final LocalDate HEARING_DAY = LocalDate.of(2026, 7, 20);

    private final AgeRestrictedImprisonmentPreprocessor preprocessor =
            new AgeRestrictedImprisonmentPreprocessor();
    private final PreprocessingDefinition config = PreprocessingDefinition.builder()
            .type("age-restricted-imprisonment")
            .filterShortCodes(List.of("IMP", "EXTIVS", "SPECC", "SUSPS", "SUSPSNR"))
            .build();

    @Test
    void type_should_return_the_registry_qualifier() {
        assertThat(preprocessor.type()).isEqualTo("age-restricted-imprisonment");
    }

    @Test
    void under21_defendant_with_imp_result_should_yield_context_with_isUnder21_true() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1")),
                List.of(defendant("d1", LocalDate.of(2006, 8, 1))));

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);

        assertThat(result).containsKey("d1");
        AgeRestrictedResultContext ctx = result.get("d1");
        assertThat(ctx.isUnder21()).isTrue();
        assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1");
    }

    @Test
    void under21_defendant_with_susps_or_suspsnr_result_should_yield_context_with_isUnder21_true() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "SUSPS", "d1", "off1"),
                        resultLine("rl2", "SUSPSNR", "d1", "off2")),
                List.of(defendant("d1", LocalDate.of(2006, 8, 1))));

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);

        assertThat(result).containsKey("d1");
        AgeRestrictedResultContext ctx = result.get("d1");
        assertThat(ctx.isUnder21()).isTrue();
        assertThat(ctx.qualifyingOffenceIds()).containsExactly("off1", "off2");
    }

    @Test
    void defendant_whose_21st_birthday_is_exactly_the_hearing_day_should_not_be_under21() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1")),
                List.of(defendant("d1", HEARING_DAY.minusYears(21))));

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);

        assertThat(result.get("d1").isUnder21()).isFalse();
    }

    @Test
    void defendant_with_no_qualifying_result_should_yield_no_context_entry() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "FINE", "d1", "off1")),
                List.of(defendant("d1", LocalDate.of(2006, 8, 1))));

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);

        assertThat(result).isEmpty();
    }

    @Test
    void defendants_sharing_masterDefendantId_should_be_grouped_together() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "EXTIVS", "d2", "off2")),
                List.of(defendant("d1", "master-1", LocalDate.of(2006, 8, 1)),
                        defendant("d2", "master-1", LocalDate.of(2006, 8, 1))));

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);

        assertThat(result).hasSize(1);
        assertThat(result).containsKey("master-1");
        assertThat(result.get("master-1").qualifyingOffenceIds()).containsExactly("off1", "off2");
    }

    @Test
    void null_dateOfBirth_should_yield_isUnder21_false_and_not_throw() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1")),
                List.of(defendant("d1", null)));

        assertThatCode(() -> preprocessor.preprocess(request, config)).doesNotThrowAnyException();

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);
        assertThat(result.get("d1").isUnder21()).isFalse();
    }

    @Test
    void null_hearingDay_should_yield_isUnder21_false_and_not_throw() {
        DraftValidationRequest request = DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(null)
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(List.of(resultLine("rl1", "IMP", "d1", "off1")))
                .defendants(List.of(defendant("d1", LocalDate.of(2006, 8, 1))))
                .offences(List.of())
                .build();

        assertThatCode(() -> preprocessor.preprocess(request, config)).doesNotThrowAnyException();

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);
        assertThat(result.get("d1").isUnder21()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"imp", "Extivs", "SPECC", "extivs", "specc", "susps", "Suspsnr", "SUSPSNR"})
    void short_code_matching_should_be_case_insensitive(final String code) {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", code, "d1", "off1")),
                List.of(defendant("d1", LocalDate.of(2006, 8, 1))));

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);

        assertThat(result).as("short code %s should be treated as imprisonment-type", code).containsKey("d1");
    }

    @Test
    void one_defendant_with_null_dob_should_not_suppress_or_corrupt_another_defendants_context() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d2", "off2")),
                List.of(defendant("d1", null),
                        defendant("d2", LocalDate.of(2006, 8, 1))));

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);

        assertThat(result).hasSize(2);
        assertThat(result.get("d1").isUnder21()).isFalse();
        assertThat(result.get("d2").isUnder21()).isTrue();
        assertThat(result.get("d2").qualifyingOffenceIds()).containsExactly("off2");
    }

    @Test
    void should_build_name_from_last_name_only_when_first_name_null() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1")),
                List.of(DefendantDto.builder()
                        .defendantId("d1").firstName(null).lastName("Smith")
                        .dateOfBirth(LocalDate.of(2006, 8, 1)).build()));

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);

        assertThat(result.get("d1").defendantName()).isEqualTo("Smith");
    }

    @Test
    void should_build_name_from_first_name_only_when_last_name_null() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1")),
                List.of(DefendantDto.builder()
                        .defendantId("d1").firstName("Jamie").lastName(null)
                        .dateOfBirth(LocalDate.of(2006, 8, 1)).build()));

        Map<String, AgeRestrictedResultContext> result = preprocessor.preprocess(request, config);

        assertThat(result.get("d1").defendantName()).isEqualTo("Jamie");
    }

    private static DefendantDto defendant(String id, LocalDate dateOfBirth) {
        return defendant(id, null, dateOfBirth);
    }

    private static DefendantDto defendant(String id, String masterDefendantId, LocalDate dateOfBirth) {
        return DefendantDto.builder()
                .defendantId(id)
                .firstName("Jamie")
                .lastName("Smith")
                .masterDefendantId(masterDefendantId)
                .dateOfBirth(dateOfBirth)
                .build();
    }

    private static ResultLineDto resultLine(String id, String shortCode, String defendantId, String offenceId) {
        return ResultLineDto.builder()
                .resultLineId(id)
                .shortCode(shortCode)
                .label(shortCode + " label")
                .defendantId(defendantId)
                .offenceId(offenceId)
                .build();
    }

    private static DraftValidationRequest buildRequest(List<ResultLineDto> resultLines, List<DefendantDto> defendants) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(HEARING_DAY)
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(resultLines)
                .defendants(defendants)
                .offences(List.<OffenceDto>of())
                .build();
    }
}
