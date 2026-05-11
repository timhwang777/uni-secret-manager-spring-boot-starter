package io.github.timhwang777.unisecret.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import io.github.timhwang777.unisecret.config.GcpSecretProviderOptions;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import org.junit.jupiter.api.Test;

class GcpSecretProviderContractTest {

    @Test
    void projectIdIsRequiredWhenEnabled() {
        GcpSecretProvider provider = new GcpSecretProvider(
                mock(SecretManagerServiceClient.class),
                new GcpSecretProviderOptions(true, null, "latest")
        );

        assertThatThrownBy(provider::validateConfiguration)
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("project-id is required");
    }
}
