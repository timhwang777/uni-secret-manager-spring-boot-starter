package io.github.timhwang777.unisecret.util;

import io.github.timhwang777.unisecret.config.RetryOptions;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Utility for retrying operations with exponential backoff.
 *
 * <h2>Why Retry?</h2>
 * Cloud provider APIs can experience transient failures:
 * <ul>
 *   <li>Network timeouts</li>
 *   <li>Rate limiting (429 errors)</li>
 *   <li>Temporary service unavailability (503 errors)</li>
 *   <li>Load balancer issues</li>
 * </ul>
 * Retrying with exponential backoff often succeeds where a single attempt would fail.
 *
 * <h2>Exponential Backoff</h2>
 * Each retry waits longer than the previous one:
 * <pre>
 * Attempt 1: immediate
 * Attempt 2: wait 1s
 * Attempt 3: wait 2s  (1s × 2.0 multiplier)
 * Attempt 4: wait 4s  (2s × 2.0 multiplier)
 * Attempt 5: wait 8s  (4s × 2.0 multiplier, capped at maxDelay)
 * </pre>
 * This pattern reduces load on struggling services while still retrying.
 *
 * <h2>Retryable vs Non-Retryable</h2>
 * Not all errors should be retried. This helper only retries when:
 * <ol>
 *   <li>Exception is a {@link SecretProviderException}</li>
 *   <li>The exception has {@code retryable=true}</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * secrets:
 *   retry:
 *     max-attempts: 3        # Give up after 3 tries
 *     initial-delay: 1s      # First retry waits 1 second
 *     multiplier: 2.0        # Each subsequent retry doubles the wait
 *     max-delay: 10s         # Never wait more than 10 seconds
 * }</pre>
 *
 * @see io.github.timhwang777.unisecret.exception.SecretProviderException#retryable
 */
@Slf4j
public class RetryHelper {

    /**
     * Retry configuration.
     */
    private final RetryOptions config;

    public RetryHelper(RetryOptions config) {
        this.config = config == null ? RetryOptions.defaults() : config;
    }

    /**
     * Executes an operation with automatic retry for transient failures.
     *
     * <h3>Retry Logic</h3>
     * <pre>
     * 1. Execute operation
     * 2. If success → return result
     * 3. If SecretProviderException with retryable=true:
     *    a. If max attempts not reached → wait with backoff, goto 1
     *    b. If max attempts reached → throw exception
     * 4. If SecretProviderException with retryable=false → throw immediately
     * 5. If other exception → throw immediately (no retry)
     * </pre>
     *
     * @param operation the operation to execute (typically a provider.getSecret() call)
     * @param <T>       the return type of the operation
     * @return the result of the operation on success
     * @throws SecretProviderException if all retry attempts fail or error is non-retryable
     * @throws RuntimeException        if thread is interrupted during retry wait
     */
    public <T> T executeWithRetry(Supplier<T> operation) {
        int attempt = 0;
        long delay = config.initialDelay().toMillis();

        // Retry loop - continues until success or max attempts reached
        while (true) {
            attempt++;
            try {
                // Attempt the operation
                return operation.get();

            } catch (SecretProviderException e) {
                // CASE 1: Non-retryable error (e.g., access denied, decryption failure)
                // These won't succeed on retry, so fail immediately
                if (!e.isRetryable()) {
                    log.debug("Non-retryable exception encountered, not retrying: {}", e.getMessage());
                    throw e;
                }

                // CASE 2: Max attempts reached - give up
                if (attempt >= config.maxAttempts()) {
                    log.warn("Max retry attempts ({}) exhausted for operation: {}",
                            config.maxAttempts(), e.getMessage());
                    throw e;
                }

                // CASE 3: Retryable error with attempts remaining - retry after delay
                log.debug("Retryable exception on attempt {}/{}, retrying after {}ms: {}",
                        attempt, config.maxAttempts(), delay, e.getMessage());

                // Wait before retrying
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    // Restore interrupt flag and abort
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }

                // Calculate next delay with exponential backoff
                // Formula: delay = min(delay * multiplier, maxDelay)
                delay = Math.min(
                        (long) (delay * config.multiplier()),
                        config.maxDelay().toMillis()
                );

            } catch (Exception e) {
                // CASE 4: Non-SecretProviderException (unexpected error)
                // Don't retry - these are likely programming errors or system issues
                log.debug("Non-retryable exception type encountered: {}", e.getClass().getSimpleName());
                throw e;
            }
        }
    }
}
