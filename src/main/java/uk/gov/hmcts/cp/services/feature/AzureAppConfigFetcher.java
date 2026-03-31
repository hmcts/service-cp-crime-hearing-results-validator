package uk.gov.hmcts.cp.services.feature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Fetches feature flags from Azure App Configuration using HMAC-SHA256 signed REST calls.
 * Returns an empty map on any failure (fail-open).
 */
@Service
@Slf4j
public class AzureAppConfigFetcher {

    private static final String FEATURE_PREFIX = ".appconfig.featureflag/";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 5;
    private static final int HTTP_OK = 200;

    private final AzureConnectionInfo connectionInfo;
    private final HttpClient httpClient;

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public AzureAppConfigFetcher(
            @Value("${feature.connection-string:}") final String connectionString) {
        AzureConnectionInfo parsed = null;
        if (!connectionString.isBlank()) {
            try {
                parsed = AzureConnectionInfo.parse(connectionString);
            } catch (Exception e) {
                log.warn("Invalid feature connection string, feature toggle will default to enabled: {}", e.getMessage());
            }
        }
        this.connectionInfo = parsed;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    /**
     * Fetches all feature flags for the given label from Azure App Configuration.
     *
     * @param label the environment label (e.g. "STE86")
     * @return map of feature name to enabled status, or empty map on failure
     */
    @Cacheable(value = "featureFlags", key = "#label")
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Map<String, Boolean> fetchFeatures(final String label) {
        Map<String, Boolean> result = Collections.emptyMap();

        if (connectionInfo != null) {
            try {
                final String path = "/kv?key=" + FEATURE_PREFIX + "*&label=" + label;
                final HttpRequest request = buildSignedRequest(path);
                final HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == HTTP_OK) {
                    result = parseFeatures(response.body());
                } else {
                    log.warn("Azure App Configuration returned HTTP {}", response.statusCode());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch features from Azure App Configuration: {}", e.getMessage());
            }
        } else {
            log.debug("Azure App Configuration not configured, returning empty feature map");
        }

        return result;
    }

    /* default */ static String buildContentHash() {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(new byte[0]);
            return Base64.getEncoder().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /* default */ static Map<String, Boolean> parseFeatures(final String responseBody) {
        final Map<String, Boolean> features = new HashMap<>();
        try {
            final JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            final JsonNode items = root.get("items");
            if (items != null && items.isArray()) {
                for (final JsonNode item : items) {
                    final String contentType = item.has("content_type")
                            ? item.get("content_type").asText() : "";
                    if (StringUtils.isNotEmpty(contentType)) {
                        if (item != null && item.get("key") != null && item.get("value") != null) {
                            final String key = item.get("key").asText().replace(FEATURE_PREFIX, "");
                            final JsonNode valueNode = OBJECT_MAPPER.readTree(item.get("value").asText());
                            final boolean enabled = valueNode.has("enabled")
                                    && valueNode.get("enabled").asBoolean();
                            features.put(key, enabled);
                        }
                    }
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to parse Azure App Configuration response: {}", e.getMessage());
        }
        return features;
    }

    private HttpRequest buildSignedRequest(final String path) {
        final String requestTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        final String contentHash = buildContentHash();
        final String host = connectionInfo.getHost();

        final String stringToSign = String.format("GET\n%s\n%s;%s;%s",
                path, requestTime, host, contentHash);
        final String signature = computeSignature(stringToSign, connectionInfo.getSecret());
        final String authorization = String.format(
                "HMAC-SHA256 Credential=%s, SignedHeaders=x-ms-date;host;x-ms-content-sha256, Signature=%s",
                connectionInfo.getId(), signature);

        return HttpRequest.newBuilder()
                .uri(URI.create(connectionInfo.getEndpoint() + path))
                .header("x-ms-date", requestTime)
                .header("x-ms-content-sha256", contentHash)
                .header("Authorization", authorization)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();
    }

    private static String computeSignature(final String stringToSign, final String base64Secret) {
        try {
            final byte[] decodedKey = Base64.getDecoder().decode(base64Secret);
            final Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(decodedKey, HMAC_SHA256));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }
}
