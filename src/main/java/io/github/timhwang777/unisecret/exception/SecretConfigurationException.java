package io.github.timhwang777.unisecret.exception;

/**
 * Thrown when the secret manager configuration is invalid at application startup.
 *
 * <h2>Fail-Fast Behavior</h2>
 * This exception is thrown during Spring context initialization, causing the
 * application to fail fast rather than starting with broken secret management.
 * This is intentional - it's better to fail immediately with a clear error than
 * to discover missing secrets at runtime.
 *
 * <h2>Common Causes</h2>
 * <ul>
 *   <li>No providers enabled in configuration</li>
 *   <li>Empty or null provider order list</li>
 *   <li>Missing required configuration (e.g., GCP project ID when GCP is enabled)</li>
 *   <li>Invalid provider names in the provider order</li>
 * </ul>
 *
 * <h2>How to Fix</h2>
 * Check your application.yml/properties:
 * <pre>{@code
 * secrets:
 *   enabled: true
 *   provider-order:
 *     - aws
 *     - local
 *   aws:
 *     enabled: true
 *     region: us-east-1
 *   local:
 *     enabled: true
 * }</pre>
 *
 * @see io.github.timhwang777.unisecret.provider.SecretResolver
 * @see io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration
 */
public class SecretConfigurationException extends SecretException {

    /**
     * Creates a new configuration exception.
     *
     * @param message description of what's wrong with the configuration
     */
    public SecretConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates a new configuration exception with a cause.
     *
     * @param message description of what's wrong with the configuration
     * @param cause   the underlying exception
     */
    public SecretConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
