package uk.gov.hmcts.cp.services.feature;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AzureConnectionInfo}.
 */
class AzureConnectionInfoTest {

    private static final String VALID_CONNECTION_STRING =
            "Endpoint=https://nle-ccp01-appconfig.azconfig.io;Id=GYQ4-lo-s0:e4at3LkBwFH;Secret=c2VjcmV0";

    @Test
    void parse_valid_connection_string_extracts_endpoint_id_and_secret() {
        AzureConnectionInfo info = AzureConnectionInfo.parse(VALID_CONNECTION_STRING);

        assertThat(info.getEndpoint()).isEqualTo("https://nle-ccp01-appconfig.azconfig.io");
        assertThat(info.getId()).isEqualTo("GYQ4-lo-s0:e4at3LkBwFH");
        assertThat(info.getSecret()).isEqualTo("c2VjcmV0");
    }

    @Test
    void parse_extracts_host_from_endpoint() {
        AzureConnectionInfo info = AzureConnectionInfo.parse(VALID_CONNECTION_STRING);

        assertThat(info.getHost()).isEqualTo("nle-ccp01-appconfig.azconfig.io");
    }

    @Test
    void parse_null_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> AzureConnectionInfo.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_empty_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> AzureConnectionInfo.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_missing_segment_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> AzureConnectionInfo.parse("Endpoint=https://test.azconfig.io;Id=abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
