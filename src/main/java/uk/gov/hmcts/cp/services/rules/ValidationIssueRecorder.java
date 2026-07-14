package uk.gov.hmcts.cp.services.rules;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.ValidationIssue;

/**
 * Emits per-issue monitoring signals when a validation condition triggers: a Micrometer counter
 * (for Grafana/Prometheus dashboards of "where mistakes are made") and a structured log line (for
 * ad-hoc querying in the JSON log stack).
 *
 * <p>The counter is tagged only with low-cardinality identifiers ({@code ruleId},
 * {@code conditionId}, {@code severity}); high-cardinality values ({@code hearingId},
 * {@code validationId}) are deliberately kept out of metric tags. The log line carries identifiers
 * only — never the resolved message text or affected offences, which can contain PII.
 *
 * <p>Recording is observability and must never alter a validation result. Each signal is emitted in
 * its own fail-safe block so a failure in one (or in the metrics/logging subsystem) cannot suppress
 * the other, nor the validation issue itself.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ValidationIssueRecorder {

    /** Fixed, non-PII log message; structured detail rides the key-value arguments and MDC. */
    public static final String ISSUE_LOG_MESSAGE = "Validation issue triggered";

    /** Counter name; rendered as {@code validation_rule_issues_total} in Prometheus. */
    public static final String COUNTER_NAME = "validation.rule.issues";

    private final MeterRegistry meterRegistry;

    /**
     * Records a single triggered validation issue as a counter increment and a structured log line.
     *
     * @param ruleId rule that triggered (e.g. {@code DR-SENT-002})
     * @param conditionId condition within the rule that triggered (e.g. {@code AC2})
     * @param severity resolved severity of the issue
     * @param hearingId hearing the draft results belong to
     * @param ruleDescription human-readable description of the rule, from the YAML {@code description}
     * @param conditionDescription human-readable description of the condition, from the YAML
     *     {@code name}
     * @param validationLevel scope of the condition (offence or defendant level), from the YAML
     *     {@code validationLevel}
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // observability must never throw
    public void record(final String ruleId, final String conditionId,
                       final ValidationIssue.SeverityEnum severity, final String hearingId,
                       final String ruleDescription, final String conditionDescription,
                       final ValidationIssue.ValidationLevelEnum validationLevel) {
        final String severityName = severity == null ? "UNKNOWN" : severity.name();
        final String validationLevelName = validationLevel == null ? "UNKNOWN" : validationLevel.name();

        try {
            meterRegistry.counter(COUNTER_NAME,
                    "ruleId", ruleId,
                    "conditionId", conditionId,
                    "severity", severityName).increment();
        } catch (Exception e) {
            log.warn("Failed to record validation issue metric for ruleId={} conditionId={}: {}",
                    ruleId, conditionId, e.getMessage());
        }

        try {
            log.info(ISSUE_LOG_MESSAGE,
                    kv("ruleId", ruleId),
                    kv("conditionId", conditionId),
                    kv("severity", severityName),
                    kv("hearingId", hearingId),
                    kv("ruleDescription", ruleDescription),
                    kv("conditionDescription", conditionDescription),
                    kv("validationLevel", validationLevelName));
        } catch (Exception e) {
            log.warn("Failed to log validation issue for ruleId={} conditionId={}: {}",
                    ruleId, conditionId, e.getMessage());
        }
    }
}
