package uk.gov.hmcts.cp.services.feature;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Feature toggle backed by Azure App Configuration. When Azure is unreachable, not configured, or
 * the feature is not found, returns the caller's default ({@code true} for the single-arg form).
 */
@Service
@Slf4j
public class AzureFeatureToggleService implements FeatureToggleService {

    private final AzureAppConfigFetcher fetcher;
    private final String label;

    /** Creates the service with the given config fetcher and environment label. */
    public AzureFeatureToggleService(
            final AzureAppConfigFetcher fetcher,
            @Value("${feature.label:}") final String label) {
        this.fetcher = fetcher;
        this.label = label;
    }

    @Override
    public boolean isFeatureEnabled(final String featureName) {
        return isFeatureEnabled(featureName, true);
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public boolean isFeatureEnabled(final String featureName, final boolean defaultValue) {
        boolean enabled = defaultValue;

        if (label.isBlank()) {
            log.debug("Feature label not configured, defaulting to {} for '{}'", defaultValue, featureName);
        } else {
            try {
                final Map<String, Boolean> features = fetcher.fetchFeatures(label);
                enabled = features.getOrDefault(featureName, defaultValue);
            } catch (Exception e) {
                log.warn("Feature toggle check failed for '{}', defaulting to {}: {}",
                        featureName, defaultValue, e.getMessage());
            }
        }

        return enabled;
    }
}
