package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.provider.ProviderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProviderTypeTest {

    @Test
    void shouldConvertFromConfigValue() {
        assertThat(ProviderType.fromConfigValue("aws")).isEqualTo(ProviderType.AWS);
        assertThat(ProviderType.fromConfigValue("gcp")).isEqualTo(ProviderType.GCP);
        assertThat(ProviderType.fromConfigValue("vault")).isEqualTo(ProviderType.VAULT);
        assertThat(ProviderType.fromConfigValue("local")).isEqualTo(ProviderType.LOCAL);
    }

    @Test
    void shouldConvertFromConfigValueCaseInsensitive() {
        assertThat(ProviderType.fromConfigValue("AWS")).isEqualTo(ProviderType.AWS);
        assertThat(ProviderType.fromConfigValue("GCP")).isEqualTo(ProviderType.GCP);
        assertThat(ProviderType.fromConfigValue("VAULT")).isEqualTo(ProviderType.VAULT);
        assertThat(ProviderType.fromConfigValue("LOCAL")).isEqualTo(ProviderType.LOCAL);
    }

    @Test
    void shouldThrowExceptionForUnknownConfigValue() {
        assertThatThrownBy(() -> ProviderType.fromConfigValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown provider type: unknown");
    }

    @Test
    void shouldReturnCorrectConfigValue() {
        assertThat(ProviderType.AWS.getConfigValue()).isEqualTo("aws");
        assertThat(ProviderType.GCP.getConfigValue()).isEqualTo("gcp");
        assertThat(ProviderType.VAULT.getConfigValue()).isEqualTo("vault");
        assertThat(ProviderType.LOCAL.getConfigValue()).isEqualTo("local");
    }
}
