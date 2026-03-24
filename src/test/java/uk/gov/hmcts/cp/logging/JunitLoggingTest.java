package uk.gov.hmcts.cp.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
/**
 * Verifies the JSON logging configuration used during plain JUnit execution.
 */
class JunitLoggingTest {

    /**
     * Covers the scenario where a standard JUnit log line is emitted with MDC data and should be
     * rendered as structured JSON with the expected core fields.
     */
    @Test
    void junit_should_log_correct_fields() throws JsonProcessingException {
        MDC.put("traceId", "1234-1234");
        final ByteArrayOutputStream capturedStdOut = captureStdOut();
        log.info("junit test message");

        final Map<String, Object> capturedFields = new ObjectMapper().readValue(capturedStdOut.toString(StandardCharsets.UTF_8), new TypeReference<>() {
        });

        assertThat(capturedFields)
                .containsEntry("traceId", "1234-1234")
                .containsKey("timestamp") // or keep .get("timestamp").isNotNull()
                .containsEntry("logger_name", "uk.gov.hmcts.cp.logging.JunitLoggingTest")
                .containsEntry("thread_name", "Test worker")
                .containsEntry("level", "INFO")
                .containsEntry("message", "junit test message\n");
    }

    private ByteArrayOutputStream captureStdOut() {
        final ByteArrayOutputStream capturedStdOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdOut, true, StandardCharsets.UTF_8));
        return capturedStdOut;
    }
}
