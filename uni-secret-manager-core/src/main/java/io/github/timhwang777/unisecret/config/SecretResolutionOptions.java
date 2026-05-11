package io.github.timhwang777.unisecret.config;

import java.util.List;

/**
 * Spring-free resolver options used by the core module.
 *
 * @param providerOrder global provider fallback order
 * @param failOnMissing whether resolve methods throw when no value is found
 * @param retry retry options for provider calls
 */
public record SecretResolutionOptions(
        List<String> providerOrder,
        boolean failOnMissing,
        RetryOptions retry
) {

    /**
     * Creates default resolver options.
     *
     * @return resolver options matching the 2.0 defaults
     */
    public static SecretResolutionOptions defaults() {
        return new SecretResolutionOptions(
                List.of(),
                true,
                RetryOptions.defaults()
        );
    }

    /**
     * Normalizes nullable values to immutable defaults.
     */
    public SecretResolutionOptions {
        if (providerOrder == null) {
            providerOrder = List.of();
        } else {
            providerOrder = List.copyOf(providerOrder);
        }
        if (retry == null) {
            retry = RetryOptions.defaults();
        }
    }
}
