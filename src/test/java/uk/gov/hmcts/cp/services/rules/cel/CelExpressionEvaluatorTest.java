package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CelExpressionEvaluator}.
 */
class CelExpressionEvaluatorTest {

    private final CelExpressionEvaluator evaluator = new CelExpressionEvaluator();

    /**
     * Verifies a simple greater-than expression evaluates to true when the supplied count exceeds
     * the threshold.
     */
    @Test
    void evaluate_greater_than_should_return_true() {
        Map<String, Long> context = Map.of("noInfoCount", 3L);
        assertThat(evaluator.evaluate("noInfoCount > 1", context)).isTrue();
    }

    /**
     * Verifies the same greater-than expression evaluates to false at the non-matching boundary.
     */
    @Test
    void evaluate_greater_than_should_return_false() {
        Map<String, Long> context = Map.of("noInfoCount", 1L);
        assertThat(evaluator.evaluate("noInfoCount > 1", context)).isFalse();
    }

    /**
     * Verifies equality expressions return true when the supplied count matches exactly.
     */
    @Test
    void evaluate_equality_should_return_true() {
        Map<String, Long> context = Map.of("noInfoCount", 0L);
        assertThat(evaluator.evaluate("noInfoCount == 0", context)).isTrue();
    }

    /**
     * Verifies equality expressions return false when the supplied count differs from the expected
     * value.
     */
    @Test
    void evaluate_equality_should_return_false() {
        Map<String, Long> context = Map.of("noInfoCount", 2L);
        assertThat(evaluator.evaluate("noInfoCount == 0", context)).isFalse();
    }

    /**
     * Verifies expressions can be evaluated repeatedly against a context that exposes multiple CEL
     * variables.
     */
    @Test
    void evaluate_with_multiple_variables() {
        Map<String, Long> context = Map.of(
                "hasBothCount", 1L,
                "noInfoCount", 0L
        );
        assertThat(evaluator.evaluate("hasBothCount > 0", context)).isTrue();
        assertThat(evaluator.evaluate("noInfoCount == 0", context)).isTrue();
        assertThat(evaluator.evaluate("noInfoCount > 1", context)).isFalse();
    }

    /**
     * Verifies invalid CEL syntax is surfaced as an {@link IllegalArgumentException}.
     */
    @Test
    void evaluate_invalid_expression_should_throw() {
        Map<String, Long> context = Map.of("x", 1L);
        assertThatThrownBy(() -> evaluator.evaluate(">>> invalid", context))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies references to undeclared variables fail rather than silently defaulting.
     */
    @Test
    void evaluate_with_missing_variable_should_throw() {
        Map<String, Long> context = Map.of("x", 1L);
        assertThatThrownBy(() -> evaluator.evaluate("missingVar > 0", context))
                .isInstanceOf(Exception.class);
    }

    /**
     * Verifies evaluating the same expression twice yields the same result and exercises the script
     * cache path.
     */
    @Test
    void evaluate_same_expression_twice_should_use_cache() {
        Map<String, Long> context = Map.of("noInfoCount", 5L);
        boolean first = evaluator.evaluate("noInfoCount > 1", context);
        boolean second = evaluator.evaluate("noInfoCount > 1", context);
        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }

    /**
     * Verifies values on either side of the threshold produce the expected true and false results.
     */
    @Test
    void evaluate_boundary_at_threshold() {
        Map<String, Long> context = Map.of("noInfoCount", 2L);
        assertThat(evaluator.evaluate("noInfoCount > 1", context)).isTrue();

        Map<String, Long> atBoundary = Map.of("noInfoCount", 1L);
        assertThat(evaluator.evaluate("noInfoCount > 1", atBoundary)).isFalse();
    }
}
