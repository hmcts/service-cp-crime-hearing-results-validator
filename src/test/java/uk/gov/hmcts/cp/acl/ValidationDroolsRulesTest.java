package uk.gov.hmcts.cp.acl;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the Drools ACL rules file packaged with the service.
 */
class ValidationDroolsRulesTest {

    private static final String DRL_PATH = "/acl/validation-rules.drl";

    /**
     * Verifies the ACL rules file is packaged on the classpath and can be opened by tests.
     */
    @Test
    void drl_file_should_be_loadable_from_classpath() {
        try (InputStream stream = getClass().getResourceAsStream(DRL_PATH)) {
            assertThat(stream).as("DRL file should exist on classpath at %s", DRL_PATH).isNotNull();
        } catch (Exception e) {
            throw new AssertionError("Failed to read DRL file", e);
        }
    }

    /**
     * Verifies the ACL rules reference each application action name exposed by the HTTP layer.
     */
    @Test
    void drl_file_should_contain_expected_action_names() throws Exception {
        String content;
        try (InputStream stream = getClass().getResourceAsStream(DRL_PATH)) {
            assertThat(stream).isNotNull();
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(content).contains("validation-service.validate");
        assertThat(content).contains("validation-service.rules");
        assertThat(content).contains("validation-service.rules-detail");
    }

    /**
     * Verifies the ACL rules mention the expected allowed identity groups.
     */
    @Test
    void drl_file_should_reference_expected_groups() throws Exception {
        String content;
        try (InputStream stream = getClass().getResourceAsStream(DRL_PATH)) {
            assertThat(stream).isNotNull();
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(content).contains("Legal Advisers");
        assertThat(content).contains("Court Clerks");
        assertThat(content).contains("System Users");
    }

    /**
     * Verifies the ACL rules import the Drools types and globals required at runtime.
     */
    @Test
    void drl_file_should_import_required_drools_types() throws Exception {
        String content;
        try (InputStream stream = getClass().getResourceAsStream(DRL_PATH)) {
            assertThat(stream).isNotNull();
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(content).contains("import uk.gov.moj.cpp.authz.drools.Outcome");
        assertThat(content).contains("import uk.gov.moj.cpp.authz.drools.Action");
        assertThat(content).contains("global uk.gov.moj.cpp.authz.http.providers.UserAndGroupProvider userAndGroupProvider");
    }
}
