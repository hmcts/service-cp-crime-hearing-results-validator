package uk.gov.hmcts.cp.services.rules;

import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared test-data builders for validation rule unit and scenario tests.
 */
public final class ValidationRuleTestHelper {

    public static final String DEFAULT_DEFENDANT_FIRST_NAME = "John";
    public static final String DEFAULT_DEFENDANT_LAST_NAME = "Smith";

    private ValidationRuleTestHelper() {
    }

    public static ResultLineDto resultLine(String id, String shortCode,
                                            String defendantId, String offenceId) {
        return ResultLineDto.builder()
                .id(id)
                .shortCode(shortCode)
                .label(shortCode + " label")
                .defendantId(defendantId)
                .offenceId(offenceId)
                .build();
    }

    public static OffenceDto offence(String id, int countNumber, String title) {
        return OffenceDto.builder()
                .id(id)
                .offenceCode("TH68001")
                .offenceTitle(title)
                .orderIndex(countNumber)
                .caseUrn("32AH9105826")
                .build();
    }

    public static OffenceDto offence(String id, int countNumber, String title, String caseUrn) {
        return OffenceDto.builder()
                .id(id)
                .offenceCode("TH68001")
                .offenceTitle(title)
                .orderIndex(countNumber)
                .caseUrn(caseUrn)
                .build();
    }

    public static OffenceDto offenceWithCode(String id, int countNumber, String title,
                                              String offenceCode) {
        return OffenceDto.builder()
                .id(id)
                .offenceCode(offenceCode)
                .offenceTitle(title)
                .orderIndex(countNumber)
                .caseUrn("32AH9105826")
                .build();
    }

    public static OffenceDto offenceWithCtlFlags(String id, int countNumber, String title,
                                                  boolean hasExistingCtlRecord,
                                                  boolean isConvicted) {
        return OffenceDto.builder()
                .id(id)
                .offenceCode("TH68001")
                .offenceTitle(title)
                .orderIndex(countNumber)
                .caseUrn("32AH9105826")
                .hasExistingCtlRecord(hasExistingCtlRecord)
                .isConvicted(isConvicted)
                .build();
    }

    public static DefendantDto defendant(String id, String firstName, String lastName) {
        return DefendantDto.builder()
                .id(id)
                .firstName(firstName)
                .lastName(lastName)
                .build();
    }

    public static DraftValidationRequest buildRequest(List<ResultLineDto> resultLines,
                                                       List<OffenceDto> offences) {
        return buildRequest(resultLines, offences,
                DraftValidationRequest.CourtTypeEnum.MAGISTRATES);
    }

    public static DraftValidationRequest buildRequest(List<ResultLineDto> resultLines,
                                                       List<OffenceDto> offences,
                                                       DraftValidationRequest.CourtTypeEnum courtType) {
        Map<String, DefendantDto> defendants = new LinkedHashMap<>();
        for (ResultLineDto rl : resultLines) {
            defendants.computeIfAbsent(rl.getDefendantId(),
                    id -> defendant(id, DEFAULT_DEFENDANT_FIRST_NAME, DEFAULT_DEFENDANT_LAST_NAME));
        }

        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(LocalDate.of(2026, 3, 11))
                .courtType(courtType)
                .resultLines(resultLines)
                .defendants(List.copyOf(defendants.values()))
                .offences(offences)
                .build();
    }
}
