package uk.gov.hmcts.cp.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.config.TestContainersInitialise;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
@AutoConfigureMockMvc
@Slf4j
public abstract class IntegrationTestBase {

    protected static final String IDENTITY_PATH =
            "/usersgroups-query-api/query/api/rest/usersgroups/users/logged-in-user/permissions";

    protected static final WireMockServer IDENTITY_WIRE_MOCK =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        IDENTITY_WIRE_MOCK.start();
        stubDefaultIdentityResponse();
    }

    @Resource
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void overrideIdentityUrl(DynamicPropertyRegistry registry) {
        registry.add("authz.http.identity-url-template",
                () -> "http://localhost:" + IDENTITY_WIRE_MOCK.port() + IDENTITY_PATH);
    }

    /**
     * Default stub that allows all requests through by returning the "System Users" group.
     * Tests that need specific group behaviour should call {@link #stubIdentityResponse(String)}
     * after resetting WireMock.
     */
    private static void stubDefaultIdentityResponse() {
        stubIdentityResponse("System Users");
    }

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
}
