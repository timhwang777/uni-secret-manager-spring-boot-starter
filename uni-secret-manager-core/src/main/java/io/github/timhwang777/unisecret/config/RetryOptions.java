package io.github.timhwang777.unisecret.config;

import java.time.Duration;

/**
 * Spring-free retry options used by the core module.
 *
 * @param maxAttempts maximum attempts including the initial try
 * @param initialDelay delay before the first retry
 * @param multiplier exponential backoff multiplier
 * @param maxDelay maximum delay between retries
 */
public record RetryOptions(
        int maxAttempts,
        Duration initialDelay,
        double multiplier,
        Duration maxDelay
) {

    /**
     * Creates default retry options.
     *
     * @return retry options matching the 1.x defaults
     */
    public static RetryOptions defaults() {
        return new RetryOptions(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10));
    }

    /**
     * Normalizes nullable values to safe defaults.
     */
    public RetryOptions {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Retry maxAttempts must be at least 1");
        }
        if (initialDelay == null) {
            initialDelay = Duration.ofSeconds(1);
        }
        if (maxDelay == null) {
            maxDelay = Duration.ofSeconds(10);
        }
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("Retry initialDelay must not be negative");
        }
        if (maxDelay.isNegative()) {
            throw new IllegalArgumentException("Retry maxDelay must not be negative");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("Retry multiplier must be at least 1.0");
        }
    }
}
