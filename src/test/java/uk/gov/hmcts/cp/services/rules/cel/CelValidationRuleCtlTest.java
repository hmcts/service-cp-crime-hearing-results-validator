package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceConviction;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.ctlPreprocessors;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.wrapWithConvictions;

/**
 * Focused unit tests for the DR-CTL-001 CEL validation rule implementation.
 */
class CelValidationRuleCtlTest {

    private static final String OFFENCE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String CTL_WARNING_MESSAGE =
            "This offence does not have a CTL. If the trial has started a CTL is not needed. "
                    + "It is your responsibility to check and confirm.";

    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();
    private final RuleOverrideService ruleOverrideService =
            mock(RuleOverrideService.class);
    private final CelValidationRule rule = new CelValidationRule(
            "rules/DR-CTL-001.yaml",
            ctlPreprocessors(),
            new CelExpressionEvaluator(),
            new MessageTemplateResolver(offenceDisplayHelper),
            offenceDisplayHelper,
            ruleOverrideService);

    @Test
    @DisplayName("getRuleDetail returns DR-CTL-001 metadata")
    void getRuleDetail_should_return_DR_CTL_001() {
        RuleDetailResponse detail = rule.getRuleDetail();

        assertThat(detail.getRuleId()).isEqualTo("DR-CTL-001");
        assertThat(detail.getTitle()).isNotBlank();
        assertThat(detail.getEnabled()).isTrue();
    }

    @Nested
    @DisplayName("AC1 – Warning fires")
    class WarningShouldFire {

