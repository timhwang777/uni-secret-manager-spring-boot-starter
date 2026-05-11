package io.github.timhwang777.unisecret.provider;

import io.github.timhwang777.unisecret.config.VaultSecretProviderOptions;
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

import java.util.Map;
import java.util.Optional;

@Slf4j
final class SpringVaultSecretOperations implements VaultSecretOperations {

    private final VaultTemplate vaultTemplate;
    private final VaultSecretProviderOptions options;

    SpringVaultSecretOperations(Object vaultTemplate, VaultSecretProviderOptions options) {
        this.vaultTemplate = (VaultTemplate) vaultTemplate;
        this.options = options;
    }

    @Override
    public Optional<Map<String, Object>> read(String key, String version) {
        try {
            Map<String, Object> data = readData(key, version);
            if (data == null) {
                log.debug("Secret '{}' not found in Vault (no data)", key);
                return Optional.empty();
            }
            return Optional.of(data);
        } catch (VaultException e) {
            return handleVaultException(key, e);
        } catch (ResourceAccessException e) {
            log.error("Network error retrieving secret '{}' from Vault: {}", key, e.getMessage());
            throw new SecretProviderException(
                    "Network error retrieving secret '" + key + "' from Vault", e, true);
        }
    }

    private Map<String, Object> readData(String key, String version) {
        if (options.kvVersion() == 2 && hasExplicitVersion(version)) {
            return readVersionedSecret(key, version);
        }
        return readLatestSecret(key, version);
    }

    private boolean hasExplicitVersion(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        return !"latest".equalsIgnoreCase(version.trim());
    }

    private Map<String, Object> readVersionedSecret(String key, String version) {
        int versionNumber;
        try {
            versionNumber = Integer.parseInt(version.trim());
        } catch (NumberFormatException e) {
            throw new SecretProviderException("Invalid Vault version number: " + version, e, false);
        }

        VaultVersionedKeyValueOperations operations =
                vaultTemplate.opsForVersionedKeyValue(options.mount());
        @SuppressWarnings({"rawtypes", "unchecked"})
        Versioned raw = operations.get(key, Version.from(versionNumber));

        if (raw == null || raw.getData() == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) raw.getData();
        return data;
    }

    private Map<String, Object> readLatestSecret(String key, String version) {
        if (options.kvVersion() == 1 && version != null && !version.isBlank()) {
            log.debug("Version '{}' ignored for KV v1 engine (versioning not supported)", version);
        }

        KeyValueBackend backend = options.kvVersion() == 2
                ? KeyValueBackend.KV_2 : KeyValueBackend.KV_1;
        VaultKeyValueOperations operations = vaultTemplate.opsForKeyValue(options.mount(), backend);
        VaultResponse response = operations.get(key);

        if (response == null) {
            return null;
        }
        return response.getData();
    }

    private Optional<Map<String, Object>> handleVaultException(String key, VaultException e) {
        Throwable cause = e.getCause();
        if (cause instanceof HttpStatusCodeException httpException) {
            int status = httpException.getStatusCode().value();
            if (status == 404) {
                log.debug("Secret '{}' not found in Vault (HTTP 404)", key);
                return Optional.empty();
            }
            boolean retryable = status >= 500;
            String message = String.format(
                    "Vault HTTP %d error for secret '%s': %s",
                    status,
                    key,
                    e.getMessage()
            );
            log.error(message);
            throw new SecretProviderException(message, e, retryable);
        }
        log.error("Vault error retrieving secret '{}': {}", key, e.getMessage());
        throw new SecretProviderException(
                "Vault error retrieving secret '" + key + "'", e, true);
    }
}
