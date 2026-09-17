package uk.gov.hmcts.cp.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.annotation.Resource;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.config.TestContainersInitialise;
import uk.gov.hmcts.cp.entity.ValidationRuleEntity;
import uk.gov.hmcts.cp.services.rules.RuleOverrideService;

/**
 * Base class for Spring-context integration tests. Boots the full application context against
 * the shared TestContainers Postgres instance and stubs the identity endpoint via WireMock so
 * every subclass starts from an authorised, deterministic baseline.
 */
@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
@AutoConfigureMockMvc
@Slf4j
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod") // never instantiated directly, always extended by a concrete test class
public abstract class IntegrationTestBase {

    protected static final String IDENTITY_PATH =
            "/usersgroups-query-api/query/api/rest/usersgroups/users/logged-in-user/permissions";

    protected static final String REFERENCEDATA_OFFENCE_PATH =
            "/referencedataoffences-query-api/query/api/rest/referencedataoffences/offences";

    private static final String CJS_OFFENCE_CODE_PARAM = "cjsoffencecode";

    protected static final WireMockServer IDENTITY_WIRE_MOCK =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    protected static final WireMockServer REFERENCEDATA_OFFENCE_WIRE_MOCK =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        IDENTITY_WIRE_MOCK.start();
        stubDefaultIdentityResponse();
        REFERENCEDATA_OFFENCE_WIRE_MOCK.start();
    }

    @Resource
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void overrideIdentityUrl(DynamicPropertyRegistry registry) {
        registry.add("authz.http.identity-url-template",
                () -> "http://localhost:" + IDENTITY_WIRE_MOCK.port() + IDENTITY_PATH);
    }

    @DynamicPropertySource
    static void overrideReferencedataOffenceUrl(DynamicPropertyRegistry registry) {
        registry.add("referencedata.offences.http.offence-url-template",
                () -> "http://localhost:" + REFERENCEDATA_OFFENCE_WIRE_MOCK.port()
                        + REFERENCEDATA_OFFENCE_PATH + "?" + CJS_OFFENCE_CODE_PARAM + "={offenceCode}");
    }

    /**
     * Default stub that allows all requests through by returning the "System Users" group.
     * Tests that need specific group behaviour should call {@link #stubIdentityResponse(String)}
     * after resetting WireMock.
     */
    private static void stubDefaultIdentityResponse() {
        stubIdentityResponse("System Users");
    }

    /**
     * Replaces the default identity stub so a test can exercise a specific caller group.
     *
     * @param groupName the single group name to return for the logged-in user
     */
    protected static void stubIdentityResponse(String groupName) {
        String responseBody = """
                {
                  "groups": [
                    {
                      "groupId": "grp-1",
                      "groupName": "%s",
                      "prosecutingAuthority": null
                    }
                  ],
                  "switchableRoles": [],
                  "permissions": []
                }
                """.formatted(groupName);

        IDENTITY_WIRE_MOCK.stubFor(get(urlPathEqualTo(IDENTITY_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(responseBody)));
    }

    /**
     * Stubs the reference-data offence lookup for a specific {@code cjsOffenceCode}, returning
     * the given {@code misCode} (or no {@code misCode} field at all when {@code misCode} is
     * {@code null}, exercising the "not a relevant sexual offence" path).
     *
     * @param offenceCode the {@code cjsoffencecode} query value to stub
     * @param misCode the {@code misCode} to return, or {@code null} to omit the field
     */
    protected static void stubReferencedataOffenceResponse(final String offenceCode, final String misCode) {
        final String misCodeJson = misCode == null ? "" : "\"misCode\": \"%s\",".formatted(misCode);
        final String responseBody = """
                {
                  "offences": [
                    {
                      "offenceId": "0000357a-2b27-3eb5-9377-d7e9d680eb87",
                      %s
                      "cjsOffenceCode": "%s"
                    }
                  ]
                }
                """.formatted(misCodeJson, offenceCode);

        REFERENCEDATA_OFFENCE_WIRE_MOCK.stubFor(get(urlPathEqualTo(REFERENCEDATA_OFFENCE_PATH))
                .withQueryParam(CJS_OFFENCE_CODE_PARAM, equalTo(offenceCode))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/vnd.referencedataoffences.offences-list+json")
                        .withBody(responseBody)));
    }

    /**
     * Stubs the reference-data offence lookup for a specific {@code cjsOffenceCode} to fail
     * (404), exercising the fail-open path.
     *
     * @param offenceCode the {@code cjsoffencecode} query value to stub
     */
    protected static void stubReferencedataOffenceNotFound(final String offenceCode) {
        REFERENCEDATA_OFFENCE_WIRE_MOCK.stubFor(get(urlPathEqualTo(REFERENCEDATA_OFFENCE_PATH))
                .withQueryParam(CJS_OFFENCE_CODE_PARAM, equalTo(offenceCode))
                .willReturn(aResponse().withStatus(404)));
    }

    /**
     * Restores a rule override row to its default enabled/ERROR state via
     * {@link RuleOverrideService#saveOverride}, which both persists the row and evicts the
     * cache entry in a single call — preventing DB overrides made by one test from leaking
     * into others sharing the same TestContainers database.
     */
    protected static void resetRuleOverride(final RuleOverrideService ruleOverrideService, final String ruleId) {
        ruleOverrideService.saveOverride(ValidationRuleEntity.builder()
                .id(ruleId)
                .enabled(true)
                .severity("ERROR")
                .updatedAt(Instant.now())
                .updatedBy("test-reset")
                .build());
    }
}
