package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PreReleaseBailContext")
class PreReleaseBailContextTest {

    private static final String OFFENCE_ID = "off-1";

    private static PreReleaseBailContext adult() {
        return new PreReleaseBailContext(OFFENCE_ID, 1L, 1L, 0L, 0L, 0L, 0L);
    }

    private static PreReleaseBailContext youth() {
        return new PreReleaseBailContext(OFFENCE_ID, 1L, 0L, 1L, 0L, 0L, 0L);
    }

    private static PreReleaseBailContext noDob() {
        return new PreReleaseBailContext(OFFENCE_ID, 1L, 0L, 0L, 1L, 0L, 0L);
    }

    private static PreReleaseBailContext ccic() {
        return new PreReleaseBailContext(OFFENCE_ID, 1L, 1L, 0L, 0L, 1L, 0L);
    }

    private static PreReleaseBailContext ccsc() {
        return new PreReleaseBailContext(OFFENCE_ID, 1L, 0L, 1L, 0L, 0L, 1L);
    }

    @Nested
    @DisplayName("toCelContext()")
    class ToCelContext {

        @Test
        @DisplayName("adult context — 6 variables returned with correct values")
        void adult_should_return_six_cel_variables() {
            Map<String, Long> ctx = adult().toCelContext();

            assertThat(ctx).containsEntry("triggerCount", 1L)
                    .containsEntry("adultCount", 1L)
                    .containsEntry("youthCount", 0L)
                    .containsEntry("noDobCount", 0L)
                    .containsEntry("ccicCount", 0L)
                    .containsEntry("ccscCount", 0L)
                    .hasSize(6);
        }

        @Test
        @DisplayName("youth context — youthCount=1, adultCount=0")
        void youth_should_return_youthCount_one() {
            Map<String, Long> ctx = youth().toCelContext();

            assertThat(ctx).containsEntry("youthCount", 1L)
                    .containsEntry("adultCount", 0L);
        }

        @Test
        @DisplayName("no-DOB context — noDobCount=1")
        void noDob_should_return_noDobCount_one() {
            Map<String, Long> ctx = noDob().toCelContext();

            assertThat(ctx).containsEntry("noDobCount", 1L)
                    .containsEntry("adultCount", 0L)
                    .containsEntry("youthCount", 0L);
        }

        @Test
        @DisplayName("CCIC context — ccicCount=1, ccscCount=0")
        void ccic_should_return_ccicCount_one() {
            Map<String, Long> ctx = ccic().toCelContext();

            assertThat(ctx).containsEntry("ccicCount", 1L)
                    .containsEntry("ccscCount", 0L);
        }

        @Test
        @DisplayName("CCSC context — ccscCount=1, ccicCount=0")
        void ccsc_should_return_ccscCount_one() {
            Map<String, Long> ctx = ccsc().toCelContext();

            assertThat(ctx).containsEntry("ccscCount", 1L)
                    .containsEntry("ccicCount", 0L);
        }
    }

    @Nested
    @DisplayName("getOffenceIdSet()")
    class GetOffenceIdSet {

        @Test
        @DisplayName("'offenceId' set name — returns singleton list with offenceId")
        void offenceId_setName_should_return_singleton_offenceId() {
            List<String> ids = adult().getOffenceIdSet("offenceId");

            assertThat(ids).containsExactly(OFFENCE_ID);
        }

        @Test
        @DisplayName("unknown set name — throws IllegalArgumentException")
        void unknownSetName_should_throwIllegalArgumentException() {
            assertThatThrownBy(() -> adult().getOffenceIdSet("unknownSet"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknownSet");
        }
    }

    @Nested
    @DisplayName("allOffenceIds()")
    class AllOffenceIds {

        @Test
        @DisplayName("returns singleton list with offenceId")
        void should_return_singleton_offenceId() {
            assertThat(adult().allOffenceIds()).containsExactly(OFFENCE_ID);
        }
    }

    @Nested
    @DisplayName("defendantName()")
    class DefendantName {

        @Test
        @DisplayName("always returns null — context is offence-level only")
        void should_return_null() {
            assertThat(adult().defendantName()).isNull();
        }
    }
}
