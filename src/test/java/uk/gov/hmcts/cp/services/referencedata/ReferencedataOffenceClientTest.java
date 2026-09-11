package uk.gov.hmcts.cp.services.referencedata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import uk.gov.hmcts.cp.filters.tracing.TracingFilter;

/**
 * Unit tests for {@link ReferencedataOffenceClient} against a local {@link WireMockServer} — the
 * client builds its own {@code RestTemplate} internally, so exercising real HTTP round trips
 * (including a deterministic timeout via a fixed WireMock delay) is more faithful than mocking
 * the transport. Caching behaviour (the {@code @Cacheable} annotation) requires a Spring AOP
 * proxy and is instead verified in {@code SexualOffenceNotificationRuleIT}, which runs inside a
 * full Spring context.
 */
class ReferencedataOffenceClientTest {

    private static final String OFFENCE_CODE = "SX03007C";
    private static final String OFFENCE_ID = "0000357a-2b27-3eb5-9377-d7e9d680eb87";
    private static final String PATH =
            "/referencedataoffences-query-api/query/api/rest/referencedataoffences/offences";
    private static final String QUERY_PARAM = "cjsoffencecode";

    private WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopWireMock() {
        if (wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    @Nested
    @DisplayName("misCode present or absent")
    class MisCodePresence {

        @Test
        void lookupMisCode_whenMisCodePresent_shouldReturnIt() {
            stubOffencesList("""
                    {"offences": [{"offenceId": "%s", "misCode": "SEX", "cjsOffenceCode": "%s"}]}
                    """.formatted(OFFENCE_ID, OFFENCE_CODE));

            Optional<String> result = client().lookupMisCode(OFFENCE_CODE);

            assertThat(result).contains("SEX");
        }

        @Test
        void lookupMisCode_whenMisCodeAbsent_shouldReturnEmpty() {
            stubOffencesList("""
                    {"offences": [{"offenceId": "%s", "cjsOffenceCode": "%s"}]}
                    """.formatted(OFFENCE_ID, OFFENCE_CODE));

            Optional<String> result = client().lookupMisCode(OFFENCE_CODE);

            assertThat(result).isEmpty();
        }

        @Test
        void lookupMisCode_whenMisCodeBlank_shouldReturnEmpty() {
            stubOffencesList("""
                    {"offences": [{"offenceId": "%s", "misCode": ""}]}
                    """.formatted(OFFENCE_ID));

            Optional<String> result = client().lookupMisCode(OFFENCE_CODE);

            assertThat(result).isEmpty();
        }

        @Test
        void lookupMisCode_whenOffencesListEmpty_shouldReturnEmpty() {
            stubOffencesList("{\"offences\": []}");

            Optional<String> result = client().lookupMisCode(OFFENCE_CODE);

            assertThat(result).isEmpty();
        }

        @Test
        void lookupMisCode_whenOffencesFieldMissing_shouldReturnEmpty() {
            stubOffencesList("{}");

            Optional<String> result = client().lookupMisCode(OFFENCE_CODE);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("fail-open on any failure")
    class FailOpen {

        @Test
        void lookupMisCode_when404_shouldReturnEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(PATH))
                    .withQueryParam(QUERY_PARAM, equalTo(OFFENCE_CODE))
                    .willReturn(aResponse().withStatus(404)));

            Optional<String> result = client().lookupMisCode(OFFENCE_CODE);

            assertThat(result).isEmpty();
        }

        @Test
        void lookupMisCode_when500_shouldReturnEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(PATH))
                    .withQueryParam(QUERY_PARAM, equalTo(OFFENCE_CODE))
                    .willReturn(aResponse().withStatus(500)));

            Optional<String> result = client().lookupMisCode(OFFENCE_CODE);

