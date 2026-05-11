package io.github.timhwang777.unisecret.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.timhwang777.unisecret.config.AwsSecretProviderOptions;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

class AwsSecretProviderContractTest {

    @Test
    void binarySecretsFailAsUnsupportedAndNonRetryable() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder()
                        .secretBinary(SdkBytes.fromUtf8String("binary"))
                        .build());
        AwsSecretProvider provider = new AwsSecretProvider(
                client,
                new AwsSecretProviderOptions(true, "us-east-1", null)
        );

        assertThatThrownBy(() -> provider.getSecret("binary-secret"))
                .isInstanceOfSatisfying(SecretProviderException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.isRetryable()).isFalse())
                .hasMessageContaining("binary secrets are not supported");
    }
}
