package io.github.timhwang777.unisecret.integration;

import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.config.VaultAutoConfiguration;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Vault provider in the fallback chain.
 * Verifies that secrets are resolved from Vault first and Local as fallback.
 */
@Testcontainers
class VaultFallbackIntegrationTest {

    private static final String VAULT_TOKEN = "fallback-test-token";
    private static final String VAULT_IMAGE = "hashicorp/vault:1.16.3";

    @Container
    static final VaultContainer<?> vault = new VaultContainer<>(DockerImageName.parse(VAULT_IMAGE))
            .withVaultToken(VAULT_TOKEN)
            .withSecretInVault("secret/vault-only-secret", "value=from-vault")
            .withSecretInVault("secret/shared-secret", "value=vault-value");

    private static String vaultHost;
    private static int vaultPort;

    @BeforeAll
    static void setUp() {
        vaultHost = vault.getHost();
        vaultPort = vault.getMappedPort(8200);
    }

    @AfterAll
    static void tearDown() {
        // Testcontainers manages container lifecycle
    }

    private ApplicationContextRunner buildContextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(SecretManagerAutoConfiguration.class, VaultAutoConfiguration.class)
                .withPropertyValues(
                        "secrets.enabled=true",
                        "secrets.fail-on-missing=false",
                        "secrets.provider-order=vault,local",
                        "secrets.vault.enabled=true",
                        "secrets.vault.host=" + vaultHost,
                        "secrets.vault.port=" + vaultPort,
                        "secrets.vault.scheme=http",
                        "secrets.vault.auth-method=TOKEN",
                        "secrets.vault.token=" + VAULT_TOKEN,
                        "secrets.vault.mount=secret",
                        "secrets.vault.kv-version=2",
                        "secrets.local.enabled=true",
                        "secrets.local.secrets.local-only-secret=from-local",
                        "secrets.local.secrets.shared-secret=local-value"
                );
    }

    @Test
    void shouldResolveSecretFromVaultWhenPresentInBoth() {
        buildContextRunner().run(context -> {
            SecretResolver resolver = context.getBean(SecretResolver.class);

            // "shared-secret" exists in both Vault and Local; Vault is first
            String result = resolver.resolve("shared-secret");
            assertThat(result).contains("vault-value");
        });
    }

    @Test
    void shouldFallbackToLocalWhenSecretNotInVault() {
        buildContextRunner().run(context -> {
            SecretResolver resolver = context.getBean(SecretResolver.class);

            // "local-only-secret" exists only in Local
            String result = resolver.resolve("local-only-secret");
            assertThat(result).isEqualTo("from-local");
        });
    }

    @Test
    void shouldResolveVaultOnlySecret() {
        buildContextRunner().run(context -> {
            SecretResolver resolver = context.getBean(SecretResolver.class);

            String result = resolver.resolve("vault-only-secret");
            assertThat(result).contains("from-vault");
        });
    }

    @Test
    void shouldReturnNullForSecretNotInAnyProvider() {
        buildContextRunner().run(context -> {
            SecretResolver resolver = context.getBean(SecretResolver.class);

            String result = resolver.resolve("completely-nonexistent-secret");
            assertThat(result).isNull();
        });
    }
}
