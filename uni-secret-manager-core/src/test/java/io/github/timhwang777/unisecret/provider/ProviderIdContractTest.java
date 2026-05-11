package io.github.timhwang777.unisecret.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProviderIdContractTest {

    @Test
    void providerIdsAreTrimmedAndLowercased() {
        ProviderId providerId = ProviderId.of(" AWS ");

        assertThat(providerId.value()).isEqualTo("aws");
        assertThat(providerId).isEqualTo(ProviderId.AWS);
    }

    @Test
    void builtInProviderIdsAreConstants() {
        assertThat(ProviderId.AWS.value()).isEqualTo("aws");
        assertThat(ProviderId.GCP.value()).isEqualTo("gcp");
        assertThat(ProviderId.VAULT.value()).isEqualTo("vault");
        assertThat(ProviderId.LOCAL.value()).isEqualTo("local");
    }

    @Test
    void validCustomProviderIdsAreAllowed() {
        assertThat(ProviderId.of("onepassword").value()).isEqualTo("onepassword");
        assertThat(ProviderId.of("doppler-prod_1").value()).isEqualTo("doppler-prod_1");
        assertThat(ProviderId.of("azure.keyvault").value()).isEqualTo("azure.keyvault");
    }

    @Test
    void invalidProviderIdsAreRejected() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid(" ");
        assertInvalid("a");
        assertInvalid("-aws");
        assertInvalid("aws-");
        assertInvalid("aws local");
        assertInvalid("aws/local");
        assertInvalid("../aws");
        assertInvalid("aws..local");
        assertInvalid("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklm");
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> ProviderId.of(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
