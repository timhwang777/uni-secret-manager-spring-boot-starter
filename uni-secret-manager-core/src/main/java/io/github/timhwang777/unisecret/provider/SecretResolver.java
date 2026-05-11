package io.github.timhwang777.unisecret.provider;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.cache.SecretCacheKey;
import io.github.timhwang777.unisecret.config.SecretResolutionOptions;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import io.github.timhwang777.unisecret.exception.SecretNotFoundException;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import io.github.timhwang777.unisecret.util.JsonFieldExtractor;
import io.github.timhwang777.unisecret.util.RetryHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central orchestrator for secret retrieval across multiple providers.
 */
@Slf4j
public class SecretResolver {

    private final Map<ProviderId, SecretProvider> providers;
    private final SecretResolutionOptions options;
    private final SecretCache cache;
    private final RetryHelper retryHelper;

    /**
     * Constructs the resolver with available providers.
     *
     * @param providerList providers available to this resolver; may be empty
     * @param options resolution options
     * @param cache cache instance
     */
    public SecretResolver(
            List<SecretProvider> providerList,
            SecretResolutionOptions options,
            SecretCache cache
    ) {
        List<SecretProvider> safeProviders = providerList == null ? List.of() : providerList;
        this.providers = toProviderMap(safeProviders);
        this.options = options == null ? SecretResolutionOptions.defaults() : options;
        this.cache = cache == null ? new SecretCache() : cache;
        this.retryHelper = new RetryHelper(this.options.retry());

        normalizeProviderIds(this.options.providerOrder());
        log.info("SecretResolver initialized with providers: {}", providers.keySet());
    }

    /**
     * Resolves a secret using the global provider order.
     *
     * @param key the secret key
     * @return the resolved value
     */
    public String resolve(String key) {
        return resolve(SecretReference.builder().key(key).build());
    }

    /**
     * Resolves a secret or throws according to {@code failOnMissing}.
     *
     * @param reference secret reference
     * @return resolved value, default value, or null when configured
     */
    public String resolve(SecretReference reference) {
        ResolutionResult result = resolveInternal(reference);
        if (result.value().isPresent()) {
            return result.value().get();
        }
        if (!options.failOnMissing()) {
            log.warn("Secret '{}' not found in any provider, returning null (fail-on-missing=false)",
                    reference.getKey());
            return null;
        }
        throw new SecretNotFoundException(reference.getKey(), result.attempts());
    }

    /**
     * Finds a secret without throwing when the value is absent.
     *
     * @param key secret key
     * @return resolved value or empty
     */
    public Optional<String> find(String key) {
        return find(SecretReference.builder().key(key).build());
    }

    /**
     * Finds a secret without throwing when the value is absent.
     *
     * @param reference secret reference
     * @return resolved value or empty
     */
    public Optional<String> find(SecretReference reference) {
        return resolveInternal(reference).value();
    }

    /**
     * Validates that provider names are known and currently configured.
     *
     * @param providerNames provider names from annotations or configuration
     */
    public void validateConfiguredProviders(List<String> providerNames) {
        for (ProviderId providerId : normalizeProviderIds(providerNames)) {
            if (!providers.containsKey(providerId)) {
                throw new SecretConfigurationException(
                        "Provider '" + providerId + "' is referenced but not configured. "
                                + "Enable it in application settings first."
                );
            }
        }
    }

    /**
     * Returns the effective provider chain for logging and diagnostics.
     *
     * @param reference secret reference, or null for global order
     * @return normalized provider chain
     */
    public List<String> getProviderChain(SecretReference reference) {
        if (reference == null) {
            return normalizeProviderNames(options.providerOrder());
        }
        return effectiveProviderChain(reference);
    }

