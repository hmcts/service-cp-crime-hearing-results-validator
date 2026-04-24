package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;

/**
 * Common abstraction for per-entry contexts produced by a {@link RulePreprocessor}.
 */
public interface RuleContext {

    /**
     * Returns the numeric variable map exposed to CEL expressions for this entry.
     *
     * @return CEL variable map
     */
    Map<String, Long> toCelContext();

    /**
     * Returns the named set of offence IDs referenced by a condition's affectedOffenceSet.
     *
     * @param setName configured offence-id set name
     * @return matching offence-id list
     */
    List<String> getOffenceIdSet(String setName);

    /**
     * Returns the display name used for message template substitution.
     *
     * @return display name
     */
    String displayName();

    /**
     * Returns all offence IDs associated with this context entry.
     *
     * @return all offence IDs
     */
    List<String> allOffenceIds();
}
