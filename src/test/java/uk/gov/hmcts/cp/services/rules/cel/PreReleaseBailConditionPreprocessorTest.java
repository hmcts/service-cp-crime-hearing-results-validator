package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.DefendantDto;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

@ExtendWith(MockitoExtension.class)
class PreReleaseBailConditionPreprocessorTest {

    private static final LocalDate HEARING_DAY = LocalDate.of(2026, 3, 11);
    // 25 years old on hearing day → adult
    private static final LocalDate ADULT_DOB = HEARING_DAY.minusYears(25);
    // 15 years old on hearing day → youth
    private static final LocalDate YOUTH_DOB = HEARING_DAY.minusYears(15);

    private final PreReleaseBailConditionPreprocessor preprocessor =
            new PreReleaseBailConditionPreprocessor();

    private final PreprocessingDefinition config = PreprocessingDefinition.builder()
            .type(PreReleaseBailConditionPreprocessor.QUALIFIER)
            .remandConditionalBailShortCodes(
                    List.of("RC", "RCBV", "BAILREVC", "REMCB", "REMCBY", "RCCCB"))
            .ccicShortCodes(List.of("CCIC"))
            .ccscShortCodes(List.of("CCSC"))
            .preReleaseBailConditionShortCodes(List.of(
                    "PREBC", "PRBCO",
                    "PRBCP1", "PRBCP2", "PRBCP3", "PRBCP4",
                    "PRBCP5", "PRBCP6", "PRBCP7", "PRBCP8",
                    "PRBCS1", "PRBCS2", "PRBCS3",
                    "PRBCSE1", "PRBCSE2", "PRBCT"))
            .build();

