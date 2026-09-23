package uk.gov.hmcts.cp.services.rules.cel;

import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.buildDefendantNames;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperOrNull;
import static uk.gov.hmcts.cp.services.rules.cel.PreprocessorHelper.upperSet;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.DraftValidationRequest;
import uk.gov.hmcts.cp.openapi.model.ResultLineDto;

/**
 * Per-offence preprocessor for the DR-APP-009 rule. Produces one
 * {@link ApplicationResultBreachContext} per offence carrying at least one {@code ResultLineDto}
 * whose short code is one of a fixed, application-only set (results that may only be recorded
 * against an application, never against an offence).
 *
 * <p>Groups every breaching result line by {@code offenceId} rather than emitting one context per
 * line, so two or more breaches on the same offence consolidate into a single context -- and
 * therefore a single inline error naming all of that offence's breaching results, comma-separated
 * (spec.md AC2A). It also precomputes one hearing-wide, comma-joined, de-duplicated label list
 * (in first-encounter order across the whole request) and shares it, identical, across every
 * context it builds, so the page-level message resolves to the same text for every breaching
 * offence and merges into a single entry (AC2B) -- see {@link ApplicationResultBreachContext} and
 * {@code CelValidationRule.errorMessageCalculatedValuePlaceholder}.
 *
 * <p>An offence belongs to exactly one defendant, so a context's {@code defendantId}/
 * {@code defendantName} is taken from the offence's breaching result lines ({@code OffenceDto}
 * carries no defendant reference of its own).
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
    @SuppressWarnings({"PMD.OnlyOneReturn", "PMD.AvoidCatchingGenericException",
        "PMD.AvoidInstantiatingObjectsInLoops"})
    // early-return on "no result lines" reads clearer here; the catch is deliberate -- see
    // in-line comment below; the per-offence LinkedHashSet is intentional -- one label
    // accumulator per distinct offence, not a stray allocation.
    public Map<String, ApplicationResultBreachContext> preprocess(final DraftValidationRequest request,
                                                                   final PreprocessingDefinition config) {
        final Set<String> applicationOnlyCodes = upperSet(config.applicationOnlyShortCodes());
        final Map<String, String> defendantNames = buildDefendantNames(request);

        if (request.getResultLines() == null) {
            return Map.of();
        }

        final Map<String, Set<String>> labelsByOffence = new LinkedHashMap<>();
        final Map<String, String> defendantIdByOffence = new LinkedHashMap<>();
        final Set<String> globalLabels = new LinkedHashSet<>();

        for (final ResultLineDto line : request.getResultLines()) {
            try {
                collectBreachIfAny(line, applicationOnlyCodes, labelsByOffence, defendantIdByOffence, globalLabels);
            } catch (RuntimeException e) {
                // A single malformed result line must never suppress every other breach in the
                // same request -- DefaultValidationService.evaluateRulesWithMdc()'s per-rule
                // catch (Exception e) would otherwise skip this rule for the entire request.
                log.warn("Skipping malformed result line while evaluating application-only "
                        + "result breaches: {}", e.getMessage());
            }
        }

        final String globalResultLabels = String.join(", ", globalLabels);

        final Map<String, ApplicationResultBreachContext> contexts = new LinkedHashMap<>();
        for (final Map.Entry<String, Set<String>> entry : labelsByOffence.entrySet()) {
            final String offenceId = entry.getKey();
            final String defendantId = defendantIdByOffence.get(offenceId);
            final String defendantName = defendantNames.getOrDefault(defendantId, "");
            contexts.put(offenceId, new ApplicationResultBreachContext(
                    offenceId,
                    List.copyOf(entry.getValue()),
                    globalResultLabels,
                    defendantId,
                    defendantName == null ? "" : defendantName));
        }
        return contexts;
    }

    @SuppressWarnings("PMD.OnlyOneReturn") // early-return on each non-breach guard reads clearer here
    private void collectBreachIfAny(final ResultLineDto line,
                                     final Set<String> applicationOnlyCodes,
                                     final Map<String, Set<String>> labelsByOffence,
                                     final Map<String, String> defendantIdByOffence,
                                     final Set<String> globalLabels) {
        final String offenceId = line.getOffenceId();
        if (offenceId == null || offenceId.isBlank()) {
            return;
        }
        final String upperShortCode = upperOrNull(line.getShortCode());
        if (upperShortCode == null || !applicationOnlyCodes.contains(upperShortCode)) {
            return;
        }

        final String resultLabel = line.getLabel() != null && !line.getLabel().isBlank()
                ? line.getLabel()
                : line.getShortCode();

        labelsByOffence.computeIfAbsent(offenceId, k -> new LinkedHashSet<>()).add(resultLabel);
        defendantIdByOffence.putIfAbsent(offenceId, line.getDefendantId());
        globalLabels.add(resultLabel);
    }
}
