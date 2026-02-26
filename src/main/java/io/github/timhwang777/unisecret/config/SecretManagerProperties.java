package io.github.timhwang777.unisecret.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the Universal Secret Manager.
 *
 * <h2>Configuration Structure</h2>
 * This class maps to the {@code secrets.*} properties in your configuration files.
 *
 * <h2>Complete Configuration Example</h2>
 * <pre>{@code
 * # application.yml
 * secrets:
 *   enabled: true                    # Master enable/disable switch
 *   fail-on-missing: true            # Fail startup if secrets not found
 *   provider-order:                  # Fallback chain order
 *     - aws
 *     - gcp
 *     - local
 *
 *   aws:                             # AWS Secrets Manager settings
 *     enabled: true
 *     region: us-east-1              # Optional: uses SDK default
 *     endpoint: http://localhost:4566  # Optional: for LocalStack
 *
 *   gcp:                             # GCP Secret Manager settings
 *     enabled: true
 *     project-id: my-gcp-project     # Required if GCP enabled
 *     default-version: latest        # Optional
 *
 *   local:                           # Local provider for dev/test
 *     enabled: true
 *     secrets:                       # Map of key-value pairs
 *       database-password: secret123
 *       api-key: abc-def-ghi
 *
 *   cache:                           # Caching settings
 *     enabled: true
 *     ttl: 5m                        # Time-to-live
 *     max-size: 1000                 # Max entries
 *
 *   retry:                           # Retry settings for transient failures
 *     max-attempts: 3
 *     initial-delay: 1s
 *     multiplier: 2.0
 *     max-delay: 10s
 * }</pre>
 *
 * <h2>Profile-Based Configuration</h2>
 * <pre>{@code
 * # application-local.yml (local development)
 * secrets:
 *   provider-order: [local]
 *   local:
 *     enabled: true
 *     secrets:
 *       database-password: dev-password
 *
 * # application-prod.yml (production)
 * secrets:
 *   provider-order: [aws, gcp]
 *   aws:
 *     enabled: true
 *     region: us-west-2
 * }</pre>
 *
 * @see SecretManagerAutoConfiguration
 */
@Data  // Lombok: generates getters, setters, equals, hashCode, toString
@ConfigurationProperties(prefix = "secrets")  // Binds to secrets.* properties
public class SecretManagerProperties {

    // ==================== Global Settings ====================

    /**
     * Master switch to enable/disable the entire secret management library.
     * When false, @SecretValue annotations will not be processed.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * The order in which providers are tried when resolving secrets.
     * Each provider is tried in sequence until one returns a value.
     *
     * Example: ["aws", "gcp", "local"] means:
     * 1. Try AWS first
     * 2. If not found, try GCP
     * 3. If still not found, try local
     *
     * Default: ["aws", "gcp", "local"]
     */
    private List<String> providerOrder = List.of("aws", "gcp", "local");

    /**
     * Whether to fail application startup if a required secret is not found.
     * When true: Missing secrets throw SecretNotFoundException → startup fails
     * When false: Missing secrets return null/empty (use with defaultValue)
     * Default: true (recommended for production)
     */
    private boolean failOnMissing = true;

    // ==================== Provider Configurations ====================

    /**
     * AWS Secrets Manager configuration.
     */
    private Aws aws = new Aws();

    /**
     * GCP Secret Manager configuration.
     */
    private Gcp gcp = new Gcp();

    /**
     * HashiCorp Vault provider configuration.
     */
    private Vault vault = new Vault();

    /**
     * Local provider configuration (for development/testing).
     */
    private Local local = new Local();

    // ==================== Operational Settings ====================

    /**
     * Cache configuration for reducing provider API calls.
     */
    private Cache cache = new Cache();

    /**
     * Retry configuration for handling transient failures.
     */
    private Retry retry = new Retry();

    // ==================== Inner Configuration Classes ====================

    /**
     * AWS Secrets Manager specific configuration.
     *
     * <h3>Configuration Example</h3>
     * <pre>{@code
     * secrets:
     *   aws:
     *     enabled: true
     *     region: us-east-1           # Optional
     *     endpoint: http://localhost:4566  # For LocalStack
     * }</pre>
     */
    @Data
    public static class Aws {
        /**
         * Enable the AWS Secrets Manager provider.
         * Default: false (must explicitly enable)
         */
        private boolean enabled = false;

        /**
         * AWS region for the Secrets Manager client.
         * If not specified, uses the AWS SDK default region resolution:
         * 1. AWS_REGION environment variable
         * 2. Region from AWS profile
         * 3. EC2/ECS metadata (when running on AWS)
         */
        private String region;

        /**
         * Custom endpoint URL for AWS Secrets Manager.
         * Useful for:
         * - LocalStack testing: "http://localhost:4566"
         * - AWS GovCloud or China regions
         * - VPC endpoints
         */
        private String endpoint;
    }

