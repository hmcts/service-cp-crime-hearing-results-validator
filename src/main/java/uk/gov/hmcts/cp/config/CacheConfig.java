package uk.gov.hmcts.cp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures application caches used for runtime rule override lookups.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Creates the cache manager with per-cache TTL configuration.
     *
     * @param overrideTtlSeconds time-to-live for rule override cache entries in seconds
     * @param featureTtlSeconds time-to-live for feature flag cache entries in seconds
     * @param referencedataOffencesTtlSeconds time-to-live for reference-data offence
     *         classification cache entries in seconds — a much longer default than the other two
     *         caches, since offence classification data changes far less often than rule
     *         overrides or feature flags
     * @return cache manager backed by Caffeine
     */
    @Bean
    public CacheManager cacheManager(
            @Value("${validation.cache.override-ttl-seconds:30}") final long overrideTtlSeconds,
            @Value("${feature.cache.ttl-seconds:600}") final long featureTtlSeconds,
            @Value("${referencedata.offences.cache.ttl-seconds:3600}") final long referencedataOffencesTtlSeconds) {
        final CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("ruleOverrides",
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(overrideTtlSeconds))
                        .build());
        manager.registerCustomCache("featureFlags",
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(featureTtlSeconds))
                        .build());
        manager.registerCustomCache("referencedataOffences",
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(referencedataOffencesTtlSeconds))
                        .build());
        return manager;
    }
}
