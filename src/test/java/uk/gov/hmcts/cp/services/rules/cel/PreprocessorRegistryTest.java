package uk.gov.hmcts.cp.services.rules.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;

/**
 * Unit tests for the data-driven preprocessor dispatch (Constitution Principle III).
 */
class PreprocessorRegistryTest {

    @Nested
    @DisplayName("require")
    class Require {

        @Test
        void require_should_resolve_preprocessor_by_qualifier() {
            final ValidationPreprocessor stub = new TypedStub("custodial-concurrent-consecutive");
            final PreprocessorRegistry registry = new PreprocessorRegistry(List.of(stub));

            assertThat(registry.require("custodial-concurrent-consecutive")).isSameAs(stub);
        }

        @Test
        void require_should_resolve_correct_preprocessor_when_multiple_registered() {
            final ValidationPreprocessor a = new TypedStub("alpha");
            final ValidationPreprocessor b = new TypedStub("beta");
            final PreprocessorRegistry registry = new PreprocessorRegistry(List.of(a, b));

            assertThat(registry.require("alpha")).isSameAs(a);
            assertThat(registry.require("beta")).isSameAs(b);
        }

        @Test
        void require_should_throw_when_qualifier_unknown() {
            final ValidationPreprocessor stub = new TypedStub("known-type");
            final PreprocessorRegistry registry = new PreprocessorRegistry(List.of(stub));

            assertThatThrownBy(() -> registry.require("missing-type"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing-type");
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        void constructor_should_throw_when_duplicate_qualifier() {
            final ValidationPreprocessor first = new TypedStub("dup");
            final ValidationPreprocessor second = new TypedStub("dup");

            assertThatThrownBy(() -> new PreprocessorRegistry(List.of(first, second)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("dup");
        }
    }

    private record TypedStub(String type) implements ValidationPreprocessor {
        @Override
        public Map<String, ? extends RuleEvaluationContext> preprocess(
                final DraftValidationRequest request,
                final PreprocessingDefinition config) {
            return Map.of();
        }
    }
}
