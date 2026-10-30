package uk.gov.hmcts.cp.services.referencedata;

/**
 * Outcome of an {@code endorsableFlag} lookup via {@link ReferencedataOffenceClient#lookupEndorsability(String)}.
 * Separates "reference data says the flag has no value" ({@link #NON_ENDORSABLE}) from "reference
 * data could not be consulted" ({@link #UNAVAILABLE}), which an {@code Optional<Boolean>} cannot.
 */
public enum Endorsability {

    /** Reference data returned {@code endorsableFlag: true}. */
    ENDORSABLE,

    /**
     * Reference data returned {@code endorsableFlag: false}, or answered successfully with the
     * field null or missing — CRA-337 treats "has no value" the same as {@code 0}.
     */
    NON_ENDORSABLE,

    /**
     * The lookup did not produce an answer: lookup disabled, blank offence code, timeout, non-2xx,
     * malformed body, or an empty {@code offences} list. Never cached.
     */
    UNAVAILABLE
}
