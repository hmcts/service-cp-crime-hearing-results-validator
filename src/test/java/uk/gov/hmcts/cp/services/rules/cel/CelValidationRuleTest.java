package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.ValidationIssueResult;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;
import uk.gov.hmcts.cp.services.rules.ValidationIssueRecorder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

/**
 * Focused unit tests for the DR-SENT-002 CEL validation rule implementation.
 */
class CelValidationRuleTest {

    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();
    private final ValidationIssueRecorder issueRecorder = mock(ValidationIssueRecorder.class);
    private final CelValidationRule rule = new CelValidationRule(
            "rules/DR-SENT-002.yaml",
            new PreprocessorRegistry(List.of(new CustodialPreprocessor())),
            new CelExpressionEvaluator(),
            new MessageTemplateResolver(offenceDisplayHelper),
            offenceDisplayHelper,
            mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class),
            issueRecorder);

    /**
     * Verifies the rule exposes the expected metadata loaded from DR-SENT-002.
     */
    @Test
    void getRuleDetail_should_return_DR_SENT_002() {
        RuleDetailResponse detail = rule.getRuleDetail();

        assertThat(detail.getRuleId()).isEqualTo("DR-SENT-002");
        assertThat(detail.getTitle()).isNotBlank();
        assertThat(detail.getSeverity()).isEqualTo(RuleDetailResponse.SeverityEnum.ERROR);
        assertThat(detail.getEnabled()).isTrue();
    }

    /**
     * Covers the AC1 pass scenario where there is exactly one primary custodial offence and a
     * second offence already marked concurrent, so no issue is produced.
     */
    @Test
    void ac1_single_offence_no_concurrent_consecutive_should_have_no_issues() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault")
                )
        );
        request.getResultLines().get(1).setIsConcurrent(true);

        List<ValidationIssueResult> results = rule.evaluate(request);

        assertThat(results).isEmpty();
    }

    /**
     * Covers AC2 where two non-primary custodial offences omit relationship data, so the rule
     * returns a single blocking error pointing at both affected offences.
     */
    @Test
    void ac2_multiple_offences_missing_info_should_produce_error() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3"),
                        resultLine("rl4", "IMP", "d1", "off4")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary"),
                        offence("off4", 4, "Fraud")
                )
        );
        // off1=primary (first no-info), off2+off3=noInfo (2 non-primary missing), off4=concurrent
        request.getResultLines().get(3).setIsConcurrent(true);

        List<ValidationIssueResult> results = rule.evaluate(request);

        assertThat(results).hasSize(1);
        ValidationIssueResult result = results.getFirst();
        ValidationIssue error = result.issue();
        assertThat(error.getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
        assertThat(error.getRuleId()).isEqualTo("DR-SENT-002");
        assertThat(result.errorMessage()).isEqualTo(
                "Some offences do not include details of whether they are concurrent or"
                        + " consecutive. There should be only one primary sentence for each"
                        + " defendant, therefore one result without concurrent or consecutive"
                        + " information. This affects ${defendantNames}.");
        assertThat(result.affectedDefendantName()).isEqualTo("John Smith");
        assertThat(error.getValidationLevel()).isEqualTo(ValidationIssue.ValidationLevelEnum.OFFENCE);
        assertThat(error.getAffectedOffences()).hasSize(3);
        assertThat(error.getAffectedOffences()).extracting(o -> o.getOffenceId())
                .containsExactlyInAnyOrder("off1", "off2", "off3");
        assertThat(error.getAffectedDefendants()).isNullOrEmpty();
    }

    /**
     * Covers AC3 where an offence is marked both concurrent and consecutive, producing a warning
     * against the offending count.
     */
    @Test
    void ac3_offence_with_both_concurrent_and_consecutive_should_produce_warning() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault")
                )
        );
        request.getResultLines().get(1).setIsConcurrent(true);
        request.getResultLines().get(1).setConsecutiveToOffence("off1");

        List<ValidationIssueResult> results = rule.evaluate(request);

        assertThat(results).hasSize(1);
        ValidationIssueResult result = results.getFirst();
        ValidationIssue warning = result.issue();
        assertThat(warning.getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
        assertThat(result.errorMessage()).isNull();
        assertThat(warning.getAffectedOffences()).hasSize(1);
        assertThat(warning.getAffectedOffences().get(0).getMessage()).isEqualTo(
                "This offence has both concurrent and consecutive information."
                        + " Check this is correct before sharing");
    }

    /**
     * Covers AC4 where all custodial offences carry relationship data and no primary sentence can
     * be inferred, resulting in a warning.
     */
    @Test
    void ac4_all_offences_have_info_no_primary_should_produce_warning() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault")
                )
        );
        request.getResultLines().get(0).setIsConcurrent(true);
        request.getResultLines().get(1).setConsecutiveToOffence("off1");

        List<ValidationIssueResult> results = rule.evaluate(request);

        assertThat(results).hasSize(1);
        ValidationIssueResult result = results.getFirst();
        ValidationIssue warning = result.issue();
        assertThat(warning.getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.WARNING);
        assertThat(result.errorMessage()).isNull();
        assertThat(warning.getAffectedDefendants().get(0).getMessage()).isEqualTo(
                "All offences include details of being concurrent or consecutive with no"
                        + " primary sentence. Check that this is correct before sharing");
    }

    /**
     * Verifies non-custodial result lines are ignored entirely by the rule.
     */
    @Test
    void no_custodial_sentences_should_produce_no_issues() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "EMONE", "d1", "off1"),
                        resultLine("rl2", "FINE", "d1", "off2")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault")
                )
        );

        List<ValidationIssueResult> results = rule.evaluate(request);

        assertThat(results).isEmpty();
    }

    /**
     * Verifies defendant grouping works independently so one defendant can fail AC2 without
     * creating issues for another defendant in the same hearing.
     */
    @Test
    void multiple_defendants_should_be_validated_independently() {
        // d1: off1=primary, off2+off3=noInfo (2), off4=concurrent -> AC2 error
        // d2: off5=primary, off6=concurrent -> pass
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3"),
                        resultLine("rl4", "IMP", "d1", "off4"),
                        resultLine("rl5", "IMP", "d2", "off5"),
                        resultLine("rl6", "IMP", "d2", "off6")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary"),
                        offence("off4", 4, "Fraud"),
                        offence("off5", 5, "Robbery"),
                        offence("off6", 6, "Arson")
                )
        );
        request.getResultLines().get(3).setIsConcurrent(true);
        request.getResultLines().get(5).setIsConcurrent(true);

        List<ValidationIssueResult> results = rule.evaluate(request);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().issue().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
    }

    /**
     * Verifies only configured custodial short codes participate when mixed with non-custodial
     * result lines.
     */
    @Test
    void non_custodial_short_codes_should_be_ignored() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "FINE", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary")
                )
        );
        request.getResultLines().get(2).setIsConcurrent(true);

        List<ValidationIssueResult> results = rule.evaluate(request);

        assertThat(results).isEmpty();
    }

    /**
     * Verifies a single custodial offence is skipped because there is no relationship ambiguity to
     * validate.
     */
    @Test
    void single_offence_with_custodial_sentence_should_produce_no_issues() {
        DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "IMP", "d1", "off1")),
                List.of(offence("off1", 1, "Theft"))
        );

        List<ValidationIssueResult> results = rule.evaluate(request);

        assertThat(results).isEmpty();
    }

    /**
     * Verifies that getPriority reads directly from the YAML definition without consulting
     * the override service, so startup sorting does not trigger database calls.
     */
    @Test
    void getPriority_should_not_call_override_service() {
        uk.gov.hmcts.cp.services.rules.RuleOverrideService mockOverrideService =
                mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class);
        CelValidationRule localRule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new PreprocessorRegistry(List.of(new CustodialPreprocessor())),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mockOverrideService,
                issueRecorder);

        localRule.getPriority();

        verify(mockOverrideService, never()).findOverride(anyString());
    }

    /**
     * Verifies an empty hearing payload produces no validation issues.
     */
    @Test
    void empty_result_lines_should_produce_no_issues() {
        DraftValidationRequest request = buildRequest(List.of(), List.of());

        List<ValidationIssueResult> results = rule.evaluate(request);

        assertThat(results).isEmpty();
    }

    /**
     * Verifies multiple warning conditions can fire together for the same defendant when the data
     * simultaneously matches AC3 and AC4.
     */
    @Test
    void ac3_and_ac4_can_both_fire_for_same_defendant() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault")
                )
        );
        request.getResultLines().get(0).setIsConcurrent(true);
        request.getResultLines().get(0).setConsecutiveToOffence("off2");
        request.getResultLines().get(1).setConsecutiveToOffence("off1");

        List<ValidationIssueResult> results = rule.evaluate(request);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(r -> r.issue().getSeverity())
                .containsOnly(ValidationIssue.SeverityEnum.WARNING);
    }

    /**
     * Verifies that a DEFENDANT-level condition populates affectedDefendants (not affectedOffences)
     * and sets validationLevel to DEFENDANT on the produced issue.
     */
    @Test
    void defendant_level_condition_should_populate_affected_defendants() {
        CelValidationRule defendantRule = new CelValidationRule(
                "rules/TEST-defendant-level.yaml",
                new PreprocessorRegistry(List.of(new CustodialPreprocessor())),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class),
                mock(ValidationIssueRecorder.class));

        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault")
                )
        );
        request.getResultLines().get(1).setIsConcurrent(true);
        request.getResultLines().get(1).setConsecutiveToOffence("off1");

        List<ValidationIssueResult> results = defendantRule.evaluate(request);

        assertThat(results).hasSize(1);
        ValidationIssue issue = results.getFirst().issue();
        assertThat(issue.getValidationLevel()).isEqualTo(ValidationIssue.ValidationLevelEnum.DEFENDANT);
        assertThat(issue.getAffectedDefendants()).hasSize(1);
        assertThat(issue.getAffectedDefendants().getFirst().getDefendantId()).isEqualTo("d1");
        assertThat(issue.getAffectedOffences()).isNullOrEmpty();
    }

    /**
     * Verifies that a DEFENDANT-level condition with ERROR severity preserves the DEFENDANT
     * validation level rather than silently demoting it to OFFENCE.
     */
    @Test
    void defendant_level_error_condition_should_preserve_defendant_level() {
        CelValidationRule defendantErrorRule = new CelValidationRule(
                "rules/TEST-defendant-level-error.yaml",
                new PreprocessorRegistry(List.of(new CustodialPreprocessor())),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class),
                mock(ValidationIssueRecorder.class));

        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault")
                )
        );
        request.getResultLines().get(1).setIsConcurrent(true);
        request.getResultLines().get(1).setConsecutiveToOffence("off1");

        List<ValidationIssueResult> results = defendantErrorRule.evaluate(request);

        assertThat(results).hasSize(1);
        ValidationIssue issue = results.getFirst().issue();
        assertThat(issue.getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
        assertThat(issue.getValidationLevel()).isEqualTo(ValidationIssue.ValidationLevelEnum.DEFENDANT);
        assertThat(issue.getAffectedDefendants()).hasSize(1);
        assertThat(issue.getAffectedDefendants().getFirst().getDefendantId()).isEqualTo("d1");
        assertThat(issue.getAffectedOffences()).isNullOrEmpty();
    }

    /**
     * Verifies that a YAML rule referencing an unregistered preprocessor qualifier fails fast at
     * construction time rather than deferring the failure to the first validation request.
     */
    @Test
    void constructor_should_throw_when_preprocessor_qualifier_unknown() {
        PreprocessorRegistry emptyRegistry = new PreprocessorRegistry(List.of());

        assertThatThrownBy(() -> new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                emptyRegistry,
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class),
                issueRecorder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("custodial-concurrent-consecutive");
    }

    /**
     * Verifies a triggered condition records the issue for monitoring with the rule id, condition
     * id, resolved severity and hearing id, after the issue has been built.
     */
    @Test
    void triggered_condition_should_record_issue_for_monitoring() {
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3"),
                        resultLine("rl4", "IMP", "d1", "off4")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary"),
                        offence("off4", 4, "Fraud")
                )
        );
        request.getResultLines().get(3).setIsConcurrent(true);

        rule.evaluate(request);

        verify(issueRecorder).record(eq("DR-SENT-002"), eq("AC2"),
                eq(ValidationIssue.SeverityEnum.ERROR), eq("h1"),
                anyString(), eq("Multiple offences missing info"),
                eq(ValidationIssue.ValidationLevelEnum.OFFENCE));
    }

    /**
     * Verifies a rule disabled via database override produces no issues and records nothing.
     */
    @Test
    void disabled_rule_should_not_record_any_issue() {
        RuleOverrideService disabledOverride = mock(RuleOverrideService.class);
        when(disabledOverride.findOverride("DR-SENT-002")).thenReturn(Optional.of(
                ValidationRuleEntity.builder().id("DR-SENT-002").enabled(false).build()));
        ValidationIssueRecorder localRecorder = mock(ValidationIssueRecorder.class);
        CelValidationRule disabledRule = new CelValidationRule(
                "rules/DR-SENT-002.yaml",
                new PreprocessorRegistry(List.of(new CustodialPreprocessor())),
                new CelExpressionEvaluator(),
                new MessageTemplateResolver(offenceDisplayHelper),
                offenceDisplayHelper,
                disabledOverride,
                localRecorder);
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault")
                )
        );

        List<ValidationIssueResult> issues = disabledRule.evaluate(request);

        assertThat(issues).isEmpty();
        verify(localRecorder, never()).record(anyString(), anyString(), any(), anyString(),
                anyString(), anyString(), any());
    }

    /**
     * Verifies a recorder failure is contained at the call site and never suppresses the produced
     * validation issue (observability must not alter validation results).
     */
    @Test
    void recorder_failure_should_not_suppress_issue() {
        doThrow(new RuntimeException("recorder down")).when(issueRecorder)
                .record(eq("DR-SENT-002"), eq("AC2"), any(), eq("h1"), any(), any(), any());
        DraftValidationRequest request = buildRequest(
                List.of(
                        resultLine("rl1", "IMP", "d1", "off1"),
                        resultLine("rl2", "IMP", "d1", "off2"),
                        resultLine("rl3", "IMP", "d1", "off3"),
                        resultLine("rl4", "IMP", "d1", "off4")
                ),
                List.of(
                        offence("off1", 1, "Theft"),
                        offence("off2", 2, "Assault"),
                        offence("off3", 3, "Burglary"),
                        offence("off4", 4, "Fraud")
                )
        );
        request.getResultLines().get(3).setIsConcurrent(true);

        List<ValidationIssueResult> issues = rule.evaluate(request);

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().issue().getSeverity()).isEqualTo(ValidationIssue.SeverityEnum.ERROR);
    }

}
