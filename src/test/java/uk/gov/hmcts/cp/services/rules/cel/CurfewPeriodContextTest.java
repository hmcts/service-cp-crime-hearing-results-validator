package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CurfewPeriodContext}.
 */
class CurfewPeriodContextTest {

    private static final String DEFENDANT_ID = "d1";
    private static final String DEFENDANT_NAME = "John Smith";
    private static final String OFFENCE_ID = "off1";
    private static final String EXPECTED_END_DATE = "30/07/2026";

    private final CurfewPeriodContext context = new CurfewPeriodContext(
            DEFENDANT_ID, DEFENDANT_NAME, OFFENCE_ID, 1L, EXPECTED_END_DATE);

    @Nested
    @DisplayName("toCelContext()")
    class ToCelContext {

        @Test
        @DisplayName("returns violationCount = 1 as the only CEL variable")
        void toCelContext_should_return_violation_count_one() {
            Map<String, Long> cel = context.toCelContext();

            assertThat(cel).containsExactly(Map.entry("violationCount", 1L));
        }

        @Test
        @DisplayName("CEL map is always 1L even when constructed with a different value")
        void toCelContext_violation_count_reflects_constructor_value() {
            CurfewPeriodContext ctx = new CurfewPeriodContext(DEFENDANT_ID, DEFENDANT_NAME, OFFENCE_ID, 1L, EXPECTED_END_DATE);

            assertThat(ctx.toCelContext().get("violationCount")).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet()")
    class GetOffenceIdSet {

        @Test
        @DisplayName("returns singleton list for the standard 'violatedOffenceIds' set name")
        void getOffenceIdSet_should_return_singleton_for_violatedOffenceIds() {
            assertThat(context.getOffenceIdSet("violatedOffenceIds")).containsExactly(OFFENCE_ID);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for unknown set name")
        void getOffenceIdSet_should_throw_for_unknown_set_name() {
            assertThatThrownBy(() -> context.getOffenceIdSet("someOtherSetName"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown offence set");
        }
    }

    @Nested
    @DisplayName("stringVariables()")
    class StringVariables {

        @Test
        @DisplayName("returns map containing expectedEndDate key with formatted date value")
        void stringVariables_should_return_expectedEndDate() {
            Map<String, String> vars = context.stringVariables();

            assertThat(vars).containsEntry("expectedEndDate", EXPECTED_END_DATE);
        }

        @Test
        @DisplayName("stringVariables map is not empty")
        void stringVariables_should_not_be_empty() {
            assertThat(context.stringVariables()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("defendantId()")
    class DefendantId {

        @Test
        @DisplayName("returns the defendant id injected at construction")
        void defendantId_should_return_injected_id() {
            assertThat(context.defendantId()).isEqualTo(DEFENDANT_ID);
        }

        @Test
        @DisplayName("defendantId is distinct from the interface default (null)")
        void defendantId_should_not_be_null() {
            assertThat(context.defendantId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("allOffenceIds()")
    class AllOffenceIds {

        @Test
        @DisplayName("returns singleton list containing the offence id")
        void allOffenceIds_should_return_singleton() {
            assertThat(context.allOffenceIds()).containsExactly(OFFENCE_ID);
        }
    }

    @Nested
    @DisplayName("defendantName()")
    class DefendantNameMethod {

        @Test
        @DisplayName("returns the defendant display name injected at construction")
        void defendantName_should_return_injected_name() {
            assertThat(context.defendantName()).isEqualTo(DEFENDANT_NAME);
        }
    }

    @Nested
    @DisplayName("populateAffectedDefendantsOnOffenceError()")
    class PopulateAffectedDefendants {

        @Test
        @DisplayName("returns true — CurfewPeriodContext opts in to affectedDefendants on OFFENCE-level ERRORs")
        void populateAffectedDefendantsOnOffenceError_should_return_true() {
            assertThat(context.populateAffectedDefendantsOnOffenceError()).isTrue();
        }
    }
}
