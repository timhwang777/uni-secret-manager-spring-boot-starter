package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.service.SecretRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for SecretRefreshService.
 */
@ExtendWith(MockitoExtension.class)
class SecretRefreshTest {

    @Mock
    private SecretCache cache;

    private SecretManagerProperties properties;
    private SecretRefreshService refreshService;

    @BeforeEach
    void setUp() {
        properties = new SecretManagerProperties();

        // Stub buildKey to return proper cache keys (lenient for tests that don't need it)
        lenient().when(cache.buildKey(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    String version = invocation.getArgument(1);
                    String field = invocation.getArgument(2);
                    return key + ":" + version + ":" + field;
                });

        refreshService = new SecretRefreshService(cache, properties);
    }

    @Test
    void shouldRefreshSingleSecret() {
        String secretKey = "my-secret";

        refreshService.refresh(secretKey);

        // Verify cache was invalidated with the correct key
        verify(cache).invalidate("my-secret:latest:null");
    }

    @Test
    void shouldRefreshSingleSecretWithVersion() {
        String secretKey = "my-secret";
        String version = "v1";

        refreshService.refresh(secretKey, version);

        // Verify cache was invalidated for specific version
        String expectedKey = cache.buildKey(secretKey, version, null);
        verify(cache).invalidate(expectedKey);
    }

    @Test
    void shouldRefreshSingleSecretWithVersionAndField() {
        String secretKey = "my-secret";
        String version = "latest";
        String field = "password";

        refreshService.refresh(secretKey, version, field);

        // Verify cache was invalidated for specific version and field
        String expectedKey = cache.buildKey(secretKey, version, field);
        verify(cache).invalidate(expectedKey);
    }

    @Test
    void shouldRefreshAllSecrets() {
        refreshService.refreshAll();

        verify(cache).invalidateAll();
    }

    @Test
    void shouldReturnRefreshStatistics() {
        refreshService.refresh("secret1");
        refreshService.refresh("secret2");
        refreshService.refreshAll();

        SecretRefreshService.RefreshStats stats = refreshService.getRefreshStats();

        assertThat(stats.singleRefreshCount()).isEqualTo(2);
        assertThat(stats.fullRefreshCount()).isEqualTo(1);
        assertThat(stats.lastRefreshTime()).isNotNull();
    }

    @Test
    void shouldResetStatistics() {
        refreshService.refresh("secret1");
        refreshService.refreshAll();

        refreshService.resetStats();

        SecretRefreshService.RefreshStats stats = refreshService.getRefreshStats();
        assertThat(stats.singleRefreshCount()).isEqualTo(0);
        assertThat(stats.fullRefreshCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleMultipleRefreshesOfSameSecret() {
        String secretKey = "my-secret";

        refreshService.refresh(secretKey);
        refreshService.refresh(secretKey);
        refreshService.refresh(secretKey);

        SecretRefreshService.RefreshStats stats = refreshService.getRefreshStats();
        assertThat(stats.singleRefreshCount()).isEqualTo(3);
    }

    @Test
    void shouldInvalidateAllVariantsWhenRefreshingSecretWithoutVersion() {
        String secretKey = "my-secret";

        refreshService.refresh(secretKey);

        // Should invalidate the secret with default version and no field
        verify(cache).invalidate(cache.buildKey(secretKey, "latest", null));
    }
}
