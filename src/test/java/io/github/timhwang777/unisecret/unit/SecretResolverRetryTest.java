package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretNotFoundException;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import io.github.timhwang777.unisecret.provider.ProviderType;
import io.github.timhwang777.unisecret.provider.SecretProvider;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for retry logic integration in SecretResolver.
 */
@ExtendWith(MockitoExtension.class)
class SecretResolverRetryTest {

    @Mock
    private SecretProvider mockProvider;

    @Mock
    private SecretCache cache;

    private SecretManagerProperties properties;
    private SecretResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new SecretManagerProperties();
        properties.setProviderOrder(List.of("aws"));
        properties.getRetry().setMaxAttempts(3);
        properties.getRetry().setInitialDelay(Duration.ofMillis(10));
        properties.getRetry().setMultiplier(2.0);
        properties.getRetry().setMaxDelay(Duration.ofMillis(100));

        // Configure cache mock
        lenient().when(cache.buildKey(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    String version = invocation.getArgument(1);
                    String field = invocation.getArgument(2);
                    return key + ":" + version + ":" + field;
                });
        lenient().when(cache.get(anyString())).thenReturn(Optional.empty());

        // Configure provider mock
        when(mockProvider.getProviderType()).thenReturn(ProviderType.AWS);
        when(mockProvider.isEnabled()).thenReturn(true);
    }

    @Test
    void shouldSucceedWithoutRetry() {
        when(mockProvider.getSecret(anyString(), anyString()))
                .thenReturn(Optional.of("secret-value"));

        resolver = new SecretResolver(List.of(mockProvider), properties, cache);

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("secret-value");
        verify(mockProvider, times(1)).getSecret("test-secret", "latest");
    }

    @Test
    void shouldRetryAndSucceedOnSecondAttempt() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        when(mockProvider.getSecret(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (attemptCount.incrementAndGet() < 2) {
                        throw new SecretProviderException("Transient error", null, true);
                    }
                    return Optional.of("secret-value");
                });

        resolver = new SecretResolver(List.of(mockProvider), properties, cache);

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("secret-value");
        assertThat(attemptCount.get()).isEqualTo(2);
        verify(mockProvider, times(2)).getSecret("test-secret", "latest");
    }

    @Test
    void shouldRetryAndSucceedOnThirdAttempt() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        when(mockProvider.getSecret(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (attemptCount.incrementAndGet() < 3) {
                        throw new SecretProviderException("Transient error", null, true);
                    }
                    return Optional.of("secret-value");
                });

        resolver = new SecretResolver(List.of(mockProvider), properties, cache);

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("secret-value");
        assertThat(attemptCount.get()).isEqualTo(3);
        verify(mockProvider, times(3)).getSecret("test-secret", "latest");
    }

    @Test
    void shouldExhaustRetriesAndThrowSecretNotFoundException() {
        when(mockProvider.getSecret(anyString(), anyString()))
                .thenThrow(new SecretProviderException("Always fails", null, true));

        resolver = new SecretResolver(List.of(mockProvider), properties, cache);

        assertThatThrownBy(() -> resolver.resolve("test-secret"))
                .isInstanceOf(SecretNotFoundException.class);

        verify(mockProvider, times(3)).getSecret("test-secret", "latest");
    }

    @Test
    void shouldNotRetryOnNonRetryableException() {
        when(mockProvider.getSecret(anyString(), anyString()))
                .thenThrow(new SecretProviderException("Non-retryable", null, false));

        resolver = new SecretResolver(List.of(mockProvider), properties, cache);

        assertThatThrownBy(() -> resolver.resolve("test-secret"))
                .isInstanceOf(SecretNotFoundException.class);

        verify(mockProvider, times(1)).getSecret("test-secret", "latest");
    }

    @Test
    void shouldNotRetryWhenSecretNotFound() {
        when(mockProvider.getSecret(anyString(), anyString()))
                .thenReturn(Optional.empty());

        resolver = new SecretResolver(List.of(mockProvider), properties, cache);

        assertThatThrownBy(() -> resolver.resolve("test-secret"))
                .isInstanceOf(SecretNotFoundException.class);

        verify(mockProvider, times(1)).getSecret("test-secret", "latest");
    }

    @Test
    void shouldRetryWithExponentialBackoff() {
        properties.getRetry().setInitialDelay(Duration.ofMillis(50));
        properties.getRetry().setMultiplier(2.0);
        properties.getRetry().setMaxAttempts(4);

        AtomicInteger attemptCount = new AtomicInteger(0);
        when(mockProvider.getSecret(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    if (attemptCount.incrementAndGet() < 4) {
                        throw new SecretProviderException("Transient error", null, true);
                    }
                    return Optional.of("secret-value");
                });

        resolver = new SecretResolver(List.of(mockProvider), properties, cache);

        long startTime = System.currentTimeMillis();
        String result = resolver.resolve("test-secret");
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertThat(result).isEqualTo("secret-value");
        assertThat(attemptCount.get()).isEqualTo(4);
        // Expected delays: 50ms, 100ms, 200ms = 350ms minimum
        // Allow some variance for execution time
        assertThat(elapsedTime).isGreaterThanOrEqualTo(250L);
    }

    @Test
    void shouldUseDefaultRetryConfiguration() {
        SecretManagerProperties defaultProps = new SecretManagerProperties();
        defaultProps.setProviderOrder(List.of("aws"));

        when(mockProvider.getSecret(anyString(), anyString()))
                .thenThrow(new SecretProviderException("Always fails", null, true));

        resolver = new SecretResolver(List.of(mockProvider), defaultProps, cache);

        assertThatThrownBy(() -> resolver.resolve("test-secret"))
                .isInstanceOf(SecretNotFoundException.class);

        // Default retry max attempts is 3
        verify(mockProvider, times(3)).getSecret("test-secret", "latest");
    }
}
