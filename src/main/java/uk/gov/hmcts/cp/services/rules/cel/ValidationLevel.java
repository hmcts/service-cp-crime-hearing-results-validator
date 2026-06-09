package uk.gov.hmcts.cp.services.rules.cel;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Level at which a validation condition is scoped — per-offence or per-defendant.
 * A YAML typo now fails at parse time with an unrecognised value error rather than
 * silently falling through to OFFENCE behaviour.
 */
public enum ValidationLevel {
    OFFENCE,
    DEFENDANT;

    /** Deserializes from YAML/JSON; throws on unrecognised values rather than silently defaulting. */
    @JsonCreator
    public static ValidationLevel fromValue(final String value) {
        for (final ValidationLevel level : values()) {
            if (level.name().equalsIgnoreCase(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown validationLevel: '" + value
                + "'. Expected one of: OFFENCE, DEFENDANT");
    }
}
