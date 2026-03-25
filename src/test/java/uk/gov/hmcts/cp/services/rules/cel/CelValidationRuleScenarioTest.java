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

import uk.gov.hmcts.cp.openapi.model.DefendantDto;

/**
 * Scenario-oriented acceptance tests for DR-SENT-002 grouped by acceptance condition.
 */
class CelValidationRuleScenarioTest {

    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();
    private final CelValidationRule rule = new CelValidationRule(
            "rules/DR-SENT-002.yaml",
            new CustodialPreprocessor(),
            new CelExpressionEvaluator(),
            new MessageTemplateResolver(offenceDisplayHelper),
            offenceDisplayHelper,
            mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class));

    /**
     * Scenarios that should not raise any issues because the custodial sentence relationships are
     * complete enough to infer a single primary sentence.
     */
    @Nested
    @DisplayName("AC1 – Pass scenarios")
    class Ac1Pass {

        /**
         * Scenario S1 verifies a single custodial offence is skipped and produces no issues.
         */
        @Test
        @DisplayName("S1: 1 offence, no info → Pass (single custodial offence skipped)")
        void s1_single_offence_no_info() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(offence("off1", 1, "Theft")));

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).isEmpty();
        }

        /**
         * Scenario S2 verifies one primary offence plus one concurrent and one consecutive offence
         * passes AC1 with no warnings or errors.
         */
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

    /**
     * Scenarios that should raise blocking AC2 errors because too many custodial offences omit
     * concurrent or consecutive information.
     */
    @Nested
    @DisplayName("AC2 – Error scenarios")
    class Ac2Error {

        /**
         * Scenario S3 verifies AC2 fires when one additional custodial offence beyond the primary
         * omits relationship data.
         */
        @Test
        @DisplayName("S3: 3 offences – 1 primary, 1 no-info, 1 concurrent → Error AC2 (1 non-primary missing info)")
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

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
            assertThat(issues.getFirst().getAffectedOffences()).hasSize(1);
        }

        /**
         * Scenario S4 verifies AC2 fires when every custodial offence lacks relationship data, so
         * two non-primary offences are flagged.
         */
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

    /**
     * Scenarios that should raise AC3 warnings because one or more offences are marked both
     * concurrent and consecutive.
     */
    @Nested
    @DisplayName("AC3 – Warning (both concurrent and consecutive)")
    class Ac3Warning {

        /**
         * Scenario S5 verifies a single offence marked both concurrent and consecutive produces one
         * warning naming that offence.
         */
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

        /**
         * Scenario S6 verifies AC3 can include multiple affected offences when more than one offence
         * is marked both concurrent and consecutive.
         */
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

    /**
     * Scenarios that should raise AC4 warnings because every custodial offence already has
     * relationship data and no primary sentence can be identified.
     */
    @Nested
    @DisplayName("AC4 – Warning (no primary sentence)")
    class Ac4Warning {

        /**
         * Scenario S7 verifies all-concurrent custodial offences produce the no-primary warning.
         */
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

        /**
         * Scenario S8 verifies all-consecutive custodial offences also produce the no-primary
         * warning.
         */
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

        /**
         * Scenario S9 verifies a mix of concurrent and consecutive data can still produce AC4 when
         * no offence remains as the primary sentence.
         */
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

    /**
     * Scenarios covering error resolution and mixed custodial or non-custodial input combinations.
     */
    @Nested
    @DisplayName("Error resolution and mixed types")
    class ErrorResolutionAndMixedTypes {

        /**
         * Scenario S10 verifies an AC2 error disappears once the missing relationship data is added
         * to the remaining custodial offences.
         */
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

        /**
         * Scenario S11 verifies different custodial short codes still participate together in the
         * same validation rule.
         */
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

        /**
         * Scenario S12 verifies non-custodial offences are ignored when there is only one custodial
         * offence in the hearing.
         */
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

    /**
     * Scenarios that prove offence numbering and validation continue to work across multiple cases
     * for the same defendant.
     */
    @Nested
    @DisplayName("Cross-case scenarios (S13–S18)")
    class CrossCase {

        /**
         * Scenario S13 verifies one primary offence and one concurrent offence across separate cases
         * still pass AC1.
         */
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

        /**
         * Scenario S14 verifies two cross-case custodial offences with no relationship data trigger
         * AC2.
         */
        @Test
        @DisplayName("S14: 2 cases – both no-info (same defendant) → Error AC2 (1 non-primary missing info)")
        void s14_cross_case_both_no_info() {
            List<ResultLineDto> lines = List.of(
                    resultLine("rl1", "IMP", "d1", "off1"),
                    resultLine("rl2", "IMP", "d1", "off2"));
            DraftValidationRequest request = buildRequest(lines, List.of(
                    offence("off1", 1, "Theft"),
                    offence("off2", 501, "Assault")));

            List<ValidationIssue> issues = rule.evaluate(request);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
        }

        /**
         * Scenario S14b verifies the cross-case AC2 error expands to both non-primary offences when
         * three offences all omit relationship data.
         */
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

        /**
         * Scenario S15 verifies AC3 warnings work when the offending relationship data spans
         * offences from different cases.
         */
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

        /**
         * Scenario S16 verifies AC4 warnings also work when all cross-case offences already carry
         * relationship data.
         */
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

        /**
         * Scenario S17 verifies the primary sentence can appear in a later case and still satisfy
         * AC1.
         */
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

        /**
         * Scenario S18 verifies a mixed three-offence cross-case setup with one primary, one
         * concurrent and one consecutive offence passes cleanly.
         */
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
