package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.config.VaultAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for @SecretValue annotation injection with the Vault provider.
 * Verifies that Vault-sourced secrets are injected into Spring beans,
 * including JSON field extraction via the 'field' parameter.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = VaultAnnotationIntegrationTest.TestConfig.class)
class VaultAnnotationIntegrationTest {

    private static final String VAULT_TOKEN = "annotation-test-token";
    private static final String VAULT_IMAGE = "hashicorp/vault:1.16.3";

    @Container
    static final VaultContainer<?> vault = new VaultContainer<>(DockerImageName.parse(VAULT_IMAGE))
            .withVaultToken(VAULT_TOKEN);

    @DynamicPropertySource
    static void vaultProperties(DynamicPropertyRegistry registry) {
        registry.add("secrets.enabled", () -> true);
        registry.add("secrets.fail-on-missing", () -> true);
        registry.add("secrets.provider-order[0]", () -> "vault");
        registry.add("secrets.vault.enabled", () -> true);
        registry.add("secrets.vault.host", vault::getHost);
        registry.add("secrets.vault.port", () -> vault.getMappedPort(8200));
        registry.add("secrets.vault.scheme", () -> "http");
        registry.add("secrets.vault.auth-method", () -> "TOKEN");
        registry.add("secrets.vault.token", () -> VAULT_TOKEN);
        registry.add("secrets.vault.mount", () -> "secret");
        registry.add("secrets.vault.kv-version", () -> 2);
    }

    @BeforeAll
    static void setUp() throws Exception {
        putSecret("secret/db-config", "host=db.internal", "password=s3cret");
        putSecret("secret/api-key", "value=my-api-key-123");
    }

    @AfterAll
    static void tearDown() {
        // Testcontainers manages container lifecycle
    }

    private static void putSecret(String path, String... keyValues) throws Exception {
        String[] command = new String[4 + keyValues.length];
        command[0] = "vault";
        command[1] = "kv";
        command[2] = "put";
        command[3] = path;
        System.arraycopy(keyValues, 0, command, 4, keyValues.length);

        org.testcontainers.containers.Container.ExecResult result = vault.execInContainer(command);
        assertThat(result.getExitCode())
                .as("failed to seed Vault secret at %s, stderr: %s", path, result.getStderr())
                .isEqualTo(0);
    }

    @Configuration
    @Import({SecretManagerAutoConfiguration.class, VaultAutoConfiguration.class})
    static class TestConfig {

        @Bean
        public AnnotationTestBean annotationTestBean() {
            return new AnnotationTestBean();
        }
    }

    static class AnnotationTestBean {

        @SecretValue(value = "api-key", provider = "vault")
        private String apiKeyRaw;

        @SecretValue(value = "db-config", provider = "vault", field = "password")
        private String dbPassword;

        @SecretValue(value = "db-config", provider = "vault", field = "host")
        private String dbHost;

        public String getApiKeyRaw() {
            return apiKeyRaw;
        }

        public String getDbPassword() {
            return dbPassword;
        }

        public String getDbHost() {
            return dbHost;
        }
    }

    @Autowired
    private AnnotationTestBean testBean;

    @Test
    void shouldInjectVaultSecretViaAnnotation() {
        assertThat(testBean).isNotNull();
        assertThat(testBean.getApiKeyRaw()).contains("my-api-key-123");
    }

    @Test
    void shouldExtractJsonFieldFromVaultSecret() {
        assertThat(testBean).isNotNull();
        assertThat(testBean.getDbPassword()).isEqualTo("s3cret");
        assertThat(testBean.getDbHost()).isEqualTo("db.internal");
    }
}
