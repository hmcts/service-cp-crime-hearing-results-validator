package uk.gov.hmcts.cp.integration;

import ch.qos.logback.classic.AsyncAppender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the application JSON logging configuration in a Spring Boot test context.
 */
@Slf4j
@Disabled
class SpringLoggingIntegrationTest extends IntegrationTestBase {
    private PrintStream originalStdOut = System.out;

    @AfterEach
    void afterEach() {
        System.setOut(originalStdOut);
    }

    /**
     * Verifies a Spring-managed log event with MDC data and an exception is emitted as structured
     * JSON containing the expected core fields and stack trace text.
     */
    @Test
    void springboot_test_should_log_correct_fields_including_exception() throws IOException {
        MDC.put("any-mdc-field", "1234-1234");
        ByteArrayOutputStream capturedStdOut = captureStdOut();
        log.info("spring boot test message", new RuntimeException("MyException"));

        String logMessage = capturedStdOut.toString();
        AssertionsForClassTypes.assertThat(logMessage).isNotEmpty();
        Map<String, Object> capturedFields = new ObjectMapper().readValue(logMessage, new TypeReference<>() {
        });
        assertThat(capturedFields.get("any-mdc-field")).isEqualTo("1234-1234");
        assertThat(capturedFields.get("timestamp")).isNotNull();
        assertThat(capturedFields.get("logger_name")).isEqualTo("uk.gov.hmcts.cp.integration.SpringLoggingIntegrationTest");
        assertThat(capturedFields.get("thread_name")).isEqualTo("Test worker");
        assertThat(capturedFields.get("level")).isEqualTo("INFO");
        assertThat(capturedFields.get("message").toString()).contains("spring boot test message\njava.lang.RuntimeException: MyException");
        assertThat(capturedFields.get("message").toString()).contains("at uk.gov.hmcts.cp.integration.SpringLoggingIntegrationTest");
    }

    private ByteArrayOutputStream captureStdOut() {
        final ByteArrayOutputStream capturedStdOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdOut));
        return capturedStdOut;
    }
}
