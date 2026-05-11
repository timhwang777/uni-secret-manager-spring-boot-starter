package io.github.timhwang777.unisecret.provider;

import io.github.timhwang777.unisecret.config.AwsSecretProviderOptions;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DecryptionFailureException;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.InternalServiceErrorException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.util.Optional;

/**
 * AWS Secrets Manager implementation of the SecretProvider interface.
 *
 * <h2>AWS Secrets Manager Overview</h2>
 * AWS Secrets Manager stores secrets encrypted with AWS KMS (Key Management Service).
 * Secrets can have multiple versions, tracked by:
 * <ul>
 *   <li><b>Staging Labels</b>: AWSCURRENT (latest), AWSPREVIOUS (before last rotation)</li>
 *   <li><b>Version IDs</b>: UUIDs for specific versions</li>
 * </ul>
 *
 * <h2>Version Mapping</h2>
 * This provider maps user-friendly version names to AWS concepts:
 * <pre>
 * User Version    → AWS API Parameter
 * ─────────────────────────────────────
 * "latest"        → versionStage=AWSCURRENT
 * "previous"      → versionStage=AWSPREVIOUS
 * "abc-123-uuid"  → versionId=abc-123-uuid
 * </pre>
 *
 * <h2>Error Handling</h2>
 * Different AWS exceptions are handled differently:
 * <ul>
 *   <li>{@code ResourceNotFoundException}: Return empty (secret doesn't exist)</li>
 *   <li>{@code DecryptionFailureException}: Non-retryable error (KMS issue)</li>
 *   <li>{@code InternalServiceErrorException}: Retryable error (AWS hiccup)</li>
 *   <li>{@code AccessDeniedException}: Non-retryable error (permission issue)</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * secrets:
 *   aws:
 *     enabled: true
 *     region: us-east-1          # Optional: uses SDK default if not set
 *     endpoint: http://localhost:4566  # Optional: for LocalStack testing
 * }</pre>
 *
 * @see <a href="https://docs.aws.amazon.com/secretsmanager/latest/userguide/">AWS Secrets Manager Documentation</a>
 */
@Slf4j
public class AwsSecretProvider implements SecretProvider {

    /**
     * AWS SDK client for Secrets Manager operations.
     * Created and configured by {@link io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration}.
     */
    private final SecretsManagerClient client;

    /**
     * AWS-specific options.
     */
    private final AwsSecretProviderOptions options;

    public AwsSecretProvider(SecretsManagerClient client, AwsSecretProviderOptions options) {
        this.client = client;
        this.options = options == null ? AwsSecretProviderOptions.disabled() : options;
    }

    /**
     * Retrieves the latest version of a secret.
     *
     * @param key the secret name/ARN in AWS Secrets Manager
     * @return the secret value, or empty if not found
     */
    @Override
    public Optional<String> getSecret(String key) {
        return getSecret(key, "latest");
    }

