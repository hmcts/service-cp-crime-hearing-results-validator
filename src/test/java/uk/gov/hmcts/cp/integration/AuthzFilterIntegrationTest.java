package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ACL and identity-group based access around HTTP endpoints.
 */
class AuthzFilterIntegrationTest extends IntegrationTestBase {

    private static final String VALIDATE_URL = "/api/validation/validate";
    private static final String RULES_URL = "/api/validation/rules";
    private static final String RULES_DETAIL_URL = "/api/validation/rules/DR-SENT-002";
    private static final String RULE_ID = "DR-SENT-002";

    @Resource
    private RuleOverrideService ruleOverrideService;

    private static final String EMPTY_REQUEST = """
            {
              "hearingId": "h1",
              "caseId": "c1",
              "hearingDay": "2026-03-11",
              "courtType": "MAGISTRATES",
              "resultLines": [],
              "defendants": [],
              "offences": []
            }
            """;

    @AfterEach
    void restoreDefaultStub() {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("System Users");
    }

    /**
     * Restores DR-SENT-002 to its default enabled/ERROR state, since the rules-update tests in
     * this class persist real DB overrides that would otherwise leak into other integration tests
     * sharing the same TestContainers database.
     */
    @AfterEach
    void resetRuleOverride() {
        resetRuleOverride(ruleOverrideService, RULE_ID);
    }

    // -------------------------------------------------------------------------
    // validate endpoint — explicit CPP-ACTION header
    // -------------------------------------------------------------------------

