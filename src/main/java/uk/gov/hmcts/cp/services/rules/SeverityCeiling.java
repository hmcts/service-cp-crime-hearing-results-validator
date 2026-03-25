package uk.gov.hmcts.cp.services.rules;

import java.util.Locale;
import java.util.Optional;

/**
 * Stateless utility for severity ceiling arithmetic used by validation rule override resolution.
 *
 * <p>The ceiling model caps condition severities downward but never upgrades them:
 * WARNING &lt; ERROR, so a DB override of WARNING reduces any ERROR to WARNING,
 * while an ERROR override leaves all severities unchanged.
 */
public final class SeverityCeiling {

    private SeverityCeiling() {}

    /**
     * Normalises a raw severity string to {@code "ERROR"} or {@code "WARNING"}.
     *
     * @param severity raw value (may be mixed case or null)
     * @return normalised value, or {@code null} if the input is not a recognised severity
     */
    public static String normalize(final String severity) {
        return Optional.ofNullable(severity)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(upper -> "ERROR".equals(upper) || "WARNING".equals(upper))
                .orElse(null);
    }

    /**
     * Returns the ordinal value for a severity: WARNING=0, ERROR=1.
     * Any unrecognised value is treated as ERROR.
     *
     * @param severity normalised severity string
     * @return 0 for WARNING, 1 for everything else
     */
    public static int ordinal(final String severity) {
        return "WARNING".equalsIgnoreCase(severity) ? 0 : 1;
    }

    /**
     * Applies the DB override as a ceiling to the YAML condition severity.
     *
     * <p>If {@code dbSeverity} is {@code null} or invalid, the YAML value is returned unchanged.
     * Otherwise the lower of the two severities wins (ceiling prevents upgrades).
     *
     * @param yamlSeverity the severity declared in the rule YAML
     * @param dbSeverity   the severity stored in the database override, or {@code null}
     * @return effective severity after applying the ceiling
     */
    public static String resolve(final String yamlSeverity, final String dbSeverity) {
        final String result;
        if (dbSeverity == null) {
            result = yamlSeverity;
        } else {
            final String normalizedDb = normalize(dbSeverity);
            if (normalizedDb == null) {
                result = yamlSeverity;
            } else {
                result = ordinal(yamlSeverity) <= ordinal(normalizedDb) ? yamlSeverity : normalizedDb;
            }
        }
        return result;
    }
}
