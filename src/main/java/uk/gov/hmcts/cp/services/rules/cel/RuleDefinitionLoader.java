package uk.gov.hmcts.cp.services.rules.cel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Loads YAML rule definitions from the classpath.
 */
public class RuleDefinitionLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * Loads a rule definition from the supplied classpath resource.
     *
     * @param classpathLocation location of the YAML file relative to the classpath root
     * @return parsed rule definition
     */
    public static RuleDefinition load(String classpathLocation) {
        try (InputStream is = new ClassPathResource(classpathLocation).getInputStream()) {
            Map<String, RuleDefinition> wrapper = YAML_MAPPER.readValue(
                    is,
                    YAML_MAPPER.getTypeFactory().constructMapType(
                            Map.class, String.class, RuleDefinition.class));
            RuleDefinition rule = wrapper.get("rule");
            if (rule == null) {
                throw new IllegalArgumentException(
                        "YAML file must contain a top-level 'rule' key: " + classpathLocation);
            }
            return rule;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load rule definition: " + classpathLocation, e);
        }
    }
}
