package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ApplicationResultBreachContext} (feature
 * 010-application-result-offence-error). Covers {@code toCelContext()}, the two named id sets,
 * and the {@code resultLabelByOffenceId} calculated-value lookup.
 */
class ApplicationResultBreachContextTest {

    private final ApplicationResultBreachContext context = new ApplicationResultBreachContext(
            "d1", "Jamie Smith", "off1", "Legal Aid Withdrawn");

    @Test
    void toCelContext_shouldReturnHasBreachOne() {
        assertThat(context.toCelContext()).isEqualTo(Map.of("hasBreach", 1L));
    }

    @Test
    void getOffenceIdSet_breachOffenceId_shouldReturnSingletonListOfOffenceId() {
        assertThat(context.getOffenceIdSet("breachOffenceId")).containsExactly("off1");
    }

    @Test
    void getOffenceIdSet_unknownSetName_shouldThrow() {
        assertThatThrownBy(() -> context.getOffenceIdSet("somethingElse"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allOffenceIds_shouldReturnSingletonListOfOffenceId() {
        assertThat(context.allOffenceIds()).containsExactly("off1");
    }

    @Test
    void getDefendantIdSet_defendantId_shouldReturnSingletonListOfDefendantId() {
        assertThat(context.getDefendantIdSet("defendantId")).containsExactly("d1");
    }

    @Test
    void getDefendantIdSet_unknownSetName_shouldThrow() {
        assertThatThrownBy(() -> context.getDefendantIdSet("somethingElse"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCalculatedValue_resultLabelByOffenceId_ownOffenceId_shouldReturnResultLabel() {
        assertThat(context.getCalculatedValue("resultLabelByOffenceId", "off1"))
                .isEqualTo("Legal Aid Withdrawn");
    }

    @Test
    void getCalculatedValue_resultLabelByOffenceId_differentOffenceId_shouldReturnNull() {
        assertThat(context.getCalculatedValue("resultLabelByOffenceId", "off2")).isNull();
    }

    @Test
    void getCalculatedValue_unknownSetName_shouldThrow() {
        assertThatThrownBy(() -> context.getCalculatedValue("somethingElse", "off1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defendantName_and_defendantId_shouldBeAccessibleFromRecordComponents() {
        assertThat(context.defendantName()).isEqualTo("Jamie Smith");
        assertThat(context.defendantId()).isEqualTo("d1");
    }

    @Test
    void record_shouldExposeResultLabelAndOffenceId() {
        assertThat(context.resultLabel()).isEqualTo("Legal Aid Withdrawn");
        assertThat(context.offenceId()).isEqualTo("off1");
    }

    @Test
    void blankDefendantName_shouldBeAcceptedAsIs_forLaterAggregationLayerFiltering() {
        // "" -- not null, not "Unknown" -- is the deliberate fallback for an unresolvable
        // defendant (research.md R8 / data-model.md); the context itself does not filter it.
        ApplicationResultBreachContext unresolved = new ApplicationResultBreachContext(
                "d2", "", "off2", "Application refused");

        assertThat(unresolved.defendantName()).isEmpty();
    }
}
