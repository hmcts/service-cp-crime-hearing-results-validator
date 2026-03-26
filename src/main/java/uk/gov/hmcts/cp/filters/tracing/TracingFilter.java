package uk.gov.hmcts.cp.filters.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Copies inbound tracing and identity headers into the MDC and echoes tracing headers
 * back on the response. Identity headers (userId, clientCorrelationId) are placed into
 * MDC only — they are not echoed on the response.
 */
@Component
@Slf4j
public class TracingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String APPLICATION_NAME = "applicationName";
    public static final String USER_ID = "userId";
    public static final String CLIENT_CORRELATION_ID = "clientCorrelationId";

    /* default */ static final String CJSCPPUID_HEADER = "CJSCPPUID";
    /* default */ static final String CORRELATION_HEADER = "CPPCLIENTCORRELATIONID";

    private final String applicationName;

    public TracingFilter(@Value("${spring.application.name}") final String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * Wraps request processing so tracing context is available for the full request lifecycle and
     * then cleared to avoid leaking MDC state between requests.
     *
     * @param request incoming HTTP request
     * @param response outgoing HTTP response
     * @param filterChain remaining servlet filter chain
     * @throws ServletException if request processing fails
     * @throws IOException if request processing fails
     */
    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain) throws ServletException, IOException {
        try {
            populateMDC(request, response, filterChain);
        } finally {
            MDC.clear();
        }
    }

    private void populateMDC(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain) throws IOException, ServletException {
        log.debug("TracingFilter for uri:{}", Encode.forJava(request.getRequestURI()));
        MDC.put(APPLICATION_NAME, applicationName);
        if (request.getHeader(TRACE_ID) != null) {
            MDC.put(TRACE_ID, request.getHeader(TRACE_ID));
            response.setHeader(TRACE_ID, request.getHeader(TRACE_ID));
        }
        if (request.getHeader(SPAN_ID) != null) {
            MDC.put(SPAN_ID, request.getHeader(SPAN_ID));
            response.setHeader(SPAN_ID, request.getHeader(SPAN_ID));
        }
        if (request.getHeader(CJSCPPUID_HEADER) != null) {
            MDC.put(USER_ID, request.getHeader(CJSCPPUID_HEADER));
        }
        if (request.getHeader(CORRELATION_HEADER) != null) {
            MDC.put(CLIENT_CORRELATION_ID, request.getHeader(CORRELATION_HEADER));
        }
        filterChain.doFilter(request, response);
    }
}
