package uk.gov.hmcts.cp.controllers;

import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uk.gov.hmcts.cp.exceptions.InvalidRuleUpdateException;
import uk.gov.hmcts.cp.exceptions.RuleNotFoundException;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;

/**
 * Maps exceptions to ErrorResponse as defined in the OpenAPI spec.
 * Handles 400 (Bad Request) and 404 (Not Found) per the spec, plus a 500 (Internal Server
 * Error) fallback for any uncaught exception so the client never sees a non-ErrorResponse body.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    private final Tracer tracer;

    /** Constructs the exception handler with the given tracer for trace-id propagation. */
    public GlobalExceptionHandler(final Tracer tracer) {
        this.tracer = tracer;
    }

    /** Handles rule-not-found conditions and returns a 404 ErrorResponse. */
    @ExceptionHandler(RuleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRuleNotFoundException(
            final RuleNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse()
                        .error("Rule not found")
                        .message(exception.getMessage())
                        .traceId(resolveTraceId())
                        .timestamp(Instant.now()));
    }

    /** Handles invalid rule update requests and returns a 400 ErrorResponse. */
    @ExceptionHandler(InvalidRuleUpdateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRuleUpdateException(
            final InvalidRuleUpdateException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse()
                        .error("Bad Request")
                        .message(exception.getMessage())
                        .traceId(resolveTraceId())
                        .timestamp(Instant.now()));
    }

    /** Handles bean validation failures on request bodies and returns a 400 ErrorResponse. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException exception) {

        final String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse()
                        .error("Bad Request")
                        .message(detail.isEmpty() ? "Validation failed" : detail)
                        .traceId(resolveTraceId())
                        .timestamp(Instant.now()));
    }

    /** Handles malformed request bodies and returns a 400 ErrorResponse. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            final HttpMessageNotReadableException exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse()
                        .error("Bad Request")
                        .message("Malformed request body")
                        .traceId(resolveTraceId())
                        .timestamp(Instant.now()));
    }

    /** Handles any uncaught exception and returns a 500 ErrorResponse without leaking internal details. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(final Exception exception) {
        log.error("Unhandled exception", exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse()
                        .error("Internal Server Error")
                        .message("An unexpected error occurred")
                        .traceId(resolveTraceId())
                        .timestamp(Instant.now()));
    }

    private String resolveTraceId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "no-trace";
    }
}