        @Test
        @DisplayName("RI result + no CTL + not convicted → WARNING with exact message")
        void ri_result_no_ctl_not_convicted_produces_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of()));

            assertThat(issues).hasSize(1);
            ValidationIssue issue = issues.getFirst();
            assertThat(issue.getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
            assertThat(issue.getMessage()).isEqualTo(CTL_WARNING_MESSAGE);
            assertThat(issue.getRuleId()).isEqualTo("DR-CTL-001");
        }

        @Test
        @DisplayName("RIYDA result + no CTL + not convicted → WARNING")
        void riyda_result_no_ctl_not_convicted_produces_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RIYDA", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of()));

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
        }

        @Test
        @DisplayName("REMYD with 'Remitted for trial' label + no CTL + not convicted → WARNING")
        void remyd_remitted_for_trial_no_ctl_not_convicted_produces_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithLabel("rl1", "REMYD", "Remitted for trial", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of()));

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
        }

        @Test
        @DisplayName("Warning includes the affected offence")
        void warning_includes_affected_offence() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of()));

            assertThat(issues.getFirst().getAffectedOffences()).isNotEmpty();
            assertThat(issues.getFirst().getAffectedOffences().getFirst().getOffenceId())
                    .isEqualTo(OFFENCE_ID);
        }
    }

    @Nested
    @DisplayName("AC1 – Warning should not fire")
    class WarningShouldNotFire {

        @Test
        @DisplayName("RI result + CTL present → no warning")
        void ri_result_with_ctl_present_no_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID),
                            resultLine("rl2", "CTL", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of()));

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("RI result + offence convicted → no warning")
        void ri_result_convicted_offence_no_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));
            OffenceConviction conviction = OffenceConviction.builder()
                    .offenceId(UUID.fromString(OFFENCE_ID))
                    .convicted(true)
                    .build();

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of(conviction)));

            assertThat(issues).isEmpty();
        }

        @ParameterizedTest(name = "shortCode={0} is not a relevant remand code → no warning")
        @ValueSource(strings = {"IMP", "FINE", "EMONE", "DTO", "YOI", "STSDY",
                "SPECC", "SPECCC", "SPECCD", "EXTDVS", "EXTDVSU", "EXTIVS",
                "CUSS", "DISCO", "NILFP", "REPY"})
        @DisplayName("Non-relevant shortcode produces no warning")
        void non_relevant_shortcode_no_warning(String shortCode) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", shortCode, "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of()));

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("REMYD with different label → no warning")
        void remyd_with_wrong_label_no_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithLabel("rl1", "REMYD", "Remitted for sentence", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of()));

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("Offence with convicted=false still warns (not convicted is the trigger)")
        void offence_with_convicted_false_in_conviction_set_still_warns() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));
            OffenceConviction conviction = OffenceConviction.builder()
                    .offenceId(UUID.fromString(OFFENCE_ID))
                    .convicted(false)
                    .build();

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of(conviction)));

            assertThat(issues).hasSize(1);
        }
    }

    @Nested
    @DisplayName("OffenceConvictions with mixed convicted flags")
    class MixedConvictions {

        private static final String OTHER_OFFENCE_ID = "22222222-2222-2222-2222-222222222222";

        @Test
        @DisplayName("Target offence convicted=true suppresses warning; other entry with convicted=false is irrelevant")
        void target_convicted_true_with_other_false_no_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID),
                            resultLine("rl2", "RI", "d1", OTHER_OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery"),
                            offence(OTHER_OFFENCE_ID, 2, "Theft")));
            Set<OffenceConviction> convictions = Set.of(
                    OffenceConviction.builder().offenceId(UUID.fromString(OFFENCE_ID)).convicted(true).build(),
                    OffenceConviction.builder().offenceId(UUID.fromString(OTHER_OFFENCE_ID)).convicted(false).build());

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, convictions));

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getAffectedOffences().getFirst().getOffenceId())
                    .isEqualTo(OTHER_OFFENCE_ID);
        }

        @Test
        @DisplayName("Target offence convicted=false still produces warning; other entry with convicted=true is irrelevant")
        void target_convicted_false_with_other_true_still_warns() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));
            Set<OffenceConviction> convictions = Set.of(
                    OffenceConviction.builder().offenceId(UUID.fromString(OFFENCE_ID)).convicted(false).build(),
                    OffenceConviction.builder().offenceId(UUID.fromString(OTHER_OFFENCE_ID)).convicted(true).build());

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, convictions));

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getAffectedOffences().getFirst().getOffenceId())
                    .isEqualTo(OFFENCE_ID);
        }

        @Test
        @DisplayName("Conviction entry for a different offence does not suppress warning for the target offence")
        void conviction_for_different_offence_does_not_suppress_target_warning() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));
            OffenceConviction otherConviction = OffenceConviction.builder()
                    .offenceId(UUID.fromString(OTHER_OFFENCE_ID))
                    .convicted(true)
                    .build();

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of(otherConviction)));

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().getAffectedOffences().getFirst().getOffenceId())
                    .isEqualTo(OFFENCE_ID);
        }

        @Test
        @DisplayName("Both offences convicted=true → no warnings at all")
        void both_offences_convicted_no_warnings() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID),
                            resultLine("rl2", "RIH", "d1", OTHER_OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery"),
                            offence(OTHER_OFFENCE_ID, 2, "Theft")));
            Set<OffenceConviction> convictions = Set.of(
                    OffenceConviction.builder().offenceId(UUID.fromString(OFFENCE_ID)).convicted(true).build(),
                    OffenceConviction.builder().offenceId(UUID.fromString(OTHER_OFFENCE_ID)).convicted(true).build());

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, convictions));

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("Both offences not convicted → both produce warnings")
        void both_offences_not_convicted_both_warn() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID),
                            resultLine("rl2", "RIH", "d1", OTHER_OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery"),
                            offence(OTHER_OFFENCE_ID, 2, "Theft")));
            Set<OffenceConviction> convictions = Set.of(
                    OffenceConviction.builder().offenceId(UUID.fromString(OFFENCE_ID)).convicted(false).build(),
                    OffenceConviction.builder().offenceId(UUID.fromString(OTHER_OFFENCE_ID)).convicted(false).build());

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, convictions));

            assertThat(issues).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Database override behaviour")
    class DatabaseOverride {

        @Test
        @DisplayName("Rule disabled via DB override produces no issues")
        void rule_disabled_in_db_produces_no_issues() {
            when(ruleOverrideService.findOverride("DR-CTL-001")).thenReturn(Optional.of(
                    ValidationRuleEntity.builder()
                            .id("DR-CTL-001").enabled(false).severity("WARNING")
                            .updatedAt(Instant.now()).build()));

            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RI", "d1", OFFENCE_ID)),
                    List.of(offence(OFFENCE_ID, 1, "Robbery")));

            List<ValidationIssue> issues = rule.evaluate(wrapWithConvictions(request, Set.of()));

            assertThat(issues).isEmpty();
        }
    }

    private static uk.gov.hmcts.cp.openapi.model.ResultLineDto resultLineWithLabel(
            String id, String shortCode, String label, String defendantId, String offenceId) {
        return uk.gov.hmcts.cp.openapi.model.ResultLineDto.builder()
                .id(id).shortCode(shortCode).label(label)
                .defendantId(defendantId).offenceId(offenceId).build();
    }
}
