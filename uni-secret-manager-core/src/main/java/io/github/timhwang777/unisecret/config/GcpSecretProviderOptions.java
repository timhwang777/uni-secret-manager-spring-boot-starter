package io.github.timhwang777.unisecret.config;

/**
 * Spring-free GCP provider options.
 *
 * @param enabled whether the provider participates in resolution
 * @param projectId GCP project containing secrets
 * @param defaultVersion version used by getSecret(key)
 */
public record GcpSecretProviderOptions(
        boolean enabled,
        String projectId,
        String defaultVersion
) {

    /**
     * Disabled default GCP options.
     *
     * @return disabled GCP provider options
     */
    public static GcpSecretProviderOptions disabled() {
        return new GcpSecretProviderOptions(false, null, "latest");
    }

    /**
     * Normalizes the default version.
     */
    public GcpSecretProviderOptions {
        if (defaultVersion == null || defaultVersion.isBlank()) {
            defaultVersion = "latest";
        }
    }
}
