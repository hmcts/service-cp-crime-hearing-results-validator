package uk.gov.hmcts.cp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;

public interface ValidationRuleRepository extends JpaRepository<ValidationRuleEntity, String> {
}
