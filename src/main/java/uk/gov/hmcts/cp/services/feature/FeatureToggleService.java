package uk.gov.hmcts.cp.services.feature;

/**
 * Checks whether a named feature is enabled at runtime.
 */
public interface FeatureToggleService {

    /**
     * Returns {@code true} if the named feature is enabled, {@code false} if disabled.
     * Implementations should fail-open: return {@code true} when the feature store is
     * unavailable or the feature is not found.
     *
     * @param featureName the feature flag name
     * @return whether the feature is enabled
     */
    boolean isFeatureEnabled(String featureName);
}
