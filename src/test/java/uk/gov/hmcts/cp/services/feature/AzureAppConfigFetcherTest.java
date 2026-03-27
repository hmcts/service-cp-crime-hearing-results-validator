package uk.gov.hmcts.cp.services.feature;

import org.junit.jupiter.api.Test;

import java.util.Map;

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
}
