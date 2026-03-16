package uk.gov.hmcts.cp.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * Resolves the CPP-ACTION header from the request path when not explicitly provided.
 * Runs before the authz filter so that Drools rules can match on action names
 * even when the caller (e.g. browser UI) does not send the CPP-ACTION header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ActionHeaderFilter extends OncePerRequestFilter {

    private static final String ACTION_HEADER = "CPP-ACTION";

    private static final Map<String, String> PATH_TO_ACTION = Map.of(
            "/api/validation/validate", "validation-service.validate",
            "/api/validation/rules", "validation-service.rules"
    );

    private static final String RULES_DETAIL_PREFIX = "/api/validation/rules/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (request.getHeader(ACTION_HEADER) != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String action = resolveAction(request.getRequestURI());
        if (action != null) {
            filterChain.doFilter(new ActionHeaderRequestWrapper(request, action), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private String resolveAction(String uri) {
        String action = PATH_TO_ACTION.get(uri);
        if (action != null) {
            return action;
        }
        if (uri.startsWith(RULES_DETAIL_PREFIX)) {
            return "validation-service.rules-detail";
        }
        return null;
    }

    private static class ActionHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final String action;

        ActionHeaderRequestWrapper(HttpServletRequest request, String action) {
            super(request);
            this.action = action;
        }

        @Override
        public String getHeader(String name) {
            if (ACTION_HEADER.equalsIgnoreCase(name)) {
                return action;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (ACTION_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(action));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (!names.stream().anyMatch(ACTION_HEADER::equalsIgnoreCase)) {
                names.add(ACTION_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
