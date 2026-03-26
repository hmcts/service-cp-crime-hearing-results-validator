package uk.gov.hmcts.cp.services.feature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * Feature toggle backed by Azure App Configuration. Fail-open: returns {@code true}
 * (feature enabled) when Azure is unreachable, not configured, or feature not found.
 */
@Service
@Slf4j
public class AzureFeatureToggleService implements FeatureToggleService {

    private final AzureAppConfigFetcher fetcher;
    private final String label;

    public AzureFeatureToggleService(
            final AzureAppConfigFetcher fetcher,
            @Value("${feature.label:}") final String label) {
        this.fetcher = fetcher;
        this.label = label;
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public boolean isFeatureEnabled(final String featureName) {
        boolean enabled = true;

        if (label.isBlank()) {
            log.debug("Feature label not configured, defaulting to enabled for '{}'", featureName);
        } else {
            try {
                final Map<String, Boolean> features = fetchAllFeatures();
                enabled = features.getOrDefault(featureName, true);
            } catch (Exception e) {
                log.warn("Feature toggle check failed for '{}', defaulting to enabled: {}",
                        featureName, e.getMessage());
            }
        }

        return enabled;
    }

    /**
     * Fetches all feature flags from Azure App Configuration. The result is cached
     * by Spring's cache abstraction with the "featureFlags" cache name.
     *
     * @return map of feature name to enabled status
     */
    @Cacheable(value = "featureFlags", key = "#root.target.label")
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Map<String, Boolean> fetchAllFeatures() {
        Map<String, Boolean> result = Collections.emptyMap();
        try {
            result = fetcher.fetchFeatures(label);
        } catch (Exception e) {
            log.warn("Failed to fetch feature flags: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Exposes the label for use as a cache key via SpEL.
     *
     * @return the configured feature label
     */
    public String getLabel() {
        return label;
    }
}
