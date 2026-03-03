package io.github.timhwang777.unisecret.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import io.github.timhwang777.unisecret.provider.ProviderType;
import io.github.timhwang777.unisecret.provider.VaultSecretProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Version;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaultSecretProviderTest {

    @Mock
    private VaultTemplate vaultTemplate;

    @Mock
    private VaultKeyValueOperations kvOps;

    @Mock
    private VaultVersionedKeyValueOperations versionedOps;

    private SecretManagerProperties.Vault vaultProps;
    private VaultSecretProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        vaultProps = new SecretManagerProperties.Vault();
        vaultProps.setEnabled(true);
        vaultProps.setHost("localhost");
        vaultProps.setToken("test-token");
        vaultProps.setMount("secret");
        vaultProps.setKvVersion(2);
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);
    }

    // ==================== Provider contract ====================

    @Test
    void shouldReturnProviderType() {
        assertThat(provider.getProviderType()).isEqualTo(ProviderType.VAULT);
    }

    @Test
    void shouldBeEnabledWhenPropertyIsTrue() {
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void shouldBeDisabledWhenPropertyIsFalse() {
        vaultProps.setEnabled(false);
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);
        assertThat(provider.isEnabled()).isFalse();
    }

    // ==================== T007: KV v2 JSON-serialized map ====================

    @Test
    void shouldReturnJsonSerializedMapFromKvV2() {
        Map<String, Object> data = Map.of("username", "admin", "password", "s3cret");
        VaultResponse response = new VaultResponse();
        response.setData(data);

        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_2)).thenReturn(kvOps);
        when(kvOps.get("my-secret")).thenReturn(response);

        Optional<String> result = provider.getSecret("my-secret");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("username");
        assertThat(result.get()).contains("admin");
        assertThat(result.get()).contains("password");
        assertThat(result.get()).contains("s3cret");
    }

    @Test
    void shouldDelegateGetSecretNoVersionToGetSecretWithNullVersion() {
        Map<String, Object> data = Map.of("key", "value");
        VaultResponse response = new VaultResponse();
        response.setData(data);

        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_2)).thenReturn(kvOps);
        when(kvOps.get("my-secret")).thenReturn(response);

        Optional<String> result = provider.getSecret("my-secret");
        assertThat(result).isPresent();
    }

    // ==================== T008: KV v2 specific version ====================

    @Test
    void shouldRetrieveSpecificVersionFromKvV2() {
        Map<String, Object> data = Map.of("value", "v3-value");
        Versioned<Map<String, Object>> versioned = Versioned.create(data, Version.from(3));

        when(vaultTemplate.opsForVersionedKeyValue("secret")).thenReturn(versionedOps);
        doReturn(versioned).when(versionedOps).get(eq("my-secret"), eq(Version.from(3)));

        Optional<String> result = provider.getSecret("my-secret", "3");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("v3-value");
        verify(vaultTemplate).opsForVersionedKeyValue("secret");
        verify(versionedOps).get("my-secret", Version.from(3));
    }

    @Test
    void shouldTreatLatestAliasAsCurrentVersionForKvV2() {
        Map<String, Object> data = Map.of("value", "current-value");
        VaultResponse response = new VaultResponse();
        response.setData(data);

        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_2)).thenReturn(kvOps);
        when(kvOps.get("my-secret")).thenReturn(response);

        Optional<String> result = provider.getSecret("my-secret", "latest");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("current-value");
        verify(vaultTemplate).opsForKeyValue("secret", KeyValueBackend.KV_2);
        verify(vaultTemplate, never()).opsForVersionedKeyValue(anyString());
    }

    @Test
    void shouldThrowForInvalidVersionNumber() {
        assertThatThrownBy(() -> provider.getSecret("my-secret", "not-a-number"))
                .isInstanceOf(SecretProviderException.class)
                .hasMessageContaining("Invalid Vault version number");
    }

    // ==================== T009: KV v1 behavior ====================

    @Test
    void shouldUseKvV1BackendWhenKvVersionIsOne() {
        vaultProps.setKvVersion(1);
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        Map<String, Object> data = Map.of("key", "value");
        VaultResponse response = new VaultResponse();
        response.setData(data);

        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_1)).thenReturn(kvOps);
        when(kvOps.get("my-secret")).thenReturn(response);

        Optional<String> result = provider.getSecret("my-secret");

        assertThat(result).isPresent();
        verify(vaultTemplate).opsForKeyValue("secret", KeyValueBackend.KV_1);
    }

    @Test
    void shouldIgnoreVersionParameterForKvV1() {
        vaultProps.setKvVersion(1);
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        Map<String, Object> data = Map.of("key", "current-value");
        VaultResponse response = new VaultResponse();
        response.setData(data);

        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_1)).thenReturn(kvOps);
        when(kvOps.get("my-secret")).thenReturn(response);

        // Version "3" should be ignored for KV v1 - returns current value
        Optional<String> result = provider.getSecret("my-secret", "3");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("current-value");
        verify(vaultTemplate).opsForKeyValue("secret", KeyValueBackend.KV_1);
        verify(vaultTemplate, never()).opsForVersionedKeyValue(anyString());
    }

    // ==================== T010: Not-found returns Optional.empty() ====================

    @Test
    void shouldReturnEmptyWhenGetReturnsNull() {
        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_2)).thenReturn(kvOps);
        when(kvOps.get("missing-key")).thenReturn(null);

        Optional<String> result = provider.getSecret("missing-key");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenResponseDataIsNull() {
        VaultResponse response = new VaultResponse();
        response.setData(null);

        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_2)).thenReturn(kvOps);
        when(kvOps.get("empty-key")).thenReturn(response);

        Optional<String> result = provider.getSecret("empty-key");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenVersionedResponseIsNull() {
        when(vaultTemplate.opsForVersionedKeyValue("secret")).thenReturn(versionedOps);
        doReturn(null).when(versionedOps).get(eq("missing-key"), eq(Version.from(5)));

        Optional<String> result = provider.getSecret("missing-key", "5");

        assertThat(result).isEmpty();
    }

    // ==================== T011: Error mapping ====================

    @Test
    void shouldThrowNonRetryableExceptionOn403() {
        VaultException vaultEx = new VaultException("Forbidden",
                HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null));

        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_2)).thenReturn(kvOps);
        when(kvOps.get("secret-key")).thenThrow(vaultEx);

        assertThatThrownBy(() -> provider.getSecret("secret-key"))
                .isInstanceOf(SecretProviderException.class)
                .satisfies(e -> assertThat(((SecretProviderException) e).isRetryable()).isFalse());
    }

    @Test
    void shouldThrowRetryableExceptionOn503() {
        VaultException vaultEx = new VaultException("Service Unavailable",
                HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Sealed", null, null, null));

        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_2)).thenReturn(kvOps);
        when(kvOps.get("secret-key")).thenThrow(vaultEx);

        assertThatThrownBy(() -> provider.getSecret("secret-key"))
                .isInstanceOf(SecretProviderException.class)
                .satisfies(e -> assertThat(((SecretProviderException) e).isRetryable()).isTrue());
    }

    @Test
    void shouldThrowRetryableExceptionOnNetworkError() {
        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_2)).thenReturn(kvOps);
        when(kvOps.get("secret-key")).thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> provider.getSecret("secret-key"))
                .isInstanceOf(SecretProviderException.class)
                .satisfies(e -> assertThat(((SecretProviderException) e).isRetryable()).isTrue());
    }

    @Test
    void shouldThrowNonRetryableExceptionOn400() {
        VaultException vaultEx = new VaultException("Bad Request",
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_2)).thenReturn(kvOps);
        when(kvOps.get("secret-key")).thenThrow(vaultEx);

        assertThatThrownBy(() -> provider.getSecret("secret-key"))
                .isInstanceOf(SecretProviderException.class)
                .satisfies(e -> assertThat(((SecretProviderException) e).isRetryable()).isFalse());
    }

    @Test
    void shouldReturnEmptyOn404VaultException() {
        VaultException vaultEx = new VaultException("Not Found",
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        when(vaultTemplate.opsForKeyValue("secret", KeyValueBackend.KV_2)).thenReturn(kvOps);
        when(kvOps.get("secret-key")).thenThrow(vaultEx);

        Optional<String> result = provider.getSecret("secret-key");
        assertThat(result).isEmpty();
    }

    // ==================== T012: Destroyed/soft-deleted version ====================

    @Test
    void shouldReturnEmptyForVersionedResponseWithNullData() {
        Versioned<Map<String, Object>> versioned = Versioned.create(null, Version.from(2));

        when(vaultTemplate.opsForVersionedKeyValue("secret")).thenReturn(versionedOps);
        doReturn(versioned).when(versionedOps).get(eq("deleted-key"), eq(Version.from(2)));

        Optional<String> result = provider.getSecret("deleted-key", "2");

        assertThat(result).isEmpty();
    }

    // ==================== T015: validateConfiguration() auth validation ====================

    @Test
    void shouldPassValidationForTokenAuth() {
        assertThatCode(() -> provider.validateConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void shouldFailValidationWhenTokenIsBlankForTokenAuth() {
        vaultProps.setToken("");
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("token");
    }

    @Test
    void shouldFailValidationWhenTokenIsNullForTokenAuth() {
        vaultProps.setToken(null);
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("token");
    }

    @Test
    void shouldFailValidationWhenAppRoleRoleIdIsBlank() {
        vaultProps.setAuthMethod(SecretManagerProperties.Vault.AuthMethod.APPROLE);
        vaultProps.getAppRole().setRoleId("");
        vaultProps.getAppRole().setSecretId("secret-id");
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("role-id");
    }

    @Test
    void shouldFailValidationWhenAppRoleSecretIdIsBlank() {
        vaultProps.setAuthMethod(SecretManagerProperties.Vault.AuthMethod.APPROLE);
        vaultProps.getAppRole().setRoleId("role-id");
        vaultProps.getAppRole().setSecretId("");
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("secret-id");
    }

    @Test
    void shouldFailValidationWhenKubernetesRoleIsBlank() {
        vaultProps.setAuthMethod(SecretManagerProperties.Vault.AuthMethod.KUBERNETES);
        vaultProps.getKubernetes().setRole("");
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("role");
    }

    // ==================== T016: Valid configs pass validation ====================

    @Test
    void shouldPassValidationForAppRoleAuth() {
        vaultProps.setAuthMethod(SecretManagerProperties.Vault.AuthMethod.APPROLE);
        vaultProps.getAppRole().setRoleId("my-role-id");
        vaultProps.getAppRole().setSecretId("my-secret-id");
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatCode(() -> provider.validateConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void shouldPassValidationForKubernetesAuth() {
        vaultProps.setAuthMethod(SecretManagerProperties.Vault.AuthMethod.KUBERNETES);
        vaultProps.getKubernetes().setRole("my-k8s-role");
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatCode(() -> provider.validateConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void shouldFailValidationWhenHostIsBlank() {
        vaultProps.setHost("");
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("host");
    }

    @Test
    void shouldFailValidationForInvalidKvVersion() {
        vaultProps.setKvVersion(3);
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("kv-version");
    }

    // ==================== T017: SSL cert path validation ====================

    @Test
    void shouldFailValidationWhenSslCertPathDoesNotExist() {
        vaultProps.getSsl().setCaCertPath("/nonexistent/path/vault-ca.pem");
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatThrownBy(() -> provider.validateConfiguration())
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("CA cert file");
    }

    @Test
    void shouldPassValidationWhenSslCertPathExists() throws IOException {
        Path tempCert = Files.createTempFile("vault-ca", ".pem");
        try {
            vaultProps.getSsl().setCaCertPath(tempCert.toString());
            provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

            assertThatCode(() -> provider.validateConfiguration()).doesNotThrowAnyException();
        } finally {
            Files.deleteIfExists(tempCert);
        }
    }

    @Test
    void shouldPassValidationWhenSslCertPathIsNull() {
        vaultProps.getSsl().setCaCertPath(null);
        provider = new VaultSecretProvider(vaultTemplate, vaultProps, objectMapper);

        assertThatCode(() -> provider.validateConfiguration()).doesNotThrowAnyException();
    }
}
