package uk.gov.hmcts.cp.exceptions;

/** Thrown when a validation rule cannot be found by its identifier. */
public class RuleNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception for the given rule identifier. */
    public RuleNotFoundException(final String ruleId) {
        super("Rule not found: " + ruleId);
    }
}
