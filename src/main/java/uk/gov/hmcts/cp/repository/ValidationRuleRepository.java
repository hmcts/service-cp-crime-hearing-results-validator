package uk.gov.hmcts.cp.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;

/**
 * Repository for database-backed validation rule override rows.
 */
public interface ValidationRuleRepository extends JpaRepository<ValidationRuleEntity, String> {

    /**
     * Atomically inserts or partially updates a rule override row: only the non-null override
     * fields ({@code enabledOverride}, {@code severityOverride}) are written, and they are merged
     * against the row's value in the database at commit time -- not a value read earlier by the
     * caller. A read-then-merge-then-save round trip through the (30s TTL, per-pod) rule override
     * cache lets two concurrent or cross-pod PATCHes touching different fields silently revert
     * each other's change; this single atomic statement closes that race (DD-43134).
     *
     * @param id rule identifier (primary key)
     * @param enabledOverride requested enabled override, or {@code null} to leave it unchanged
     * @param severityOverride requested severity override, or {@code null} to leave it unchanged
     * @param defaultEnabled YAML-default enabled value, used only when inserting a brand-new row
     * @param defaultSeverity YAML-default severity value, used only when inserting a brand-new row
     * @param updatedAt audit timestamp
     * @param updatedBy audit actor
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO validation_rule (id, enabled, severity, updated_at, updated_by)
            VALUES (:id, COALESCE(:enabledOverride, :defaultEnabled),
                    COALESCE(:severityOverride, :defaultSeverity), :updatedAt, :updatedBy)
            ON CONFLICT (id) DO UPDATE SET
                enabled = COALESCE(:enabledOverride, validation_rule.enabled),
                severity = COALESCE(:severityOverride, validation_rule.severity),
                updated_at = :updatedAt,
                updated_by = :updatedBy
            """, nativeQuery = true)
    void upsertPartial(@Param("id") String id,
                        @Param("enabledOverride") Boolean enabledOverride,
                        @Param("severityOverride") String severityOverride,
                        @Param("defaultEnabled") boolean defaultEnabled,
                        @Param("defaultSeverity") String defaultSeverity,
                        @Param("updatedAt") Instant updatedAt,
                        @Param("updatedBy") String updatedBy);
}
