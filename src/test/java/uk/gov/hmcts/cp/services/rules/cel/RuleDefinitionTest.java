package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;

/**
 * Tests for loading YAML rule definitions into {@link RuleDefinition} objects.
 */
class RuleDefinitionTest {

    /**
     * Verifies the DR-SENT-001 YAML file exposes the expected top-level metadata fields.
     */
    @Test
    void loadFromYaml_should_parse_rule_id_and_title() {
        RuleDefinition rule = RuleDefinitionLoader.load("rules/DR-SENT-001.yaml");

        assertThat(rule.id()).isEqualTo("DR-SENT-001");
        assertThat(rule.title()).isEqualTo("Custodial sentence concurrent/consecutive check");
        assertThat(rule.description()).contains("concurrent/consecutive");
        assertThat(rule.priority()).isEqualTo(1000);
        assertThat(rule.enabled()).isTrue();
    }

    /**
     * Verifies the YAML preprocessing block is parsed with the expected type, short codes and
     * grouping settings.
     */
    @Test
    void loadFromYaml_should_parse_preprocessing() {
        RuleDefinition rule = RuleDefinitionLoader.load("rules/DR-SENT-001.yaml");

        PreprocessingDefinition preprocessing = rule.preprocessing();
        assertThat(preprocessing).isNotNull();
        assertThat(preprocessing.type()).isEqualTo("custodial-concurrent-consecutive");
        assertThat(preprocessing.filterShortCodes()).containsExactlyInAnyOrder(
                "IMP", "DTO", "YOI", "extdvs", "extdvsu", "extivs",
                "STSDY", "specc", "speccc", "speccd");
        assertThat(preprocessing.groupBy()).isEqualTo("defendant-then-offence");
        assertThat(preprocessing.skipWhenGroupCount()).isEqualTo(1);
    }

    /**
     * Verifies each configured acceptance condition is loaded with the expected expression,
     * severity and affected offence set.
     */
    @Test
    void loadFromYaml_should_parse_conditions() {
        RuleDefinition rule = RuleDefinitionLoader.load("rules/DR-SENT-001.yaml");

        assertThat(rule.conditions()).hasSize(3);

        ConditionDefinition ac3 = rule.conditions().get(0);
        assertThat(ac3.id()).isEqualTo("AC3");
        assertThat(ac3.expression()).isEqualTo("hasBothCount > 0");
        assertThat(ac3.severity()).isEqualTo("WARNING");
        assertThat(ac3.messageTemplate()).contains("both concurrent and consecutive");
        assertThat(ac3.errorMessageTemplate()).isNull();
        assertThat(ac3.affectedOffenceSet()).isEqualTo("hasBothOffenceIds");
        assertThat(ac3.validationLevel()).isEqualTo(ValidationLevel.OFFENCE);

        ConditionDefinition ac2 = rule.conditions().get(1);
        assertThat(ac2.id()).isEqualTo("AC2");
        assertThat(ac2.expression()).isEqualTo("noInfoCount > 0");
        assertThat(ac2.severity()).isEqualTo("ERROR");
        assertThat(ac2.errorMessageTemplate()).contains("Some offences do not include details");
        assertThat(ac2.affectedOffenceSet()).isEqualTo("allNoInfoOffenceIds");
        assertThat(ac2.validationLevel()).isEqualTo(ValidationLevel.OFFENCE);

        ConditionDefinition ac4 = rule.conditions().get(2);
        assertThat(ac4.id()).isEqualTo("AC4");
        assertThat(ac4.expression()).isEqualTo("noInfoCount == 0 && hasPrimaryCount == 0");
        assertThat(ac4.severity()).isEqualTo("WARNING");
        assertThat(ac4.validationLevel()).isEqualTo(ValidationLevel.DEFENDANT);
    }