    /**
     * Verifies a user resolved into the Court Clerks group can call the validation endpoint.
     */
    @Test
    void request_with_user_in_court_clerks_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Clerks");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "court-clerk-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isOk());
    }

    /**
     * Verifies a user resolved into the Legal Advisers group can call the validation endpoint.
     */
    @Test
    void request_with_legal_adviser_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Legal Advisers");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "legal-adviser-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isOk());
    }

    /**
     * Verifies a user resolved into the Listing Officers group can call the validation endpoint.
     */
    @Test
    void request_with_listing_officers_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Listing Officers");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "listing-officer-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isOk());
    }

    /**
     * Verifies a user resolved into the Court Associate group can call the validation endpoint.
     */
    @Test
    void request_with_court_associate_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Associate");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "court-associate-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isOk());
    }

    /**
     * Verifies a user resolved into the Court Administrators group can call the validation endpoint.
     */
    @Test
    void request_with_court_administrators_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Administrators");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "court-administrator-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isOk());
    }

    /**
     * Verifies a user resolved into the System Users group can call the validation endpoint.
     */
    @Test
    void request_with_system_users_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("System Users");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "system-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isOk());
    }

    /**
     * Verifies users outside the allowed groups are rejected with HTTP 403.
     */
    @Test
    void request_with_user_not_in_allowed_group_should_return_403() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Some Other Group");

        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "random-user")
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies the request is rejected with HTTP 401 when the mandatory identity header is absent.
     */
    @Test
    void request_without_cjscppuid_header_should_return_401() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CPP-ACTION", "validation-service.validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies the full filter → Drools → controller path when the caller omits CPP-ACTION:
     * ActionHeaderFilter must synthesise the header from the request path for auth to succeed.
     */
    @Test
    void validate_request_without_cpp_action_header_should_succeed_via_filter_injection() throws Exception {
        mockMvc.perform(post(VALIDATE_URL)
                        .header("CJSCPPUID", "system-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_REQUEST))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // rules endpoint — CPP-ACTION injected by ActionHeaderFilter
    // -------------------------------------------------------------------------

    /**
     * Verifies an allowed group can list validation rules; CPP-ACTION is supplied explicitly
     * to isolate the authz concern from filter injection.
     */
    @Test
    void rules_request_with_allowed_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("System Users");

        mockMvc.perform(get(RULES_URL)
                        .header("CJSCPPUID", "system-user")
                        .header("CPP-ACTION", "validation-service.rules"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies ActionHeaderFilter synthesises CPP-ACTION for the rules path when the caller
     * omits it — exercises the full filter → Drools → controller path for this endpoint.
     */
    @Test
    void rules_request_without_cpp_action_header_should_succeed_via_filter_injection() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("System Users");

        mockMvc.perform(get(RULES_URL)
                        .header("CJSCPPUID", "system-user"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies Court Clerks can list validation rules.
     */
    @Test
    void rules_request_with_court_clerks_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Clerks");

        mockMvc.perform(get(RULES_URL)
                        .header("CJSCPPUID", "court-clerk-user"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies Legal Advisers can list validation rules.
     */
    @Test
    void rules_request_with_legal_advisers_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Legal Advisers");

        mockMvc.perform(get(RULES_URL)
                        .header("CJSCPPUID", "legal-adviser-user"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies Listing Officers are denied access to the rules list endpoint (HTTP 403).
     */
    @Test
    void rules_request_with_listing_officers_group_should_return_403() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Listing Officers");

        mockMvc.perform(get(RULES_URL)
                        .header("CJSCPPUID", "listing-officer-user"))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies Court Associate are denied access to the rules list endpoint (HTTP 403).
     */
    @Test
    void rules_request_with_court_associate_group_should_return_403() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Associate");

        mockMvc.perform(get(RULES_URL)
                        .header("CJSCPPUID", "court-associate-user"))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies Court Administrators are denied access to the rules list endpoint (HTTP 403).
     */
    @Test
    void rules_request_with_court_administrators_group_should_return_403() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Administrators");

        mockMvc.perform(get(RULES_URL)
                        .header("CJSCPPUID", "court-administrator-user"))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies the rules endpoint rejects requests missing the mandatory identity header (HTTP 401).
     */
    @Test
    void rules_request_without_cjscppuid_header_should_return_401() throws Exception {
        mockMvc.perform(get(RULES_URL))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // rules-detail endpoint — CPP-ACTION injected by ActionHeaderFilter
    // -------------------------------------------------------------------------

    /**
     * Verifies an allowed group can retrieve a single rule detail; CPP-ACTION is supplied
     * explicitly to isolate the authz concern from filter injection.
     */
    @Test
    void rules_detail_request_with_allowed_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("System Users");

        mockMvc.perform(get(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "system-user")
                        .header("CPP-ACTION", "validation-service.rules-detail"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies ActionHeaderFilter synthesises CPP-ACTION for the rules-detail path when the
     * caller omits it.
     */
    @Test
    void rules_detail_request_without_cpp_action_header_should_succeed_via_filter_injection() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("System Users");

        mockMvc.perform(get(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "system-user"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies Court Clerks can retrieve a single rule detail.
     */
    @Test
    void rules_detail_request_with_court_clerks_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Clerks");

        mockMvc.perform(get(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "court-clerk-user"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies Legal Advisers can retrieve a single rule detail.
     */
    @Test
    void rules_detail_request_with_legal_advisers_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Legal Advisers");

        mockMvc.perform(get(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "legal-adviser-user"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies Listing Officers are denied access to the rule detail endpoint (HTTP 403).
     */
    @Test
    void rules_detail_request_with_listing_officers_group_should_return_403() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Listing Officers");

        mockMvc.perform(get(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "listing-officer-user"))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies Court Associate are denied access to the rule detail endpoint (HTTP 403).
     */
    @Test
    void rules_detail_request_with_court_associate_group_should_return_403() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Associate");

        mockMvc.perform(get(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "court-associate-user"))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies Court Administrators are denied access to the rule detail endpoint (HTTP 403).
     */
    @Test
    void rules_detail_request_with_court_administrators_group_should_return_403() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Administrators");

        mockMvc.perform(get(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "court-administrator-user"))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies the rules-detail endpoint rejects requests missing the mandatory identity header (HTTP 401).
     */
    @Test
    void rules_detail_request_without_cjscppuid_header_should_return_401() throws Exception {
        mockMvc.perform(get(RULES_DETAIL_URL))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // rules-update endpoint (PATCH) — CPP-ACTION injected by ActionHeaderFilter
    // -------------------------------------------------------------------------

    /**
     * Verifies System Users can update a rule; CPP-ACTION is supplied explicitly to isolate the
     * authz concern from filter injection.
     */
    @Test
    void rules_update_request_with_system_users_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("System Users");

        mockMvc.perform(patch(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "system-user")
                        .header("CPP-ACTION", "validation-service.rules-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies Second Line Support can update a rule.
     */
    @Test
    void rules_update_request_with_second_line_support_group_should_succeed() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Second Line Support");

        mockMvc.perform(patch(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "second-line-support-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies ActionHeaderFilter synthesises the write action for PATCH on the rules-detail path
     * when the caller omits CPP-ACTION, proving the method-aware branch fires end to end.
     */
    @Test
    void rules_update_request_without_cpp_action_header_should_succeed_via_filter_injection() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("System Users");

        mockMvc.perform(patch(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "system-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk());
    }

    /**
     * Verifies Court Clerks — allowed to read rule detail — are denied write access (HTTP 403).
     */
    @Test
    void rules_update_request_with_court_clerks_group_should_return_403() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Clerks");

        mockMvc.perform(patch(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "court-clerk-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies Legal Advisers — allowed to read rule detail — are denied write access (HTTP 403).
     */
    @Test
    void rules_update_request_with_legal_advisers_group_should_return_403() throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Legal Advisers");

        mockMvc.perform(patch(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "legal-adviser-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isForbidden());
    }

    /**
     * Verifies a Court Clerk cannot escalate to the write action by spoofing the CPP-ACTION header
     * with the value for the broader read-only rules-detail action on a PATCH request.
     * ActionHeaderFilter must derive and enforce the action from method + path rather than trusting
     * a caller-supplied header, otherwise a caller authorized only for rules-detail could have their
     * PATCH evaluated under that wider group list instead of the restricted rules-update rule.
     */
    @Test
    void rules_update_request_with_spoofed_rules_detail_header_should_still_return_403_for_court_clerks()
            throws Exception {
        IDENTITY_WIRE_MOCK.resetAll();
        stubIdentityResponse("Court Clerks");

        mockMvc.perform(patch(RULES_DETAIL_URL)
                        .header("CJSCPPUID", "court-clerk-user")
                        .header("CPP-ACTION", "validation-service.rules-detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // unauthenticated / public
    // -------------------------------------------------------------------------

    /**
     * Verifies unauthenticated access is still allowed for the health endpoint.
     */
    @Test
    void actuator_health_should_be_accessible_without_auth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