            assertThat(result).isEmpty();
        }

        @Test
        void lookupMisCode_whenMalformedJson_shouldReturnEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(PATH))
                    .withQueryParam(QUERY_PARAM, equalTo(OFFENCE_CODE))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/vnd.referencedataoffences.offences-list+json")
                            .withBody("{not valid json")));

            Optional<String> result = client().lookupMisCode(OFFENCE_CODE);

            assertThat(result).isEmpty();
        }

        @Test
        void lookupMisCode_whenReadTimesOut_shouldReturnEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(PATH))
                    .withQueryParam(QUERY_PARAM, equalTo(OFFENCE_CODE))
                    .willReturn(aResponse().withStatus(200)
                            .withFixedDelay(500) // exceeds the 100ms read timeout used by client()
                            .withBody("{\"offences\": [{\"misCode\": \"SEX\"}]}")));

            Optional<String> result = client().lookupMisCode(OFFENCE_CODE);

            assertThat(result).isEmpty();
        }

        @Test
        void lookupMisCode_serverUnreachable_shouldReturnEmpty() {
            ReferencedataOffenceClient unreachableClient = client(); // resolve the port first
            wireMock.stop(); // then take the server down so nothing answers

            Optional<String> result = unreachableClient.lookupMisCode(OFFENCE_CODE);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("CJSCPPUID header forwarding")
    class CjscppuidForwarding {

        @AfterEach
        void clearMdc() {
            MDC.remove(TracingFilter.USER_ID);
        }

        @Test
        void lookupMisCode_whenMdcHasUserId_shouldForwardCjscppuidHeader() {
            MDC.put(TracingFilter.USER_ID, "test-user-123");
            stubOffencesList("{\"offences\": [{\"misCode\": \"SEX\"}]}");

            client().lookupMisCode(OFFENCE_CODE);

            wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
                    .withQueryParam(QUERY_PARAM, equalTo(OFFENCE_CODE))
                    .withHeader("CJSCPPUID", equalTo("test-user-123")));
        }

        @Test
        void lookupMisCode_whenMdcHasNoUserId_shouldNotSendCjscppuidHeader() {
            stubOffencesList("{\"offences\": [{\"misCode\": \"SEX\"}]}");

            client().lookupMisCode(OFFENCE_CODE);

            wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
                    .withQueryParam(QUERY_PARAM, equalTo(OFFENCE_CODE))
                    .withHeader("CJSCPPUID", absent()));
        }

        @Test
        void lookupMisCode_whenMdcUserIdBlank_shouldNotSendCjscppuidHeader() {
            MDC.put(TracingFilter.USER_ID, "   ");
            stubOffencesList("{\"offences\": [{\"misCode\": \"SEX\"}]}");

            client().lookupMisCode(OFFENCE_CODE);

            wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
                    .withQueryParam(QUERY_PARAM, equalTo(OFFENCE_CODE))
                    .withHeader("CJSCPPUID", absent()));
        }
    }

    @Nested
    @DisplayName("short-circuits without calling the server")
    class ShortCircuit {

        @Test
        void lookupMisCode_whenDisabled_shouldReturnEmptyAndNotCallServer() {
            stubOffencesList("{\"offences\": [{\"misCode\": \"SEX\"}]}");

            Optional<String> result = new ReferencedataOffenceClient(properties(false)).lookupMisCode(OFFENCE_CODE);

            assertThat(result).isEmpty();
            wireMock.verify(0, getRequestedFor(urlPathEqualTo(PATH)));
        }

        @Test
        void lookupMisCode_whenOffenceCodeBlank_shouldReturnEmpty() {
            Optional<String> result = client().lookupMisCode(" ");

            assertThat(result).isEmpty();
        }

        @Test
        void lookupMisCode_whenOffenceCodeNull_shouldReturnEmpty() {
            Optional<String> result = client().lookupMisCode(null);

            assertThat(result).isEmpty();
        }
    }

    private void stubOffencesList(final String responseBody) {
        wireMock.stubFor(get(urlPathEqualTo(PATH))
                .withQueryParam(QUERY_PARAM, equalTo(OFFENCE_CODE))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/vnd.referencedataoffences.offences-list+json")
                        .withBody(responseBody)));
    }

    private ReferencedataOffenceClient client() {
        return new ReferencedataOffenceClient(properties(true));
    }

    private ReferencedataOffenceProperties properties(final boolean enabled) {
        return new ReferencedataOffenceProperties(
                enabled,
                "http://localhost:" + wireMock.port() + PATH + "?" + QUERY_PARAM + "={offenceCode}",
                "application/vnd.referencedataoffences.offences-list+json",
                100,
                100);
    }
}
