package io.github.timhwang777.unisecret.provider;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Immutable value object representing a secret reference parsed from @SecretValue annotation.
 *
 * <h2>Purpose</h2>
 * This class bridges the annotation attributes to the resolution logic. The
 * {@link io.github.timhwang777.unisecret.processor.SecretValueBeanPostProcessor}
 * creates a SecretReference from each @SecretValue annotation, and the
 * {@link SecretResolver} uses it to fetch the actual secret value.
 *
 * <h2>Provider Chain Priority</h2>
 * The effective provider chain is determined in this order:
 * <ol>
 *   <li><b>Single provider</b>: If {@code provider} is set, use only that provider</li>
 *   <li><b>Custom chain</b>: If {@code providers} is set, use that custom list</li>
 *   <li><b>Global default</b>: Otherwise, use the global {@code secrets.provider-order}</li>
 * </ol>
 *
 * <h2>Example Mappings</h2>
 * <pre>{@code
 * // Annotation                                    → SecretReference fields
 * @SecretValue("my-secret")                        → key="my-secret", all else defaults
 * @SecretValue(value="config", field="password")  → key="config", field="password"
 * @SecretValue(value="key", provider="aws")       → key="key", provider="aws"
 * @SecretValue(value="key", providers={"local","aws"}) → key="key", providers=["local","aws"]
 * }</pre>
 *
 * @see io.github.timhwang777.unisecret.annotation.SecretValue
 * @see SecretResolver#resolve(SecretReference)
 */
@Value  // Lombok: makes class immutable with getters, equals, hashCode, toString
@Builder  // Lombok: enables builder pattern for construction
public class SecretReference {

    /**
     * The secret key/name in the provider (e.g., "database-password", "api/key").
     * This is the only required field - maps to @SecretValue.value().
     */
    String key;

    /**
     * The version or stage of the secret to retrieve.
     * <ul>
     *   <li>AWS: "latest" → AWSCURRENT, "previous" → AWSPREVIOUS, or specific version ID</li>
     *   <li>GCP: Version number as string (e.g., "1", "5") or "latest"</li>
     *   <li>Local: Ignored (versions not supported)</li>
     * </ul>
     */
    @Builder.Default
    String version = "latest";

    /**
     * JSON field path to extract from a structured secret.
     * Supports dot notation for nested paths (e.g., "database.connection.password").
     * If null/empty, the entire secret value is returned.
     */
    String field;

    /**
     * Single provider override - skips the fallback chain entirely.
     * Takes precedence over {@code providers} list.
     * Example: "aws" forces retrieval from AWS only.
     */
    String provider;

    /**
     * Custom provider chain for this specific secret.
     * Only used if {@code provider} is null/empty.
     * Example: ["local", "aws"] tries local first, then AWS.
     */
    List<String> providers;

    /**
     * Fallback value if the secret isn't found in any provider.
     * If set and secret is missing, this value is returned instead of throwing.
     * Useful for optional secrets with sensible defaults.
     */
    String defaultValue;

    /**
     * Validates that this reference has a valid key.
     *
     * <p>Called before attempting to resolve a secret to fail fast with a clear error.</p>
     *
     * @throws IllegalArgumentException if key is null or blank
     */
    public void validate() {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Secret key must not be null or blank");
        }
    }

    /**
     * Determines which provider chain to use for resolving this secret.
     *
     * <h3>Priority Order</h3>
     * <ol>
     *   <li>If {@code provider} is set → use only that single provider</li>
     *   <li>Else if {@code providers} list is set → use that custom chain</li>
     *   <li>Else → use the global provider order from configuration</li>
     * </ol>
     *
     * <h3>Example</h3>
     * <pre>{@code
     * // Global order: ["aws", "gcp", "local"]
     *
     * // Case 1: provider="aws" → returns ["aws"]
     * // Case 2: providers=["local", "aws"] → returns ["local", "aws"]
     * // Case 3: neither set → returns ["aws", "gcp", "local"]
     * }</pre>
     *
     * @param globalProviderOrder the default provider order from secrets.provider-order config
     * @return the list of provider names to try, in order
     */
    public List<String> getEffectiveProviderChain(List<String> globalProviderOrder) {
        // Priority 1: Single provider override (most specific)
        if (provider != null && !provider.isEmpty()) {
            return List.of(provider);
        } else if (providers != null && !providers.isEmpty()) {
            // Priority 2: Custom provider chain for this secret
            return List.copyOf(providers);
        } else {
            // Priority 3: Fall back to global default order
            return globalProviderOrder;
        }
    }

    @Override
    public String toString() {
        return "SecretReference[key="
                + key
                + ", version="
                + version
                + ", field="
                + field
                + ", provider="
                + provider
                + ", providers="
                + providers
                + ", defaultValue=<redacted>]";
    }
}
