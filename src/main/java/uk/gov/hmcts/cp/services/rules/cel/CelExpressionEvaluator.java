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

@Component
public class CelExpressionEvaluator {

    private final ScriptHost host = ScriptHost.newBuilder().build();
    private final Map<String, Script> cache = new ConcurrentHashMap<>();

    public boolean evaluate(String expression, Map<String, Long> context) {
        try {
            String cacheKey = expression + "|" + context.keySet().stream()
                    .sorted()
                    .collect(Collectors.joining(","));
            Script script = cache.computeIfAbsent(
                    cacheKey,
                    k -> compile(expression, context));
            Map<String, Object> objectContext = new HashMap<>(context);
            return script.execute(Boolean.class, objectContext);
        } catch (ScriptException e) {
            throw new IllegalArgumentException("CEL evaluation failed for: " + expression, e);
        }
    }

    private Script compile(String expression, Map<String, Long> context) {
        try {
            ScriptHost.ScriptBuilder builder = host.buildScript(expression);
            for (String varName : context.keySet()) {
                builder.withDeclarations(Decls.newVar(varName, Decls.Int));
            }
            return builder.build();
        } catch (ScriptException e) {
            throw new IllegalArgumentException("CEL compilation failed for: " + expression, e);
        }
    }
}