    /**
     * Verifies a missing YAML resource is surfaced as a rule-loading failure.
     */
    @Test
    void loadFromYaml_with_missing_file_should_throw() {
        assertThatThrownBy(() -> RuleDefinitionLoader.load("rules/NONEXISTENT.yaml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to load rule definition");
    }

    /**
     * Verifies malformed YAML without the required top-level {@code rule} key is rejected.
     */
    @Test
    void loadFromYaml_without_rule_key_should_throw() {
        assertThatThrownBy(() -> RuleDefinitionLoader.load("rules/no-rule-key.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain a top-level 'rule' key");
    }

    /**
     * Verifies DR-COEW-005 loads all 7 conditions (4 order-end-date AC2 checks plus the 3
     * duration-mismatch checks) and that the duration conditions parse {@code calculatedValueSet}
     * to the expected named set.
     */
    @Test
    void loadFromYaml_should_parse_duration_mismatch_conditions_with_calculatedValueSet() {
        RuleDefinition rule = RuleDefinitionLoader.load("rules/DR-COEW-005.yaml");

        assertThat(rule.conditions()).hasSize(7);

        ConditionDefinition durCur = rule.conditions().get(4);
        assertThat(durCur.id()).isEqualTo("DUR-CUR");
        assertThat(durCur.expression()).isEqualTo("curDurationMismatchCount > 0");
        assertThat(durCur.severity()).isEqualTo("ERROR");
        assertThat(durCur.messageTemplate()).contains("${calculatedEndDate}");
        assertThat(durCur.errorMessageTemplate()).contains("${defendantNames}");
        assertThat(durCur.affectedOffenceSet()).isEqualTo("curDurationMismatchOffenceIds");
        assertThat(durCur.calculatedValueSet()).isEqualTo("curCalculatedEndDateByOffenceId");
        assertThat(durCur.validationLevel()).isEqualTo(ValidationLevel.OFFENCE);

        ConditionDefinition durCure = rule.conditions().get(5);
        assertThat(durCure.id()).isEqualTo("DUR-CURE");
        assertThat(durCure.expression()).isEqualTo("cureDurationMismatchCount > 0");
        assertThat(durCure.affectedOffenceSet()).isEqualTo("cureDurationMismatchOffenceIds");
        assertThat(durCure.calculatedValueSet()).isEqualTo("cureCalculatedEndDateByOffenceId");

        ConditionDefinition durAar = rule.conditions().get(6);
        assertThat(durAar.id()).isEqualTo("DUR-AAR");
        assertThat(durAar.expression()).isEqualTo("aarDurationMismatchCount > 0");
        assertThat(durAar.affectedOffenceSet()).isEqualTo("aarDurationMismatchOffenceIds");
        assertThat(durAar.calculatedValueSet()).isEqualTo("aarCalculatedEndDateByOffenceId");

        // Pre-existing AC2 conditions must not have calculatedValueSet set.
        ConditionDefinition ac2a = rule.conditions().get(0);
        assertThat(ac2a.id()).isEqualTo("AC2a");
        assertThat(ac2a.calculatedValueSet()).isNull();
    }

    /**
     * Verifies DR-YRO-004 loads all 5 conditions (3 existing AC2 checks plus the 2 new
     * duration-mismatch conditions) and that the duration conditions parse
     * {@code calculatedValueSet} to the expected named set (DD-42850).
     */
    @Test
    void loadFromYaml_should_parse_yro_duration_mismatch_conditions_with_calculatedValueSet() {
        RuleDefinition rule = RuleDefinitionLoader.load("rules/DR-YRO-004.yaml");

        assertThat(rule.conditions()).hasSize(5);

        ConditionDefinition durYrc2 = rule.conditions().get(3);
        assertThat(durYrc2.id()).isEqualTo("DUR-YRC2");
        assertThat(durYrc2.expression()).isEqualTo("curDurationMismatchCount > 0");
        assertThat(durYrc2.severity()).isEqualTo("ERROR");
        assertThat(durYrc2.messageTemplate()).contains("${calculatedEndDate}");
        assertThat(durYrc2.errorMessageTemplate()).contains("${defendantNames}");
        assertThat(durYrc2.affectedOffenceSet()).isEqualTo("curDurationMismatchOffenceIds");
        assertThat(durYrc2.calculatedValueSet()).isEqualTo("curCalculatedEndDateByOffenceId");
        assertThat(durYrc2.validationLevel()).isEqualTo(ValidationLevel.OFFENCE);

        ConditionDefinition durYrc1 = rule.conditions().get(4);
        assertThat(durYrc1.id()).isEqualTo("DUR-YRC1");
        assertThat(durYrc1.expression()).isEqualTo("cureDurationMismatchCount > 0");
        assertThat(durYrc1.affectedOffenceSet()).isEqualTo("cureDurationMismatchOffenceIds");
        assertThat(durYrc1.calculatedValueSet()).isEqualTo("cureCalculatedEndDateByOffenceId");

        // Pre-existing AC2 conditions must not have calculatedValueSet set.
        ConditionDefinition ac2a = rule.conditions().get(0);
        assertThat(ac2a.id()).isEqualTo("AC2a");
        assertThat(ac2a.calculatedValueSet()).isNull();
    }

    /**
     * Guards the sentence-removal behaviour that {@link MessageTemplateResolver#resolveDefendantNames}
     * relies on for single-defendant-hearing suppression (AC1-AC4): running every rule's real
     * templates containing {@code ${defendantNames}} through the single-defendant branch must fully
     * remove both the token and the "This affects" clause it belongs to, leaving a well-formed,
     * non-empty message with no leftover double punctuation. A future template that embeds the
     * token mid-sentence, or terminates its clause with something other than a full stop, would
     * otherwise leak a raw token or a mangled message into production error responses with no
     * other test in the suite catching it -- this test exists so that drift fails loudly here
     * instead. Rule files are discovered from the classpath (the same {@code rules/DR-*.yaml} scan
     * {@link uk.gov.hmcts.cp.config.ValidationRuleAutoConfiguration} uses) rather than hard-coded,
     * so every current and future rule template is covered automatically.
     */
    @Test
    void defendantNames_clause_should_be_fully_removable_for_every_rule_template() throws IOException {
        final MessageTemplateResolver resolver = new MessageTemplateResolver(new OffenceDisplayHelper());
        final Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:rules/*.yaml");
        final List<String> ruleFiles = new ArrayList<>();
        for (final Resource resource : resources) {
            final String filename = resource.getFilename();
            if (filename != null && filename.startsWith("DR-")) {
                ruleFiles.add("rules/" + filename);
            }
        }
        assertThat(ruleFiles).isNotEmpty();

        for (final String ruleFile : ruleFiles) {
            final RuleDefinition rule = RuleDefinitionLoader.load(ruleFile);
            for (final ConditionDefinition condition : rule.conditions()) {
                final String[] templates = {
                        condition.messageTemplate(), condition.errorMessageTemplate()};
                for (final String template : templates) {
                    if (template != null && template.contains("${defendantNames}")) {
                        final String stripped = resolver.resolveDefendantNames(
                                template, List.of("Any Name"), 1);
                        assertThat(stripped)
                                .as("rule=%s condition=%s template=%s",
                                        rule.id(), condition.id(), template)
                                .isNotBlank()
                                .doesNotContain("${defendantNames}")
                                .doesNotContain("This affects")
                                .doesNotContain("..");
                    }
                }
            }
        }
    }
}
