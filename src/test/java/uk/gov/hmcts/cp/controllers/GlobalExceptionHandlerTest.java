package uk.gov.hmcts.cp.controllers;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.TraceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.gov.hmcts.cp.exceptions.RuleNotFoundException;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 */
class GlobalExceptionHandlerTest {

    /**
     * Verifies that a missing rule is mapped to a 404 ErrorResponse with the rule id in the
     * message and the current trace id populated.
     */
    @Test
    void handle_rule_not_found_exception_should_return_404_error_response() {
        final Tracer tracer = mock(Tracer.class);
        final Span span = mock(Span.class);
        final TraceContext context = mock(TraceContext.class);

        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("trace-404");

        final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracer);

        final RuleNotFoundException exception = new RuleNotFoundException("DR-SENT-999");

        final ResponseEntity<ErrorResponse> response = handler.handleRuleNotFoundException(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

        final ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Rule not found", body.getError());
        assertThat(body.getMessage()).contains("DR-SENT-999");
        assertEquals("trace-404", body.getTraceId());
        assertNotNull(body.getTimestamp());
    }

    /**
     * Verifies that bean-validation failures are collected into a semicolon-separated detail
     * string and returned as a 400 ErrorResponse.
     */
    @Test
    void handle_method_argument_not_valid_with_field_errors_should_return_400_error_response() {
        final Tracer tracer = mock(Tracer.class);
        final Span span = mock(Span.class);
        final TraceContext context = mock(TraceContext.class);

        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("trace-abc");

        final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracer);

        final BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("request", "hearingId", "must not be blank"),
                new FieldError("request", "offences", "must not be empty")
        ));

        final MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        final ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

        final ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Bad Request", body.getError());
        assertThat(body.getMessage()).contains("hearingId: must not be blank");
        assertThat(body.getMessage()).contains("offences: must not be empty");
        assertEquals("trace-abc", body.getTraceId());
        assertNotNull(body.getTimestamp());
    }

    /**
     * Verifies that when there are no field errors the message falls back to "Validation failed".
     */
    @Test
    void handle_method_argument_not_valid_with_no_field_errors_should_return_validation_failed() {
        final Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracer);

        final BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        final MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        final ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        final ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Validation failed", body.getMessage());
        assertEquals("no-trace", body.getTraceId());
    }

    /**
     * Verifies that a malformed request body returns a 400 ErrorResponse with fixed message text.
     */
    @Test
    void handle_http_message_not_readable_should_return_400_with_malformed_body_message() {
        final Tracer tracer = mock(Tracer.class);
        final Span span = mock(Span.class);
        final TraceContext context = mock(TraceContext.class);

        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("trace-xyz");

        final GlobalExceptionHandler handler = new GlobalExceptionHandler(tracer);

        final HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);

        final ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadable(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());

        final ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Bad Request", body.getError());
        assertEquals("Malformed request body", body.getMessage());
        assertEquals("trace-xyz", body.getTraceId());
        assertNotNull(body.getTimestamp());
    }
}
