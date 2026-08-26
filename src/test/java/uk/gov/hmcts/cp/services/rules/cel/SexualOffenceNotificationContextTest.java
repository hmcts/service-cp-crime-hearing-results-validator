package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SexualOffenceNotificationContext}, the per-offence context for the
 * DR-SEX-008 sexual-offence notification-requirement rule.
 */
class SexualOffenceNotificationContextTest {

    @Test
    void toCelContext_adultMissingNotification_shouldMapBothFieldsToZero() {
        SexualOffenceNotificationContext context =
                new SexualOffenceNotificationContext("off1", "Jamie Smith", false, false);

        assertThat(context.toCelContext()).isEqualTo(Map.of(
                "isYouth", 0L,
                "hasQualifyingNotification", 0L));
    }

    @Test
    void toCelContext_adultWithNotification_shouldMapIsYouthZeroAndNotificationOne() {
        SexualOffenceNotificationContext context =
                new SexualOffenceNotificationContext("off1", "Jamie Smith", false, true);

        assertThat(context.toCelContext()).isEqualTo(Map.of(
                "isYouth", 0L,
                "hasQualifyingNotification", 1L));
    }

    @Test
    void toCelContext_youthMissingNotification_shouldMapBothFieldsAccordingly() {
        SexualOffenceNotificationContext context =
                new SexualOffenceNotificationContext("off1", "Jamie Smith", true, false);

        assertThat(context.toCelContext()).isEqualTo(Map.of(
                "isYouth", 1L,
                "hasQualifyingNotification", 0L));
    }

    @Test
    void getOffenceIdSet_offenceId_shouldReturnSingleOffenceIdList() {
        SexualOffenceNotificationContext context =
                new SexualOffenceNotificationContext("off1", "Jamie Smith", false, false);

        assertThat(context.getOffenceIdSet("offenceId")).containsExactly("off1");
    }

    @Test
    void getOffenceIdSet_unknownSet_shouldThrow() {
        SexualOffenceNotificationContext context =
                new SexualOffenceNotificationContext("off1", "Jamie Smith", false, false);

        assertThatThrownBy(() -> context.getOffenceIdSet("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void allOffenceIds_shouldReturnSingleOffenceIdList() {
        SexualOffenceNotificationContext context =
                new SexualOffenceNotificationContext("off1", "Jamie Smith", false, false);

        assertThat(context.allOffenceIds()).containsExactly("off1");
    }

    @Test
    void defendantName_shouldReturnConstructorValue() {
        SexualOffenceNotificationContext context =
                new SexualOffenceNotificationContext("off1", "Jamie Smith", false, false);

        assertThat(context.defendantName()).isEqualTo("Jamie Smith");
    }
}
