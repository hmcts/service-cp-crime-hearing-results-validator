package uk.gov.hmcts.cp.controllers;

import io.micrometer.tracing.Tracer;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import java.util.stream.Collectors;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Converts controller-layer exceptions into RFC 7807 Problem Detail responses.
 */
@RestControllerAdvice
@Order(-1)
@Slf4j
public class GlobalExceptionHandler {

    private final Tracer tracer;

    /** Constructs the exception handler with the given tracer for trace-id propagation. */
    public GlobalExceptionHandler(final Tracer tracer) {
        this.tracer = tracer;
    }

    /** Handles ResponseStatusException and returns a Problem Detail response. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(
            final ResponseStatusException responseStatusException) {

        final HttpStatus status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
        final ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(status.getReasonPhrase());
        problem.setDetail(responseStatusException.getReason() != null
                ? responseStatusException.getReason()
                : responseStatusException.getMessage());
        addTraceProperties(problem);

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** Handles bean validation failures on request bodies and returns a 400 Problem Detail response. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException exception) {

        final String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Validation failed" : detail);
        problem.setTitle("Bad Request");
        addTraceProperties(problem);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** Handles malformed request bodies and returns a 400 Problem Detail response. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(
            final HttpMessageNotReadableException exception) {

        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Malformed request body");
        problem.setTitle("Bad Request");
        addTraceProperties(problem);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** Catches all unhandled exceptions and returns a 500 Problem Detail response. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(final Exception exception) {
        log.error("Unhandled exception", exception);

        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        problem.setTitle("Internal Server Error");
        addTraceProperties(problem);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private void addTraceProperties(final ProblemDetail problem) {
        problem.setProperty("traceId",
                tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "no-trace");
        problem.setProperty("timestamp", Instant.now());
    }
}
