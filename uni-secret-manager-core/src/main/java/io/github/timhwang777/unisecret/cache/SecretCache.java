package io.github.timhwang777.unisecret.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.timhwang777.unisecret.config.SecretCacheOptions;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;

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
     * The underlying Caffeine cache instance. Null when caching is disabled.
     */
    private final Cache<SecretCacheKey, String> cache;

    /**
     * Whether caching is enabled.
     * When false, all cache operations become no-ops.
     */
    private final boolean enabled;

    /**
     * Creates a cache with default options.
     */
    public SecretCache() {
        this(SecretCacheOptions.defaults());
    }

    /**
     * Creates a new cache with the given options.
     *
     * @param options cache settings
     */
    public SecretCache(SecretCacheOptions options) {
        SecretCacheOptions effectiveOptions = options == null ? SecretCacheOptions.defaults() : options;
        this.enabled = effectiveOptions.enabled();

        if (enabled) {
            Duration ttl = effectiveOptions.ttl();
            this.cache = Caffeine.newBuilder()
                    .expireAfterWrite(ttl)
                    .maximumSize(effectiveOptions.maxSize())
                    .recordStats()
                    .build();
            log.info("Secret cache initialized (TTL: {}s, Max Size: {})",
                    ttl.toSeconds(), effectiveOptions.maxSize());
        } else {
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
     * @param key the cache key
     * @return the cached secret value, or empty if not found or cache disabled
     */
    public Optional<String> get(SecretCacheKey key) {
        if (!enabled || cache == null) {
            return Optional.empty();
        }

        String value = cache.getIfPresent(key);

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
    public void put(SecretCacheKey key, String value) {
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
    public void invalidate(SecretCacheKey key) {
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
        String normalizedVersion = version != null ? version : "null";
        String normalizedField = field != null ? field : "null";
        return secretKey + ":" + normalizedVersion + ":" + normalizedField;
    }

    /**
     * Removes all entries matching a secret key, version, and field regardless of provider selector.
     *
     * @param secretKey secret key to invalidate
     * @param version version to invalidate
     * @param field field to invalidate
     */
    public void invalidateMatching(String secretKey, String version, String field) {
        if (!enabled || cache == null) {
            return;
        }
        cache.asMap().keySet().removeIf(key -> key.matches(secretKey, version, field));
        log.debug("Invalidated cache entries for secret: key={}, version={}, field={}",
                secretKey, version, field);
    }

    /**
     * Compatibility lookup for callers using the old string cache key API.
     *
     * @param key legacy cache key
     * @return always empty for selector-aware cache entries
     * @deprecated use {@link #get(SecretCacheKey)}
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public Optional<String> get(String key) {
        return Optional.empty();
    }

    /**
     * Compatibility no-op for callers using the old string cache key API.
     *
     * @param key legacy cache key
     * @param value value to cache
     * @deprecated use {@link #put(SecretCacheKey, String)}
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public void put(String key, String value) {
        log.debug("Ignoring legacy cache put for key: {}", key);
    }

    /**
     * Compatibility invalidation for callers using the old string cache key API.
     *
     * @param key legacy cache key
     * @deprecated use {@link #invalidate(SecretCacheKey)}
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public void invalidate(String key) {
        log.debug("Ignoring legacy cache invalidation for key: {}", key);
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
                cache.estimatedSize(),
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate()
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
