package io.github.timhwang777.unisecret.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

class SecretReferenceContractTest {

    @Test
    void blankKeysAreRejected() {
        SecretReference reference = SecretReference.builder().key(" ").build();

        assertThatThrownBy(reference::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @Test
    void callerProvidedProviderNamesArePreserved() {
        SecretReference reference = SecretReference.builder()
                .key("db")
                .provider("AWS")
                .providers(List.of("LOCAL"))
                .build();

        assertThat(reference.getProvider()).isEqualTo("AWS");
        assertThat(reference.getProviders()).containsExactly("LOCAL");
    }

    @Test
    void explicitProviderCustomChainAndGlobalChainAreDistinct() {
        SecretReference explicit = SecretReference.builder()
                .key("db")
                .provider("aws")
                .providers(List.of("local"))
                .build();
        SecretReference custom = SecretReference.builder()
                .key("db")
                .providers(List.of("local", "aws"))
                .build();
        SecretReference global = SecretReference.builder()
                .key("db")
                .build();

        assertThat(explicit.getEffectiveProviderChain(List.of("gcp"))).containsExactly("aws");
        assertThat(custom.getEffectiveProviderChain(List.of("gcp"))).containsExactly("local", "aws");
        assertThat(global.getEffectiveProviderChain(List.of("gcp"))).containsExactly("gcp");
    }
}
