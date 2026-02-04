package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import io.github.timhwang777.unisecret.provider.AwsSecretProvider;
import io.github.timhwang777.unisecret.provider.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwsSecretProviderTest {

    @Mock
    private SecretsManagerClient secretsManagerClient;

    private SecretManagerProperties.Aws awsProperties;
    private AwsSecretProvider provider;

    @BeforeEach
    void setUp() {
        awsProperties = new SecretManagerProperties.Aws();
        awsProperties.setEnabled(true);
        awsProperties.setRegion("us-east-1");

        provider = new AwsSecretProvider(secretsManagerClient, awsProperties);
    }

    @Test
    void shouldReturnProviderType() {
        assertThat(provider.getProviderType()).isEqualTo(ProviderType.AWS);
    }

    @Test
    void shouldBeEnabled() {
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void shouldBeDisabled() {
        awsProperties.setEnabled(false);
        provider = new AwsSecretProvider(secretsManagerClient, awsProperties);

        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void shouldRetrieveSecret() {
        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString("secret-value")
                .build();

        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenReturn(response);

        Optional<String> result = provider.getSecret("test-secret");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("secret-value");
    }

    @Test
    void shouldRetrieveSecretWithVersion() {
        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString("secret-value")
                .build();

        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenReturn(response);

        Optional<String> result = provider.getSecret("test-secret", "v1");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("secret-value");

        verify(secretsManagerClient).getSecretValue(argThat((GetSecretValueRequest request) ->
                request.secretId().equals("test-secret") &&
                request.versionId().equals("v1")
        ));
    }

    @Test
    void shouldMapLatestToAwsCurrent() {
        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString("secret-value")
                .build();

        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenReturn(response);

        provider.getSecret("test-secret", "latest");

        verify(secretsManagerClient).getSecretValue(argThat((GetSecretValueRequest request) ->
                request.secretId().equals("test-secret") &&
                request.versionStage().equals("AWSCURRENT")
        ));
    }

    @Test
    void shouldMapPreviousToAwsPrevious() {
        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString("secret-value")
                .build();

        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenReturn(response);

        provider.getSecret("test-secret", "previous");

        verify(secretsManagerClient).getSecretValue(argThat((GetSecretValueRequest request) ->
                request.secretId().equals("test-secret") &&
                request.versionStage().equals("AWSPREVIOUS")
        ));
    }

    @Test
    void shouldReturnEmptyWhenSecretNotFound() {
        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenThrow(ResourceNotFoundException.builder().message("Secret not found").build());

        Optional<String> result = provider.getSecret("non-existent-secret");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowProviderExceptionOnAccessDenied() {
        AwsServiceException exception = AwsServiceException.builder()
                .message("Access denied")
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("AccessDeniedException")
                        .errorMessage("Access denied")
                        .build())
                .build();

        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenThrow(exception);

        assertThatThrownBy(() -> provider.getSecret("test-secret"))
                .isInstanceOf(SecretProviderException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void shouldThrowProviderExceptionOnDecryptionFailure() {
        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenThrow(DecryptionFailureException.builder().message("Decryption failed").build());

        assertThatThrownBy(() -> provider.getSecret("test-secret"))
                .isInstanceOf(SecretProviderException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void shouldThrowProviderExceptionOnInternalServiceError() {
        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenThrow(InternalServiceErrorException.builder().message("Internal error").build());

        assertThatThrownBy(() -> provider.getSecret("test-secret"))
                .isInstanceOf(SecretProviderException.class)
                .hasMessageContaining("Internal error");
    }

    @Test
    void shouldValidateConfigurationSuccessfully() {
        assertThatCode(() -> provider.validateConfiguration())
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleNullSecretString() {
        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString(null)
                .build();

        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenReturn(response);

        Optional<String> result = provider.getSecret("test-secret");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleEmptySecretString() {
        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString("")
                .build();

        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenReturn(response);

        Optional<String> result = provider.getSecret("test-secret");

        assertThat(result).isEmpty();
    }
}
