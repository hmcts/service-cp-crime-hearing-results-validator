package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.buildDefendantNames;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperOrNull;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Per-breach-occurrence preprocessor for the DR-APP-009 rule. Produces one
 * {@link ApplicationResultBreachContext} per {@code ResultLineDto} whose short code is one of a
 * fixed, application-only set (results that may only be recorded against an application, never
 * against an offence) and whose {@code offenceId} is non-blank.
 *
 * <p>Deliberately emits one context <em>per breaching result line</em>, not per offence or per
 * defendant, so that two breaches on the same offence -- or the same breaching code recorded
 * against two different offences for the same defendant -- each surface their own inline error
 * (spec.md AC2). Collapsing a repeated defendant name in the aggregated page-level message is
 * the aggregation layer's responsibility (see {@code DefaultValidationService.
 * appendDefendantName}, research.md R8), not this preprocessor's.
 *
 * <p>No upstream contract change is required for this preprocessor: {@code ResultLineDto}
 * already carries {@code offenceId}, {@code shortCode}, {@code label}, and {@code defendantId}.
 * A null/blank {@code offenceId} is treated defensively as "not attached to an offence" (never
 * true against the current contract, which has no application-linkage field at all -- see
 * research.md R1) so this preprocessor stays correct if that changes upstream without a code
 * change here.
 */
@Slf4j
@Component
public class ApplicationResultOffencePreprocessor implements ValidationPreprocessor {

    /** YAML {@code preprocessing.type} qualifier for this preprocessor. */
    public static final String QUALIFIER = "application-result-offence";

    @Override
    public String type() {
        return QUALIFIER;
    }

    @Override
    @SuppressWarnings({"PMD.OnlyOneReturn", "PMD.AvoidCatchingGenericException"})
    // early-return on "no result lines" reads clearer here; the catch is deliberate -- see
    // in-line comment below.
    public Map<String, ApplicationResultBreachContext> preprocess(final DraftValidationRequest request,
                                                                   final PreprocessingDefinition config) {
        final Set<String> applicationOnlyCodes = upperSet(config.applicationOnlyShortCodes());
        final Map<String, String> defendantNames = buildDefendantNames(request);

        final Map<String, ApplicationResultBreachContext> contexts = new LinkedHashMap<>();
        if (request.getResultLines() == null) {
            return contexts;
        }

        for (final ResultLineDto line : request.getResultLines()) {
            try {
                final ApplicationResultBreachContext context =
                        buildContextIfBreach(line, applicationOnlyCodes, defendantNames);
                if (context != null) {
                    contexts.put(line.getResultLineId(), context);
                }
            } catch (RuntimeException e) {
                // A single malformed result line must never suppress every other breach in the
                // same request -- DefaultValidationService.evaluateRulesWithMdc()'s per-rule
                // catch (Exception e) would otherwise skip this rule for the entire request.
                log.warn("Skipping malformed result line while evaluating application-only "
                        + "result breaches: {}", e.getMessage());
            }
        }
        return contexts;
    }

    @SuppressWarnings("PMD.OnlyOneReturn") // early-return on each non-breach guard reads clearer here
    private ApplicationResultBreachContext buildContextIfBreach(final ResultLineDto line,
                                                                  final Set<String> applicationOnlyCodes,
                                                                  final Map<String, String> defendantNames) {
        final String offenceId = line.getOffenceId();
        if (offenceId == null || offenceId.isBlank()) {
            return null;
        }
        final String upperShortCode = upperOrNull(line.getShortCode());
        if (upperShortCode == null || !applicationOnlyCodes.contains(upperShortCode)) {
            return null;
        }

        final String resultLabel = line.getLabel() != null && !line.getLabel().isBlank()
                ? line.getLabel()
                : line.getShortCode();
        final String defendantName = defendantNames.getOrDefault(line.getDefendantId(), "");

        return new ApplicationResultBreachContext(
                line.getDefendantId(),
                defendantName == null ? "" : defendantName,
                offenceId,
                resultLabel);
    }
}
