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
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {

        final String action = resolveAction(request.getServletPath());
        if (request.getHeader(ACTION_HEADER) == null && action != null) {
            filterChain.doFilter(new ActionHeaderRequestWrapper(request, action), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private String resolveAction(final String uri) {
        final String mapped = PATH_TO_ACTION.get(uri);
        final String action;
        if (mapped != null) {
            action = mapped;
        } else if (uri.startsWith(RULES_DETAIL_PREFIX)) {
            action = "validation-service.rules-detail";
        } else {
            action = null;
        }
        return action;
    }

    private static class ActionHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final String action;

        /* default */ ActionHeaderRequestWrapper(final HttpServletRequest request, final String action) {
            super(request);
            this.action = action;
        }

        @Override
        public String getHeader(final String name) {
            final String result;
            if (ACTION_HEADER.equalsIgnoreCase(name)) {
                result = action;
            } else {
                result = super.getHeader(name);
            }
            return result;
        }

        @Override
        public Enumeration<String> getHeaders(final String name) {
            final Enumeration<String> result;
            if (ACTION_HEADER.equalsIgnoreCase(name)) {
                result = Collections.enumeration(List.of(action));
            } else {
                result = super.getHeaders(name);
            }
            return result;
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            final List<String> names = Collections.list(super.getHeaderNames());
            if (!names.stream().anyMatch(ACTION_HEADER::equalsIgnoreCase)) {
                names.add(ACTION_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
