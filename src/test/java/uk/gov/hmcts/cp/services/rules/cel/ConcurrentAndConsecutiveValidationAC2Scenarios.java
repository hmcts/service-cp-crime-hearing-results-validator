package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.wrap;

import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Expanded AC2 scenario coverage for the custodial concurrent or consecutive validation rule.
 */
@DisplayName("AC2 – Error scenarios")
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ConcurrentAndConsecutiveValidationAC2Scenarios {

    /*
    AC2 – Custodial sentence – More than one offence for a defendants cases in the hearing with no concurrent or consecutive information – ERROR
    Given I am a user with access to Manage hearings
    And I am recording a custodial sentence against the offences for a defendant in the hearing (see result codes below) - This includes multiple cases for a defendant in the same hearing
And when recording whether the sentence is concurrent or consecutive to offence as part of each custodial result
And I leave more than one of the defendants offences with no information about whether it’s concurrent or consecutive to offence (i.e. No tick in the current box and no text in the field labelled “Consecutive to offence”)
Then when I navigate to “Manage hearings’ either using save and continue or selecting the manage hearings tab then I am presented with an ERROR message
And the error reads “[Name of defendant] offence [offence numbers] do not include details of whether they are concurrent or consecutive. There should be only one primary sentence, therefore one result without concurrent or consecutive information”
And if the offences are on different cases then the error includes the URN and offence number in the error (i.e. "Offence 1 (URN:52SB777777) and Offence 1 (URN:52SB888888) do not.....")
And the error message includes details about which offences/counts do not have this information
And I have to resolve the error before I can share the result (i.e sharing is not possible until the error is resolved)

     */
    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();
    private final CelValidationRule rule = new CelValidationRule("rules/DR-SENT-002.yaml",
            new CustodialPreprocessor(),
            new CelExpressionEvaluator(),
            new MessageTemplateResolver(offenceDisplayHelper),
            offenceDisplayHelper,
            mock(RuleOverrideService.class));

    /**
     * Verifies a single custodial offence is treated as the lone primary sentence and does not
     * raise AC2.
     */
    @Test
    @DisplayName("AC2-S1: 1 custodial offence – 1 primary (primary missing info)- No Error")
    void AC2_s1_one_primary_no_info() {
        List<ResultLineDto> lines = List.of(
                resultLine("rl1", "IMP", "d1", "off1"));
        DraftValidationRequest request = buildRequest(lines, List.of(
                offence("off1", 1, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));
        assertThat(issues).isEmpty();
    }


    /**
     * Verifies non-custodial offences do not influence AC2 when there is still only one custodial
     * primary offence without relationship information.
     */
    @Test
    @DisplayName("AC2-S2: 1 custodial offence – 1 and 1 non custodial primary (only 1 primary missing info)- No Error")
    void ac2_s2_one_primary1_plus_1_non_custodial_both_with_no_info() {
        List<ResultLineDto> lines = List.of(
                resultLine("rl1", "IMP", "d1", "off1"),
                resultLine("rl2", "OATP", "d1", "off2"));
        DraftValidationRequest request = buildRequest(lines, List.of(
                offence("off1", 1, "Burglary"),
                offence("off2", 1, "Burglary")
        ));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));
        assertThat(issues).isEmpty();
    }


    /**
     * Verifies adding a second custodial offence with no relationship data triggers AC2 even when a
     * non-custodial offence is also present.
     */
    @Test
    @DisplayName("AC2-S3: 2 custodial offence – 1 and 1 non custodial primary (1 non-primary missing info)- Error")
    void aC2_s3_one_primary1_plus_1_custodial_plus_1_non_custodial_both_with_no_info() {
        List<ResultLineDto> lines = List.of(
                resultLine("rl1", "IMP", "d1", "off1"),
                resultLine("rl2", "IMP", "d1", "off2"),
                resultLine("rl3", "OATP", "d1", "off3"));
        DraftValidationRequest request = buildRequest(lines, List.of(
                offence("off1", 1, "Burglary"),
                offence("off2", 2, "Burglary"),
                offence("off3", 3, "Burglary")
        ));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
        assertThat(issues.getFirst().getAffectedOffences()).hasSize(2);
    }


    /**
     * Verifies three custodial offences with no relationship data produce one AC2 error affecting
     * all three offences without info.
     */
    @Test
    @DisplayName("AC2-S4: 3 offences – 1 primary, 1 no-info, 1 with info (only 1 non-primary missing info)")
    void ac2_s4_one_primary_2_no_info() {
        List<ResultLineDto> lines = List.of(
                resultLine("rl1", "IMP", "d1", "off1"),
                resultLine("rl2", "IMP", "d1", "off2"),
                resultLine("rl3", "IMP", "d1", "off3"));
        DraftValidationRequest request = buildRequest(lines, List.of(
                offence("off1", 1, "Theft"),
                offence("off2", 2, "Assault"),
                offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
        assertThat(issues.getFirst().getAffectedOffences()).hasSize(3);

    }

    /**
     * Verifies AC2 still fires when there is exactly one additional no-info offence beyond the
     * primary and the remaining offence is concurrent.
     */
    @Test
    @DisplayName("AC2-S5: 3 offences – 1 primary, 1 info and 1  no-info, → Pass (only 1 non-primary missing info)")
    void ac2_s5_one_primary_one_no_info_one_concurrent() {
        List<ResultLineDto> lines = List.of(
                resultLine("rl1", "IMP", "d1", "off1"),
                resultLine("rl2", "IMP", "d1", "off2"),
                resultLine("rl3", "IMP", "d1", "off3"));
        DraftValidationRequest request = buildRequest(lines, List.of(
                offence("off1", 1, "Theft"),
                offence("off2", 2, "Assault"),
                offence("off3", 3, "Burglary")));
        request.getResultLines().get(2).setIsConcurrent(true);

        List<ValidationIssue> issues = rule.evaluate(wrap(request));
        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
        assertThat(issues.getFirst().getAffectedOffences()).hasSize(2);

    }

    /**
     * Verifies AC2 does not fire once all non-primary custodial offences have relationship data.
     */
    @Test
    @DisplayName("AC2-S6: 3 offences – 1 primary, 2 with info, → Pass (only 1 non-primary missing info)")
    void ac2_s6_one_primary_two_with_info_one_concurrent() {
        List<ResultLineDto> lines = List.of(
                resultLine("rl1", "IMP", "d1", "off1"),
                resultLine("rl2", "IMP", "d1", "off2"),
                resultLine("rl3", "IMP", "d1", "off3"));
        DraftValidationRequest request = buildRequest(lines, List.of(
                offence("off1", 1, "Theft"),
                offence("off2", 2, "Assault"),
                offence("off3", 3, "Burglary")));
        request.getResultLines().get(1).setIsConcurrent(true);
        request.getResultLines().get(2).setIsConcurrent(true);

        List<ValidationIssue> issues = rule.evaluate(wrap(request));
        assertThat(issues).hasSize(0);
    }

    /**
     * Verifies the all-no-info custodial scenario produces a single AC2 error affecting all three
     * offences without info.
     */
    @Test
    @DisplayName("AC2-S7: 3 offences – all no-info → Error")
    void ac2_sc7_all_no_info() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
        assertThat(issues.getFirst().getAffectedOffences()).hasSize(3);
    }

    /**
     * Verifies non-custodial offences are ignored when calculating the affected offences for an
     * AC2 breach.
     */
    @Test
    @DisplayName("AC2_S8: 2 custodial and 1 non custodial offences – all no-info → Error")
    void ac2_s8_all_no_info1() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "OATP", "d1", "off3")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Theft"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
        assertThat(issues.getFirst().getAffectedOffences()).hasSize(2);
    }

    /**
     * Verifies AC2 does not fire when there is only one custodial offence and the remaining
     * offences are non-custodial.
     */
    @Test
    @DisplayName("AC2_S9: 1 custodial and 2 non custodial offences – all no-info → Error")
    void ac2_s9_all_no_info1() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "OATP", "d1", "off1"),
                        resultLine("rl3", "OATP", "d1", "off2")),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Theft"),
                        offence("off3", 3, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).hasSize(0);
    }

    /**
     * Verifies null concurrent and consecutive fields are treated as absent data for custodial
     * offences and still trigger AC2.
     */
    @Test
    @DisplayName("AC2-S10: 6 offences – 2 Custodial and 4 non custodial, consecutive and Concurrent not set for all -no-info → Error ")
    void ac2_s10_all_no_infon_nullfields_are_ignored() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "OATC", "d1", "off3"),
                        resultLine("rl4", "OATC", "d1", "off3"),
                        resultLine("rl5", "vulnerability", "d1", "off3"),
                        resultLine("rl6", "timp", "d1", "off3")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary"),
                        offence("off4", 4, "Theft"),
                        offence("off5", 5, "Assault"),
                        offence("off6", 6, "Burglary")));

        request.getResultLines().get(0).setIsConcurrent(null);
        request.getResultLines().get(0).setConsecutiveToOffence(null);

        request.getResultLines().get(1).setIsConcurrent(null);
        request.getResultLines().get(1).setConsecutiveToOffence(null);

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
        assertThat(issues.getFirst().getAffectedOffences()).hasSize(2);
    }

    /**
     * Verifies null relationship fields on purely non-custodial offences are ignored completely by
     * the rule.
     */
    @Test
    @DisplayName("AC2-S11: 6 offences – 0 Custodial and 6 non custodial, consecutive and Concurrent not set for all -no-info → Error ")
    void ac2_s11_all_no_infon_nullfields_are_ignored() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "OATC", "d1", "off1"),
                        resultLine("rl2", "OATC", "d1", "off2"),
                        resultLine("rl3", "OATC", "d1", "off3"),
                        resultLine("rl4", "OATC", "d1", "off3"),
                        resultLine("rl5", "vulnerability", "d1", "off3"),
                        resultLine("rl6", "timp", "d1", "off3")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary"),
                        offence("off4", 4, "Theft"),
                        offence("off5", 5, "Assault"),
                        offence("off6", 6, "Burglary")));

        List<ValidationIssue> issues = rule.evaluate(wrap(request));

        assertThat(issues).hasSize(0);
    }


}
