package io.github.timhwang777.unisecret.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for automatic secret injection from the configured provider chain.
 *
 * <h2>Overview</h2>
 * This annotation enables declarative secret management in Spring beans. When a bean
 * is created, the {@link io.github.timhwang777.unisecret.processor.SecretValueBeanPostProcessor}
 * scans for fields with this annotation and automatically injects secret values from
 * the configured secret providers (AWS Secrets Manager, GCP Secret Manager, or local).
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>During Spring bean initialization, the processor finds all @SecretValue fields</li>
 *   <li>It builds a {@link io.github.timhwang777.unisecret.provider.SecretReference} from the annotation</li>
 *   <li>The {@link io.github.timhwang777.unisecret.provider.SecretResolver} tries each provider in the chain</li>
 *   <li>The first successful value is injected into the field</li>
 * </ol>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Simple secret injection - uses global provider chain
 * @SecretValue("database-password")
 * private String dbPassword;
 *
 * // Extract a specific field from JSON secret
 * // If secret contains {"user":"admin","password":"secret123"}
 * @SecretValue(value = "db-credentials", field = "password")
 * private String dbPassword;
 *
 * // Use nested JSON path
 * @SecretValue(value = "config", field = "database.connection.password")
 * private String dbPassword;
 *
 * // Force a specific provider (skip the chain)
 * @SecretValue(value = "my-secret", provider = "aws")
 * private String awsOnlySecret;
 *
 * // Custom provider chain for this secret only
 * @SecretValue(value = "my-secret", providers = {"local", "aws"})
 * private String localFirstSecret;
 *
 * // With fallback default value
 * @SecretValue(value = "optional-secret", defaultValue = "default-value")
 * private String optionalSecret;
 *
 * // Specific version (GCP) or stage (AWS)
 * @SecretValue(value = "my-secret", version = "5")
 * private String specificVersion;
 * }</pre>
 *
 * @see io.github.timhwang777.unisecret.processor.SecretValueBeanPostProcessor
 * @see io.github.timhwang777.unisecret.provider.SecretResolver
 */
@Target(ElementType.FIELD)  // Can only be applied to fields, not methods or classes
@Retention(RetentionPolicy.RUNTIME)  // Available at runtime for reflection-based processing
public @interface SecretValue {

    /**
     * Secret key/name in the provider.
     *
     * @return the secret key
     */
    String value();

    /**
     * Fallback value if secret not found in any provider.
     *
     * @return the default value (empty string if not specified)
     */
    String defaultValue() default "";

    /**
     * Single provider override (skips fallback chain).
     * Examples: "aws", "gcp", "local"
     *
     * @return the provider name (empty if not specified)
     */
    String provider() default "";

    /**
     * Custom provider chain for this secret.
     * Only used if provider() is not specified.
     * Examples: {"local", "aws"}, {"gcp"}
     *
     * @return the custom provider chain (empty array if not specified)
     */
    String[] providers() default {};

    /**
     * JSON field path for structured secrets.
     * Examples: "password", "database.password"
     *
     * @return the JSON field path (empty if not specified)
     */
    String field() default "";

    /**
     * Secret version (GCP) or stage (AWS).
     * Defaults to "latest".
     *
     * @return the version identifier
     */
    String version() default "latest";
}
