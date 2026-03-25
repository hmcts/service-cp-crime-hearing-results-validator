package uk.gov.hmcts.cp.services.rules.cel;

import org.projectnessie.cel.checker.Decls;
import org.springframework.stereotype.Component;
import org.projectnessie.cel.tools.Script;
import org.projectnessie.cel.tools.ScriptException;
import org.projectnessie.cel.tools.ScriptHost;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Evaluates CEL expressions used by YAML-defined validation rule conditions.
 */
@Component
public class CelExpressionEvaluator {

    private final ScriptHost host = ScriptHost.newBuilder().build();
    private final Map<String, Script> cache = new ConcurrentHashMap<>();

    /**
     * Evaluates a boolean CEL expression against the supplied numeric context.
     *
     * @param expression CEL expression to evaluate
     * @param context variable values exposed to the expression
     * @return evaluation result
     */
    public boolean evaluate(final String expression, final Map<String, Long> context) {
        try {
            final String cacheKey = expression + "|" + context.keySet().stream()
                    .sorted()
                    .collect(Collectors.joining(","));
            final Script script = cache.computeIfAbsent(
                    cacheKey,
                    k -> compile(expression, context));
            final Map<String, Object> objectContext = new HashMap<>(context);
            return script.execute(Boolean.class, objectContext);
        } catch (ScriptException e) {
            throw new IllegalArgumentException("CEL evaluation failed for: " + expression, e);
        }
    }

    /**
     * Compiles an expression for the given variable shape so it can be cached and reused.
     *
     * @param expression CEL expression to compile
     * @param context variable names that must be declared for the expression
     * @return compiled CEL script
     */
    private Script compile(final String expression, final Map<String, Long> context) {
        try {
            final ScriptHost.ScriptBuilder builder = host.buildScript(expression);
            for (final String varName : context.keySet()) {
                builder.withDeclarations(Decls.newVar(varName, Decls.Int));
            }
            return builder.build();
        } catch (ScriptException e) {
            throw new IllegalArgumentException("CEL compilation failed for: " + expression, e);
        }
    }
}
