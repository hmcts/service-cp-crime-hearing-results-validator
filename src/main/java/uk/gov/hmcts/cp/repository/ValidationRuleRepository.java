package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;

/**
 * Repository for database-backed validation rule override rows.
 */
public interface ValidationRuleRepository extends JpaRepository<ValidationRuleEntity, String> {
}
