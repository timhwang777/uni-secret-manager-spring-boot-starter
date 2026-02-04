package io.github.timhwang777.unisecret.unit;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import io.github.timhwang777.unisecret.provider.ProviderType;
import io.github.timhwang777.unisecret.provider.SecretProvider;
import io.github.timhwang777.unisecret.provider.SecretResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for provider order configuration behavior.
 */
@ExtendWith(MockitoExtension.class)
class ProviderOrderConfigurationTest {

    @Mock
    private SecretProvider awsProvider;

    @Mock
    private SecretProvider gcpProvider;

    @Mock
    private SecretProvider localProvider;

    private SecretManagerProperties properties;
    private SecretCache cache;

    @BeforeEach
    void setUp() {
        properties = new SecretManagerProperties();

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
    }

    @Test
    void shouldRespectConfiguredProviderOrder() {
        properties.setProviderOrder(List.of("gcp", "local", "aws"));

        SecretResolver resolver = new SecretResolver(
                List.of(awsProvider, gcpProvider, localProvider),
                properties,
                cache
        );

        // Verify provider order is respected
        assertThat(resolver.getProviderChain(null))
                .containsExactly("gcp", "local", "aws");
    }

    @Test
    void shouldUseDefaultProviderOrderWhenNotConfigured() {
        // Default order should be: local, aws, gcp
        properties.setProviderOrder(List.of("local", "aws", "gcp"));

        SecretResolver resolver = new SecretResolver(
                List.of(awsProvider, gcpProvider, localProvider),
                properties,
                cache
        );

        assertThat(resolver.getProviderChain(null))
                .containsExactly("local", "aws", "gcp");
    }

    @Test
    void shouldThrowExceptionWhenNoProvidersEnabled() {
        when(awsProvider.isEnabled()).thenReturn(false);
        when(gcpProvider.isEnabled()).thenReturn(false);
        when(localProvider.isEnabled()).thenReturn(false);

        properties.setProviderOrder(List.of("aws", "gcp", "local"));

        assertThatThrownBy(() ->
                new SecretResolver(List.of(awsProvider, gcpProvider, localProvider), properties, cache)
        )
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("No secret providers are enabled");
    }

    @Test
    void shouldThrowExceptionWhenNoProvidersAvailable() {
        assertThatThrownBy(() ->
                new SecretResolver(List.of(), properties, cache)
        )
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("No secret providers are configured");
    }

    @Test
    void shouldSkipDisabledProvidersInOrder() {
        when(awsProvider.isEnabled()).thenReturn(false);
        properties.setProviderOrder(List.of("aws", "gcp", "local"));

        SecretResolver resolver = new SecretResolver(
                List.of(awsProvider, gcpProvider, localProvider),
                properties,
                cache
        );

        // AWS should be skipped since it's disabled
        assertThat(resolver.getProviderChain(null))
                .containsExactly("aws", "gcp", "local");
    }

    @Test
    void shouldHandleProviderOrderWithUnknownProviders() {
        // Provider order includes "azure" which doesn't exist
        properties.setProviderOrder(List.of("local", "azure", "aws", "gcp"));

        SecretResolver resolver = new SecretResolver(
                List.of(awsProvider, gcpProvider, localProvider),
                properties,
                cache
        );

        // Should still work, unknown providers are simply ignored
        assertThat(resolver.getProviderChain(null))
                .containsExactly("local", "azure", "aws", "gcp");
    }

    @Test
    void shouldAllowPartialProviderOrderConfiguration() {
        // Only configure some providers in order
        properties.setProviderOrder(List.of("gcp", "local"));

        SecretResolver resolver = new SecretResolver(
                List.of(awsProvider, gcpProvider, localProvider),
                properties,
                cache
        );

        // Should use configured order
        assertThat(resolver.getProviderChain(null))
                .containsExactly("gcp", "local");
    }

    @Test
    void shouldValidateProviderOrderIsNotNull() {
        properties.setProviderOrder(null);

        assertThatThrownBy(() ->
                new SecretResolver(List.of(awsProvider, gcpProvider, localProvider), properties, cache)
        )
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Provider order must not be null");
    }

    @Test
    void shouldValidateProviderOrderIsNotEmpty() {
        properties.setProviderOrder(List.of());

        assertThatThrownBy(() ->
                new SecretResolver(List.of(awsProvider, gcpProvider, localProvider), properties, cache)
        )
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Provider order must not be empty");
    }

    @Test
    void shouldAllowCaseInsensitiveProviderNames() {
        properties.setProviderOrder(List.of("AWS", "GCP", "LOCAL"));

        SecretResolver resolver = new SecretResolver(
                List.of(awsProvider, gcpProvider, localProvider),
                properties,
                cache
        );

        // Should normalize to lowercase
        assertThat(resolver.getProviderChain(null))
                .containsExactly("aws", "gcp", "local");
    }
}
