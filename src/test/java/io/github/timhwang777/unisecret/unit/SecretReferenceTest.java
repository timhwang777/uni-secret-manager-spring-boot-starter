package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.provider.SecretReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SecretReferenceTest {

    @Test
    void shouldValidateKeyNotNull() {
        SecretReference reference = SecretReference.builder()
                .key(null)
                .build();

        assertThatThrownBy(reference::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Secret key must not be null or blank");
    }

    @Test
    void shouldValidateKeyNotBlank() {
        SecretReference reference = SecretReference.builder()
                .key("   ")
                .build();

        assertThatThrownBy(reference::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Secret key must not be null or blank");
    }

    @Test
    void shouldDefaultVersionToLatest() {
        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .build();

        assertThat(reference.getVersion()).isEqualTo("latest");
    }

    @Test
    void shouldUseSingleProviderOverride() {
        List<String> globalOrder = List.of("aws", "gcp", "local");

        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .provider("gcp")
                .build();

        List<String> effectiveChain = reference.getEffectiveProviderChain(globalOrder);

        assertThat(effectiveChain).containsExactly("gcp");
    }

    @Test
    void shouldUseCustomProvidersChain() {
        List<String> globalOrder = List.of("aws", "gcp", "local");

        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .providers(List.of("local", "aws"))
                .build();

        List<String> effectiveChain = reference.getEffectiveProviderChain(globalOrder);

        assertThat(effectiveChain).containsExactly("local", "aws");
    }

    @Test
    void shouldUseGlobalOrderWhenNoOverride() {
        List<String> globalOrder = List.of("aws", "gcp", "local");

        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .build();

        List<String> effectiveChain = reference.getEffectiveProviderChain(globalOrder);

        assertThat(effectiveChain).containsExactly("aws", "gcp", "local");
    }

    @Test
    void shouldPreferSingleProviderOverCustomChain() {
        List<String> globalOrder = List.of("aws", "gcp", "local");

        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .provider("gcp")
                .providers(List.of("local", "aws"))
                .build();

        List<String> effectiveChain = reference.getEffectiveProviderChain(globalOrder);

        assertThat(effectiveChain).containsExactly("gcp");
    }
}
