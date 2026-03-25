package uk.gov.hmcts.cp.services.rules;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;

import java.util.Optional;

/**
 * Loads optional runtime overrides for validation rules from the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleOverrideService {

    private final ValidationRuleRepository ruleRepository;

    /**
     * Returns the persisted override for a rule when one exists.
     *
     * <p>Failures are treated as non-fatal so validation can continue with the YAML definition.</p>
     *
     * @param ruleId identifier of the rule being evaluated
     * @return optional override row for the rule
     */
    @Cacheable(value = "ruleOverrides", key = "#ruleId")
    public Optional<ValidationRuleEntity> findOverride(final String ruleId) {
        Optional<ValidationRuleEntity> result;
        try {
            result = ruleRepository.findById(ruleId);
        } catch (Exception e) {
            log.warn("Failed to load rule override for {}: {}", ruleId, e.getMessage());
            result = Optional.empty();
        }
        return result;
    }
}
