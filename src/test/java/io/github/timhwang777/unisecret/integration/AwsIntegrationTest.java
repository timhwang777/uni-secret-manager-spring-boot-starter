package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.provider.AwsSecretProvider;
import io.github.timhwang777.unisecret.provider.ProviderType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for AwsSecretProvider using LocalStack.
 */
class AwsIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:3.0");

    private static LocalStackContainer localstack;

    private static SecretsManagerClient client;
    private static AwsSecretProvider provider;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available; skipping LocalStack integration tests");

        localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
                .withServices(LocalStackContainer.Service.SECRETSMANAGER);
        localstack.start();

        // Create AWS Secrets Manager client pointing to LocalStack
        client = SecretsManagerClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        localstack.getAccessKey(),
                                        localstack.getSecretKey()
                                )
                        )
                )
                .region(Region.of(localstack.getRegion()))
                .build();

        // Create test secrets
        client.createSecret(CreateSecretRequest.builder()
                .name("test-secret")
                .secretString("test-value")
                .build());

        client.createSecret(CreateSecretRequest.builder()
                .name("json-secret")
                .secretString("{\"password\":\"secret123\",\"username\":\"admin\"}")
                .build());

        // Initialize provider
        SecretManagerProperties.Aws awsProperties = new SecretManagerProperties.Aws();
        awsProperties.setEnabled(true);
        awsProperties.setEndpoint(localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER).toString());

        provider = new AwsSecretProvider(client, awsProperties);
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
        if (localstack != null) {
            localstack.stop();
        }
    }

    @Test
    void shouldRetrieveSecretFromLocalStack() {
        Optional<String> result = provider.getSecret("test-secret");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("test-value");
    }

    @Test
    void shouldReturnEmptyForNonExistentSecret() {
        Optional<String> result = provider.getSecret("non-existent-secret");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldRetrieveJsonSecret() {
        Optional<String> result = provider.getSecret("json-secret");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("password");
        assertThat(result.get()).contains("secret123");
    }

    @Test
    void shouldReturnCorrectProviderType() {
        assertThat(provider.getProviderType()).isEqualTo(ProviderType.AWS);
    }

    @Test
    void shouldBeEnabled() {
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void shouldValidateConfiguration() {
        provider.validateConfiguration();
        // Should not throw any exception
    }
}
