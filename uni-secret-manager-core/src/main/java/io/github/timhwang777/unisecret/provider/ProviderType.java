package io.github.timhwang777.unisecret.provider;

/**
 * Compatibility enumeration for built-in secret provider types.
 *
 * <h2>Available Providers</h2>
 * <ul>
 *   <li><b>AWS</b> - AWS Secrets Manager (cloud, production-ready)</li>
 *   <li><b>GCP</b> - Google Cloud Secret Manager (cloud, production-ready)</li>
 *   <li><b>LOCAL</b> - Local provider for development/testing (explicit map only)</li>
 * </ul>
 *
 * <h2>Configuration Values</h2>
 * In configuration files, use lowercase strings that map to these enums:
 * <pre>{@code
 * secrets:
 *   provider-order:
 *     - aws
 *     - gcp
 *     - local
 * }</pre>
 *
 * <h2>Usage in Code</h2>
 * <pre>{@code
 * // Convert config string to enum
 * ProviderId providerId = ProviderType.AWS.getProviderId();
 * }</pre>
 *
 * <p>New resolver and provider SPI code should use {@link ProviderId} directly.</p>
 *
 * @see SecretProvider
 */
public enum ProviderType {
    /**
     * AWS Secrets Manager - stores secrets encrypted with AWS KMS.
     * Requires AWS SDK and valid AWS credentials.
     */
    AWS(ProviderId.AWS),

    /**
     * Google Cloud Secret Manager - stores secrets encrypted with Google Cloud KMS.
     * Requires GCP client library and Application Default Credentials.
     */
    GCP(ProviderId.GCP),

    /**
     * HashiCorp Vault KV secrets engine (v1 or v2).
     * Requires spring-vault-core and a running Vault server.
     */
    VAULT(ProviderId.VAULT),

    /**
     * Local provider for development and testing.
     * Reads secrets from explicit local option data.
     */
    LOCAL(ProviderId.LOCAL);

    /**
     * Built-in provider id.
     */
    private final ProviderId providerId;

    ProviderType(ProviderId providerId) {
        this.providerId = providerId;
    }

    /**
     * Returns the built-in provider id.
     *
     * @return provider id
     */
    public ProviderId getProviderId() {
        return providerId;
    }

    /**
     * Returns the lowercase configuration value.
     *
     * @return configuration value
     */
    public String getConfigValue() {
        return providerId.value();
    }

    /**
     * Converts a configuration string to its corresponding ProviderType enum.
     *
     * <p>The comparison is case-insensitive, so "AWS", "aws", and "Aws" all work.</p>
     *
     * @param configValue the configuration value (e.g., "aws", "gcp", "local")
     * @return the corresponding ProviderType
     * @throws IllegalArgumentException if the value doesn't match any known provider
     */
    public static ProviderType fromConfigValue(String configValue) {
        // Iterate through all enum values to find a match
        for (ProviderType type : values()) {
            if (type.providerId.equals(ProviderId.of(configValue))) {
                return type;
            }
        }
        // No match found - throw with helpful error message
        throw new IllegalArgumentException("Unknown provider type: " + configValue);
    }
}
