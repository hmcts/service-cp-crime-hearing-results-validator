package uk.gov.hmcts.cp.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for request tracing, MDC population and trace-header passthrough.
 */
class TracingIntegrationTest extends IntegrationTestBase {

    // Constants for tracing field names
    private static final String TRACE_ID_FIELD = "traceId";
    private static final String SPAN_ID_FIELD = "spanId";

    // Constants for test trace values
    private static final String TEST_TRACE_ID_1 = "test-trace-id-12345";
    private static final String TEST_SPAN_ID_1 = "test-span-id-67890";
    private static final String TEST_TRACE_ID_2 = "1234-1234";
    private static final String TEST_SPAN_ID_2 = "567-567";

    @Value("${spring.application.name}")
    private String springApplicationName;

    private final PrintStream originalStdOut = System.out;

    @BeforeEach
    void setup() {
        MDC.put(TRACE_ID_FIELD, TEST_TRACE_ID_1);
        MDC.put(SPAN_ID_FIELD, TEST_SPAN_ID_1);
        MDC.put("applicationName", springApplicationName);
    }

    @AfterEach
    void afterEach() {
        System.setOut(originalStdOut);
        MDC.clear();
    }

    /**
     * Verifies a request without incoming trace headers still produces tracing fields and
     * application metadata in controller logs.
     */
    @Test
    void incoming_request_should_add_new_tracing() throws Exception {
        final MvcResultHelper result = performRequestAndCaptureLogs("/api/validation/rules", null, null);
        final Map<String, Object> controllerLog = findControllerLog(result.capturedLogOutput());

        assertThat(controllerLog).isNotNull();
        assertNotNull(controllerLog.get(TRACE_ID_FIELD));
        assertNotNull(controllerLog.get(SPAN_ID_FIELD));
        assertThat(controllerLog).containsEntry("applicationName", springApplicationName);

        assertCommonLogFields(controllerLog);
    }

    /**
     * Verifies incoming trace and span headers are preserved in logs and echoed back on the
     * response.
     */
    @Test
    void incoming_request_with_traceId_should_pass_through() throws Exception {
        // Override the MDC with the header values that would be set by TracingFilter
        MDC.put(TRACE_ID_FIELD, TEST_TRACE_ID_2);
        MDC.put(SPAN_ID_FIELD, TEST_SPAN_ID_2);

        final MvcResultHelper result = performRequestAndCaptureLogs("/api/validation/rules", TEST_TRACE_ID_2, TEST_SPAN_ID_2);
        final Map<String, Object> controllerLog = findControllerLog(result.capturedLogOutput());

        assertTracingFields(controllerLog, TEST_TRACE_ID_2, TEST_SPAN_ID_2);
        assertResponseHeaders(result.mvcResult(), TEST_TRACE_ID_2, TEST_SPAN_ID_2);
    }

    private ByteArrayOutputStream captureStdOut() {
        final ByteArrayOutputStream capturedStdOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdOut, true, StandardCharsets.UTF_8));
        return capturedStdOut;
    }

    private MvcResultHelper performRequestAndCaptureLogs(final String path, final String traceId, final String spanId) throws Exception {
        final ByteArrayOutputStream capturedStdOut = captureStdOut();

        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get(path)
                .header("CJSCPPUID", "test-user")
                .header("CPP-ACTION", "validation-service.rules");
        if (traceId != null) {
            requestBuilder = requestBuilder.header(TRACE_ID_FIELD, traceId);
        }
        if (spanId != null) {
            requestBuilder = requestBuilder.header(SPAN_ID_FIELD, spanId);
        }

        final MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn();

        return new MvcResultHelper(result, capturedStdOut.toString(StandardCharsets.UTF_8));
    }

    private Map<String, Object> findControllerLog(final String logOutput) throws Exception {
        final String[] logLines = logOutput.split("\n");
        final ObjectMapper objectMapper = new ObjectMapper();
        final TypeReference<Map<String, Object>> typeReference = new TypeReference<>() {
        };

        Map<String, Object> stringObjectMap = null;
        for (final String logLine : logLines) {
            if (logLine.contains("ValidationRulesController") && stringObjectMap == null) {
                stringObjectMap = objectMapper.readValue(logLine, typeReference);
            }
        }

        if (stringObjectMap != null) {
            return stringObjectMap;
        } else {
            throw new AssertionError("Controller log message not found in output: " + logOutput);
        }
    }

    private void assertTracingFields(final Map<String, Object> log, final String expectedTraceId, final String expectedSpanId) {
        assertThat(log).isNotNull();
        assertEquals(log.get(TRACE_ID_FIELD), expectedTraceId);
        assertEquals(log.get(SPAN_ID_FIELD), expectedSpanId);
        assertEquals(log.get("applicationName"), springApplicationName);
    }

    private void assertCommonLogFields(final Map<String, Object> log) {
        assertEquals("uk.gov.hmcts.cp.controllers.ValidationRulesController", log.get("logger_name"));
        assertEquals(log.get("message"), "List validation rules for user=test-user\n");
    }

    private void assertResponseHeaders(final MvcResult result, final String expectedTraceId, final String expectedSpanId) {
        assertThat(result.getResponse().getHeader(TRACE_ID_FIELD)).isEqualTo(expectedTraceId);
        assertThat(result.getResponse().getHeader(SPAN_ID_FIELD)).isEqualTo(expectedSpanId);
    }

    // Helper class to encapsulate MvcResult and captured log output
    private record MvcResultHelper(MvcResult mvcResult, String capturedLogOutput) {
    }
}
