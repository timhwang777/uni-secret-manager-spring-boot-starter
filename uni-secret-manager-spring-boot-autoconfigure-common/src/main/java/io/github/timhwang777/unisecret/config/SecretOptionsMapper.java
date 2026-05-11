package io.github.timhwang777.unisecret.config;

import java.net.URI;

/**
 * Maps Spring Boot configuration properties into Spring-free core options.
 */
final class SecretOptionsMapper {

    private SecretOptionsMapper() {
    }

    static SecretResolutionOptions resolution(SecretManagerProperties properties) {
        return new SecretResolutionOptions(
                properties.getProviderOrder(),
                properties.isFailOnMissing(),
                retry(properties.getRetry())
        );
    }

    static SecretCacheOptions cache(SecretManagerProperties.Cache properties) {
        return new SecretCacheOptions(
                properties.isEnabled(),
                properties.getTtl(),
                properties.getMaxSize()
        );
    }

    static AwsSecretProviderOptions aws(SecretManagerProperties.Aws properties) {
        return new AwsSecretProviderOptions(
                properties.isEnabled(),
                properties.getRegion(),
                uri(properties.getEndpoint())
        );
    }

    static GcpSecretProviderOptions gcp(SecretManagerProperties.Gcp properties) {
        return new GcpSecretProviderOptions(
                properties.isEnabled(),
                properties.getProjectId(),
                properties.getDefaultVersion()
        );
    }

    static LocalSecretProviderOptions local(SecretManagerProperties.Local properties) {
        return new LocalSecretProviderOptions(
                properties.isEnabled(),
                properties.getSecrets()
        );
    }

    static VaultSecretProviderOptions vault(SecretManagerProperties.Vault properties) {
        return new VaultSecretProviderOptions(
                properties.isEnabled(),
                properties.getHost(),
                properties.getPort(),
                properties.getScheme(),
                properties.getNamespace(),
                vaultAuthMethod(properties.getAuthMethod()),
                properties.getToken(),
                properties.getMount(),
                properties.getKvVersion(),
                vaultAppRole(properties.getAppRole()),
                vaultKubernetes(properties.getKubernetes()),
                vaultSsl(properties.getSsl())
        );
    }

    private static RetryOptions retry(SecretManagerProperties.Retry properties) {
        return new RetryOptions(
                properties.getMaxAttempts(),
                properties.getInitialDelay(),
                properties.getMultiplier(),
                properties.getMaxDelay()
        );
    }

    private static URI uri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return URI.create(value);
    }

    private static VaultSecretProviderOptions.AuthMethod vaultAuthMethod(
            SecretManagerProperties.Vault.AuthMethod authMethod
    ) {
        if (authMethod == null) {
            return VaultSecretProviderOptions.AuthMethod.TOKEN;
        }
        return VaultSecretProviderOptions.AuthMethod.valueOf(authMethod.name());
    }

    private static VaultSecretProviderOptions.AppRole vaultAppRole(
            SecretManagerProperties.Vault.AppRole appRole
    ) {
        return new VaultSecretProviderOptions.AppRole(
                appRole.getRoleId(),
                appRole.getSecretId(),
                appRole.getPath()
        );
    }

    private static VaultSecretProviderOptions.Kubernetes vaultKubernetes(
            SecretManagerProperties.Vault.Kubernetes kubernetes
    ) {
        return new VaultSecretProviderOptions.Kubernetes(
                kubernetes.getRole(),
                kubernetes.getServiceAccountTokenPath(),
                kubernetes.getPath()
        );
    }

    private static VaultSecretProviderOptions.Ssl vaultSsl(SecretManagerProperties.Vault.Ssl ssl) {
        return new VaultSecretProviderOptions.Ssl(ssl.getCaCertPath());
    }
}
