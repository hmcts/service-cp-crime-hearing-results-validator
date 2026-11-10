package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PreReleaseBailContextTest {

    private static final String OFFENCE_ID = "off-001";
    private static final String DEFENDANT_ID = "def-001";
    private static final String DEFENDANT_NAME = "John Smith";

    private PreReleaseBailContext buildContext(long isRemandBail, long isCcic, long isCcsc,
                                               long isAdult, long isYouth, long hasDob,
                                               long hearingHasRemandAdult,
                                               long hearingHasRemandYouth) {
        return new PreReleaseBailContext(
                OFFENCE_ID, DEFENDANT_ID, DEFENDANT_NAME,
                isRemandBail, isCcic, isCcsc,
                isAdult, isYouth, hasDob,
                hearingHasRemandAdult, hearingHasRemandYouth);
    }

    @Nested
    @DisplayName("toCelContext")
    class ToCelContext {

        @Test
        void adultRemandBail_shouldReturnAllEightKeysWithCorrectValues() {
            final PreReleaseBailContext ctx = buildContext(1L, 0L, 0L, 1L, 0L, 1L, 1L, 0L);

            final Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("isRemandBail", 1L)
                    .containsEntry("isCcic", 0L)
                    .containsEntry("isCcsc", 0L)
                    .containsEntry("isAdult", 1L)
                    .containsEntry("isYouth", 0L)
                    .containsEntry("hasDob", 1L)
                    .containsEntry("hearingHasRemandAdult", 1L)
                    .containsEntry("hearingHasRemandYouth", 0L)
                    .hasSize(8);
        }

        @Test
        void youthRemandBail_shouldReturnCorrectFlags() {
            final PreReleaseBailContext ctx = buildContext(1L, 0L, 0L, 0L, 1L, 1L, 0L, 1L);

            final Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("isRemandBail", 1L)
                    .containsEntry("isAdult", 0L)
                    .containsEntry("isYouth", 1L)
                    .containsEntry("hasDob", 1L)
                    .containsEntry("hearingHasRemandAdult", 0L)
                    .containsEntry("hearingHasRemandYouth", 1L);
        }

        @Test
        void ccicParent_shouldReturnCcicFlag() {
            final PreReleaseBailContext ctx = buildContext(0L, 1L, 0L, 1L, 0L, 1L, 0L, 0L);

            final Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("isRemandBail", 0L)
                    .containsEntry("isCcic", 1L)
                    .containsEntry("isCcsc", 0L);
        }

        @Test
        void ccscParent_shouldReturnCcscFlag() {
            final PreReleaseBailContext ctx = buildContext(0L, 0L, 1L, 1L, 0L, 1L, 0L, 0L);

            final Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("isRemandBail", 0L)
                    .containsEntry("isCcic", 0L)
                    .containsEntry("isCcsc", 1L);
        }

        @Test
        void noDob_shouldReturnHasDobZeroAndBothAgesFlagZero() {
            final PreReleaseBailContext ctx = buildContext(1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

            final Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("hasDob", 0L)
                    .containsEntry("isAdult", 0L)
                    .containsEntry("isYouth", 0L);
        }

        @Test
        void mixedHearing_bothHearingFlagsSet() {
            final PreReleaseBailContext ctx = buildContext(1L, 0L, 0L, 1L, 0L, 1L, 1L, 1L);

            final Map<String, Long> cel = ctx.toCelContext();

            assertThat(cel).containsEntry("hearingHasRemandAdult", 1L)
                    .containsEntry("hearingHasRemandYouth", 1L);
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet")
    class GetOffenceIdSet {

        @Test
        void triggeredOffenceId_shouldReturnSingletonListWithOffenceId() {
            final PreReleaseBailContext ctx = buildContext(1L, 0L, 0L, 1L, 0L, 1L, 1L, 0L);

            assertThat(ctx.getOffenceIdSet("triggeredOffenceId"))
                    .containsExactly(OFFENCE_ID);
        }

        @Test
        void unknownSetName_shouldThrowIllegalArgumentException() {
            final PreReleaseBailContext ctx = buildContext(1L, 0L, 0L, 1L, 0L, 1L, 1L, 0L);

            assertThatThrownBy(() -> ctx.getOffenceIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }

    @Nested
    @DisplayName("defendantId")
    class DefendantIdMethod {

        @Test
        void shouldReturnDefendantId() {
            final PreReleaseBailContext ctx = buildContext(1L, 0L, 0L, 1L, 0L, 1L, 0L, 0L);

            assertThat(ctx.defendantId()).isEqualTo(DEFENDANT_ID);
        }
    }

    @Nested
    @DisplayName("defendantName")
    class DefendantNameMethod {

        @Test
        void shouldReturnDefendantName() {
            final PreReleaseBailContext ctx = buildContext(1L, 0L, 0L, 1L, 0L, 1L, 0L, 0L);

            assertThat(ctx.defendantName()).isEqualTo(DEFENDANT_NAME);
        }
    }

    @Nested
    @DisplayName("allOffenceIds")
    class AllOffenceIds {

        @Test
        void shouldReturnSingletonListWithOffenceId() {
            final PreReleaseBailContext ctx = buildContext(1L, 0L, 0L, 1L, 0L, 1L, 0L, 0L);

            assertThat(ctx.allOffenceIds()).containsExactly(OFFENCE_ID);
        }
    }
}
