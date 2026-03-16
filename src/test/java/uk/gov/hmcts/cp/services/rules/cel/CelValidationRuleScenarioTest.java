package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

class CelValidationRuleScenarioTest {

    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();
    private final CelValidationRule rule = new CelValidationRule(
            new CustodialPreprocessor(),
            new CelExpressionEvaluator(),
            new MessageTemplateResolver(offenceDisplayHelper),
            offenceDisplayHelper,
            mock(uk.gov.hmcts.cp.repository.ValidationRuleRepository.class));

    @Nested
    @DisplayName("AC1 – Pass scenarios")
    class Ac1Pass {

        @Test
        @DisplayName("S1: 1 offence, no info → Pass (single custodial offence skipped)")
        void s1_single_offence_no_info() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(offence("off1", 1, "Theft")));

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("S2: 3 offences – 1 no-info, 1 concurrent, 1 consecutive → Pass")
        void s2_three_offences_one_no_info_one_concurrent_one_consecutive() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"),
                    resultLine("rl3", "IMP", "d1", "off3"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 2, "Assault"),
                    offence("off3", 3, "Burglary")));
            request.getResultLines().get(1).setIsConcurrent(true);
            request.getResultLines().get(2).setConsecutiveToOffence("off1");

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).isEmpty();
        }
    }

    @Nested
    @DisplayName("AC2 – Error scenarios")
    class Ac2Error {

        @Test
        @DisplayName("S3: 3 offences – 1 primary, 1 no-info, 1 concurrent → Pass (only 1 non-primary missing info)")
        void s3_one_primary_one_no_info_one_concurrent() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"),
                    resultLine("rl3", "IMP", "d1", "off3"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 2, "Assault"),
                    offence("off3", 3, "Burglary")));
            request.getResultLines().get(2).setIsConcurrent(true);

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("S4: 3 offences – all no-info → Error")
        void s4_all_no_info() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off1"),
                            resultLine("rl2", "IMP", "d1", "off2"),
                            resultLine("rl3", "IMP", "d1", "off3")),
                    List.of(
                            offence("off1", 1, "Theft"),
                            offence("off2", 2, "Assault"),
                            offence("off3", 3, "Burglary")));

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
            assertThat(issues.getFirst().getAffectedOffences()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("AC3 – Warning (both concurrent and consecutive)")
    class Ac3Warning {

        @Test
        @DisplayName("S5: 2 offences – offence 2 has both → Warning AC3")
        void s5_one_offence_has_both() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 2, "Assault")));
            request.getResultLines().get(1).setIsConcurrent(true);
            request.getResultLines().get(1).setConsecutiveToOffence("off1");

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
            assertThat(issues.getFirst().getMessage()).contains("both concurrent and consecutive");
            assertThat(issues.getFirst().getAffectedOffences()).hasSize(1);
        }

        @Test
        @DisplayName("S6: 3 offences – 2 have both → Warning AC3")
        void s6_two_offences_have_both() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"),
                    resultLine("rl3", "IMP", "d1", "off3"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 2, "Assault"),
                    offence("off3", 3, "Burglary")));
            request.getResultLines().get(1).setIsConcurrent(true);
            request.getResultLines().get(1).setConsecutiveToOffence("off1");
            request.getResultLines().get(2).setIsConcurrent(true);
            request.getResultLines().get(2).setConsecutiveToOffence("off1");

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
            assertThat(issues.getFirst().getMessage()).contains("both concurrent and consecutive");
            assertThat(issues.getFirst().getAffectedOffences()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("AC4 – Warning (no primary sentence)")
    class Ac4Warning {

        @Test
        @DisplayName("S7: 3 offences – all concurrent → Warning no primary")
        void s7_all_concurrent() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"),
                    resultLine("rl3", "IMP", "d1", "off3"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 2, "Assault"),
                    offence("off3", 3, "Burglary")));
            request.getResultLines().get(0).setIsConcurrent(true);
            request.getResultLines().get(1).setIsConcurrent(true);
            request.getResultLines().get(2).setIsConcurrent(true);

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
            assertThat(issues.getFirst().getMessage()).contains("no primary sentence");
        }

        @Test
        @DisplayName("S8: 3 offences – all consecutive → Warning no primary")
        void s8_all_consecutive() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"),
                    resultLine("rl3", "IMP", "d1", "off3"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 2, "Assault"),
                    offence("off3", 3, "Burglary")));
            request.getResultLines().get(0).setConsecutiveToOffence("off2");
            request.getResultLines().get(1).setConsecutiveToOffence("off1");
            request.getResultLines().get(2).setConsecutiveToOffence("off1");

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
            assertThat(issues.getFirst().getMessage()).contains("no primary sentence");
        }

        @Test
        @DisplayName("S9: 2 offences – 1 concurrent, 1 consecutive → Warning no primary")
        void s9_one_concurrent_one_consecutive() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 2, "Assault")));
            request.getResultLines().get(0).setIsConcurrent(true);
            request.getResultLines().get(1).setConsecutiveToOffence("off1");

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
            assertThat(issues.getFirst().getMessage()).contains("no primary sentence");
        }
    }

    @Nested
    @DisplayName("Error resolution and mixed types")
    class ErrorResolutionAndMixedTypes {

        @Test
        @DisplayName("S10: Fix error by adding concurrent to one offence → Error resolves")
        void s10_fix_error_by_adding_concurrent() {
            List<OffenceDto> offences = List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 2, "Assault"),
                    offence("off3", 3, "Burglary"),
                    offence("off4", 4, "Fraud"));

            // Before: off1=primary, off2+off3=noInfo (2), off4=concurrent → AC2 fires
            DraftValidationRequest requestBefore = buildRequest(List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"),
                    resultLine("rl3", "IMP", "d1", "off3"),
                    resultLine("rl4", "IMP", "d1", "off4")), offences);
            requestBefore.getResultLines().get(3).setIsConcurrent(true);

            List<ValidationIssue> issuesBefore = rule.evaluate(requestBefore);
            assertThat(issuesBefore).hasSize(1);
            assertThat(issuesBefore.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);

            // After: off1=primary, off2+off3+off4=concurrent → no error
            DraftValidationRequest requestAfter = buildRequest(List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"),
                    resultLine("rl3", "IMP", "d1", "off3"),
                    resultLine("rl4", "IMP", "d1", "off4")), offences);
            requestAfter.getResultLines().get(1).setIsConcurrent(true);
            requestAfter.getResultLines().get(2).setIsConcurrent(true);
            requestAfter.getResultLines().get(3).setIsConcurrent(true);

            List<ValidationIssue> issuesAfter = rule.evaluate(requestAfter);
            assertThat(issuesAfter).isEmpty();
        }

        @Test
        @DisplayName("S11: IMP + extdvs (mixed custodial types), 1 no-info, 1 concurrent → Pass")
        void s11_mixed_custodial_types() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "extdvs", "d1", "off2"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 2, "Assault")));
            request.getResultLines().get(1).setIsConcurrent(true);

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("S12: 1 IMP + non-custodial offences → Pass (only 1 custodial, skipped)")
        void s12_single_custodial_with_non_custodial() {
            DraftValidationRequest request = buildRequest(
                    List.of(
                            resultLine("rl1", "IMP", "d1", "off1"),
                            resultLine("rl2", "FINE", "d1", "off2"),
                            resultLine("rl3", "EMONE", "d1", "off3")),
                    List.of(
                            offence("off1", 1, "Theft"),
                            offence("off2", 2, "Tax evasion"),
                            offence("off3", 3, "Possession")));

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cross-case scenarios (S13–S18)")
    class CrossCase {

        @Test
        @DisplayName("S13: 2 cases – 1 no-info + 1 concurrent (same defendant) → Pass AC1")
        void s13_cross_case_one_no_info_one_concurrent() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 501, "Assault")));
            request.getResultLines().get(1).setIsConcurrent(true);

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("S14: 2 cases – both no-info (same defendant) → Pass (first is primary, only 1 no-info)")
        void s14_cross_case_both_no_info() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 501, "Assault")));

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("S14b: 3 cases – all no-info (same defendant) → Error AC2 (2 non-primary missing info)")
        void s14b_cross_case_three_no_info() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"),
                    resultLine("rl3", "IMP", "d1", "off3"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 501, "Assault"),
                    offence("off3", 1001, "Burglary")));

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
            assertThat(issues.getFirst().getAffectedOffences()).hasSize(2);
        }

        @Test
        @DisplayName("S15: 2 cases – one offence has both (same defendant) → Warning AC3")
        void s15_cross_case_one_has_both() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 501, "Assault")));
            request.getResultLines().get(1).setIsConcurrent(true);
            request.getResultLines().get(1).setConsecutiveToOffence("off1");

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
            assertThat(issues.getFirst().getMessage()).contains("both concurrent and consecutive");
        }

        @Test
        @DisplayName("S16: 2 cases – all have info (same defendant) → Warning AC4 no primary")
        void s16_cross_case_all_have_info() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 501, "Assault")));
            request.getResultLines().get(0).setIsConcurrent(true);
            request.getResultLines().get(1).setConsecutiveToOffence("off1");

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
            assertThat(issues.getFirst().getMessage()).contains("no primary sentence");
        }

        @Test
        @DisplayName("S17: 2 cases – primary in 2nd case → Pass AC1")
        void s17_primary_in_second_case() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 501, "Assault")));
            request.getResultLines().get(0).setIsConcurrent(true);

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("S18: 2 cases, 3 offences – 1 primary → Pass AC1")
        void s18_multi_case_multi_offence_one_primary() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"),
                    resultLine("rl3", "IMP", "d1", "off3"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 2, "Assault"),
                    offence("off3", 501, "Burglary")));
            request.getResultLines().get(1).setIsConcurrent(true);
            request.getResultLines().get(2).setConsecutiveToOffence("off1");

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).isEmpty();
        }
    }

}
