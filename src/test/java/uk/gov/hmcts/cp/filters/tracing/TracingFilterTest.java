package uk.gov.hmcts.cp.filters.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.APPLICATION_NAME;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.CJSCPPUID_HEADER;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.CLIENT_CORRELATION_ID;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.CORRELATION_HEADER;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.SPAN_ID;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.TRACE_ID;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.USER_ID;

/**
 * Unit tests for {@link TracingFilter}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracingFilterTest {
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private final TracingFilter tracingFilter = new TracingFilter("myAppName");

    /**
     * Verifies the filter copies inbound trace headers into the MDC and echoes them on the
     * response for downstream consumers.
     */
    @Test
    void filter_should_use_incoming_traceId() throws ServletException, IOException {
        when(request.getHeader(TRACE_ID)).thenReturn("incoming-traceId");
        when(request.getHeader(SPAN_ID)).thenReturn("incoming-spanId");
        when(request.getRequestURI()).thenReturn("/test");

        Map<String, String> capturedMdc = new HashMap<>();
        doAnswer(invocation -> {
            capturedMdc.put(APPLICATION_NAME, MDC.get(APPLICATION_NAME));
            capturedMdc.put(TRACE_ID, MDC.get(TRACE_ID));
            capturedMdc.put(SPAN_ID, MDC.get(SPAN_ID));
            return null;
        }).when(filterChain).doFilter(request, response);

        tracingFilter.doFilterInternal(request, response, filterChain);

        assertThat(capturedMdc.get(APPLICATION_NAME)).isEqualTo("myAppName");
        assertThat(capturedMdc.get(TRACE_ID)).isEqualTo("incoming-traceId");
        assertThat(capturedMdc.get(SPAN_ID)).isEqualTo("incoming-spanId");
        verify(response).setHeader(TRACE_ID, "incoming-traceId");
        verify(response).setHeader(SPAN_ID, "incoming-spanId");
    }

    /**
     * Verifies the filter extracts CJSCPPUID and CPPCLIENTCORRELATIONID headers into MDC
     * for user and session traceability.
     */
    @Test
    void filter_should_populate_userId_and_clientCorrelationId_from_headers() throws ServletException, IOException {
        when(request.getHeader(CJSCPPUID_HEADER)).thenReturn("user-abc-123");
        when(request.getHeader(CORRELATION_HEADER)).thenReturn("session-xyz-789");
        when(request.getRequestURI()).thenReturn("/test");

        Map<String, String> capturedMdc = new HashMap<>();
        doAnswer(invocation -> {
            capturedMdc.put(USER_ID, MDC.get(USER_ID));
            capturedMdc.put(CLIENT_CORRELATION_ID, MDC.get(CLIENT_CORRELATION_ID));
            return null;
        }).when(filterChain).doFilter(request, response);

        tracingFilter.doFilterInternal(request, response, filterChain);

        assertThat(capturedMdc.get(USER_ID)).isEqualTo("user-abc-123");
        assertThat(capturedMdc.get(CLIENT_CORRELATION_ID)).isEqualTo("session-xyz-789");
    }

    /**
     * Verifies that missing identity headers do not populate MDC fields, and that MDC is
     * cleared after request processing.
     */
    @Test
    void filter_should_not_set_userId_when_header_absent() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/test");

        Map<String, String> capturedMdc = new HashMap<>();
        doAnswer(invocation -> {
            capturedMdc.put(USER_ID, MDC.get(USER_ID));
            capturedMdc.put(CLIENT_CORRELATION_ID, MDC.get(CLIENT_CORRELATION_ID));
            return null;
        }).when(filterChain).doFilter(request, response);

        tracingFilter.doFilterInternal(request, response, filterChain);

        assertThat(capturedMdc.get(USER_ID)).isNull();
        assertThat(capturedMdc.get(CLIENT_CORRELATION_ID)).isNull();
    }

    /**
     * Verifies that MDC is cleared after the filter chain completes, preventing state
     * leaking between requests.
     */
    @Test
    void filter_should_clear_mdc_after_processing() throws ServletException, IOException {
        when(request.getHeader(TRACE_ID)).thenReturn("trace-1");
        when(request.getHeader(CJSCPPUID_HEADER)).thenReturn("user-1");
        when(request.getHeader(CORRELATION_HEADER)).thenReturn("session-1");
        when(request.getRequestURI()).thenReturn("/test");

        tracingFilter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get(TRACE_ID)).isNull();
        assertThat(MDC.get(USER_ID)).isNull();
        assertThat(MDC.get(CLIENT_CORRELATION_ID)).isNull();
        assertThat(MDC.get(APPLICATION_NAME)).isNull();
    }
}
