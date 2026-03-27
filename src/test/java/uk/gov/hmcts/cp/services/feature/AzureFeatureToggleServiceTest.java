package uk.gov.hmcts.cp.services.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AzureFeatureToggleService}.
 */
@ExtendWith(MockitoExtension.class)
class AzureFeatureToggleServiceTest {

    @Mock
    private AzureAppConfigFetcher fetcher;

    @Test
    void isFeatureEnabled_returns_true_when_feature_enabled_in_azure() {
        when(fetcher.fetchFeatures("STE86")).thenReturn(Map.of("ResultsValidation", true));
        AzureFeatureToggleService service = new AzureFeatureToggleService(fetcher, "STE86");

        assertThat(service.isFeatureEnabled("ResultsValidation")).isTrue();
    }

    @Test
    void isFeatureEnabled_returns_false_when_feature_disabled_in_azure() {
        when(fetcher.fetchFeatures("STE86")).thenReturn(Map.of("ResultsValidation", false));
        AzureFeatureToggleService service = new AzureFeatureToggleService(fetcher, "STE86");

        assertThat(service.isFeatureEnabled("ResultsValidation")).isFalse();
    }

    @Test
    void isFeatureEnabled_returns_true_when_feature_not_found() {
        when(fetcher.fetchFeatures("STE86")).thenReturn(Map.of("OtherFeature", true));
        AzureFeatureToggleService service = new AzureFeatureToggleService(fetcher, "STE86");

        assertThat(service.isFeatureEnabled("ResultsValidation")).isTrue();
    }

    @Test
    void isFeatureEnabled_returns_true_when_fetcher_throws() {
        when(fetcher.fetchFeatures("STE86")).thenThrow(new RuntimeException("Azure unavailable"));
        AzureFeatureToggleService service = new AzureFeatureToggleService(fetcher, "STE86");

        assertThat(service.isFeatureEnabled("ResultsValidation")).isTrue();
    }

    @Test
    void isFeatureEnabled_returns_true_when_label_blank() {
        AzureFeatureToggleService service = new AzureFeatureToggleService(fetcher, "");

        assertThat(service.isFeatureEnabled("ResultsValidation")).isTrue();
    }

    @Test
    void isFeatureEnabled_returns_true_when_fetcher_returns_empty_map() {
        when(fetcher.fetchFeatures("STE86")).thenReturn(Collections.emptyMap());
        AzureFeatureToggleService service = new AzureFeatureToggleService(fetcher, "STE86");

        assertThat(service.isFeatureEnabled("ResultsValidation")).isTrue();
    }
}
