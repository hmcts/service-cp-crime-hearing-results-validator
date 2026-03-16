package uk.gov.hmcts.cp.controllers;

import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final Tracer tracer;

    public GlobalExceptionHandler(final Tracer tracer) {
        this.tracer = tracer;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            final ResponseStatusException responseStatusException) {

        final ErrorResponse error = ErrorResponse.builder()
                .error(String.valueOf(responseStatusException.getStatusCode().value()))
                .message(responseStatusException.getReason() != null
                        ? responseStatusException.getReason()
                        : responseStatusException.getMessage())
                .timestamp(Instant.now())
                .traceId(tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "no-trace")
                .build();

        return ResponseEntity
                .status(responseStatusException.getStatusCode())
                .body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            final HttpMessageNotReadableException exception) {

        final ErrorResponse error = ErrorResponse.builder()
                .error(String.valueOf(HttpStatus.BAD_REQUEST.value()))
                .message("Malformed request body")
                .timestamp(Instant.now())
                .traceId(tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "no-trace")
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(final Exception exception) {
        log.error("Unhandled exception", exception);

        final ErrorResponse error = ErrorResponse.builder()
                .error(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                .message("Internal server error")
                .timestamp(Instant.now())
                .traceId(tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "no-trace")
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}
