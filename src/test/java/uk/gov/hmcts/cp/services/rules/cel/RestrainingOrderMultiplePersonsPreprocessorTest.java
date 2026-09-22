package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.buildRequest;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.offence;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLine;
import static uk.gov.hmcts.cp.services.rules.ValidationRuleTestHelper.resultLineWithPrompt;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.OffenceDto;

@ExtendWith(MockitoExtension.class)
class RestrainingOrderMultiplePersonsPreprocessorTest {

    private static final String PROMPT_REF = "protectedPersonsName";

    private final RestrainingOrderMultiplePersonsPreprocessor preprocessor =
            new RestrainingOrderMultiplePersonsPreprocessor();

    private final PreprocessingDefinition config = PreprocessingDefinition.builder()
            .type(RestrainingOrderMultiplePersonsPreprocessor.QUALIFIER)
            .filterShortCodes(List.of("RESTRAO"))
            .build();

    private Map<String, RestrainingOrderContext> preprocess(DraftValidationRequest request) {
        return preprocessor.preprocess(request, config);
    }

    @Nested
    @DisplayName("SeparatorCharTriggers")
    class SeparatorCharTriggers {

        @Test
        void ampersandInName_should_produceMultiplePersonsCount1() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "John Smith & Jane Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx).isNotNull();
            assertThat(ctx.multiplePersonsCount()).isEqualTo(1L);
            assertThat(ctx.breachingOffenceIds()).containsExactly("off1");
        }

        @Test
        void commaInName_should_produceMultiplePersonsCount1() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "John Smith, Jane Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(1L);
            assertThat(ctx.breachingOffenceIds()).containsExactly("off1");
        }

        @Test
        void slashInName_should_produceMultiplePersonsCount1() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "John Smith/Jane Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(1L);
            assertThat(ctx.breachingOffenceIds()).containsExactly("off1");
        }

        @ParameterizedTest(name = "separator ''{0}'' should trigger warning")
        @ValueSource(strings = {"&", ",", "/"})
        void eachSeparatorChar_should_produceMultiplePersonsCount1(String separator) {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "John Smith" + separator + "Jane Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("NoTriggerCases")
    class NoTriggerCases {

        @Test
        void blankName_should_produceMultiplePersonsCount0() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "   ")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(0L);
            assertThat(ctx.breachingOffenceIds()).isEmpty();
        }

        @Test
        void nullPrompts_should_produceMultiplePersonsCount0() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "RESTRAO", "d1", "off1")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(0L);
            assertThat(ctx.breachingOffenceIds()).isEmpty();
        }

        @Test
        void noRestraoLines_should_returnEmptyMap() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLine("rl1", "IMP", "d1", "off1")),
                    List.of(offence("off1", 1, "Imprisonment")));

            Map<String, RestrainingOrderContext> result = preprocess(request);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("WholeWordAndTriggers")
    class WholeWordAndTriggers {

        // "and" in the middle — WARNING expected

        @Test
        void andInMiddle_lowercase_should_produceMultiplePersonsCount1() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "John Smith and Jane Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(1L);
            assertThat(ctx.breachingOffenceIds()).containsExactly("off1");
        }

        @Test
        void andInMiddle_uppercase_should_produceMultiplePersonsCount1() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "John Smith AND Jane Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(1L);
            assertThat(ctx.breachingOffenceIds()).containsExactly("off1");
        }

        @Test
        void andInMiddle_mixedCase_should_produceMultiplePersonsCount1() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "John Smith And Jane Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(1L);
            assertThat(ctx.breachingOffenceIds()).containsExactly("off1");
        }

        // "and" at the start — no WARNING (no word before "and")

        @Test
        void andAtStart_should_produceMultiplePersonsCount0() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "and Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(0L);
            assertThat(ctx.breachingOffenceIds()).isEmpty();
        }

        // "and" at the end — no WARNING (no word after "and")

        @Test
        void andAtEnd_should_produceMultiplePersonsCount0() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "John and")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(0L);
            assertThat(ctx.breachingOffenceIds()).isEmpty();
        }

        @Test
        void andAsTrailingWord_should_produceMultiplePersonsCount0() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "Alexandra Sanderson And")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(0L);
            assertThat(ctx.breachingOffenceIds()).isEmpty();
        }

        // "and" as substring inside a word — no WARNING

        @Test
        void andAsSubstringInAlexandraSanderson_should_produceMultiplePersonsCount0() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "Alexandra Sanderson")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(0L);
            assertThat(ctx.breachingOffenceIds()).isEmpty();
        }

        @Test
        void andAsSubstringInAmandaAnderson_should_produceMultiplePersonsCount0() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "Amanda Anderson")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(0L);
            assertThat(ctx.breachingOffenceIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("EdgeCases")
    class EdgeCases {

        @Test
        void andAtStartOfField_should_produceMultiplePersonsCount0() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "and Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(0L);
            assertThat(ctx.breachingOffenceIds()).isEmpty();
        }

        @Test
        void andAtEndOfField_should_produceMultiplePersonsCount0() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "John and")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(0L);
            assertThat(ctx.breachingOffenceIds()).isEmpty();
        }

        @Test
        void singleCleanName_should_produceMultiplePersonsCount0() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "RESTRAO", "d1", "off1",
                            PROMPT_REF, "Jane Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(0L);
            assertThat(ctx.breachingOffenceIds()).isEmpty();
            assertThat(ctx.allOffenceIds()).containsExactly("off1");
        }

        @Test
        void restraoShortCodeMatchingIsCaseInsensitive() {
            DraftValidationRequest request = buildRequest(
                    List.of(resultLineWithPrompt("rl1", "restrao", "d1", "off1",
                            PROMPT_REF, "John Smith & Jane Smith")),
                    List.of(offence("off1", 1, "Restraining Order")));

            RestrainingOrderContext ctx = preprocess(request).get("off1");

            assertThat(ctx.multiplePersonsCount()).isEqualTo(1L);
        }
    }
}
