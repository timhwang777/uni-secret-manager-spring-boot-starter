package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretNotFoundException;
import io.github.timhwang777.unisecret.exception.SecretProviderException;
import io.github.timhwang777.unisecret.provider.*;
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

/**
 * Tests for multi-provider fallback chain behavior.
 */
@ExtendWith(MockitoExtension.class)
class SecretResolverFallbackTest {

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
        properties.setProviderOrder(List.of("local", "aws", "gcp"));

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
    void shouldFallbackFromLocalToAwsToGcp() {
        // Local provider doesn't have it
        when(localProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());

        // AWS provider doesn't have it
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());

        // GCP provider has it
        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("gcp-value"));

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("gcp-value");

        // Verify all providers were tried in order
        verify(localProvider).getSecret("test-secret", "latest");
        verify(awsProvider).getSecret("test-secret", "latest");
        verify(gcpProvider).getSecret("test-secret", "latest");
    }

    @Test
    void shouldStopAtFirstProviderWithValue() {
        // Local provider has it
        when(localProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("local-value"));

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("local-value");

        // Only local provider should be called
        verify(localProvider).getSecret("test-secret", "latest");
        verify(awsProvider, never()).getSecret(anyString(), anyString());
        verify(gcpProvider, never()).getSecret(anyString(), anyString());
    }

    @Test
    void shouldSkipDisabledProvidersInChain() {
        // Disable local provider
        when(localProvider.isEnabled()).thenReturn(false);

        // AWS has the secret
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("aws-value"));

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("aws-value");

        // Local provider should be skipped
        verify(localProvider, never()).getSecret(anyString(), anyString());
        verify(awsProvider).getSecret("test-secret", "latest");
    }

    @Test
    void shouldContinueOnProviderException() {
        // Local provider throws exception
        when(localProvider.getSecret("test-secret", "latest"))
                .thenThrow(new SecretProviderException("Local provider failed", null, false));

        // AWS provider has the secret
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("aws-value"));

        String result = resolver.resolve("test-secret");

        assertThat(result).isEqualTo("aws-value");

        verify(localProvider).getSecret("test-secret", "latest");
        verify(awsProvider).getSecret("test-secret", "latest");
    }

    @Test
    void shouldThrowSecretNotFoundWithAllAttempts() {
        // All providers return empty
        when(localProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(awsProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("test-secret"))
                .isInstanceOf(SecretNotFoundException.class)
                .hasMessageContaining("test-secret")
                .hasMessageContaining("not found in any provider")
                .satisfies(exception -> {
                    SecretNotFoundException snfe = (SecretNotFoundException) exception;
                    assertThat(snfe.getAttempts()).hasSize(3);
                    assertThat(snfe.getAttempts()).extracting(a -> a.provider())
                            .containsExactly(ProviderType.LOCAL, ProviderType.AWS, ProviderType.GCP);
                });
    }

    @Test
    void shouldUseSingleProviderOverride() {
        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .provider("gcp")
                .build();

        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("gcp-value"));

        String result = resolver.resolve(reference);

        assertThat(result).isEqualTo("gcp-value");

        // Only GCP should be called
        verify(localProvider, never()).getSecret(anyString(), anyString());
        verify(awsProvider, never()).getSecret(anyString(), anyString());
        verify(gcpProvider).getSecret("test-secret", "latest");
    }

    @Test
    void shouldUseCustomProviderChain() {
        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .providers(List.of("gcp", "local"))
                .build();

        when(gcpProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.empty());
        when(localProvider.getSecret("test-secret", "latest"))
                .thenReturn(Optional.of("local-value"));

        String result = resolver.resolve(reference);

        assertThat(result).isEqualTo("local-value");

        // Only GCP and local should be called (not AWS)
        verify(gcpProvider).getSecret("test-secret", "latest");
        verify(localProvider).getSecret("test-secret", "latest");
        verify(awsProvider, never()).getSecret(anyString(), anyString());
    }

    @Test
    void shouldReturnProviderChain() {
        SecretReference reference = SecretReference.builder()
                .key("test-secret")
                .build();

        List<String> chain = resolver.getProviderChain(reference);

        assertThat(chain).containsExactly("local", "aws", "gcp");
    }

    @Test
    void shouldHandleAllProvidersDisabled() {
        when(localProvider.isEnabled()).thenReturn(false);
        when(awsProvider.isEnabled()).thenReturn(false);
        when(gcpProvider.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> resolver.resolve("test-secret"))
                .isInstanceOf(SecretNotFoundException.class)
                .hasMessageContaining("test-secret")
                .satisfies(exception -> {
                    SecretNotFoundException snfe = (SecretNotFoundException) exception;
                    assertThat(snfe.getAttempts()).hasSize(3);
                    assertThat(snfe.getAttempts()).allMatch(a -> a.errorMessage() != null);
                });
    }
}
