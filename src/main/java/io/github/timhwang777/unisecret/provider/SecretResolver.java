package io.github.timhwang777.unisecret.provider;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import io.github.timhwang777.unisecret.exception.SecretNotFoundException;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import io.github.timhwang777.unisecret.util.JsonFieldExtractor;
import io.github.timhwang777.unisecret.util.RetryHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Central orchestrator for secret retrieval across multiple providers.
 *
 * <h2>Core Responsibilities</h2>
 * <ol>
 *   <li><b>Provider Chain</b>: Tries providers in configured order until secret is found</li>
 *   <li><b>Caching</b>: Checks cache first, stores retrieved secrets to reduce provider calls</li>
 *   <li><b>Retry Logic</b>: Wraps provider calls with exponential backoff for transient failures</li>
 *   <li><b>JSON Extraction</b>: Extracts specific fields from JSON secrets if requested</li>
 *   <li><b>Fallback</b>: Returns default value if secret not found and default is configured</li>
 * </ol>
 *
 * <h2>Resolution Flow</h2>
 * <pre>
 *                    ┌─────────────────┐
 *                    │  resolve(ref)   │
 *                    └────────┬────────┘
 *                             │
 *                    ┌────────▼────────┐
 *                    │  Check Cache    │──── Hit ────► Return cached value
 *                    └────────┬────────┘
 *                             │ Miss
 *              ┌──────────────▼──────────────┐
 *              │  For each provider in chain │
 *              └──────────────┬──────────────┘
 *                             │
 *              ┌──────────────▼──────────────┐
 *              │  Try provider (with retry)  │
 *              └──────────────┬──────────────┘
 *                             │
 *         ┌───────────────────┼───────────────────┐
 *         │                   │                   │
 *    ┌────▼────┐        ┌─────▼─────┐      ┌──────▼──────┐
 *    │  Found  │        │ Not Found │      │    Error    │
 *    └────┬────┘        └─────┬─────┘      └──────┬──────┘
 *         │                   │                   │
 *   Extract JSON      Try next provider    Log and continue
 *   Cache &amp; return
 * </pre>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Simple resolution with global provider chain
 * String password = resolver.resolve("database-password");
 *
 * // Resolution with specific reference
 * SecretReference ref = SecretReference.builder()
 *     .key("config")
 *     .field("database.password")
 *     .provider("aws")
 *     .build();
 * String password = resolver.resolve(ref);
 * }</pre>
 *
 * @see SecretReference
 * @see SecretProvider
 * @see SecretCache
 */
@Slf4j
@Service
public class SecretResolver {

    /**
     * Map of provider type to provider instance for O(1) lookup.
     * Populated from Spring-injected list of SecretProvider beans.
     */
    private final Map<ProviderType, SecretProvider> providers;

    /**
     * Configuration properties including provider order, cache settings, retry config.
     */
    private final SecretManagerProperties properties;

    /**
     * Cache for storing resolved secrets to avoid repeated provider calls.
     */
    private final SecretCache cache;

    /**
     * Helper for executing provider calls with exponential backoff retry.
     */
    private final RetryHelper retryHelper;

    /**
     * Constructs the resolver with all available providers.
     *
     * <p>This constructor performs important validation at startup:</p>
     * <ul>
     *   <li>Ensures at least one provider is configured</li>
     *   <li>Validates the provider order configuration</li>
     *   <li>Normalizes provider names to lowercase for case-insensitive matching</li>
     *   <li>Ensures at least one provider is enabled</li>
     * </ul>
     *
     * <p>If validation fails, the application will fail to start with a clear error message.</p>
     *
     * @param providerList all SecretProvider beans from Spring context
     * @param properties   configuration properties
     * @param cache        the secret cache instance
     * @throws SecretConfigurationException if configuration is invalid
     */
    public SecretResolver(List<SecretProvider> providerList, SecretManagerProperties properties, SecretCache cache) {
        this.cache = cache;

        // VALIDATION 1: At least one provider must be configured
        if (providerList == null || providerList.isEmpty()) {
            throw new SecretConfigurationException("No secret providers are configured");
        }

        // Convert list to map for efficient lookup by provider type
        // Example: [AwsSecretProvider, LocalSecretProvider] → {AWS: awsProvider, LOCAL: localProvider}
        this.providers = providerList.stream()
                .collect(Collectors.toMap(SecretProvider::getProviderType, p -> p));
        this.properties = properties;
        this.retryHelper = new RetryHelper(properties.getRetry());

        // VALIDATION 2: Provider order must be configured
        validateProviderOrderConfiguration(properties);

        // NORMALIZATION: Convert provider order to lowercase for case-insensitive matching
        // This allows users to write "AWS", "aws", or "Aws" in config
        normalizeProviderOrder(properties);

        // VALIDATION 3: At least one provider must be enabled
        validateAtLeastOneProviderEnabled(providerList);

        log.info("SecretResolver initialized with providers: {}", providers.keySet());
    }

