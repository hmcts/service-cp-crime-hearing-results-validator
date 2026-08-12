package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

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
}
