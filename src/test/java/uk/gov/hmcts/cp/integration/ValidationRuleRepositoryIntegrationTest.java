package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the rule override JPA repository against the test database.
 */
class ValidationRuleRepositoryIntegrationTest extends IntegrationTestBase {

    @Resource
    private ValidationRuleRepository repository;

    /**
     * Verifies the Flyway seed data can be read back for the bundled DR-SENT-002 override row.
     */
    @Test
    void findById_should_return_seeded_rule() {
        Optional<ValidationRuleEntity> result = repository.findById("DR-SENT-002");

        assertThat(result).isPresent();
        assertThat(result.get().isEnabled()).isTrue();
        assertThat(result.get().getSeverity()).isEqualTo("ERROR");
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
}
