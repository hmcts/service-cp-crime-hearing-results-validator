package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleDefinitionTest {

    @Test
    void loadFromYaml_should_parse_rule_id_and_title() {
        RuleDefinition rule = RuleDefinitionLoader.load("rules/DR-SENT-002.yaml");

        assertThat(rule.getId()).isEqualTo("DR-SENT-002");
        assertThat(rule.getTitle()).isEqualTo("Custodial sentence concurrent/consecutive check");
        assertThat(rule.getDescription()).contains("concurrent/consecutive");
        assertThat(rule.getPriority()).isEqualTo(1000);
        assertThat(rule.isEnabled()).isTrue();
    }

    @Test
    void loadFromYaml_should_parse_preprocessing() {
        RuleDefinition rule = RuleDefinitionLoader.load("rules/DR-SENT-002.yaml");

        PreprocessingDefinition preprocessing = rule.getPreprocessing();
        assertThat(preprocessing).isNotNull();
        assertThat(preprocessing.getType()).isEqualTo("custodial-concurrent-consecutive");
        assertThat(preprocessing.getFilterShortCodes()).containsExactlyInAnyOrder(
                "IMP", "DTO", "YOI", "extdvs", "extdvsu", "extivs",
                "STSDY", "specc", "speccc", "speccd");
        assertThat(preprocessing.getGroupBy()).isEqualTo("defendant-then-offence");
        assertThat(preprocessing.getSkipWhenGroupCount()).isEqualTo(1);
    }

    @Test
    void loadFromYaml_should_parse_conditions() {
        RuleDefinition rule = RuleDefinitionLoader.load("rules/DR-SENT-002.yaml");

        assertThat(rule.getConditions()).hasSize(3);

        ConditionDefinition ac3 = rule.getConditions().get(0);
        assertThat(ac3.getId()).isEqualTo("AC3");
        assertThat(ac3.getExpression()).isEqualTo("hasBothCount > 0");
        assertThat(ac3.getSeverity()).isEqualTo("WARNING");
        assertThat(ac3.getMessageTemplate()).contains("both concurrent and consecutive");
        assertThat(ac3.getAffectedOffenceSet()).isEqualTo("hasBothOffenceIds");

        ConditionDefinition ac2 = rule.getConditions().get(1);
        assertThat(ac2.getId()).isEqualTo("AC2");
        assertThat(ac2.getExpression()).isEqualTo("noInfoCount > 1");
        assertThat(ac2.getSeverity()).isEqualTo("ERROR");

        ConditionDefinition ac4 = rule.getConditions().get(2);
        assertThat(ac4.getId()).isEqualTo("AC4");
        assertThat(ac4.getExpression()).isEqualTo("noInfoCount == 0 && hasPrimaryCount == 0");
        assertThat(ac4.getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void loadFromYaml_with_missing_file_should_throw() {
        assertThatThrownBy(() -> RuleDefinitionLoader.load("rules/NONEXISTENT.yaml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to load rule definition");
    }

    @Test
    void loadFromYaml_without_rule_key_should_throw() {
        assertThatThrownBy(() -> RuleDefinitionLoader.load("rules/no-rule-key.yaml"))
                .isInstanceOf(Exception.class);
    }
}
