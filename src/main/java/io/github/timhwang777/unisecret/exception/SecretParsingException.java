package io.github.timhwang777.unisecret.exception;

/**
 * Thrown when JSON field extraction fails during secret parsing.
 *
 * <h2>When This Is Thrown</h2>
 * This exception occurs when using the {@code field} attribute of @SecretValue
 * to extract a specific field from a JSON secret, and the extraction fails:
 *
 * <pre>{@code
 * // If the secret value is {"user": "admin", "password": "secret123"}
 * @SecretValue(value = "db-credentials", field = "password")
 * private String dbPassword;  // Works: extracts "secret123"
 *
 * @SecretValue(value = "db-credentials", field = "nonexistent")
 * private String missing;  // Throws SecretParsingException!
 * }</pre>
 *
 * <h2>Common Causes</h2>
 * <ul>
 *   <li>The specified field path doesn't exist in the JSON</li>
 *   <li>The secret value is not valid JSON</li>
 *   <li>The field path is correct but the value is null</li>
 *   <li>Typo in the field path (e.g., "pasword" instead of "password")</li>
 * </ul>
 *
 * <h2>Nested Paths</h2>
 * For nested JSON, use dot notation:
 * <pre>{@code
 * // Secret: {"database": {"connection": {"password": "secret"}}}
 * @SecretValue(value = "config", field = "database.connection.password")
 * private String password;
 * }</pre>
 *
 * @see io.github.timhwang777.unisecret.util.JsonFieldExtractor
 */
public class SecretParsingException extends SecretException {

    /**
     * Creates a new parsing exception.
     *
     * @param message description of what went wrong during parsing
     */
    public SecretParsingException(String message) {
        super(message);
    }

    /**
     * Creates a new parsing exception with a cause.
     *
     * @param message description of what went wrong during parsing
     * @param cause   the underlying JSON parsing exception
     */
    public SecretParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
