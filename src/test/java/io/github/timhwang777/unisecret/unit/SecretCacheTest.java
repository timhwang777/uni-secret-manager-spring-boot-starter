package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Unit tests for SecretCache.
 */
class SecretCacheTest {

    private SecretCache cache;
    private SecretManagerProperties.Cache cacheConfig;

    @BeforeEach
    void setUp() {
        cacheConfig = new SecretManagerProperties.Cache();
        cacheConfig.setEnabled(true);
        cacheConfig.setTtl(java.time.Duration.ofSeconds(60));
        cacheConfig.setMaxSize(100);

        cache = new SecretCache(cacheConfig);
    }

    @Test
    void shouldCacheSecretValue() {
        String key = "test-secret:latest:null";
        String value = "secret-value";

        cache.put(key, value);

        Optional<String> cached = cache.get(key);
        assertThat(cached).isPresent();
        assertThat(cached.get()).isEqualTo(value);
    }

    @Test
    void shouldReturnEmptyForNonExistentKey() {
        Optional<String> cached = cache.get("non-existent-key");
        assertThat(cached).isEmpty();
    }

    @Test
    void shouldEvictExpiredEntries() {
        cacheConfig.setTtl(java.time.Duration.ofSeconds(1));
        cache = new SecretCache(cacheConfig);

        String key = "expiring-secret:latest:null";
        cache.put(key, "value");

        // Should be present immediately
        assertThat(cache.get(key)).isPresent();

        // Wait for expiration
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(cache.get(key)).isEmpty());
    }

    @Test
    void shouldRespectMaxSize() {
        cacheConfig.setMaxSize(2);
        cache = new SecretCache(cacheConfig);

        cache.put("key1:latest:null", "value1");
        cache.put("key2:latest:null", "value2");
        cache.put("key3:latest:null", "value3");

        // Trigger cleanup by accessing cache
        cache.get("key3:latest:null");

        // Cache should eventually contain at most 2 entries (LRU eviction)
        // Caffeine's eviction is asynchronous, so wait for it
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(cache.getStats().estimatedSize()).isLessThanOrEqualTo(2));
    }

    @Test
    void shouldInvalidateSingleKey() {
        cache.put("key1:latest:null", "value1");
        cache.put("key2:latest:null", "value2");

        cache.invalidate("key1:latest:null");

        assertThat(cache.get("key1:latest:null")).isEmpty();
        assertThat(cache.get("key2:latest:null")).isPresent();
    }

    @Test
    void shouldInvalidateAll() {
        cache.put("key1:latest:null", "value1");
        cache.put("key2:latest:null", "value2");
        cache.put("key3:latest:null", "value3");

        cache.invalidateAll();

        assertThat(cache.get("key1:latest:null")).isEmpty();
        assertThat(cache.get("key2:latest:null")).isEmpty();
        assertThat(cache.get("key3:latest:null")).isEmpty();
    }

    @Test
    void shouldReturnCacheStatistics() {
        cache.put("key1:latest:null", "value1");
        cache.get("key1:latest:null"); // Hit
        cache.get("key2:latest:null"); // Miss

        SecretCache.CacheStats stats = cache.getStats();

        assertThat(stats.estimatedSize()).isEqualTo(1);
        assertThat(stats.hitCount()).isEqualTo(1);
        assertThat(stats.missCount()).isEqualTo(1);
        assertThat(stats.hitRate()).isGreaterThan(0.0);
    }

    @Test
    void shouldBuildCorrectCacheKey() {
        String key1 = cache.buildKey("my-secret", "latest", null);
        assertThat(key1).isEqualTo("my-secret:latest:null");

        String key2 = cache.buildKey("my-secret", "v1", null);
        assertThat(key2).isEqualTo("my-secret:v1:null");

        String key3 = cache.buildKey("my-secret", "latest", "password");
        assertThat(key3).isEqualTo("my-secret:latest:password");
    }

    @Test
    void shouldHandleNullVersionInCacheKey() {
        String key = cache.buildKey("my-secret", null, null);
        assertThat(key).isEqualTo("my-secret:null:null");
    }

    @Test
    void shouldNotCacheWhenDisabled() {
        cacheConfig.setEnabled(false);
        cache = new SecretCache(cacheConfig);

        cache.put("key1:latest:null", "value1");

        Optional<String> cached = cache.get("key1:latest:null");
        assertThat(cached).isEmpty();
    }

    @Test
    void shouldHandleEmptyStringsInCacheKey() {
        String key = cache.buildKey("my-secret", "", "");
        assertThat(key).isEqualTo("my-secret::");
    }

    @Test
    void shouldReportZeroStatsWhenEmpty() {
        SecretCache.CacheStats stats = cache.getStats();

        assertThat(stats.estimatedSize()).isEqualTo(0);
        assertThat(stats.hitCount()).isEqualTo(0);
        assertThat(stats.missCount()).isEqualTo(0);
    }

    @Test
    void shouldUpdateStatsOnMultipleOperations() {
        // Put 3 items
        cache.put("key1:latest:null", "value1");
        cache.put("key2:latest:null", "value2");
        cache.put("key3:latest:null", "value3");

        // 5 hits
        cache.get("key1:latest:null");
        cache.get("key2:latest:null");
        cache.get("key3:latest:null");
        cache.get("key1:latest:null");
        cache.get("key2:latest:null");

        // 2 misses
        cache.get("key4:latest:null");
        cache.get("key5:latest:null");

        SecretCache.CacheStats stats = cache.getStats();

        assertThat(stats.estimatedSize()).isEqualTo(3);
        assertThat(stats.hitCount()).isEqualTo(5);
        assertThat(stats.missCount()).isEqualTo(2);
        assertThat(stats.hitRate()).isEqualTo(5.0 / 7.0);
    }
}
