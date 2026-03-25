package uk.gov.hmcts.cp.controllers;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.TraceContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 */
class GlobalExceptionHandlerTest {

    /**
     * Verifies that a {@link ResponseStatusException} with an explicit reason is mapped to the same
     * HTTP status and a structured error body that includes the current trace id.
     */
    @Test
    void handle_response_status_exception_should_return_error_response_with_correct_fields() {
        // Arrange
        final Tracer tracer = mock(Tracer.class);
        final Span span = mock(Span.class);
        final TraceContext context = mock(TraceContext.class);

        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("test-trace-id");

        final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracer);

        final String reason = "Test error";
        final ResponseStatusException exception =
                new ResponseStatusException(HttpStatus.NOT_FOUND, reason);

        final Instant beforeCall = Instant.now();

        // Act
        final ResponseEntity<ErrorResponse> response =
                handler.handleResponseStatusException(exception);

        final Instant afterCall = Instant.now();

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        final ErrorResponse error = response.getBody();
        assertNotNull(error);

        assertEquals("404", error.getError());
        assertEquals(reason, error.getMessage());
        assertEquals("test-trace-id", error.getTraceId());

        assertNotNull(error.getTimestamp());
        assertTrue(
                !error.getTimestamp().isBefore(beforeCall)
                        && !error.getTimestamp().isAfter(afterCall),
                "Timestamp should be between beforeCall and afterCall"
        );
    }

    /**
     * Verifies the fallback scenario where a {@link ResponseStatusException} has no explicit
     * reason, so the handler uses the exception message instead.
     */
    @Test
    void handle_response_status_exception_with_null_reason_should_use_message() {
        // Arrange
        final Tracer tracer = mock(Tracer.class);
        final Span span = mock(Span.class);
        final TraceContext context = mock(TraceContext.class);

        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("test-trace-id");

        final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracer);

        final ResponseStatusException exception =
                new ResponseStatusException(HttpStatus.BAD_REQUEST);

        // Act
        final ResponseEntity<ErrorResponse> response =
                handler.handleResponseStatusException(exception);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        final ErrorResponse error = response.getBody();
        assertNotNull(error);
        assertEquals("400", error.getError());
        assertNotNull(error.getMessage());
        assertThat(error.getMessage()).isNotBlank();
    }

    /**
     * Verifies the handler returns {@code no-trace} when no current span is available for a
     * response-status failure.
     */
    @Test
    void handle_response_status_exception_with_null_span_should_return_no_trace() {
        // Arrange
        final Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracer);

        final ResponseStatusException exception =
                new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error");

        // Act
        final ResponseEntity<ErrorResponse> response =
                handler.handleResponseStatusException(exception);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        final ErrorResponse error = response.getBody();
        assertNotNull(error);
        assertEquals("no-trace", error.getTraceId());
        assertEquals("500", error.getError());
        assertEquals("Server error", error.getMessage());
    }

    /**
     * Verifies uncaught exceptions are converted into a standard HTTP 500 payload with trace data.
     */
    @Test
    void handle_generic_exception_should_return_500_with_structured_error() {
        // Arrange
        final Tracer tracer = mock(Tracer.class);
        final Span span = mock(Span.class);
        final TraceContext context = mock(TraceContext.class);

        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("test-trace-id");

        final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracer);

        final Exception exception = new RuntimeException("Something went wrong");

        // Act
        final ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(exception);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        final ErrorResponse error = response.getBody();
        assertNotNull(error);
        assertEquals("500", error.getError());
        assertEquals("Internal server error", error.getMessage());
        assertEquals("test-trace-id", error.getTraceId());
        assertNotNull(error.getTimestamp());
    }

    /**
     * Verifies the generic-exception path still succeeds when tracing is unavailable.
     */
    @Test
    void handle_generic_exception_with_null_span_should_return_no_trace() {
        // Arrange
        final Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracer);

        final Exception exception = new RuntimeException("Something went wrong");

        // Act
        final ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(exception);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        final ErrorResponse error = response.getBody();
        assertNotNull(error);
        assertEquals("no-trace", error.getTraceId());
    }
}
