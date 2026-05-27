package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.RuleDetailResponse;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;
import uk.gov.hmcts.cp.services.rules.ValidationIssueResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;

/**
 * Focused unit tests for the DR-SENT-002 CEL validation rule implementation.
 */
class CelValidationRuleTest {

    private final OffenceDisplayHelper offenceDisplayHelper = new OffenceDisplayHelper();
    private final CelValidationRule rule = new CelValidationRule(
            "rules/DR-SENT-002.yaml",
            new PreprocessorRegistry(List.of(new CustodialPreprocessor())),
            new CelExpressionEvaluator(),
            new MessageTemplateResolver(offenceDisplayHelper),
            offenceDisplayHelper,
            mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class));

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
        assertThat(result.errorMessage()).contains("Some offences do not include details");
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
        assertThat(warning.getAffectedOffences()).hasSize(2);
        assertThat(warning.getAffectedOffences()).allSatisfy(ao -> {
            assertThat(ao.getMessage()).contains("John Smith");
            assertThat(ao.getMessage()).contains("both concurrent and consecutive");
        });
        assertThat(warning.getAffectedOffences().get(1).getMessage()).contains("Offence 2 (URN:32AH9105826)");
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
        assertThat(warning.getAffectedDefendants().get(0).getMessage()).contains("no primary sentence");
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
                mockOverrideService);

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
                mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class));

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
                mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("custodial-concurrent-consecutive");
    }

    // ── T009a: framework changes — stringVariables() and affectedDefendants ──

    /**
     * A minimal test-only preprocessor that always returns one {@link CurfewPeriodContext}
     * with a known expected end date. Used to verify the T009 framework changes without
     * depending on the full {@link CurfewPeriodPreprocessor} implementation.
     */
    private static class TestCurfewContextPreprocessor implements ValidationPreprocessor {

        private final String defendantId;

        TestCurfewContextPreprocessor(final String defendantId) {
            this.defendantId = defendantId;
        }

        @Override
        public String type() {
            return "test-curfew-context";
        }

        @Override
        public Map<String, ? extends RuleEvaluationContext> preprocess(
                final uk.gov.hmcts.cp.openapi.model.DraftValidationRequest request,
                final PreprocessingDefinition config) {
            final CurfewPeriodContext ctx = new CurfewPeriodContext(
                    defendantId, "Test Defendant", "off1", 1L, "30/07/2026");
            return Map.of("key:d1:off1", ctx);
        }
    }

    /**
     * A preprocessor variant whose context returns null from {@code defendantId()} —
     * simulates the existing backward-compatible contexts (CommunityOrderContext, etc.).
     */
    private static class TestNullDefendantContextPreprocessor implements ValidationPreprocessor {

        @Override
        public String type() {
            return "test-curfew-context";
        }

        @Override
        public Map<String, ? extends RuleEvaluationContext> preprocess(
                final uk.gov.hmcts.cp.openapi.model.DraftValidationRequest request,
                final PreprocessingDefinition config) {
            // Wrap CurfewPeriodContext through an anonymous RuleEvaluationContext where
            // defendantId() returns null to simulate the existing backward-compatible contexts.
            final RuleEvaluationContext nullDefendantCtx = new RuleEvaluationContext() {
                @Override
                public Map<String, Long> toCelContext() {
                    return Map.of("violationCount", 1L);
                }

                @Override
                public List<String> getOffenceIdSet(final String setName) {
                    return List.of("off1");
                }

                @Override
                public String defendantName() {
                    return "Test Defendant";
                }

                @Override
                public List<String> allOffenceIds() {
                    return List.of("off1");
                }

                @Override
                public Map<String, String> stringVariables() {
                    return Map.of("expectedEndDate", "30/07/2026");
                }
                // defendantId() returns null via interface default
            };
            return Map.of("null-def-key", nullDefendantCtx);
        }
    }

    @Nested
    @DisplayName("T009a — stringVariables() and affectedDefendants framework changes")
    class StringVariablesAndAffectedDefendants {

        private final DraftValidationRequest request = buildRequest(
                List.of(resultLine("rl1", "CUR", "d1", "off1")),
                List.of(offence("off1", 1, "Theft")));

        /**
         * Verifies that when a context returns {@code stringVariables()} with an
         * {@code expectedEndDate} key, the resolved message contains the date value — not the
         * literal {@code ${expectedEndDate}} placeholder. This fails before T009 because
         * {@code CelValidationRule} still calls the 5-arg {@code resolve()} which does not
         * substitute string variables.
         */
        @Test
        @DisplayName("message should contain resolved expectedEndDate from stringVariables()")
        void stringVariables_should_be_resolved_in_message() {
            final CelValidationRule testRule = new CelValidationRule(
                    "rules/TEST-curfew-string-var.yaml",
                    new PreprocessorRegistry(List.of(new TestCurfewContextPreprocessor("d1"))),
                    new CelExpressionEvaluator(),
                    new MessageTemplateResolver(offenceDisplayHelper),
                    offenceDisplayHelper,
                    mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class));

            final List<ValidationIssueResult> results = testRule.evaluate(request);

            assertThat(results).hasSize(1);
            final ValidationIssue issue = results.getFirst().issue();
            // The per-offence message on the affectedOffence should contain the resolved date.
            assertThat(issue.getAffectedOffences()).isNotEmpty();
            assertThat(issue.getAffectedOffences().getFirst().getMessage())
                    .contains("30/07/2026")
                    .doesNotContain("${expectedEndDate}");
        }

        /**
         * Verifies that the errorMessage (top-level summary) also resolves {@code ${expectedEndDate}}.
         */
        @Test
        @DisplayName("errorMessage should contain resolved expectedEndDate from stringVariables()")
        void stringVariables_should_be_resolved_in_error_message() {
            final CelValidationRule testRule = new CelValidationRule(
                    "rules/TEST-curfew-string-var.yaml",
                    new PreprocessorRegistry(List.of(new TestCurfewContextPreprocessor("d1"))),
                    new CelExpressionEvaluator(),
                    new MessageTemplateResolver(offenceDisplayHelper),
                    offenceDisplayHelper,
                    mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class));

            final List<ValidationIssueResult> results = testRule.evaluate(request);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().errorMessage())
                    .contains("30/07/2026")
                    .doesNotContain("${expectedEndDate}");
        }

        /**
         * Verifies that when an OFFENCE-level ERROR condition fires and the context returns a
         * non-null {@code defendantId()}, the emitted {@code ValidationIssue} carries
         * {@code affectedDefendants} containing that defendant id. Fails before T009 because
         * {@code CelValidationRule} does not yet set {@code affectedDefendants} for OFFENCE-level
         * conditions.
         */
        @Test
        @DisplayName("affectedDefendants should be populated when defendantId() is non-null and severity is ERROR")
        void offence_level_error_with_non_null_defendantId_should_set_affectedDefendants() {
            final CelValidationRule testRule = new CelValidationRule(
                    "rules/TEST-curfew-string-var.yaml",
                    new PreprocessorRegistry(List.of(new TestCurfewContextPreprocessor("d1"))),
                    new CelExpressionEvaluator(),
                    new MessageTemplateResolver(offenceDisplayHelper),
                    offenceDisplayHelper,
                    mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class));

            final List<ValidationIssueResult> results = testRule.evaluate(request);

            assertThat(results).hasSize(1);
            final ValidationIssue issue = results.getFirst().issue();
            assertThat(issue.getAffectedDefendants()).isNotEmpty();
            assertThat(issue.getAffectedDefendants().getFirst().getDefendantId()).isEqualTo("d1");
        }

        /**
         * Verifies backward compatibility: when the context returns {@code null} from
         * {@code defendantId()} (the interface default), {@code affectedDefendants} is NOT set
         * on the issued {@code ValidationIssue}.
         */
        @Test
        @DisplayName("affectedDefendants should NOT be set when defendantId() returns null (backward compatible)")
        void offence_level_error_with_null_defendantId_should_not_set_affectedDefendants() {
            final CelValidationRule testRule = new CelValidationRule(
                    "rules/TEST-curfew-string-var.yaml",
                    new PreprocessorRegistry(List.of(new TestNullDefendantContextPreprocessor())),
                    new CelExpressionEvaluator(),
                    new MessageTemplateResolver(offenceDisplayHelper),
                    offenceDisplayHelper,
                    mock(uk.gov.hmcts.cp.services.rules.RuleOverrideService.class));

            final List<ValidationIssueResult> results = testRule.evaluate(request);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().issue().getAffectedDefendants()).isNullOrEmpty();
        }
    }

}