    /**
     * Validates that the provider order is properly configured.
     *
     * <p>The provider order defines the fallback sequence for secret retrieval.
     * Without it, the resolver doesn't know which provider to try first.</p>
     *
     * @param properties the configuration to validate
     * @throws SecretConfigurationException if provider order is null or empty
     */
    private void validateProviderOrderConfiguration(SecretManagerProperties properties) {
        List<String> providerOrder = properties.getProviderOrder();
        if (providerOrder == null) {
            throw new SecretConfigurationException("Provider order must not be null");
        }
        if (providerOrder.isEmpty()) {
            throw new SecretConfigurationException("Provider order must not be empty");
        }
    }

    /**
     * Normalizes provider names in the order to lowercase for case-insensitive matching.
     *
     * <p>This allows users to write provider names in any case in their configuration:</p>
     * <pre>{@code
     * secrets:
     *   provider-order:
     *     - AWS    # Works
     *     - Aws    # Works
     *     - aws    # Works (all become "aws")
     * }</pre>
     *
     * @param properties the configuration to normalize
     */
    private void normalizeProviderOrder(SecretManagerProperties properties) {
        List<String> providerOrder = properties.getProviderOrder();
        List<String> normalized = providerOrder.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        properties.setProviderOrder(normalized);
    }

    /**
     * Validates that at least one secret provider is enabled.
     *
     * <p>Having providers configured but none enabled is likely a configuration error.
     * This check helps catch such mistakes at startup rather than at runtime.</p>
     *
     * @param providerList the list of configured providers
     * @throws SecretConfigurationException if no providers are enabled
     */
    private void validateAtLeastOneProviderEnabled(List<SecretProvider> providerList) {
        boolean anyEnabled = providerList.stream()
                .anyMatch(SecretProvider::isEnabled);

        if (!anyEnabled) {
            throw new SecretConfigurationException(
                    "No secret providers are enabled. At least one provider must be enabled in configuration."
            );
        }
    }

    /**
     * Resolves a secret using the global provider order.
     *
     * <p>Convenience method that creates a simple SecretReference with just the key.</p>
     *
     * @param key the secret key/name in the provider
     * @return the secret value as a string
     * @throws SecretNotFoundException if secret not found in any provider and no default configured
     */
    public String resolve(String key) {
        // Build a minimal reference with just the key
        SecretReference reference = SecretReference.builder()
                .key(key)
                .build();
        return resolve(reference);
    }

