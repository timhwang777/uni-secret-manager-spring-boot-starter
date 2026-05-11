package io.github.timhwang777.unisecret.config;

import java.net.URI;

/**
 * Spring-free AWS provider options.
 *
 * @param enabled whether the provider participates in resolution
 * @param region optional AWS region
 * @param endpoint optional endpoint override
 */
public record AwsSecretProviderOptions(
        boolean enabled,
        String region,
        URI endpoint
) {

    /**
     * Disabled default AWS options.
     *
     * @return disabled AWS provider options
     */
    public static AwsSecretProviderOptions disabled() {
        return new AwsSecretProviderOptions(false, null, null);
    }
}
