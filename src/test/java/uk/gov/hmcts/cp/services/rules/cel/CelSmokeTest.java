package uk.gov.hmcts.cp.services.rules.cel;

import org.junit.jupiter.api.Test;
import org.projectnessie.cel.checker.Decls;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptHost;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Smoke tests proving the underlying CEL library behaves as expected in this project.
 */
class CelSmokeTest {

    private final ScriptHost host = ScriptHost.newBuilder().build();

    /**
     * Verifies a constant boolean expression can be compiled and evaluated successfully.
     */
    @Test
    void simple_arithmetic_expression_should_evaluate() throws Exception {
        Script script = host.buildScript("1 + 1 == 2").build();
        Boolean result = script.execute(Boolean.class, Map.of());
        assertThat(result).isTrue();
    }

    /**
     * Verifies CEL variable declarations work when the supplied value satisfies the expression.
     */
    @Test
    void expression_with_variable_should_evaluate_true() throws Exception {
        Script script = host.buildScript("x > 1")
                .withDeclarations(Decls.newVar("x", Decls.Int))
                .build();
        Boolean result = script.execute(Boolean.class, Map.of("x", 3L));
        assertThat(result).isTrue();
    }

    /**
     * Verifies the same variable-based expression returns false for a non-matching value.
     */
    @Test
    void expression_with_variable_should_evaluate_false() throws Exception {
        Script script = host.buildScript("x > 1")
                .withDeclarations(Decls.newVar("x", Decls.Int))
                .build();
        Boolean result = script.execute(Boolean.class, Map.of("x", 0L));
        assertThat(result).isFalse();
    }

    /**
     * Verifies equality expressions behave correctly for exact matches.
     */
    @Test
    void equality_check_should_work() throws Exception {
        Script script = host.buildScript("count == 0")
                .withDeclarations(Decls.newVar("count", Decls.Int))
                .build();
        Boolean result = script.execute(Boolean.class, Map.of("count", 0L));
        assertThat(result).isTrue();
    }

    /**
     * Verifies invalid CEL syntax is rejected during script compilation.
     */
    @Test
    void invalid_expression_should_throw() {
        assertThatThrownBy(() -> host.buildScript(">>> invalid <<<").build())
                .isInstanceOf(Exception.class);
    }
}
