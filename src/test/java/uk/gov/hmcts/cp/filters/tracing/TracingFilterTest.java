package uk.gov.hmcts.cp.filters.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.APPLICATION_NAME;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.SPAN_ID;
import static uk.gov.hmcts.cp.filters.tracing.TracingFilter.TRACE_ID;

@ExtendWith(MockitoExtension.class)
class TracingFilterTest {
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private final TracingFilter tracingFilter = new TracingFilter("myAppName");

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
}
