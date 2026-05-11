package io.github.timhwang777.unisecret.service;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.cache.SecretCacheKey;
import io.github.timhwang777.unisecret.provider.SecretReference;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for refreshing cached secrets at runtime without application restart.
 *
 * <h2>Purpose</h2>
 * When secrets are rotated in the cloud provider, the application may still have
 * the old values cached. This service allows you to:
 * <ul>
 *   <li>Force refresh of a specific secret</li>
 *   <li>Clear the entire cache after bulk rotation</li>
 *   <li>Monitor refresh activity via statistics</li>
 * </ul>
 *
 * <h2>How Refresh Works</h2>
 * Refresh doesn't immediately fetch new values. Instead, it invalidates cached entries,
 * so the next access to that secret triggers a fresh fetch from the provider:
 * <pre>
 * 1. refresh("my-secret")  → Removes "my-secret" from cache
 * 2. Next @SecretValue access → Cache miss → Fetches fresh value from provider
 * </pre>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * @Autowired
 * private SecretRefreshService refreshService;
 *
 * // Refresh a single secret
 * refreshService.refresh("database-password");
 *
 * // Refresh a specific version and field
 * refreshService.refresh("config", "latest", "api-key");
 *
 * // Refresh all secrets (after bulk rotation)
 * refreshService.refreshAll();
 *
 * // Check refresh statistics
 * RefreshStats stats = refreshService.getRefreshStats();
 * System.out.println("Refreshes: " + stats.singleRefreshCount());
 * }</pre>
 *
 * <h2>Integration with Secret Rotation</h2>
 * For automated refresh after rotation, consider:
 * <ul>
 *   <li>AWS: CloudWatch Events → Lambda → call refresh endpoint</li>
 *   <li>GCP: Pub/Sub notification → Cloud Function → call refresh endpoint</li>
 *   <li>Kubernetes: ExternalSecret operator with refresh annotation</li>
 * </ul>
 *
 * @see SecretCache
 */
@Slf4j
public class SecretRefreshService {

    /**
     * The secret cache to invalidate on refresh.
     */
    private final SecretCache cache;

    // ========== Statistics Tracking ==========
    // Using AtomicInteger/AtomicReference for thread-safe counters

    /**
     * Count of individual secret refreshes (via refresh() methods).
     */
    private final AtomicInteger singleRefreshCount = new AtomicInteger(0);

    /**
     * Count of full cache refreshes (via refreshAll()).
     */
    private final AtomicInteger fullRefreshCount = new AtomicInteger(0);

    /**
     * Timestamp of the most recent refresh operation.
     */
    private final AtomicReference<Instant> lastRefreshTime = new AtomicReference<>();

    public SecretRefreshService(SecretCache cache) {
        this.cache = cache;
        log.info("SecretRefreshService initialized");
    }

    /**
     * Refreshes a single secret using the latest version.
     *
     * <p>Invalidates the cached value for this secret, so the next access
     * will fetch a fresh value from the provider.</p>
     *
     * @param secretKey the secret key/name to refresh
     */
    public void refresh(String secretKey) {
        refresh(secretKey, "latest", null);
    }

    /**
     * Refreshes a specific version of a secret.
     *
     * @param secretKey the secret key/name to refresh
     * @param version   the version to refresh (e.g., "latest", "previous", "5")
     */
    public void refresh(String secretKey, String version) {
        refresh(secretKey, version, null);
    }

    /**
     * Refreshes a specific field extraction from a secret.
     *
     * <p>Use this when you're using JSON field extraction and only want to
     * refresh a specific field's cached value.</p>
     *
     * @param secretKey the secret key/name to refresh
     * @param version   the version to refresh (e.g., "latest")
     * @param field     the JSON field path (e.g., "database.password"), or null for entire secret
     */
    public void refresh(String secretKey, String version, String field) {
        cache.invalidateMatching(secretKey, version, field);

        singleRefreshCount.incrementAndGet();
        lastRefreshTime.set(Instant.now());

        log.info("Refreshed secret: key={}, version={}, field={}", secretKey, version, field);
    }

    /**
     * Refreshes a specific reference using the same provider-aware key shape used by the resolver.
     *
     * @param reference secret reference to invalidate
     * @param globalProviderOrder current global provider order
     */
    public void refresh(SecretReference reference, List<String> globalProviderOrder) {
        reference.validate();
        SecretCacheKey cacheKey = SecretCacheKey.from(reference, globalProviderOrder);
        cache.invalidate(cacheKey);

        singleRefreshCount.incrementAndGet();
        lastRefreshTime.set(Instant.now());

        log.info("Refreshed secret: key={}, version={}, field={}",
                reference.getKey(), reference.getVersion(), reference.getField());
    }

    /**
     * Refreshes all cached secrets.
     *
     * <p>Use this after a bulk secret rotation to ensure all cached values
     * are invalidated. The next access to any secret will fetch fresh values.</p>
     *
     * <p><b>Warning:</b> This may cause a temporary increase in provider API calls
     * as all secrets are re-fetched on next access.</p>
     */
    public void refreshAll() {
        cache.invalidateAll();

        fullRefreshCount.incrementAndGet();
        lastRefreshTime.set(Instant.now());

        log.info("Refreshed all cached secrets");
    }

    /**
     * Returns refresh statistics for monitoring.
     *
     * <p>Useful for dashboards and alerting on secret rotation activity.</p>
     *
     * @return immutable snapshot of refresh statistics
     */
    public RefreshStats getRefreshStats() {
        return new RefreshStats(
                singleRefreshCount.get(),
                fullRefreshCount.get(),
                lastRefreshTime.get()
        );
    }

    /**
     * Resets all refresh statistics to zero.
     *
     * <p>Useful for testing or after a monitoring period reset.</p>
     */
    public void resetStats() {
        singleRefreshCount.set(0);
        fullRefreshCount.set(0);
        lastRefreshTime.set(null);
        log.debug("Reset refresh statistics");
    }

    /**
     * Immutable snapshot of refresh activity statistics.
     *
     * @param singleRefreshCount number of individual secret refreshes
     * @param fullRefreshCount   number of full cache clears
     * @param lastRefreshTime    timestamp of most recent refresh operation (null if never refreshed)
     */
    public record RefreshStats(
            int singleRefreshCount,
            int fullRefreshCount,
            Instant lastRefreshTime
    ) {
    }
}
