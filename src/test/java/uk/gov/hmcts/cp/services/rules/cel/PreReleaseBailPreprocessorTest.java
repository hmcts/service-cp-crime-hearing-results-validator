package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

import java.time.LocalDate;
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

@DisplayName("PreReleaseBailPreprocessor")
class PreReleaseBailPreprocessorTest {

    private static final RuleDefinition RULE =
            RuleDefinitionLoader.load("rules/DR-BAIL-016.yaml");

    private final PreReleaseBailPreprocessor preprocessor = new PreReleaseBailPreprocessor();
    private final PreprocessingDefinition config = RULE.preprocessing();

    // -----------------------------------------------------------------------
    // Builder helpers
    // -----------------------------------------------------------------------

    private static OffenceDto custodialOffence(String id, int idx, OffenceDto.BailStatusEnum status) {
        return OffenceDto.builder()
                .offenceId(id)
                .offenceCode("TH68001")
                .offenceTitle("Test Offence")
                .orderIndex(idx)
                .caseUrn("32AH0000001")
                .defendantId("d1")
                .bailStatus(status)
                .build();
    }

    private static DefendantDto adultDefendant() {
        return DefendantDto.builder()
                .defendantId("d1")
                .firstName("Jane")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build();
    }

    private static DefendantDto youthDefendant() {
        return DefendantDto.builder()
                .defendantId("d1")
                .firstName("Young")
                .lastName("Person")
                .dateOfBirth(LocalDate.of(2010, 1, 1))
                .build();
    }

    private static DefendantDto noDobDefendant() {
        return DefendantDto.builder()
                .defendantId("d1")
                .firstName("No")
                .lastName("Dob")
                .build();
    }

