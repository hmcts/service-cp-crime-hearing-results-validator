package uk.gov.hmcts.cp.services.rules;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.repository.ValidationRuleRepository;

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
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // DB failures must not break validation
    public Optional<ValidationRuleEntity> findOverride(final String ruleId) {
        Optional<ValidationRuleEntity> result;
        try {
            result = ruleRepository.findById(ruleId);
        } catch (Exception e) {
            log.warn("Failed to load rule override for {}: {}", Encode.forJava(ruleId), e.getMessage());
            result = Optional.empty();
        }
        return result;
    }

    /**
     * Persists a rule override row and evicts the stale cache entry so the next evaluation
     * picks up the updated values without waiting for the TTL to expire.
     *
     * @param entity rule override entity to save
     * @return the saved entity as returned by the repository
     */
    @CacheEvict(value = "ruleOverrides", key = "#entity.id")
    public ValidationRuleEntity saveOverride(final ValidationRuleEntity entity) {
        return ruleRepository.save(entity);
    }

    /**
     * Atomically applies a partial PATCH (enabled and/or severity) as a single database
     * statement, merging against the row's current value rather than a value read earlier by the
     * caller -- see {@link ValidationRuleRepository#upsertPartial} for why this matters. Evicts
     * this pod's cache entry so the next evaluation on this pod picks up the change immediately;
     * other pods still see it within the cache's TTL.
     *
     * <p>The transaction boundary lives on {@code upsertPartial} itself (like {@link #saveOverride}
     * delegates to the individually-transactional {@code JpaRepository.save}), not on this method,
     * so eviction is guaranteed to run only after that write has committed. Stacking
     * {@code @Transactional} and {@code @CacheEvict} on the same method would leave their relative
     * advisor ordering unpinned (both default to the same precedence), risking an eviction that
     * fires before commit and gets silently repopulated with the stale pre-write value.
     *
     * @param ruleId identifier of the rule being updated
     * @param enabledOverride requested enabled override, or {@code null} to leave it unchanged
     * @param severityOverride requested severity override, or {@code null} to leave it unchanged
     * @param defaultEnabled YAML-default enabled value, used only if no row exists yet
     * @param defaultSeverity YAML-default severity value, used only if no row exists yet
     * @param updatedAt audit timestamp
     * @param updatedBy audit actor
     */
    @CacheEvict(value = "ruleOverrides", key = "#ruleId")
    public void applyPartialUpdate(final String ruleId,
                                    final Boolean enabledOverride,
                                    final String severityOverride,
                                    final boolean defaultEnabled,
                                    final String defaultSeverity,
                                    final Instant updatedAt,
                                    final String updatedBy) {
        ruleRepository.upsertPartial(ruleId, enabledOverride, severityOverride,
                defaultEnabled, defaultSeverity, updatedAt, updatedBy);
    }
}
