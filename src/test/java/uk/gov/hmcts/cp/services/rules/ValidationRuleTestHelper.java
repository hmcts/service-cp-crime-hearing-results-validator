package uk.gov.hmcts.cp.services.rules;

import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

import java.time.LocalDate;
import java.util.List;

public final class ValidationRuleTestHelper {

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
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(LocalDate.of(2026, 3, 11))
                .courtType(courtType)
                .resultLines(resultLines)
                .defendants(List.of())
                .offences(offences)
                .build();
    }
}
