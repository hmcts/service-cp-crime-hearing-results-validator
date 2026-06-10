package uk.gov.hmcts.cp.acl;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.authz.drools.Action;
import uk.gov.moj.cpp.authz.drools.Outcome;
import uk.gov.moj.cpp.authz.http.providers.UserAndGroupProvider;

class ValidationDroolsRulesTest {

    private static final String DRL_PATH = "/acl/validation-rules.drl";
    private static String drlContent;
    private static KieContainer kieContainer;

    @BeforeAll
    static void setUp() throws Exception {
        try (InputStream stream = ValidationDroolsRulesTest.class.getResourceAsStream(DRL_PATH)) {
            assertThat(stream).as("DRL file must exist on classpath at %s", DRL_PATH).isNotNull();
            drlContent = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write(ks.getResources()
                .newClassPathResource("acl/validation-rules.drl").setResourceType(ResourceType.DRL));
        ks.newKieBuilder(kfs).buildAll();
        kieContainer = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
    }

    private static boolean fireRule(String action, String simulatedUserGroup) {
        return fireRuleWithGroups(action, simulatedUserGroup);
    }

    private static boolean fireRuleWithGroups(String action, String... userGroups) {
        KieSession session = kieContainer.newKieSession();
        try {
            UserAndGroupProvider provider = mock(UserAndGroupProvider.class);
            when(provider.isMemberOfAnyOfTheSuppliedGroups(any(Action.class), any(String[].class)))
                    .thenAnswer(inv -> {
                        String[] allowedGroups = (String[]) inv.getRawArguments()[1];
                        return Arrays.stream(userGroups)
                                .anyMatch(Arrays.asList(allowedGroups)::contains);
                    });
            session.setGlobal("userAndGroupProvider", provider);
            Outcome outcome = new Outcome();
            session.insert(outcome);
            session.insert(new Action(action, Map.of()));
            session.fireAllRules();
            return outcome.isSuccess();
        } finally {
            session.dispose();
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

    @Nested
    @DisplayName("Allow – validate rule")
    class ValidateRule {

        @Test
        void validate_rule_should_grant_access_to_listing_officers() {
            assertThat(fireRule("validation-service.validate", "Listing Officers")).isTrue();
        }

        @Test
        void validate_rule_should_grant_access_to_court_clerks() {
            assertThat(fireRule("validation-service.validate", "Court Clerks")).isTrue();
        }

        @Test
        void validate_rule_should_grant_access_to_legal_advisers() {
            assertThat(fireRule("validation-service.validate", "Legal Advisers")).isTrue();
        }

        @Test
        void validate_rule_should_grant_access_to_court_associate() {
            assertThat(fireRule("validation-service.validate", "Court Associate")).isTrue();
        }

        @Test
        void validate_rule_should_grant_access_to_court_administrators() {
            assertThat(fireRule("validation-service.validate", "Court Administrators")).isTrue();
        }

        @Test
        void validate_rule_should_grant_access_to_system_users() {
            assertThat(fireRule("validation-service.validate", "System Users")).isTrue();
        }

        @Test
        void validate_rule_should_deny_access_to_unknown_group() {
            assertThat(fireRule("validation-service.validate", "Unknown Group")).isFalse();
        }
    }

    @Nested
    @DisplayName("Allow – rules rule")
    class RulesRule {

        @Test
        void rules_rule_should_grant_access_to_court_clerks() {
            assertThat(fireRule("validation-service.rules", "Court Clerks")).isTrue();
        }

        @Test
        void rules_rule_should_grant_access_to_legal_advisers() {
            assertThat(fireRule("validation-service.rules", "Legal Advisers")).isTrue();
        }

        @Test
        void rules_rule_should_grant_access_to_system_users() {
            assertThat(fireRule("validation-service.rules", "System Users")).isTrue();
        }

        @Test
        void rules_rule_should_deny_access_to_listing_officers() {
            assertThat(fireRule("validation-service.rules", "Listing Officers")).isFalse();
        }

        @Test
        void rules_rule_should_deny_access_to_court_associate() {
            assertThat(fireRule("validation-service.rules", "Court Associate")).isFalse();
        }

        @Test
        void rules_rule_should_deny_access_to_court_administrators() {
            assertThat(fireRule("validation-service.rules", "Court Administrators")).isFalse();
        }
    }

    @Nested
    @DisplayName("Allow – rules-detail rule")
    class RulesDetailRule {

        @Test
        void rules_detail_rule_should_grant_access_to_court_clerks() {
            assertThat(fireRule("validation-service.rules-detail", "Court Clerks")).isTrue();
        }

        @Test
        void rules_detail_rule_should_grant_access_to_legal_advisers() {
            assertThat(fireRule("validation-service.rules-detail", "Legal Advisers")).isTrue();
        }

        @Test
        void rules_detail_rule_should_grant_access_to_system_users() {
            assertThat(fireRule("validation-service.rules-detail", "System Users")).isTrue();
        }

        @Test
        void rules_detail_rule_should_deny_access_to_listing_officers() {
            assertThat(fireRule("validation-service.rules-detail", "Listing Officers")).isFalse();
        }

        @Test
        void rules_detail_rule_should_deny_access_to_court_associate() {
            assertThat(fireRule("validation-service.rules-detail", "Court Associate")).isFalse();
        }

        @Test
        void rules_detail_rule_should_deny_access_to_court_administrators() {
            assertThat(fireRule("validation-service.rules-detail", "Court Administrators")).isFalse();
        }
    }

    @Nested
    @DisplayName("Unrecognised action name")
    class UnrecognisedAction {

        @Test
        void unrecognised_action_with_allowed_group_should_deny() {
            assertThat(fireRule("validation-service.unknown", "System Users")).isFalse();
        }

        @Test
        void unrecognised_action_with_unknown_group_should_deny() {
            assertThat(fireRule("validation-service.unknown", "Unknown Group")).isFalse();
        }
    }

    @Nested
    @DisplayName("Multi-group membership")
    class MultiGroupMembership {

        @Test
        void validate_rule_should_grant_access_when_one_of_multiple_groups_is_allowed() {
            assertThat(fireRuleWithGroups("validation-service.validate", "Unknown Group", "Court Clerks")).isTrue();
        }

        @Test
        void rules_rule_should_grant_access_when_one_of_multiple_groups_is_allowed() {
            assertThat(fireRuleWithGroups("validation-service.rules", "Listing Officers", "Court Clerks")).isTrue();
        }

        @Test
        void rules_rule_should_deny_access_when_all_groups_are_denied() {
            assertThat(fireRuleWithGroups("validation-service.rules", "Court Associate", "Court Administrators")).isFalse();
        }

        @Test
        void rules_detail_rule_should_grant_access_when_one_of_multiple_groups_is_allowed() {
            assertThat(fireRuleWithGroups("validation-service.rules-detail", "Listing Officers", "Legal Advisers")).isTrue();
        }

        @Test
        void rules_detail_rule_should_deny_access_when_all_groups_are_denied() {
            assertThat(fireRuleWithGroups("validation-service.rules-detail", "Court Associate", "Court Administrators")).isFalse();
        }
    }
}
