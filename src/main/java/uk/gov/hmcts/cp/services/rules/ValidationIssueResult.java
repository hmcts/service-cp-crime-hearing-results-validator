package uk.gov.hmcts.cp.services.rules;

import uk.gov.hmcts.cp.openapi.model.ValidationIssue;

/**
 * Pairs a {@link ValidationIssue} with optional error message metadata produced by an
 * {@code errorMessageTemplate} in the rule YAML. The error message is surfaced in
 * {@code DraftValidationResponse.errorMessages}; the issue itself goes into {@code errors}.
 * When {@code affectedDefendantName} is non-null, {@link uk.gov.hmcts.cp.services.impl.DefaultValidationService}
 * groups results by rule and appends "This affects &lt;names&gt;." to the base message.
 *
 * @param issue                the validation issue
 * @param errorMessage         optional base error message (null for WARNING issues)
 * @param affectedDefendantName optional defendant name to group into the combined error message
 */
public record ValidationIssueResult(ValidationIssue issue, String errorMessage, String affectedDefendantName) {

    /** Convenience factory for issues that carry no top-level error message (WARNING severity). */
    public static ValidationIssueResult of(final ValidationIssue issue) {
        return new ValidationIssueResult(issue, null, null);
    }
}
