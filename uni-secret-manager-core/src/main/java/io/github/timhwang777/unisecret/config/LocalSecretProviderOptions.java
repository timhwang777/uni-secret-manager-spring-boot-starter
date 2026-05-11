package io.github.timhwang777.unisecret.config;

import java.util.Map;

/**
 * Spring-free local provider options.
 *
 * @param enabled whether the provider participates in resolution
 * @param secrets explicit local secret map
 */
public record LocalSecretProviderOptions(
        boolean enabled,
        Map<String, String> secrets
) {

    /**
     * Disabled default local provider options.
     *
     * @return disabled local provider options
     */
    public static LocalSecretProviderOptions disabled() {
        return new LocalSecretProviderOptions(false, Map.of());
    }

    /**
     * Normalizes the explicit secret map.
     */
    public LocalSecretProviderOptions {
        if (secrets == null) {
            secrets = Map.of();
        } else {
            secrets = Map.copyOf(secrets);
        }
    }

    @Override
    public String toString() {
        return "LocalSecretProviderOptions[enabled="
                + enabled
                + ", secrets=<redacted:"
                + secrets.size()
                + "]";
    }
}