    /**
     * GCP Secret Manager specific configuration.
     *
     * <h3>Configuration Example</h3>
     * <pre>{@code
     * secrets:
     *   gcp:
     *     enabled: true
     *     project-id: my-gcp-project
     *     default-version: latest
     * }</pre>
     */
    @Data
    public static class Gcp {
        /**
         * Enable the GCP Secret Manager provider.
         * Default: false (must explicitly enable)
         */
        private boolean enabled = false;

        /**
         * GCP project ID containing the secrets.
         * Required when GCP provider is enabled.
         * If not specified, uses the default from Application Default Credentials.
         */
        private String projectId;

        /**
         * Default version to use when version is not specified in @SecretValue.
         * Common values: "latest" (most recent), or a specific version number.
         * Default: "latest"
         */
        private String defaultVersion = "latest";
    }

    /**
     * Local secret provider configuration for development and testing.
     *
     * <h3>Configuration Example</h3>
     * <pre>{@code
     * secrets:
     *   local:
     *     enabled: true
     *     secrets:
     *       database-password: secret123
     *       api-key: abc-def-ghi
     * }</pre>
     */
    @Data
    public static class Local {
        /**
         * Enable the local secret provider.
         * Default: false (must explicitly enable)
         */
        private boolean enabled = false;

        /**
         * Map of secret key to secret value.
         * These values are stored directly in configuration (use for dev only!).
         *
         * Example:
         *   secrets:
         *     database-password: dev-password
         *     api-key: test-key-123
         */
        private Map<String, String> secrets = new HashMap<>();
    }

    /**
     * HashiCorp Vault provider configuration.
     */
    @Data
    public static class Vault {

        private boolean enabled = false;
        private String host = "localhost";
        private int port = 8200;
        private String scheme = "https";
        private String namespace;
        private AuthMethod authMethod = AuthMethod.TOKEN;
        private String token;
        private String mount = "secret";
        private int kvVersion = 2;
        private AppRole appRole = new AppRole();
        private Kubernetes kubernetes = new Kubernetes();
        private Ssl ssl = new Ssl();

        /**
         * Vault authentication method.
         */
        public enum AuthMethod {
            TOKEN, APPROLE, KUBERNETES
        }

        /**
         * AppRole authentication configuration.
         */
        @Data
        public static class AppRole {
            private String roleId;
            private String secretId;
            private String path = "approle";
        }

        /**
         * Kubernetes authentication configuration.
         */
        @Data
        public static class Kubernetes {
            private String role;
            private String serviceAccountTokenPath =
                    "/var/run/secrets/kubernetes.io/serviceaccount/token";
            private String path = "kubernetes";
        }

        /**
         * SSL/TLS configuration.
         */
        @Data
        public static class Ssl {
            private String caCertPath;
        }
    }

    /**
     * Cache configuration for storing retrieved secrets in memory.
     *
     * <h3>Configuration Example</h3>
     * <pre>{@code
     * secrets:
     *   cache:
     *     enabled: true
     *     ttl: 5m        # 5 minutes
     *     max-size: 1000 # Maximum 1000 entries
     * }</pre>
     */
    @Data
    public static class Cache {
        /**
         * Enable in-memory caching of secrets.
         * Recommended for production to reduce API calls and latency.
         * Default: true
         */
        private boolean enabled = true;

        /**
         * Time-to-live for cached entries.
         * After this duration, cached values expire and are re-fetched on next access.
         * Supports duration formats: 5m, 300s, PT5M
         * Default: 5 minutes
         */
        private Duration ttl = Duration.ofMinutes(5);

        /**
         * Maximum number of secrets to cache.
         * When exceeded, least-recently-used entries are evicted.
         * Default: 1000
         */
        private int maxSize = 1000;
    }

    /**
     * Retry configuration for handling transient provider failures.
     *
     * <h3>How Retries Work</h3>
     * Uses exponential backoff: each retry waits longer than the previous.
     * <pre>
     * Attempt 1: immediate
     * Attempt 2: wait 1s
     * Attempt 3: wait 2s (1s × 2.0)
     * </pre>
     *
     * <h3>Configuration Example</h3>
     * <pre>{@code
     * secrets:
     *   retry:
     *     max-attempts: 3
     *     initial-delay: 1s
     *     multiplier: 2.0
     *     max-delay: 10s
     * }</pre>
     */
    @Data
    public static class Retry {
        /**
         * Maximum number of retry attempts before giving up.
         * Includes the initial attempt (so 3 means: try, retry, retry).
         * Default: 3
         */
        private int maxAttempts = 3;

        /**
         * Initial delay before the first retry.
         * Supports duration formats: 1s, 1000ms, PT1S
         * Default: 1 second
         */
        private Duration initialDelay = Duration.ofSeconds(1);

        /**
         * Multiplier for exponential backoff.
         * Each retry delay = previous delay × multiplier
         * Example: 1s → 2s → 4s → 8s (with multiplier 2.0)
         * Default: 2.0
         */
        private double multiplier = 2.0;

        /**
         * Maximum delay between retries (caps the exponential growth).
         * Default: 10 seconds
         */
        private Duration maxDelay = Duration.ofSeconds(10);
    }
}
