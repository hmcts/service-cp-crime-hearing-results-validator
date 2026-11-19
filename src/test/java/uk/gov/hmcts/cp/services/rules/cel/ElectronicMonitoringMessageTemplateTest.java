package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.services.rules.OffenceDisplayHelper;

/**
 * Verifies that the DR-EMON-017 YAML errorMessageTemplate expands correctly via
 * {@link MessageTemplateResolver#resolveDefendantNames} for single-defendant,
 * two-defendant, and three-defendant hearings.
 */
class ElectronicMonitoringMessageTemplateTest {

    private static final RuleDefinition RULE_DEFINITION =
            RuleDefinitionLoader.load("rules/DR-EMON-017.yaml");

    private static final String ERROR_MSG_TEMPLATE =
            RULE_DEFINITION.conditions().getFirst().errorMessageTemplate();

    private static final String BASE_ERROR_MSG =
            "Electronic monitoring is in place on offences. Add a result that continues"
                    + " electronic monitoring, or end it with 'Electronic Monitoring End"
                    + " - notify contractor (EMONE)'.";

    private final MessageTemplateResolver resolver =
            new MessageTemplateResolver(new OffenceDisplayHelper());

    @Nested
    @DisplayName("Single-defendant hearing — 'This affects' clause omitted")
    class SingleDefendantHearing {

        @Test
        @DisplayName("errorMessageTemplate strips the defendant-name clause for a single-defendant hearing")
        void singleDefendant_should_omitThisAffectsClause() {
            String resolved = resolver.resolveDefendantNames(
                    ERROR_MSG_TEMPLATE, List.of("John Smith"), 1);

            assertThat(resolved).isEqualToIgnoringWhitespace(BASE_ERROR_MSG);
            assertThat(resolved).doesNotContain("This affects");
        }
    }

    @Nested
    @DisplayName("Two-defendant hearing — names joined with 'and'")
    class TwoDefendantHearing {

        @Test
        @DisplayName("errorMessageTemplate appends 'This affects A and B' for two defendants")
        void twoDefendants_should_appendAndSeparatedNames() {
            String resolved = resolver.resolveDefendantNames(
                    ERROR_MSG_TEMPLATE, List.of("John Smith", "Jane Jones"), 2);

            assertThat(resolved).contains("This affects John Smith and Jane Jones");
        }
    }

    @Nested
    @DisplayName("Three-defendant hearing — comma with 'and' before last name (spec AC4)")
    class ThreeDefendantHearing {

        @Test
        @DisplayName("errorMessageTemplate appends 'This affects A, B and C' for three defendants")
        void threeDefendants_should_appendCommaAndBeforeLastName() {
            String resolved = resolver.resolveDefendantNames(
                    ERROR_MSG_TEMPLATE, List.of("John Smith", "Jane Jones", "Sam Patel"), 3);

            assertThat(resolved).contains("This affects John Smith, Jane Jones and Sam Patel");
        }
    }

    @Nested
    @DisplayName("Five-defendant hearing — four commas and 'and' before last (spec AC4)")
    class FiveDefendantHearing {

        @Test
        @DisplayName("errorMessageTemplate appends 'This affects A, B, C, D and E' for five defendants")
        void fiveDefendants_should_appendCommaAndBeforeLastName() {
            String resolved = resolver.resolveDefendantNames(
                    ERROR_MSG_TEMPLATE,
                    List.of("John Smith", "Jane Jones", "Sam Patel", "Alex Lee", "Kim Davis"),
                    5);

            assertThat(resolved).contains(
                    "This affects John Smith, Jane Jones, Sam Patel, Alex Lee and Kim Davis");
        }
    }
}
