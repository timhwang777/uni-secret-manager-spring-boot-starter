package io.github.timhwang777.unisecret.config;

import java.time.Duration;

/**
 * Spring-free cache options used by the core module.
 *
 * @param enabled whether cache reads and writes are active
 * @param ttl time to keep successful provider results
 * @param maxSize maximum number of cached entries
 */
public record SecretCacheOptions(
        boolean enabled,
        Duration ttl,
        long maxSize
) {

    /**
     * Creates default cache options.
     *
     * @return enabled cache with five-minute TTL and 1000 entries
     */
    public static SecretCacheOptions defaults() {
        return new SecretCacheOptions(true, Duration.ofMinutes(5), 1000);
    }

    /**
     * Normalizes nullable values to safe defaults.
     */
    public SecretCacheOptions {
        if (ttl == null) {
            ttl = Duration.ofMinutes(5);
        }
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Cache ttl must be positive");
        }
        if (maxSize < 1) {
            throw new IllegalArgumentException("Cache maxSize must be at least 1");
        }
    }
}
