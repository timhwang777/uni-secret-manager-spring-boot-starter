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
@Testcontainers
@SpringBootTest(classes = VaultAnnotationIntegrationTest.TestConfig.class)
class VaultAnnotationIntegrationTest {

    private static final String VAULT_TOKEN = "annotation-test-token";
    private static final String VAULT_IMAGE = "hashicorp/vault:1.16.3";

    @Container
    static final VaultContainer<?> vault = new VaultContainer<>(DockerImageName.parse(VAULT_IMAGE))
            .withVaultToken(VAULT_TOKEN)
            .withSecretInVault("secret/db-config", "host=db.internal", "password=s3cret")
            .withSecretInVault("secret/api-key", "value=my-api-key-123");

    @DynamicPropertySource
    static void vaultProperties(DynamicPropertyRegistry registry) {
        registry.add("secrets.vault.host", vault::getHost);
        registry.add("secrets.vault.port", () -> vault.getMappedPort(8200));
        registry.add("secrets.vault.token", () -> VAULT_TOKEN);
    }

    @BeforeAll
    static void setUp() {
        // Container started by @Testcontainers; properties registered via @DynamicPropertySource
    }

    @AfterAll
    static void tearDown() {
        // Testcontainers manages container lifecycle
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
