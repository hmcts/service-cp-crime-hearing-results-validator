package uk.gov.hmcts.cp.acl;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationDroolsRulesTest {

    private static final String DRL_PATH = "/acl/validation-rules.drl";

    @Test
    void drl_file_should_be_loadable_from_classpath() {
        try (InputStream stream = getClass().getResourceAsStream(DRL_PATH)) {
            assertThat(stream).as("DRL file should exist on classpath at %s", DRL_PATH).isNotNull();
        } catch (Exception e) {
            throw new AssertionError("Failed to read DRL file", e);
        }
    }

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
