package io.github.timhwang777.unisecret.exception;

/**
 * Base exception for all secret-related errors in the Universal Secret Manager.
 *
 * <h2>Exception Hierarchy</h2>
 * This is the root of the exception hierarchy for this library:
 * <pre>
 * SecretException (abstract base)
 * ├── SecretNotFoundException      - Secret doesn't exist in any provider
 * ├── SecretProviderException      - Communication failure with provider (may be retryable)
 * ├── SecretConfigurationException - Invalid configuration at startup
 * └── SecretParsingException       - JSON field extraction failure
 * </pre>
 *
 * <h2>Design Notes</h2>
 * <ul>
 *   <li>Extends {@link RuntimeException} so callers aren't forced to catch it (unchecked)</li>
 *   <li>Abstract to prevent direct instantiation - use specific subclasses</li>
 *   <li>All subclasses carry context about what went wrong (key, provider, etc.)</li>
 * </ul>
 *
 * @see SecretNotFoundException
 * @see SecretProviderException
 * @see SecretConfigurationException
 * @see SecretParsingException
 */
public abstract class SecretException extends RuntimeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message description of what went wrong
     */
    public SecretException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message description of what went wrong
     * @param cause   the underlying exception that caused this error
     */
    public SecretException(String message, Throwable cause) {
        super(message, cause);
    }
}