    /**
     * Retrieves a specific version of a secret from AWS Secrets Manager.
     *
     * <h3>How Version Mapping Works</h3>
     * <pre>
     * version="latest"   → API: versionStage=AWSCURRENT
     * version="previous" → API: versionStage=AWSPREVIOUS
     * version="abc-uuid" → API: versionId=abc-uuid
     * </pre>
     *
     * @param key     the secret name or ARN
     * @param version "latest", "previous", or a specific version ID
     * @return the secret value, or empty if not found
     * @throws SecretProviderException on provider errors (access denied, decryption failure, etc.)
     */
    @Override
    public Optional<String> getSecret(String key, String version) {
        try {
            log.debug("Retrieving secret '{}' with version '{}' from AWS Secrets Manager", key, version);

            // Build the request starting with the secret identifier
            GetSecretValueRequest.Builder requestBuilder = GetSecretValueRequest.builder()
                    .secretId(key);

            // Map user-friendly version to AWS-specific parameter
            // AWS distinguishes between "stage labels" and "version IDs"
            String mappedVersion = mapVersion(version);
            if (isAwsStage(mappedVersion)) {
                // AWSCURRENT or AWSPREVIOUS are stage labels
                requestBuilder.versionStage(mappedVersion);
            } else {
                // Anything else is treated as a specific version ID (UUID)
                requestBuilder.versionId(mappedVersion);
            }

            // Execute the API call
            GetSecretValueResponse response = client.getSecretValue(requestBuilder.build());
            String secretString = response.secretString();

            if (secretString == null || secretString.isEmpty()) {
                if (response.secretBinary() != null) {
                    throw new SecretProviderException(
                            "AWS binary secrets are not supported in 2.0 for secret '" + key + "'",
                            null,
                            false
                    );
                }
                log.debug("Secret '{}' exists but has no value", key);
                return Optional.empty();
            }

            log.debug("Successfully retrieved secret '{}' from AWS Secrets Manager", key);
            return Optional.of(secretString);

        } catch (ResourceNotFoundException e) {
            // SECRET NOT FOUND: This is normal - return empty to try next provider
            log.debug("Secret '{}' not found in AWS Secrets Manager", key);
            return Optional.empty();

        } catch (DecryptionFailureException e) {
            // KMS DECRYPTION FAILED: Non-retryable (wrong key, key disabled, etc.)
            log.error("Decryption failed for secret '{}' in AWS Secrets Manager: {}", key, e.getMessage());
            throw new SecretProviderException(
                    String.format("Decryption failed for secret '%s': %s", key, e.getMessage()),
                    e
            );

        } catch (InternalServiceErrorException e) {
            // AWS INTERNAL ERROR: Retryable (transient AWS issue)
            log.error("AWS Secrets Manager internal error for secret '{}': {}", key, e.getMessage());
            throw new SecretProviderException(
                    String.format("AWS Secrets Manager internal error for secret '%s': %s", key, e.getMessage()),
                    e,
                    true  // Mark as retryable - AWS may recover
            );

        } catch (AwsServiceException e) {
            // OTHER AWS ERRORS: Check for access denied specifically
            if (e.awsErrorDetails() != null && "AccessDeniedException".equals(e.awsErrorDetails().errorCode())) {
                // ACCESS DENIED: Non-retryable (IAM policy issue)
                log.error("Access denied for secret '{}' in AWS Secrets Manager: {}", key, e.getMessage());
                throw new SecretProviderException(
                        String.format("Access denied for secret '%s': %s", key, e.getMessage()),
                        e
                );
            }
            boolean retryable = e.statusCode() == 429 || e.statusCode() >= 500;
            log.error("AWS service error retrieving secret '{}': {}", key, e.getMessage());
            throw new SecretProviderException(
                    String.format("AWS service error retrieving secret '%s': %s", key, e.getMessage()),
                    e,
                    retryable
            );

        } catch (SecretProviderException e) {
            throw e;

        } catch (Exception e) {
            // UNEXPECTED ERROR: Catch-all for any other issues
            log.error("Unexpected error retrieving secret '{}' from AWS Secrets Manager: {}", key, e.getMessage());
            throw new SecretProviderException(
                    String.format("Unexpected error retrieving secret '%s': %s", key, e.getMessage()),
                    e
            );
        }
    }

    @Override
    public ProviderId getProviderId() {
        return ProviderId.AWS;
    }

    /**
     * Logs the provider configuration at startup.
     * AWS provider doesn't require strict validation since the SDK handles credentials.
     */
    @Override
    public void validateConfiguration() {
        log.info("AWS Secrets Manager provider configuration validated (region: {})",
                options.region() != null ? options.region() : "default");
    }

    @Override
    public boolean isEnabled() {
        return options.enabled();
    }

    /**
     * Maps user-friendly version strings to AWS Secrets Manager version concepts.
     *
     * <p>AWS Secrets Manager has two ways to identify versions:</p>
     * <ol>
     *   <li><b>Staging Labels</b>: Logical pointers like AWSCURRENT, AWSPREVIOUS</li>
     *   <li><b>Version IDs</b>: UUIDs like "a1b2c3d4-5678-90ab-cdef-EXAMPLE11111"</li>
     * </ol>
     *
     * <p>This method converts user input to the appropriate AWS format:</p>
     * <pre>
     * "latest"   → "AWSCURRENT"  (current active version)
     * "previous" → "AWSPREVIOUS" (version before last rotation)
     * "abc-123"  → "abc-123"     (passed through as version ID)
     * </pre>
     *
     * @param version the user-specified version ("latest", "previous", or version ID)
     * @return the AWS-formatted version string
     */
    private String mapVersion(String version) {
        // Default to latest if version is null, empty, or explicitly "latest"
        if (version == null || version.isEmpty() || "latest".equalsIgnoreCase(version)) {
            return "AWSCURRENT";
        }
        // Map "previous" to AWS's previous version label
        if ("previous".equalsIgnoreCase(version)) {
            return "AWSPREVIOUS";
        }
        // Anything else is assumed to be a specific version ID (UUID)
        return version;
    }

    /**
     * Determines if a mapped version string is an AWS staging label vs a version ID.
     *
     * <p>This is important because AWS uses different API parameters:</p>
     * <ul>
     *   <li>Stage labels → {@code versionStage} parameter</li>
     *   <li>Version IDs → {@code versionId} parameter</li>
     * </ul>
     *
     * @param version the mapped version string (after calling mapVersion)
     * @return true if it's a staging label (AWSCURRENT/AWSPREVIOUS), false if it's a version ID
     */
    private boolean isAwsStage(String version) {
        return "AWSCURRENT".equals(version) || "AWSPREVIOUS".equals(version);
    }
}
