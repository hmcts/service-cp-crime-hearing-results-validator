package uk.gov.hmcts.cp.acl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationDroolsRulesTest {

    private static final String DRL_PATH = "/acl/validation-rules.drl";
    private static String drlContent;

    @BeforeAll
    static void loadDrl() throws Exception {
        try (InputStream stream = ValidationDroolsRulesTest.class.getResourceAsStream(DRL_PATH)) {
            assertThat(stream).as("DRL file must exist on classpath at %s", DRL_PATH).isNotNull();
            drlContent = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void drl_file_should_be_loadable_from_classpath() {
        assertThat(drlContent).isNotEmpty();
    }

    @Test
    void drl_file_should_contain_expected_action_names() {
        assertThat(drlContent).contains("validation-service.validate");
        assertThat(drlContent).contains("validation-service.rules");
        assertThat(drlContent).contains("validation-service.rules-detail");
    }

    @Test
    void drl_file_should_reference_expected_groups() {
        assertThat(drlContent).contains("Listing Officers");
        assertThat(drlContent).contains("Court Clerks");
        assertThat(drlContent).contains("Legal Advisers");
        assertThat(drlContent).contains("Court Associate");
        assertThat(drlContent).contains("Court Administrators");
        assertThat(drlContent).contains("System Users");
    }

    @Test
    void drl_file_should_import_required_drools_types() {
        assertThat(drlContent).contains("import uk.gov.moj.cpp.authz.drools.Outcome");
        assertThat(drlContent).contains("import uk.gov.moj.cpp.authz.drools.Action");
        assertThat(drlContent).contains("global uk.gov.moj.cpp.authz.http.providers.UserAndGroupProvider userAndGroupProvider");
    }

    @Nested
    @DisplayName("Allow – validate rule")
    class ValidateRule {

        private String block() {
            return extractRuleBlock(drlContent, "Allow – validate");
        }

        @Test
        void validate_rule_should_map_to_correct_action_name() {
            assertThat(block()).contains("validation-service.validate");
        }

        @Test
        void validate_rule_should_grant_access_to_listing_officers() {
            assertThat(block()).contains("Listing Officers");
        }

        @Test
        void validate_rule_should_grant_access_to_court_clerks() {
            assertThat(block()).contains("Court Clerks");
        }

        @Test
        void validate_rule_should_grant_access_to_legal_advisers() {
            assertThat(block()).contains("Legal Advisers");
        }

        @Test
        void validate_rule_should_grant_access_to_court_associate() {
            assertThat(block()).contains("Court Associate");
        }

        @Test
        void validate_rule_should_grant_access_to_court_administrators() {
            assertThat(block()).contains("Court Administrators");
        }

        @Test
        void validate_rule_should_grant_access_to_system_users() {
            assertThat(block()).contains("System Users");
        }
    }

    @Nested
    @DisplayName("Validate-only groups must not appear in rules or rules-detail")
    class ValidateOnlyGroupIsolation {

        @Test
        void listing_officers_should_not_appear_in_rules_rule() {
            assertThat(extractRuleBlock(drlContent, "Allow – rules")).doesNotContain("Listing Officers");
        }

        @Test
        void court_associate_should_not_appear_in_rules_rule() {
            assertThat(extractRuleBlock(drlContent, "Allow – rules")).doesNotContain("Court Associate");
        }

        @Test
        void court_administrators_should_not_appear_in_rules_rule() {
            assertThat(extractRuleBlock(drlContent, "Allow – rules")).doesNotContain("Court Administrators");
        }

        @Test
        void listing_officers_should_not_appear_in_rules_detail_rule() {
            assertThat(extractRuleBlock(drlContent, "Allow – rules-detail")).doesNotContain("Listing Officers");
        }

        @Test
        void court_associate_should_not_appear_in_rules_detail_rule() {
            assertThat(extractRuleBlock(drlContent, "Allow – rules-detail")).doesNotContain("Court Associate");
        }

        @Test
        void court_administrators_should_not_appear_in_rules_detail_rule() {
            assertThat(extractRuleBlock(drlContent, "Allow – rules-detail")).doesNotContain("Court Administrators");
        }
    }

    private static String extractRuleBlock(String content, String ruleName) {
        String marker = "rule \"" + ruleName + "\"";
        int start = content.indexOf(marker);
        assertThat(start).as("Rule '%s' not found in DRL", ruleName).isNotNegative();
        int endIdx = content.indexOf("\nend", start);
        assertThat(endIdx).as("No closing 'end' token found for rule '%s'", ruleName).isNotNegative();
        return content.substring(start, endIdx + "\nend".length());
    }
}
