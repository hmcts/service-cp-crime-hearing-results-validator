package uk.gov.hmcts.cp.services.feature;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Feature flag names that map to entries in Azure App Configuration.
 */
@Getter
@RequiredArgsConstructor
public enum FeatureToggleConstants {

    /** Master switch for validation. Fail-open: missing/unreachable means enabled. */
    RESULTS_VALIDATION("ResultsValidation", true),

    /**
     * When enabled, hearings containing a civil offence ({@code OffenceDto.isCivil == true}) are
     * validated against every rule. When disabled, such hearings skip all rules. Fail-closed:
     * missing/unreachable means disabled, so civil hearings are skipped unless explicitly enabled.
     */
    VALIDATE_CIVIL_HEARINGS("ValidateCivilHearings", false);

    private final String featureName;

    /** Value used when the flag is missing from Azure, no label is configured, or the lookup fails. */
    private final boolean defaultEnabled;
}
