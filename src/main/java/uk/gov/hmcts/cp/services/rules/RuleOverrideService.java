package uk.gov.hmcts.cp.services.rules;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleOverrideService {

    private final ValidationRuleRepository ruleRepository;

    @Cacheable(value = "ruleOverrides", key = "#ruleId")
    public Optional<ValidationRuleEntity> findOverride(String ruleId) {
        try {
            return ruleRepository.findById(ruleId);
        } catch (Exception e) {
            log.warn("Failed to load rule override for {}: {}", ruleId, e.getMessage());
            return Optional.empty();
        }
    }
}
