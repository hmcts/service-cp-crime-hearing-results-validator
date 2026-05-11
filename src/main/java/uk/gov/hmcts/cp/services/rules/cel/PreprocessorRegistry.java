package uk.gov.hmcts.cp.services.rules.cel;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Resolves a {@link ValidationPreprocessor} by its YAML qualifier. Built once at startup from
 * the Spring application context; fails fast if two preprocessors share the same qualifier.
 */
@Component
public class PreprocessorRegistry {

    private final Map<String, ValidationPreprocessor> byType;

    /**
     * Builds the registry from all {@link ValidationPreprocessor} beans.
     *
     * @param preprocessors all preprocessors discovered by Spring
     * @throws IllegalStateException if two preprocessors share the same {@link
     *         ValidationPreprocessor#type()} qualifier
     */
    public PreprocessorRegistry(final List<ValidationPreprocessor> preprocessors) {
        this.byType = preprocessors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ValidationPreprocessor::type,
                        p -> p,
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "Duplicate preprocessor qualifier: " + existing.type());
                        }));
    }

    /**
     * Returns the preprocessor registered for the given qualifier.
     *
     * @param type YAML {@code preprocessing.type} qualifier
     * @return the matching preprocessor
     * @throws IllegalStateException if no preprocessor is registered for the qualifier
     */
    public ValidationPreprocessor require(final String type) {
        final ValidationPreprocessor preprocessor = byType.get(type);
        if (preprocessor == null) {
            throw new IllegalStateException("No preprocessor registered for type: " + type);
        }
        return preprocessor;
    }
}
