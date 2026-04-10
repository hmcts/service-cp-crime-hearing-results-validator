package uk.gov.hmcts.cp.services.feature;

import java.net.URI;
import lombok.Getter;

/**
 * Immutable value object that parses an Azure App Configuration connection string
 * into its component parts: endpoint, host, credential ID, and secret.
 */
@Getter
public final class AzureConnectionInfo {

    private final String endpoint;
    private final String host;
    private final String id;
    private final String secret;

    private AzureConnectionInfo(final String endpoint, final String host, final String id, final String secret) {
        this.endpoint = endpoint;
        this.host = host;
        this.id = id;
        this.secret = secret;
    }

    /**
     * Parses an Azure App Configuration connection string.
     * {@code Endpoint=https://...;Id=...;Secret=...}
     *
     * @param connectionString the raw connection string
     * @return parsed connection info
     * @throws IllegalArgumentException if the string is null, blank, or missing required segments
     */
    public static AzureConnectionInfo parse(final String connectionString) {
        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalArgumentException("Connection string must not be null or blank");
        }

        String endpoint = null;
        String id = null;
        String secret = null;

        for (final String segment : connectionString.split(";")) {
            if (segment.startsWith("Endpoint=")) {
                endpoint = segment.substring("Endpoint=".length());
            } else if (segment.startsWith("Id=")) {
                id = segment.substring("Id=".length());
            } else if (segment.startsWith("Secret=")) {
                secret = segment.substring("Secret=".length());
            }
        }

        if (endpoint == null || id == null || secret == null) {
            throw new IllegalArgumentException(
                    "Connection string must contain Endpoint, Id, and Secret segments");
        }

        final String host = URI.create(endpoint).getHost();
        return new AzureConnectionInfo(endpoint, host, id, secret);
    }
}
