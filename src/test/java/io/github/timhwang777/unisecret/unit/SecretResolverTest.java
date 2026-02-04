package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretNotFoundException;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import io.github.timhwang777.unisecret.provider.ProviderType;
import io.github.timhwang777.unisecret.provider.SecretProvider;
import io.github.timhwang777.unisecret.provider.SecretReference;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SecretResolverTest {

    @Mock
    private SecretProvider awsProvider;

    @Mock
    private SecretProvider gcpProvider;

    @Mock
    private SecretProvider localProvider;

    private SecretManagerProperties properties;
    private SecretResolver resolver;
    private SecretCache cache;

    @BeforeEach
    void setUp() {
        properties = new SecretManagerProperties();
        properties.setProviderOrder(List.of("aws", "gcp", "local"));

        // Initialize cache with default config
        SecretManagerProperties.Cache cacheConfig = new SecretManagerProperties.Cache();
        cacheConfig.setEnabled(true);
        cacheConfig.setTtl(java.time.Duration.ofSeconds(60));
        cacheConfig.setMaxSize(100);
        cache = new SecretCache(cacheConfig);

        lenient().when(awsProvider.getProviderType()).thenReturn(ProviderType.AWS);
        lenient().when(awsProvider.isEnabled()).thenReturn(true);

        lenient().when(gcpProvider.getProviderType()).thenReturn(ProviderType.GCP);
        lenient().when(gcpProvider.isEnabled()).thenReturn(true);

        lenient().when(localProvider.getProviderType()).thenReturn(ProviderType.LOCAL);
        lenient().when(localProvider.isEnabled()).thenReturn(true);

        resolver = new SecretResolver(List.of(awsProvider, gcpProvider, localProvider), properties, cache);
    }

    @Test
    void shouldResolveFromFirstProvider() {
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("secret-value"));

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("secret-value");
        verify(awsProvider).getSecret("test-secret", "latest");
        verify(gcpProvider, never()).getSecret(anyString(), anyString());
        verify(localProvider, never()).getSecret(anyString(), anyString());
    }

    @Test
    void shouldFallbackToSecondProvider() {
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("secret-value"));

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("secret-value");
        verify(awsProvider).getSecret("test-secret", "latest");
        verify(gcpProvider).getSecret("test-secret", "latest");
        verify(localProvider, never()).getSecret(anyString(), anyString());
    }

    @Test
    void shouldFallbackToThirdProvider() {
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(localProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("secret-value"));

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("secret-value");
        verify(awsProvider).getSecret("test-secret", "latest");
        verify(gcpProvider).getSecret("test-secret", "latest");
        verify(localProvider).getSecret("test-secret", "latest");
    }

    @Test
    void shouldThrowExceptionWhenNotFoundInAnyProvider() {
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(localProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("test-secret"))
                .isInstanceOf(SecretNotFoundException.class)
                .hasMessageContaining("test-secret")
                .hasMessageContaining("not found in any provider");
    }

    @Test
    void shouldUseDefaultValueWhenNotFound() {
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(localProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());

        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .defaultValue("default-value")
                .build();

        String result = resolver.resolve(reference);

        assertThat(result).isEqualTo("default-value");
    }

    @Test
    void shouldSkipDisabledProviders() {
        when(awsProvider.isEnabled()).thenReturn(false);
        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("secret-value"));

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("secret-value");
        verify(awsProvider, never()).getSecret(anyString(), anyString());
        verify(gcpProvider).getSecret("test-secret", "latest");
    }

    @Test
    void shouldContinueOnProviderException() {
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenThrow(new SecretProviderException("AWS failed", null, false));
        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("secret-value"));

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("secret-value");
        verify(awsProvider).getSecret("test-secret", "latest");
        verify(gcpProvider).getSecret("test-secret", "latest");
    }

    @Test
    void shouldExtractJsonField() {
        String jsonSecret = "{\"password\":\"secret123\"}";
        when(awsProvider.getSecret("db-config", "latest"))
                .thenReturn(Optional.of(jsonSecret));

        SecretReference reference = SecretReference.builder()
                .key("db-config")
                .field("password")
                .build();

        String result = resolver.resolve(reference);

        assertThat(result).isEqualTo("secret123");
    }

    @Test
    void shouldUseSingleProviderOverride() {
        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .provider("gcp")
                .build();

        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("secret-value"));

        String result = resolver.resolve(reference);

        assertThat(result).isEqualTo("secret-value");
        verify(awsProvider, never()).getSecret(anyString(), anyString());
        verify(gcpProvider).getSecret("test-secret", "latest");
        verify(localProvider, never()).getSecret(anyString(), anyString());
    }

    @Test
    void shouldUseCustomProviderChain() {
        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .providers(List.of("local", "aws"))
                .build();

        when(localProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("secret-value"));

        String result = resolver.resolve(reference);

        assertThat(result).isEqualTo("secret-value");
        verify(localProvider).getSecret("test-secret", "latest");
        verify(awsProvider).getSecret("test-secret", "latest");
        verify(gcpProvider, never()).getSecret(anyString(), anyString());
    }
}
