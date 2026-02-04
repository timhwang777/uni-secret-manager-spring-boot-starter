package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import io.github.timhwang777.unisecret.util.RetryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RetryHelper.
 */
class RetryHelperTest {

    private SecretManagerProperties.Retry retryConfig;
    private RetryHelper retryHelper;

    @BeforeEach
    void setUp() {
        retryConfig = new SecretManagerProperties.Retry();
        retryConfig.setMaxAttempts(3);
        retryConfig.setInitialDelay(Duration.ofMillis(10)); // Short delay for tests
        retryConfig.setMultiplier(2.0);
        retryConfig.setMaxDelay(Duration.ofMillis(100));

        retryHelper = new RetryHelper(retryConfig);
    }

    @Test
    void shouldSucceedOnFirstAttempt() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = retryHelper.executeWithRetry(() -> {
            attemptCount.incrementAndGet();
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attemptCount.get()).isEqualTo(1);
    }

    @Test
    void shouldRetryOnRetryableException() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = retryHelper.executeWithRetry(() -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new SecretProviderException("Transient error", null, true);
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attemptCount.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryOnNonRetryableException() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        assertThatThrownBy(() -> retryHelper.executeWithRetry(() -> {
            attemptCount.incrementAndGet();
            throw new SecretProviderException("Non-retryable error", null, false);
        }))
                .isInstanceOf(SecretProviderException.class)
                .hasMessageContaining("Non-retryable error");

        assertThat(attemptCount.get()).isEqualTo(1);
    }

    @Test
    void shouldExhaustRetriesAndThrowException() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        assertThatThrownBy(() -> retryHelper.executeWithRetry(() -> {
            attemptCount.incrementAndGet();
            throw new SecretProviderException("Always fails", null, true);
        }))
                .isInstanceOf(SecretProviderException.class)
                .hasMessageContaining("Always fails");

        assertThat(attemptCount.get()).isEqualTo(3);
    }

    @Test
    void shouldApplyExponentialBackoff() {
        retryConfig.setMaxAttempts(4);
        retryConfig.setInitialDelay(Duration.ofMillis(50));
        retryConfig.setMultiplier(2.0);
        retryConfig.setMaxDelay(Duration.ofSeconds(10));
        retryHelper = new RetryHelper(retryConfig);

        AtomicInteger attemptCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        assertThatThrownBy(() -> retryHelper.executeWithRetry(() -> {
            attemptCount.incrementAndGet();
            throw new SecretProviderException("Always fails", null, true);
        }))
                .isInstanceOf(SecretProviderException.class);

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Expected delays: 50ms, 100ms, 200ms = 350ms total minimum
        // Allow some variance for execution time
        assertThat(elapsedTime).isGreaterThanOrEqualTo(300L);
        assertThat(attemptCount.get()).isEqualTo(4);
    }

    @Test
    void shouldRespectMaxDelay() {
        retryConfig.setMaxAttempts(5);
        retryConfig.setInitialDelay(Duration.ofMillis(10));
        retryConfig.setMultiplier(10.0); // Very high multiplier
        retryConfig.setMaxDelay(Duration.ofMillis(50)); // Cap at 50ms
        retryHelper = new RetryHelper(retryConfig);

        AtomicInteger attemptCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        assertThatThrownBy(() -> retryHelper.executeWithRetry(() -> {
            attemptCount.incrementAndGet();
            throw new SecretProviderException("Always fails", null, true);
        }))
                .isInstanceOf(SecretProviderException.class);

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Delays should be capped: 10ms, 50ms, 50ms, 50ms = 160ms max
        assertThat(elapsedTime).isLessThan(300L);
        assertThat(attemptCount.get()).isEqualTo(5);
    }

    @Test
    void shouldHandleRuntimeExceptions() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        assertThatThrownBy(() -> retryHelper.executeWithRetry(() -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("Unexpected error");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unexpected error");

        // Should not retry on non-SecretProviderException
        assertThat(attemptCount.get()).isEqualTo(1);
    }

    @Test
    void shouldHandleNullResult() {
        String result = retryHelper.executeWithRetry(() -> null);
        assertThat(result).isNull();
    }

    @Test
    void shouldUseDefaultConfigurationWhenNotProvided() {
        SecretManagerProperties.Retry defaultConfig = new SecretManagerProperties.Retry();
        RetryHelper defaultRetryHelper = new RetryHelper(defaultConfig);

        AtomicInteger attemptCount = new AtomicInteger(0);

        assertThatThrownBy(() -> defaultRetryHelper.executeWithRetry(() -> {
            attemptCount.incrementAndGet();
            throw new SecretProviderException("Always fails", null, true);
        }))
                .isInstanceOf(SecretProviderException.class);

        // Default is 3 attempts
        assertThat(attemptCount.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryWhenMaxAttemptsIsOne() {
        retryConfig.setMaxAttempts(1);
        retryHelper = new RetryHelper(retryConfig);

        AtomicInteger attemptCount = new AtomicInteger(0);

        assertThatThrownBy(() -> retryHelper.executeWithRetry(() -> {
            attemptCount.incrementAndGet();
            throw new SecretProviderException("Fails", null, true);
        }))
                .isInstanceOf(SecretProviderException.class);

        assertThat(attemptCount.get()).isEqualTo(1);
    }

    @Test
    void shouldHandleInterruptedException() {
        retryConfig.setInitialDelay(Duration.ofSeconds(1));
        retryHelper = new RetryHelper(retryConfig);

        Thread.currentThread().interrupt(); // Set interrupt flag

        AtomicInteger attemptCount = new AtomicInteger(0);

        assertThatThrownBy(() -> retryHelper.executeWithRetry(() -> {
            attemptCount.incrementAndGet();
            throw new SecretProviderException("Retryable", null, true);
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Retry interrupted");

        // Should fail on first retry attempt when interrupted
        assertThat(attemptCount.get()).isEqualTo(1);

        // Clear interrupt flag
        Thread.interrupted();
    }
}
