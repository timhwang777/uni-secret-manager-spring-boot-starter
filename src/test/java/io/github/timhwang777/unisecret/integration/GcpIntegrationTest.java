package io.github.timhwang777.unisecret.integration;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.provider.GcpSecretProvider;
import io.github.timhwang777.unisecret.provider.ProviderType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for GcpSecretProvider.
 * <p>
 * This test requires actual GCP credentials and a GCP project with Secret Manager enabled.
 * It is disabled by default and should be run manually with proper GCP setup.
 * <p>
 * To run this test:
 * 1. Set up GCP credentials (gcloud auth application-default login)
 * 2. Create a test secret in GCP Secret Manager
 * 3. Set the GCP_PROJECT_ID environment variable
 * 4. Remove the @Disabled annotation
 */
@Disabled("Requires GCP credentials and manual setup")
class GcpIntegrationTest {

    @Test
    void shouldRetrieveSecretFromGcp() throws Exception {
        // Setup
        String projectId = System.getenv("GCP_PROJECT_ID");
        if (projectId == null || projectId.isEmpty()) {
            projectId = "test-project"; // fallback for local testing
        }

        SecretManagerProperties.Gcp gcpProperties = new SecretManagerProperties.Gcp();
        gcpProperties.setEnabled(true);
        gcpProperties.setProjectId(projectId);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            GcpSecretProvider provider = new GcpSecretProvider(client, gcpProperties);

            // Test retrieving a secret (assumes "test-secret" exists in your GCP project)
            Optional<String> result = provider.getSecret("test-secret");

            // Assertions
            assertThat(result).isPresent();
            assertThat(result.get()).isNotEmpty();
        }
    }

    @Test
    void shouldReturnEmptyForNonExistentSecret() throws Exception {
        // Setup
        String projectId = System.getenv("GCP_PROJECT_ID");
        if (projectId == null || projectId.isEmpty()) {
            projectId = "test-project";
        }

        SecretManagerProperties.Gcp gcpProperties = new SecretManagerProperties.Gcp();
        gcpProperties.setEnabled(true);
        gcpProperties.setProjectId(projectId);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            GcpSecretProvider provider = new GcpSecretProvider(client, gcpProperties);

            // Test retrieving a non-existent secret
            Optional<String> result = provider.getSecret("non-existent-secret-" + System.currentTimeMillis());

            // Assertions
            assertThat(result).isEmpty();
        }
    }

    @Test
    void shouldReturnCorrectProviderType() throws Exception {
        String projectId = "test-project";

        SecretManagerProperties.Gcp gcpProperties = new SecretManagerProperties.Gcp();
        gcpProperties.setEnabled(true);
        gcpProperties.setProjectId(projectId);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            GcpSecretProvider provider = new GcpSecretProvider(client, gcpProperties);

            assertThat(provider.getProviderType()).isEqualTo(ProviderType.GCP);
        }
    }

    @Test
    void shouldBeEnabled() throws Exception {
        String projectId = "test-project";

        SecretManagerProperties.Gcp gcpProperties = new SecretManagerProperties.Gcp();
        gcpProperties.setEnabled(true);
        gcpProperties.setProjectId(projectId);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            GcpSecretProvider provider = new GcpSecretProvider(client, gcpProperties);

            assertThat(provider.isEnabled()).isTrue();
        }
    }

    @Test
    void shouldValidateConfiguration() throws Exception {
        String projectId = "test-project";

        SecretManagerProperties.Gcp gcpProperties = new SecretManagerProperties.Gcp();
        gcpProperties.setEnabled(true);
        gcpProperties.setProjectId(projectId);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            GcpSecretProvider provider = new GcpSecretProvider(client, gcpProperties);

            provider.validateConfiguration();
            // Should not throw any exception
        }
    }
}
