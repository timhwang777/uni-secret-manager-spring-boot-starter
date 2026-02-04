package io.github.timhwang777.unisecret.provider;

import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.util.Optional;

/**
 * Local secret provider for development and testing environments.
 *
 * <h2>Purpose</h2>
 * This provider allows developers to test their applications without connecting to
 * cloud secret managers (AWS, GCP). It reads secrets from the Spring Environment,
 * which includes:
 * <ul>
 *   <li>application.yml / application.properties</li>
 *   <li>Environment variables</li>
 *   <li>System properties</li>
 *   <li>Command-line arguments</li>
 * </ul>
 *
 * <h2>Secret Lookup Order</h2>
 * When looking for a secret, the provider checks these sources in order:
 * <ol>
 *   <li>Explicit secrets map: {@code secrets.local.secrets.my-secret}</li>
 *   <li>Prefixed property: {@code secrets.local.my-secret}</li>
 *   <li>Direct property: {@code my-secret}</li>
 * </ol>
 *
 * <h2>Configuration Examples</h2>
 * <pre>{@code
 * # Option 1: Using the secrets map (recommended for clarity)
 * secrets:
 *   local:
 *     enabled: true
 *     secrets:
 *       database-password: secret123
 *       api-key: abc-def-ghi
 *
 * # Option 2: Using prefixed properties
 * secrets:
 *   local:
 *     enabled: true
 *     database-password: secret123
 *
 * # Option 3: Direct property (least preferred - may conflict)
 * database-password: secret123
 * }</pre>
 *
 * <h2>Version Handling</h2>
 * The local provider ignores the version parameter since local secrets don't have
 * versions. All version values (including "latest", "previous", or specific IDs)
 * are effectively ignored.
 *
 * <h2>Recommended Setup for Local Development</h2>
 * <pre>{@code
 * # application-local.yml (for local development profile)
 * secrets:
 *   provider-order:
 *     - local       # Local provider first for development
 *   local:
 *     enabled: true
 *     secrets:
 *       database-password: dev-password
 *       api-key: dev-api-key
 * }</pre>
 *
 * @see io.github.timhwang777.unisecret.config.SecretManagerProperties.Local
 */
@Slf4j
public class LocalSecretProvider implements SecretProvider {

    /**
     * Spring Environment for property lookup.
     * Provides access to all property sources (YAML, properties, env vars, etc.)
     */
    private final Environment environment;

    /**
     * Local provider configuration including the explicit secrets map.
     */
    private final SecretManagerProperties.Local properties;

    public LocalSecretProvider(Environment environment, SecretManagerProperties.Local properties) {
        this.environment = environment;
        this.properties = properties;
    }

    /**
     * Retrieves a secret from local sources.
     *
     * @param key the secret key to look up
     * @return the secret value, or empty if not found
     */
    @Override
    public Optional<String> getSecret(String key) {
        // Version is ignored for local secrets - just delegate with dummy version
        return getSecret(key, "latest");
    }

    /**
     * Retrieves a secret from local sources.
     *
     * <p>The version parameter is ignored since local secrets don't support versioning.
     * This method tries multiple locations to find the secret value.</p>
     *
     * @param key     the secret key to look up
     * @param version ignored (local secrets don't have versions)
     * @return the secret value, or empty if not found in any location
     */
    @Override
    public Optional<String> getSecret(String key, String version) {
        log.debug("Retrieving secret '{}' from local provider", key);

        // LOOKUP 1: Check the explicit secrets map first
        // Config: secrets.local.secrets.{key}
        String value = properties.getSecrets().get(key);

        // LOOKUP 2: Check prefixed property in Spring Environment
        // This catches: secrets.local.{key} in any property source
        if (value == null) {
            value = environment.getProperty("secrets.local." + key);
        }

        // LOOKUP 3: Check direct property name (fallback for convenience)
        // This catches: {key} directly in any property source
        // Useful when migrating from simple property files
        if (value == null) {
            value = environment.getProperty(key);
        }

        // Return result
        if (value == null || value.isEmpty()) {
            log.debug("Secret '{}' not found in local provider", key);
            return Optional.empty();
        }

        log.debug("Successfully retrieved secret '{}' from local provider", key);
        return Optional.of(value);
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.LOCAL;
    }

    /**
     * Logs the number of explicitly configured secrets at startup.
     */
    @Override
    public void validateConfiguration() {
        int secretCount = properties.getSecrets().size();
        log.info("Local secret provider configuration validated ({} secrets configured)", secretCount);
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }
}
