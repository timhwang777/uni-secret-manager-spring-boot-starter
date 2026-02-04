package io.github.timhwang777.unisecret.unit;

import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import io.github.timhwang777.unisecret.provider.GcpSecretProvider;
import io.github.timhwang777.unisecret.provider.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class GcpSecretProviderTest {

    @Mock
    private SecretManagerServiceClient client;

    private SecretManagerProperties.Gcp gcpProperties;
    private GcpSecretProvider provider;

    @BeforeEach
    void setUp() {
        gcpProperties = new SecretManagerProperties.Gcp();
        gcpProperties.setEnabled(true);
        gcpProperties.setProjectId("test-project");

        provider = new GcpSecretProvider(client, gcpProperties);
    }

    @Test
    void shouldReturnProviderType() {
        assertThat(provider.getProviderType()).isEqualTo(ProviderType.GCP);
    }

    @Test
    void shouldBeEnabled() {
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void shouldBeDisabled() {
        gcpProperties.setEnabled(false);
        provider = new GcpSecretProvider(client, gcpProperties);

        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void shouldRetrieveSecret() {
        AccessSecretVersionResponse response = AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8("secret-value"))
                        .build())
                .build();

        doReturn(response).when(client).accessSecretVersion((SecretVersionName) any());

        Optional<String> result = provider.getSecret("test-secret");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("secret-value");
    }

    @Test
    void shouldRetrieveSecretWithVersion() {
        AccessSecretVersionResponse response = AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8("secret-value"))
                        .build())
                .build();

        doReturn(response).when(client).accessSecretVersion((SecretVersionName) any());

        Optional<String> result = provider.getSecret("test-secret", "2");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("secret-value");
    }

    @Test
    void shouldMapLatestVersion() {
        AccessSecretVersionResponse response = AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8("secret-value"))
                        .build())
                .build();

        doReturn(response).when(client).accessSecretVersion((SecretVersionName) any());

        provider.getSecret("test-secret", "latest");

        verify(client).accessSecretVersion(argThat((SecretVersionName name) ->
                name.getProject().equals("test-project") &&
                name.getSecret().equals("test-secret") &&
                name.getSecretVersion().equals("latest")
        ));
    }

    @Test
    void shouldUseNumericVersion() {
        AccessSecretVersionResponse response = AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8("secret-value"))
                        .build())
                .build();

        doReturn(response).when(client).accessSecretVersion((SecretVersionName) any());

        provider.getSecret("test-secret", "5");

        verify(client).accessSecretVersion(argThat((SecretVersionName name) ->
                name.getProject().equals("test-project") &&
                name.getSecret().equals("test-secret") &&
                name.getSecretVersion().equals("5")
        ));
    }

    @Test
    void shouldHandleNotFoundExceptionType() {
        // Use a generic RuntimeException with NOT_FOUND in message
        // Real GCP client would throw NotFoundException, but for unit test we simulate the behavior
        doThrow(new RuntimeException("NOT_FOUND: Secret not found"))
                .when(client).accessSecretVersion((SecretVersionName) any());

        // The provider should catch the exception and convert to Optional.empty() based on message
        Optional<String> result = provider.getSecret("non-existent-secret");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandlePermissionDeniedExceptionType() {
        // Use a generic RuntimeException with PERMISSION_DENIED in message
        doThrow(new RuntimeException("PERMISSION_DENIED: Permission denied"))
                .when(client).accessSecretVersion((SecretVersionName) any());

        assertThatThrownBy(() -> provider.getSecret("test-secret"))
                .isInstanceOf(SecretProviderException.class)
                .hasMessageContaining("Permission denied");
    }

    @Test
    void shouldThrowProviderExceptionOnUnexpectedError() {
        doThrow(new RuntimeException("Unexpected error"))
                .when(client).accessSecretVersion((SecretVersionName) any());

        assertThatThrownBy(() -> provider.getSecret("test-secret"))
                .isInstanceOf(SecretProviderException.class)
                .hasMessageContaining("Unexpected error");
    }

    @Test
    void shouldValidateConfigurationSuccessfully() {
        assertThatCode(() -> provider.validateConfiguration())
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleEmptyPayload() {
        AccessSecretVersionResponse response = AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder()
                        .setData(ByteString.EMPTY)
                        .build())
                .build();

        doReturn(response).when(client).accessSecretVersion((SecretVersionName) any());

        Optional<String> result = provider.getSecret("test-secret");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldUseDefaultVersion() {
        gcpProperties.setDefaultVersion("2");
        provider = new GcpSecretProvider(client, gcpProperties);

        AccessSecretVersionResponse response = AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8("secret-value"))
                        .build())
                .build();

        doReturn(response).when(client).accessSecretVersion((SecretVersionName) any());

        provider.getSecret("test-secret");

        verify(client).accessSecretVersion(argThat((SecretVersionName name) ->
                name.getSecretVersion().equals("2")
        ));
    }
}
