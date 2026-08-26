package uk.gov.hmcts.cp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;

/**
 * Integration tests for the rule override JPA repository against the test database.
 */
class ValidationRuleRepositoryIntegrationTest extends IntegrationTestBase {

    private static final String UPSERT_TEST_ID = "UPSERT-TEST-001";

    @Resource
    private ValidationRuleRepository repository;

    @Resource
    private RuleOverrideService ruleOverrideService;

    @AfterEach
    void cleanUpUpsertTestRow() {
        if (repository.existsById(UPSERT_TEST_ID)) {
            repository.deleteById(UPSERT_TEST_ID);
        }
    }

    /**
     * Verifies the Flyway seed data can be read back for the bundled DR-SENT-001 override row.
     */
    @Test
    void findById_should_return_seeded_rule() {
        Optional<ValidationRuleEntity> result = repository.findById("DR-SENT-001");

        assertThat(result).isPresent();
        assertThat(result.get().isEnabled()).isTrue();
        assertThat(result.get().getSeverity()).isEqualTo("ERROR");
    }

    /**
     * Verifies the Flyway seed data can be read back for the bundled DR-DISQ-002 override row.
     */
    @Test
    void findById_should_return_seeded_dr_disq_002_rule() {
        Optional<ValidationRuleEntity> result = repository.findById("DR-DISQ-002");

        assertThat(result).isPresent();
        assertThat(result.get().isEnabled()).isTrue();
        assertThat(result.get().getSeverity()).isEqualTo("WARNING");
    }

    /**
     * Verifies unknown identifiers are returned as empty optionals rather than errors.
     */
    @Test
    void findById_should_return_empty_for_unknown_id() {
        Optional<ValidationRuleEntity> result = repository.findById("UNKNOWN-RULE");

        assertThat(result).isEmpty();
    }

    /**
     * Verifies a new override row can be saved and read back with all persisted fields intact.
     */
    @Test
    void save_and_retrieve_should_roundtrip() {
        ValidationRuleEntity entity = ValidationRuleEntity.builder()
                .id("TEST-001")
                .enabled(false)
                .severity("WARNING")
                .updatedAt(Instant.now())
                .updatedBy("test-user")
                .build();

        repository.save(entity);

        Optional<ValidationRuleEntity> result = repository.findById("TEST-001");
        assertThat(result).isPresent();
        assertThat(result.get().isEnabled()).isFalse();
        assertThat(result.get().getSeverity()).isEqualTo("WARNING");
        assertThat(result.get().getUpdatedBy()).isEqualTo("test-user");

        repository.deleteById("TEST-001");
    }

    /**
     * {@link ValidationRuleRepository#upsertPartial}'s INSERT branch: for a brand-new row, an
     * unrequested field (severity here) falls back to the supplied YAML default rather than being
     * left null.
     */
    @Test
    void applyPartialUpdate_withNoExistingRow_should_insert_using_defaults_for_untouched_field() {
        ruleOverrideService.applyPartialUpdate(
                UPSERT_TEST_ID, false, null, true, "ERROR", Instant.now(), "test-user");

        Optional<ValidationRuleEntity> result = repository.findById(UPSERT_TEST_ID);
        assertThat(result).isPresent();
        assertThat(result.get().isEnabled()).isFalse();
        assertThat(result.get().getSeverity()).isEqualTo("ERROR");
    }

    /**
     * Regression test for DD-43134 (PR review, item 6): two partial updates touching different
     * fields must not revert each other. Under the old read-modify-write-via-cached-entity flow,
     * the second call's write would re-persist whatever it had read for the field it wasn't
     * changing — silently discarding the first call's change if that read happened to predate it.
     * The atomic {@code COALESCE}-based upsert merges against the database's row at commit time,
     * not an application-level read, so this can't happen regardless of ordering or staleness.
     */
    @Test
    void applyPartialUpdate_two_calls_touching_different_fields_should_not_revert_each_other() {
        ruleOverrideService.applyPartialUpdate(
                UPSERT_TEST_ID, true, "ERROR", true, "ERROR", Instant.now(), "seed");

        // Call 1: PATCH enabled=false, severity untouched.
        ruleOverrideService.applyPartialUpdate(
                UPSERT_TEST_ID, false, null, true, "ERROR", Instant.now(), "user-a");

        // Call 2: PATCH severity=WARNING, enabled untouched. If this were a stale-read
        // merge rather than an atomic one, it could re-write enabled=true here.
        ruleOverrideService.applyPartialUpdate(
                UPSERT_TEST_ID, null, "WARNING", true, "ERROR", Instant.now(), "user-b");

        Optional<ValidationRuleEntity> result = repository.findById(UPSERT_TEST_ID);
        assertThat(result).isPresent();
        assertThat(result.get().isEnabled()).as("call 1's enabled=false must survive call 2").isFalse();
        assertThat(result.get().getSeverity()).isEqualTo("WARNING");
    }
}
