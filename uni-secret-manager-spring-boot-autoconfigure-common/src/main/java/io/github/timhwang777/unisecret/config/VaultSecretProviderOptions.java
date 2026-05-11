package io.github.timhwang777.unisecret.config;

import java.io.File;

/**
 * Spring-free Vault provider options used by the UniSecret Vault adapter.
 *
 * @param enabled whether the provider participates in resolution
 * @param host Vault host
 * @param port Vault port
 * @param scheme Vault URI scheme
 * @param namespace Vault Enterprise namespace
 * @param authMethod authentication method
 * @param token token used for TOKEN authentication
 * @param mount KV mount path
 * @param kvVersion KV engine version, 1 or 2
 * @param appRole AppRole authentication options
 * @param kubernetes Kubernetes authentication options
 * @param ssl SSL options
 */
public record VaultSecretProviderOptions(
        boolean enabled,
        String host,
        int port,
        String scheme,
        String namespace,
        AuthMethod authMethod,
        String token,
        String mount,
        int kvVersion,
        AppRole appRole,
        Kubernetes kubernetes,
        Ssl ssl
) {

    private static final String DEFAULT_KUBERNETES_TOKEN_PATH =
            "/var/run/secrets/kubernetes.io/serviceaccount/token";

    /**
     * Vault authentication methods supported by the adapter.
     */
    public enum AuthMethod {
        TOKEN, APPROLE, KUBERNETES
    }

    /**
     * AppRole authentication options.
     *
     * @param roleId AppRole role id
     * @param secretId AppRole secret id
     * @param path AppRole auth mount path
     */
    public record AppRole(String roleId, String secretId, String path) {

        public AppRole {
            if (path == null || path.isBlank()) {
                path = "approle";
            }
        }

        @Override
        public String toString() {
            return "AppRole[roleId=" + roleId + ", secretId=<redacted>, path=" + path + "]";
        }
    }

    /**
     * Kubernetes authentication options.
     *
     * @param role Kubernetes auth role
     * @param serviceAccountTokenPath service account token file path
     * @param path Kubernetes auth mount path
     */
    public record Kubernetes(String role, String serviceAccountTokenPath, String path) {

        public Kubernetes {
            if (serviceAccountTokenPath == null || serviceAccountTokenPath.isBlank()) {
                serviceAccountTokenPath = DEFAULT_KUBERNETES_TOKEN_PATH;
            }
            if (path == null || path.isBlank()) {
                path = "kubernetes";
            }
        }
    }

    /**
     * SSL options.
     *
     * @param caCertPath path to a PEM CA certificate
     */
    public record Ssl(String caCertPath) {
    }

    /**
     * Disabled Vault options.
     *
     * @return disabled options
     */
    public static VaultSecretProviderOptions disabled() {
        return new VaultSecretProviderOptions(
                false,
                "localhost",
                8200,
                "https",
                null,
                AuthMethod.TOKEN,
                null,
                "secret",
                2,
                new AppRole(null, null, "approle"),
                new Kubernetes(null, DEFAULT_KUBERNETES_TOKEN_PATH, "kubernetes"),
                new Ssl(null)
        );
    }

    /**
     * Normalizes nullable option groups and defaults.
     */
    public VaultSecretProviderOptions {
        if (host == null) {
            host = "localhost";
        }
        if (scheme == null || scheme.isBlank()) {
            scheme = "https";
        }
        if (authMethod == null) {
            authMethod = AuthMethod.TOKEN;
        }
        if (mount == null || mount.isBlank()) {
            mount = "secret";
        }
        if (appRole == null) {
            appRole = new AppRole(null, null, "approle");
        }
        if (kubernetes == null) {
            kubernetes = new Kubernetes(null, DEFAULT_KUBERNETES_TOKEN_PATH, "kubernetes");
        }
        if (ssl == null) {
            ssl = new Ssl(null);
        }
    }

    /**
     * Returns whether a CA certificate path was configured.
     *
     * @return true when a CA certificate path is configured
     */
    public boolean hasCaCertPath() {
        return ssl.caCertPath() != null && !ssl.caCertPath().isBlank();
    }

    /**
     * Returns the configured CA certificate file.
     *
     * @return configured CA certificate file
     */
    public File caCertFile() {
        return new File(ssl.caCertPath());
    }

    @Override
    public String toString() {
        return "VaultSecretProviderOptions[enabled="
                + enabled
                + ", host="
                + host
                + ", port="
                + port
                + ", scheme="
                + scheme
                + ", namespace="
                + namespace
                + ", authMethod="
                + authMethod
                + ", token=<redacted>, mount="
                + mount
                + ", kvVersion="
                + kvVersion
                + ", appRole="
                + appRole
                + ", kubernetes="
                + kubernetes
                + ", ssl="
                + ssl
                + "]";
    }
}
