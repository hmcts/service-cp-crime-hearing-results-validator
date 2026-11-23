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

    /**
     * Returns whether the named feature is enabled, falling back to {@code defaultValue} when the
     * feature store is unavailable, not configured, or the feature is not found.
     *
     * <p>The default implementation cannot tell "not found" from a real value, so it delegates to
     * {@link #isFeatureEnabled(String)}; implementations backed by a real store override it.
     *
     * @param featureName the feature flag name
     * @param defaultValue the value to use when the flag cannot be resolved
     * @return whether the feature is enabled
     */
    default boolean isFeatureEnabled(final String featureName, final boolean defaultValue) {
        return isFeatureEnabled(featureName);
    }
}
