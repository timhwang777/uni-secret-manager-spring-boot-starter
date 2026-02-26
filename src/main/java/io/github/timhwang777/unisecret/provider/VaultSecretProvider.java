package io.github.timhwang777.unisecret.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Version;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.io.File;
import java.util.Map;
import java.util.Optional;

/**
 * HashiCorp Vault implementation of the SecretProvider interface.
 *
 * <p>Reads secrets from Vault's KV v1 or v2 secrets engine using Spring Vault's
 * {@link VaultTemplate}. The full KV data map is serialized as a JSON string,
 * consistent with the behavior expected by the existing {@code JsonFieldExtractor}.</p>
 *
 * <h2>KV Engine Support</h2>
 * <ul>
 *   <li>KV v2 (default): supports versioned reads via {@code getSecret(key, version)}</li>
 *   <li>KV v1: version parameter is silently ignored; always returns the current value</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <ul>
 *   <li>Secret not found (null response or 404) → {@code Optional.empty()}</li>
 *   <li>Permission denied (403), bad request (400) → non-retryable exception</li>
 *   <li>Vault sealed (503), server error (500) → retryable exception</li>
 *   <li>Network error → retryable exception</li>
 * </ul>
 */
@Slf4j
public class VaultSecretProvider implements SecretProvider {

    private final VaultTemplate vaultTemplate;
    private final SecretManagerProperties.Vault properties;
    private final ObjectMapper objectMapper;

    public VaultSecretProvider(VaultTemplate vaultTemplate,
                                SecretManagerProperties.Vault properties,
                                ObjectMapper objectMapper) {
        this.vaultTemplate = vaultTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<String> getSecret(String key) {
        return getSecret(key, null);
    }

    @Override
    public Optional<String> getSecret(String key, String version) {
        log.debug("Retrieving secret '{}' (version={}, kv-v{}) from Vault", key, version, properties.getKvVersion());
        try {
            return fetchSecret(key, version);
        } catch (JsonProcessingException e) {
            throw new SecretProviderException("Failed to serialize secret '" + key + "'", e, false);
        } catch (VaultException e) {
            return handleVaultException(key, e);
        } catch (ResourceAccessException e) {
            log.error("Network error retrieving secret '{}' from Vault: {}", key, e.getMessage());
            throw new SecretProviderException(
                    "Network error retrieving secret '" + key + "' from Vault", e, true);
        }
    }

    private Optional<String> fetchSecret(String key, String version) throws JsonProcessingException {
        String mount = properties.getMount();
        Map<String, Object> data;

        if (properties.getKvVersion() == 2 && version != null && !version.isBlank()) {
            data = fetchVersionedSecret(key, version, mount);
        } else {
            data = fetchLatestSecret(key, version, mount);
        }

        if (data == null) {
            log.debug("Secret '{}' not found in Vault (no data)", key);
            return Optional.empty();
        }

        log.debug("Successfully retrieved secret '{}' from Vault", key);
        return Optional.of(objectMapper.writeValueAsString(data));
    }

    private Map<String, Object> fetchVersionedSecret(String key, String version, String mount) {
        int versionNumber;
        try {
            versionNumber = Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new SecretProviderException("Invalid Vault version number: " + version, e, false);
        }

        VaultVersionedKeyValueOperations ops = vaultTemplate.opsForVersionedKeyValue(mount);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Versioned raw = ops.get(key, Version.from(versionNumber));

        if (raw == null || raw.getData() == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> versionedData = (Map<String, Object>) raw.getData();
        return versionedData;
    }

    private Map<String, Object> fetchLatestSecret(String key, String version, String mount) {
        if (properties.getKvVersion() == 1 && version != null && !version.isBlank()) {
            log.debug("Version '{}' ignored for KV v1 engine (versioning not supported)", version);
        }

        KeyValueBackend backend = properties.getKvVersion() == 2
                ? KeyValueBackend.KV_2 : KeyValueBackend.KV_1;
        VaultKeyValueOperations ops = vaultTemplate.opsForKeyValue(mount, backend);
        VaultResponse response = ops.get(key);

        if (response == null) {
            return null;
        }
        return response.getData();
    }

    private Optional<String> handleVaultException(String key, VaultException e) {
        Throwable cause = e.getCause();
        if (cause instanceof HttpStatusCodeException httpEx) {
            int status = httpEx.getStatusCode().value();
            if (status == 404) {
                log.debug("Secret '{}' not found in Vault (HTTP 404)", key);
                return Optional.empty();
            }
            boolean retryable = status >= 500;
            String msg = String.format("Vault HTTP %d error for secret '%s': %s", status, key, e.getMessage());
            log.error(msg);
            throw new SecretProviderException(msg, e, retryable);
        }
        log.error("Vault error retrieving secret '{}': {}", key, e.getMessage());
        throw new SecretProviderException(
                "Vault error retrieving secret '" + key + "'", e, true);
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.VAULT;
    }

    @Override
    public void validateConfiguration() {
        validateHost();
        validateKvVersion();
        validateAuthConfiguration();
        validateSslConfiguration();
        log.info("Vault provider validated (host={}:{}, auth={}, kv-v{})",
                properties.getHost(), properties.getPort(),
                properties.getAuthMethod(), properties.getKvVersion());
    }

    private void validateHost() {
        if (properties.getHost() == null || properties.getHost().isBlank()) {
            throw new SecretConfigurationException("Vault host must not be blank");
        }
    }

    private void validateKvVersion() {
        int kv = properties.getKvVersion();
        if (kv != 1 && kv != 2) {
            throw new SecretConfigurationException(
                    "Vault kv-version must be 1 or 2, got: " + kv);
        }
    }

    private void validateAuthConfiguration() {
        SecretManagerProperties.Vault.AuthMethod authMethod = properties.getAuthMethod();
        switch (authMethod) {
            case TOKEN:
                validateTokenAuth();
                break;
            case APPROLE:
                validateAppRoleAuth();
                break;
            case KUBERNETES:
                validateKubernetesAuth();
                break;
            default:
                throw new SecretConfigurationException(
                        "Unsupported Vault auth method: " + authMethod);
        }
    }

    private void validateTokenAuth() {
        if (properties.getToken() == null || properties.getToken().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault token must not be blank for TOKEN auth");
        }
    }

    private void validateAppRoleAuth() {
        SecretManagerProperties.Vault.AppRole appRole = properties.getAppRole();
        if (appRole.getRoleId() == null || appRole.getRoleId().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault app-role.role-id must not be blank for APPROLE auth");
        }
        if (appRole.getSecretId() == null || appRole.getSecretId().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault app-role.secret-id must not be blank for APPROLE auth");
        }
    }

    private void validateKubernetesAuth() {
        SecretManagerProperties.Vault.Kubernetes kubernetes = properties.getKubernetes();
        if (kubernetes.getRole() == null || kubernetes.getRole().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault kubernetes.role must not be blank for KUBERNETES auth");
        }
    }

    private void validateSslConfiguration() {
        String caCertPath = properties.getSsl().getCaCertPath();
        if (caCertPath != null && !caCertPath.isBlank()) {
            File certFile = new File(caCertPath);
            if (!certFile.exists() || !certFile.canRead()) {
                throw new SecretConfigurationException(
                        "Vault SSL CA cert file not found or not readable: " + caCertPath);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }
}
