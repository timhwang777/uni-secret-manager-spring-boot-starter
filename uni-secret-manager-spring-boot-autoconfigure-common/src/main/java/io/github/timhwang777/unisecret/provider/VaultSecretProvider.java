package io.github.timhwang777.unisecret.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.timhwang777.unisecret.config.VaultSecretProviderOptions;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Map;
import java.util.Optional;

/**
 * HashiCorp Vault implementation of the SecretProvider interface.
 *
 * <p>Reads secrets from Vault's KV v1 or v2 secrets engine through a package-private
 * operations adapter. The full KV data map is serialized as a JSON string,
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

    private final VaultSecretProviderOptions options;
    private final VaultSecretOperations operations;
    private final ObjectMapper objectMapper;

    VaultSecretProvider(
            VaultSecretProviderOptions options,
            VaultSecretOperations operations,
            ObjectMapper objectMapper
    ) {
        this.options = options == null ? VaultSecretProviderOptions.disabled() : options;
        this.operations = operations;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * Creates a provider backed by the Spring Vault adapter without exposing Spring Vault types.
     *
     * @param vaultTemplate Spring Vault template instance
     * @param options provider options
     * @param objectMapper object mapper
     * @return provider backed by Spring Vault operations
     */
    public static VaultSecretProvider springVault(
            Object vaultTemplate,
            VaultSecretProviderOptions options,
            ObjectMapper objectMapper
    ) {
        VaultSecretProviderOptions safeOptions =
                options == null ? VaultSecretProviderOptions.disabled() : options;
        return new VaultSecretProvider(
                safeOptions,
                new SpringVaultSecretOperations(vaultTemplate, safeOptions),
                objectMapper
        );
    }

    @Override
    public Optional<String> getSecret(String key) {
        return getSecret(key, null);
    }

    @Override
    public Optional<String> getSecret(String key, String version) {
        log.debug("Retrieving secret '{}' (version={}, kv-v{}) from Vault", key, version, options.kvVersion());
        try {
            Optional<Map<String, Object>> data = operations.read(key, version);
            if (data.isEmpty()) {
                return Optional.empty();
            }
            log.debug("Successfully retrieved secret '{}' from Vault", key);
            return Optional.of(objectMapper.writeValueAsString(data.get()));
        } catch (JsonProcessingException e) {
            throw new SecretProviderException("Failed to serialize secret '" + key + "'", e, false);
        }
    }

    @Override
    public ProviderId getProviderId() {
        return ProviderId.VAULT;
    }

    @Override
    public void validateConfiguration() {
        validateHost();
        validateKvVersion();
        validateAuthConfiguration();
        validateSslConfiguration();
        log.info("Vault provider validated (host={}:{}, auth={}, kv-v{})",
                options.host(), options.port(),
                options.authMethod(), options.kvVersion());
    }

    private void validateHost() {
        if (options.host() == null || options.host().isBlank()) {
            throw new SecretConfigurationException("Vault host must not be blank");
        }
    }

    private void validateKvVersion() {
        int kv = options.kvVersion();
        if (kv != 1 && kv != 2) {
            throw new SecretConfigurationException(
                    "Vault kv-version must be 1 or 2, got: " + kv);
        }
    }

    private void validateAuthConfiguration() {
        VaultSecretProviderOptions.AuthMethod authMethod = options.authMethod();
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
        if (options.token() == null || options.token().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault token must not be blank for TOKEN auth");
        }
    }

    private void validateAppRoleAuth() {
        VaultSecretProviderOptions.AppRole appRole = options.appRole();
        if (appRole.roleId() == null || appRole.roleId().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault app-role.role-id must not be blank for APPROLE auth");
        }
        if (appRole.secretId() == null || appRole.secretId().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault app-role.secret-id must not be blank for APPROLE auth");
        }
    }

    private void validateKubernetesAuth() {
        VaultSecretProviderOptions.Kubernetes kubernetes = options.kubernetes();
        if (kubernetes.role() == null || kubernetes.role().isBlank()) {
            throw new SecretConfigurationException(
                    "Vault kubernetes.role must not be blank for KUBERNETES auth");
        }
    }

    private void validateSslConfiguration() {
        if (options.hasCaCertPath()) {
            File certFile = options.caCertFile();
            if (!certFile.exists() || !certFile.canRead()) {
                throw new SecretConfigurationException(
                        "Vault SSL CA cert file not found or not readable: "
                                + options.ssl().caCertPath());
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return options.enabled();
    }
}