    private ResolutionResult resolveInternal(SecretReference reference) {
        reference.validate();
        List<ProviderId> providerChain = effectiveProviderIds(reference);

        SecretCacheKey cacheKey = SecretCacheKey.from(reference, options.providerOrder());
        Optional<String> cachedValue = cache.get(cacheKey);
        if (cachedValue.isPresent()) {
            log.debug("Returning cached value for secret '{}'", reference.getKey());
            return ResolutionResult.found(cachedValue.get(), List.of());
        }

        List<SecretNotFoundException.ProviderAttempt> attempts = new ArrayList<>();
        log.debug("Resolving secret '{}' using provider chain: {}", reference.getKey(), providerChain);

        for (ProviderId providerId : providerChain) {
            SecretProvider provider = providers.get(providerId);
            if (provider == null) {
                throw new SecretConfigurationException(
                        "Provider '" + providerId + "' is referenced but not configured. "
                                + "Enable it in application settings first."
                );
            }
            if (!provider.isEnabled()) {
                attempts.add(new SecretNotFoundException.ProviderAttempt(
                        providerId,
                        "Provider not available or not enabled"
                ));
                continue;
            }

            Optional<String> resolved = resolveWithProvider(reference, provider, providerId, attempts);
            if (resolved.isPresent()) {
                cache.put(cacheKey, resolved.get());
                return ResolutionResult.found(resolved.get(), attempts);
            }
        }

        if (reference.getDefaultValue() != null && !reference.getDefaultValue().isEmpty()) {
            log.info("Secret '{}' not found in any provider, using default value", reference.getKey());
            return ResolutionResult.found(reference.getDefaultValue(), attempts);
        }

        return ResolutionResult.missing(attempts);
    }

    private Optional<String> resolveWithProvider(
            SecretReference reference,
            SecretProvider provider,
            ProviderId providerId,
            List<SecretNotFoundException.ProviderAttempt> attempts
    ) {
        try {
            Optional<String> secretValue = retryHelper.executeWithRetry(() ->
                    provider.getSecret(reference.getKey(), reference.getVersion())
            );
            if (secretValue.isEmpty()) {
                attempts.add(new SecretNotFoundException.ProviderAttempt(provider.getProviderId(), null));
                return Optional.empty();
            }

            String value = secretValue.get();
            if (reference.getField() != null && !reference.getField().isEmpty()) {
                value = JsonFieldExtractor.extractField(value, reference.getField());
            }

            log.info("Secret '{}' successfully retrieved from provider '{}'",
                    reference.getKey(), providerId);
            return Optional.of(value);
        } catch (SecretProviderException e) {
            log.warn("Provider '{}' failed for secret '{}': {}",
                    providerId, reference.getKey(), e.getMessage());
            attempts.add(new SecretNotFoundException.ProviderAttempt(
                    provider.getProviderId(),
                    e.getMessage()
            ));
            return Optional.empty();
        }
    }

    private List<String> effectiveProviderChain(SecretReference reference) {
        return normalizeProviderNames(reference.getEffectiveProviderChain(options.providerOrder()));
    }

    private List<ProviderId> effectiveProviderIds(SecretReference reference) {
        return normalizeProviderIds(reference.getEffectiveProviderChain(options.providerOrder()));
    }

    private List<String> normalizeProviderNames(List<String> providerNames) {
        return normalizeProviderIds(providerNames).stream()
                .map(ProviderId::value)
                .toList();
    }

    private List<ProviderId> normalizeProviderIds(List<String> providerNames) {
        if (providerNames == null || providerNames.isEmpty()) {
            return Collections.emptyList();
        }
        return providerNames.stream()
                .map(this::normalizeProviderId)
                .toList();
    }

    private ProviderId normalizeProviderId(String providerName) {
        try {
            return ProviderId.of(providerName);
        } catch (IllegalArgumentException e) {
            throw new SecretConfigurationException(
                    "Invalid provider id '" + providerName + "' in provider configuration",
                    e
            );
        }
    }

    private Map<ProviderId, SecretProvider> toProviderMap(List<SecretProvider> providerList) {
        Map<ProviderId, SecretProvider> mappedProviders = new LinkedHashMap<>();
        for (SecretProvider provider : providerList) {
            ProviderId providerId = provider.getProviderId();
            if (providerId == null) {
                throw new SecretConfigurationException("Provider id must not be null");
            }
            SecretProvider previous = mappedProviders.putIfAbsent(providerId, provider);
            if (previous != null) {
                throw new SecretConfigurationException(
                        "Duplicate provider id '" + providerId + "' after normalization");
            }
        }
        return Collections.unmodifiableMap(mappedProviders);
    }

    private record ResolutionResult(
            Optional<String> value,
            List<SecretNotFoundException.ProviderAttempt> attempts
    ) {
        private static ResolutionResult found(
                String value,
                List<SecretNotFoundException.ProviderAttempt> attempts
        ) {
            return new ResolutionResult(Optional.of(value), List.copyOf(attempts));
        }

        private static ResolutionResult missing(List<SecretNotFoundException.ProviderAttempt> attempts) {
            return new ResolutionResult(Optional.empty(), List.copyOf(attempts));
        }
    }
}
