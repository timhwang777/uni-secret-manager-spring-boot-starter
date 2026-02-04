package io.github.timhwang777.unisecret.provider;

import lombok.Getter;

/**
 * Enumeration of supported secret provider types.
 *
 * <h2>Available Providers</h2>
 * <ul>
 *   <li><b>AWS</b> - AWS Secrets Manager (cloud, production-ready)</li>
 *   <li><b>GCP</b> - Google Cloud Secret Manager (cloud, production-ready)</li>
 *   <li><b>LOCAL</b> - Local provider for development/testing (reads from Spring Environment)</li>
 * </ul>
 *
 * <h2>Configuration Values</h2>
 * In configuration files, use lowercase strings that map to these enums:
 * <pre>{@code
 * secrets:
 *   provider-order:
 *     - aws    # Maps to ProviderType.AWS
 *     - gcp    # Maps to ProviderType.GCP
 *     - local  # Maps to ProviderType.LOCAL
 * }</pre>
 *
 * <h2>Usage in Code</h2>
 * <pre>{@code
 * // Convert config string to enum
 * ProviderType type = ProviderType.fromConfigValue("aws");
 *
 * // Get config string from enum
 * String configValue = ProviderType.AWS.getConfigValue();  // "aws"
 * }</pre>
 *
 * @see SecretProvider
 */
@Getter
public enum ProviderType {
    /**
     * AWS Secrets Manager - stores secrets encrypted with AWS KMS.
     * Requires AWS SDK and valid AWS credentials.
     */
    AWS("aws"),

    /**
     * Google Cloud Secret Manager - stores secrets encrypted with Google Cloud KMS.
     * Requires GCP client library and Application Default Credentials.
     */
    GCP("gcp"),

    /**
     * Local provider for development and testing.
     * Reads secrets from Spring Environment (application.yml, system properties, env vars).
     */
    LOCAL("local");

    /**
     * The lowercase string used in configuration files.
     * Example: "aws", "gcp", "local"
     */
    private final String configValue;

    ProviderType(String configValue) {
        this.configValue = configValue;
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
            if (type.configValue.equalsIgnoreCase(configValue)) {
                return type;
            }
        }
        // No match found - throw with helpful error message
        throw new IllegalArgumentException("Unknown provider type: " + configValue);
    }
}
