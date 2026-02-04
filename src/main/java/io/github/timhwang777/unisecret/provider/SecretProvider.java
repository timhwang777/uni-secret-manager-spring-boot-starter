package io.github.timhwang777.unisecret.provider;

import java.util.Optional;

/**
 * Core interface for secret retrieval from different backends.
 *
 * <h2>Implementation Contract</h2>
 * All secret providers (AWS, GCP, Local) implement this interface. The key contract is:
 * <ul>
 *   <li><b>Not Found</b>: Return {@code Optional.empty()} - don't throw exceptions</li>
 *   <li><b>Error</b>: Throw {@link io.github.timhwang777.unisecret.exception.SecretProviderException}</li>
 *   <li><b>Success</b>: Return {@code Optional.of(secretValue)}</li>
 * </ul>
 *
 * <h2>Why Optional Instead of Exceptions?</h2>
 * The fallback chain pattern requires distinguishing between:
 * <ul>
 *   <li>"Secret doesn't exist here" → try next provider (Optional.empty)</li>
 *   <li>"Provider is broken" → fail or retry (SecretProviderException)</li>
 * </ul>
 *
 * <h2>Implementing a Custom Provider</h2>
 * <pre>{@code
 * public class VaultSecretProvider implements SecretProvider {
 *     @Override
 *     public Optional<String> getSecret(String key, String version) {
 *         try {
 *             String value = vault.read(key, version);
 *             return Optional.ofNullable(value);
 *         } catch (VaultNotFoundException e) {
 *             return Optional.empty();  // Secret doesn't exist - try next provider
 *         } catch (VaultException e) {
 *             throw new SecretProviderException("Vault error", e);  // Provider failure
 *         }
 *     }
 *     // ... other methods
 * }
 * }</pre>
 *
 * @see AwsSecretProvider
 * @see GcpSecretProvider
 * @see LocalSecretProvider
 * @see SecretResolver
 */
public interface SecretProvider {

    /**
     * Retrieves a secret by key, using the latest version.
     *
     * @param key the secret key
     * @return the secret value, or empty if not found
     * @throws io.github.timhwang777.unisecret.exception.SecretProviderException if provider communication fails
     */
    Optional<String> getSecret(String key);

    /**
     * Retrieves a specific version of a secret.
     *
     * @param key     the secret key
     * @param version the version identifier
     * @return the secret value, or empty if not found
     * @throws io.github.timhwang777.unisecret.exception.SecretProviderException if provider communication fails
     */
    Optional<String> getSecret(String key, String version);

    /**
     * Returns the provider type identifier.
     *
     * @return the provider type
     */
    ProviderType getProviderType();

    /**
     * Validates the provider configuration at startup.
     *
     * @throws io.github.timhwang777.unisecret.exception.SecretConfigurationException if configuration is invalid
     */
    void validateConfiguration();

    /**
     * Returns whether this provider is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
}
