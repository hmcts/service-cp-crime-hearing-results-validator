package uk.gov.hmcts.cp.services.feature;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Feature flag names that map to entries in Azure App Configuration.
 */
@Getter
@RequiredArgsConstructor
public enum FeatureToggleConstants {

    RESULTS_VALIDATION("ResultsValidation");

    private final String featureName;
}
