package io.github.timhwang777.unisecret.provider;

import io.github.timhwang777.unisecret.config.LocalSecretProviderOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Local secret provider for development and testing environments.
 *
 * <p>This provider is intentionally Spring-free. It reads only from the explicit
 * local secret map supplied through {@link LocalSecretProviderOptions}.</p>
 */
@Slf4j
public class LocalSecretProvider implements SecretProvider {

    private final LocalSecretProviderOptions options;

    public LocalSecretProvider(LocalSecretProviderOptions options) {
        this.options = options == null ? LocalSecretProviderOptions.disabled() : options;
    }

    /**
     * Retrieves a secret from local sources.
     *
     * @param key the secret key to look up
     * @return the secret value, or empty if not found
     */
    @Override
    public Optional<String> getSecret(String key) {
        return getSecret(key, "latest");
    }

    /**
     * Retrieves a secret from the configured local map.
     *
     * @param key the secret key to look up
     * @param version ignored for local secrets
     * @return the secret value, or empty if not found
     */
    @Override
    public Optional<String> getSecret(String key, String version) {
        log.debug("Retrieving secret '{}' from local provider", key);

        String value = options.secrets().get(key);
        if (value == null || value.isEmpty()) {
            log.debug("Secret '{}' not found in local provider", key);
            return Optional.empty();
        }

        log.debug("Successfully retrieved secret '{}' from local provider", key);
        return Optional.of(value);
    }

    @Override
    public ProviderId getProviderId() {
        return ProviderId.LOCAL;
    }

    @Override
    public void validateConfiguration() {
        log.info("Local secret provider configuration validated ({} secrets configured)",
                options.secrets().size());
    }

    @Override
    public boolean isEnabled() {
        return options.enabled();
    }
}
