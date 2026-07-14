package uk.gov.hmcts.cp.services.rules;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ValidationIssueRecorder}.
 */
class ValidationIssueRecorderTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ValidationIssueRecorder recorder = new ValidationIssueRecorder(meterRegistry);

    /**
     * Verifies a triggered issue increments the counter tagged with the rule, condition and
     * severity identifiers.
     */
    @Test
    void record_should_increment_counter_with_rule_condition_severity_tags() {
        recorder.record("DR-SENT-002", "AC2", ValidationIssue.SeverityEnum.ERROR, "h1",
                "Custodial sentence concurrent/consecutive check",
                "Multiple offences missing info",
                ValidationIssue.ValidationLevelEnum.OFFENCE);

        Counter counter = meterRegistry.find(ValidationIssueRecorder.COUNTER_NAME)
                .tag("ruleId", "DR-SENT-002")
                .tag("conditionId", "AC2")
                .tag("severity", "ERROR")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    /**
     * Verifies distinct conditions/severities accumulate as separate counter series rather than
     * collapsing into one.
     */
    @Test
    void record_should_keep_separate_series_per_condition_and_severity() {
        recorder.record("DR-SENT-002", "AC2", ValidationIssue.SeverityEnum.ERROR, "h1",
                "Custodial sentence concurrent/consecutive check",
                "Multiple offences missing info",
                ValidationIssue.ValidationLevelEnum.OFFENCE);
        recorder.record("DR-SENT-002", "AC2", ValidationIssue.SeverityEnum.ERROR, "h1",
                "Custodial sentence concurrent/consecutive check",
                "Multiple offences missing info",
                ValidationIssue.ValidationLevelEnum.OFFENCE);
        recorder.record("DR-SENT-002", "AC3", ValidationIssue.SeverityEnum.WARNING, "h1",
                "Custodial sentence concurrent/consecutive check",
                "Both concurrent and consecutive",
                ValidationIssue.ValidationLevelEnum.OFFENCE);

        assertThat(meterRegistry.find(ValidationIssueRecorder.COUNTER_NAME)
                .tag("conditionId", "AC2").counter().count()).isEqualTo(2.0);
        assertThat(meterRegistry.find(ValidationIssueRecorder.COUNTER_NAME)
                .tag("conditionId", "AC3").counter().count()).isEqualTo(1.0);
    }

    /**
     * Verifies a null severity is recorded safely rather than throwing.
     */
    @Test
    void record_should_tolerate_null_severity() {
        recorder.record("DR-SENT-002", "AC2", null, "h1",
                "Custodial sentence concurrent/consecutive check",
                "Multiple offences missing info",
                ValidationIssue.ValidationLevelEnum.OFFENCE);

        assertThat(meterRegistry.find(ValidationIssueRecorder.COUNTER_NAME)
                .tag("severity", "UNKNOWN").counter().count()).isEqualTo(1.0);
    }

    /**
     * Verifies a null validation level is recorded safely rather than throwing.
     */
    @Test
    void record_should_tolerate_null_validation_level() {
        assertThatCode(() -> recorder.record("DR-SENT-002", "AC2",
                ValidationIssue.SeverityEnum.ERROR, "h1",
                "Custodial sentence concurrent/consecutive check",
                "Multiple offences missing info",
                null))
                .doesNotThrowAnyException();
    }

    /**
     * Verifies the recorder never propagates a failure from the metrics subsystem, so observability
     * cannot suppress a validation result. Uses a real recorder with a throwing registry — a mock
     * recorder could not prove the production fail-safe.
     */
    @Test
    void record_should_not_propagate_metrics_failure() {
        MeterRegistry throwingRegistry = mock(MeterRegistry.class);
        when(throwingRegistry.counter(anyString(), any(String[].class)))
                .thenThrow(new RuntimeException("metrics subsystem down"));
        ValidationIssueRecorder failSafeRecorder = new ValidationIssueRecorder(throwingRegistry);

        assertThatCode(() -> failSafeRecorder.record(
                "DR-SENT-002", "AC2", ValidationIssue.SeverityEnum.ERROR, "h1",
                "Custodial sentence concurrent/consecutive check",
                "Multiple offences missing info",
                ValidationIssue.ValidationLevelEnum.OFFENCE))
                .doesNotThrowAnyException();
    }
}
