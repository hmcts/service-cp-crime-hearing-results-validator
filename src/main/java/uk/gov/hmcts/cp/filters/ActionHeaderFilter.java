package uk.gov.hmcts.cp.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the CPP-ACTION header from the request path and method, and enforces it
 * server-side for every path this filter recognises.
 *
 * <p>The action is always derived from path + method and never taken from the caller,
 * even when the request already carries a CPP-ACTION header. Trusting a caller-supplied
 * value here would let a caller authorized only for a read action (e.g. rules-detail)
 * spoof that same header on a mutating request (e.g. a PATCH that resolves to
 * rules-update) and have the Drools authz filter evaluate the wrong, more permissive
 * action. Paths this filter does not recognise are passed through unchanged, leaving
 * any caller-supplied CPP-ACTION (or its absence) untouched.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ActionHeaderFilter extends OncePerRequestFilter {

    private static final String ACTION_HEADER = "CPP-ACTION";

    private static final String RULES_DETAIL_ACTION = "validation-service.rules-detail";
    private static final String RULES_UPDATE_ACTION = "validation-service.rules-update";

    private static final Map<String, String> PATH_TO_ACTION = Map.of(
            "/api/validation/validate", "validation-service.validate",
            "/api/validation/rules", "validation-service.rules"
    );

    private static final String RULES_DETAIL_PREFIX = "/api/validation/rules/";

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {

        final String servletPath = request.getServletPath();
        final String path = (servletPath == null || servletPath.isEmpty())
                ? request.getRequestURI()
                : servletPath;
        final String action = resolveAction(path, request.getMethod());
        if (action != null) {
            filterChain.doFilter(new ActionHeaderRequestWrapper(request, action), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private String resolveAction(final String uri, final String method) {
        final String mapped = PATH_TO_ACTION.get(uri);
        final String action;
        if (mapped != null) {
            action = mapped;
        } else if (uri.startsWith(RULES_DETAIL_PREFIX)) {
            action = "PATCH".equalsIgnoreCase(method) ? RULES_UPDATE_ACTION : RULES_DETAIL_ACTION;
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
