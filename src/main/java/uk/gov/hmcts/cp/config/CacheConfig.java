package uk.gov.hmcts.cp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configures application caches used for runtime rule override lookups.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Creates the cache manager used to hold database-backed rule override values.
     *
     * @param ttlSeconds time-to-live for override cache entries in seconds
     * @return cache manager backed by Caffeine
     */
    @Bean
    public CacheManager cacheManager(
            @Value("${validation.cache.override-ttl-seconds:30}") final long ttlSeconds) {
        final CaffeineCacheManager manager = new CaffeineCacheManager("ruleOverrides");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds)));
        return manager;
    }
}
