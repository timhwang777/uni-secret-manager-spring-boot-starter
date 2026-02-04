package io.github.timhwang777.unisecret.provider;

import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Google Cloud Secret Manager implementation of the SecretProvider interface.
 *
 * <h2>GCP Secret Manager Overview</h2>
 * Google Cloud Secret Manager stores secrets encrypted with Google Cloud KMS.
 * Each secret can have multiple versions, numbered sequentially (1, 2, 3, ...).
 * The special version "latest" always refers to the most recent enabled version.
 *
 * <h2>Authentication</h2>
 * This provider uses Application Default Credentials (ADC) for authentication.
 * ADC automatically finds credentials in this order:
 * <ol>
 *   <li>GOOGLE_APPLICATION_CREDENTIALS environment variable (path to service account key)</li>
 *   <li>User credentials from `gcloud auth application-default login`</li>
 *   <li>Attached service account (when running on GCP: GCE, GKE, Cloud Run, etc.)</li>
 * </ol>
 *
 * <h2>Secret Naming</h2>
 * GCP secrets are addressed as: projects/{project}/secrets/{secret}/versions/{version}
 * <pre>
 * Example: projects/my-project/secrets/database-password/versions/latest
 * </pre>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * secrets:
 *   gcp:
 *     enabled: true
 *     project-id: my-gcp-project    # Required: GCP project containing secrets
 *     default-version: latest        # Optional: default is "latest"
 * }</pre>
 *
 * @see <a href="https://cloud.google.com/secret-manager/docs">GCP Secret Manager Documentation</a>
 */
@Slf4j
public class GcpSecretProvider implements SecretProvider {

    /**
     * GCP SDK client for Secret Manager operations.
     * Created by {@link io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration}
     * using Application Default Credentials (ADC).
     */
    private final SecretManagerServiceClient client;

    /**
     * GCP-specific configuration (project ID, default version).
     */
    private final SecretManagerProperties.Gcp properties;

    public GcpSecretProvider(SecretManagerServiceClient client, SecretManagerProperties.Gcp properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * Retrieves a secret using the configured default version.
     *
     * @param key the secret name in GCP Secret Manager
     * @return the secret value, or empty if not found
     */
    @Override
    public Optional<String> getSecret(String key) {
        // Use configured default version, falling back to "latest"
        String version = properties.getDefaultVersion() != null ? properties.getDefaultVersion() : "latest";
        return getSecret(key, version);
    }

    /**
     * Retrieves a specific version of a secret from GCP Secret Manager.
     *
     * <h3>Version Format</h3>
     * <ul>
     *   <li>"latest" - Most recent enabled version</li>
     *   <li>"1", "2", "3", etc. - Specific version number</li>
     * </ul>
     *
     * @param key     the secret name in GCP Secret Manager
     * @param version "latest" or a specific version number
     * @return the secret value, or empty if not found
     * @throws SecretProviderException on permission denied or other errors
     */
    @Override
    public Optional<String> getSecret(String key, String version) {
        try {
            log.debug("Retrieving secret '{}' with version '{}' from GCP Secret Manager", key, version);

            // Build the fully-qualified secret version resource name
            // Format: projects/{project}/secrets/{secret}/versions/{version}
            SecretVersionName secretVersionName = SecretVersionName.of(
                    properties.getProjectId(),
                    key,
                    version
            );

            // Call GCP API to access the secret version
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);

            // Extract the secret payload as UTF-8 string
            // GCP stores secrets as binary data (ByteString), we convert to String
            String secretString = response.getPayload().getData().toStringUtf8();

            // Handle edge case of empty secret value
            if (secretString == null || secretString.isEmpty()) {
                log.debug("Secret '{}' exists but has no value", key);
                return Optional.empty();
            }

            log.debug("Successfully retrieved secret '{}' from GCP Secret Manager", key);
            return Optional.of(secretString);

        } catch (NotFoundException e) {
            // SECRET NOT FOUND: Normal case - return empty to try next provider
            log.debug("Secret '{}' not found in GCP Secret Manager", key);
            return Optional.empty();

        } catch (PermissionDeniedException e) {
            // PERMISSION DENIED: The service account lacks access to this secret
            // This is non-retryable - requires IAM policy change
            log.error("Permission denied for secret '{}' in GCP Secret Manager: {}", key, e.getMessage());
            throw new SecretProviderException(
                    String.format("Permission denied for secret '%s': %s", key, e.getMessage()),
                    e
            );

        } catch (Exception e) {
            // FALLBACK NOT_FOUND DETECTION: Some GCP client versions throw generic exceptions
            // with "NOT_FOUND" in the message instead of NotFoundException
            String message = e.getMessage();
            if (message != null && (message.contains("NOT_FOUND") || message.contains("not found"))) {
                log.debug("Secret '{}' not found in GCP Secret Manager (fallback detection)", key);
                return Optional.empty();
            }

            // UNEXPECTED ERROR: Catch-all for network issues, etc.
            log.error("Unexpected error retrieving secret '{}' from GCP Secret Manager: {}", key, e.getMessage());
            throw new SecretProviderException(
                    String.format("Unexpected error retrieving secret '%s': %s", key, e.getMessage()),
                    e
            );
        }
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GCP;
    }

    /**
     * Validates GCP configuration at startup.
     *
     * <p>Note: Project ID is optional if the default project from ADC is acceptable,
     * but it's recommended to configure it explicitly for clarity.</p>
     */
    @Override
    public void validateConfiguration() {
        if (properties.getProjectId() == null || properties.getProjectId().isEmpty()) {
            log.warn("GCP project ID not configured, using Application Default Credentials default");
        }
        log.info("GCP Secret Manager provider configuration validated (project: {})",
                properties.getProjectId() != null ? properties.getProjectId() : "default");
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }
}
