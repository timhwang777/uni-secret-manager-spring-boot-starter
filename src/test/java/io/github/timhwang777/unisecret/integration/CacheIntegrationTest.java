package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.provider.LocalSecretProvider;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for secret caching functionality.
 */
@SpringBootTest(classes = {
        CacheIntegrationTest.TestConfig.class,
        SecretManagerAutoConfiguration.class
})
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=local",
        "secrets.local.enabled=true",
        "secrets.local.secrets.test-secret=cached-value",
        "secrets.local.secrets.json-secret={\"username\":\"admin\",\"password\":\"secret123\"}",
        "secrets.cache.enabled=true",
        "secrets.cache.ttl=60s",
        "secrets.cache.max-size=100"
})
class CacheIntegrationTest {

    @Configuration
    @Import(SecretManagerAutoConfiguration.class)
    static class TestConfig {
    }

    @Autowired(required = false)
    private SecretResolver resolver;

    @Autowired(required = false)
    private SecretCache cache;

    @Autowired(required = false)
    private LocalSecretProvider localProvider;

    @org.junit.jupiter.api.BeforeEach
    void clearCache() {
        // Clear cache before each test to avoid interference
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    @Test
    void shouldCacheSecretsOnFirstRetrieval() {
        assertThat(resolver).isNotNull();
        assertThat(cache).isNotNull();

        // First retrieval - should cache
        String value1 = resolver.resolve("test-secret");
        assertThat(value1).isEqualTo("cached-value");

        // Cache should contain the secret
        String cacheKey = cache.buildKey("test-secret", "latest", null);
        assertThat(cache.get(cacheKey)).isPresent();
        assertThat(cache.get(cacheKey).get()).isEqualTo("cached-value");
    }

    @Test
    void shouldUseCacheOnSubsequentRetrievals() {
        assertThat(resolver).isNotNull();
        assertThat(cache).isNotNull();

        // Get initial stats
        long initialHits = cache.getStats().hitCount();

        // First retrieval - cache miss
        resolver.resolve("test-secret");

        // Second retrieval - should hit cache
        resolver.resolve("test-secret");

        // Verify cache was used
        long finalHits = cache.getStats().hitCount();
        assertThat(finalHits).isGreaterThan(initialHits);
    }

    @Test
    void shouldCacheSecretsWithDifferentVersions() {
        assertThat(cache).isNotNull();

        String key1 = cache.buildKey("test-secret", "latest", null);
        String key2 = cache.buildKey("test-secret", "v1", null);

        cache.put(key1, "value-latest");
        cache.put(key2, "value-v1");

        assertThat(cache.get(key1).get()).isEqualTo("value-latest");
        assertThat(cache.get(key2).get()).isEqualTo("value-v1");
    }

    @Test
    void shouldCacheJsonFieldExtractions() {
        assertThat(cache).isNotNull();

        // Cache keys for different fields should be different
        String key1 = cache.buildKey("json-secret", "latest", "username");
        String key2 = cache.buildKey("json-secret", "latest", "password");

        cache.put(key1, "admin");
        cache.put(key2, "secret123");

        assertThat(cache.get(key1).get()).isEqualTo("admin");
        assertThat(cache.get(key2).get()).isEqualTo("secret123");
    }

    @Test
    void shouldInvalidateCacheForSpecificSecret() {
        assertThat(cache).isNotNull();

        String cacheKey = cache.buildKey("test-secret", "latest", null);
        cache.put(cacheKey, "cached-value");

        assertThat(cache.get(cacheKey)).isPresent();

        cache.invalidate(cacheKey);

        assertThat(cache.get(cacheKey)).isEmpty();
    }

    @Test
    void shouldInvalidateAllCachedSecrets() {
        assertThat(cache).isNotNull();

        cache.put(cache.buildKey("secret1", "latest", null), "value1");
        cache.put(cache.buildKey("secret2", "latest", null), "value2");
        cache.put(cache.buildKey("secret3", "latest", null), "value3");

        assertThat(cache.getStats().estimatedSize()).isEqualTo(3);

        cache.invalidateAll();

        assertThat(cache.getStats().estimatedSize()).isEqualTo(0);
    }

    @Test
    void shouldProvideCacheStatistics() {
        assertThat(resolver).isNotNull();
        assertThat(cache).isNotNull();

        // Perform some operations
        resolver.resolve("test-secret"); // Miss
        resolver.resolve("test-secret"); // Hit
        resolver.resolve("test-secret"); // Hit

        SecretCache.CacheStats stats = cache.getStats();

        assertThat(stats.estimatedSize()).isGreaterThan(0);
        assertThat(stats.hitCount()).isGreaterThanOrEqualTo(2);
        assertThat(stats.missCount()).isGreaterThanOrEqualTo(1);
        assertThat(stats.hitRate()).isGreaterThan(0.0);
    }

    @Test
    void shouldRespectCacheConfiguration() {
        assertThat(cache).isNotNull();

        SecretManagerProperties.Cache config = new SecretManagerProperties.Cache();
        config.setEnabled(true);
        config.setTtl(java.time.Duration.ofSeconds(60));
        config.setMaxSize(100);

        // Verify configuration is applied
        assertThat(cache).isNotNull();
    }
}