    /**
     * Resolves a secret using the full configuration from a SecretReference.
     *
     * <p>This is the main resolution method that implements the full fallback logic:</p>
     * <ol>
     *   <li>Check cache for previously resolved value</li>
     *   <li>Try each provider in the chain until one has the secret</li>
     *   <li>Extract JSON field if specified</li>
     *   <li>Cache the result for future lookups</li>
     *   <li>Return default value if configured and secret not found</li>
     *   <li>Throw SecretNotFoundException if all providers exhausted</li>
     * </ol>
     *
     * @param reference the secret reference containing key, version, field, provider chain, etc.
     * @return the secret value (or extracted JSON field value) as a string
     * @throws SecretNotFoundException if secret not found in any provider and no default configured
     */
    public String resolve(SecretReference reference) {
        // Validate the reference before attempting resolution
        reference.validate();

        // STEP 1: Build a unique cache key that includes version and field
        // Format: "{key}:{version}:{field}" (e.g., "db-config:latest:password")
        String cacheKey = cache.buildKey(
                reference.getKey(),
                reference.getVersion(),
                reference.getField()
        );

        // STEP 2: Check cache first (cache-aside pattern)
        Optional<String> cachedValue = cache.get(cacheKey);
        if (cachedValue.isPresent()) {
            log.debug("Returning cached value for secret '{}'", reference.getKey());
            return cachedValue.get();
        }

        // STEP 3: Determine which providers to try and in what order
        List<String> providerChain = reference.getEffectiveProviderChain(properties.getProviderOrder());

        // Track attempts for debugging (included in exception if all fail)
        List<SecretNotFoundException.ProviderAttempt> attempts = new ArrayList<>();

        log.debug("Resolving secret '{}' using provider chain: {}", reference.getKey(), providerChain);

        // STEP 4: Try each provider in order until one succeeds
        for (String providerName : providerChain) {
            try {
                // Convert string name to enum for map lookup
                ProviderType providerType = ProviderType.fromConfigValue(providerName);
                SecretProvider provider = providers.get(providerType);

                // Skip providers that aren't available or are disabled
                if (provider == null || !provider.isEnabled()) {
                    log.debug("Provider '{}' not available or not enabled, skipping", providerName);
                    attempts.add(new SecretNotFoundException.ProviderAttempt(
                            providerType,
                            "Provider not available or not enabled"
                    ));
                    continue;  // Try next provider
                }

                // Execute provider call with automatic retry for transient failures
                Optional<String> secretValue = retryHelper.executeWithRetry(() ->
                        provider.getSecret(reference.getKey(), reference.getVersion())
                );

                if (secretValue.isPresent()) {
                    String value = secretValue.get();

                    // STEP 5: Extract JSON field if specified (e.g., "password" from {"password":"secret"})
                    if (reference.getField() != null && !reference.getField().isEmpty()) {
                        value = JsonFieldExtractor.extractField(value, reference.getField());
                    }

                    // STEP 6: Cache the resolved value for future lookups
                    cache.put(cacheKey, value);

                    log.info("Secret '{}' successfully retrieved from provider '{}'",
                            reference.getKey(), providerName);
                    return value;
                } else {
                    // Secret not found in this provider - record and try next
                    log.debug("Secret '{}' not found in provider '{}'", reference.getKey(), providerName);
                    attempts.add(new SecretNotFoundException.ProviderAttempt(providerType, null));
                }
            } catch (SecretProviderException e) {
                // Provider failed (network error, permission issue, etc.)
                // Record the error and try next provider
                log.warn("Provider '{}' failed for secret '{}': {}",
                        providerName, reference.getKey(), e.getMessage());
                attempts.add(new SecretNotFoundException.ProviderAttempt(
                        ProviderType.fromConfigValue(providerName),
                        e.getMessage()
                ));
            }
        }

        // STEP 7: All providers exhausted - check for default value
        if (reference.getDefaultValue() != null && !reference.getDefaultValue().isEmpty()) {
            String defaultValue = reference.getDefaultValue();
            // Cache the default value to avoid re-checking providers on future calls
            cache.put(cacheKey, defaultValue);
            log.info("Secret '{}' not found in any provider, using default value", reference.getKey());
            return defaultValue;
        }

        // STEP 8: No secret found and no default
        if (!properties.isFailOnMissing()) {
            log.warn("Secret '{}' not found in any provider, returning null (fail-on-missing=false)",
                    reference.getKey());
            return null;
        }

        // fail-on-missing is true - throw with full attempt history
        log.error("Secret '{}' not found in any provider after {} attempts",
                reference.getKey(), attempts.size());
        throw new SecretNotFoundException(reference.getKey(), attempts);
    }

    /**
     * Returns the effective provider chain for a reference.
     *
     * <p>Used primarily for logging/auditing to show which providers were/will be tried.</p>
     *
     * @param reference the secret reference (can be null for global chain)
     * @return the list of provider names that would be tried, in order
     */
    public List<String> getProviderChain(SecretReference reference) {
        if (reference == null) {
            // No reference specified - return the global default order
            return properties.getProviderOrder();
        }
        // Delegate to reference to determine effective chain
        return reference.getEffectiveProviderChain(properties.getProviderOrder());
    }
}
