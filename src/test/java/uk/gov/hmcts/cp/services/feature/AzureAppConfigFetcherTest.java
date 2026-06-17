package uk.gov.hmcts.cp.services.feature;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AzureAppConfigFetcher}.
 */
class AzureAppConfigFetcherTest {

    @Test
    void buildContentHash_returns_base64_sha256_of_empty_string() {
        String hash = AzureAppConfigFetcher.buildContentHash();

        assertThat(hash).isEqualTo("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=");
    }

    @Test
    void parseFeatures_extracts_enabled_features_from_json_response() {
        String json = """
                {
                  "items": [
                    {
                      "key": ".appconfig.featureflag/ResultsValidation",
                      "value": "{\\"id\\":\\"ResultsValidation\\",\\"enabled\\":true}",
                      "content_type": "application/vnd.microsoft.appconfig.ff+json;charset=utf-8",
                      "label": "STE86"
                    },
                    {
                      "key": ".appconfig.featureflag/HearingNows",
                      "value": "{\\"id\\":\\"HearingNows\\",\\"enabled\\":false}",
                      "content_type": "application/vnd.microsoft.appconfig.ff+json;charset=utf-8",
                      "label": "STE86"
                    }
                  ]
                }
                """;

        Map<String, Boolean> features = AzureAppConfigFetcher.parseFeatures(json);

        assertThat(features).containsEntry("ResultsValidation", true);
        assertThat(features).containsEntry("HearingNows", false);
    }

    @Test
    void parseFeatures_skips_items_without_content_type() {
        String json = """
                {
                  "items": [
                    {
                      "key": "some-plain-setting",
                      "value": "some-value",
                      "content_type": "",
                      "label": "STE86"
                    },
                    {
                      "key": ".appconfig.featureflag/OPA",
                      "value": "{\\"id\\":\\"OPA\\",\\"enabled\\":true}",
                      "content_type": "application/vnd.microsoft.appconfig.ff+json;charset=utf-8",
                      "label": "STE86"
                    }
                  ]
                }
                """;

        Map<String, Boolean> features = AzureAppConfigFetcher.parseFeatures(json);

        assertThat(features).hasSize(1);
        assertThat(features).containsEntry("OPA", true);
    }

    @Test
    void parseFeatures_handles_empty_items_array() {
        String json = """
                { "items": [] }
                """;

        Map<String, Boolean> features = AzureAppConfigFetcher.parseFeatures(json);

        assertThat(features).isEmpty();
    }

    @Test
    void fetchFeatures_returns_empty_map_when_connection_string_blank() {
        AzureAppConfigFetcher fetcher = new AzureAppConfigFetcher("");

        Map<String, Boolean> features = fetcher.fetchFeatures("STE86");

        assertThat(features).isEmpty();
    }

    @Test
    void malformed_connection_string_should_not_crash_and_returns_empty_map() {
        AzureAppConfigFetcher fetcher = new AzureAppConfigFetcher("garbage-value");

        Map<String, Boolean> features = fetcher.fetchFeatures("STE86");

        assertThat(features).isEmpty();
    }

    /**
     * Exercises the live HTTP path against a stubbed Azure App Configuration endpoint, covering the
     * HMAC-signed request construction and the success/failure response handling that the static
     * parsing tests above cannot reach.
     */
    @Nested
    @DisplayName("fetchFeatures HTTP path")
    class HttpFetch {

        // Dummy base64 secret, computed at runtime (computeSignature base64-decodes it). Built from
        // a plain string rather than a literal so it is not mistaken for a real credential.
        private static final String SECRET =
                Base64.getEncoder().encodeToString("test-secret".getBytes(StandardCharsets.UTF_8));
        private static final String EMPTY_BODY_SHA256 = "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=";

        private WireMockServer wireMock;
        private String connectionString;

        @BeforeEach
        void startServer() {
            wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            wireMock.start();
            connectionString = "Endpoint=http://localhost:" + wireMock.port()
                    + ";Id=test-id;Secret=" + SECRET;
        }

        @AfterEach
        void stopServer() {
            wireMock.stop();
        }

        /**
         * Verifies that a configured fetcher signs the request (HMAC-SHA256 authorization, date and
         * content-hash headers) and returns the parsed feature flags on an HTTP 200 response.
         */
        @Test
        void fetchFeatures_with_valid_connection_should_sign_request_and_return_features() {
            String body = """
                    {
                      "items": [
                        {
                          "key": ".appconfig.featureflag/ResultsValidation",
                          "value": "{\\"id\\":\\"ResultsValidation\\",\\"enabled\\":true}",
                          "content_type": "application/vnd.microsoft.appconfig.ff+json;charset=utf-8",
                          "label": "STE86"
                        }
                      ]
                    }
                    """;
            wireMock.stubFor(get(urlPathEqualTo("/kv"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(body)));

            AzureAppConfigFetcher fetcher = new AzureAppConfigFetcher(connectionString);

            Map<String, Boolean> features = fetcher.fetchFeatures("STE86");

            assertThat(features).containsEntry("ResultsValidation", true);
            wireMock.verify(getRequestedFor(urlPathEqualTo("/kv"))
                    .withHeader("Authorization", matching("HMAC-SHA256 Credential=test-id.*Signature=.+"))
                    .withHeader("x-ms-content-sha256", equalTo(EMPTY_BODY_SHA256))
                    .withHeader("x-ms-date", matching(".+")));
        }

        /**
         * Verifies a non-200 response from Azure App Configuration is handled gracefully and yields
         * an empty feature map rather than propagating an error.
         */
        @Test
        void fetchFeatures_with_non_200_response_should_return_empty_map() {
            wireMock.stubFor(get(urlPathEqualTo("/kv"))
                    .willReturn(aResponse().withStatus(500)));

            AzureAppConfigFetcher fetcher = new AzureAppConfigFetcher(connectionString);

            assertThat(fetcher.fetchFeatures("STE86")).isEmpty();
        }
    }
}
