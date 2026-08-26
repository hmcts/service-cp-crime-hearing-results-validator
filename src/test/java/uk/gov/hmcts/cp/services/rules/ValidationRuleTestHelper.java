package uk.gov.hmcts.cp.services.rules;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.Prompt;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Shared test-data builders for validation rule unit and scenario tests.
 */
public final class ValidationRuleTestHelper {

    public static final String DEFAULT_DEFENDANT_FIRST_NAME = "John";
    public static final String DEFAULT_DEFENDANT_LAST_NAME = "Smith";

    private ValidationRuleTestHelper() {
    }

    /** Builds a minimal result line with the given short code, defendant, and offence linkage. */
    public static ResultLineDto resultLine(String id, String shortCode,
                                            String defendantId, String offenceId) {
        return ResultLineDto.builder()
                .resultLineId(id)
                .shortCode(shortCode)
                .label(shortCode + " label")
                .defendantId(defendantId)
                .offenceId(offenceId)
                .build();
    }

    /** As {@link #resultLine}, with a single prompt attached. */
    public static ResultLineDto resultLineWithPrompt(String id, String shortCode,
                                                       String defendantId, String offenceId,
                                                       String promptRef, String promptValue) {
        ResultLineDto resultLine = resultLine(id, shortCode, defendantId, offenceId);
        resultLine.setPrompts(List.of(new Prompt(promptRef, promptValue)));
        return resultLine;
    }

    /** Builds an offence with a fixed default offence code ({@code TH68001}) and case URN. */
    public static OffenceDto offence(String id, int countNumber, String title) {
        return OffenceDto.builder()
                .offenceId(id)
                .offenceCode("TH68001")
                .offenceTitle(title)
                .orderIndex(countNumber)
                .caseUrn("32AH9105826")
                .build();
    }

    /** As {@link #offence(String, int, String)}, with an explicit case URN. */
    public static OffenceDto offence(String id, int countNumber, String title, String caseUrn) {
        return OffenceDto.builder()
                .offenceId(id)
                .offenceCode("TH68001")
                .offenceTitle(title)
                .orderIndex(countNumber)
                .caseUrn(caseUrn)
                .build();
    }

    /** As {@link #offence(String, int, String)}, with an explicit Home Office offence code. */
    public static OffenceDto offenceWithCode(String id, int countNumber, String title,
                                              String offenceCode) {
        return OffenceDto.builder()
                .offenceId(id)
                .offenceCode(offenceCode)
                .offenceTitle(title)
                .orderIndex(countNumber)
                .caseUrn("32AH9105826")
                .build();
    }

    /** As {@link #offence(String, int, String)}, with explicit CTL/conviction flags for DR-CTL-003. */
    public static OffenceDto offenceWithCtlFlags(String id, int countNumber, String title,
                                                  boolean hasExistingCtlRecord,
                                                  boolean isConvicted) {
        return OffenceDto.builder()
                .offenceId(id)
                .offenceCode("TH68001")
                .offenceTitle(title)
                .orderIndex(countNumber)
                .caseUrn("32AH9105826")
                .hasExistingCtlRecord(hasExistingCtlRecord)
                .isConvicted(isConvicted)
                .build();
    }

    /** Builds a defendant with the given id and name. */
    public static DefendantDto defendant(String id, String firstName, String lastName) {
        return DefendantDto.builder()
                .defendantId(id)
                .firstName(firstName)
                .lastName(lastName)
                .build();
    }

    /** Builds a full request for the given lines/offences, defaulting to a Magistrates hearing. */
    public static DraftValidationRequest buildRequest(List<ResultLineDto> resultLines,
                                                       List<OffenceDto> offences) {
        return buildRequest(resultLines, offences,
                DraftValidationRequest.CourtTypeEnum.MAGISTRATES);
    }

    /** As {@link #buildRequest(List, List)}, with an explicit court type. */
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
