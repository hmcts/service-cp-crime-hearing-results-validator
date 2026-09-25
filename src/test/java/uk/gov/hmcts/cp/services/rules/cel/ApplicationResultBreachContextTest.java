package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ApplicationResultBreachContext} (feature
 * 010-application-result-offence-error). Covers {@code toCelContext()}, the two named id sets,
 * and the {@code resultLabelByOffenceId} per-offence and hearing-wide calculated-value lookups
 * (AC2A/AC2B).
 */
class ApplicationResultBreachContextTest {

    private final ApplicationResultBreachContext context = new ApplicationResultBreachContext(
            "off1", List.of("Legal Aid Withdrawn"), "Legal Aid Withdrawn", 1, "d1", "Jamie Smith");

    @Test
    void toCelContext_singleHearingWideLabel_shouldExposeHasBreachAndLabelCountOfOne() {
        assertThat(context.toCelContext())
                .isEqualTo(Map.of("hasBreach", 1L, "offenceResultLabelCount", 1L,
                        "globalResultLabelCount", 1L));
    }

    @Test
    void toCelContext_multipleHearingWideLabels_shouldExposeHearingWideLabelCount() {
        // Drives DR-APP-009's singular/plural page-level wording split ("is an application
        // result" vs "are application results") -- hearing-wide, not this offence's own count.
        ApplicationResultBreachContext multiOffence = new ApplicationResultBreachContext(
                "off1", List.of("Legal Aid Withdrawn"),
                "Legal Aid Withdrawn, Application refused, Granted", 3, "d1", "Jamie Smith");

        assertThat(multiOffence.toCelContext())
                .isEqualTo(Map.of("hasBreach", 1L, "offenceResultLabelCount", 1L,
                        "globalResultLabelCount", 3L));
    }

    @Test
    void toCelContext_multipleLabelsOnThisOffence_shouldExposeOffenceLabelCount() {
        // Drives DR-APP-009's singular/plural inline wording ("It is an application result" vs
        // "They are application results") -- this offence's own count, not the hearing-wide one.
        ApplicationResultBreachContext multi = new ApplicationResultBreachContext(
                "off1", List.of("Legal Aid Withdrawn", "Application refused"),
                "Legal Aid Withdrawn, Application refused", 2, "d1", "Jamie Smith");

        assertThat(multi.toCelContext())
                .isEqualTo(Map.of("hasBreach", 1L, "offenceResultLabelCount", 2L,
                        "globalResultLabelCount", 2L));
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
    void getCalculatedValue_multipleResultLabelsOnSameOffence_shouldReturnCommaJoinedList() {
        ApplicationResultBreachContext multi = new ApplicationResultBreachContext(
                "off1", List.of("Legal Aid Withdrawn", "Application refused"),
                "Legal Aid Withdrawn, Application refused", 2, "d1", "Jamie Smith");

        assertThat(multi.getCalculatedValue("resultLabelByOffenceId", "off1"))
                .isEqualTo("Legal Aid Withdrawn, Application refused");
    }

    @Test
    void getGlobalCalculatedValue_resultLabelByOffenceId_shouldReturnHearingWideLabelList() {
        ApplicationResultBreachContext multiOffence = new ApplicationResultBreachContext(
                "off1", List.of("Legal Aid Withdrawn"),
                "Legal Aid Withdrawn, Application refused", 2, "d1", "Jamie Smith");

        assertThat(multiOffence.getGlobalCalculatedValue("resultLabelByOffenceId"))
                .isEqualTo("Legal Aid Withdrawn, Application refused");
    }

    @Test
    void getGlobalCalculatedValue_unknownSetName_shouldThrow() {
        assertThatThrownBy(() -> context.getGlobalCalculatedValue("somethingElse"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defendantName_and_defendantId_shouldBeAccessibleFromRecordComponents() {
        assertThat(context.defendantName()).isEqualTo("Jamie Smith");
        assertThat(context.defendantId()).isEqualTo("d1");
    }

    @Test
    void record_shouldExposeResultLabelsAndOffenceId() {
        assertThat(context.resultLabels()).containsExactly("Legal Aid Withdrawn");
        assertThat(context.offenceId()).isEqualTo("off1");
    }

    @Test
    void blankDefendantName_shouldBeAcceptedAsIs_forLaterAggregationLayerFiltering() {
        // "" -- not null, not "Unknown" -- is the deliberate fallback for an unresolvable
        // defendant (research.md R8 / data-model.md); the context itself does not filter it.
        ApplicationResultBreachContext unresolved = new ApplicationResultBreachContext(
                "off2", List.of("Application refused"), "Application refused", 1, "d2", "");

        assertThat(unresolved.defendantName()).isEmpty();
    }
}
