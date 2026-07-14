package uk.gov.hmcts.cp.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;
import uk.gov.hmcts.cp.services.rules.ValidationIssueRecorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the recorder's structured log line surfaces as top-level JSON fields when emitted
 * through the application's real {@code logback.xml}. Driving the production config (rather than a
 * hand-built appender) is deliberate: it fails if the {@code <arguments/>} provider is ever dropped.
 */
class ValidationIssueRecorderLoggingIntegrationTest extends IntegrationTestBase {

    private final PrintStream originalStdOut = System.out;

    @Resource
    private ValidationIssueRecorder recorder;

    @AfterEach
    void afterEach() {
        System.setOut(originalStdOut);
        MDC.clear();
    }

    /**
     * Verifies the issue log line carries rule/condition/severity/hearing as structured fields plus
     * the MDC validation and correlation ids, with a fixed non-PII message and no issue detail.
     */
    @Test
    void record_should_emit_structured_json_fields_via_production_logback() throws IOException {
        MDC.put("validationId", "val-test-123");
        MDC.put("clientCorrelationId", "corr-abc");
        ByteArrayOutputStream capturedStdOut = captureStdOut();

        recorder.record("DR-SENT-002", "AC2", ValidationIssue.SeverityEnum.ERROR, "hearing-xyz",
                "Validates that custodial sentences have correct concurrent/consecutive "
                        + "information per defendant across all cases in a hearing.",
                "Multiple offences missing info",
                ValidationIssue.ValidationLevelEnum.OFFENCE);

        String issueLine = Arrays.stream(capturedStdOut.toString().split(System.lineSeparator()))
                .filter(line -> line.contains(ValidationIssueRecorder.ISSUE_LOG_MESSAGE))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No issue log line captured"));

        Map<String, Object> fields = new ObjectMapper().readValue(issueLine, new TypeReference<>() {
        });
        assertThat(fields.get("ruleId")).isEqualTo("DR-SENT-002");
        assertThat(fields.get("conditionId")).isEqualTo("AC2");
        assertThat(fields.get("severity")).isEqualTo("ERROR");
        assertThat(fields.get("hearingId")).isEqualTo("hearing-xyz");
        assertThat(fields.get("validationId")).isEqualTo("val-test-123");
        assertThat(fields.get("clientCorrelationId")).isEqualTo("corr-abc");
        assertThat(fields.get("ruleDescription")).isEqualTo(
                "Validates that custodial sentences have correct concurrent/consecutive "
                        + "information per defendant across all cases in a hearing.");
        assertThat(fields.get("conditionDescription")).isEqualTo("Multiple offences missing info");
        assertThat(fields.get("validationLevel")).isEqualTo("OFFENCE");
        assertThat(fields.get("message").toString())
                .contains(ValidationIssueRecorder.ISSUE_LOG_MESSAGE);
        // No PII / issue detail leaks into the log.
        assertThat(fields).doesNotContainKeys("affectedOffences", "defendantName");
    }

    private ByteArrayOutputStream captureStdOut() {
        final ByteArrayOutputStream capturedStdOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdOut));
        return capturedStdOut;
    }
}
