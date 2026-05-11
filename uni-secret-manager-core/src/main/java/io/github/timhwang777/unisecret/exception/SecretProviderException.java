package io.github.timhwang777.unisecret.exception;

import lombok.Getter;

/**
 * Thrown when a provider fails to communicate with its backend service.
 *
 * <h2>Retryable vs Non-Retryable Errors</h2>
 * This exception has a {@link #retryable} flag that indicates whether the
 * operation should be retried:
 *
 * <ul>
 *   <li><b>Retryable (retryable=true)</b>: Transient failures that may succeed on retry
 *       <ul>
 *         <li>Network timeouts</li>
 *         <li>Service temporarily unavailable (503)</li>
 *         <li>Rate limiting (429)</li>
 *         <li>AWS InternalServiceError</li>
 *       </ul>
 *   </li>
 *   <li><b>Non-retryable (retryable=false)</b>: Permanent failures that won't succeed on retry
 *       <ul>
 *         <li>Access denied / permission errors</li>
 *         <li>Decryption failures (bad KMS key)</li>
 *         <li>Invalid credentials</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>How Retries Work</h2>
 * The {@link io.github.timhwang777.unisecret.util.RetryHelper} checks this flag
 * to decide whether to retry:
 * <pre>{@code
 * catch (SecretProviderException e) {
 *     if (e.isRetryable() && attempt < maxAttempts) {
 *         // Wait with exponential backoff and retry
 *     } else {
 *         throw e;  // Give up
 *     }
 * }
 * }</pre>
 *
 * @see io.github.timhwang777.unisecret.util.RetryHelper
 */
@Getter
public class SecretProviderException extends SecretException {

    /**
     * Whether this error might succeed if retried.
     * True for transient errors (timeouts, rate limits), false for permanent errors (auth failures).
     */
    private final boolean retryable;

    /**
     * Creates a retryable exception with the given message.
     * Use this for transient failures like network issues.
     *
     * @param message description of what went wrong
     */
    public SecretProviderException(String message) {
        this(message, null, true);  // Default: assume retryable
    }

    /**
     * Creates a retryable exception with the given message and cause.
     * Use this for transient failures like network issues.
     *
     * @param message description of what went wrong
     * @param cause   the underlying exception
     */
    public SecretProviderException(String message, Throwable cause) {
        this(message, cause, true);  // Default: assume retryable
    }

    /**
     * Creates an exception with explicit retryable flag.
     * Use this when you know whether the error is transient or permanent.
     *
     * @param message   description of what went wrong
     * @param cause     the underlying exception (can be null)
     * @param retryable true if this error might succeed on retry, false for permanent errors
     */
    public SecretProviderException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }
}
