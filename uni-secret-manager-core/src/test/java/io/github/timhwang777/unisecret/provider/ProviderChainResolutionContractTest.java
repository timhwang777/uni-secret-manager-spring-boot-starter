package io.github.timhwang777.unisecret.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretResolutionOptions;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;

class ProviderChainResolutionContractTest {

    @Test
    void providerNamesAreNormalizedWithoutMutatingConfiguration() {
        SecretResolutionOptions options = new SecretResolutionOptions(
                List.of("AWS", "Local"),
                true,
                null
        );
        SecretResolver resolver = new SecretResolver(List.of(), options, new SecretCache());

        assertThat(resolver.getProviderChain(null)).containsExactly("aws", "local");
        assertThat(options.providerOrder()).containsExactly("AWS", "Local");
    }

    @Test
    void invalidProviderNamesAreRejected() {
        SecretResolutionOptions options = new SecretResolutionOptions(
                List.of("aws", "../missing"),
                true,
                null
        );

        assertThatThrownBy(() -> new SecretResolver(List.of(), options, new SecretCache()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Invalid provider id");
    }
}
