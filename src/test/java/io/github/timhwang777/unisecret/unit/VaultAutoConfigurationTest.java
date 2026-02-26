package io.github.timhwang777.unisecret.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.timhwang777.unisecret.config.VaultAutoConfiguration;
import io.github.timhwang777.unisecret.provider.VaultSecretProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for VaultAutoConfiguration — verifies conditional bean creation
 * with different authentication methods, without requiring a real Vault server.
 */
class VaultAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(VaultAutoConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void shouldNotCreateBeansWhenVaultDisabled() {
        contextRunner
                .withPropertyValues("secrets.vault.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(VaultTemplate.class);
                    assertThat(context).doesNotHaveBean(VaultSecretProvider.class);
                });
    }

    @Test
    void shouldCreateBeansWithTokenAuth() {
        contextRunner
                .withPropertyValues(
                        "secrets.vault.enabled=true",
                        "secrets.vault.host=localhost",
                        "secrets.vault.port=8200",
                        "secrets.vault.scheme=http",
                        "secrets.vault.auth-method=TOKEN",
                        "secrets.vault.token=dev-token"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultTemplate.class);
                    assertThat(context).hasSingleBean(VaultSecretProvider.class);
                });
    }

    @Test
    void shouldCreateBeansWithAppRoleAuth() {
        contextRunner
                .withPropertyValues(
                        "secrets.vault.enabled=true",
                        "secrets.vault.host=localhost",
                        "secrets.vault.port=8200",
                        "secrets.vault.scheme=http",
                        "secrets.vault.auth-method=APPROLE",
                        "secrets.vault.app-role.role-id=test-role-id",
                        "secrets.vault.app-role.secret-id=test-secret-id"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultTemplate.class);
                    assertThat(context).hasSingleBean(VaultSecretProvider.class);
                });
    }

    @Test
    void shouldCreateBeansWithKubernetesAuth() {
        contextRunner
                .withPropertyValues(
                        "secrets.vault.enabled=true",
                        "secrets.vault.host=localhost",
                        "secrets.vault.port=8200",
                        "secrets.vault.scheme=http",
                        "secrets.vault.auth-method=KUBERNETES",
                        "secrets.vault.kubernetes.role=my-app-role"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultTemplate.class);
                    assertThat(context).hasSingleBean(VaultSecretProvider.class);
                });
    }

    @Test
    void shouldFailContextWhenTokenAuthHasNoToken() {
        contextRunner
                .withPropertyValues(
                        "secrets.vault.enabled=true",
                        "secrets.vault.host=localhost",
                        "secrets.vault.auth-method=TOKEN",
                        "secrets.vault.token="
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailContextWhenAppRoleHasNoRoleId() {
        contextRunner
                .withPropertyValues(
                        "secrets.vault.enabled=true",
                        "secrets.vault.host=localhost",
                        "secrets.vault.auth-method=APPROLE",
                        "secrets.vault.app-role.role-id=",
                        "secrets.vault.app-role.secret-id=test-secret"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldProviderHaveCorrectType() {
        contextRunner
                .withPropertyValues(
                        "secrets.vault.enabled=true",
                        "secrets.vault.host=localhost",
                        "secrets.vault.scheme=http",
                        "secrets.vault.auth-method=TOKEN",
                        "secrets.vault.token=dev-token"
                )
                .run(context -> {
                    VaultSecretProvider provider = context.getBean(VaultSecretProvider.class);
                    assertThat(provider.isEnabled()).isTrue();
                });
    }
}
