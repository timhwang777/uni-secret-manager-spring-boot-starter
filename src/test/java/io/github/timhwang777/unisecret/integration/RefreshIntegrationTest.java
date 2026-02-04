package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import io.github.timhwang777.unisecret.service.SecretRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for secret refresh functionality.
 */
@SpringBootTest(classes = {
        RefreshIntegrationTest.TestConfig.class,
        SecretManagerAutoConfiguration.class
})
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=local",
        "secrets.local.enabled=true",
        "secrets.local.secrets.dynamic-secret=initial-value",
        "secrets.local.secrets.versioned-secret=v1-value",
        "secrets.cache.enabled=true",
        "secrets.cache.ttl=60s",
        "secrets.cache.max-size=100"
})
class RefreshIntegrationTest {

    @Configuration
    @Import(SecretManagerAutoConfiguration.class)
    static class TestConfig {
    }

    @Autowired(required = false)
    private SecretResolver resolver;

    @Autowired(required = false)
    private SecretCache cache;

    @Autowired(required = false)
    private SecretRefreshService refreshService;

    @BeforeEach
    void setUp() {
        // Clear cache before each test
        if (cache != null) {
            cache.invalidateAll();
        }
        if (refreshService != null) {
            refreshService.resetStats();
        }
    }

    @Test
    void shouldRefreshServiceBeAvailable() {
        assertThat(refreshService).isNotNull();
    }

    @Test
    void shouldRefreshSingleSecretAndInvalidateCache() {
        assertThat(resolver).isNotNull();
        assertThat(refreshService).isNotNull();

        // First retrieval - cache miss
        String value1 = resolver.resolve("dynamic-secret");
        assertThat(value1).isEqualTo("initial-value");

        // Verify it's cached
        String cacheKey = cache.buildKey("dynamic-secret", "latest", null);
        assertThat(cache.get(cacheKey)).isPresent();

        // Refresh the secret
        refreshService.refresh("dynamic-secret");

        // Verify cache was invalidated
        assertThat(cache.get(cacheKey)).isEmpty();
    }

    @Test
    void shouldRefreshAllSecretsAndInvalidateEntireCache() {
        assertThat(resolver).isNotNull();
        assertThat(refreshService).isNotNull();

        // Load multiple secrets into cache
        resolver.resolve("dynamic-secret");
        resolver.resolve("versioned-secret");

        // Verify cache has entries
        long initialSize = cache.getStats().estimatedSize();
        assertThat(initialSize).isGreaterThan(0);

        // Refresh all secrets
        refreshService.refreshAll();

        // Verify cache was cleared
        assertThat(cache.getStats().estimatedSize()).isEqualTo(0);
    }

    @Test
    void shouldTrackRefreshStatistics() {
        assertThat(refreshService).isNotNull();

        // Perform various refresh operations
        refreshService.refresh("secret1");
        refreshService.refresh("secret2");
        refreshService.refresh("secret3");
        refreshService.refreshAll();

        SecretRefreshService.RefreshStats stats = refreshService.getRefreshStats();

        assertThat(stats.singleRefreshCount()).isEqualTo(3);
        assertThat(stats.fullRefreshCount()).isEqualTo(1);
        assertThat(stats.lastRefreshTime()).isNotNull();
    }

    @Test
    void shouldResetRefreshStatistics() {
        assertThat(refreshService).isNotNull();

        refreshService.refresh("secret1");
        refreshService.refreshAll();

        SecretRefreshService.RefreshStats statsBefore = refreshService.getRefreshStats();
        assertThat(statsBefore.singleRefreshCount()).isGreaterThan(0);

        refreshService.resetStats();

        SecretRefreshService.RefreshStats statsAfter = refreshService.getRefreshStats();
        assertThat(statsAfter.singleRefreshCount()).isEqualTo(0);
        assertThat(statsAfter.fullRefreshCount()).isEqualTo(0);
    }

    @Test
    void shouldRefreshSpecificSecretVersion() {
        assertThat(refreshService).isNotNull();
        assertThat(cache).isNotNull();

        // Cache a specific version
        String cacheKey = cache.buildKey("versioned-secret", "v1", null);
        cache.put(cacheKey, "cached-value");

        assertThat(cache.get(cacheKey)).isPresent();

        // Refresh specific version
        refreshService.refresh("versioned-secret", "v1");

        // Verify it was invalidated
        assertThat(cache.get(cacheKey)).isEmpty();
    }

    @Test
    void shouldRefreshSpecificSecretField() {
        assertThat(refreshService).isNotNull();
        assertThat(cache).isNotNull();

        // Cache a specific field
        String cacheKey = cache.buildKey("dynamic-secret", "latest", "password");
        cache.put(cacheKey, "cached-password");

        assertThat(cache.get(cacheKey)).isPresent();

        // Refresh specific field
        refreshService.refresh("dynamic-secret", "latest", "password");

        // Verify it was invalidated
        assertThat(cache.get(cacheKey)).isEmpty();
    }

    @Test
    void shouldAllowSecretReloadAfterRefresh() {
        assertThat(resolver).isNotNull();
        assertThat(refreshService).isNotNull();

        // Load secret into cache
        String value1 = resolver.resolve("dynamic-secret");
        assertThat(value1).isEqualTo("initial-value");

        // Get cache stats before refresh
        long hitsBefore = cache.getStats().hitCount();

        // Refresh the secret
        refreshService.refresh("dynamic-secret");

        // Reload the secret - should query provider again
        String value2 = resolver.resolve("dynamic-secret");
        assertThat(value2).isEqualTo("initial-value");

        // Verify it was a cache miss (re-queried provider)
        long hitsAfter = cache.getStats().hitCount();
        // Hit count should not have increased since cache was invalidated
        assertThat(hitsAfter).isEqualTo(hitsBefore);
    }
}
