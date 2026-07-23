package uk.gov.hmcts.cp.exceptions;

/** Thrown when a rule update request is invalid (e.g. no updatable fields provided). */
public class InvalidRuleUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception with the given detail message. */
    public InvalidRuleUpdateException(final String message) {
        super(message);
    }
}
