package uk.gov.hmcts.cp.controllers;

import java.time.Instant;

/** Structured error response body for 400 Bad Request responses. */
public record ErrorResponse(int status, String title, String detail, String traceId, Instant timestamp) {
}
