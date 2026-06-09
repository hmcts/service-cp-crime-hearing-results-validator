package uk.gov.hmcts.cp.services.rules;

import uk.gov.hmcts.cp.openapi.model.ValidationIssue;

/**
 * Pairs a {@link ValidationIssue} with optional error message metadata produced by an
 * {@code errorMessageTemplate} in the rule YAML. The error message is surfaced in
 * {@code DraftValidationResponse.errorMessages}; the issue itself goes into {@code errors}.
 * When {@code affectedDefendantName} is non-null, {@link uk.gov.hmcts.cp.services.impl.DefaultValidationService}
 * groups results by {@code ruleId + "::" + conditionId} and substitutes the accumulated
 * defendant names into the {@code ${defendantNames}} placeholder in the base message.
 *
 * @param issue                 the validation issue
 * @param errorMessage          optional base error message with {@code ${defendantNames}} placeholder
 *                              (null for WARNING issues or errors without an errorMessageTemplate)
 * @param affectedDefendantName optional defendant name to accumulate into the combined message
 * @param conditionId           the YAML condition id used as the stable grouping key; null when
 *                              {@code affectedDefendantName} is also null
 */
public record ValidationIssueResult(
        ValidationIssue issue,
        String errorMessage,
        String affectedDefendantName,
        String conditionId) {

    /** Convenience factory for issues that carry no top-level error message (WARNING severity). */
    public static ValidationIssueResult forWarning(final ValidationIssue issue) {
        return new ValidationIssueResult(issue, null, null, null);
    }

    /** Convenience factory for ERROR issues carrying an error message and optional defendant name. */
    public static ValidationIssueResult forError(final ValidationIssue issue,
                                                  final String errorMessage,
                                                  final String affectedDefendantName,
                                                  final String conditionId) {
        return new ValidationIssueResult(issue, errorMessage, affectedDefendantName, conditionId);
    }
}
