package uk.gov.hmcts.cp.services.feature;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Feature toggle backed by Azure App Configuration. Fail-open: returns {@code true}
 * (feature enabled) when Azure is unreachable, not configured, or feature not found.
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
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public boolean isFeatureEnabled(final String featureName) {
        boolean enabled = true;

        if (label.isBlank()) {
            log.debug("Feature label not configured, defaulting to enabled for '{}'", featureName);
        } else {
            try {
                final Map<String, Boolean> features = fetcher.fetchFeatures(label);
                enabled = features.getOrDefault(featureName, true);
            } catch (Exception e) {
                log.warn("Feature toggle check failed for '{}', defaulting to enabled: {}",
                        featureName, e.getMessage());
            }
        }

        return enabled;
    }
}
