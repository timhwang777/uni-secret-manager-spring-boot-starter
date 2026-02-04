package io.github.timhwang777.unisecret.exception;

import io.github.timhwang777.unisecret.provider.ProviderType;
import lombok.Getter;

import java.util.List;

/**
 * Thrown when a secret cannot be found in any provider in the fallback chain.
 *
 * <h2>When This Is Thrown</h2>
 * This exception is thrown by {@link io.github.timhwang777.unisecret.provider.SecretResolver}
 * when:
 * <ol>
 *   <li>All providers in the chain have been tried</li>
 *   <li>None of them had the secret</li>
 *   <li>No default value was configured</li>
 * </ol>
 *
 * <h2>Debugging with Attempts</h2>
 * The {@link #attempts} list provides a detailed log of what happened with each provider,
 * making it easy to diagnose issues:
 * <pre>{@code
 * try {
 *     resolver.resolve("my-secret");
 * } catch (SecretNotFoundException e) {
 *     for (ProviderAttempt attempt : e.getAttempts()) {
 *         System.out.println("Provider: " + attempt.provider());
 *         System.out.println("Error: " + attempt.errorMessage());  // null if just not found
 *     }
 * }
 * }</pre>
 *
 * <h2>How to Avoid This Exception</h2>
 * <ul>
 *   <li>Ensure the secret exists in at least one enabled provider</li>
 *   <li>Use {@code defaultValue} in @SecretValue annotation for optional secrets</li>
 *   <li>Check that the correct providers are enabled in configuration</li>
 * </ul>
 */
@Getter
public class SecretNotFoundException extends SecretException {

    /**
     * The secret key that was not found.
     */
    private final String key;

    /**
     * A record of each provider that was tried and what happened.
     * Useful for debugging why the secret wasn't found.
     */
    private final List<ProviderAttempt> attempts;

    /**
     * Creates a new exception for a secret that wasn't found.
     *
     * @param key      the secret key that was searched for
     * @param attempts the list of providers tried and their results
     */
    public SecretNotFoundException(String key, List<ProviderAttempt> attempts) {
        super(buildMessage(key, attempts));
        this.key = key;
        this.attempts = attempts;
    }

    /**
     * Builds a human-readable message listing all attempted providers.
     * Example: "Secret 'db-pass' not found. Attempted: AWS (not found), GCP (permission denied)"
     */
    private static String buildMessage(String key, List<ProviderAttempt> attempts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Secret '").append(key).append("' not found in any provider. Attempted providers: ");

        // List each provider and its result
        for (int i = 0; i < attempts.size(); i++) {
            ProviderAttempt attempt = attempts.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(attempt.provider());
            // Include error message if there was one (vs just "not found")
            if (attempt.errorMessage() != null) {
                sb.append(" (").append(attempt.errorMessage()).append(")");
            }
        }

        return sb.toString();
    }

    /**
     * Records a single attempt to retrieve a secret from a provider.
     *
     * <p>This record captures what happened when trying to fetch a secret from
     * a specific provider, enabling detailed debugging of fallback behavior.</p>
     *
     * @param provider     the type of provider that was attempted (AWS, GCP, LOCAL)
     * @param errorMessage if null, the secret simply wasn't found;
     *                     if non-null, an error occurred (e.g., "permission denied", "timeout")
     */
    public record ProviderAttempt(
            ProviderType provider,
            String errorMessage
    ) {
    }
}
