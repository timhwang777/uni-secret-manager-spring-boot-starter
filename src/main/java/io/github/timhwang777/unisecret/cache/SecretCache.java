package io.github.timhwang777.unisecret.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * In-memory cache for retrieved secrets to reduce provider calls.
 *
 * <h2>Why Cache Secrets?</h2>
 * <ul>
 *   <li><b>Performance</b>: Avoid network calls to cloud providers on every access</li>
 *   <li><b>Cost</b>: Cloud provider API calls often have per-request charges</li>
 *   <li><b>Reliability</b>: Cached values available even during brief provider outages</li>
 * </ul>
 *
 * <h2>Cache Implementation</h2>
 * Uses <a href="https://github.com/ben-manes/caffeine">Caffeine</a>, a high-performance
 * Java caching library. Caffeine provides:
 * <ul>
 *   <li>Time-based expiration (TTL)</li>
 *   <li>Size-based eviction (LRU when max size reached)</li>
 *   <li>Thread-safe concurrent access</li>
 *   <li>Statistics tracking (hit rate, miss count, etc.)</li>
 * </ul>
 *
 * <h2>Cache Key Format</h2>
 * Keys are composite to uniquely identify each secret variation:
 * <pre>
 * {secretKey}:{version}:{field}
 *
 * Examples:
 * - "database-password:latest:null"     → entire secret, latest version
 * - "config:5:password"                  → password field from version 5
 * - "api-key:latest:null"               → API key, latest version
 * </pre>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * secrets:
 *   cache:
 *     enabled: true       # Set to false to disable caching
 *     ttl: 5m             # Time-to-live (5 minutes default)
 *     max-size: 1000      # Maximum entries before eviction
 * }</pre>
 *
 * <h2>Cache Invalidation</h2>
 * The cache can be invalidated via:
 * <ul>
 *   <li>{@link io.github.timhwang777.unisecret.service.SecretRefreshService#refresh} - single secret</li>
 *   <li>{@link io.github.timhwang777.unisecret.service.SecretRefreshService#refreshAll} - all secrets</li>
 *   <li>TTL expiration - automatic</li>
 * </ul>
 *
 * @see io.github.timhwang777.unisecret.service.SecretRefreshService
 */
@Slf4j
public class SecretCache {

    /**
     * The underlying Caffeine cache instance.
     * Null when caching is disabled to avoid memory allocation.
     */
    private final Cache<String, String> cache;

    /**
     * Whether caching is enabled.
     * When false, all cache operations become no-ops.
     */
    private final boolean enabled;

    /**
     * Creates a new cache with the given configuration.
     *
     * @param config cache settings (enabled, TTL, max size)
     */
    public SecretCache(SecretManagerProperties.Cache config) {
        this.enabled = config.isEnabled();

        if (enabled) {
            // Build Caffeine cache with configured settings
            this.cache = Caffeine.newBuilder()
                    .expireAfterWrite(config.getTtl().getSeconds(), TimeUnit.SECONDS)  // Entries expire after TTL
                    .maximumSize(config.getMaxSize())  // Evict LRU entries when size exceeded
                    .recordStats()  // Enable statistics tracking (hit rate, etc.)
                    .build();
            log.info("Secret cache initialized (TTL: {}s, Max Size: {})",
                    config.getTtl().getSeconds(), config.getMaxSize());
        } else {
            // Caching disabled - don't allocate cache memory
            this.cache = null;
            log.info("Secret cache is disabled");
        }
    }

    /**
     * Retrieves a cached secret value by its cache key.
     *
     * <p>This is the primary cache lookup method used by SecretResolver.
     * Returns empty if caching is disabled or the key is not in cache.</p>
     *
     * @param key the cache key (format: "{secretKey}:{version}:{field}")
     * @return the cached secret value, or empty if not found or cache disabled
     */
    public Optional<String> get(String key) {
        // Early return if caching is disabled
        if (!enabled || cache == null) {
            return Optional.empty();
        }

        // Attempt cache lookup
        String value = cache.getIfPresent(key);

        // Log for debugging cache effectiveness
        if (value != null) {
            log.debug("Cache hit for key: {}", key);
        } else {
            log.debug("Cache miss for key: {}", key);
        }

        return Optional.ofNullable(value);
    }

    /**
     * Stores a secret value in the cache.
     *
     * <p>Called by SecretResolver after successfully retrieving a secret from a provider.
     * The value will be automatically evicted after TTL expires or when max size is reached.</p>
     *
     * @param key   the cache key (format: "{secretKey}:{version}:{field}")
     * @param value the secret value to cache
     */
    public void put(String key, String value) {
        // No-op if caching is disabled
        if (!enabled || cache == null) {
            return;
        }

        cache.put(key, value);
        log.debug("Cached secret with key: {}", key);
    }

    /**
     * Removes a specific entry from the cache.
     *
     * <p>Used by {@link io.github.timhwang777.unisecret.service.SecretRefreshService}
     * to force re-fetching of a specific secret from the provider.</p>
     *
     * @param key the cache key to remove
     */
    public void invalidate(String key) {
        // No-op if caching is disabled
        if (!enabled || cache == null) {
            return;
        }

        cache.invalidate(key);
        log.debug("Invalidated cache key: {}", key);
    }

    /**
     * Clears all entries from the cache.
     *
     * <p>Used by {@link io.github.timhwang777.unisecret.service.SecretRefreshService#refreshAll}
     * to force re-fetching all secrets. This is useful after a bulk secret rotation.</p>
     */
    public void invalidateAll() {
        // No-op if caching is disabled
        if (!enabled || cache == null) {
            return;
        }

        cache.invalidateAll();
        log.info("Invalidated all cached secrets");
    }

    /**
     * Builds a unique cache key from secret metadata.
     *
     * <p>The key format ensures that different versions and field extractions
     * of the same secret are cached separately:</p>
     * <pre>
     * buildKey("db-config", "latest", "password") → "db-config:latest:password"
     * buildKey("db-config", "latest", null)       → "db-config:latest:null"
     * buildKey("db-config", "5", "password")      → "db-config:5:password"
     * </pre>
     *
     * @param secretKey the secret name/key
     * @param version   the secret version (null becomes "null")
     * @param field     the JSON field path (null becomes "null")
     * @return the composite cache key
     */
    public String buildKey(String secretKey, String version, String field) {
        // Normalize null values to string "null" for consistent key format
        String normalizedVersion = version != null ? version : "null";
        String normalizedField = field != null ? field : "null";
        return secretKey + ":" + normalizedVersion + ":" + normalizedField;
    }

    /**
     * Returns current cache statistics for monitoring.
     *
     * <p>Useful for monitoring cache effectiveness and tuning configuration.
     * A low hit rate may indicate TTL is too short or secrets are accessed infrequently.</p>
     *
     * @return statistics including hit/miss counts and hit rate
     */
    public CacheStats getStats() {
        // Return zeroed stats if caching is disabled
        if (!enabled || cache == null) {
            return new CacheStats(0, 0, 0, 0.0);
        }

        // Get stats from Caffeine and wrap in our record type
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = cache.stats();
        return new CacheStats(
                cache.estimatedSize(),  // Current number of entries
                stats.hitCount(),       // Total cache hits
                stats.missCount(),      // Total cache misses
                stats.hitRate()         // Hit rate as decimal (0.0 to 1.0)
        );
    }

    /**
     * Immutable snapshot of cache statistics for monitoring.
     *
     * @param estimatedSize approximate number of entries currently in cache
     * @param hitCount      total number of cache hits since startup
     * @param missCount     total number of cache misses since startup
     * @param hitRate       ratio of hits to total lookups (0.0 to 1.0)
     */
    public record CacheStats(
            long estimatedSize,
            long hitCount,
            long missCount,
            double hitRate
    ) {
    }
}
