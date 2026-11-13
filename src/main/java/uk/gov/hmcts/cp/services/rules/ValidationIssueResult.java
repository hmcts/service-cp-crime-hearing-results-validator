package uk.gov.hmcts.cp.services.rules;

import uk.gov.hmcts.cp.openapi.model.ValidationIssue;

/**
 * Pairs a {@link ValidationIssue} with optional error message metadata produced by an
 * {@code errorMessageTemplate} in the rule YAML. The error message is surfaced in
 * {@code DraftValidationResponse.errorMessages}; the issue itself goes into {@code errors}.
 * When {@code affectedDefendantName} is non-null, {@link uk.gov.hmcts.cp.services.impl.DefaultValidationService}
 * groups results by rule and appends "This affects &lt;names&gt;." to the base message -- unless
 * the hearing has only one defendant, in which case that clause is omitted as redundant (see
 * {@link uk.gov.hmcts.cp.services.rules.cel.MessageTemplateResolver#resolveDefendantNames}).
 *
 * @param issue                the validation issue
 * @param errorMessage         optional base error message (null for WARNING issues)
 * @param affectedDefendantName optional defendant name to group into the combined error message
 * @param affectedDefendantKey  optional defendant identity (dedupe key); when non-null, results of
 *                              the same rule and message sharing it name that defendant only once
 */
public record ValidationIssueResult(ValidationIssue issue, String errorMessage, String affectedDefendantName,
                                    String affectedDefendantKey) {

    /** Convenience factory for issues that carry no top-level error message (WARNING severity). */
    public static ValidationIssueResult forWarning(final ValidationIssue issue) {
        return new ValidationIssueResult(issue, null, null, null);
    }

    /** Convenience factory for ERROR issues carrying an error message and optional defendant name. */
    public static ValidationIssueResult forError(final ValidationIssue issue,
                                                  final String errorMessage,
                                                  final String affectedDefendantName) {
        return new ValidationIssueResult(issue, errorMessage, affectedDefendantName, null);
    }

    /**
     * As {@link #forError(ValidationIssue, String, String)}, also carrying the defendant's identity
     * so a defendant named by several results (e.g. one per offence) is listed once.
     */
    public static ValidationIssueResult forError(final ValidationIssue issue,
                                                  final String errorMessage,
                                                  final String affectedDefendantName,
                                                  final String affectedDefendantKey) {
        return new ValidationIssueResult(issue, errorMessage, affectedDefendantName, affectedDefendantKey);
    }
}
