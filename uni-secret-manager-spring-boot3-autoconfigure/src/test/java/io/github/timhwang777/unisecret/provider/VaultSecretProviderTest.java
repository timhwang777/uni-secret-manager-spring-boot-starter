package io.github.timhwang777.unisecret.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.timhwang777.unisecret.config.VaultSecretProviderOptions;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

class VaultSecretProviderTest {

    @Test
    void readsThroughSpringFreeOperationsAndSerializesData() {
        FakeOperations operations = new FakeOperations();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", "app");
        data.put("password", "secret");
        operations.data = Optional.of(data);
        VaultSecretProvider provider = new VaultSecretProvider(
                validOptions(),
                operations,
                new ObjectMapper()
        );

        assertThat(provider.getSecret("db", "7"))
                .contains("{\"username\":\"app\",\"password\":\"secret\"}");
        assertThat(operations.key).isEqualTo("db");
        assertThat(operations.version).isEqualTo("7");
    }

    @Test
    void emptyOperationsResultIsAbsentSecret() {
        FakeOperations operations = new FakeOperations();
        operations.data = Optional.empty();
        VaultSecretProvider provider = new VaultSecretProvider(
                validOptions(),
                operations,
                new ObjectMapper()
        );

        assertThat(provider.getSecret("missing")).isEmpty();
    }

    @Test
    void validatesSpringFreeOptions() {
        assertThatThrownBy(() -> providerWith(validOptions(" ", 2)).validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Vault host must not be blank");
        assertThatThrownBy(() -> providerWith(validOptions("localhost", 3)).validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Vault kv-version must be 1 or 2");
        assertThatThrownBy(() -> providerWith(tokenOptions(null)).validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Vault token must not be blank");
        assertThatThrownBy(() -> providerWith(appRoleOptions(null, "secret-id")).validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Vault app-role.role-id must not be blank");
    }

    @Test
    void optionsToStringRedactsSensitiveValues() {
        assertThat(appRoleOptions("role-id", "secret-id").toString())
                .contains("token=<redacted>")
                .contains("secretId=<redacted>")
                .doesNotContain("secret-token")
                .doesNotContain("secret-id");
    }

    private VaultSecretProvider providerWith(VaultSecretProviderOptions options) {
        FakeOperations operations = new FakeOperations();
        operations.data = Optional.empty();
        return new VaultSecretProvider(options, operations, new ObjectMapper());
    }

    private VaultSecretProviderOptions validOptions() {
        return validOptions("localhost", 2);
    }

    private VaultSecretProviderOptions validOptions(String host, int kvVersion) {
        return new VaultSecretProviderOptions(
                true,
                host,
                8200,
                "http",
                null,
                VaultSecretProviderOptions.AuthMethod.TOKEN,
                "secret-token",
                "secret",
                kvVersion,
                new VaultSecretProviderOptions.AppRole("role-id", "secret-id", "approle"),
                new VaultSecretProviderOptions.Kubernetes("role", "token-path", "kubernetes"),
                new VaultSecretProviderOptions.Ssl(null)
        );
    }

    private VaultSecretProviderOptions tokenOptions(String token) {
        VaultSecretProviderOptions options = validOptions();
        return new VaultSecretProviderOptions(
                options.enabled(),
                options.host(),
                options.port(),
                options.scheme(),
                options.namespace(),
                VaultSecretProviderOptions.AuthMethod.TOKEN,
                token,
                options.mount(),
                options.kvVersion(),
                options.appRole(),
                options.kubernetes(),
                options.ssl()
        );
    }

    private VaultSecretProviderOptions appRoleOptions(String roleId, String secretId) {
        VaultSecretProviderOptions options = validOptions();
        return new VaultSecretProviderOptions(
                options.enabled(),
                options.host(),
                options.port(),
                options.scheme(),
                options.namespace(),
                VaultSecretProviderOptions.AuthMethod.APPROLE,
                options.token(),
                options.mount(),
                options.kvVersion(),
                new VaultSecretProviderOptions.AppRole(roleId, secretId, "approle"),
                options.kubernetes(),
                options.ssl()
        );
    }

    private static final class FakeOperations implements VaultSecretOperations {

        private Optional<Map<String, Object>> data = Optional.empty();
        private String key;
        private String version;

        @Override
        public Optional<Map<String, Object>> read(String key, String version) {
            this.key = key;
            this.version = version;
            return data;
        }
    }
}