    private static DraftValidationRequest request(List<ResultLineDto> lines,
                                                   List<OffenceDto> offences,
                                                   List<DefendantDto> defendants) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(LocalDate.of(2026, 11, 9))
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(lines)
                .offences(offences)
                .defendants(defendants)
                .build();
    }

    private static ResultLineDto poro1Line(String promptText) {
        ResultLineDto line = resultLine("rl-poro1", "PORO1", "d1", "off-1");
        line.setPrompts(List.of(new Prompt("bailConditionDetail", promptText)));
        return line;
    }

    private Map<String, PreReleaseBailContext> preprocess(DraftValidationRequest req) {
        return preprocessor.preprocess(req, config);
    }

    // -----------------------------------------------------------------------
    // Phase 3: US1 + US2 + US6 + US7 scenarios
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("[US1] Adult defendant — Warning A scenarios")
    class AdultWarningA {

        @Test
        @DisplayName("bailStatus=C, result=RC, PASS5 → adultCount=1, triggerCount=1")
        void adult_bailStatusC_rc_pass5_should_emitAdultContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", "PASS5", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            Map<String, PreReleaseBailContext> result = preprocess(req);

            assertThat(result).containsKey("off-1");
            PreReleaseBailContext ctx = result.get("off-1");
            assertThat(ctx.triggerCount()).isEqualTo(1L);
            assertThat(ctx.adultCount()).isEqualTo(1L);
            assertThat(ctx.youthCount()).isEqualTo(0L);
            assertThat(ctx.noDobCount()).isEqualTo(0L);
            assertThat(ctx.ccicCount()).isEqualTo(0L);
            assertThat(ctx.ccscCount()).isEqualTo(0L);
        }

        @ParameterizedTest(name = "result=''{0}'' → adultCount=1")
        @ValueSource(strings = {"RC", "RCBV", "BAILREVC", "REMCB", "REMCBY", "RCCCB"})
        @DisplayName("all conditional bail result codes produce adultCount=1")
        void allConditionalBailCodes_adult_should_emitAdultContext(String shortCode) {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", shortCode, "d1", "off-1"),
                            resultLine("rl2", "PASS1", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.adultCount()).isEqualTo(1L);
        }

        @ParameterizedTest(name = "trigger=''{0}'' → triggerCount=1")
        @ValueSource(strings = {"PASS1", "PASS2", "PASS3", "PASS4", "PASS5",
            "PASS6", "PASS7", "PASS8", "PORSC", "PORS"})
        @DisplayName("all named trigger bail condition codes produce triggerCount=1")
        void allNamedTriggerCodes_adult_should_emitContext(String triggerCode) {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", triggerCode, "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.triggerCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("bailStatus=L — also custodial, produces context")
        void bailStatusL_should_emitContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", "PASS1", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.L)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).containsKey("off-1");
        }

        @Test
        @DisplayName("bailStatus=S — also custodial, produces context")
        void bailStatusS_should_emitContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "REMCB", "d1", "off-1"),
                            resultLine("rl2", "PASS8", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.S)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).containsKey("off-1");
        }
    }

    @Nested
    @DisplayName("[US2] Youth defendant — Warning B scenarios")
    class YouthWarningB {

        @ParameterizedTest(name = "result=''{0}'' → youthCount=1")
        @ValueSource(strings = {"RC", "RCBV", "BAILREVC", "REMCB", "REMCBY", "RCCCB"})
        @DisplayName("all 6 conditional bail result codes → youthCount=1")
        void allConditionalBailCodes_youth_should_emitYouthContext(String shortCode) {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", shortCode, "d1", "off-1"),
                            resultLine("rl2", "PASS1", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.S)),
                    List.of(youthDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.youthCount()).isEqualTo(1L);
            assertThat(ctx.adultCount()).isEqualTo(0L);
        }

        @ParameterizedTest(name = "trigger=''{0}'' → youthCount=1")
        @ValueSource(strings = {"PASS1", "PASS2", "PASS3", "PASS4", "PASS5",
            "PASS6", "PASS7", "PASS8", "PORSC", "PORS"})
        @DisplayName("all 10 named trigger bail condition codes → youthCount=1")
        void allNamedTriggerCodes_youth_should_emitYouthContext(String triggerCode) {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", triggerCode, "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(youthDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.youthCount()).isEqualTo(1L);
            assertThat(ctx.triggerCount()).isEqualTo(1L);
        }

        @ParameterizedTest(name = "bailStatus=''{0}'' → youthCount=1")
        @ValueSource(strings = {"C", "L", "S"})
        @DisplayName("bailStatus C, L, S all qualify as custodial → youthCount=1")
        void allCustodialBailStatuses_youth_should_emitYouthContext(String bailStatusStr) {
            OffenceDto.BailStatusEnum status = OffenceDto.BailStatusEnum.valueOf(bailStatusStr);
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", "PASS1", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, status)),
                    List.of(youthDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.youthCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("bailStatus=S, result=REMCB, PORS → youthCount=1, adultCount=0")
        void youth_bailStatusS_remcb_pors_should_emitYouthContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "REMCB", "d1", "off-1"),
                            resultLine("rl2", "PORS", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.S)),
                    List.of(youthDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.youthCount()).isEqualTo(1L);
            assertThat(ctx.adultCount()).isEqualTo(0L);
            assertThat(ctx.triggerCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Age boundary — defendant turns exactly 18 on hearing day")
    class AgeBoundary {

        @Test
        @DisplayName("DOB=2008-11-09, hearingDay=2026-11-09 (exactly 18) → adultCount=1")
        void defendantTurning18OnHearingDay_should_emitAdultWarning() {
            DefendantDto defendant = DefendantDto.builder()
                    .defendantId("d1").firstName("Jane").lastName("Doe")
                    .dateOfBirth(LocalDate.of(2008, 11, 9)).build();

            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", "PASS5", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(defendant));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.adultCount()).isEqualTo(1L);
            assertThat(ctx.youthCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("DOB=2008-11-10, hearingDay=2026-11-09 (one day before 18th birthday) → youthCount=1")
        void defendantOneDayBefore18thBirthday_should_emitYouthWarning() {
            DefendantDto defendant = DefendantDto.builder()
                    .defendantId("d1").firstName("Young").lastName("Person")
                    .dateOfBirth(LocalDate.of(2008, 11, 10)).build();

            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", "PASS5", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(defendant));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.youthCount()).isEqualTo(1L);
            assertThat(ctx.adultCount()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("CCIC+CCSC on same offence — both counts set, both Crown Court warnings fire")
    class CcicAndCcscBothPresent {

        @Test
        @DisplayName("CCIC and CCSC both on same offence → ccicCount=1 and ccscCount=1")
        void ccicAndCcscBothPresent_should_setCcicAndCcscCountBothOne() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "CCIC", "d1", "off-1"),
                            resultLine("rl2", "CCSC", "d1", "off-1"),
                            resultLine("rl3", "PASS1", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.ccicCount()).isEqualTo(1L);
            assertThat(ctx.ccscCount()).isEqualTo(1L);
            assertThat(ctx.triggerCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("[US6] Multiple offences — each qualifying offence gets its own context entry")
    class MultipleOffences {

        @Test
        @DisplayName("two qualifying offences for same defendant → two context entries")
        void twoQualifyingOffences_should_emitTwoContextEntries() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", "PASS5", "d1", "off-1"),
                            resultLine("rl3", "RCBV", "d1", "off-2"),
                            resultLine("rl4", "PORSC", "d1", "off-2")),
                    List.of(
                            custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C),
                            custodialOffence("off-2", 2, OffenceDto.BailStatusEnum.L)),
                    List.of(adultDefendant()));

            Map<String, PreReleaseBailContext> result = preprocess(req);

            assertThat(result).containsKeys("off-1", "off-2");
        }
    }

    @Nested
    @DisplayName("[US7] No warning when trigger conditions absent")
    class NoWarningScenarios {

        @Test
        @DisplayName("bailStatus=B — not custodial → no context entry")
        void bailStatusB_should_notEmitContext() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off-1").offenceCode("TH68001").offenceTitle("Test")
                    .orderIndex(1).caseUrn("32AH0000001").defendantId("d1")
                    .bailStatus(OffenceDto.BailStatusEnum.B).build();

            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", "PASS5", "d1", "off-1")),
                    List.of(offence),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).isEmpty();
        }

        @Test
        @DisplayName("bailStatus=C, result=RIB (non-conditional) → no context entry")
        void nonConditionalResult_should_notEmitContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RIB", "d1", "off-1"),
                            resultLine("rl2", "PASS5", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).isEmpty();
        }

        @Test
        @DisplayName("bailStatus=C, result=RC but no trigger bail condition → no context entry")
        void noTriggerBailCondition_should_notEmitContext() {
            DraftValidationRequest req = request(
                    List.of(resultLine("rl1", "RC", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).isEmpty();
        }

        @Test
        @DisplayName("null offences and result lines → empty map returned")
        void nullOffencesAndLines_should_returnEmptyMap() {
            DraftValidationRequest req = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(LocalDate.of(2026, 11, 9))
                    .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                    .resultLines(null)
                    .offences(null)
                    .defendants(List.of(adultDefendant()))
                    .build();

            assertThat(preprocess(req)).isEmpty();
        }

        @Test
        @DisplayName("empty offences and result lines → empty map returned")
        void emptyOffencesAndLines_should_returnEmptyMap() {
            DraftValidationRequest req = request(
                    List.of(), List.of(), List.of());

            assertThat(preprocess(req)).isEmpty();
        }

        @Test
        @DisplayName("bailStatus=null — not custodial → no context entry (AC7: fail-open)")
        void bailStatus_null_should_notEmitContext() {
            OffenceDto offence = OffenceDto.builder()
                    .offenceId("off-1").offenceCode("TH68001").offenceTitle("Test")
                    .orderIndex(1).caseUrn("32AH0000001").defendantId("d1")
                    .build();

            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", "PASS5", "d1", "off-1")),
                    List.of(offence),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Phase 4: US3 + US4 + US5 scenarios (extended tests added in T008)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("[US3] No date of birth — Warning C scenarios")
    class NoDobWarningC {

        @ParameterizedTest(name = "result=''{0}'' → noDobCount=1")
        @ValueSource(strings = {"RC", "RCBV", "BAILREVC", "REMCB", "REMCBY", "RCCCB"})
        @DisplayName("all 6 conditional bail result codes → noDobCount=1")
        void allConditionalBailCodes_noDob_should_emitNoDobContext(String shortCode) {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", shortCode, "d1", "off-1"),
                            resultLine("rl2", "PASS1", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(noDobDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.noDobCount()).isEqualTo(1L);
            assertThat(ctx.adultCount()).isEqualTo(0L);
            assertThat(ctx.youthCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("no DOB, result=RC, PASS5 → noDobCount=1, adultCount=0, youthCount=0")
        void noDob_rc_pass5_should_emitNoDobContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", "PASS5", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(noDobDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.noDobCount()).isEqualTo(1L);
            assertThat(ctx.adultCount()).isEqualTo(0L);
            assertThat(ctx.youthCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("null hearingDay with DOB present → noDobCount=1 (treat as unknown age)")
        void nullHearingDay_dobPresent_should_emitNoDobContext() {
            DraftValidationRequest req = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(null)
                    .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                    .resultLines(List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            resultLine("rl2", "PASS5", "d1", "off-1")))
                    .offences(List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)))
                    .defendants(List.of(adultDefendant()))
                    .build();

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.noDobCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("[US4] Crown Court results — Warning D (CCIC) and Warning E (CCSC)")
    class CrownCourtWarnings {

        @ParameterizedTest(name = "crownCourtCode=''{0}'' → context emitted")
        @ValueSource(strings = {"CCIC", "CCSC"})
        @DisplayName("all 2 crown court bail codes → context emitted")
        void allCrownCourtBailCodes_should_emitContext(String crownCourtCode) {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", crownCourtCode, "d1", "off-1"),
                            resultLine("rl2", "PASS1", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.triggerCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("adult, bailStatus=C, result=CCIC, PASS1 → ccicCount=1, adultCount=1")
        void adult_ccic_pass1_should_emitCcicContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "CCIC", "d1", "off-1"),
                            resultLine("rl2", "PASS1", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.ccicCount()).isEqualTo(1L);
            assertThat(ctx.ccscCount()).isEqualTo(0L);
            assertThat(ctx.adultCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("youth, bailStatus=L, result=CCIC, PORSC → ccicCount=1, youthCount=1")
        void youth_bailStatusL_ccic_porsc_should_emitCcicYouthContext() {
            DefendantDto youth = DefendantDto.builder()
                    .defendantId("d1").firstName("Y").lastName("P")
                    .dateOfBirth(LocalDate.of(2010, 6, 1)).build();

            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "CCIC", "d1", "off-1"),
                            resultLine("rl2", "PORSC", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.L)),
                    List.of(youth));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.ccicCount()).isEqualTo(1L);
            assertThat(ctx.youthCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("no DOB, result=CCIC, PASS3 → ccicCount=1, noDobCount=1")
        void noDob_ccic_pass3_should_emitCcicNoDobContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "CCIC", "d1", "off-1"),
                            resultLine("rl2", "PASS3", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(noDobDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.ccicCount()).isEqualTo(1L);
            assertThat(ctx.noDobCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("adult, bailStatus=S, result=CCSC, PASS8 → ccscCount=1, adultCount=1")
        void adult_bailStatusS_ccsc_pass8_should_emitCcscContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "CCSC", "d1", "off-1"),
                            resultLine("rl2", "PASS8", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.S)),
                    List.of(adultDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.ccscCount()).isEqualTo(1L);
            assertThat(ctx.adultCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("youth, bailStatus=S, result=CCSC, PASS1 → ccscCount=1, youthCount=1")
        void youth_bailStatusS_ccsc_pass1_should_emitCcscYouthContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "CCSC", "d1", "off-1"),
                            resultLine("rl2", "PASS1", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.S)),
                    List.of(youthDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.ccscCount()).isEqualTo(1L);
            assertThat(ctx.youthCount()).isEqualTo(1L);
            assertThat(ctx.ccicCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("no DOB, result=CCSC, PASS5 → ccscCount=1, noDobCount=1")
        void noDob_ccsc_pass5_should_emitCcscNoDobContext() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "CCSC", "d1", "off-1"),
                            resultLine("rl2", "PASS5", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(noDobDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.ccscCount()).isEqualTo(1L);
            assertThat(ctx.noDobCount()).isEqualTo(1L);
            assertThat(ctx.ccicCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("RC before CCIC in result lines — ccicCount=1 regardless of line order")
        void rcBeforeCcic_should_still_setCcicCount() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC",   "d1", "off-1"),
                            resultLine("rl2", "CCIC", "d1", "off-1"),
                            resultLine("rl3", "PASS1", "d1", "off-1")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            PreReleaseBailContext ctx = preprocess(req).get("off-1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.ccicCount()).isEqualTo(1L);
            assertThat(ctx.ccscCount()).isEqualTo(0L);
            assertThat(ctx.adultCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("[US5] PORO1 free-text keyword matching")
    class Poro1KeywordMatching {

        @Test
        @DisplayName("PORO1 prompt 'needs a passport' → triggerCount=1 (stem 'passport')")
        void poro1_passport_english_should_trigger() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            poro1Line("needs a passport")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).containsKey("off-1");
        }

        @Test
        @DisplayName("PORO1 prompt 'PASSPORT' (uppercase) → triggerCount=1 (case-insensitive)")
        void poro1_passport_uppercase_should_trigger() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            poro1Line("PASSPORT")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).containsKey("off-1");
        }

        @Test
        @DisplayName("PORO1 prompt 'provide surety' → triggerCount=1 (stem 'suret')")
        void poro1_surety_should_trigger() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            poro1Line("provide surety")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).containsKey("off-1");
        }

        @Test
        @DisplayName("PORO1 prompt 'security deposit' → triggerCount=1 (stem 'securit')")
        void poro1_security_should_trigger() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            poro1Line("security deposit")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).containsKey("off-1");
        }

        @Test
        @DisplayName("PORO1 prompt 'pasbort' (Welsh) → triggerCount=1 (stem 'asbort')")
        void poro1_pasbort_welsh_should_trigger() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            poro1Line("pasbort")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).containsKey("off-1");
        }

        @Test
        @DisplayName("PORO1 prompt 'mechnïwr' (Welsh surety) → triggerCount=1 (stem 'echniw' after NFD)")
        void poro1_mechniwr_welsh_should_trigger() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            poro1Line("mechnïwr")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).containsKey("off-1");
        }

        @Test
        @DisplayName("PORO1 prompt 'mechniwr' (Welsh no accent) → triggerCount=1")
        void poro1_mechniwr_no_accent_should_trigger() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            poro1Line("mechniwr")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).containsKey("off-1");
        }

        @Test
        @DisplayName("PORO1 prompt 'sicrwydd' (Welsh security) → triggerCount=1")
        void poro1_sicrwydd_welsh_should_trigger() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            poro1Line("sicrwydd")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).containsKey("off-1");
        }

        @Test
        @DisplayName("PORO1 prompt 'report to probation officer' → triggerCount=0 (no keyword)")
        void poro1_noKeyword_should_notTrigger() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            poro1Line("report to probation officer")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).isEmpty();
        }

        @Test
        @DisplayName("PORO1 null prompt value → triggerCount=0 (null-safe)")
        void poro1_nullPromptValue_should_notTrigger() {
            ResultLineDto poro1 = resultLine("rl-poro1", "PORO1", "d1", "off-1");
            poro1.setPrompts(List.of(new Prompt("bailConditionDetail", null)));

            DraftValidationRequest req = request(
                    List.of(resultLine("rl1", "RC", "d1", "off-1"), poro1),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).isEmpty();
        }

        @Test
        @DisplayName("PORO1 prompt text '' (blank) → triggerCount=0")
        void poro1_blankPrompt_should_notTrigger() {
            DraftValidationRequest req = request(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off-1"),
                            poro1Line("")),
                    List.of(custodialOffence("off-1", 1, OffenceDto.BailStatusEnum.C)),
                    List.of(adultDefendant()));

            assertThat(preprocess(req)).isEmpty();
        }
    }

    @Nested
    @DisplayName("containsTriggerKeyword — all word forms from the spec")
    class ContainsTriggerKeywordWordForms {

        @ParameterizedTest(name = "''{0}'' → true")
        @ValueSource(strings = {
            "passport", "passports",
            "surety", "sureties",
            "security", "securities",
            "pasbort", "pasbortau", "basbort",
            "mechnïwr", "mechnïwyr", "fechnïwr", "fechnïwyr",
            "sicrwydd"
        })
        @DisplayName("spec trigger word forms → true")
        void triggerWordForms_should_returnTrue(String word) {
            assertThat(PreReleaseBailPreprocessor.containsTriggerKeyword(word)).isTrue();
        }

        @ParameterizedTest(name = "''{0}'' → false")
        @ValueSource(strings = {
            "report to probation",
            "curfew",
            "electronic monitoring",
            "residence",
            "tagging"
        })
        @DisplayName("non-trigger phrases → false")
        void nonTriggerPhrases_should_returnFalse(String phrase) {
            assertThat(PreReleaseBailPreprocessor.containsTriggerKeyword(phrase)).isFalse();
        }
    }
}