    private DraftValidationRequest buildRequest(List<ResultLineDto> lines,
                                                 List<DefendantDto> defendants) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(HEARING_DAY)
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(lines)
                .defendants(defendants)
                .offences(List.of(offence("off1", 1, "Test Offence")))
                .build();
    }

    private DraftValidationRequest buildRequestMultiOffence(List<ResultLineDto> lines,
                                                             List<DefendantDto> defendants,
                                                             List<OffenceDto> offences) {
        return DraftValidationRequest.builder()
                .hearingId("h1")
                .hearingDay(HEARING_DAY)
                .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                .resultLines(lines)
                .defendants(defendants)
                .offences(offences)
                .build();
    }

    private DefendantDto defendant(String id, LocalDate dob) {
        return DefendantDto.builder()
                .defendantId(id)
                .firstName("John")
                .lastName("Smith")
                .dateOfBirth(dob)
                .build();
    }

    @Nested
    @DisplayName("type()")
    class TypeQualifier {

        @Test
        void type_shouldReturnPreReleaseBailConditionQualifier() {
            assertThat(preprocessor.type()).isEqualTo("pre-release-bail-condition");
        }
    }

    @Nested
    @DisplayName("Empty / null result lines")
    class EmptyInput {

        @Test
        void nullResultLines_shouldReturnEmptyMap() {
            final DraftValidationRequest req = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(HEARING_DAY)
                    .resultLines(null)
                    .defendants(List.of())
                    .build();

            assertThat(preprocessor.preprocess(req, config)).isEmpty();
        }

        @Test
        void emptyResultLines_shouldReturnEmptyMap() {
            final DraftValidationRequest req = buildRequest(
                    List.of(), List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).isEmpty();
        }
    }

    @Nested
    @DisplayName("No trigger condition")
    class NoTrigger {

        @Test
        void parentOnlyNoPreRelease_shouldReturnEmptyMap() {
            final DraftValidationRequest req = buildRequest(
                    List.of(resultLine("rl1", "RC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).isEmpty();
        }

        @Test
        void preReleaseOnlyNoParent_shouldReturnEmptyMap() {
            final DraftValidationRequest req = buildRequest(
                    List.of(resultLine("rl1", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).isEmpty();
        }

        @Test
        void preReleaseOnDifferentOffence_shouldReturnEmptyMap() {
            final DraftValidationRequest req = buildRequestMultiOffence(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off2")),
                    List.of(defendant("d1", ADULT_DOB)),
                    List.of(offence("off1", 1, "Off1"), offence("off2", 2, "Off2")));

            assertThat(preprocessor.preprocess(req, config)).isEmpty();
        }

        @Test
        void ricdParent_withPreRelease_shouldNotTrigger() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RICD", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).isEmpty();
        }

        @Test
        void unconditionalBailParent_withPreRelease_shouldNotTrigger() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "UB", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).isEmpty();
        }

        @ParameterizedTest(name = "{0} extradition result should not trigger (post-hearing custody status A, not B)")
        @ValueSource(strings = {"E1CB", "E1CONCB", "E2CB"})
        void extraditionConditionalBailCode_withPreRelease_shouldNotTrigger(
                final String extraditionCode) {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", extraditionCode, "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Adult defendant — remand bail parent")
    class AdultRemandBail {

        @Test
        void rcParent_prebcChild_adultDefendant_shouldEmitContextWithAdultFlags() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final Map<String, PreReleaseBailContext> result =
                    preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            final PreReleaseBailContext ctx = result.get("off1");
            assertThat(ctx).isNotNull();
            assertThat(ctx.isRemandBail()).isEqualTo(1L);
            assertThat(ctx.isCcic()).isEqualTo(0L);
            assertThat(ctx.isCcsc()).isEqualTo(0L);
            assertThat(ctx.isAdult()).isEqualTo(1L);
            assertThat(ctx.isYouth()).isEqualTo(0L);
            assertThat(ctx.hasDob()).isEqualTo(1L);
            assertThat(ctx.hearingHasRemandAdult()).isEqualTo(1L);
            assertThat(ctx.hearingHasRemandYouth()).isEqualTo(0L);
        }

        @Test
        void adultOnlyHearing_hearingHasRemandYouthShouldBeZero() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.hearingHasRemandAdult()).isEqualTo(1L);
            assertThat(ctx.hearingHasRemandYouth()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Youth defendant — remand bail parent")
    class YouthRemandBail {

        @Test
        void remcbyParent_prbcoChild_youthDefendant_shouldEmitContextWithYouthFlags() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "REMCBY", "d1", "off1"),
                            resultLine("rl2", "PRBCO", "d1", "off1")),
                    List.of(defendant("d1", YOUTH_DOB)));

            final Map<String, PreReleaseBailContext> result =
                    preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1);
            final PreReleaseBailContext ctx = result.get("off1");
            assertThat(ctx.isRemandBail()).isEqualTo(1L);
            assertThat(ctx.isAdult()).isEqualTo(0L);
            assertThat(ctx.isYouth()).isEqualTo(1L);
            assertThat(ctx.hasDob()).isEqualTo(1L);
            assertThat(ctx.hearingHasRemandAdult()).isEqualTo(0L);
            assertThat(ctx.hearingHasRemandYouth()).isEqualTo(1L);
        }

        @Test
        void youthOnlyHearing_hearingHasRemandAdultShouldBeZero() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "REMCBY", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", YOUTH_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.hearingHasRemandAdult()).isEqualTo(0L);
            assertThat(ctx.hearingHasRemandYouth()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("No date of birth")
    class NoDob {

        @Test
        void rcParent_prebcChild_noDob_shouldSetHasDobZeroAndBothAgesZero() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", null)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.hasDob()).isEqualTo(0L);
            assertThat(ctx.isAdult()).isEqualTo(0L);
            assertThat(ctx.isYouth()).isEqualTo(0L);
            // no-DOB defendant does not contribute to hearingHasRemandAdult/Youth
            assertThat(ctx.hearingHasRemandAdult()).isEqualTo(0L);
            assertThat(ctx.hearingHasRemandYouth()).isEqualTo(0L);
        }

        @Test
        void noDobDefendant_alongsideAdult_shouldNotInflateHearingYouthFlag() {
            // off1: adult d1 → hearingHasRemandAdult=true; off2: no-DOB d2 → must not set Youth
            final DraftValidationRequest req = buildRequestMultiOffence(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1"),
                            resultLine("rl3", "RC", "d2", "off2"),
                            resultLine("rl4", "PREBC", "d2", "off2")),
                    List.of(defendant("d1", ADULT_DOB), defendant("d2", null)),
                    List.of(offence("off1", 1, "Off1"), offence("off2", 2, "Off2")));

            final Map<String, PreReleaseBailContext> result =
                    preprocessor.preprocess(req, config);

            assertThat(result).hasSize(2);
            assertThat(result.get("off1").hearingHasRemandAdult()).isEqualTo(1L);
            assertThat(result.get("off1").hearingHasRemandYouth()).isEqualTo(0L);
            // no-DOB offence inherits the hearing flags — adult flag set by d1; youth stays 0
            assertThat(result.get("off2").hearingHasRemandAdult()).isEqualTo(1L);
            assertThat(result.get("off2").hearingHasRemandYouth()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("CCIC and CCSC parents")
    class CcicCcscParents {

        @Test
        void ccicParent_prebcChild_shouldSetIsCcicFlag() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "CCIC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.isCcic()).isEqualTo(1L);
            assertThat(ctx.isRemandBail()).isEqualTo(0L);
            assertThat(ctx.isCcsc()).isEqualTo(0L);
        }

        @Test
        void ccscParent_prebcChild_shouldSetIsCcscFlag() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "CCSC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.isCcsc()).isEqualTo(1L);
            assertThat(ctx.isRemandBail()).isEqualTo(0L);
            assertThat(ctx.isCcic()).isEqualTo(0L);
        }

        @Test
        void ccicAndCcscOnSameOffence_firstCcicWins() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "CCIC", "d1", "off1"),
                            resultLine("rl2", "CCSC", "d1", "off1"),
                            resultLine("rl3", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.isCcic()).isEqualTo(1L);
            assertThat(ctx.isCcsc()).isEqualTo(0L);
            assertThat(ctx.isRemandBail()).isEqualTo(0L);
        }

        @Test
        void ccscAndCcicOnSameOffence_firstCcscWins() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "CCSC", "d1", "off1"),
                            resultLine("rl2", "CCIC", "d1", "off1"),
                            resultLine("rl3", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.isCcsc()).isEqualTo(1L);
            assertThat(ctx.isCcic()).isEqualTo(0L);
            assertThat(ctx.isRemandBail()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Mixed adult and youth hearing")
    class MixedHearing {

        @Test
        void adultAndYouthBothTriggered_bothHearingFlagsShouldBeSet() {
            final DraftValidationRequest req = buildRequestMultiOffence(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1"),
                            resultLine("rl3", "REMCBY", "d2", "off2"),
                            resultLine("rl4", "PRBCO", "d2", "off2")),
                    List.of(defendant("d1", ADULT_DOB), defendant("d2", YOUTH_DOB)),
                    List.of(offence("off1", 1, "Off1"), offence("off2", 2, "Off2")));

            final Map<String, PreReleaseBailContext> result =
                    preprocessor.preprocess(req, config);

            assertThat(result).hasSize(2);

            final PreReleaseBailContext adultCtx = result.get("off1");
            assertThat(adultCtx.isAdult()).isEqualTo(1L);
            assertThat(adultCtx.isYouth()).isEqualTo(0L);
            assertThat(adultCtx.hearingHasRemandAdult()).isEqualTo(1L);
            assertThat(adultCtx.hearingHasRemandYouth()).isEqualTo(1L);

            final PreReleaseBailContext youthCtx = result.get("off2");
            assertThat(youthCtx.isAdult()).isEqualTo(0L);
            assertThat(youthCtx.isYouth()).isEqualTo(1L);
            assertThat(youthCtx.hearingHasRemandAdult()).isEqualTo(1L);
            assertThat(youthCtx.hearingHasRemandYouth()).isEqualTo(1L);
        }

        @Test
        void threeOffencesMixedAges_bothHearingFlagsShouldBeSetOnAllContexts() {
            // off1: adult, off2: adult, off3: youth → all three contexts carry both hearing flags
            final DraftValidationRequest req = buildRequestMultiOffence(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1"),
                            resultLine("rl3", "RC", "d2", "off2"),
                            resultLine("rl4", "PREBC", "d2", "off2"),
                            resultLine("rl5", "REMCBY", "d3", "off3"),
                            resultLine("rl6", "PRBCO", "d3", "off3")),
                    List.of(defendant("d1", ADULT_DOB), defendant("d2", ADULT_DOB),
                            defendant("d3", YOUTH_DOB)),
                    List.of(offence("off1", 1, "Off1"), offence("off2", 2, "Off2"),
                            offence("off3", 3, "Off3")));

            final Map<String, PreReleaseBailContext> result =
                    preprocessor.preprocess(req, config);

            assertThat(result).hasSize(3);
            result.values().forEach(ctx -> {
                assertThat(ctx.hearingHasRemandAdult()).isEqualTo(1L);
                assertThat(ctx.hearingHasRemandYouth()).isEqualTo(1L);
            });
        }
    }

    @Nested
    @DisplayName("First parent code wins when multiple parent codes on same offence")
    class FirstParentWins {

        @Test
        void remandBeforeCcic_remandWins() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "CCIC", "d1", "off1"),
                            resultLine("rl3", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.isRemandBail()).isEqualTo(1L);
            assertThat(ctx.isCcic()).isEqualTo(0L);
            assertThat(ctx.isCcsc()).isEqualTo(0L);
        }

        @Test
        void ccicBeforeRemand_ccicWins() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "CCIC", "d1", "off1"),
                            resultLine("rl2", "RC", "d1", "off1"),
                            resultLine("rl3", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.isCcic()).isEqualTo(1L);
            assertThat(ctx.isRemandBail()).isEqualTo(0L);
            assertThat(ctx.isCcsc()).isEqualTo(0L);
        }

        @Test
        void remandBeforeCcsc_remandWins() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "CCSC", "d1", "off1"),
                            resultLine("rl3", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.isRemandBail()).isEqualTo(1L);
            assertThat(ctx.isCcic()).isEqualTo(0L);
            assertThat(ctx.isCcsc()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("All remand parent codes trigger isRemandBail")
    class AllRemandParentCodes {

        @ParameterizedTest(name = "{0} parent should trigger isRemandBail=1")
        @ValueSource(strings = {"RC", "RCBV", "BAILREVC", "REMCB", "REMCBY", "RCCCB"})
        void remandParentCode_shouldTriggerIsRemandBailFlag(String parentCode) {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", parentCode, "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.isRemandBail()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("All pre-release child codes trigger the rule")
    class AllPreReleaseChildCodes {

        @ParameterizedTest(name = "{0} child code should trigger context")
        @ValueSource(strings = {
            "PREBC", "PRBCO",
            "PRBCP1", "PRBCP2", "PRBCP3", "PRBCP4",
            "PRBCP5", "PRBCP6", "PRBCP7", "PRBCP8",
            "PRBCS1", "PRBCS2", "PRBCS3",
            "PRBCSE1", "PRBCSE2", "PRBCT"
        })
        void preReleaseChildCode_withRcParent_shouldEmitContext(String childCode) {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", childCode, "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).containsKey("off1");
        }
    }

    @Nested
    @DisplayName("Multiple pre-release codes on same offence")
    class MultiplePreReleaseCodes {

        @Test
        void multiplePreReleaseCodesOnSameOffence_shouldEmitOneContextOnly() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1"),
                            resultLine("rl3", "PRBCO", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final Map<String, PreReleaseBailContext> result =
                    preprocessor.preprocess(req, config);

            assertThat(result).hasSize(1).containsKey("off1");
        }
    }

    @Nested
    @DisplayName("Case insensitivity")
    class CaseInsensitivity {

        @Test
        void lowercaseShortCodes_shouldStillTrigger() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "rc", "d1", "off1"),
                            resultLine("rl2", "prebc", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).containsKey("off1");
        }

        @Test
        void mixedCaseShortCodes_shouldStillTrigger() {
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "Rc", "d1", "off1"),
                            resultLine("rl2", "pRbCo", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).containsKey("off1");
        }
    }

    @Nested
    @DisplayName("Age boundary — exactly 18 on hearing day")
    class AgeBoundary {

        @Test
        void eighteenYearsOldOnHearingDay_shouldBeAdult() {
            final LocalDate exactlyEighteenDob = HEARING_DAY.minusYears(18);

            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", exactlyEighteenDob)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.isAdult()).isEqualTo(1L);
            assertThat(ctx.isYouth()).isEqualTo(0L);
            assertThat(ctx.hasDob()).isEqualTo(1L);
        }

        @Test
        void oneDayBeforeEighteenthBirthday_shouldBeYouth() {
            // age = 17 years and 364 days → youth
            final LocalDate dayBeforeEighteenDob = HEARING_DAY.minusYears(18).plusDays(1);

            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", dayBeforeEighteenDob)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.isAdult()).isEqualTo(0L);
            assertThat(ctx.isYouth()).isEqualTo(1L);
        }

        @Test
        void oneDayAfterEighteenthBirthday_shouldBeAdult() {
            // age = 18 years and 1 day → adult
            final LocalDate dayAfterEighteenDob = HEARING_DAY.minusYears(18).minusDays(1);

            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", dayAfterEighteenDob)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.isAdult()).isEqualTo(1L);
            assertThat(ctx.isYouth()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Null-safety / defensive guards")
    class NullSafety {

        @Test
        void nullOffenceId_inResultLine_shouldSkipLine() {
            final ResultLineDto rcNoOffence = ResultLineDto.builder()
                    .resultLineId("rl1").shortCode("RC").defendantId("d1").build();
            final DraftValidationRequest req = buildRequest(
                    List.of(rcNoOffence, resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).isEmpty();
        }

        @Test
        void nullShortCode_inResultLine_shouldSkipLine() {
            final ResultLineDto noCode = ResultLineDto.builder()
                    .resultLineId("rl1").offenceId("off1").defendantId("d1").build();
            final DraftValidationRequest req = buildRequest(
                    List.of(noCode, resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            assertThat(preprocessor.preprocess(req, config)).isEmpty();
        }

        @Test
        void nullElementInResultLinesList_shouldSkipAndContinueProcessing() {
            final List<ResultLineDto> lines = new ArrayList<>();
            lines.add(null);
            lines.add(resultLine("rl1", "RC", "d1", "off1"));
            lines.add(resultLine("rl2", "PREBC", "d1", "off1"));
            final DraftValidationRequest req = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(HEARING_DAY)
                    .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                    .resultLines(lines)
                    .defendants(List.of(defendant("d1", ADULT_DOB)))
                    .offences(List.of(offence("off1", 1, "Test Offence")))
                    .build();

            assertThat(preprocessor.preprocess(req, config)).containsKey("off1");
        }

        @Test
        void nullDefendants_withTriggeredOffence_shouldEmitContextWithNoDob() {
            final DraftValidationRequest req = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(HEARING_DAY)
                    .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                    .resultLines(List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")))
                    .defendants(null)
                    .offences(List.of(offence("off1", 1, "Test Offence")))
                    .build();

            final PreReleaseBailContext ctx = preprocessor.preprocess(req, config).get("off1");
            assertThat(ctx).isNotNull();
            assertThat(ctx.hasDob()).isZero();
            assertThat(ctx.isAdult()).isZero();
            assertThat(ctx.isYouth()).isZero();
        }

        @Test
        void nullHearingDay_withNonNullDob_shouldSetHasDobZeroRoutingToNoDobPath() {
            // The API schema requires hearingDay, so null can only arrive via programmatic call.
            // Age cannot be determined without hearingDay; hasDob=0 routes CEL to AC_NODOB (Error C).
            final DraftValidationRequest req = DraftValidationRequest.builder()
                    .hearingId("h1")
                    .hearingDay(null)
                    .courtType(DraftValidationRequest.CourtTypeEnum.MAGISTRATES)
                    .resultLines(List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")))
                    .defendants(List.of(defendant("d1", ADULT_DOB)))
                    .offences(List.of(offence("off1", 1, "Test Offence")))
                    .build();

            final PreReleaseBailContext ctx = preprocessor.preprocess(req, config).get("off1");
            assertThat(ctx).isNotNull();
            assertThat(ctx.hasDob()).isZero();
            assertThat(ctx.isAdult()).isZero();
            assertThat(ctx.isYouth()).isZero();
        }
    }

    @Nested
    @DisplayName("Defendant name resolution")
    class DefendantName {

        @Test
        void adultDefendant_shouldPopulateDefendantName() {
            final DefendantDto def = DefendantDto.builder()
                    .defendantId("d1")
                    .firstName("Jane")
                    .lastName("Doe")
                    .dateOfBirth(ADULT_DOB)
                    .build();

            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1")),
                    List.of(def));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx.defendantName()).isEqualTo("Jane Doe");
            assertThat(ctx.defendantId()).isEqualTo("d1");
        }

        @Test
        void missingDefendantInList_shouldFallbackToEmptyName() {
            // Result lines reference d2 but defendants list only contains d1
            final DraftValidationRequest req = buildRequest(
                    List.of(
                            resultLine("rl1", "RC", "d2", "off1"),
                            resultLine("rl2", "PREBC", "d2", "off1")),
                    List.of(defendant("d1", ADULT_DOB)));

            final PreReleaseBailContext ctx =
                    preprocessor.preprocess(req, config).get("off1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.defendantName()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Context map preserves result-line insertion order")
    class InsertionOrder {

        @Test
        void triggeredOffences_returnedInResultLineOrder() {
            final DraftValidationRequest req = buildRequestMultiOffence(
                    List.of(
                            resultLine("rl1", "RC", "d1", "off1"),
                            resultLine("rl2", "PREBC", "d1", "off1"),
                            resultLine("rl3", "RC", "d2", "off2"),
                            resultLine("rl4", "PREBC", "d2", "off2")),
                    List.of(defendant("d1", ADULT_DOB), defendant("d2", ADULT_DOB)),
                    List.of(offence("off1", 1, "Off1"), offence("off2", 2, "Off2")));

            final Map<String, PreReleaseBailContext> result =
                    preprocessor.preprocess(req, config);

            assertThat(result.keySet()).containsExactly("off1", "off2");
        }
    }
}