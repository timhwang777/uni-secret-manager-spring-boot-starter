package io.github.timhwang777.unisecret.cache;

import io.github.timhwang777.unisecret.provider.ProviderId;
import io.github.timhwang777.unisecret.provider.SecretReference;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Cache key that includes the provider selection mode as well as secret identity.
 *
 * @param secretKey provider-specific secret name
 * @param version requested secret version
 * @param field requested JSON field
 * @param providerSelection explicit provider, custom chain, or global chain snapshot
 */
public record SecretCacheKey(
        String secretKey,
        String version,
        String field,
        String providerSelection
) {

    /**
     * Creates a cache key for the supplied reference.
     *
     * @param reference secret reference
     * @param globalProviderOrder current global provider order
     * @return cache key with provider selection included
     */
    public static SecretCacheKey from(SecretReference reference, List<String> globalProviderOrder) {
        return new SecretCacheKey(
                reference.getKey(),
                reference.getVersion(),
                reference.getField(),
                providerSelection(reference, globalProviderOrder)
        );
    }

    private static String providerSelection(SecretReference reference, List<String> globalProviderOrder) {
        if (reference.getProvider() != null && !reference.getProvider().isBlank()) {
            return "provider:" + normalize(reference.getProvider());
        }
        if (reference.getProviders() != null && !reference.getProviders().isEmpty()) {
            return "chain:" + normalize(reference.getProviders());
        }
        return "global:" + normalize(globalProviderOrder);
    }

    private static String normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .map(SecretCacheKey::normalize)
                .collect(Collectors.joining(","));
    }

    private static String normalize(String value) {
        return ProviderId.of(value).value();
    }

    /**
     * Returns whether this key targets the supplied secret identity.
     *
     * @param key secret key
     * @param requestedVersion version to match
     * @param requestedField field to match
     * @return true when secret key, version, and field match
     */
    public boolean matches(String key, String requestedVersion, String requestedField) {
        return secretKey.equals(key)
                && normalized(version).equals(normalized(requestedVersion))
                && normalized(field).equals(normalized(requestedField));
    }

    private static String normalized(String value) {
        return value == null ? "" : value;
    }
}
